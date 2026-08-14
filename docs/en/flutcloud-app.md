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
| Nextcloud server | 28 – 31 |
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

## Installation

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
| `GET` | `/ping` | App info: `{ app, name, version, features, user }` |
| `GET` | `/links` | List virtual links (subfolders of `resources/`) |
| `POST` | `/links` | Create a virtual link (`name` form/query param) |
| `DELETE` | `/links/{name}` | Delete a virtual link |
| `GET` | `/parts` | List writable parts (subfolders of `parts/`) |
| `POST` | `/parts` | Create a writable part (`name` param) |
| `POST` | `/project-folder` | Ensure `/FlutLink/FlutCloud` (admin only) |

Link/part entries are returned as `{ name, path, readOnly }`.

## Troubleshooting

- **FlutLink rejects the server** (`FlutCloudAppMissing`) — the capability is
  not advertised. Check that the app is enabled
  (`php occ app:list | grep flutcloud`), that the directory is named
  `flutcloud`, and that the `curl` above returns
  `ocs.data.capabilities.flutcloud`.
- **`composer` command not found** — install Composer or skip step 2; the app
  runs without the generated autoloader.
- **App cannot be enabled** — verify the Nextcloud version is between 28 and
  31 and PHP is 8.1+ (see `<dependencies>` in `appinfo/info.xml`).
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
