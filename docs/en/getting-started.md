# Getting Started

FlutLink is the desktop client for the **FlutCloud** server built with
**Tauri v2** — a Rust backend that speaks WebDAV and OCS directly, and a Vue 3
+ TypeScript + Tailwind frontend. FlutLink only connects to the FlutCloud
server (which must run the `flutcloud` Nextcloud app); it is not a generic
Nextcloud client.

## Installing FlutLink

The easiest way to install the latest FlutLink release on Windows, macOS or
Linux is the install script (PowerShell 7+):

```powershell
iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1)
```

or with `curl`:

```powershell
curl.exe -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1 | iex
```

On macOS and Linux the native bash installer works without PowerShell:

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.sh | bash
```

The scripts download the installer for your platform from the latest GitHub
release, verify its SHA-256 checksum and run it. To pick a specific release or
only download the installer, save the script and pass parameters:

```powershell
irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1 -OutFile install-flutlink.ps1
./install-flutlink.ps1 -Tag v0.1.0 -NoRun
```

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.sh -o install-flutlink.sh
./install-flutlink.sh --tag v0.1.0 --no-run
```

For how the `curl | iex` / `curl | bash` one-liners work, the available
options and troubleshooting, see [Install scripts](install-scripts.md).

The server must run the `flutcloud` Nextcloud app — install it with
[`install-flutcloud-app.ps1`](flutcloud-app.md) (PowerShell) or
[`install-flutcloud-app.sh`](flutcloud-app.md) (bash, Ubuntu/Debian) — and the
FlutCloud server URL must be configured via `FLUTCLOUD_URL` (see
[FlutCloud account](#adding-a-flutcloud-account)).

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

## Adding a FlutCloud account

1. Create an **app password** in FlutCloud:
   *Settings → Security → App passwords*.
2. Open FlutLink → **Sign In**.
3. Enter your username and the app password. The server is fixed to the
   `FLUTCLOUD_URL` configured in your local `.env`.
4. The token is stored in your OS keychain (Windows Credential Manager, macOS
   Keychain, Linux Secret Service).

The account is probed against `ocs/v2.php/cloud/user` and marked as **admin**
when the user belongs to an admin group.

## What to try next

- Browse your cloud files in the **Files** tab.
- Add a local folder in the **Sync** tab — it will be mirrored to
  `/FlutLink/<folder>`.
- Use the command line: `flutlink --sync` or `flutlink --path <dir>`
  (see [Tray & CLI](tray-and-cli.md)).
- Connect an admin account and open the **Admin** tab.

## Production build

```bash
npm run build             # vue-tsc + vite build (frontend check)
cargo build --release --manifest-path src-tauri/Cargo.toml
npm run tauri build       # full bundled app
```
