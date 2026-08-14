# Tray & CLI

FlutLink ist eine Tray-first-Anwendung: Schließt man das Fenster, wird es in
den System-Tray verschoben statt beendet, sodass der Sync im Hintergrund
weiterläuft.

## System-Tray

- **Setup** — `setup_tray` in `src-tauri/src/lib.rs` erzeugt ein Tray-Symbol
  mit einem Zwei-Punkte-Menü (*FlutLink anzeigen*, *FlutLink beenden*). Das
  Symbol stammt aus dem Standard-Fenstericon (regeneriert aus
  `app-icon.png` via `npm run tauri icon app-icon.png`).
- **Schließen-in-Tray** — `on_window_event` fängt `CloseRequested` ab. Außer
  wenn ein Quit-Flag gesetzt ist (Tray *Beenden*), wird das Schließen
  verhindert und das Fenster stattdessen versteckt.
- **Wiederherstellen** — das Menü *Anzeigen* und ein Linksklick auf das
  Tray-Symbol rufen `show_main_window` auf (unminimize + show + focus).
- **Beenden** — das Menü *Beenden* setzt das Quit-Flag und ruft
  `app.exit(0)` auf.

## CLI-Flags

Konfiguriert in `tauri.conf.json` unter `plugins.cli` und geparst in
`handle_cli` (für die Rust-seitige Auswertung sind keine Capabilities nötig).

| Flag | Kurz | Argument | Verhalten |
| --- | --- | --- | --- |
| `--sync` | `-s` | keins | Nach dem Start einen Sync-Durchlauf ausführen (`sync.notify_one()`) |
| `--path` | `-p` | Verzeichnis | Lokalen Ordner zum Zwei-Wege-Sync hinzufügen |
| `--url` | `-u` | URL (ignoriert) | Login-Dialog für den FlutCloud-Server öffnen |
| `--tray` | `-t` | keins | Minimiert in den System-Tray starten |

Beispiele:

```bash
flutlink --tray --sync                     # still starten und einmal synchronisieren
flutlink --path "C:\Users\me\Dokumente"    # Sync-Ordner hinzufügen
flutlink --url             # Anmeldung öffnen (der FlutCloud-Server ist fest)
```

Frontend-Interaktion:

- `--url` emittiert `flutlink:cli-open`; `App.vue` hört zu und öffnet
  `LoginModal`. Die Server-URL ist immer der FlutCloud-Server (abgerufen über
  den `get_flutcloud_url`-Command, der `FLUTCLOUD_URL` aus der `.env` in
  `src-tauri/src/flutcloud.rs` liest) — FlutLink ist ein dedizierter Client und
  verbindet sich nie woanders hin.
- `--path` führt `commands::sync_add` aus und emittiert
  `sync-folders-changed`, damit sich das Sync-Panel aktualisiert.
