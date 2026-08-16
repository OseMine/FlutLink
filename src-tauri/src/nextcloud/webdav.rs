use futures_util::Stream;
use quick_xml::events::Event;
use quick_xml::Reader;
use reqwest::{Client, Method};
use std::pin::Pin;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::task::{Context, Poll};

use super::*;
use crate::error::{AppError, AppResult};
use crate::state::{Account, WebDavEntry};

/// Callback invoked with `(transferred_bytes, total_bytes)` as a transfer
/// progresses. Total is `0` when the remote did not advertise a size.
pub type ProgressFn = Arc<dyn Fn(u64, u64) + Send + Sync>;

/// Files larger than this many bytes are uploaded via the WebDAV chunked
/// upload v2 protocol instead of a single PUT. The server enforces a minimum
/// chunk size of 5 MiB (last chunk excluded), so chunking only pays off for
/// files that exceed the chunk size itself.
const CHUNK_UPLOAD_MIN_BYTES: u64 = 10 * 1024 * 1024;
/// Bytes per chunk of the v2 protocol. The server accepts chunks between
/// 5 MiB and 5 GiB (the last chunk may be smaller); 10 MiB stays within range.
const CHUNK_UPLOAD_CHUNK_BYTES: u64 = 10 * 1024 * 1024;

// Compile-time guards so future size changes cannot silently violate the
// server-side v2 constraints (intermediate chunks 5 MiB..5 GiB).
const _: () = assert!(CHUNK_UPLOAD_CHUNK_BYTES >= 5 * 1024 * 1024);
const _: () = assert!(CHUNK_UPLOAD_CHUNK_BYTES <= 5 * 1024 * 1024 * 1024);
const _: () = assert!(CHUNK_UPLOAD_MIN_BYTES == CHUNK_UPLOAD_CHUNK_BYTES);

/// Wraps a byte stream and reports `(transferred, total)` for every chunk.
struct ProgressStream<S> {
    inner: S,
    total: u64,
    transferred: u64,
    on_progress: ProgressFn,
}

impl<S, E> Stream for ProgressStream<S>
where
    S: Stream<Item = Result<bytes::Bytes, E>> + Unpin,
{
    type Item = Result<bytes::Bytes, E>;

    fn poll_next(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        match Pin::new(&mut self.inner).poll_next(cx) {
            Poll::Ready(Some(Ok(chunk))) => {
                self.transferred += chunk.len() as u64;
                (self.on_progress)(self.transferred, self.total);
                Poll::Ready(Some(Ok(chunk)))
            }
            Poll::Ready(Some(Err(e))) => Poll::Ready(Some(Err(e))),
            Poll::Ready(None) => Poll::Ready(None),
            Poll::Pending => Poll::Pending,
        }
    }
}

