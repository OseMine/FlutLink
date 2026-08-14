# Features

FlutLink combines four work areas in one window: file browsing, sharing,
administration and two-way sync.

## Files tab

- WebDAV browser with `PROPFIND` (Depth 1) listings.
- Entries are flagged as **resource** or **part** (see the `resources`/`parts`
  conventions used on the FlutCloud server side) and shown as badges.
- One-click **link sharing** via the OCS share API — the public link is copied
  to the clipboard.
- **File operations**: upload, download / open (downloads to a temp file and
  opens it with the default app), new folder, rename, delete — via the toolbar,
  the context menu and multi-selection.
- View toggle (grid / list), sortable columns and multi-select.
- Multi-account support: switch accounts from the sidebar or the avatar menu.

## Admin tab

Only visible/enabled for accounts that are members of an admin group.

- List all users (OCS Provisioning API).
- Inspect user details and quota.
- Set user quotas and edit user attributes.
- Browse a user's files through **admin impersonation**: `webdav_list`
  accepts an optional `target_user`; the backend refuses the call for
  non-admins and sets the `Impersonate-User` header.

## Sync tab

- Add any local folder; its content is mirrored to `/FlutLink/<folder>` on the
  active FlutCloud account.
- Per-folder status: `idle`, `syncing`, `paused`, `error` with pending upload /
  download / delete counters and failures.
- Pause and resume individual folders; remove folders again.
- "Sync now" triggers an immediate pass; otherwise the background worker runs
  every 10 seconds and after relevant changes.

See [Sync engine](sync.md) for the details.

## System tray & CLI

- Closing the window hides FlutLink to the system tray instead of quitting.
- Tray menu: *Show FlutLink* / *Quit FlutLink*; left-clicking the icon also
  restores the window.
- Command-line flags cover headless-ish workflows:
  `--sync`, `--path <dir>`, `--tray`.

See [Tray & CLI](tray-and-cli.md).
