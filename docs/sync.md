# Sync Engine Architecture

The sync engine lives in `src-tauri/src/sync.rs` and is owned by `AppState` as
`Arc<crate::sync::SyncEngine>` (built in `state.rs`, see `SyncFolder` /
`SyncFolderStatus` models). All file transfer reuses the WebDAV helpers in
`nextcloud/webdav.rs` (`put_file`, `get_file`, `delete`, `make_collection`).

## Concepts

- **Folder** — a local directory bound to a remote directory
  `/FlutLink/<name>` for a specific account. The folder id is a hex unix
  nanosecond timestamp; the account key is `"{username}@{instance_url}"`.
- **Journal** — a JSON file `sync-journal-<id>.json` in the app-data dir. Per
  relative path it stores the last-synced `{ size, mtime }` fingerprint of the
  local and the remote side. This is the source of truth for deciding what
  changed on either side.
- **Pass** — one planner run that scans local (iterative directory walk) and
  remote (BFS `PROPFIND`) state, produces a bounded list of actions
  (`MAX_OPS_PER_PASS = 200`), and executes them in dependency-safe order:
  `EnsureDir` before uploads, parents before children.

## Decision rules (`decide`)

| Local | Remote | Journal | Action |
| --- | --- | --- | --- |
| new | — | none | Upload |
| — | new | none | Download |
| changed | unchanged | synced | Upload |
| unchanged | changed | synced | Download |
| missing | unchanged | synced | DeleteRemote |
| unchanged | missing | synced | DeleteLocal |
| changed | changed | synced | UploadConflict (`name (conflict copy).ext`) |
| unchanged | unchanged | synced | Skip |

Deletions only propagate when the other side is unchanged since the journal,
which prevents accidental loss. Skipped patterns: dotfiles, `~$*`, trailing
`~`, `Thumbs.db`, symlinks.

## Executors

- `exec_upload` — streams the file with `X-OC-MTime` set to the local mtime so
  the server timestamp matches, then records the journal.
- `exec_download` — streams to a temp file and atomically renames into place.
- `exec_delete_remote` / `exec_delete_local` — 404s are tolerated.
- `exec_upload_conflict` — uploads the local copy under a conflict name and
  records both sides, so the conflict never re-triggers.

## Statuses & events

`SyncFolderStatus` carries `state` (`idle | syncing | paused | error`),
pending counts, failures, last error and `lastSyncedAt`. The worker emits a
`sync-status` event (payload: all statuses) only when something changed. A
paused folder is skipped by passes.

## Worker

`spawn_worker` starts a tokio task that waits on the engine's
`tokio::sync::Notify` (fired by `sync_trigger`, `sync_add`, CLI `--sync`, and
any status change) and a 10-second interval, running `run_all` (all unpaused
folders) whenever a signal arrives.

## IPC

Backend: `sync_list`, `sync_add` (canonicalizes the path, rejects duplicates),
`sync_remove`, `sync_set_paused`, `sync_trigger` in `commands.rs`. Frontend:
wrappers in `src/lib/ipc.ts`, reactive state in `src/stores/sync.ts`, UI in
`src/components/SyncPanel.vue` (folder picker via `tauri-plugin-dialog`).
