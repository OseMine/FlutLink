use quick_xml::events::Event;
use quick_xml::Reader;
use reqwest::{Client, Method};

use super::*;
use crate::error::{AppError, AppResult};
use crate::state::{Account, WebDavEntry};

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
        parse_multistatus(&body, &base_path)
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
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
fn remote_url(account: &Account, remote_rel: &str) -> String {
    format!(
        "{}/remote.php/dav/files/{}/{}",
        account.base_url(),
        urlencoding::encode(&account.meta.username),
        encode_segments(remote_rel)
    )
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
    let url = remote_url(account, remote_rel);
    let file = tokio::fs::File::open(local_path).await?;
    let body = reqwest::Body::wrap_stream(tokio_util::io::ReaderStream::new(file));
    let res = client
        .put(&url)
        .basic_auth(&account.meta.username, Some(&account.token))
        .header("X-OC-MTime", mtime_secs.to_string())
        .header("Content-Type", "application/octet-stream")
        .body(body)
        .send()
        .await?;
    status_check(res).await
}

/// Upload a small UTF-8 string (e.g. a README) via PUT. The server sets the
/// modification time itself.
pub async fn put_text(
    client: &Client,
    account: &Account,
    remote_rel: &str,
    content: &str,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel);
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
    use futures_util::StreamExt;
    use tokio::io::AsyncWriteExt;

    let url = remote_url(account, remote_rel);
    let res = client
        .get(&url)
        .basic_auth(&account.meta.username, Some(&account.token))
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
    let tmp = tmp_path(dest);
    let mut file = tokio::fs::File::create(&tmp).await?;
    let mut stream = res.bytes_stream();
    while let Some(chunk) = stream.next().await {
        file.write_all(&chunk?).await?;
    }
    file.sync_all().await?;
    drop(file);
    if let Err(e) = tokio::fs::rename(&tmp, dest).await {
        let _ = tokio::fs::remove_file(&tmp).await;
        return Err(e.into());
    }
    Ok(())
}

/// Create a remote collection (directory) via MKCOL.
pub async fn make_collection(
    client: &Client,
    account: &Account,
    remote_rel: &str,
) -> AppResult<()> {
    let url = remote_url(account, remote_rel);
    let method = Method::from_bytes(b"MKCOL").expect("valid HTTP method");
    let res = client
        .request(method, &url)
        .basic_auth(&account.meta.username, Some(&account.token))
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
    let mut path = String::new();
    for segment in remote_rel.split('/').filter(|s| !s.is_empty()) {
        path.push('/');
        path.push_str(segment);
        make_collection(client, account, &path).await?;
    }
    Ok(())
}

/// Delete a remote resource (file or folder).
pub async fn delete(client: &Client, account: &Account, remote_rel: &str) -> AppResult<()> {
    let url = remote_url(account, remote_rel);
    let method = Method::from_bytes(b"DELETE").expect("valid HTTP method");
    let res = client
        .request(method, &url)
        .basic_auth(&account.meta.username, Some(&account.token))
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

fn tmp_path(dest: &std::path::Path) -> std::path::PathBuf {
    let mut name = dest
        .file_name()
        .map(|n| n.to_os_string())
        .unwrap_or_default();
    name.push(format!(".flutlink-{}.tmp", std::process::id()));
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
        path: rel,
        is_dir,
        size,
        mtime,
        etag,
        content_type,
        is_resource,
        is_part,
    })
}

/// Convert a WebDAV href into a decoded logical path relative to the files root.
fn relative_path(href: &str, base_path: &str) -> String {
    let after = match href.find(base_path) {
        Some(idx) => &href[idx + base_path.len()..],
        None => href,
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
}