/// List the contents of a folder via a WebDAV PROPFIND (Depth: 1).
///
/// `path` is a decoded logical path relative to the user's files root,
/// e.g. "" or "/" for home, "/Photos" for a subfolder.
///
/// `target_user` optionally switches the namespace to another user's files
/// root. Admins can browse private namespaces by attaching the
/// `Impersonate-User: {target_user}` header (requires admin credentials).
pub async fn list(
    client: &Client,
    account: &Account,
    path: &str,
    target_user: Option<&str>,
) -> AppResult<Vec<WebDavEntry>> {
    let effective_user = target_user.unwrap_or(&account.meta.username);
    let base = format!(
        "{}/remote.php/dav/files/{}",
        account.base_url(),
        urlencoding::encode(effective_user)
    );
    let url = if path.is_empty() || path == "/" {
        base.clone()
    } else {
        format!("{}/{}", base, encode_segments(path))
    };
    let method = Method::from_bytes(b"PROPFIND").expect("valid HTTP method");
    let mut req = client
        .request(method, &url)
        .basic_auth(&account.meta.username, Some(&account.token))
        .header("Depth", "1");
    if effective_user != account.meta.username {
        req = req.header("Impersonate-User", effective_user);
    }
    let res = req.send().await?;
    let status = res.status();
    if status.is_success() || status.as_u16() == 207 {
        let body = res.text().await?;
        let base_path = format!(
            "/remote.php/dav/files/{}",
            urlencoding::encode(effective_user)
        );
        let entries = parse_multistatus(&body, &base_path)?;
        // Namespace guard: if the server silently ignored `Impersonate-User`,
        // the hrefs point at the *admin's* namespace. `relative_path` strips
        // scheme + host from absolute hrefs, so a leftover "/remote.php/..."
        // path signals the mismatch for both the relative and the absolute
        // href form (a leaked URL like "/https:/host/..." is caught too).
        // Refuse the mismatched listing instead of feeding garbage paths to
        // the caller.
        if entries.iter().any(|e| is_namespace_mismatch(&e.path)) {
            return Err(AppError::App(format!(
                "Server did not honor the impersonated namespace for '{}'.",
                effective_user
            )));
        }
        Ok(entries)
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
}

/// Search the whole files tree of `account` (or `target_user`) for entries
/// whose name contains `query`, via the WebDAV-SEARCH extension (RFC 5323)
/// that Nextcloud exposes on `/remote.php/dav/`.
///
/// Returns the same structured entries as [`list`], with `path` relative to
/// the user's files root.
pub async fn search(
    client: &Client,
    account: &Account,
    query: &str,
    target_user: Option<&str>,
) -> AppResult<Vec<WebDavEntry>> {
    let effective_user = target_user.unwrap_or(&account.meta.username);
    let url = format!("{}/remote.php/dav/", account.base_url());
    let body = search_request_body(effective_user, query);
    let method = Method::from_bytes(b"SEARCH").expect("valid HTTP method");
    let mut req = client
        .request(method, &url)
        .basic_auth(&account.meta.username, Some(&account.token))
        .header("Content-Type", "application/xml")
        .header("Depth", "0")
        .body(body);
    if effective_user != account.meta.username {
        req = req.header("Impersonate-User", effective_user);
    }
    let res = req.send().await?;
    let status = res.status();
    if status.is_success() || status.as_u16() == 207 {
        let body = res.text().await?;
        let base_path = format!(
            "/remote.php/dav/files/{}",
            urlencoding::encode(effective_user)
        );
        let entries = parse_multistatus(&body, &base_path)?;
        // Namespace guard (same as `list`): a server that ignored
        // `Impersonate-User` would answer with hrefs in the admin namespace.
        if entries.iter().any(|e| e.path.starts_with("/remote.php/")) {
            return Err(AppError::App(format!(
                "Server did not honor the impersonated namespace for '{}'.",
                effective_user
            )));
        }
        Ok(entries)
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
}

/// Build the WebDAV-SEARCH body searching `displayname` (which Nextcloud
/// interprets as a case-insensitive "contains" on the file name) over the
/// whole `depth: infinity` tree of `user`.
fn search_request_body(user: &str, query: &str) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<d:searchrequest xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:basicsearch>
    <d:select>
      <d:prop>
        <d:displayname/>
        <d:getcontentlength/>
        <d:getlastmodified/>
        <d:getetag/>
        <d:getcontenttype/>
      </d:prop>
    </d:select>
    <d:from>
      <d:scope>
        <d:href>/files/{}</d:href>
        <d:depth>infinity</d:depth>
      </d:scope>
    </d:from>
    <d:where>
      <d:eq>
        <d:prop><d:displayname/></d:prop>
        <d:literal>{}</d:literal>
      </d:eq>
    </d:where>
    <d:orderby/>
  </d:basicsearch>
</d:searchrequest>
"#,
        escape_xml(user),
        escape_xml(query)
    )
}

/// Escape a string for use as XML element text content.
fn escape_xml(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

#[derive(Clone, Copy, PartialEq)]
enum Field {
    Href,
    Size,
    Mtime,
    Etag,
    ContentType,
}

/// Full WebDAV URL of a path relative to the user's files root,
/// e.g. "/FlutLink/Photos/a.txt" → https://host/remote.php/dav/files/admin/...
///
/// `target_user` switches the namespace to another user's files root (requires
/// admin credentials + the `Impersonate-User` header).
fn remote_url(account: &Account, remote_rel: &str, target_user: Option<&str>) -> String {
    let effective_user = target_user.unwrap_or(&account.meta.username);
    format!(
        "{}/remote.php/dav/files/{}/{}",
        account.base_url(),
        urlencoding::encode(effective_user),
        encode_segments(remote_rel)
    )
}

fn impersonation_header(
    req: reqwest::RequestBuilder,
    account: &Account,
    target_user: Option<&str>,
) -> reqwest::RequestBuilder {
    match target_user {
        Some(user) if user != account.meta.username.as_str() => {
            req.header("Impersonate-User", user)
        }
        _ => req,
    }
}

/// Stream a local file to the cloud via PUT. Sends `X-OC-MTime` so the
/// server stores the local modification time (keeps change detection stable).
pub async fn put_file(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    local_path: &std::path::Path,
    mtime_secs: i64,
) -> AppResult<()> {
    put_file_as(client, account, remote_rel, local_path, mtime_secs, None).await
}

/// Like [`put_file`], but in another user's namespace (admin impersonation).
pub async fn put_file_as(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    local_path: &std::path::Path,
    mtime_secs: i64,
    target_user: Option<&str>,
) -> AppResult<()> {
    put_file_as_progress(
        client,
        account,
        remote_rel,
        local_path,
        mtime_secs,
        target_user,
        None,
    )
    .await
}

/// Like [`put_file_as`], but reports `(transferred, total)` per uploaded chunk.
///
/// Files above [`CHUNK_UPLOAD_MIN_BYTES`] are uploaded through the WebDAV
/// chunked upload v2 protocol: a session folder under
/// `/remote.php/dav/uploads/` receives the file in numbered chunks which the
/// server assembles on a final MOVE. Each chunk is an independent request, so
/// no single transfer can run into the client's read timeout.
pub async fn put_file_as_progress(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    local_path: &std::path::Path,
    mtime_secs: i64,
    target_user: Option<&str>,
    on_progress: Option<ProgressFn>,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel, target_user);
    let file = tokio::fs::File::open(local_path).await?;
    let total = file.metadata().await.map(|m| m.len()).unwrap_or(0);
    let on_progress = on_progress.unwrap_or_else(|| Arc::new(|_, _| {}));
    if total > CHUNK_UPLOAD_MIN_BYTES {
        chunked_put_v2(
            client,
            account,
            &url,
            file,
            mtime_secs,
            target_user,
            &on_progress,
        )
        .await
    } else {
        let stream = ProgressStream {
            inner: tokio_util::io::ReaderStream::new(file),
            total,
            transferred: 0,
            on_progress,
        };
        let body = reqwest::Body::wrap_stream(stream);
        let res = impersonation_header(
            client
                .put(&url)
                .basic_auth(&account.meta.username, Some(&account.token))
                .header("X-OC-MTime", mtime_secs.to_string())
                .header("Content-Type", "application/octet-stream")
                .body(body),
            account,
            target_user,
        )
        .send()
        .await?;
        status_check(res).await
    }
}

