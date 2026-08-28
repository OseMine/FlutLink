# FlutCloud Nextcloud App

The **FlutCloud** Nextcloud app (`flutcloud-app/` in this repository) is the
server-side companion of the FlutLink desktop client. It provides the
**non-standard features** of the FlutCloud server — the parts vanilla
Nextcloud does not offer — and it is what tells FlutLink "this is a FlutCloud
server, not just any Nextcloud".

FlutLink connects **exclusively** to servers that run this app. Before any
account is added it queries the OCS capabilities endpoint
(`/ocs/v2.php/cloud/capabilities?format=json`) and refuses to continue unless
the `flutcloud` capability is announced (`AppError::FlutCloudAppMissing`
otherwise).

## Requirements

| Requirement | Version |
| --- | --- |
| Nextcloud server | 28 – 37 |
| PHP | 8.1+ |
| Composer | optional — only to generate the OCA autoloader |

The app itself has no runtime PHP dependencies; the Composer autoloader is
only needed for the `OCA\FlutCloud\` namespace (PSR-4 → `lib/`).

## Features

| Feature | What it does |
| --- | --- |
| Capability | Advertises `ocs.data.capabilities.flutcloud` — the FlutCloud server marker |
| Ping | `GET /ocs/v2.php/apps/flutcloud/api/v1/ping` — app info for client verification |
| Virtual links | Read-only `resources/` folders, managed via the links API |
| Writable parts | Write-enabled `parts/` folders, managed via the parts API |
| Project folder | `/FlutLink/FlutCloud` in the admin home with a bilingual README |
| Complete public shares | Anonymous, strictly read-only guest access to folders shared publicly as a whole, with categories and recursive subfolder locks |
| iOS AltStore Classic source | `GET /apps/flutcloud/ios/classic` — always redirects to the source JSON of the latest FlutLink GitHub release |

## Installation

### Via install script

Run the following on the machine that hosts the Nextcloud server (Ubuntu/Debian
or any Linux with the `occ` script). The script detects the Nextcloud
installation (or accepts `--nextcloud-root`), downloads the app from the
repository into `nextcloud/apps/flutcloud`, enables it with `occ` and verifies
it:

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-nextcloud.sh | bash
```

Save the script to a file first to pass parameters — for example for the
official Nextcloud Docker image, or to generate the Composer autoloader:

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-nextcloud.sh -o install-nextcloud.sh
./install-nextcloud.sh --docker-container nextcloud --composer
```

Useful parameters: `--nextcloud-root <path>` (detected automatically
otherwise), `--ref <tag-or-branch>` (defaults to the latest release),
`--web-user <user>` (default `www-data`), `--no-sudo`, `--skip-verify`,
`--docker-container` and `--composer`. For how the `curl | bash` pattern
works, options and troubleshooting, see [Install scripts](install-scripts.md).
The manual steps below are equivalent to what the script does.

### Manual installation

1. **Copy the app** into the Nextcloud apps directory on the server:

   ```bash
   cp -r flutcloud-app nextcloud/apps/flutcloud
   ```

   The directory must be named `flutcloud` (it must match the `<id>` in
   `appinfo/info.xml`).

2. **Generate the autoloader** (optional but recommended):

   ```bash
   cd nextcloud/apps/flutcloud
   php composer.phar install --no-dev
   ```

   If Composer is not available, the app still works — Nextcloud's own
   autoloading handles the `OCA\FlutCloud\` namespace via `appinfo/info.xml`
   and the app code under `lib/`.

3. **Enable the app**:

   ```bash
   cd /var/www/nextcloud
   sudo -u www-data php occ app:enable flutcloud
   ```

   (Replace `www-data` with your web-server user; on the official
   `nextcloud` Docker image use `docker exec -u www-data nextcloud php occ
   app:enable flutcloud`.)

4. **Verify** that the capability is served:

   ```bash
   curl -u alice:apptoken "https://YOUR-SERVER/ocs/v2.php/cloud/capabilities?format=json"
   ```

   `ocs.data.capabilities.flutcloud` must be present. FlutLink will now
   accept the server as a FlutCloud instance.

## API

All routes are under `/ocs/v2.php/apps/flutcloud/api/v1` and require an
authenticated user (except the capabilities endpoint, which is public):

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/ping` | App info: `{ app, name, version, features, user, managed_by, managed_by_url }` |
| `GET` | `/links` | List virtual links (subfolders of `resources/`) |
| `POST` | `/links` | Create a virtual link (`name` form/query param) |
| `DELETE` | `/links/{name}` | Delete a virtual link |
| `GET` | `/parts` | List writable parts (subfolders of `parts/`) |
| `POST` | `/parts` | Create a writable part (`name` param) |
| `POST` | `/project-folder` | Ensure `/FlutLink/FlutCloud` (admin only) |
| `GET` | `/public` | **Guest:** every complete public share, bundled (`{ shares, categories }`) |
| `GET` | `/public/categories` | **Guest:** all configured categories |
| `GET` | `/public/{token}` | **Guest:** list a folder of one share (`path` query param); 404 for missing/locked paths |
| `POST` | `/public/categories` | Admin: create/update a category (`name`, optional `prefixless`) |
| `DELETE` | `/public/categories/{name}` | Admin: delete a category |
| `POST` / `DELETE` | `/public/shares/{token}/category` | Admin: assign/remove a share's category (`category` param) |
| `POST` / `DELETE` | `/public/shares/{token}/lock` | Admin: lock/unlock a subfolder recursively (`path` param) |

