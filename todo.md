# FlutLink Todo

Einzige Tracking-Datei des Projekts: offene Punkte, datierte Review-Abschnitte
und das Archiv erledigter Punkte in einem Dokument. Ersetzt die früheren
Dateien `archived-todo.md` und `reports/review-*.md`.

## Offen

### Review 2026-08-15 (Lauf 6, Fokus: Phase 3 & 4 — Chunking/Progress, DnD, resources/parts, Provisioning, Notifications, Offline-Cache)

**Verifikation:** Seit Lauf 5 sind genau zwei Fixes eingegangen: **U11**
(doRename aktualisiert `selected`-Set, Commit d6771b9 — in
`FileExplorer.vue:170-178` verifiziert) und **N12** (`unique_conflict_target`
prüft jetzt lokal **und** remote, Commit a102bb6 — `sync.rs:151-169` +
Unit-Test `move_local_conflict_skips_existing_local_conflict_copy` verifiziert).
Beide sind bereits im Archiv markiert. **Alle übrigen Punkte (U1–U10, F1–F10,
N1–N11, N13–N16, P1–P17, U8–U16) sind weiterhin offen** — erneut gegen den
aktuellen Stand geprüft und bestätigt. Checks ausgeführt:
`cargo test --manifest-path src-tauri/Cargo.toml` → **43 passed / 0 failed**
(42 + neuer N12-Test); `npm run build` (vue-tsc + vite) → grün. Hinweis:
`cargo test` benötigt auf Linux glib/webkit2gtk-4.1-Systembibliotheken (auf dem
Runner nachinstalliert), sonst bricht der Build vor den Tests ab (kein Codefehler).

Neue Befunde (Lauf 6, Fokus Phase 3 & 4):

- [ ] **Q1 (Phase 4, Feature, mittel):** Native Notifications fehlen komplett:
      `tauri-plugin-notification` ist weder in `src-tauri/Cargo.toml` noch in
      `lib.rs:196-199` (nur opener/dialog/cli) registriert. Sync-Fehler/
      -Abschluss, verfügbare Updates und Transfers erzeugen keine
      OS-Notification. README Phase 4 verspricht „native notifications".
      Fix: Plugin registrieren + Emission aus `sync.rs::run_all` (Z. 1119-1121)
      und `updater.rs::check_update`.
- [ ] **Q2 (Phase 4, Feature, mittel):** Kein Offline-Cache: `src/stores/files.ts`
      hält nur das aktuelle Listing in Memory; ohne Netz zeigt der Browser
      leere Ordner/Fehler statt gecachter Daten. README Phase 4 „offline cache"
      ist nicht umgesetzt (kein Cache-Code in `src/`, verifiziert). Fix:
      Listing-/Quota-Cache im AppData-Dir + „Offline"-Indikator im
      `FileExplorer.vue`.
- [ ] **Q3 (Phase 4, Feature, mittel):** Gruppen-Verwaltung fehlt: `AdminPanel.vue`
      zeigt `selected.groups` nur read-only (Z. 400-410); `ocs.rs` kennt keine
      Gruppen-Endpunkte (nur Lesen in `get_user`, Z. 151-169), kein Command in
      `commands.rs`, kein Wrapper in `src/lib/ipc.ts`. README Phase 4 listet
      „groups". Fix: OCS-Gruppen-Commands (create/add-member/remove-member)
      + UI.
- [ ] **Q4 (Phase 3, Feature, mittel/hoch):** Chunked Uploads/Downloads mit
      Progress fehlen: `webdav_upload_file` (`commands.rs:364-391`) streamt
      einen einzigen PUT (`put_file_as`, `webdav.rs:122-146`),
      `webdav_download_file` (`commands.rs:395-418`) einen einzigen GET.
      **Kein** WebDAV-Chunking, **keine** `app.emit`-Progress-Events für
      Datei-Transfers (nur der Updater emittiert `update://progress`,
      `updater.rs:271-283`; in `commands.rs`/`webdav.rs` kein `emit`,
      verifiziert). README Phase 3 verspricht „chunked uploads/downloads with
      progress events (app.emit)"; große Dateien brechen zusätzlich am 60-s-
      Total-Timeout ab (F2, `state.rs:112-113`). Fix: Progress-Callback durch
      die Transfer-Helper ziehen + Chunking (WebDAV-Chunked-Upload v2).
- [ ] **Q5 (Phase 3, Feature, minor):** Drag & Drop-Upload fehlt:
      `FileExplorer.vue` hat keinen `@drop`/`@dragover`-Handler (kein
      Vorkommen von `@drop`/`dragover`/`dataTransfer` im File, verifiziert).
      README Phase 3 „drag & drop". Fix: DnD-Handler, der `files.uploadFile`
      wie `uploadFiles` (Z. 123-142) aufruft.
- [ ] **Q6 (Phase 3, Feature, mittel):** `resources`/`parts`-Dual-Pane-Workflow
      fehlt: Backend klassifiziert korrekt (`webdav.rs::classify`,
      Z. 531-542, Flags `is_resource`/`is_part`), die UI zeigt nur Badges
      (`FileExplorer.vue:464-478`). Kein Pairing virtueller Links
      (`resources`) mit ihren schreibbaren Teilen (`parts`), kein
      „virtual ↔ real"-Navigationsfluss. Fix: Split-View-/Pairing-Konzept +
      Verknüpfungsfeld in `WebDavEntry`.
- [ ] **Q7 (Phase 3, Feature, minor):** Symlink-/Virtual-Link-Auflösung fehlt:
      `walk_local` überspringt Symlinks still (`sync.rs:196-198`),
      `resources`-Einträge werden nie auf ihr Ziel aufgelöst (kein
      Link-Target-Feld in `WebDavEntry`). README Phase 3 „symlink/virtual-link
      resolution". Fix: Symlink-Following-Option bzw. Link-Auflösung im
      Backend.
- [ ] **Q8 (Phase 4, Feature, minor):** Quota-Presets fehlen: `AdminPanel.vue`
      (Z. 431-447) bietet nur freie MB/GB-Eingabe + „Unlimited"; README
      Phase 4 „quota presets". Fix: Preset-Select (z. B. 1/5/10 GB,
      unlimited) + benutzerdefiniert.