/// Upload `file` (of `total` bytes) via the WebDAV chunked upload v2 protocol
/// (Nextcloud):
///
/// 1. A MKCOL creates a uniquely named session folder under
///    `/remote.php/dav/uploads/{user}/{transferId}`.
/// 2. The file is read in [`CHUNK_UPLOAD_CHUNK_BYTES`] blocks and each block is
///    PUT to the session folder under a running number (1..=10000).
/// 3. A final MOVE of the `.file` pseudo-entry assembles the chunks into the
///    destination file.
///
/// `Destination` (the final file URL) and `OC-Total-Length` are sent on every
/// request so the server checks the quota while the chunks arrive. On failure
/// the session folder is removed again to not leak uploaded chunks.
async fn chunked_put_v2(
    client: &Client,
    account: &Account,
    dest_url: &str,
    mut file: tokio::fs::File,
    mtime_secs: i64,
    target_user: Option<&str>,
    on_progress: &ProgressFn,
) -> AppResult<()> {
    use tokio::io::AsyncReadExt;

    let total = file.metadata().await.map(|m| m.len()).unwrap_or(0);
    let effective_user = target_user.unwrap_or(&account.meta.username);
    let upload_dir = format!(
        "{}/remote.php/dav/uploads/{}/{}",
        account.base_url(),
        urlencoding::encode(effective_user),
        transfer_id()
    );

    let method = Method::from_bytes(b"MKCOL").expect("valid HTTP method");
    let res = impersonation_header(
        client
            .request(method, &upload_dir)
            .basic_auth(&account.meta.username, Some(&account.token))
            .header("Destination", dest_url)
            .header("OC-Total-Length", total.to_string()),
        account,
        target_user,
    )
    .send()
    .await?;
    let status = res.status();
    // 405 = session already exists; with a unique transfer id this only happens
    // when a previous attempt left a folder behind, which is fine to reuse.
    if !(status.is_success() || status.as_u16() == 405) {
        let body = res.text().await.unwrap_or_default();
        return Err(AppError::Status {
            status: status.as_u16(),
            body,
        });
    }

    let result: AppResult<()> = async {
        let mut transferred = 0u64;
        let mut number = 1u64;
        let mut buffer = vec![0u8; CHUNK_UPLOAD_CHUNK_BYTES as usize];
        loop {
            let read = file.read(&mut buffer).await?;
            if read == 0 {
                break;
            }
            let res = impersonation_header(
                client
                    .put(format!("{}/{}", upload_dir, number))
                    .basic_auth(&account.meta.username, Some(&account.token))
                    .header("Destination", dest_url)
                    .header("OC-Total-Length", total.to_string())
                    .body(buffer[..read].to_vec()),
                account,
                target_user,
            )
            .send()
            .await?;
            status_check(res).await?;
            transferred += read as u64;
            on_progress(transferred, total);
            number += 1;
        }
        // Assembling the chunks is a MOVE of the `.file` pseudo-entry; the
        // modification time is forwarded so change detection stays stable.
        let method = Method::from_bytes(b"MOVE").expect("valid HTTP method");
        let res = impersonation_header(
            client
                .request(method, format!("{}/.file", upload_dir))
                .basic_auth(&account.meta.username, Some(&account.token))
                .header("Destination", dest_url)
                .header("OC-Total-Length", total.to_string())
                .header("X-OC-MTime", mtime_secs.to_string()),
            account,
            target_user,
        )
        .send()
        .await?;
        status_check(res).await
    }
    .await;
    if result.is_err() {
        // Never leave an orphaned chunk session behind: the server would only
        // expire it after 24 h and it would keep occupying storage quota.
        let _ = delete_upload_session(client, account, &upload_dir, target_user).await;
    }
    result
}

