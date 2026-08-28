# Security

## Credentials never reach the renderer

- Tokens are stored in the **OS keychain** via the `keyring` crate (Windows
  Credential Manager, macOS Keychain, Linux Secret Service).
- At runtime tokens live in Rust-managed memory inside `AppState`; the
  frontend never receives them.
- `accounts.json` (app-data dir) persists **metadata only**: username,
  instance URL, display name, admin flag, active flag. Accounts whose keychain
  token is missing are skipped on startup.

## Registration password = app password

Accounts created through the **Register** flow sign in with the password chosen
during registration: it is stored in the OS keychain and used as the app
password for every request. There is no separate app password to create for a
newly registered account.

Because the stored token *is* the account password, changing the account
password on the server invalidates the token. After a password change the
account must be removed and re-added in FlutLink (see
[Getting started](getting-started.md)).

## All HTTP traffic stays in Rust

WebDAV and OCS requests are issued by the backend, which means:

- No CORS issues and no cross-origin exposure in the webview.
- Custom methods like `PROPFIND` work as intended.
- Credentials are attached in Rust and never logged.

## Admin gating

- The admin flag is detected at sign-in via OCS (`user_group_details`).
- Admin commands (`admin_list_users`, `admin_get_user`,
  `admin_set_user_quota`, `admin_edit_user`, `admin_create_user`,
  `admin_delete_user`) are only allowed for admin accounts.
- **Impersonation:** `webdav_list` accepts an optional `target_user`. The
  backend refuses the call with `AppError::Forbidden` for non-admins and sets
  the `Impersonate-User` header on the WebDAV request for admins. The
  frontend shows an "Admin impersonation" notice while browsing another
  user's files.

## FlutCloud-only enforcement

FlutLink connects exclusively to the FlutCloud server and rejects all others:

- The server URL is read from `FLUTCLOUD_URL` in `.env`
  (`src-tauri/src/flutcloud.rs`) and exposed to the frontend via
  `get_flutcloud_url`. CI release builds bake the URL into binaries at compile
  time (`option_env!`) so installed apps work without a local `.env`.
- The mobile client (`kmp/`) bakes the same URL into
  `BuildConfig.FLUTCLOUD_URL` from the `FLUTCLOUD_URL` environment variable
  (falling back to the `-PflutcloudUrl` Gradle property) and locks the login
  server field when a URL is compiled in.
- `account_add` / `register_user` reject other URLs
  (`AppError::NotFlutCloud`).
- Before connecting, the OCS capabilities endpoint is probed for the
  `flutcloud` capability (`AppError::FlutCloudAppMissing` if absent).
- Accounts persisted for other servers are dropped when the app starts.

## Error handling

- All commands return a serialized `AppError { code, message }`; the frontend
  surfaces it as an inline error or toast (`invokeError` in `src/lib/ipc.ts`).
- Secrets are never part of error messages.

## Webview hardening

- `capabilities/default.json` grants only the minimum permissions
  (`core:default`, `opener:default`, `dialog:default`).
- Frontend origin: only the packaged frontend / dev server are loaded; a CSP
  (`default-src 'self'`, see `src-tauri/tauri.conf.json`) limits what the
  webview may load — tighten it before adding remote content.