- [ ] **Q9 (Bug, Datenverlust-Risiko, mittel):** Upload überschreibt still:
      `webdav_upload_file` sendet einen ungeprüften PUT (`put_file_as`,
      `webdav.rs:135-146`) — existiert die Zieldatei, wird sie ohne Rückfrage
      überschrieben. `uploadFiles` (`FileExplorer.vue:123-142`) prüft nicht
      und meldet nur „Uploaded.". N1 deckt nur das Rename-Overwrite ab
      (`Overwrite: T`, `webdav.rs:359`). Fix: Existenz-Check (PROPFIND) vor
      PUT oder `Overwrite: F` + klarer `AppError`; UI-Confirm bei existierendem
      Ziel.

### Review 2026-08-14 (Lauf 5, Fokus: Browsing & Link-Sharing)

**Verifikation:** Kein Anwendungscode-Change an `src/` oder `src-tauri/src/`
seit Lauf 4 (letzte App-Komits e31f4f6/davor; seitdem nur `scripts/`-Komits).
Damit sind **U1–U7, F1–F10 und N1–N9 weiterhin offen** (alle Verdachtsfälle
erneut gegen den aktuellen Stand geprüft und bestätigt). Checks ausgeführt:
`cargo test --manifest-path src-tauri/Cargo.toml` → 42 passed / 0 failed;
`npm run build` (vue-tsc + vite) → grün.

Neue Befunde (Lauf 5, Fokus „real browsing like Nextcloud UI/Google Drive,
public + private link sharing"):

- [x] **P1 (Feature, mittel/hoch — Kern-Fokus):** Nur öffentliche
      Read-Only-Links, kein privates Sharing. `ocs::create_share`
      (`src-tauri/src/nextcloud/ocs.rs:252-285`) sendet hartkodiert
      `shareType=3` (öffentlicher Link) + `permissions=1`. Private Freigaben
      (OCS `shareType=0` User / `shareType=1` Gruppe mit `shareWith`) fehlen
      durchgängig: `webdav_create_share` (`src-tauri/src/commands.rs:326-337`)
      hat keinen `share_with`/`share_type`-Parameter, `src/lib/ipc.ts`
      (`webdavCreateShare`, Z. 124-125) und `FileExplorer.vue` (`createLink`,
      Z. 198-208) bieten nur „Link". Fix: Parameter durchreichen + UI-Dialog
      („Mit Benutzer teilen" / „Link mit Passwort").
- [x] **P2 (Feature, mittel):** Kein Share-Management: Es gibt weder
      `list_shares` noch `delete_share` (OCS `GET /shares` bzw.
      `DELETE /shares/{id}` unter `apps/files_sharing/api/v1/shares`). Ein
      erstellter Link ist nach dem Kopieren weder erneut abrufbar noch
      widerrufbar (`FileExplorer.vue` zeigt nur ein ✓-Icon) — für
      Nextcloud-UI-Niveau unverzichtbar. Fix: `list_shares`/`delete_share` als
      Commands (commands.rs + ipc.ts) + Share-Status pro Eintrag in der UI.
- [x] **P3 (Feature, mittel):** Link-Optionen fehlen: `create_share`
      (ocs.rs:262-267) unterstützt kein `password`, `expireDate` oder
      `publicUpload` (permissions 15). Öffentliches Link-Sharing ist damit auf
      „read-only, ewig gültig, ohne Passwort" reduziert.
- [x] **P4 (Bug, bestätigt F4, hier gegen Stand verifiziert):** Doppel-Encoding
      in `create_share`: `encode_segments(rel_path)` (ocs.rs:262) + `req.form()`
      (`nextcloud/mod.rs:57-59`, url-encodiert erneut) → `%20` wird `%2520` →
      Share-Erstellung für Pfade mit Leerzeichen/Umlauten schlägt fehl. Fix:
      Raw-Pfad ins Formular + Roundtrip-Test.
- [ ] **P5 (Bug, bestätigt U1):** `webdav_rename` (`commands.rs:466-467`):
      `parent = path.rsplit('/').nth(1)` → `new_path` ohne führenden Slash →
      `validate_dav_path` (commands.rs:342-359) lehnt ab. Umbenennen in
      Unterordnern schlägt immer fehl, nur Root-Rename funktioniert. Fix:
      `rsplit_once('/')` + Test.
- [x] **P6 (Bug, bestätigt U3/N7):** `createLink` (`FileExplorer.vue:198-208`)
      verschluckt den Backend-Fehler (nur ✗-Icon) und verliert die Share-URL,
      wenn `navigator.clipboard.writeText` fehlschlägt (häufig WebKitGTK/
      Linux). Fix: URL auch bei Clipboard-Fehler anzeigen (Toast/
      Klick-zum-Kopieren), Fehler via `invokeError` toasten.
- [ ] **P7 (Bug, bestätigt U4):** `uploadFiles` (`FileExplorer.vue:123-142`)
      setzt `busyPath = ""` (falsy) → Re-Entry-Guards in `open`/`download`
      (Z. 94/109) greifen nicht; Mehrupload bricht beim ersten Fehler ab. Fix:
      Token/Set statt String.
- [ ] **P8 (Bug, bestätigt N5):** `open()` (`FileExplorer.vue:96-100`) legt
      jeden Download dauerhaft in `tempDir()` ab (kein Cleanup) — jedes Öffnen
      einer Datei füllt das Temp-Verzeichnis. Fix: nach `openPath` löschen oder
      eigenes Cache-Verzeichnis mit Cleanup.
- [ ] **P9 (Bug, bestätigt N2):** `list_users` (`ocs.rs:75-119`): Offset-
      Pagination ohne Fortschritts-Guard → Endlosschleife, wenn der Server
      `offset` ignoriert (gleiche Seite erneut, `count == LIMIT`). Fix:
      Duplikat-Erkennung als Abbruchbedingung.
- [ ] **P10 (Bug, bestätigt N6):** `should_skip_name` (`sync.rs:172-175`)
      asymmetrisch: lokale versteckte Dateien (`.env`, `.gitignore`) werden
      nie hochgeladen, remote vorhandene versteckte Dateien werden beim
      Erst-Sync heruntergeladen. Beide Richtungen einheitlich skippen.
- [ ] **P11 (Bug, bestätigt N8):** `ensure_collection` doppelt pro Pass:
      `run_all` (`sync.rs:1079-1080`) und `run_pass` (`sync.rs:714`) →
      unnötige MKCOL-Requests pro Tick. Eine Stelle reicht.
- [ ] **P12 (Security, bestätigt N3):** `release.yml` (Z. 41-57) interpoliert
      Commit-Nachrichten (`$LOG`) ungefiltert in den opencode-Prompt →
      Prompt-Injection über bösartige Commit-Titel (Gegenstück in
      `opencode.yml` ist bereits entschärft). Fix: „UNTRUSTED INPUT"-
      Markierung + Log auf `--oneline`-Titel kürzen.
- [ ] **P13 (CI, bestätigt N4):** `build.yml` (Z. 6-9): `paths-ignore:
      ['.github/**']` auf PRs → Workflow-/Action-Änderungen lösen keine CI
      aus und werden nie getestet. `.github/**` aus paths-ignore nehmen.
- [ ] **P14 (Feature, minor):** Keine Dateisuche: Es gibt keinen
      WebDAV-SEARCH-Command (weder `commands.rs` noch `src/lib/ipc.ts`;
      „search" aus B9 wurde nie umgesetzt — nur die OCS-Benutzersuche in
      `ocs.rs:78-92` existiert). Für Google-Drive-ähnliches Browsing fehlt
      die globale Dateisuche. Fix: SEARCH-Command + UI-Suchfeld.
- [ ] **P15 (Bug/Robustheit, minor):** `is_admin` wird nur beim
      Account-Add/Register einmal ermittelt (`commands.rs:56-58`, Z. 224-226)
      und dauerhaft in `accounts.json` gespeichert. `ocs::is_admin`
      (`ocs.rs:59-71`) schluckt alle Fehler (`Err(_) => Ok(false)`) →
      transiente Netzwerkfehler beim Login markieren ein Admin-Konto dauerhaft
      als Nicht-Admin. Fix: Admin-Status beim App-Start neu evaluieren.
- [ ] **P16 (Bug/Robustheit, minor):** `relative_path` (`webdav.rs:517-528`):
      Liefert der Server absolute hrefs (mit Scheme/Host), findet
      `href.find(base_path)` nichts → Pfade wie `/https:/host/...`; der
      Namespace-Guard (`webdav.rs:56-61`) prüft nur das Präfix `/remote.php/`
      und greift dann nicht. Guard auf beide Formen erweitern.
- [ ] **P17 (Feature, minor):** Browsing-UX-Lücken im `FileExplorer.vue`:
      kein „Zurück"-Button, keine Tastatur-Navigation (Enter = öffnen,
      Entf = löschen), Grid-Ansicht ohne Link-Button, keine Ordner-ZIP-
      Downloads (Nextcloud bietet `downloadzip`), keine Thumbnails
      (`/core/preview.png`). Für „real browsing"-Niveau sinnvoll.