/// Best-effort removal of a chunked-upload session folder after a failure.
async fn delete_upload_session(
    client: &Client,
    account: &Account,
    upload_dir: &str,
    target_user: Option<&str>,
) -> AppResult<()> {
    let method = Method::from_bytes(b"DELETE").expect("valid HTTP method");
    let res = impersonation_header(
        client
            .request(method, upload_dir)
            .basic_auth(&account.meta.username, Some(&account.token)),
        account,
        target_user,
    )
    .send()
    .await?;
    status_check(res).await
}

/// Unique session id for a chunked upload (e.g. `flutlink-1234-1f6c2a...-0`).
/// Process id + nanosecond timestamp + counter keep collisions negligible even
/// for concurrent uploads of the same file.
fn transfer_id() -> String {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let n = COUNTER.fetch_add(1, Ordering::Relaxed);
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or_default();
    format!("flutlink-{:x}-{:x}-{}", std::process::id(), nanos, n)
}

/// Check whether a remote resource exists via a WebDAV PROPFIND (Depth: 0).
/// Used to refuse silent overwrites on upload.
pub async fn exists(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    target_user: Option<&str>,
) -> AppResult<bool> {
    let url = remote_url(account, remote_rel, target_user);
    let method = Method::from_bytes(b"PROPFIND").expect("valid HTTP method");
    let res = impersonation_header(
        client
            .request(method, &url)
            .basic_auth(&account.meta.username, Some(&account.token))
            .header("Depth", "0"),
        account,
        target_user,
    )
    .send()
    .await?;
    let status = res.status();
    if status.is_success() || status.as_u16() == 207 {
        Ok(true)
    } else if status.as_u16() == 404 {
        Ok(false)
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
}

/// Upload a small UTF-8 string (e.g. a README) via PUT. The server sets the
/// modification time itself.
pub async fn put_text(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    content: &str,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel, None);
    let res = client
        .put(&url)
        .basic_auth(&account.meta.username, Some(&account.token))
        .header("Content-Type", "text/markdown; charset=utf-8")
        .body(content.to_string())
        .send()
        .await?;
    status_check(res).await
}

/// Download a remote file to `dest`, writing to a temp file first so the
/// destination is only replaced once the transfer fully succeeded.
pub async fn get_file(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    dest: &std::path::Path,
) -> AppResult<()> {
    get_file_as(client, account, remote_rel, dest, None).await
}

/// Like [`get_file`], but in another user's namespace (admin impersonation).
pub async fn get_file_as(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    dest: &std::path::Path,
    target_user: Option<&str>,
) -> AppResult<()> {
    get_file_as_progress(client, account, remote_rel, dest, target_user, None).await
}

/// Like [`get_file_as`], but reports `(transferred, total)` per received chunk.
pub async fn get_file_as_progress(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    dest: &std::path::Path,
    target_user: Option<&str>,
    on_progress: Option<ProgressFn>,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel, target_user);
    stream_to_file(client, account, &url, dest, target_user, on_progress, None).await
}

/// Download a remote folder as a ZIP archive (Nextcloud WebDAV extension).
///
/// A GET request on the folder's DAV URL with `Accept: application/zip`
/// streams an archive of the folder contents. Reports transfer progress like
/// [`get_file_as_progress`] and writes atomically (temp file + rename).
pub async fn download_zip_as(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    dest: &std::path::Path,
    target_user: Option<&str>,
    on_progress: Option<ProgressFn>,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel, target_user);
    stream_to_file(
        client,
        account,
        &url,
        dest,
        target_user,
        on_progress,
        Some("application/zip"),
    )
    .await
}

/// Shared GET + stream-to-file helper used by [`get_file_as_progress`] and
/// [`download_zip_as`]. Writes to a temp file first so the destination is only
/// replaced once the transfer fully succeeded.
async fn stream_to_file(
    client: &Client,
    account: &Account,
    url: &str,
    dest: &std::path::Path,
    target_user: Option<&str>,
    on_progress: Option<ProgressFn>,
    accept: Option<&str>,
) -> AppResult<()> {
    use futures_util::StreamExt;
    use tokio::io::AsyncWriteExt;

    let mut req = client
        .get(url)
        .basic_auth(&account.meta.username, Some(&account.token));
    if let Some(accept) = accept {
        req = req.header("Accept", accept);
    }
    let res = impersonation_header(req, account, target_user)
        .send()
        .await?;
    let status = res.status();
    if !status.is_success() {
        let body = res.text().await.unwrap_or_default();
        return Err(AppError::Status {
            status: status.as_u16(),
            body,
        });
    }
    let total = res.content_length().unwrap_or(0);
    let on_progress = on_progress.unwrap_or_else(|| Arc::new(|_, _| {}));
    let tmp = tmp_path(dest);
    let result: Result<(), Box<dyn std::error::Error + Send + Sync>> = async {
        let mut file = tokio::fs::File::create(&tmp).await?;
        let mut stream = ProgressStream {
            inner: res.bytes_stream(),
            total,
            transferred: 0,
            on_progress,
        };
        while let Some(chunk) = stream.next().await {
            file.write_all(&chunk?).await?;
        }
        file.sync_all().await?;
        drop(file);
        tokio::fs::rename(&tmp, dest).await?;
        Ok(())
    }
    .await;
    if let Err(e) = result {
        // B18: never leave a half-written .tmp file behind.
        let _ = tokio::fs::remove_file(&tmp).await;
        return Err(AppError::App(format!("download failed: {}", e)));
    }
    Ok(())
}

