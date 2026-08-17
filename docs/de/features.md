# Funktionen

FlutLink bündelt vier Arbeitsbereiche in einem Fenster: Dateibrowser,
Freigaben, Administration und Zwei-Wege-Sync.

## Dateien-Tab

- WebDAV-Browser mit `PROPFIND`-Listings (Depth 1).
- Einträge werden als **resource** oder **part** markiert (siehe die
  `resources`/`parts`-Konventionen auf der FlutCloud-Server-Seite) und als
  Badges angezeigt. Jeder `resources`-Eintrag trägt sein schreibbares
  `parts`-Gegenstück (`pairedPath`); eine **Pairing-Leiste** springt
  „virtuell ↔ real" und eine **Split-Ansicht** zeigt beide Namespaces
  nebeneinander. Virtuelle Links werden aufgelöst: Beim Überfahren des
  Badges erscheint das beschreibbare `parts/…`-Gegenstück eines
  `resources/…`-Eintrags (und umgekehrt) über das `linkTarget`-Feld.
- **Link-Freigabe** per Klick über die OCS-Share-API — der öffentliche Link
  wird in die Zwischenablage kopiert.
- **Volle Freigabe-Verwaltung** im Teilen-Dialog pro Datei/Ordner: öffentliche
  Links erstellen (optional mit Passwort, Ablaufdatum und öffentlichem Upload),
  mit einem Benutzer oder einer Gruppe teilen (OCS `shareType` 0/1 +
  `shareWith`), bestehende Freigaben auflisten, Link-URLs kopieren und
  Freigaben widerrufen. Einträge zeigen ein Badge mit ihrer Freigabe-Anzahl.
- **Dateioperationen**: Hochladen, Herunterladen / Öffnen (lädt in eine
  Tempdatei und öffnet sie mit der Standard-App), Neuer Ordner, Umbenennen,
  Löschen — über die Symbolleiste, das Kontextmenü und Mehrfachauswahl.
- Umschalter Raster/Liste, sortierbare Spalten und Mehrfachauswahl.
- **Globale Suche** über WebDAV `SEARCH` (Debounce-Eingabe): Treffer zeigen
  ihren Speicherort und springen per Klick in den Ordner.
- **Bulk-Aktionen**: Select-All-Checkbox sowie Bulk-Download und -Löschen.
- **Drag-&-Drop-Upload** von Dateien und Ordnern mit Bestätigung pro Datei bei
  Überschreiben.
- **Fortschritt**: Uploads und Downloads emittieren `file://progress`-Events
  und werden als Fortschrittsindikatoren angezeigt.
- **Ordner-ZIP-Download** und lazy-geladene **Bild-Thumbnails** in Listen- und
  Rasteransicht.
- **Offline-Modus**: Listings und Kontingente werden gecacht; bei
  Netzwerkfehlern werden die gecachten Daten mit einem „Offline"-Banner
  angezeigt.
- „Zurück"-Button und vollständige Tastatur-Navigation (Pfeiltasten bewegen den
  Fokus, Enter öffnet, Entf löscht).
- Mehrere Konten: Kontowechsel über die Seitenleiste oder das
  Avatar-Menü.

## Admin-Tab

Nur für Konten sichtbar/aktiv, die Mitglied einer Admin-Gruppe sind.

- Alle Benutzer auflisten (OCS Provisioning API).
- Benutzerdetails und Kontingente ansehen.
- Kontingente setzen (mit **Presets** wie 1/5/10 GB, unbegrenzt oder
  benutzerdefiniert) und Benutzerattribute bearbeiten.
- **Gruppenverwaltung**: Gruppen auflisten und anlegen, Mitglieder hinzufügen
  und entfernen.
- Dateien von Benutzern per **Admin-Impersonation** durchsuchen:
  `webdav_list` akzeptiert optional `target_user`; das Backend verweigert den
  Aufruf für Nicht-Admins und setzt den `Impersonate-User`-Header.

## Sync-Tab

