# Funktionen

FlutLink bündelt vier Arbeitsbereiche in einem Fenster: Dateibrowser,
Freigaben, Administration und Zwei-Wege-Sync.

## Dateien-Tab

- WebDAV-Browser mit `PROPFIND`-Listings (Depth 1).
- Einträge werden als **resource** oder **part** markiert (siehe die
  `resources`/`parts`-Konventionen auf der FlutCloud-Server-Seite) und als
  Badges angezeigt.
- **Link-Freigabe** per Klick über die OCS-Share-API — der öffentliche Link
  wird in die Zwischenablage kopiert.
- **Dateioperationen**: Hochladen, Herunterladen / Öffnen (lädt in eine
  Tempdatei und öffnet sie mit der Standard-App), Neuer Ordner, Umbenennen,
  Löschen — über die Symbolleiste, das Kontextmenü und Mehrfachauswahl.
- Umschalter Raster/Liste, sortierbare Spalten und Mehrfachauswahl.
- Mehrere Konten: Kontowechsel über die Seitenleiste oder das
  Avatar-Menü.

## Admin-Tab

Nur für Konten sichtbar/aktiv, die Mitglied einer Admin-Gruppe sind.

- Alle Benutzer auflisten (OCS Provisioning API).
- Benutzerdetails und Kontingente ansehen.
- Kontingente setzen und Benutzerattribute bearbeiten.
- Dateien von Benutzern per **Admin-Impersonation** durchsuchen:
  `webdav_list` akzeptiert optional `target_user`; das Backend verweigert den
  Aufruf für Nicht-Admins und setzt den `Impersonate-User`-Header.

## Sync-Tab

- Beliebigen lokalen Ordner hinzufügen; sein Inhalt wird nach
  `/FlutLink/<Ordner>` auf dem aktiven FlutCloud-Konto gespiegelt.
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