/// Fetched preview/thumbnail bytes for a file.
pub struct Preview {
    pub content_type: String,
    pub bytes: Vec<u8>,
}

/// Fetch a preview thumbnail for a file from the Nextcloud `/core/preview.png`
/// endpoint. Returns `None` when the server has no preview for the file.
pub async fn preview(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    size: u32,
    target_user: Option<&str>,
) -> AppResult<Option<Preview>> {
    let url = format!(
        "{}/index.php/core/preview.png?file={}&x={}&y={}",
        account.base_url(),
        urlencoding::encode(remote_rel),
        size,
        size
    );
    let res = impersonation_header(
        client
            .get(&url)
            .basic_auth(&account.meta.username, Some(&account.token)),
        account,
        target_user,
    )
    .send()
    .await?;
    let status = res.status();
    // 404/400 = no preview available (unknown file, no provider, disabled).
    if status.as_u16() == 404 || status.as_u16() == 400 {
        return Ok(None);
    }
    if !status.is_success() {
        let body = res.text().await.unwrap_or_default();
        return Err(AppError::Status {
            status: status.as_u16(),
            body,
        });
    }
    let content_type = res
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .map(|ct| ct.split(';').next().unwrap_or(ct).trim().to_string())
        .unwrap_or_else(|| "image/png".to_string());
    let bytes = res.bytes().await?.to_vec();
    Ok(Some(Preview {
        content_type,
        bytes,
    }))
}

/// Create a remote collection (directory) via MKCOL.
pub async fn make_collection(
    client: &Client,
    account: &Account,
    remote_rel: &str,
) -> AppResult<()> {
    make_collection_as(client, account, remote_rel, None).await
}

/// Like [`make_collection`], but in another user's namespace.
pub async fn make_collection_as(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    target_user: Option<&str>,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel, target_user);
    let method = Method::from_bytes(b"MKCOL").expect("valid HTTP method");
    let res = impersonation_header(
        client
            .request(method, &url)
            .basic_auth(&account.meta.username, Some(&account.token)),
        account,
        target_user,
    )
    .send()
    .await?;
    let status = res.status();
    // 405 = already exists, which is fine for idempotent dir creation.
    if status.is_success() || status.as_u16() == 405 {
        Ok(())
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
}

/// Create the whole collection chain for `remote_rel`, top-down. MKCOL only
/// creates a single level, so the root of a sync folder (e.g.
/// `/FlutLink/Name`) 409s with "Parent node does not exist" unless `/FlutLink`
/// is created first. Idempotent: existing levels answer 405 and are ignored.
pub async fn ensure_collection(
    client: &Client,
    account: &Account,
    remote_rel: &str,
) -> AppResult<()> {
    ensure_collection_as(client, account, remote_rel, None).await
}

/// Like [`ensure_collection`], but in another user's namespace.
pub async fn ensure_collection_as(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    target_user: Option<&str>,
) -> AppResult<()> {
    let mut path = String::new();
    for segment in remote_rel.split('/').filter(|s| !s.is_empty()) {
        path.push('/');
        path.push_str(segment);
        make_collection_as(client, account, &path, target_user).await?;
    }
    Ok(())
}

/// Delete a remote resource (file or folder).
pub async fn delete(client: &Client, account: &Account, remote_rel: &str) -> AppResult<()> {
    delete_as(client, account, remote_rel, None).await
}

/// Like [`delete`], but in another user's namespace.
pub async fn delete_as(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    target_user: Option<&str>,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel, target_user);
    let method = Method::from_bytes(b"DELETE").expect("valid HTTP method");
    let res = impersonation_header(
        client
            .request(method, &url)
            .basic_auth(&account.meta.username, Some(&account.token)),
        account,
        target_user,
    )
    .send()
    .await?;
    let status = res.status();
    // 404 = already gone.
    if status.is_success() || status.as_u16() == 404 {
        Ok(())
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
}

/// Rename/move a remote resource via MOVE. Refuses to overwrite an existing
/// destination (WebDAV `Overwrite: F`): if the target already exists the
/// server answers 412 Precondition Failed and [`AppError::TargetExists`] is
/// returned instead of silently destroying the target.
pub async fn rename(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    new_rel: &str,
) -> AppResult<()> {
    rename_as(client, account, remote_rel, new_rel, None).await
}

