# Getting Started

FlutLink is a desktop client for Nextcloud built with **Tauri v2** — a Rust
backend that speaks WebDAV and OCS directly, and a Vue 3 + TypeScript +
Tailwind frontend.

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| Node.js | 20+ | npm is used for the frontend |
| Rust | 1.85+ (stable) | via rustup |
| Tauri prerequisites | — | Platform tooling, see [tauri.app](https://tauri.app/start/prerequisites/) |

On Windows this includes the MSVC build tools and WebView2. On Linux, the
webkit2gtk system packages are required.

## Development setup

```bash
npm install          # frontend dependencies
npm run tauri dev    # starts Vite (port 1420) + the Rust app
```

The app opens a 1200×800 window. The first time you run it you can either sign
in with an account or just explore the welcome screen.

## Adding a Nextcloud account

1. Create an **app password** in Nextcloud:
   *Settings → Security → App passwords*.
2. Open FlutLink → **Sign In**.
3. Enter the server URL, your username and the app password.
4. The token is stored in your OS keychain (Windows Credential Manager, macOS
   Keychain, Linux Secret Service).

The account is probed against `ocs/v2.php/cloud/user` and marked as **admin**
when the user belongs to an admin group.

## What to try next

- Browse your cloud files in the **Files** tab.
- Add a local folder in the **Sync** tab — it will be mirrored to
  `/FlutLink/<folder>`.
- Use the command line: `flutlink --sync`, `flutlink --path <dir>`,
  `flutlink --url <server>` (see [Tray & CLI](tray-and-cli.md)).
- Connect an admin account and open the **Admin** tab.

## Production build

```bash
npm run build             # vue-tsc + vite build (frontend check)
cargo build --release --manifest-path src-tauri/Cargo.toml
npm run tauri build       # full bundled app
```
