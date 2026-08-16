# Sync-Engine

Die Sync-Engine liegt in `src-tauri/src/sync.rs` und gehört `AppState`
(`sync: Arc<SyncEngine>`). Sämtliche Datei-Übertragungen nutzen die
WebDAV-Helfer in `nextcloud/webdav.rs` (`put_file`, `get_file`, `delete`,
`make_collection`).

## Konzepte

- **Ordner** — ein lokaler Ordner, der an einen Remote-Ordner
  `/FlutLink/<Name>` für ein bestimmtes Konto gebunden ist. Die Ordner-ID ist
  ein hexadezimaler Unix-Nanosekunden-Zeitstempel; der Account-Key ist
  `"{benutzername}@{instanz_url}"`.
- **Journal** — eine JSON-Datei `sync-journal-<id>.json` im App-Data-Verzeichnis.
  Pro relativem Pfad speichert es den zuletzt synchronisierten
  `{ size, mtime }`-Fingerabdruck der lokalen und der Remote-Seite (plus ein
  `is_dir`-Flag). Es ist die Quelle der Wahrheit, um zu entscheiden, was sich
  auf welcher Seite geändert hat. Beim Start werden die Status aus dem Journal
  befüllt, damit Sync-Ordner ihren persistierten Zustand sofort zeigen.
- **Pass** — ein Planner-Durchlauf, der den lokalen (iterativer
  Verzeichnislauf) und den Remote-Zustand (BFS-`PROPFIND`) scannt, eine
  begrenzte Aktionsliste erzeugt (`MAX_OPS_PER_PASS = 200`) und sie in
  abhängigkeitssicherer Reihenfolge ausführt: `EnsureDir` vor Uploads,
  Elternordner vor Kindern.

## Entscheidungsregeln (`decide`)

| Lokal | Remote | Journal | Aktion |
| --- | --- | --- | --- |
| neu | — | keine | Upload |
| — | neu | keine | Download |
| geändert | unverändert | synchronisiert | Upload |
| unverändert | geändert | synchronisiert | Download |
| fehlt | unverändert | synchronisiert | DeleteRemote |
| unverändert | fehlt | synchronisiert | DeleteLocal |
| geändert | geändert | synchronisiert | UploadConflict (`Name (conflict copy).ext`) |
| unverändert | unverändert | synchronisiert | Skip |
| leerer Ordner | — | — | EnsureDir |
| — | leerer Ordner | synchronisiert (Ordner) | DeleteRemoteDir |
| Ordner | Datei (unverändert) | — | MoveRemoteConflict |
| Datei (unverändert) | Ordner | — | MoveLocalConflict |

Löschungen werden nur weitergegeben, wenn die andere Seite seit dem Journal
unverändert ist — das verhindert versehentlichen Datenverlust. Übersprungene
Muster: Dotfiles, `~$*`, nachgestelltes `~`, `Thumbs.db`. Symbolische Links
werden standardmäßig übersprungen; wird ein Sync-Ordner mit der Option
„Symlinks folgen“ angelegt, werden Links in `walk_local` aufgelöst (mit
Kanonal-Pfad-Zyklusschutz), sodass ihre Ziele synchronisiert werden.

## Verzeichnisse & Typkonflikte

- Leere lokale Ordner werden remote gespiegelt (`EnsureDir`); das Journal legt
  dafür Verzeichniseinträge an, damit eine spätere Remote-Löschung
  weitergegeben werden kann.
- Nur-remote leere Ordner werden nur gelöscht, wenn das Journal sie als
  synchronisierte Verzeichnisse kennt (`DeleteRemoteDir`) — und nie, wenn der
  Remote-Ordner noch Kinder hat.
- Lokaler Ordner vs. Remote-Datei und lokale Datei vs. Remote-Ordner gelten als
  Konflikt statt als Löschung: Die kollidierende Remote-Seite wird beiseite
  verschoben (`MoveRemoteConflict` / `MoveLocalConflict`) in eine eindeutige
  Konfliktkopie.

## Konfliktnamen

Konflikte verwenden `Name (conflict copy).ext` für Dateien. Innerhalb eines
Durchlaufs ist jedes Ziel eindeutig (`conflict_name_n`): Existiert das
Kopierziel bereits (aus einem früheren Durchlauf oder einem früheren
Konflikt), wird ein numerisches Suffix `(conflict copy 2)`, `(conflict copy
3)`, … ergänzt, sodass keine Kopie eine andere überschreibt.

## Executors

- `exec_upload` — streamt die Datei mit `X-OC-MTime` = lokale mtime, damit der
  Server-Zeitstempel passt, und schreibt danach das Journal.
- `exec_download` — streamt in eine Tempdatei und benennt atomar um.
- `exec_delete_remote` / `exec_delete_local` — 404 wird toleriert.
- `exec_upload_conflict` — lädt die lokale Kopie unter einem Konfliktnamen hoch
  und schreibt beide Seiten ins Journal, damit der Konflikt nie erneut
  ausgelöst wird.
- `exec_mkdir` — erstellt einen Remote-Ordner und journalt ihn als
  Verzeichniseintrag.
- `exec_delete_remote_dir` — löscht einen synchronisierten leeren
  Remote-Ordner und entfernt seinen Journal-Eintrag.
- `exec_move_remote_conflict` / `exec_move_local_conflict` — benennt die
  kollidierende Seite beiseite (WebDAV-`MOVE` / lokales `rename`) und trägt
  den neuen Pfad ins Journal ein.

## Status & Events

`SyncFolderStatus` trägt `state` (`idle | syncing | paused | error`),
Zähler für anstehende Aktionen, Fehler, letzten Fehler und `lastSyncedAt`.
Der Worker emittiert ein `sync-status`-Event (Payload: alle Status) nur dann,
wenn sich etwas geändert hat. Pausierte Ordner werden von Durchläufen
übersprungen; beim Fortsetzen springt der Status sofort auf `idle`.

## Worker

`spawn_worker` startet eine Tokio-Task, die auf das `tokio::sync::Notify` der
Engine wartet (ausgelöst durch `sync_trigger`, `sync_add`, CLI `--sync` und
jede Statusänderung) sowie auf ein 10-Sekunden-Intervall, und führt bei jedem
Signal `run_all` (alle nicht pausierten Ordner) aus. Der CLI-`--path`-Handler
emittiert `sync-folders-changed`, das das Frontend zum Neuladen der Ordnerliste
empfängt.

## IPC

Backend: `sync_list`, `sync_add` (kanonisiert den Pfad, akzeptiert ein
optionales `follow_symlinks`-Flag, weist doppelte Remote-Pfade ab),
`sync_remove`, `sync_set_paused`, `sync_trigger` in
`commands.rs`. Frontend: Wrapper in `src/lib/ipc.ts`, reaktiver Zustand in
`src/stores/sync.ts`, UI in `src/components/SyncPanel.vue` (Ordnerauswahl via
`tauri-plugin-dialog`).
