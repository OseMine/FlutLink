# FlutCloud-Nextcloud-App

Die **FlutCloud**-Nextcloud-App (`flutcloud-app/` in diesem Repository) ist
die serverseitige Ergänzung des FlutLink-Desktop-Clients. Sie liefert die
**Nicht-Standard-Funktionen** des FlutCloud-Servers — die Teile, die Vanilla-
Nextcloud nicht bietet — und sie sagt FlutLink: „Das ist ein FlutCloud-Server,
nicht irgendein Nextcloud."

FlutLink verbindet sich **ausschließlich** mit Servern, die diese App
ausführen. Vor jedem Konto-Login fragt der Client den OCS-Capabilities-Endpoint
(`/ocs/v2.php/cloud/capabilities?format=json`) ab und bricht ab, wenn die
`flutcloud`-Capability nicht angekündigt wird (`AppError::FlutCloudAppMissing`).

## Voraussetzungen

| Voraussetzung | Version |
| --- | --- |
| Nextcloud-Server | 28 – 37 |
| PHP | 8.1+ |
| Composer | optional — nur für den OCA-Autoloader |

Die App selbst hat keine Laufzeit-PHP-Abhängigkeiten; der Composer-Autoloader
wird nur für den `OCA\FlutCloud\`-Namespace gebraucht (PSR-4 → `lib/`).

## Funktionen

| Feature | Was es tut |
| --- | --- |
| Capability | Kündigt `ocs.data.capabilities.flutcloud` an — der FlutCloud-Server-Marker |
| Ping | `GET /ocs/v2.php/apps/flutcloud/api/v1/ping` — App-Info zur Client-Prüfung |
| Virtuelle Links | Schreibgeschützte `resources/`-Ordner, verwaltet über die Links-API |
| Schreibbare Parts | Beschreibbare `parts/`-Ordner, verwaltet über die Parts-API |
| Projektordner | `/FlutLink/FlutCloud` im Admin-Home mit zweisprachiger README |
| Vollständige öffentliche Freigaben | Anonymer, streng schreibgeschützter Gastzugriff auf Ordner mit passwortfreier Linkfreigabe, mit Kategorien und rekursiven Unterordner-Locks |
| iOS-AltStore-Classic-Quelle | `GET /apps/flutcloud/ios/classic` — leitet immer zur Quell-JSON des neuesten FlutLink-GitHub-Releases weiter |

## Installation

### Via Installationsskript

Führe Folgendes auf dem Rechner aus, der den Nextcloud-Server hostet
(PowerShell 7+). Das Skript findet die Nextcloud-Installation automatisch
(oder akzeptiert `-NextcloudRoot`), lädt die App aus dem Repository nach
`nextcloud/apps/flutcloud` herunter, aktiviert sie mit `occ` und prüft sie:

```powershell
iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.ps1)
```

oder mit `curl`:

```powershell
curl.exe -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.ps1 | iex
```

Auf Ubuntu-/Debian-Servern funktioniert auch das native Bash-Installationsskript
ohne PowerShell:

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.sh | bash
```

Speichere das Skript zuerst in eine Datei, um Parameter zu übergeben — zum
Beispiel für das offizielle Nextcloud-Docker-Image oder um den
Composer-Autoloader zu erzeugen:

```powershell
irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.ps1 -OutFile install-flutcloud-app.ps1
./install-flutcloud-app.ps1 -DockerContainer nextcloud -Composer
```

Nützliche Parameter: `-NextcloudRoot <Pfad>` (sonst automatisch erkannt),
`-Ref <Tag-oder-Branch>` (Standard: letztes Release), `-WebUser <Benutzer>`
(Standard: `www-data`), `-NoSudo`, `-SkipVerify` (Bash-Pendants:
`--nextcloud-root`, `--ref`, `--web-user`, `--no-sudo`, `--skip-verify`, plus
`--docker-container` und `--composer`). Wie die `curl | iex`- /
`curl | bash`-Muster funktionieren, welche Optionen es gibt und wie du
Probleme behebst, findest du unter
[Installationsskripte](install-scripts.md). Die manuellen Schritte unten
entsprechen dem, was die Skripte tun.