- Beliebigen lokalen Ordner hinzufügen; sein Inhalt wird nach
  `/FlutLink/<Ordner>` auf dem aktiven FlutCloud-Konto gespiegelt.
- Optional **Symlinks im Ordner verfolgen** (Kontrollkästchen „Symlinks folgen"
  beim Hinzufügen) — Links werden mit Zyklusschutz aufgelöst statt
  übersprungen.
- Status pro Ordner: `idle`, `syncing`, `paused`, `error` mit Zählern für
  anstehende Uploads/Downloads/Löschungen und Fehlern.
- Ordner einzeln pausieren/fortsetzen oder wieder entfernen.
- „Jetzt synchronisieren" startet sofort einen Durchlauf; sonst läuft der
  Hintergrund-Worker alle 10 Sekunden und nach relevanten Änderungen.

Details siehe [Sync-Engine](sync.md).

## System-Tray & CLI

- Schließt man das Fenster, wird FlutLink in den System-Tray verschoben statt
  beendet.
- Tray-Menü: *FlutLink anzeigen* / *FlutLink beenden*; ein Linksklick auf das
  Symbol stellt das Fenster ebenfalls wieder her.
- Kommandozeilen-Flags decken Headless-artige Workflows ab:
  `--sync`, `--path <Ordner>`, `--tray`.

Siehe [Tray & CLI](tray-and-cli.md).

## Updates & Benachrichtigungen

- **Auto-Update-Check** beim Start: Ist ein neues Release verfügbar, erscheint
  ein Update-Banner, und die Einstellungen bieten manuelle Prüfung und
  Installation. Downloads werden gegen den im Release veröffentlichten
  SHA-256-Digest verifiziert.
- **Native OS-Benachrichtigungen** nach einem Sync-Pass (Fehler bzw.
  synchronisierte Dateien, über alle Ordner aggregiert, kein Spam bei
  Leerläufen) und wenn ein Update verfügbar ist.

## Android-Client

Ein begleitender **Android-Client** (`android/`) portiert den
Desktop-Funktionsumfang auf Kotlin + Jetpack Compose (Material 3). Er wurde
mit opencode generiert, spiegelt die Desktop-App und ist kein separates
Produkt. Es gilt dieselbe FlutCloud-only-Policy: Vor der Annahme eines Kontos
wird die `flutcloud`-Capability geprüft, und wenn eine Server-URL ins Build
eingebaut ist (`FLUTCLOUD_URL` / `-PflutcloudUrl`), ist das URL-Feld im
Login-Screen gesperrt.

Auf Android verfügbar:

- **Dateien** — WebDAV-Browsing (PROPFIND), Upload über das Storage Access
  Framework (mit Überschreiben-Bestätigung), Download + Öffnen mit einer
  externen App, Download in den öffentlichen **Downloads-Ordner**, Teilen über
  das Android-**Share-Sheet**, Ordner anlegen, Umbenennen, Löschen, globale
  Suche (SEARCH), `resources`/`parts`-Virtuallink-Pairing, Share-Verwaltung
  (öffentliche Links anlegen, Shares auflisten und widerrufen) sowie ein
  Offline-Cache für Ordner-Listings.
- **Admin** — OCS-Benutzerverwaltung: auflisten/suchen, anlegen, löschen,
  aktivieren/deaktivieren, Quota-Presets und Gruppenverwaltung.
- **Registrierung** — neues Konto direkt vom Login-Screen über die
  OCS-Provisioning-API (Admin-Zugangsdaten), dann Anmeldung mit dem neuen
  Passwort.
- **Sicherheit** — Tokens liegen in `EncryptedSharedPreferences`
  (Android-Keystore), Kontometadaten in einer separaten Preferences-Datei;
  kein Token wird jemals geloggt oder im Klartext gespeichert.

Nicht portiert (vorerst nur Desktop): Zwei-Wege-Sync, Admin-Impersonation
sowie Tray-/CLI-Verhalten.

Build und Details siehe [`android/README.md`](../../android/README.md).
