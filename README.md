# FlutLink

A high-performance sync and management desktop client for the **FlutCloud**
server built with **Tauri v2** (Rust backend, Vue 3 + TypeScript + Tailwind
frontend).

> **FlutLink is a dedicated client for the FlutCloud server — not a generic
> Nextcloud client.** Every connection is verified against the FlutCloud
> Nextcloud app (`flutcloud-app/`); servers that do not run it are rejected.

All HTTP traffic (WebDAV, OCS) is handled in Rust, which avoids CORS, enables
custom HTTP methods like `PROPFIND`, and keeps credentials out of the renderer.

## Documentation

Full bilingual documentation (English + German) lives in
[`docs/`](docs/README.md) — getting started, features, architecture, sync
engine, tray & CLI, security and development guide.

## Architecture

```
flutlink/
├── src/                        # Vue 3 + TypeScript + Tailwind v4 frontend
│   ├── components/
│   │   ├── AccountBar.vue      # Account switcher + add/remove + keychain-backed sign-in
│   │   ├── FileExplorer.vue    # WebDAV browser: grid/list, sort, multi-select, file ops, link sharing
│   │   ├── AdminPanel.vue      # OCS user provisioning (list, details, quota)
│   │   └── SyncPanel.vue       # Two-way sync folders (add/pause/remove, status)
│   ├── lib/ipc.ts              # Typed invoke() wrappers for every Rust command
│   ├── stores/                 # Pinia: accounts + files + sync state
│   └── App.vue                 # Shell: sidebar + Files/Sync/Admin tabs
├── src-tauri/                  # Rust backend
│   ├── src/
│   │   ├── main.rs / lib.rs    # App bootstrap, state injection, tray, CLI, command registry
│   │   ├── state.rs            # AppState: shared reqwest client + account + sync engine
│   │   ├── error.rs            # Serializable AppError (code + message) for the frontend
│   │   ├── accounts.rs         # OS keychain (keyring) + accounts.json persistence
│   │   ├── commands.rs         # All #[tauri::command] IPC endpoints
│   │   ├── flutcloud.rs        # FlutCloud-only enforcement (URL check + capability probe)
│   │   ├── sync.rs             # Two-way sync engine (journal, planner, worker)
│   │   └── nextcloud/
│   │       ├── mod.rs          # Shared auth request helper + URL/encoding utils
│   │       ├── webdav.rs       # PROPFIND, multistatus XML parsing, transfer helpers
│   │       └── ocs.rs          # OCS: user info, admin probe, user provisioning, share links
│   └── tauri.conf.json         # Tauri config & branding
├── android/                    # (planned) FlutLink mobile client
└── flutcloud-app/              # FlutCloud Nextcloud server app (non-standard server features)
    ├── appinfo/                # info.xml + OCS routes
    ├── lib/                    # Capabilities, ApiController, LinkService (resources/parts)
    └── composer.json           # OCA\FlutCloud autoloading
```

### FlutCloud-only policy

FlutLink connects exclusively to the server configured in the local `.env`
file (`FLUTCLOUD_URL`) and only to servers that run the FlutCloud Nextcloud app:

- The server URL is never hard-coded in source; the backend reads
  `FLUTCLOUD_URL` from `.env` (`src-tauri/src/flutcloud.rs`) and exposes it to
  the frontend via the `get_flutcloud_url` command.
- `account_add` / `register_user` reject other URLs
  (`AppError::NotFlutCloud`).
- Before connecting, the OCS capabilities endpoint is probed for the
  `flutcloud` capability (`AppError::FlutCloudAppMissing` if absent).
- Accounts persisted for other servers are dropped when the app starts.

### Security model

- **Tokens never touch the renderer or disk** in plaintext. They are stored in the OS keychain (Windows Credential Manager / macOS Keychain / Linux Secret Service) via the `keyring` crate, and kept in Rust-managed memory only (`AppState`).
- `accounts.json` (app-data dir) persists **metadata only** (username, instance URL, display name, admin flag, active flag). Tokens are rehydrated from the keychain on startup; accounts without a token are skipped.
- All commands return a serialized `AppError { code, message }`, surfaced as toasts/inline errors in the UI.

### Key commands

| Command | Backend | Purpose |
| --- | --- | --- |
| `account_add` | OCS `/cloud/user` + admin probe | Verify credentials, store token in keychain, add/activate account |
| `account_switch` / `account_remove` / `account_list` | state | Multi-account lifecycle |
| `webdav_list` | WebDAV `PROPFIND` (Depth 1) | Browse a folder; entries flagged `isResource` / `isPart` |
| `webdav_create_share` | OCS share API | Generate a public link, URL returned to frontend |
| `webdav_upload_file` / `webdav_download_file` / `webdav_delete` / `webdav_mkdir` / `webdav_rename` | WebDAV | File operations in the browser (admins may target another user) |
| `admin_list_users` / `admin_get_user` / `admin_set_user_quota` | OCS Provisioning API | Admin panel (admin accounts only) |
| `sync_list` / `sync_add` / `sync_remove` / `sync_set_paused` | `sync.rs` | Manage two-way sync folders |
| `sync_trigger` | `sync.rs` | Kick off a sync pass immediately |

### Two-way sync

Folders are mirrored to `/FlutLink/<folder>` on Nextcloud. A JSON journal
(`sync-journal-<id>.json` in the app-data dir) records the last-synced
local/remote `{size, mtime}` fingerprint per file; the background worker (10 s
interval) propagates local uploads, remote downloads and deletions. Conflicts
upload the local copy as `name (conflict copy).ext`.

### System tray & CLI

Closing the window hides FlutLink to the system tray instead of quitting; the
tray menu restores the window or quits the app.

```bash
flutlink --sync          # run a sync pass after startup
flutlink --path <dir>    # add a local folder to sync
flutlink --url           # open the login dialog (the FlutCloud server is fixed)
flutlink --tray          # start minimized to the system tray
```

## Development

Prerequisites: Node 20+, Rust 1.85+ (stable), and the [Tauri prerequisites](https://tauri.app/start/prerequisites/).

```bash
npm install
npm run tauri dev
```

Typecheck/build checks:

```bash
npm run build              # vue-tsc + vite build
cargo check                # Rust type check
cargo test                 # Rust unit tests (incl. WebDAV XML parser)
```

## Roadmap

- **Phase 1 (done):** Tauri v2 + Vite + Tailwind scaffold; Rust backend with keychain auth, multi-account state, WebDAV listing, OCS admin endpoints; account switcher UI.
- **Phase 2 (done):** Two-way sync engine with journal, background worker, sync panel; system tray + close-to-tray; CLI flags; official FlutLink/OperationFlut branding.
- **Phase 3:** Chunked uploads/downloads with progress events (`app.emit`), drag & drop, `resources`/`parts` dual-pane workflows, symlink/virtual-link resolution.
- **Phase 4:** Full provisioning UI (create/delete users, groups, impersonation) and quota presets, native notifications, offline cache.
