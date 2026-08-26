# FlutCloud — Nextcloud App

## English

The **FlutCloud** Nextcloud app is the server-side companion of the
[FlutLink](../README.md) desktop client. It provides the **non-standard
features** of the FlutCloud server — the parts that vanilla Nextcloud does not
offer — and it is what tells FlutLink "this is a FlutCloud server, not just any
Nextcloud".

FlutLink connects **exclusively** to servers that run this app. Before any
account is added it queries the OCS capabilities endpoint
(`/ocs/v2.php/cloud/capabilities?format=json`) and refuses to continue unless
the `flutcloud` capability is announced.

## Attribution

The whole FlutCloud app and the server are **managed** and all software is
**developed** by [marcante_musik](https://instagram.com/marcante_musik).
The capability payload and the ping endpoint announce this via
`managed_by` / `managed_by_url`.

## Requirements

| Requirement | Version |
| --- | --- |
| Nextcloud server | 28 – 37 |
| PHP | 8.1+ |

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
| iOS AltStore source | `GET /apps/flutcloud/ios/classic` — always redirects to the source JSON of the latest FlutLink GitHub release |

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

### Complete public shares ("Gast-Zugriff")

A folder counts as *completely public* when its owner granted a password-free
link share on it. Guests browse these folders without any account — strictly
read-only; there is no write path in the API and the underlying read-only link
permissions are enforced by Nextcloud itself. Every request resolves the share
live: shares that get deleted, password-protected or expired disappear from
the guest view immediately. Locked subfolders (recursive) answer 404 — also
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

Public endpoint that hands out the latest FlutLink AltStore Classic source JSON
for iOS sideloading. No authentication is required, so it can be added in
AltStore directly under *Sources → +*:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/apps/flutcloud/ios` | Lists sources with their current target URLs |
| `GET` | `/apps/flutcloud/ios/classic` | 302 to the latest AltStore **Classic** source JSON |

The target is resolved on demand: the app queries
`https://api.github.com/repos/OseMine/FlutLink/releases/latest` (cached for
10 minutes) and redirects to the `classic.json` asset of that
release. If GitHub is unreachable or rate-limits the server, the endpoint
falls back to the copy committed to `main`, so it keeps working.

### Short URL (`/ios/classic`)

Nextcloud only serves app routes below `/apps/flutcloud/…`. To answer at the
server root as well, rewrite the path internally to the app route:

nginx (server block):

```nginx
location = /ios/classic {
    rewrite ^ /index.php/apps/flutcloud/ios/classic last;
}
```

Apache (vhost):

```apache
RewriteEngine On
RewriteRule ^/ios/classic$ /index.php/apps/flutcloud/ios/classic [PT,L]
```

Apache (`.htaccess` in the Nextcloud root; note that Nextcloud may regenerate
this file during upgrades):

```apache
RewriteEngine On
RewriteRule ^ios/classic$ index.php/apps/flutcloud/ios/classic [END]
```

## Installation

1. Copy this directory into `nextcloud/apps/flutcloud/` on the server.
2. Run `php composer.phar install --no-dev` inside it (or
   `php occ app:enable flutcloud` if the app has no Composer dependencies —
   the autoloader is only needed for the OCA namespace).
3. `php occ app:enable flutcloud`.

Check that the capability is served:

```bash
curl -u alice:apptoken "https://YOUR-SERVER/ocs/v2.php/cloud/capabilities?format=json"
```

`ocs.data.capabilities.flutcloud` must be present.

## Development

```bash
composer check   # php -l lint on all source files
```

Keep `appinfo/info.xml`, `composer.json` and the `version` returned by `/ping`
in sync when bumping the version.

---

## Deutsch

Die **FlutCloud**-Nextcloud-App ist die serverseitige Ergänzung des
[FlutLink](../README.md)-Desktop-Clients. Sie liefert die
**Nicht-Standard-Funktionen** des FlutCloud-Servers — die Teile, die Vanilla-
Nextcloud nicht bietet — und sie sagt FlutLink: „Das ist ein FlutCloud-Server,
nicht irgendein Nextcloud."

FlutLink verbindet sich **ausschließlich** mit Servern, die diese App
ausführen. Vor jedem Konto-Login fragt der Client den OCS-Capabilities-Endpoint
(`/ocs/v2.php/cloud/capabilities?format=json`) ab und bricht ab, wenn die
`flutcloud`-Capability nicht angekündigt wird.

## Urheberschaft