### Manuelle Installation

1. **App kopieren** in das Nextcloud-Apps-Verzeichnis auf dem Server:

   ```bash
   cp -r flutcloud-app nextcloud/apps/flutcloud
   ```

   Das Verzeichnis muss `flutcloud` heißen (es muss der `<id>` in
   `appinfo/info.xml` entsprechen).

2. **Autoloader erzeugen** (optional, aber empfohlen):

   ```bash
   cd nextcloud/apps/flutcloud
   php composer.phar install --no-dev
   ```

   Ist Composer nicht verfügbar, funktioniert die App trotzdem — Nextclouds
   eigenes Autoloading übernimmt den `OCA\FlutCloud\`-Namespace über
   `appinfo/info.xml` und den Code unter `lib/`.

3. **App aktivieren**:

   ```bash
   cd /var/www/nextcloud
   sudo -u www-data php occ app:enable flutcloud
   ```

   (`www-data` durch den Webserver-Benutzer ersetzen; beim offiziellen
   `nextcloud`-Docker-Image `docker exec -u www-data nextcloud php occ
   app:enable flutcloud` verwenden.)

4. **Prüfen**, dass die Capability ausgeliefert wird:

   ```bash
   curl -u alice:apptoken "https://YOUR-SERVER/ocs/v2.php/cloud/capabilities?format=json"
   ```

   `ocs.data.capabilities.flutcloud` muss vorhanden sein. FlutLink akzeptiert
   den Server damit als FlutCloud-Instanz.

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
| `GET` | `/public` | **Gast:** Jede vollständige öffentliche Freigabe, gebündelt (`{ shares, categories }`) |
| `GET` | `/public/categories` | **Gast:** Alle konfigurierten Kategorien |
| `GET` | `/public/{token}` | **Gast:** Ordner einer Freigabe auflisten (`path` Query-Parameter); 404 für fehlende/gesperrte Pfade |
| `POST` | `/public/categories` | Admin: Kategorie erstellen/aktualisieren (`name`, optional `prefixless`) |
| `DELETE` | `/public/categories/{name}` | Admin: Kategorie löschen |
| `POST` / `DELETE` | `/public/shares/{token}/category` | Admin: Freigabe einer Kategorie zuweisen/entfernen (`category`-Parameter) |
| `POST` / `DELETE` | `/public/shares/{token}/lock` | Admin: Unterordner rekursiv sperren/entsperren (`path`-Parameter) |

Link-/Part-Einträge werden als `{ name, path, readOnly }` zurückgegeben.

### Vollständige öffentliche Freigaben („Gastzugriff")

Ein Ordner gilt als *vollständig öffentlich*, wenn der Eigentümer eine
passwortfreie Linkfreigabe darauf erteilt hat. Gäste durchsuchen diese Ordner
ohne Konto — streng schreibgeschützt; es gibt keinen Schreibpfad in der API
und die darunterliegenden schreibgeschützten Link-Berechtigungen werden von
Nextcloud selbst durchgesetzt. Jeder Request löst die Freigabe live auf:
Gelöschte, passwortgeschützte oder abgelaufene Freigaben verschwinden
sofort aus der Gästansicht. Gesperrte Unterordner (rekursiv) antworten mit
404 — auch bei direkter Pfad-Manipulation.

Web-Routen spiegeln die Gast-API ohne Authentifizierung:

| Methode | Pfad | Beschreibung |
| --- | --- | --- |
| `GET` | `/apps/flutcloud/public` | Alle vollständigen öffentlichen Freigaben |
| `GET` | `/apps/flutcloud/public/{category}` | Freigaben einer Kategorie |
| `GET` | `/apps/flutcloud/{category}` | Dasselbe, nur für Kategorien, bei denen der Admin das `/public/`-Präfix entfernt hat |

Downloads laufen über Nextclouds Standard-Public-WebDAV-Endpunkt:
`/public.php/webdav/<token>/<path>` mit Basic-Auth (`<token>` als Benutzername,
leeres Passwort). Ein Sabre-Plugin (`GuestLockPlugin`) überwacht diesen
Endpunkt, sodass gesperrte Unterordner dort ebenfalls mit 404 antworten —
Pfad-Manipulation kann die Lock-Liste nicht umgehen.

Vertrags-Tests (kein laufender Server nötig):

```bash
php flutcloud-app/tests/capability-contract.php
php flutcloud-app/tests/public-share-contract.php
```

## iOS-/AltStore-Classic-Quelle

Öffentlicher Endpoint (ohne Authentifizierung), der die neueste
FlutLink-AltStore-Classic-Quell-JSON fürs iOS-Sideloading ausliefert — in
AltStore direkt unter *Quellen → +* hinzufügen:

| Methode | Pfad | Beschreibung |
| --- | --- | --- |
| `GET` | `/apps/flutcloud/ios` | Listet die Quellen mit ihren aktuellen Ziel-URLs |
| `GET` | `/apps/flutcloud/ios/classic` | 302 zur neuesten AltStore-**Classic**-Quell-JSON |

Das Ziel wird bei Bedarf aufgelöst: Die App fragt die GitHub-Releases-API
ab (10 Minuten gecacht) und leitet zum `classic.json`-Asset des
neuesten Releases weiter; ist GitHub nicht erreichbar oder drosselt es den
Server, greift sie auf die in `main` eingecheckte Kopie zurück. Um sie auch
an der Serverwurzel (`/ios/classic`) auszuliefern, eines der
Webserver-Rewrite-Snippets aus dem
[App-README](../../flutcloud-app/README.md#ios-altstore-classic-quelle) ergänzen.

## Fehlerbehebung

- **FlutLink lehnt den Server ab** (`FlutCloudAppMissing`) — die Capability
  wird nicht angekündigt. Prüfen, dass die App aktiv ist
  (`php occ app:list | grep flutcloud`), das Verzeichnis `flutcloud` heißt
  und das `curl` oben `ocs.data.capabilities.flutcloud` liefert.
- **`composer` nicht gefunden** — Composer installieren oder Schritt 2
  überspringen; die App läuft ohne generierten Autoloader.
- **App lässt sich nicht aktivieren** — Nextcloud-Version 28–37 und PHP 8.1+
  prüfen (siehe `<dependencies>` in `appinfo/info.xml`). Bei der Meldung
  *„cannot be installed because it is not compatible with this version of the
  server"* ist der Server neuer als die deklarierte `max-version`; diese in
  `appinfo/info.xml` anheben.
- **`Nextcloud or one of the apps require upgrade`** — Nextcloud verweigert
  die meisten `occ`-Befehle, bis die Datenbank aktualisiert ist. Einmal
  `sudo -u www-data php occ upgrade` ausführen (oder über die Weboberfläche).
  Das Installationsskript macht das automatisch und versucht `app:enable`
  danach erneut.
- **Permissions-Fehler** — sicherstellen, dass die App-Dateien dem
  Webserver-Benutzer gehören und `nextcloud/apps/` beschreibbar ist.

## Entwicklung

```bash
composer check   # php -l Lint aller Quelldateien
```

Bei Versionsanhebung `appinfo/info.xml`, `composer.json` und die von `/ping`
gelieferte `version` synchron halten. Repository-weiten Verifikationsablauf
siehe [Entwicklung](development.md).

## Siehe auch

- [Erste Schritte](getting-started.md) — der FlutLink-Client
- [Funktionen](features.md) — was FlutLink bietet
- [Sicherheit](security.md) — wie der Capability-Check den Client schützt