/// Like [`rename`], but in another user's namespace.
pub async fn rename_as(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    new_rel: &str,
    target_user: Option<&str>,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel, target_user);
    let dest = remote_url(account, new_rel, target_user);
    let method = Method::from_bytes(b"MOVE").expect("valid HTTP method");
    let res = impersonation_header(
        client
            .request(method, &url)
            .basic_auth(&account.meta.username, Some(&account.token))
            .header("Destination", dest)
            .header("Overwrite", "F"),
        account,
        target_user,
    )
    .send()
    .await?;
    let status = res.status();
    if status.as_u16() == 412 {
        return Err(AppError::TargetExists(new_rel.to_string()));
    }
    status_check(res).await
}

async fn status_check(res: reqwest::Response) -> AppResult<()> {
    let status = res.status();
    if status.is_success() {
        Ok(())
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
}

/// Temp file next to the destination. The counter makes the name unique even
/// for parallel downloads of the same destination (which the sync engine can
/// trigger for conflict copies).
fn tmp_path(dest: &std::path::Path) -> std::path::PathBuf {
    static COUNTER: AtomicU64 = AtomicU64::new(0);
    let n = COUNTER.fetch_add(1, Ordering::Relaxed);
    let mut name = dest
        .file_name()
        .map(|n| n.to_os_string())
        .unwrap_or_default();
    name.push(format!(".flutlink-{}-{}.tmp", std::process::id(), n));
    dest.with_file_name(name)
}

/// Parse a WebDAV multistatus XML document into structured entries.
pub fn parse_multistatus(body: &str, base_path: &str) -> AppResult<Vec<WebDavEntry>> {
    let mut reader = Reader::from_str(body);
    let mut buf = Vec::new();
    let mut entries: Vec<WebDavEntry> = Vec::new();

    let mut href: Option<String> = None;
    let mut is_dir = false;
    let mut in_resourcetype = false;
    let mut field: Option<Field> = None;
    let mut text = String::new();
    let mut size: Option<u64> = None;
    let mut mtime: Option<String> = None;
    let mut etag: Option<String> = None;
    let mut content_type: Option<String> = None;

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(e)) => match local(e.name().as_ref()) {
                b"response" => {
                    href = None;
                    is_dir = false;
                    size = None;
                    mtime = None;
                    etag = None;
                    content_type = None;
                }
                b"href" => field = Some(Field::Href),
                b"resourcetype" => in_resourcetype = true,
                b"collection" if in_resourcetype => is_dir = true,
                b"getcontentlength" => field = Some(Field::Size),
                b"getlastmodified" => field = Some(Field::Mtime),
                b"getetag" => field = Some(Field::Etag),
                b"getcontenttype" => field = Some(Field::ContentType),
                _ => {}
            },
            Ok(Event::Empty(e)) => {
                if in_resourcetype && local(e.name().as_ref()) == b"collection" {
                    is_dir = true;
                }
            }
            Ok(Event::Text(t)) => {
                if field.is_some() {
                    let decoded = t
                        .decode()
                        .map_err(|e| AppError::Parse(format!("XML decode error: {}", e)))?;
                    text.push_str(decoded.as_ref());
                }
            }
            Ok(Event::End(e)) => {
                if field.is_some() {
                    let value = text.trim().to_string();
                    match field.take() {
                        Some(Field::Href) => href = Some(value),
                        Some(Field::Size) => size = value.parse::<u64>().ok(),
                        Some(Field::Mtime) => mtime = Some(value),
                        Some(Field::Etag) => etag = Some(value),
                        Some(Field::ContentType) => content_type = Some(value),
                        None => {}
                    }
                    text.clear();
                }
                match local(e.name().as_ref()) {
                    b"resourcetype" => in_resourcetype = false,
                    b"response" => {
                        if let Some(href_value) = href.take() {
                            if let Some(entry) = to_entry(
                                &href_value,
                                base_path,
                                is_dir,
                                size,
                                mtime.clone(),
                                etag.clone(),
                                content_type.clone(),
                            ) {
                                entries.push(entry);
                            }
                        }
                    }
                    _ => {}
                }
            }
            Ok(Event::Eof) => break,
            Err(e) => return Err(AppError::Parse(format!("WebDAV XML parse error: {}", e))),
            _ => {}
        }
    }
    Ok(entries)
}

fn to_entry(
    href: &str,
    base_path: &str,
    is_dir: bool,
    size: Option<u64>,
    mtime: Option<String>,
    etag: Option<String>,
    content_type: Option<String>,
) -> Option<WebDavEntry> {
    let rel = relative_path(href, base_path);
    if rel == "/" || rel.is_empty() {
        return None;
    }
    let name = decode_segment(rel.rsplit('/').next().unwrap_or_default());
    if name.is_empty() {
        return None;
    }
    let (is_resource, is_part) = classify(&rel);
    Some(WebDavEntry {
        name,
        path: rel.clone(),
        is_dir,
        size,
        mtime,
        etag,
        content_type,
        is_resource,
        is_part,
        paired_path: paired_path(&rel),
    })
}