Neue Befunde (Lauf 5, Fokus Material-3-Expressive-UI / neue Features):

- [x] **U9 (Feature, mittel):** Dateibrowser ohne Google-Drive-Kernfunktionen:
      Es gab **keine Suche** (der in B9 archivierte `webdav_search`-Command wurde
      nie implementiert — weder in `commands.rs` noch `src/lib/ipc.ts`), keine
      Select-All-Checkbox, **keine Bulk-Aktionen** trotz Mehrfachauswahl
      (Z. 389-394 zeigten nur Zähler + Clear), kein Drag & Drop-Upload, keine
      Upload/Download-Fortschrittsanzeige (README „Phase 3" verspricht chunked
      progress events per `app.emit`). Fix: Select-All + Bulk-Download/Delete
      (`webdav_bulk_delete`/`webdav_bulk_download`) + Drag & Drop-Upload
      (`webdav_upload_local_paths`, Webview-DragDrop-Event) +
      `file://progress`-Progress-Events in `FileExplorer.vue`/`ipc.ts`/
      `commands.rs` umgesetzt. **Suche bleibt offen** (getrackt unter Issue #73).
- [ ] **U10 (UX, minor):** Grid-View (`FileExplorer.vue:495-532`): Single-Click
      auf eine Kachel wählt nichts aus (nur die Checkbox), Download/Link/
      Delete fehlen pro Kachel (nur Open/Rename), kein Hover-Preview.
      Google-Drive-Verhalten: Klick = Auswahl, Aktionen im Hover-Overlay.
- [x] **U11 (Bug, minor):** `selected`-Set in `FileExplorer.vue` wird nach
      Rename nie bereinigt (`doRename` Z. 162-177): `files.renameEntry`
      refresht die Liste, der alte Pfad bleibt in `selected` → Zähler
      (Z. 390) zeigt stale Einträge. Fix: Pfad nach Rename im Set ersetzen
      (bzw. `removeEntry` Z. 183 analog bereits ok).
- [x] **N10 (Konsistenz, minor):** `webdav_create_share` (`commands.rs:326-337`)
      ruft im Gegensatz zu allen anderen `webdav_*`-Commands **kein**
      `validate_dav_path` auf → Shares auf `resources`/`parts`-Virtual-Pfaden
      oder mit `..` sind möglich. Fix: `validate_dav_path(&path)?` ergänzt.
- [ ] **N11 (Bug, minor):** `webdav_rename` (`commands.rs:466-467`) akzeptiert
      `/` im `new_name` → „Rename" wird still zu einem Move in einen
      Unterordner. Fix: `/` und `..` im neuen Namen ablehnen (validieren,
      nicht nur auf den zusammengesetzten Pfad).
- [ ] **N13 (Cleanup, minor):** `api.accountActive` in `src/lib/ipc.ts:111` hat
      keinen Frontend-Aufrufer (Dead Code). Entfernen oder im
      `accounts`-Store nutzen.
- [ ] **N16 (Feature, minor):** Auto-Update-Check beim App-Start fehlt weiterhin
      (F7 offen) und die About-Version ist hartkodiert (F5 offen,
      `SettingsModal.vue:237`) — beides für die „neue Features"-Roadmap mit
      einplanen (Update-Banner + `getVersion()`).

### Review 2026-08-14 (Lauf 4, Fokus: Bugs & Errors)

**Verifikation:** Kein Code-Change an `src/` oder `src-tauri/src/` seit Lauf 3
(letzte Anwendungskomits: e31f4f6 „consolidate tracking", davor e696d4d;
seitdem nur `scripts/`-Komits). Damit sind **U1–U7 und F1–F10 weiterhin offen** —
alle Verdachtsfälle erneut gegen den aktuellen Stand geprüft und bestätigt.
Checks ausgeführt: `cargo test --manifest-path src-tauri/Cargo.toml`
→ 42 passed / 0 failed; `cargo clippy --all-targets -- -D warnings` → grün;
`npm run build` (vue-tsc + vite) → grün. Hinweis: `cargo test` braucht auf
Linux `libsecret-1-dev`/`libwebkit2gtk-4.1-dev` (keyring-Systemlib), sonst
bricht der Build vor den Tests ab (kein Codefehler).

Neue Befunde (Lauf 4):

- [ ] **N1 (Bug, Datenverlust, mittel/hoch):** `webdav_rename`
      (`src-tauri/src/commands.rs:453-477`) delegiert an `webdav::rename_as`,
      das den MOVE mit `Overwrite: T` sendet (`nextcloud/webdav.rs:359`). Die
      UI (`FileExplorer.vue` `doRename`, Z. 162-177) prüft nicht, ob der
      Zielname bereits existiert → „a.txt" → „b.txt" überschreibt b.txt
      stillschweigend (SabreDAV/Nextcloud führt Overwrite aus). Fix:
      Existenz-Check des Ziels im Backend (PROPFIND oder `Overwrite: F` +
      sauberer AppError „Ziel existiert bereits") + UI-Hinweis.
- [ ] **N2 (Robustheit, minor):** `nextcloud/ocs.rs` `list_users` (Z. 75-119):
      Die Offset-Pagination hat keinen Fortschritts-Guard. Wenn der Server den
      `offset`-Parameter ignoriert (gleiche Seite erneut), läuft die Schleife
      endlos (Duplikate werden nicht erkannt, `count == LIMIT` → `offset += 200`).
      Fix: Sammel-Menge oder Duplikat-Erkennung als Abbruchbedingung.
- [ ] **N3 (Security, mittel):** `release.yml` `release-notes`-Job (Z. 41-57):
      Die Commit-Nachrichten (`$LOG`) werden ungefiltert in den opencode-Prompt
      interpoliert — Prompt-Injection über bösartige Commit-Titel möglich. Das
      Gegenstück in `opencode.yml` ist bereits mit „UNTRUSTED INPUT"-Markierung
      entschärft (B13), `release.yml` nicht. Fix: Prompt um Warnhinweis
      ergänzen und Log auf `--oneline`-Titel kürzen/escapen.
- [ ] **N4 (CI, minor):** `build.yml` (Z. 6-9): `paths-ignore: ['*.md',
      '.github/**']` auf Pull Requests → Änderungen an Workflows/Actions lösen
      keine CI aus und werden nie getestet. `.github/**` aus paths-ignore
      nehmen (oder nur `*.md` ignorieren).
- [ ] **N5 (Bug, minor):** `FileExplorer.vue` `open()` (Z. 97-99): Jeder
      Datei-Download in `tempDir()` bleibt dauerhaft liegen (kein Cleanup) —
      jedes Öffnen einer Datei füllt das Temp-Verzeichnis. Fix: Datei nach
      `openPath` löschen oder eigenes Cache-Verzeichnis mit Cleanup.
- [ ] **N6 (Bug, minor):** Sync-Skip-Regel `should_skip_name` (`sync.rs:172-175`)
      ist asymmetrisch: Lokale versteckte Dateien (`.env`, `.gitignore`) werden
      nie hochgeladen, aber remote vorhandene versteckte Dateien werden beim
      Erst-Sync heruntergeladen. Entweder beide Richtungen einheitlich skippen
      oder in der Doku dokumentieren.
- [x] **N7 (Bug, minor):** `FileExplorer.vue` `createLink` (Z. 198-208): Der
      `catch`-Block verschluckt die Backend-Fehlermeldung (nur ✗-Icon, kein
      Toast/kein Grund) — Fehlermeldung per `invokeError` anzeigen (ergänzt U3).
- [ ] **N8 (Perf, minor):** `sync.rs` führt `ensure_collection` doppelt pro
      Pass aus: `run_all` (Z. 1079-1080) und nochmals `run_pass` (Z. 714) →
      unnötige MKCOL-Requests auf jedem Tick. Eine Stelle reicht.
- [ ] **N9 (Doku/UX, minor):** `register_user` (`commands.rs:156-253`) speichert
      das echte Kontopasswort als Keyring-Token (`save_token`, Z. 245), während
      der Login-Flow ein App-Passwort erwartet. Passwortwechsel macht das Token
      ungültig; in `docs/` + i18n (`initHint`) klarstellen, dass das
      Registrierungs-Passwort dauerhaft das App-Passwort ist.

Bestätigt weiter offen (kein Fix seit Lauf 3, Code unverändert): U1 (Rename
`commands.rs:466-467` ohne führenden Slash), U2 (`account_switch` emittiert
kein `accounts-changed`, `commands.rs:260-273`; Store `sync()` statt `load()`),
U3, U4 (`busyPath = ""`), U5, U6, U7, F1 (`ADMIN_EDIT_KEYS` ohne `enabled`,
`commands.rs:550-557`), F2 (60 s-Total-Timeout `state.rs:112-113`, 30 s
`updater.rs:526-528`), F3 (partieller Download bleibt in `updater.rs:260-283`),
F4 (Doppel-Encoding `ocs.rs:262-273`), F5, F6, F8, F9, F10.

### Review 2026-08-14 (Lauf 3, Fokus UI)

Alle Punkte aus Lauf 2 (F1–F10) sind weiterhin offen (kein Code-Change seit
Lauf 2). Details zu den Befunden: datierter Review-Abschnitt Lauf 3 (vormals
`reports/review-2026-08-14.md`).

- [ ] **U1 (Bug, mittel):** Umbenennen in Unterordnern schlägt immer fehl —
      `webdav_rename` (`src-tauri/src/commands.rs:466-467`) baut `new_path`
      ohne führenden Slash (`parent = path.rsplit('/').nth(1)` →
      `"Documents/neu.txt"`), `validate_dav_path` lehnt ab. Nur Root-Rename
      funktioniert. Fix: `path.rsplit_once('/')` / Slash beibehalten + Test.
- [ ] **U2 (Bug, UI):** Nach Konto-Wechsel über das UI erscheinen mehrere
      Konten als „aktiv" — `accounts.switchTo` (`src/stores/accounts.ts:90-101`)
      nutzt `sync()` statt `load()`, und `account_switch`
      (`src-tauri/src/commands.rs:260-273`) emittiert kein `accounts-changed`
      (nur der Tray-Pfad `lib.rs:115-128`). Fix: `await load()` oder Event
      emittieren.
- [x] **U3 (Bug/UX, mittel):** Share-Link — bei `navigator.clipboard.writeText`
      -Fehler (v. a. Linux/WebKitGTK) zeigt `FileExplorer.vue:198-208`
      (`createLink`) ein ✗, obwohl der Share erstellt wurde; die URL ist dann
      verloren. Fix: URL bei Clipboard-Fehler trotzdem anzeigen (Toast/
      Klick-zum-Kopieren) oder `plugin-clipboard-manager`.
- [ ] **U4 (Bug, minor):** `uploadFiles` (`FileExplorer.vue:123-142`) setzt
      `busyPath = ""` → falsy → Re-Entry-Guards in `open`/`download` (Z. 94,
      109) greifen nicht; Mehrupload bricht beim ersten Fehler ab und der
      Erfolgs-Toast fehlt. Fix: Token/Set verwenden, Fehler pro Datei sammeln.
- [ ] **U5 (UX, minor):** „Sync now" (`SyncPanel.vue:59-62`) toastet „Sync
      started." auch wenn `sync.trigger()` (store `sync.ts:72-78`) einen
      Fehler verschluckt hat. Fix: Fehler propagieren, nur bei Erfolg toasten.
- [ ] **U6 (UX, minor):** Konto-Entfernung ohne Bestätigungsdialog
      (`AccountBar.vue:129-134`, `App.vue:60-70`) — im Gegensatz zu
      Datei-/User-Löschungen. Fix: `confirm()` + i18n-Key.
- [ ] **U7 (UX, minor):** „Add account" öffnet den Login-Dialog im zuletzt
      gewählten Modus (Register bleibt Register) — `App.vue:111`
      (`@login="showLogin = true"`) reicht kein `loginMode` durch, das
      `LoginModal` (Z. 38-44) setzt `mode` nur bei `initialMode`-Änderung.
      Fix: `openLogin('login')` oder Mode bei jedem Öffnen neu setzen.

### Review 2026-08-14 (Lauf 2, v1.0.0-Bereitschaft)

- [ ] **F1 (Blockierer):** AdminPanel-Button „enabled" funktioniert nie —
      `adminEditUser(id, "enabled", …)` in `src/components/AdminPanel.vue:143`
      vs. `ADMIN_EDIT_KEYS`-Whitelist ohne `enabled` in
      `src-tauri/src/commands.rs:550-570`. Entweder `enabled` in die Whitelist
      aufnehmen (`ocs::update_user` unterstützt es, Nextcloud 25+) oder Button
      entfernen.
- [ ] **F2 (Blockierer):** Total-Timeout 60 s (state.rs:112-113) bricht große
      WebDAV-Transfers ab; 30 s (updater.rs:526-527) bricht
      Installer-Downloads ab. Auf `connect_timeout`/`read_timeout` umstellen
      (reqwest 0.13).
- [ ] **F4 (Blockierer):** Doppel-URL-Encoding in `ocs::create_share`
      (ocs.rs:262-273): `encode_segments` + `req.form()` → Shares für Pfade mit
      Leerzeichen/Umlauten/Sonderzeichen schlagen fehl. Raw-Pfad ins Formular,
      Roundtrip-Test ergänzen.
- [ ] **F5:** About-Tab zeigt hartkodierte Version „0.1.0"
      (SettingsModal.vue:237) — via `getVersion()` auflösen.
- [ ] **F3:** Updater räumt partielle Downloads bei Stream-Fehlern nicht auf
      (updater.rs, Stream-Schleife ~260-283, analog B18).
- [ ] **F6:** `load_accounts` verwirft still Konten bei fehlender/abweichender
      FLUTCLOUD_URL (accounts.rs:84-94) — Hinweis/Status an Frontend
      durchreichen.
- [ ] **F7:** Automatischer Update-Check beim App-Start (aktuell nur manuell in
      SettingsModal) — nicht-blockierendes Update-Banner.
- [ ] **F8:** Signing/Notarisierung nur dokumentiert (release.yml:116-141,
      Fallback ad-hoc/unsigned) + `opencode.yml` pinnt
      `anomalyco/opencode/github@latest` nicht (auf Tag pinnen).
- [ ] **F9:** SHA-256-Prüfung still übersprungen, wenn GitHub keinen
      Asset-Digest liefert (updater.rs:302-310) — Warnung/Log ergänzen, besser
      eigenen `.sha256`-Anhang im Release-Workflow erzeugen.
- [ ] **F10:** Sync: gleiche lokale Ordnernamen kollidieren beim remote_path
      (commands.rs:618-633) — i18n-Fehltext präzisieren (kein Blocker).

**Priorität für v1.0.0:** Muss — F1, F2, F4 (F5 kosmetisch). Sollte — F3, F6,
F7, F8, F9. Kein Blocker — F10. Verifikation Stand 2026-08-14:
`cargo test` 41 passed, `cargo clippy -D warnings` grün, `npm run build` ok.

## Archiv (erledigt)

### Review 2026-08-16 (Sharing vervollständigen)

- [x] **P1 (Feature, mittel/hoch — Kern-Fokus):** Private Freigaben fehlten
      durchgängig (nur `shareType=3` + `permissions=1` hartkodiert). Fix:
      `ocs::create_share` akzeptiert jetzt `ShareOptions` (`share_type`,
      `share_with`, `password`, `expire_date`, `permissions`, `public_upload`);
      `webdav_create_share` reicht die Optionen per `ShareInput`-Parameter
      durch und verweigert User-/Gruppen-Shares ohne `share_with`; der
      FileExplorer-Dialog bietet „Mit Benutzer teilen" / „Mit Gruppe teilen".
- [x] **P2 (Feature, mittel):** Kein Share-Management. Fix: `list_shares`
      (OCS `GET /shares`, optional mit `path`) und `delete_share`
      (OCS `DELETE /shares/{id}`) als `ocs`-Funktionen + Commands
      `webdav_list_shares`/`webdav_delete_share` (commands.rs, in lib.rs
      registriert) + IPC-Wrapper. Die UI zeigt pro Eintrag ein
      Freigabe-Badge, und der Share-Dialog listet alle Shares des Eintrags
      mit Widerruf-Button und Copy-Link-Button.
- [x] **P3 (Feature, mittel):** Link-Optionen fehlten. Fix: `create_share`
      unterstützt `password`, `expireDate` und `publicUpload` (permissions 15,
      explizite `permissions` haben Vorrang). UI-Formular im Share-Dialog
      (Passwort, Ablaufdatum, öffentlicher Upload).
- [x] **P4 (Bug, bestätigt F4):** Doppel-Encoding bestätigt und abgesichert:
      Der Raw-Pfad geht in `build_share_form` unencodiert ins Formular
      (Roundtrip-Test `share_form_keeps_path_raw_for_roundtrip`).
- [x] **P6 / U3 / N7:** Der alte `createLink` ist durch den Share-Dialog
      ersetzt: Die URL bleibt nach dem Erstellen sichtbar (Klick-zum-Kopieren),
      Clipboard-Fehler toasten, Backend-Fehler werden per `invokeError` toastet.
- [x] **Tests:** Neue OCS-Unit-Tests für Share-Form (Raw-Pfad, User/Gruppen-
      Parameter, Link-Optionen inkl. permissions-Vorrang) und `parse_share`-Mapping.
      Verifikation: `cargo fmt --check`, `cargo clippy -D warnings`,
      `cargo test` (53 passed), `npm run build` — alles grün.

### Review 2026-08-15 (U8)

- [x] **U8 (Feature/Design, mittel):** Kein Material-3-Expressive-Design
      umgesetzt. Fix umgesetzt: M3-Token-System in `src/style.css` unter
      `[data-theme]` (State-Layer, Elevation, Shape, Motion, `:focus-visible`-
      Ringe, M3-Ripple), dynamische Farbpalette (Material You, konfigurierbarer
      Accent-Hue in den Settings, `--m3-accent-hue`), SVG-Icon-Set
      (`src/components/Icon.vue` mit Material-Icons-Pfaden) ersetzt alle Emojis
      (📁/📄/☰/▦/⚙/🔒/✕/✓/✗), Ripple-Feedback über `src/lib/ripple.ts`
      (delegierter Pointerdown). Alle Komponenten (App, FileExplorer,
      SettingsModal, LoginModal, AdminPanel, SyncPanel, AccountBar,
      WelcomeScreen, ToastStack) auf Tokens umgestellt statt hartkodierter
      Klassen (`bg-indigo-600`, `text-indigo-300`, `bg-indigo-950/40`); Theme
      liegt jetzt auf `document.documentElement` (Teleport-Overlays erben
      Tokens), Accent-Wert persistiert in localStorage. Verifikation:
      `npm run build` grün (vue-tsc + vite).

### Review 2026-08-15 (N14)

- [x] **N14 (i18n, mittel):** Backend-Fehlermeldungen waren nicht lokalisiert:
      `error.rs` (`message()`), `ocs.rs`, `webdav.rs` und `updater.rs` lieferten
      englische Strings, die direkt als Toast/Inline-Fehler erschienen. Fix:
      Backend serialisiert jetzt `{code, message, detail}` (neue
      `AppError::detail()`, `Update`-Variant, `PassError` für Sync-Status);
      Frontend mappt Codes über `translateError()`/`ERROR_CODE_KEYS` in
      `src/lib/i18n.ts` auf en/de-Texte (`err*`-Schlüssel). `updater.rs`
      liefert strukturierte `update://status`-Payloads; `SettingsModal.vue`
      und `SyncPanel.vue` rendern nur noch lokalisierte Fehler.

### Review 2026-08-15 (N10)

- [x] **N10 (Konsistenz, minor):** `webdav_create_share` (`commands.rs:326-337`)
      rief im Gegensatz zu allen anderen `webdav_*`-Commands **kein**
      `validate_dav_path` auf → Shares auf `resources`/`parts`-Virtual-Pfaden
      oder mit `..` waren möglich. Fix: `validate_dav_path(&path)?` ergänzt.

### Review 2026-08-15 (N15)

- [x] **N15 (UX, minor):** Nach Admin-Login setzt `loadAdminUsers`
      (`FileExplorer.vue:214-231`) `targetUser` auf den **eigenen** Namen →
      Banner „Admin impersonation — … von <ich>" (Z. 377-383) erscheint beim
      Betrachten der eigenen Dateien. `webdav_list` filtert den eigenen Namen
      zwar heraus (`commands.rs:307`), das Banner bleibt aber irreführend.
      Fix: Banner nur bei fremdem `targetUser` anzeigen. → Banner-Bedingung
      in `FileExplorer.vue` prüft jetzt `targetUser !== username`.

### Review 2026-08-15 (U11)

- [x] **U11 (Bug, minor):** `selected`-Set in `FileExplorer.vue` wurde nach
      einem Rename nie bereinigt — `files.renameEntry` refresht die Liste, der
      alte Pfad blieb in `selected` und der Zähler zeigte stale Einträge. Fix:
      `doRename` (Z. 162-177) ersetzt nach erfolgreichem Rename den alten Pfad
      im `selected`-Set durch den neuen (analog zu `removeEntry`).

### Review 2026-08-14 (Lauf 5, N12)

- [x] **N12 (Bug, Datenverlust, mittel):** `unique_conflict_target`
      (`sync.rs:149-163`) prüfte für `MoveLocalConflict` (`sync.rs:425-427`)
      Kollisionen gegen die **remote**-Map statt gegen die lokale Dateiliste:
      Existierte lokal bereits „a (conflict copy).txt", wurde es von
      `exec_move_local_conflict` (`tokio::fs::rename`) **überschrieben** — die
      frühere Konfliktkopie ging verloren. Fix: `unique_conflict_target`
      prüft den Kandidaten jetzt gegen `local` **und** `remote` (Parameter
      erweitert), Unit-Test ergänzt.

### Review Lauf 2 2026-08-13 (Fokus: Features bis v1)

- [x] **V1.1 - Bug:** `sync_add` (commands.rs:410-416) erlaubt kollidierende `remote_path` für gleichnamige lokale Ordner (`/home/a/Docs` + `/home/b/Docs` → beide `/FlutLink/Docs`); Duplikat-Check in `sync.rs::add_folder` (Z. 671-678) prüft nur `account_key`+`local_path`. Verifiziert per Logik-Replikation. Eindeutigen Remote-Namen ableiten oder `remote_path` in die Prüfung aufnehmen. → Duplicate-`remote_path`-Check in `sync_add` ergänzt.
- [x] **V1.2 - Bug:** Typ-Konflikt Datei↔Ordner in `sync.rs` (`decide` Z. 236-289, `exec_download` Z. 427-441): lokaler Ordner vs. Remote-Datei (mit Journal) → `DeleteRemote` (Remote-Datei wird gelöscht!); lokale Datei vs. Remote-Ordner → `Skip` (still ignoriert); Erst-Sync Ordner vs. Datei → Download bricht mit `File::create`-Fehler ab. Alle 3 Fälle verifiziert. Als Konflikt behandeln statt löschen/ignorieren. → `MoveRemoteConflict`/`MoveLocalConflict` in `decide`/`plan_ops`/Executors.
- [x] **V1.3 - Bug (Minor):** `sync.rs::set_paused` (Z. 716-732): Resume setzt `paused=false`, aber `state` bleibt "paused" bis zum nächsten Worker-Tick (bis 10 s). Bei Resume sofort auf "idle" setzen.
- [x] **V1.4 - Feature:** `account_remove` (commands.rs:241-255) räumt Sync-Ordner des Kontos nicht ab; `sync.rs::run_all` (Z. 800-806) zeigt dann ewig "Account is no longer connected." Sync-Ordner (+ Journals) mitentfernen oder im UI als verwaist markieren/löschbar machen. → `remove_folders_for_account` entfernt Sync-Ordner + Journal-Dateien.
- [x] **V1.5 - Feature:** Updater (`updater.rs::download_update` Z. 223-281) verifiziert den Download nicht (keine SHA-256-Checksumme); kein Auto-Update-Check beim Start (SettingsModal.vue `checkForUpdate` Z. 95-106). Checksumme prüfen, optional Auto-Check. → SHA-256- und Größen-Verifikation in `download_update`.
- [x] **V1.6 - Feature (v1-Blocker):** Keine Dateioperationen im Dateibrowser: `FileExplorer.vue` `open` (Z. 21-23) tut bei Dateien nichts; `webdav.rs`-Helper (`put_file`/`get_file`/`delete`/`make_collection`) sind nicht als Commands exponiert (`commands.rs`, `ipc.ts`). Upload/Download/Öffnen/Rename/Mkdir/Delete bis v1. (Überlappt mit bestehendem B9/U2.) → Commands + UI umgesetzt.
- [x] **V1.7 - Bug (Minor):** `flutcloud.rs::flutcloud_url` (Z. 17-28) cached den Fehlerfall in `OnceLock` — korrigierte `.env` wirkt erst nach Neustart. Nur Erfolgswert cachen.
- [x] **V1.8 - Feature/Infrastruktur (v1-Blocker):** `release.yml` (Z. 121) baut macOS nur mit Ad-hoc-Signing (`APPLE_SIGNING_IDENTITY: "-"`), Windows ohne Code-Signing, keine Notarisierung. Developer-ID + Notarisierung (macOS) und Code-Signing (Windows) für Distribution einplanen. → Signing-/Notarisierungs-Plan in `release.yml` (secrets-gated, Fallback ad-hoc/unsigned).

### Review 2026-08-13 (automatisiertes Code-Review, Fokus UI + Backend)

- [x] **F1/B1** Konto-Identifikation auf `username` + `instanceUrl` umstellen: `src/stores/accounts.ts` (Z. 51, 74), `src-tauri/src/state.rs` (`remove`/`set_active`), `src-tauri/src/commands.rs` (`account_switch`, `account_remove`). Bug: gleicher Username auf zwei Servern wird falsch behandelt.
- [x] **B3** Eindeutige Konfliktkopie-Namen im Sync: `src-tauri/src/sync.rs` (`conflict_name`, `exec_upload_conflict`) — zweiter Konflikt überschreibt erste Kopie. → `conflict_name_n` + BTreeSet über den Pass.
- [x] **B2** `register_user` Teilfehler behandeln (Konto existiert bereits, wenn README/Projektordner-Erstellung fehlschlägt): `src-tauri/src/commands.rs` Z. 133-204. → best-effort, `err.message()`.
- [x] **F3** Race Condition im Dateibrowser: Requests sequenzieren + Loading-Feedback bei bestehenden Entries: `src/stores/files.ts`, `src/components/FileExplorer.vue`. → Sequenz-Guard in `files.ts`.
- [x] **B4** Leere Ordner synchronisieren (walk_local sammelt nur Dateien; decide skippt leere Remote-Ordner): `src-tauri/src/sync.rs`.
- [x] **B5** `statuses()` beim App-Start aus `sync-folders.json` initialisieren: `src-tauri/src/sync.rs` Z. 735-746.
- [x] **B6** `delete_token`-Fehler in `account_remove` nicht verschlucken: `src-tauri/src/commands.rs` Z. 235. → `?`-Propagation.
- [x] **B7** WebDAV-Transfer-Helper (`remote_url`) um `target_user`-Impersonation erweitern: `src-tauri/src/nextcloud/webdav.rs`.
- [x] **B8** Whitelist für `admin_edit_user`-Keys (displayname/email/password/enabled/quota): `src-tauri/src/commands.rs` Z. 339-351. → `ADMIN_EDIT_KEYS`.
- [x] **B9** Transfer-/Datei-Commands exponieren (upload/download/delete/mkdir/rename/search) in `commands.rs` + `src/lib/ipc.ts` (Grundlage für UI-Dateioperationen).
- [x] **B11** `tmp_path()` eindeutig machen für parallele Downloads: `src-tauri/src/nextcloud/webdav.rs` Z. 241-248. → AtomicU64-Zähler.

### UI „Google Drive mit FlutLink-Farben"

- [x] **U1** Dateibrowser: Grid/Listen-Umschalter, sortierbare Spalten, Mehrfachauswahl, Kontextmenü, Hover-Aktionen: `src/components/FileExplorer.vue`.
- [x] **U2** Dateioperationen in der UI: Upload, Download/Öffnen, Umbenennen, Neuer Ordner, Löschen (Backend: B9).
- [x] **U3** Zentrale FlutLink-Farbvariablen statt hartkodierter `indigo-*`-Klassen; helles Theme; `src/style.css`, `src/App.vue`.
- [x] **F2** System-Theme-Auswahl implementieren oder entfernen: `src/App.vue` Z. 37-39, 49-51 (aktuell No-Op). → `matchMedia`-Auswertung.
- [x] **F4** Doppeltes "frei" in Speicheranzeige: `src/components/AccountBar.vue` Z. 44-52/122.
- [x] **F5** Fehlende i18n-Keys: "Hide"/"Show" (`LoginModal.vue`), "Close" (`SettingsModal.vue`), "Email" (`AdminPanel.vue` Z. 353), "Home" (`src/stores/files.ts` Z. 14).
- [x] **F6** Unbehandelte Promise-Rejection bei `accounts.remove` in `src/App.vue` Z. 185 (try/catch + Toast).

### CI / Repo

- [x] **C1** `ci.yml`: Clippy mit `-D warnings` oder Job durch `.github/actions/checks` ersetzen. → `ci.yml` entfernt; `build.yml` nutzt `checks`-Action.
- [x] **C2** `ci.yml` und `build.yml` zusammenlegen (Redundanz). → `ci.yml` entfernt.

### Review vom 2026-08-13 (neue Befunde)

- [x] **B1 - Bug:** `account_add` (commands.rs:47-61): Erneutes Hinzufügen des aktiven Kontos setzt `is_active=false`, `current()` fällt auf ein anderes Konto zurück. `is_active` nach dem `upsert` bestimmen oder Status vom ersetzten Eintrag übernehmen.
- [x] **B2 - Bug:** `src/App.vue` `resolveTheme` (Z. 37-39): Theme "system" wird immer auf `operationflut` abgebildet, `prefers-color-scheme` wird ignoriert. Dunkel-Präferenz auswerten oder Option entfernen.
- [x] **B3 - Feature:** `webdav_create_share` (commands.rs:264-268) unterstützt kein `target_user`; Share-Erstellung im Impersonations-Modus zielt auf das Admin-Konto. `target_user` ergänzen.
- [x] **B4 - Feature:** `list_users` (ocs.rs:79) limitiert hart auf 200 Benutzer; Pagination über `offset` implementieren.
- [x] **B5 - Bug:** `ocs_current_user` (commands.rs:353-357) ist registriert, hat aber keinen IPC-Wrapper in `src/lib/ipc.ts` und keine Frontend-Nutzung. Nutzen oder entfernen. → entfernt.
- [x] **B6 - Bug:** Event `sync-folders-changed` (lib.rs:92, CLI `--path`) wird nirgends gelauscht; Sync-Panel zeigt neue Ordner erst nach Reload. Listener + `sync.load()` ergänzen.
- [x] **B7 - Doku/Feature:** README (Z. 63) behauptet "change notifications"; es gibt keinen Dateisystem-Watcher (keine notify-Crate). Doku korrigieren oder Watcher ergänzen. → Doku korrigiert.
- [x] **B8 - Bug (Minor):** `accounts.ts` `add()` (Z. 47-61): Nach B1-Szenario laufen Store und Backend beim aktiven Konto auseinander. Nach B1-Fix `add()` um Reload ergänzen.
- [x] **B9 - Robustheit:** `webdav.rs` `list` (Z. 39-50): Wenn der Server `Impersonate-User` ignoriert, entstehen kaputte Pfade; Namespace der hrefs mit `effective_user` abgleichen und klaren Fehler werfen. → Namespace-Guard (`/remote.php/`).
- [x] **B10 - i18n:** `AdminPanel.vue` `setQuota` (Z. 124) und `createUser` (Z. 173): zusammengesetzte Fehlerstrings mit hartkodierten Trenner; eigene i18n-Schlüssel mit Platzhaltern. → `quotaInvalid`/`userFieldsRequired`.
- [x] **B11 - Feature:** `sync.rs` `decide`/`walk_local`/`plan_ops`: Leere lokale Ordner werden nicht remote erstellt, leere Remote-Ordner nie gelöscht (nur Datei-Sync).
- [x] **B12 - CI:** `ci.yml` (Z. 60): Clippy ohne `-- -D warnings`, inkonsistent zu `checks`/`lint`-Actions und AGENTS.md. Vereinheitlichen. → `ci.yml` entfernt.
- [x] **B13 - CI/Security:** `opencode.yml`: Kommentar-Trigger mit `id-token: write` + `contents: write`; Prompt-Injection-Risiko minimieren, `id-token: write` entfernen, Prompt so formulieren, dass Kommentar nur Aufgabenbeschreibung ist.
- [x] **B14 - Security:** `tauri.conf.json` (Z. 23): `"csp": null`; CSP (`default-src 'self'`) setzen.
- [x] **B15 - Cleanup:** `@tauri-apps/plugin-opener` ist registriert (lib.rs:116), aber im Frontend ungenutzt. Nutzen oder entfernen. → in `FileExplorer.vue` genutzt.
- [x] **B16 - CI:** `ci.yml` (Z. 37-46): Linux-System-Dependencies doppelt installiert (auch in `setup`-Action); auf die Action umstellen. → `ci.yml` entfernt.
- [x] **B17 - Robustheit:** `accounts.rs` `save_token`/`load_accounts`: Ohne Secret-Service (Linux) ist die App unbenutzbar; Hinweis/Fehlermeldung für Linux-Nutzer ergänzen.
- [x] **B18 - Robustheit:** `webdav.rs` `get_file` (Z. 147-159): `.flutlink-<pid>.tmp`-Reste bei Stream-Fehlern nicht aufgeräumt; `remove_file(&tmp)` im Fehlerpfad ergänzen.
