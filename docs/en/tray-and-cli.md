# Tray & CLI

FlutLink is a tray-first application: closing the window hides it to the
system tray instead of terminating, so sync keeps running in the background.

## System tray

- **Setup** — `setup_tray` in `src-tauri/src/lib.rs` creates a tray icon with
  a two-item menu (*Show FlutLink*, *Quit FlutLink*). The icon comes from the
  default window icon (regenerated from `app-icon.png` via
  `npm run tauri icon app-icon.png`).
- **Close to tray** — `on_window_event` intercepts `CloseRequested`. Unless a
  quit flag is set (tray *Quit*), the close is prevented and the window is
  hidden instead.
- **Restore** — the *Show* menu item and a left click on the tray icon both
  call `show_main_window` (unminimize + show + focus).
- **Quit** — the *Quit* menu item sets the quit flag and calls `app.exit(0)`.

## CLI flags

Configured in `tauri.conf.json` under `plugins.cli` and parsed in `handle_cli`
(no capabilities needed for Rust-side parsing).

| Flag | Short | Argument | Behaviour |
| --- | --- | --- | --- |
| `--sync` | `-s` | none | Run a sync pass after startup (`sync.notify_one()`) |
| `--path` | `-p` | directory | Add a local folder to the two-way sync |
| `--url` | `-u` | URL (ignored) | Open the login dialog for the FlutCloud server |
| `--tray` | `-t` | none | Start minimized to the system tray |

Examples:

```bash
flutlink --tray --sync                 # start silently and sync once
flutlink --path "C:\Users\me\Documents"  # add a sync folder
flutlink --url             # open sign-in (the FlutCloud server is fixed)
```

Frontend interaction:

- `--url` emits `flutlink:cli-open`; `App.vue` listens and opens `LoginModal`.
  The server URL is always the FlutCloud server (`src/lib/config.ts`) — FlutLink
  is a dedicated client and never connects elsewhere.
- `--path` runs `commands::sync_add` and emits `sync-folders-changed` so the
  sync panel refreshes.
