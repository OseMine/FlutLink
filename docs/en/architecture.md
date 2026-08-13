# Architecture

FlutLink is split into a Rust backend (`src-tauri/`) that owns all HTTP
traffic, and a Vue 3 frontend (`src/`) that renders state and dispatches typed
IPC calls. Running requests in Rust avoids CORS, allows custom HTTP methods
like `PROPFIND`, and keeps credentials out of the renderer.

## Module map

```
src/                              # Vue 3 + TypeScript + Tailwind v4
├── components/
│   ├── AccountBar.vue            # account switcher, storage widget, add/remove
│   ├── FileExplorer.vue          # WebDAV browser, resources/parts, link sharing
│   ├── AdminPanel.vue            # OCS user provisioning + impersonation
│   ├── SyncPanel.vue             # sync folder management + live status
│   ├── LoginModal.vue            # keychain-backed sign-in
│   ├── SettingsModal.vue         # language, theme, about
│   ├── AppLogo.vue / WelcomeScreen.vue  # FlutLink/OperationFlut branding
│   └── ToastStack.vue            # toast notifications
├── lib/
│   ├── ipc.ts                    # typed invoke() wrappers for every command
│   ├── i18n.ts                   # EN/DE dictionaries, translate()
│   └── format.ts                 # byte formatting helpers
├── stores/
│   ├── accounts.ts               # account list, active account, storage quota
│   ├── files.ts                  # WebDAV listing state
│   ├── sync.ts                   # sync folder statuses (subscribed to events)
│   └── ui.ts                     # language + theme, persisted to localStorage
└── App.vue                       # shell: sidebar + Files/Sync/Admin tabs

src-tauri/                        # Rust backend
├── src/
│   ├── main.rs / lib.rs          # bootstrap, plugins, tray, CLI, registry
│   ├── state.rs                  # AppState: reqwest client, accounts, sync engine
│   ├── error.rs                  # AppError/AppResult (JSON-serialized)
│   ├── accounts.rs               # accounts.json metadata + keyring tokens
│   ├── commands.rs               # all #[tauri::command] handlers
│   ├── flutcloud.rs              # FlutCloud-only enforcement (fixed server URL + capability probe)
│   ├── sync.rs                   # two-way sync engine (journal/planner/worker)
│   └── nextcloud/
│       ├── mod.rs                # auth request helper, URL/encoding utils
│       ├── webdav.rs             # PROPFIND + multistatus parsing, transfers
│       └── ocs.rs                # OCS: user info, admin probe, provisioning, shares
├── capabilities/default.json     # window permissions (core, opener, dialog)
└── tauri.conf.json               # app + CLI plugin config, bundling

flutcloud-app/                    # FlutCloud Nextcloud server app (PHP)
├── appinfo/                      # info.xml, OCS routes (api/v1/*)
├── lib/                          # Capabilities, ApiController, LinkService
└── composer.json                 # OCA\FlutCloud autoloading
```

## FlutCloud-only

FlutLink is **not** a generic Nextcloud client. It connects exclusively to the
FlutCloud server (the URL is read from `FLUTCLOUD_URL` in the local `.env`,
never hard-coded) and only when that server runs
the FlutCloud Nextcloud app (`flutcloud-app/`). `flutcloud.rs` rejects foreign
URLs (`AppError::NotFlutCloud`) and probes the OCS capabilities endpoint for
the `flutcloud` capability (`AppError::FlutCloudAppMissing`) before any
account is created.

## Frontend ↔ backend data flow

1. Components call **Pinia stores** (`src/stores/`).
2. Stores call typed wrappers in `src/lib/ipc.ts` (`api.*`).
3. Wrappers `invoke()` a Rust command registered in `lib.rs`.
4. Commands operate on `AppState` and return serde types (`camelCase`).
5. Errors cross the boundary as `AppError { code, message }`.

**Events** flow the other way with `app.emit`:

| Event | Payload | Purpose |
| --- | --- | --- |
| `sync-status` | `SyncFolderStatus[]` | push updated sync statuses to `stores/sync.ts` |
| `flutlink:cli-open` | `string` | open the login dialog with a server URL |
| `sync-folders-changed` | `()` | folder added via CLI → refresh sync panel |

## State model (`state.rs`)

`AppState` holds the account list, a shared `reqwest::Client`, and
`sync: Arc<SyncEngine>`. Serde models use `#[serde(rename_all = "camelCase")]`
so Rust `snake_case` maps to TS `camelCase` automatically.
