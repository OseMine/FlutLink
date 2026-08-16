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

## API

Alle Routen liegen unter `/ocs/v2.php/apps/flutcloud/api/v1` und benötigen
einen angemeldeten Benutzer (außer dem Capabilities-Endpoint, der öffentlich
ist):

| Methode | Pfad | Beschreibung |
| --- | --- | --- |
| `GET` | `/ping` | App-Info: `{ app, name, version, features, user }` |
| `GET` | `/links` | Virtuelle Links auflisten (Unterordner von `resources/`) |
| `POST` | `/links` | Virtuellen Link erstellen (`name` als Form/Query-Parameter) |
| `DELETE` | `/links/{name}` | Virtuellen Link löschen |
| `GET` | `/parts` | Schreibbare Parts auflisten (Unterordner von `parts/`) |
| `POST` | `/parts` | Schreibbaren Part erstellen (`name`-Parameter) |
| `POST` | `/project-folder` | `/FlutLink/FlutCloud` sicherstellen (nur Admin) |

Link-/Part-Einträge werden als `{ name, path, readOnly }` zurückgegeben.

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