Die gesamte FlutCloud-App und der Server werden von
[marcante_musik](https://instagram.com/marcante_musik) **verwaltet**; die
gesamte Software stammt aus ihrer/seiner **Entwicklung**. Capability-Payload
und Ping-Endpoint kündigen dies über `managed_by` / `managed_by_url` an.

## Voraussetzungen

| Voraussetzung | Version |
| --- | --- |
| Nextcloud-Server | 28 – 37 |
| PHP | 8.1+ |

Die App selbst hat keine Laufzeit-PHP-Abhängigkeiten; der Composer-Autoloader
wird nur für den `OCA\FlutCloud`-Namespace gebraucht (PSR-4 → `lib/`).

## Funktionen

| Feature | Was es tut |
| --- | --- |
| Capability | Kündigt `ocs.data.capabilities.flutcloud` an — der FlutCloud-Server-Marker |
| Ping | `GET /ocs/v2.php/apps/flutcloud/api/v1/ping` — App-Info zur Client-Prüfung |
| Virtuelle Links | Schreibgeschützte `resources/`-Ordner, verwaltet über die Links-API |
| Schreibbare Parts | Beschreibbare `parts/`-Ordner, verwaltet über die Parts-API |
| Projektordner | `/FlutLink/FlutCloud` im Admin-Home mit zweisprachiger README |
| iOS-AltStore-Quelle | `GET /apps/flutcloud/ios/classic` — leitet immer zur Quell-JSON des neuesten FlutLink-GitHub-Releases weiter |

## API

Alle Routen liegen unter `/ocs/v2.php/apps/flutcloud/api/v1` und benötigen
einen angemeldeten Benutzer (außer dem Capabilities-Endpoint, der öffentlich
ist):

| Methode | Pfad | Beschreibung |
| --- | --- | --- |
| `GET` | `/ping` | App-Info: `{ app, name, version, features, user, managed_by, managed_by_url }` |
| `GET` | `/links` | Virtuelle Links auflisten (Unterordner von `resources/`) |
| `POST` | `/links` | Virtuellen Link erstellen (`name` als Form/Query-Parameter) |
| `DELETE` | `/links/{name}` | Virtuellen Link löschen |
| `GET` | `/parts` | Schreibbare Parts auflisten (Unterordner von `parts/`) |
| `POST` | `/parts` | Schreibbaren Part erstellen (`name`-Parameter) |
| `POST` | `/project-folder` | `/FlutLink/FlutCloud` sicherstellen (nur Admin) |

Link-/Part-Einträge werden als `{ name, path, readOnly }` zurückgegeben.

## iOS-/AltStore-Classic-Quelle

Öffentlicher Endpoint, der die neueste FlutLink-AltStore-Classic-Quell-JSON
fürs iOS-Sideloading ausliefert. Sie braucht keine Authentifizierung und lässt
sich in AltStore direkt unter *Quellen → +* hinzufügen:

| Methode | Pfad | Beschreibung |
| --- | --- | --- |
| `GET` | `/apps/flutcloud/ios` | Listet die Quellen mit ihren aktuellen Ziel-URLs |
| `GET` | `/apps/flutcloud/ios/classic` | 302 zur neuesten AltStore-**Classic**-Quell-JSON |

Das Ziel wird bei Bedarf aufgelöst: Die App fragt
`https://api.github.com/repos/OseMine/FlutLink/releases/latest` ab (10 Minuten
gecacht) und leitet zum `classic.json`-Asset dieses Releases weiter.
Ist GitHub nicht erreichbar oder drosselt es den Server, greift der Endpoint
auf die in `main` eingecheckte Kopie zurück und bleibt so nutzbar.

### Kurze URL (`/ios/classic`)

Nextcloud bedient App-Routen nur unterhalb von `/apps/flutcloud/…`. Damit sie
auch an der Serverwurzel antwortet, wird dieser Pfad intern auf die
App-Route umgeschrieben:

nginx (Server-Block):

```nginx
location = /ios/classic {
    rewrite ^ /index.php/apps/flutcloud/ios/classic last;
}
```

Apache (Vhost):

```apache
RewriteEngine On
RewriteRule ^/ios/classic$ /index.php/apps/flutcloud/ios/classic [PT,L]
```

Apache (`.htaccess` im Nextcloud-Stammverzeichnis; Hinweis: Nextcloud kann
diese Datei bei Upgrades neu erzeugen):

```apache
RewriteEngine On
RewriteRule ^ios/classic$ index.php/apps/flutcloud/ios/classic [END]
```

## Installation

1. Diesen Ordner nach `nextcloud/apps/flutcloud/` auf dem Server kopieren.
2. `php occ app:enable flutcloud` ausführen (Composer-Dependencies werden nur
   für die OCA-Namespace-Autoloading gebraucht).
3. Prüfen, ob die Capability ausgeliefert wird:

```bash
curl -u alice:apptoken "https://YOUR-SERVER/ocs/v2.php/cloud/capabilities?format=json"
```

`ocs.data.capabilities.flutcloud` muss vorhanden sein.

## Entwicklung

```bash
composer check   # php -l Lint aller Quelldateien
```

Bei Versionsanhebung `appinfo/info.xml`, `composer.json` und die von `/ping`
gelieferte `version` synchron halten.