/// Path portion of a WebDAV href. Servers may return relative
/// (`/remote.php/dav/files/…`) or absolute (`https://host/remote.php/…`)
/// hrefs; for absolute hrefs the scheme + host are stripped so the path can
/// be matched against `base_path`.
fn href_path(href: &str) -> &str {
    match href.find("://") {
        Some(idx) => match href[idx + 3..].find('/') {
            Some(slash) => &href[idx + 3 + slash..],
            None => "/",
        },
        None => href,
    }
}

/// Index right after the first `base_path` occurrence in `path` that ends on
/// a path boundary, so e.g. `/remote.php/dav/files/admin` never matches inside
/// `/remote.php/dav/files/admin2`. Returns `None` when not found.
fn find_base_path(path: &str, base_path: &str) -> Option<usize> {
    let mut start = 0;
    while let Some(idx) = path[start..].find(base_path) {
        let abs = start + idx;
        let end = abs + base_path.len();
        if path[end..].starts_with('/') || path[end..].is_empty() {
            return Some(end);
        }
        start = abs + 1;
    }
    None
}

/// True if a normalized path still points into a foreign WebDAV namespace —
/// either the relative form (`/remote.php/…`) or an absolute URL that leaked
/// into the path (`/https:/host/…`, http/https schemes).
fn is_namespace_mismatch(path: &str) -> bool {
    path.starts_with("/remote.php/") || path.starts_with("/http:/") || path.starts_with("/https:/")
}

/// Convert a WebDAV href into a decoded logical path relative to the files root.
fn relative_path(href: &str, base_path: &str) -> String {
    let path = href_path(href);
    let after = match find_base_path(path, base_path) {
        Some(end) => &path[end..],
        None => path,
    };
    let trimmed = after.trim_matches('/');
    if trimmed.is_empty() {
        return "/".to_string();
    }
    let decoded: Vec<String> = trimmed.split('/').map(decode_segment).collect();
    format!("/{}", decoded.join("/"))
}

/// Flag entries living under `resources` (read-only / virtual links) or `parts` (write-enabled).
fn classify(rel: &str) -> (bool, bool) {
    let mut is_resource = false;
    let mut is_part = false;
    for segment in rel.split('/') {
        match segment.to_ascii_lowercase().as_str() {
            "resources" => is_resource = true,
            "parts" => is_part = true,
            _ => {}
        }
    }
    (is_resource, is_part)
}

/// Compute the counterpart of a path in the FlutCloud virtual namespaces:
/// `/resources/…` (read-only virtual links) pair with the matching `/parts/…`
/// (write-enabled) and vice versa. The first segment matching either name is
/// swapped; the remainder of the path is preserved. Returns `None` for paths
/// outside both namespaces.
fn paired_path(rel: &str) -> Option<String> {
    let segments: Vec<&str> = rel.split('/').collect();
    for (i, segment) in segments.iter().enumerate() {
        if segment.eq_ignore_ascii_case("resources") {
            let mut paired = segments.clone();
            paired[i] = "parts";
            return Some(paired.join("/"));
        }
        if segment.eq_ignore_ascii_case("parts") {
            let mut paired = segments.clone();
            paired[i] = "resources";
            return Some(paired.join("/"));
        }
    }
    None
}

fn decode_segment(segment: &str) -> String {
    urlencoding::decode(segment)
        .map(|c| c.into_owned())
        .unwrap_or_else(|_| segment.to_string())
}

