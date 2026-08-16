# Sync Engine

The sync engine lives in `src-tauri/src/sync.rs` and is owned by `AppState`
(`sync: Arc<SyncEngine>`). All file transfer reuses the WebDAV helpers in
`nextcloud/webdav.rs` (`put_file`, `get_file`, `delete`, `make_collection`).

## Concepts

- **Folder** — a local directory bound to a remote directory
  `/FlutLink/<name>` for a specific account. The folder id is a hex unix
  nanosecond timestamp; the account key is `"{username}@{instance_url}"`.
- **Journal** — a JSON file `sync-journal-<id>.json` in the app-data dir. Per
  relative path it stores the last-synced `{ size, mtime }` fingerprint of the
  local and the remote side (plus an `is_dir` flag). It is the source of truth
  for deciding what changed on either side. Statuses are seeded from the
  journal on startup, so sync folders show their persisted state immediately.
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
| empty dir | — | — | EnsureDir |
| — | empty dir | synced (dir) | DeleteRemoteDir |
| dir | file (unchanged) | — | MoveRemoteConflict |
| file (unchanged) | dir | — | MoveLocalConflict |

Deletions only propagate when the other side is unchanged since the journal,
which prevents accidental loss. Skipped patterns: dotfiles, `~$*`, trailing
`~`, `Thumbs.db`. Symbolic links are skipped by default; when a sync folder is
created with the "follow symlinks" option, links are dereferenced during
`walk_local` (with canonical-path cycle protection) so their targets sync
instead.

## Directories & type conflicts

- Empty local folders are mirrored remotely (`EnsureDir`); the journal records
  them as directory entries so a later remote deletion can propagate.
- Remote-only empty folders are only deleted when the journal knows them as
  synced directories (`DeleteRemoteDir`), and never when the remote folder
  still has children.
- Local folder vs. remote file and local file vs. remote folder are treated as
  conflicts, not as deletions: the colliding remote side is moved aside
  (`MoveRemoteConflict` / `MoveLocalConflict`) into a unique conflict copy.

## Conflict names

Conflicts use `name (conflict copy).ext` for files. Within one pass every
target is unique (`conflict_name_n`): if the copy target already exists (from
an earlier pass or a prior conflict), a numeric suffix `(conflict copy 2)`,
`(conflict copy 3)`, … is added so no copy overwrites another.

## Executors

- `exec_upload` — streams the file with `X-OC-MTime` set to the local mtime so
  the server timestamp matches, then records the journal.
- `exec_download` — streams to a temp file and atomically renames into place.
- `exec_delete_remote` / `exec_delete_local` — 404s are tolerated.
- `exec_upload_conflict` — uploads the local copy under a conflict name and
  records both sides, so the conflict never re-triggers.
- `exec_mkdir` — creates a remote folder and journals it as a directory entry.
- `exec_delete_remote_dir` — deletes a synced empty remote folder and removes
  its journal entry.
- `exec_move_remote_conflict` / `exec_move_local_conflict` — rename the
  colliding side aside (WebDAV `MOVE` / local `rename`) and record the new
  path in the journal.

## Statuses & events

`SyncFolderStatus` carries `state` (`idle | syncing | paused | error`),
pending counts, failures, last error and `lastSyncedAt`. The worker emits a
`sync-status` event (payload: all statuses) only when something changed. A
paused folder is skipped by passes; resuming sets the state back to `idle`
immediately.

## Worker

`spawn_worker` starts a tokio task that waits on the engine's
`tokio::sync::Notify` (fired by `sync_trigger`, `sync_add`, CLI `--sync`, and
any status change) and a 10-second interval, running `run_all` (all unpaused
folders) whenever a signal arrives. The CLI `--path` handler emits
`sync-folders-changed`, which the frontend listens for to reload the folder
list.

## IPC

Backend: `sync_list`, `sync_add` (canonicalizes the path, accepts an optional
`follow_symlinks` flag, rejects duplicate remote paths), `sync_remove`,
`sync_set_paused`, `sync_trigger` in `commands.rs`. Frontend: wrappers in
`src/lib/ipc.ts`, reactive state in `src/stores/sync.ts`, UI in
`src/components/SyncPanel.vue` (folder picker via `tauri-plugin-dialog`).