Link/part entries are returned as `{ name, path, readOnly }`.

### Complete public shares ("guest access")

A folder counts as *completely public* when its owner granted a password-free
link share on it. Guests browse these folders without any account — strictly
read-only; there is no write path in the API and the underlying read-only link
permissions are enforced by Nextcloud itself. Every request resolves the share
live: shares that get deleted, password-protected or expired disappear from the
guest view immediately. Locked subfolders (recursive) answer 404 — also
for direct path manipulation.

Web routes mirror the guest API without authentication:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/apps/flutcloud/public` | All complete public shares |
| `GET` | `/apps/flutcloud/public/{category}` | Shares of one category |
| `GET` | `/apps/flutcloud/{category}` | Same, only for categories where the admin dropped the `/public/` prefix |

Downloads run through Nextcloud's standard public WebDAV endpoint:
`/public.php/webdav/<token>/<path>` with basic auth (`<token>` as username,
empty password). A Sabre plugin (`GuestLockPlugin`) watches that endpoint so
locked subfolders answer 404 there too — path manipulation cannot bypass the
lock list.

Contract tests (no live server required):

```bash
php flutcloud-app/tests/capability-contract.php
php flutcloud-app/tests/public-share-contract.php
```

## iOS / AltStore Classic source

Public endpoint (no authentication) that hands out the latest FlutLink
AltStore Classic source JSON for iOS sideloading — add it in AltStore under
*Sources → +*:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/apps/flutcloud/ios` | Lists sources with their current target URLs |
| `GET` | `/apps/flutcloud/ios/classic` | 302 to the latest AltStore **Classic** source JSON |

The target is resolved on demand: the app queries the GitHub releases API
(cached for 10 minutes) and redirects to the `classic.json`
asset of the latest release; if GitHub is unreachable or rate-limited it
falls back to the copy committed to `main`. To also serve it at the
server root (`/ios/classic`), add one of the web-server rewrite
snippets from the [app README](../../flutcloud-app/README.md#ios--altstore-classic-source).

## Troubleshooting

- **FlutLink rejects the server** (`FlutCloudAppMissing`) — the capability is
  not advertised. Check that the app is enabled
  (`php occ app:list | grep flutcloud`), that the directory is named
  `flutcloud`, and that the `curl` above returns
  `ocs.data.capabilities.flutcloud`.
- **`composer` command not found** — install Composer or skip step 2; the app
  runs without the generated autoloader.
- **App cannot be enabled** — verify the Nextcloud version is between 28 and
  37 and PHP is 8.1+ (see `<dependencies>` in `appinfo/info.xml`). If the
  message says *"cannot be installed because it is not compatible with this
  version of the server"*, the server is newer than the declared
  `max-version`; bump it in `appinfo/info.xml`.
- **`Nextcloud or one of the apps require upgrade`** — Nextcloud refuses most
  `occ` commands until the database is upgraded. Run `sudo -u www-data php
  occ upgrade` once (or use the web UI). The install script does this
  automatically and then retries `app:enable`.
- **Permission errors** — ensure the app files are owned by the web-server
  user and `nextcloud/apps/` is writable.

## Development

```bash
composer check   # php -l lint on all source files
```

Keep `appinfo/info.xml`, `composer.json` and the `version` returned by `/ping`
in sync when bumping the version. See
[Development](development.md) for the repository-wide verification flow.

## See also

- [Getting started](getting-started.md) — the FlutLink client
- [Features](features.md) — what FlutLink offers
- [Security](security.md) — how the capability check protects the client