fn local(name: &[u8]) -> &[u8] {
    match name.iter().rposition(|&b| b == b':') {
        Some(idx) => &name[idx + 1..],
        None => name,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const MULTISTATUS: &str = r#"<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:response>
    <d:href>/remote.php/dav/files/admin/</d:href>
    <d:propstat>
      <d:prop>
        <d:resourcetype><d:collection/></d:resourcetype>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/admin/Photos/</d:href>
    <d:propstat>
      <d:prop>
        <d:getlastmodified>Thu, 13 Aug 2026 12:00:00 GMT</d:getlastmodified>
        <d:getetag>"abcdef"</d:getetag>
        <d:resourcetype><d:collection/></d:resourcetype>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/admin/resources/</d:href>
    <d:propstat>
      <d:prop>
        <d:resourcetype><d:collection/></d:resourcetype>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/remote.php/dav/files/admin/Parts/Data.bin</d:href>
    <d:propstat>
      <d:prop>
        <d:getcontentlength>2048</d:getcontentlength>
        <d:getlastmodified>Wed, 12 Aug 2026 08:00:00 GMT</d:getlastmodified>
        <d:getetag>"xyz"</d:getetag>
        <d:getcontenttype>application/octet-stream</d:getcontenttype>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>"#;

    #[test]
    fn parses_multistatus() {
        let entries =
            parse_multistatus(MULTISTATUS, "/remote.php/dav/files/admin").expect("parse ok");
        assert_eq!(entries.len(), 3, "root container must be skipped");

        let photos = entries.iter().find(|e| e.name == "Photos").expect("Photos");
        assert!(photos.is_dir);
        assert_eq!(photos.path, "/Photos");
        assert_eq!(photos.etag.as_deref(), Some("\"abcdef\""));

        let resources = entries
            .iter()
            .find(|e| e.name == "resources")
            .expect("resources");
        assert!(resources.is_resource);
        assert!(!resources.is_part);
        assert_eq!(resources.paired_path.as_deref(), Some("/parts"));

        let data = entries
            .iter()
            .find(|e| e.name == "Data.bin")
            .expect("Data.bin");
        assert!(!data.is_dir);
        assert_eq!(data.size, Some(2048));
        assert!(
            data.is_part,
            "folder 'Parts' is case-insensitively detected"
        );
        assert_eq!(
            data.paired_path.as_deref(),
            Some("/resources/Data.bin"),
            "an entry under 'Parts' pairs with the 'resources' namespace"
        );
        assert_eq!(
            data.content_type.as_deref(),
            Some("application/octet-stream")
        );
    }

    #[test]
    fn classifies_paths() {
        assert_eq!(classify("/foo"), (false, false));
        assert_eq!(classify("/resources"), (true, false));
        assert_eq!(classify("/resources/virtual/link.txt"), (true, false));
        assert_eq!(classify("/Parts"), (false, true));
        assert_eq!(classify("/parts/write/me.txt"), (false, true));
    }

    #[test]
    fn pairs_virtual_and_real_paths() {
        assert_eq!(paired_path("/foo"), None);
        assert_eq!(paired_path("/Photos"), None);
        assert_eq!(paired_path("/resources"), Some("/parts".to_string()));
        assert_eq!(
            paired_path("/resources/link"),
            Some("/parts/link".to_string())
        );
        assert_eq!(
            paired_path("/resources/link/file.txt"),
            Some("/parts/link/file.txt".to_string())
        );
        assert_eq!(paired_path("/Parts"), Some("/resources".to_string()));
        assert_eq!(
            paired_path("/parts/write/me.txt"),
            Some("/resources/write/me.txt".to_string())
        );
    }

    #[test]
    fn handles_absolute_hrefs() {
        assert_eq!(
            relative_path(
                "https://host/remote.php/dav/files/admin/Photos/",
                "/remote.php/dav/files/admin"
            ),
            "/Photos"
        );
        assert_eq!(
            relative_path(
                "http://host:8080/remote.php/dav/files/admin/Data.bin",
                "/remote.php/dav/files/admin"
            ),
            "/Data.bin"
        );
    }

    #[test]
    fn relative_path_keeps_base_path_boundaries() {
        assert_eq!(
            relative_path(
                "/remote.php/dav/files/admin2/foo.txt",
                "/remote.php/dav/files/admin"
            ),
            "/remote.php/dav/files/admin2/foo.txt"
        );
    }

    #[test]
    fn detects_namespace_mismatch_for_both_href_forms() {
        assert!(is_namespace_mismatch("/remote.php/dav/files/admin/Photos"));
        assert!(is_namespace_mismatch(
            "/https:/host/remote.php/dav/files/admin/Photos"
        ));
        assert!(is_namespace_mismatch(
            "/http:/host/remote.php/dav/files/admin/Photos"
        ));
        assert!(!is_namespace_mismatch("/Photos"));
    }

    #[test]
    fn parses_absolute_hrefs() {
        let entries = parse_multistatus(
            r#"<d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>https://host/remote.php/dav/files/admin/My%20Folder/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
            </d:multistatus>"#,
            "/remote.php/dav/files/admin",
        )
        .expect("parse ok");
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].name, "My Folder");
        assert_eq!(entries[0].path, "/My Folder");
    }

    #[test]
    fn transfer_ids_are_unique() {
        let first = transfer_id();
        let second = transfer_id();
        assert_ne!(first, second);
        assert!(first.starts_with("flutlink-"));
        assert!(second.starts_with("flutlink-"));
    }

    #[test]
    fn decodes_encoded_names() {
        let entries = parse_multistatus(
            r#"<d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/admin/My%20Folder/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
            </d:multistatus>"#,
            "/remote.php/dav/files/admin",
        )
        .expect("parse ok");
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].name, "My Folder");
        assert_eq!(entries[0].path, "/My Folder");
    }

    #[test]
    fn escapes_search_terms() {
        assert_eq!(escape_xml("a&b<c>d"), "a&amp;b&lt;c&gt;d");
        assert_eq!(escape_xml("plain"), "plain");
    }

    #[test]
    fn builds_search_request_body() {
        let body = search_request_body("admin", "report & final<1>");
        assert!(
            body.contains("<d:scope>\n        <d:href>/files/admin</d:href>"),
            "scope points at the user's files root"
        );
        assert!(
            body.contains("<d:depth>infinity</d:depth>"),
            "search spans the whole tree"
        );
        assert!(
            body.contains("<d:literal>report &amp; final&lt;1&gt;</d:literal>"),
            "search term is XML-escaped"
        );
    }
}
