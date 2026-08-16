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
