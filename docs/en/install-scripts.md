# Install scripts (curl & iex)

FlutLink ships four install scripts under `scripts/` that can be run directly
from the GitHub repository without cloning it:

| Script | Target | Shell |
| --- | --- | --- |
| `install.sh` | auto-select: FlutCloud server app or FlutLink client | bash |
| `install-flutlink.ps1` | FlutLink desktop client (Windows, macOS, Linux) | PowerShell 7+ |
| `install-flutlink.sh` | FlutLink desktop client (macOS, Linux) | bash |
| `install-flutcloud-app.ps1` | FlutCloud Nextcloud app on the server | PowerShell 7+ |
| `install-flutcloud-app.sh` | FlutCloud Nextcloud app on the server (Ubuntu/Debian) | bash |

All of them are invoked the same way: the script text is streamed straight
from the raw GitHub URL into the shell and executed in memory — nothing is
saved to disk unless you do it yourself.

## Root `install.sh` wrapper

There is a single entry point at the repository root that picks the right
installer for you: it installs the FlutCloud Nextcloud app when it finds a
Nextcloud installation (a folder containing `occ` in the current directory,
an ancestor, or a common location like `~/nextcloud` or
`/var/www/nextcloud`), otherwise it installs the FlutLink desktop client:

```bash
curl -sSL https://raw.githubusercontent.com/OseMine/FlutLink/main/install.sh | bash
```

To force the server install (or point it at your Nextcloud), pass the path —
`--path` and `--nextcloud-root` are equivalent:

```bash
curl -sSL https://raw.githubusercontent.com/OseMine/FlutLink/main/install.sh | bash -s -- --path ~/nextcloud
```

Client options (e.g. `--tag`, `--no-run`) are passed through to
`install-flutlink.sh`.

## PowerShell: `iex (irm <url>)`

`irm` (Invoke-RestMethod) downloads the script text, `iex` (Invoke-Expression)
runs it:

```powershell
iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1)
```

```powershell
iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.ps1)
```

## PowerShell: `curl.exe ... | iex`

The classic Windows piping style — `curl.exe` (the real curl, **not** the
PowerShell `curl` alias for `Invoke-WebRequest`) downloads the script and
pipes it to `iex`:

```powershell
curl.exe -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1 | iex
```

```powershell
curl.exe -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.ps1 | iex
```

- `-s` silences curl's progress output, `-L` follows GitHub's redirects to the
  raw file. If you omit `-L`, GitHub redirects anyway, but `curl.exe` without
  `-L` will not follow them.
- In PowerShell, `curl` alone is an alias for `Invoke-WebRequest`; always write
  `curl.exe` in scripts so the real curl is used.

## bash: `curl ... | bash`

On macOS and Linux the equivalent one-liner is:

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.sh | bash
```

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.sh | bash
```

The bash scripts need a POSIX `bash` (the macOS system bash 3.2 works) plus
`curl`. The client script additionally needs `jq` or `python3` to read the
GitHub release metadata (either one is usually present on modern systems).

## What happens when you run them

1. `install-flutlink.*` queries the GitHub API for the latest release, picks
   the installer for your OS/architecture, downloads it, verifies its SHA-256
   digest and runs it (AppImage/.deb on Linux, `.dmg` on macOS, `.exe`/`.msi`
   on Windows).
2. `install-flutcloud-app.*` finds the Nextcloud installation, downloads the
   app as `flutcloud-app.zip` from the latest GitHub release, copies it into
   `apps/flutcloud`, enables the app with `occ` and verifies it. When run
   interactively (in a terminal) it first asks you to confirm the detected
   path or enter the path where you installed Nextcloud; piped (`curl | bash`)
   runs skip the prompt and use the detected path.

## Passing parameters

When piped directly, the scripts run with defaults (latest release, automatic
detection). To pass parameters, download the script to a file first and run
it:

```powershell
irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1 -OutFile install-flutlink.ps1
./install-flutlink.ps1 -Tag v1.0.0 -NoRun
```

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.sh -o install-flutlink.sh
./install-flutlink.sh --tag v1.0.0 --no-run
```

### `install-flutlink.*`

| PowerShell | bash | Meaning |
| --- | --- | --- |
| `-Tag v1.0.0` | `--tag v1.0.0` | Install a specific release instead of the latest |
| `-DownloadDir <dir>` | `--dir <dir>` | Directory for the downloaded installer |
| `-NoRun` | `--no-run` | Only download (and verify), do not install |
| `-NoVerify` | `--no-verify` | Skip the SHA-256 verification (not recommended) |

### `install-flutcloud-app.*`

| PowerShell | bash | Meaning |
| --- | --- | --- |
| `-NextcloudRoot <path>` | `--nextcloud-root <path>` | Nextcloud folder (contains `occ`); when not given it is auto-detected and confirmed interactively |
| `-Ref <tag-or-branch>` | `--ref <tag-or-branch>` | Install a specific release or branch: a release tag uses its `flutcloud-app.zip` asset (falling back to the tagged sources), a branch uses the current branch sources |
| `-WebUser <user>` | `--web-user <user>` | Web-server user (default `www-data`) |
| `-DockerContainer <id>` | `--docker-container <id>` | Run occ via `docker exec` |
| `-Composer` | `--composer` | Generate the Composer autoloader |
| `-NoSudo` | `--no-sudo` | Run occ/chown directly (as `www-data` or root) |
| `-SkipVerify` | `--skip-verify` | Skip the `app:list` check afterwards |

## Security notes

- The pipe runs whatever the URL returns. Always double-check the URL is the
  official `raw.githubusercontent.com/OseMine/FlutLink/...` path and review the
  script once before trusting it — save it to a file and read it, or
  `irm <url> | Get-Content` to inspect the text before running.
- The scripts only download from the GitHub Releases API and verify the
  SHA-256 digest published by GitHub; they never ask for passwords and never
  store credentials.

## Troubleshooting

- **`curl : The term 'curl' is not recognized`** → you are in PowerShell
  without `curl.exe`; use `iex (irm <url>)` instead.
- **`Invoke-RestMethod` fails on GitHub redirects / TLS** → make sure you are
  on PowerShell 7+ (`$PSVersionTable.PSVersion`) and that TLS 1.2+ is enabled.
- **Execution policy** — `iex (irm ...)` is not blocked by the execution
  policy (it is an expression, not a script file). If you saved the script to
  a file and it is blocked, run `./install-flutlink.ps1` after
  `Set-ExecutionPolicy -Scope Process Bypass` for the current session.
- **`jq`/`python3` missing** (Linux/macOS client script) → install one of
  them, e.g. `sudo apt install jq` on Ubuntu or `brew install jq` on macOS.
- **Permission denied while installing the server app** — the script
  elevates to `sudo` automatically when `nextcloud/apps` is not writable by
  your user (typical for `/var/www/nextcloud`); run it from an account with
  `sudo` rights, or pass `--no-sudo` when you are already running as
  `www-data` or root.
- **Proxy/offline server** → download the scripts and the assets manually and
  run them from files; the scripts have no additional network requirements
  beyond GitHub.

See [Getting started](getting-started.md) for the one-liners and
[FlutCloud app](flutcloud-app.md) for the server-side installation.
