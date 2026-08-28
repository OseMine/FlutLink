# Archiv (erledigt)

Alle erledigten Aufgaben aus `todo.md`, sortiert nach Review/Lauf.


## Erledigt (2026-08-25, Review-Läufe 17–19 abgeschlossen — Abschnitte verschoben)

Alle Befunde der drei Review-Abschnitte vom 2026-08-24 (Lauf 17 „ganzes
Projekt", Lauf 18 „Cross-Platform-Feature-Ideen", Lauf 19 „Desktop UI")
sind seit HEAD `f9b7351` im Code umgesetzt und am 2026-08-25 (Review
Lauf 20) gegen den aktuellen Stand verifiziert: `cargo fmt --all --check`,
`cargo clippy --all-targets -- -D warnings` und `cargo test` (108 passed /
0 failed) grün, `npm run build` grün. Einzelnachweise der Verifikation in
`todo.md` unter „Review 2026-08-25 (Lauf 20)" → Schritt 5. Weiterhin offen
aus diesen Läufen ist nur der Punkt „Desktop-JVM: Token-Speicher härten"
(`FileKeyValueStorage`) — er bleibt in `todo.md` unter „Offen" geführt.

## Review 2026-08-24 (Lauf 19, Fokus Desktop UI — neue Befunde)

Gegenstand: Desktop-UI (`src/`: `App.vue`, `FileExplorer.vue`, `EntryList.vue`,
`AccountBar.vue`, `AdminPanel.vue`, `SyncPanel.vue`, `LoginModal.vue`,
`SettingsModal.vue`, `WelcomeScreen.vue`, `ToastStack.vue`, Pinia-Stores,
`lib/ipc.ts`, `lib/i18n.ts`, `lib/sort.ts`, `lib/format.ts`, `lib/ripple.ts`)
plus die Standard-Bereiche (IPC-Commands, WebDAV/OCS, Keyring,
Fehler-/State-Management, CI) und die Nachprüfung der offenen
L17-/L18-/CP-Befunde gegen HEAD (`dd7fdb7`).

Neu gefunden:

- [x] **L19-F1 (Bug, mittel): Split-View + Grid-Ansicht: tote Hover-Buttons —
      die beiden Split-View-`EntryList`s binden `@download`/`@delete`/`@share`
      nicht.** `FileExplorer.vue:1088-1104` (linke Panes) und `:1122-1138`
      (rechte Pane) binden nur `open/toggle-select/contextmenu/rename/
      create-link/copy-link/pair/toggle-sort`; die Grid-Hover-Overlay-Buttons
      in `EntryList.vue:275-317` emittieren aber zusätzlich `download`,
      `delete` und `share`. Schaltet man in einer `resources`/`parts`-
      Split-Ansicht auf Rasteransicht, passieren Download-, Löschen- und
      Share-Overlay-Button schlicht nichts (Emit ohne Listener). Außerdem
      fehlen den Split-View-Instanzen die Props `:thumbs`,
      `:shares-by-path` und `:searching` (nur die Vollflächen-Listen
      `:1145-1167`/`:1172-1194` bekommen sie) — keine Vorschauen/
      Freigabe-Badges in der Split-Ansicht. Fix: Event-Bindings + Props auf
      beide Split-View-Instanzen ergänzen.
- [x] **L19-F2 (Robustheit, mittel): `webdav_bulk_delete` validiert die Pfade
      erst innerhalb der Lösch-Schleife — ein geschützter Pfad mitten in der
      Auswahl führt zu einem teilweisen Bulk-Delete.** `commands.rs:914-919`
      ruft `validate_dav_path(path)?` erst pro Iteration; Pfade vor dem ersten
      ungültigen sind bereits gelöscht, wenn der Guard abbricht. Der
      Geschwister-Command `webdav_bulk_download` validiert dagegen alle
      Targets vorab (`commands.rs:1006-1008`). Fix: denselben
      Pre-Validierungsloop übernehmen (oder `validate_dav_path` für alle
      `paths` vor der Schleife ausführen).
- [x] **L19-F3 (Bug/UX, mittel): Sync-Ordner werden ohne Bestätigungsdialog
      entfernt — der Klick löscht den Ordner **und** sein Sync-Journal
      unwiderruflich.** `SyncPanel.vue:64-71` (`remove`) ruft direkt
      `sync.remove(folderId)`; backendseitig wirft
      `SyncEngine::remove_folder` (`sync.rs:1364-1375`) Ordner, Status und
      Journal-Datei weg. Ein Versehen bedeutet: Sync stoppt, Journal weg —
      beim erneuten Hinzufügen läuft alles als Erst-Sync (Konfliktkopien-
      Risiko). Inkonsistent zum F7-Muster (Konto-Entfernung, Datei-Löschen,
      Share-Widerruf fragen jeweils per `window.confirm`). Fix: Bestätigung
      wie `deleteSelectedConfirm` ergänzen.
- [x] **L19-F4 (UX-Konsistenz, minor): Der Rename-Dialog schließt auch bei
      invalidem Namen — die getippte Eingabe geht verloren.**
      `doRename` (`FileExplorer.vue:438-469`) zeigt zwar den Toast
      „Ordnername ungültig", aber das `finally` setzt `renameTarget = null`
      und schließt damit den Dialog; `createFolder` (`:413-431`) hält den
      Dialog bei demselben Fehler offen. Fix: Validierung vor dem
      Dialog-Close handhaben (Close nur bei Erfolg).
- [x] **L19-F5 (Bug, minor): `nameInput` wird zwischen Neu-Ordner- und
      Rename-Dialog geteilt und bei Abbruch nicht geleert — der nächste
      Dialog startet mit Alt-Inhalt vorbelegt.** Abbruch-Pfade
      (`showNewFolder = false` Button `:1255-1257`, Rename-Cancel/
      Backdrop `:1273`, `:1286-1288`) setzen `nameInput` nicht zurück; nur
      der Erfolgs-Pfad von `createFolder` leert es (`:425`). Öffnet man nach
      einem abgebrochenen Rename „Neuer Ordner", ist das Feld mit dem alten
      Dateinamen vorbelegt und der Create-Button aktiviert
      (`:disabled="nameInput.trim().length === 0"`, `:1260`). Fix:
      `nameInput.value = ""` beim Öffnen/Abbrechen beider Dialoge.
- [x] **L19-F6 (Bug, minor): `setQuota` im Admin-Panel lässt `NaN` durch und
      sendet `"NaN"` als Quota an die OCS-API.** Das Quota-Feld bindet
      `edits.quotaValue = $event.target.valueAsNumber`
      (`AdminPanel.vue:632-640`) — bei geleertem/ungültigem Feld ist das
      `NaN`; die Prüfung in `setQuota` (`AdminPanel.vue:280-288`,
      `value === null || value <= 0`) greift bei `NaN` nicht (`NaN <= 0`
      ist false), sodass `String(Math.round(NaN))` = `"NaN"` an
      `admin_set_user_quota` geht und der Server-Fehltext statt des
      lokalisierten `quotaInvalid` erscheint. Fix: `!Number.isFinite(value)`
      mitprüfen.
- [x] **L19-F7 (i18n, minor): Der Update-Banner zeigt rohe Backend-Statuscodes
      statt lokalisierter Texte.** `App.vue:166-171` rendert im
      Banner-Fortschritt `${e.payload.code}` (z. B. „downloading",
      „installing") als Klartext; nur `checksum_warning` wird übersetzt.
      Das SettingsModal hat mit `updateStatusText`
      (`SettingsModal.vue:206-222`) bereits die vollständige Code→Key-Map —
      der Banner sollte dieselbe Übersetzung nutzen.
- [x] **L19-N1 (UX, minor): Escape schließt weder Kontextmenü noch Modals.**
      Das Datei-Kontextmenü (`FileExplorer.vue:1198-1235`) schließt nur per
      Außenklick (`@click="closeCtx"` am Container `:802`), LoginModal/
      SettingsModal/New-Folder/Rename/Share-Dialoge reagieren ebenfalls
      nicht auf `Escape` (nur `@click.self`). Auf dem Desktop ist Escape zum
      Schließen von Menüs/Modals erwartetes Verhalten; ein zentraler
      `keydown.escape`-Handler würde genügen.

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs` `generate_handler!` deckungsgleich mit `src/lib/ipc.ts`, alle
Wrapper typisiert), Keyring (`accounts.rs` save/load/delete +
Linux-Hint-Mapping, `token_missing`-Reporting), Fehler-Serialisierung
(`error.rs` code/message/detail inkl. TargetExists/SyncFolderConflict),
Offline-Cache (`cache.rs` atomic write + Quarantäne kaputter Files + LRU-
Eviction), WebDAV-Layer (`webdav.rs`: Impersonation-Namespace-Guards,
Chunked-v2-Cleanup, temp+rename Downloads, TOCTOU-Guards), OCS-Layer
(`ocs.rs`: Share-Owner-Verifizierung, Dedup-Pagination mit Progress-Guard,
einzelne Form-Encoding-Kette), FlutCloud-only-Policy (`flutcloud.rs`
URL-Lock + Capability-Probe), Updater (`updater.rs` SHA-256-Gate,
Asset-Name-Härtung, Size-Match), Tray/CLI (`lib.rs` Tray-Menü-Rebuild,
Close-to-Tray, CLI-Flags) sowie Theme/i18n-Grundlagen (`ui.ts`
localStorage-Persistenz, `i18n.ts` ERROR_CODE_KEYS vollständig gegenüber
`error.rs::code()` bis auf den bekannten L17-F4-Auslass).

Verifikation frisch ausgeführt (HEAD `dd7fdb7`): Tauri-Linux-Systemdeps
waren nachzuinstallieren; danach `cargo fmt --all --check` grün;
`cargo clippy --all-targets -- -D warnings` grün;
`cargo test --manifest-path src-tauri/Cargo.toml` → **103 passed /
0 failed**; `npm run build` (vue-tsc + vite) grün (Haupt-Chunk ~118 kB,
Code-Splitting weiterhin wirksam).

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `dd7fdb7`)

Seit dem letzten Lauf ist kein Commit hinzugekommen (`dd7fdb7` = Merge von
Lauf 18), daher sind **alle offenen Einträge unverändert offen** — keiner
ist erledigt, also gibt es dieses Mal nichts nach `archived-todo.md` zu
verschieben. Stichprobenhafte Bestätigung:

- L17-F1 bestätigt: `bulkDelete` setzt `busyPath` vor dem Confirm
  (`FileExplorer.vue:134-148`).
- L17-F2 bestätigt: `webdav_thumbnail`/`open_remote_file`/
  `webdav_download_file`/`webdav_download_zip` laufen weiter durch
  `validate_dav_path` (`commands.rs:823`, `:738`, `:706`, `:790`);
  `getThumbnail` fängt alle Fehler (`src/stores/files.ts:263-269`).
- L17-F3 bestätigt: `accounts.rs:70` und `sync.rs` (`persist`) schreiben
  weiter via `std::fs::write`.
- L17-F4 bestätigt: `walk_incomplete` fehlt weiterhin in
  `ERROR_CODE_KEYS` (`i18n.ts:631-649`).
- L17-N1 bestätigt: `kmp.yml:129` (setup-android v4.0.1) vs.
  `action.yml:30` (v3.2.2). — L17-N2 bestätigt: `flutcloud.yml:79-81`
  lintet nur `scripts/install-flutcloud-app.sh`. — L17-N3 bestätigt
  (`LoginModal.vue:99-120`). — L17-N4 bestätigt (`ocs.rs:318`).
- CP-N1 bestätigt: `FlutCloudOcs.kt:107` bricht weiter bei
  `groups.size < limit` ab (übrige CP-Befunde betreffen `kmp/` und sind
  seit `dd7fdb7` ebenfalls unverändert).
- „Desktop-JVM: Token-Speicher härten" bleibt offen:
  `FileKeyValueStorage.kt` dokumentiert die Keyring-Anbindung weiterhin
  als Follow-up.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe sind in diesem Lauf
verboten): `git log` zeigt **keinen** Commit nach `dd7fdb7` (Merge des
Lauf-18-Berichts #328) — damit sind seit Lauf 18 keine neuen Fixes oder
Issue-Schließungen im Repo sichtbar; ob parallel offene Issues entstanden
sind, ist hier nicht prüfbar. Der `opencode-todo-issues`-Workflow
(wöchentlich mittwochs, `.github/workflows/opencode-todo-issues.yml`)
sollte beim nächsten Lauf die L19-Befunde oben als Issues erfassen und die
weiterhin offenen L17-/L18-/CP-Befunde erneut einplayen (allesamt laut
Schritt 5 noch offen).

## Review 2026-08-24 (Lauf 18, Fokus Cross-Platform-Feature-Ideen — neue Befunde)

Gegenstand: plattformübergreifende Feature-Parität und -Ideen zwischen dem
Tauri-Desktop-Client (`src-tauri/`, `src/`) und dem mobilen KMP-Client
(`kmp/`: `commonMain`/`androidMain`/`iosMain`/`jvmMain`); zusätzlich die
Standard-Bereiche (IPC, WebDAV/OCS, Keyring, Fehler-/State-Management, CI)
und die Nachprüfung der offenen L17-Befunde gegen HEAD (`4f34a9a`).

Verifikation: siehe Abschnittsende.

Neu gefunden (Cross-Platform-Parität Desktop ⇔ KMP):

- [x] **CP-F1 (Paritäts-Bug, mittel): Share-API ohne Impersonation im
      KMP-Client — Admin-Shares landen im falschen Namespace.** Der Desktop
      reicht `target_user` durch alle Share-Commands
      (`commands.rs:514-586`: `webdav_create_share`/`webdav_list_shares`/
      `webdav_delete_share`, Admin-Gate je Command) und guarded die Antwort
      serverseitig via `request_as` + `verify_share_owner`
      (`ocs.rs:431-484`, Owner-Filter in `list_shares` :576-585). Der KMP-
      Client hat davon nichts: `FlutCloudOcs.kt:138-179`
      (`createShare`/`listShares`/`deleteShare`) kennen keinen
      `targetUser`-Parameter und setzen nie `Impersonate-User`;
      `ShareDto`/`Share` (`Models.kt:44-65`, `:122-133`) haben nicht einmal
      ein `uid_owner`-Feld für einen Owner-Guard; `FilesViewModel.createShare/
      loadShares/deleteShare` (`FilesViewModel.kt:322-389`) reichen
      `targetUser.value` nicht durch. Folge beim Impersonation-Browsing:
      „Link erstellen" schickt den Pfad des Zielnutzers ohne
      Impersonate-Header (OCS-Fehler oder Share im Admin-Namespace), und der
      Share-Dialog listet die Shares des **Admins**. Fix: `targetUser`
      durchreichen + Header setzen (analog `WebDavApi.impersonate`)
      + `uid_owner`-Guard portieren.
- [x] **CP-F2 (Paritäts-Bug, mittel): `rename` validiert den neuen Namen
      nicht — ein Name mit `/` wird still zum MOVE, `..` escape't den
      Ordner.** Desktop: `validate_rename_name` (`commands.rs:629-636`)
      lehnt `""`/`.`/`..`/`/` ab. KMP: `FilesViewModel.rename`
      (`FilesViewModel.kt:284-299`) prüft nur `isBlank`/Unverändert und
      baut `newPath = parent + "/" + newName` — `"a/b"` erzeugt einen MOVE
      in einen Unterordner (Rename wird zum Verschieben), `".."` springt in
      den Elternordner; auch das `RenameDialog`-UI validiert nur
      `isNotBlank` (`FilesScreen.kt:663`). `mkdir` zeigt die richtige
      Validierung direkt nebenan (`FilesViewModel.kt:265`). Fix: dieselben
      Regeln auf den Rename-Namen anwenden (lokalisiert als
      `error_invalid_folder_name`).
- [x] **CP-F3 (Feature-Idee/Parität, mittel): Kein Chunked-Upload v2 mobil —
      große Uploads sind ein einzelner PUT.** Der Desktop lädt Dateien
      > 10 MiB über das Nextcloud-Chunked-v2-Protokoll hoch
      (`webdav.rs:22-31` Schwellen/Konstanten, `chunked_put_v2`
      `webdav.rs:390-502` inkl. `OC-Total-Length`-Quotavorprüfung und
      Session-Cleanup). KMP `WebDavApi.uploadStream` (`WebDavApi.kt:224-238`)
      streamt zwar aus dem Speicher heraus, aber als einen einzigen PUT —
      bei großen Dateien drohen Client-Timeouts, und die Server-Quota wird
      erst am Ende geprüft. Idee: Chunked-v2-Port nach `commonMain`.
- [x] **CP-N1 (Bug/Parität, minor): Der L17-N4-Paginierungs-Bug von
      `list_groups` ist 1:1 in den KMP-Port kopiert.** `FlutCloudOcs.kt:92-111`
      (`listGroups`) bricht nach dem Dedup-Filter ab, sobald
      `groups.size < limit` (:107) — eine volle 200er-Seite mit nur einem
      Duplikat beendet das Paging vorzeitig; identisch zur Desktop-Stelle
      `ocs.rs:318` (`new_groups < LIMIT`, L17-N4). Fix gemeinsam umsetzen:
      Rohseitenlänge `< limit` zählen, `seen`-Schutz behalten.
- [x] **CP-N2 (Parität, minor): Keine FlutCloud-only-Kontobereinigung beim
      Start im KMP — Fremd-Konten und tokenlose Konten bleiben stehen.**
      Der Desktop droppt Konten fremder Server beim Start und meldet
      Verworfenes/Fehlendes über `account_filter_info`
      (`AccountFilterInfo` in `ipc.ts:86-91,198-199`). KMP:
      `AccountStore.loadAccounts` (`AccountStore.kt:27-32`) lädt ungefiltert,
      `SessionManager.init`/`restoreSession`
      (`SessionManager.kt:21-57`) filtern weder noch melden; ein Konto mit
      verlorenem Token bleibt in der Liste und „Wechseln" endet stumm bei
      `session = null`. Idee: Filterung + `tokenMissing`-Reporting portieren.
- [x] **CP-N3 (Feature-Ideen, minor): Drei Desktop-Features haben kein
      mobiles Pendant:** Bildvorschauen (`preview` in `webdav.rs:719-765`,
      Command `webdav_thumbnail` vs. nur `fileIcon`-Icons in
      `FilesScreen.kt:519-527`), Ordner-ZIP-Download (`download_zip_as`
      `webdav.rs:630-649` vs. fehlend in `WebDavApi.kt`) sowie
      Mehrfachauswahl/Bulk-Aktionen (Select-all/Bulk-Download/-Delete im
      Desktop-`FileExplorer.vue` vs. nur Einzeldatei-Dropdown in
      `FilesScreen.kt:544-602`). Alles Kandidaten für Paritäts-Läufe;
      Reihenfolge nach Nutzen: Bulk-Aktionen > Thumbnails > ZIP.
- [x] **CP-N4 (Parität, minor): Quota ohne Offline-Cache mobil.** Desktop
      cached die Quota offline (`account_storage` + `cache.rs`); KMP
      `FilesViewModel.refreshQuota` (`FilesViewModel.kt:152-157`) setzt bei
      Netzwerkfehlern still auf `null` (QuotaBar leer, obwohl die letzte
      Quota bekannt war). Idee: letzte Quota in `ListCache`/Settings
      mitspeichern und als `offline=true`-Wert zeigen.
- [x] **CP-N5 (Parität/Policy, minor): Kein Client-seitiger Schutz der
      virtuellen Namespaces im KMP.** Der Desktop lehnt Pfade mit
      `resources`/`parts`-Segmenten client-seitig ab
      (`validate_dav_path`, `commands.rs:591-608`; zu aggressiv für
      Lese-Zugriffe, siehe L17-F2). Der KMP-Client hat keinerlei Äquivalent:
      `mkdir`/`rename`/`delete`/`upload*` in `WebDavApi.kt` und
      `FilesViewModel.kt` schicken Schreibzugriffe auf `/resources/…`/
      `/parts/…` ungeprüft an den Server — die Policy lebt dort allein im
      Server. Idee: denselben Guard (nur für schreibende Operationen)
      portieren; dabei gleich die L17-F2-Aufteilung lesen/schreiben
      übernehmen.

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs` `generate_handler!` deckungsgleich mit `src/lib/ipc.ts`),
Desktop-Keyring (`accounts.rs` save/load/delete), Fehler-Serialisierung
(`error.rs`) und Offline-Cache (`cache.rs`), Sync-Engine (`sync.rs`,
Desktop-only per Design), Desktop-Updater (`updater.rs`, SHA-256-Gate),
Login-/FlutCloud-only-Policy beider Clients (`flutcloud.rs`,
`LoginViewModel.kt` URL-Lock + `verifyServer`), iOS-Plattform-Layer
(`IosPlatform.kt`, `IosStorages.kt`) sowie die Workflows
(`kmp.yml`, `build.yml`, `release.yml`, `.github/actions/kmp-*`) über die
bekannten Punkte hinaus (L17-N1-Pin-Diskrepanz unverändert).

Verifikation frisch ausgeführt (HEAD `4f34a9a`): `cargo test
--manifest-path src-tauri/Cargo.toml` → **103 passed / 0 failed**;
`cargo fmt --all --check` grün; `cargo clippy --all-targets -- -D warnings`
grün (nach Nachinstallieren der Tauri-Linux-Systemdeps);
`npm run build` (vue-tsc + vite) grün (Haupt-Chunk 118 kB,
Code-Splitting wirksam); KMP `./gradlew :shared:testAndroidHostTest
:shared:compileKotlinJvm` → BUILD SUCCESSFUL (iOS-Targets auf dem
Linux-Runner nicht kompilierbar).

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `4f34a9a`)

Alle offenen Einträge wurden gegen den aktuellen Code nachgeprüft — **keiner
ist erledigt**, daher gibt es dieses Mal nichts nach `archived-todo.md` zu
verschieben:

- L17-F1 bestätigt: `bulkDelete` setzt `busyPath` vor dem Confirm
  (`FileExplorer.vue:134-138`), Early-Return außerhalb von try/finally.
- L17-F2 bestätigt: `validate_dav_path` (`commands.rs:591-608`) blockiert
  weiterhin auch Lese-Commands; `getThumbnail` fängt alle Fehler
  (`src/stores/files.ts:263-266`).
- L17-F3 bestätigt: `accounts.rs:70` und `sync.rs:1336` schreiben weiterhin
  nicht-atomar via `std::fs::write` (gegenüber temp+rename in
  `cache.rs:85-90`).
- L17-F4 bestätigt: `walk_incomplete` fehlt weiterhin in `ERROR_CODE_KEYS`
  (`i18n.ts:631`).
- L17-N1 bestätigt: `kmp.yml:129` pinnt setup-android v4.0.1, die
  `kmp-ios-build`-Action (`action.yml:30`) v3.2.2.
- L17-N2 bestätigt: `flutcloud.yml:79-81` lintet nur
  `scripts/install-flutcloud-app.sh`.
- L17-N3 bestätigt: `submit` validiert nur `serverUrl`
  (`LoginModal.vue:99-104`), `submitRegister` zusätzlich Benutzername/Token
  (`:128-131`).
- L17-N4 bestätigt: `ocs.rs:318` bricht weiter bei `new_groups < LIMIT` ab;
  neu: derselbe Bug existiert im KMP (`CP-N1`).
- „Desktop-JVM: Token-Speicher härten" bleibt offen —
  `FileKeyValueStorage.kt:13-15` dokumentiert die Keyring-Anbindung
  ausdrücklich als Follow-up.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe sind in diesem Lauf
verboten): seit dem letzten Lauf ist nur PR #327 (Lauf-17-Bericht)
gemergt; die zuvor gemergten PRs #319–#326 (u. a. iOS/Android-Parität
über #320 und Dependabot-Bumps) decken die bekannten Issues ab. Die
Issue-Templates
(`bug_report.yml`, `feature_request.yml`, `kmp.yml`) liegen vor. Ob
dazwischen neue offene Issues entstanden sind, ist hier nicht prüfbar — der
`opencode-todo-issues`-Workflow sollte beim nächsten Lauf die
CP-Befunde oben als Paritäts-Issues erfassen und die L17-Befunde erneut
einplayen (sie sind allesamt noch offen, s. Schritt 5).

## Review 2026-08-24 (Lauf 17, ganzes Projekt — neue Befunde)

Gegenstand: gesamtes Projekt — Rust-Backend (`src-tauri/`: `lib.rs`,
`commands.rs`, `accounts.rs`, `state.rs`, `error.rs`, `flutcloud.rs`,
`cache.rs`, `updater.rs`, `sync.rs`, `nextcloud/{mod,webdav,ocs}.rs`),
Frontend (`src/`: Komponenten, Pinia-Stores, `lib/ipc.ts`, `lib/i18n.ts`,
`lib/sort.ts`), Workflows/Actions (`.github/workflows/*`,
`.github/actions/*`) sowie die offenen KMP-Todos unten.

Verifikation frisch ausgeführt: `cargo test --manifest-path
src-tauri/Cargo.toml` → **103 passed / 0 failed**;
`cargo fmt --all --check` grün; `cargo clippy --all-targets -- -D warnings`
grün; `npm run build` (vue-tsc + vite) grün (Haupt-Chunk 117 kB,
Code-Splitting L12-N6 weiterhin wirksam). Tauri-Linux-Systemdeps
(`libwebkit2gtk-4.1-dev`/`libgtk-3-dev`) waren nachzuinstallieren.

Neu gefunden:

- [x] **L17-F1 (Bug, mittel): `bulkDelete` in `FileExplorer.vue` lässt
      `busyPath` nach abgebrochenem Bestätigungsdialog für immer gesetzt —
      danach blockieren alle Dateiaktionen still.**
      `FileExplorer.vue:134-138` setzt `busyPath.value = "bulk-delete"`,
      BEVOR `window.confirm` fragt; der Early-Return beim Abbrechen liegt
      **außerhalb** des try/finally, das `busyPath.value = null`
      (:145-148) sicherstellt. Nach einmal Abbrechen von „Ausgewählte
      löschen" bleiben dauerhaft wirkungslos: `bulkDownload` (:118),
      `dropUpload`/Drag-&-Drop (:152), `open` (:195), `download` (:240),
      `downloadZip` (:258) — jeweils `if (busyPath.value) return` — und
      der „Working…"-Indikator (`v-if="busyPath !== null"`, :1027)
      bleibt sichtbar. Fix: Confirm vor dem Setzen von `busyPath`
      abfragen oder den Return in den try-Block ziehen.
- [x] **L17-F2 (Bug/UX, mittel): `validate_dav_path` blockiert auch die
      nur lesenden Zugriffe auf die virtuellen Namespaces
      `resources`/`parts` — Browse/Open/Download/Thumbnail scheitern dort
      mit irreführender Meldung.** `commands.rs:591-608` lehnt jeden Pfad
      ab, der ein Segment `resources`/`parts` (case-insensitiv) enthält;
      derselbe Guard läuft aber auch in `webdav_thumbnail` (:813-839),
      `open_remote_file` (:731-777), `webdav_download_file` (:698-724)
      und `webdav_download_zip` (:782-808). Da `webdav_list` bewusst
      ohne Guard arbeitet, kann man `/resources/…` browsen; die Einträge
      tragen `isResource=true` und bieten Open/Download an — jeder Klick
      endet mit „The virtual 'resources'/'parts' folders cannot be
      modified." (Stimulans ist eine Lese-Aktion.) Thumbnails schlagen
      zusätzlich stumm fehl: `getThumbnail` fängt alle Fehler
      (`src/stores/files.ts:263-269`) → Bilder unter `/resources`
      erhalten nie Vorschauen. Fix: Guard nur für schreibende Commands
      (`webdav_delete`, `webdav_rename`, `webdav_mkdir`,
      `webdav_upload_*`, `webdav_bulk_delete`, `webdav_create_share`)
      anwenden; Lese-Commands zulassen bzw. eigenem Fehlercode spendieren.
- [x] **L17-F3 (Robustheit, minor): `accounts.json` und
      `sync-folders.json` werden nicht atomar geschrieben — ein Crash
      mid-write kann still alle Konten bzw. Sync-Ordner löschen.**
      `accounts.rs:67-72` (`persist_accounts` → `std::fs::write`) und
      `sync.rs:1333-1338` (`SyncEngine::persist` → `std::fs::write`)
      schreiben direkt; die Journal-/Cache-Pfade nutzen dagegen
      temp+rename (`persist_journal_to_disk` `sync.rs:1258-1269`,
      `atomic_write` `cache.rs:85-90`; Patterns #279/#286). Folge eines
      truncierten Files: `load_accounts` failt mit `AppError::Parse` →
      `lib.rs:213` `unwrap_or_default()` verwirft **alle** Konten
      stillschweigend; `SyncEngine::load` (`sync.rs:1317-1318`,
      `if let Ok`) verliert analog alle Sync-Ordner inkl. Journal-
      Kopplung. Fix: dieselben atomic-write-Patterns übernehmen und
      kaputte Dateien wie die Journals quarantänen statt sie beim
      nächsten `persist` kommentarlos zu überschreiben.
- [x] **L17-F4 (i18n, minor): Der Sync-`PassError`-Code
      `walk_incomplete` fehlt in `ERROR_CODE_KEYS` — die UI zeigt
      „Unbekannter Fehler." statt des Hinweises, dass Löschungen
      übersprungen wurden.** `sync.rs:1159-1164` erzeugt
      `{ code: "walk_incomplete", detail: Some("Some files could not be
      read. Deletions were skipped for safety.") }`; `i18n.ts:631-649`
      mappt den Code nicht → `translateError` fällt auf `errUnknown`
      zurück (`i18n.ts:652`), `SyncPanel.vue:23-25` rendert
      „Unbekannter Fehler."/"Unknown error.". Fix: Keys
      `errWalkIncomplete` (en+de) + Mapping ergänzen.
- [x] **L17-N1 (CI, minor): Abweichende `setup-android`-Pins zwischen
      `kmp.yml` und der `kmp-ios-build`-Action.**
      `.github/workflows/kmp.yml:129` pinnt
      `android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699 # v4.0.1`,
      `.github/actions/kmp-ios-build/action.yml:30` dagegen
      `@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407 # v3.2.2`. Der
      iOS-Kompilier-Check und der echte IPA-Build laufen damit gegen
      unterschiedliche Action-Versionen (alle übrigen Pins im Repo sind
      konsistent auf volle SHAs gesetzt). Fix: einen gemeinsamen Pin
      verwenden.
- [x] **L17-N2 (CI, minor): Kein ShellCheck/Syntax-Check für die
      Haupt-Installationsskripte.** `.github/workflows/flutcloud.yml:79-82`
      lintet ausschließlich `scripts/install-flutcloud-app.sh`; die
      README-One-Liner-Pfade `scripts/install-flutlink.sh`, Root-`install.sh`
      und `scripts/opencode-with-fallback.sh` (Release-Pipeline,
      `release.yml:108`) haben keinerlei Bash-Lint-Abdeckung. Fix: dieselben
      `bash -n` + `shellcheck -S warning`-Schritte auf alle `.sh` ausdehnen.
- [x] **L17-N3 (UX-Konsistenz, minor): Der Login-Tab validiert leere
      Formularfelder nicht client-seitig.** `LoginModal.vue:99-120`
      (`submit`) prüft nur `serverUrl` und schickt leere
      Benutzername/Token-Felder ans Backend (OCS-Fehltext statt dem
      lokalisierten `requiredFields`-Hinweis, den `submitRegister`
      (`LoginModal.vue:128-131`) zeigt. Fix: dieselbe Prüfung wie im
      Register-Tab ergänzen.
- [x] **L17-N4 (Konsistenz, minor): Paginierungs-Guard von
      `list_groups` bricht bei vollen Seiten mit Duplikaten zu früh ab.**
      `ocs.rs:316-321` stoppt, sobald `new_groups < LIMIT` (neue Einträge
      pro Seite) — enthält eine volle 200er-Seite auch nur einen
      Duplikat-Eintrag (Verschiebungen während der Paginierung), werden
      weitere Gruppen nicht mehr geholt; `list_users` nutzt dafür
      konsistent die Rohseitenlänge `count < PAGE`
      (`ocs.rs:116-118`). Fix: denselben `count < PAGE`-Check verwenden
      (der Dedup-/Loop-Schutz via `seen` bleibt erhalten).

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs:239-281`) deckungsgleich mit `src/lib/ipc.ts`; Keyring-Handling
(`accounts.rs` save/load/delete + `token_missing`-Reporting F8);
Fehler-Serialisierung (`error.rs` code/message/detail) und Offline-Cache-
Fallbacks (`commands.rs` `webdav_list`/`account_storage` + `cache.rs`
LRU-Eviction); Sync-Engine-Sicherheitsmechanismen (fail-closed bei
unvollständigem Walk, TOCTOU-Guards, If-Match-Lost-Update-Schutz,
dirty-dir-Schutz, Journal-Quarantine); Updater (SHA-256-Gate,
asset-name-Härtung R7-2); Chunked-Upload-v2-Cleanup; Impersonation-Guards
(`webdav.rs` Namespace-Check, `ocs.rs` `verify_share_owner`).

### todo.md-Nachprüfung (Schritt 5, gegen den aktuellen Code)

- [x] „`SettingsStore` nach `commonMain` heben" → **erledigt**:
      `kmp/shared/src/commonMain/kotlin/com/flutcloud/flutlink/core/SettingsStore.kt`
      liegt vollständig in `commonMain` (Flow-basiert, persistiert über
      den plattformgelieferten `KeyValueStorage`; Android-Actual in
      `androidMain/…/core/AndroidStorages.kt`). Verschoben nach
      `archived-todo.md`.
- [x] „iOS-Parität (Langläufer)" → **erledigt**: die Compose-UI
      (Login, Files, Admin, Settings) liegt komplett in `commonMain`
      (45 Kotlin-Dateien in commonMain vs. 7 in androidMain);
      plattformgebundene Dienste sind als Actuals umgesetzt
      (`iosMain/…/core/IosStorages.kt`: `IosKeychainStorage` via
      SecItem-API + `IosDefaultsStorage` via NSUserDefaults;
      `PlatformActuals.kt`, `PlatformUi.ios.kt`) und in
      `kmp/README.md` („Stand der iOS-Parität") dokumentiert.
      Verschoben nach `archived-todo.md`.
- [ ] „Desktop-JVM: Token-Speicher härten" → **weiter offen, bestätigt**:
      `kmp/shared/src/jvmMain/kotlin/com/flutcloud/flutlink/desktop/FileKeyValueStorage.kt:12-15`
      legt Tokens weiterhin als Properties-Datei mit 600-Rechten unter
      `$XDG_STATE_HOME/flutlink` ab; Kommentar nennt die Keyring-Anbindung
      ausdrücklich als Follow-up.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe sind in diesem Lauf
verboten): `git log` belegt die gemergten Dependabot-PRs #324 (okio 3.18.1),
#325 (okhttp) und #326 (opencode/github 1.18.21) sowie die Feature-Commits
seit dem letzten Lauf (FLUTCLOUD_URL-Baking `5357baf`, FlutCloud-App-Zip
`79afc45`/`a5eed2f`, iOS-AltStore-Quellen `8f5213b`, KMP-Update-Check
`47ca9d2`); der todo.md-Kopf bestätigt, dass #293/#317/#318 geschlossen
sind. Ob darüber hinaus offene Issues existieren oder veraltet sind, ist
hier nicht prüfbar — der `opencode-todo-issues`-Workflow sollte beim
nächsten Lauf einen Re-Sync machen und dabei die L17-Befunde oben als
Issues erfassen.


## Erledigt (2026-08-25, Issue „Update fails on android“)

- [x] **Android-Self-Update bricht mit
      `…/cache/updates/flutlink-update.apk: open failed: ENOENT` ab.**
      → erledigt (2026-08-25): Ursache war v1.0.0, das `/cache/updates/`
      nirgends anlegte — `downloadUpdate` schrieb direkt in den fehlenden
      Ordner (mkdirs kam erst in v1.1.0; betroffene Installationen konnten
      sich nicht selbst heilen). Härtung im KMP-Client:
      - `AndroidPlatform.downloadUpdate`: Zielordner wird unmittelbar vor
        dem Schreiben geprüft/angelegt (klare Fehlermeldung statt bare
        ENOENT, auch falls „updates“ als Datei existiert); bei einem
        Abbruch mitten im Body wird die Teil-APK gelöscht.
      - `AndroidPlatform.installUpdate`: wirft eine verständliche
        `IOException`, wenn die APK fehlt, statt dem Installer eine
        hängende FileProvider-URI zu übergeben.
      - `SettingsViewModel.installUpdate`: fängt unerwartete Exceptions
        (z. B. verschwundene APK vor dem Hashing) und zeigt einen Toast,
        statt die App abzustürzen (Parität zum `AutoUpdatePrompt`).
      - Verifikation: `./gradlew :shared:testAndroidHostTest
        :shared:compileKotlinJvm` grün; `:android-app:assembleDebug` grün;
        `cargo fmt --all --check`, `cargo clippy --all-targets --
        -D warnings` grün.

## Erledigt (2026-08-25, CP-F4 — Android-Self-Update mit SHA-256-Gate)

- [x] **CP-F4 (Sicherheit/Parität, mittel): Android-Self-Update lädt das APK
      ohne SHA-256-Prüfung herunter.**
      → erledigt (seit Lauf 18 im Code): `UpdateChecker.checkForUpdate`
      liest das GitHub-`digest`-Feld (`sha256:<hex>`) des `.apk`-Assets und
      reicht es als `AppUpdate.sha256` durch; `AppUpdater.downloadAndInstall`
      berechnet die Prüfsumme des Downloads (`Sha256.Digester`, streaming)
      und bricht mit `update_checksum_mismatch` ab, bevor `installUpdate`
      aufgerufen wird — analog zum Desktop-Gate in `updater.rs`.


## Erledigt (2026-08-25, Feedback „Android: Geräte-Theme + eigene Wahl“)

- [x] **Android folgt standardmäßig dem Geräte-Theme (korrekte
      Akzentfarbe/Modus), behält aber die Wahl Light/Dark/System.**
      → erledigt (2026-08-25): übernimmt das Feedback zum vorherigen Eintrag
      („Issue UI-Styles …“) und hebt dessen Android-Sonderfall auf —
      `keepsDeviceTheme` ist entfallen.
      - KMP (`kmp/`): `Platform.keepsDeviceTheme` (Interface,
        `AndroidPlatform`-Override, `AppContainer`-Weiterleitung) entfernt;
        `FlutLinkRoot` reicht die gespeicherte Präferenz unverändert durch
        (`"system"` ist Default → frische Installationen folgen dem Gerät);
        `SettingsScreen` zeigt den Theme-Picker wieder plattformübergreifend
        an (Deep Midnight / Bright Daylight / System). Korrekte
        Akzentfarben je Modus bleiben erhalten (`defaultAccentHue`,
        Material-You-Dynamic-Color nur bei „system“).
      - Verifikation: `cargo fmt --all --check` grün; `cargo clippy
        --all-targets -- -D warnings` grün; `./gradlew :shared:build`
        BUILD SUCCESSFUL (Android + JVM + iOS-Metadaten + Unit-Tests).


## Erledigt (2026-08-24, Issue „UI-Styles: Deep Midnight / Bright Daylight / System“)

- [x] **Theme-Auswahl auf Deep Midnight (Dark), Bright Daylight (Light) und
      System reduziert; Android folgt immer dem Gerät.**
      → erledigt (2026-08-24): OperationFlut ist als wählbares Design
      entfallen.
      - Desktop (`src/`): `Theme`-Typ ist jetzt `midnight | light | system`
        mit Migration alter Werte (`operationflut`/`dark` → `midnight`);
        `[data-theme="operationflut"]` aus `style.css` entfernt;
        `SettingsModal.vue` bietet Deep Midnight / Bright Daylight / System;
        i18n-Schlüssel `themeLight` (+ de) ersetzen `themeOperationflut`,
        `systemThemeNote` neutral formuliert.
      - KMP (`kmp/`): `FlutResolvedTheme` ohne OperationFlut
        (Legacy-Mappings bleiben), `operationflutScheme`/Surfaces entfernt;
        neues `Platform.keepsDeviceTheme` (true nur auf Android) erzwingt
        `"system"` in `FlutLinkRoot` und blendet den Theme-Picker in
        `SettingsScreen` aus (Akzentfarbe/Dynamic Color bleiben);
        Strings zweisprachig aktualisiert (`theme_daylight`, Hinweise).
      - Verifikation: `cargo fmt --all --check` grün; `cargo clippy
        --all-targets -- -D warnings` grün; `cargo test` → **107 passed /
        0 failed**; `npm run build` grün; `./gradlew :shared:build`
        BUILD SUCCESSFUL.


## Erledigt (2026-08-24, Lauf 17 abgehakt)

- [x] **`SettingsStore` nach `commonMain` heben** (DataStore Preferences
      ist multiplatform; der `Context`-Delegate bleibt androidMain-actual)
      — Voraussetzung für Einstellungen im späteren iOS-/Desktop-UI.
      → erledigt (verifiziert 2026-08-24, Review Lauf 17):
      `kmp/shared/src/commonMain/kotlin/com/flutcloud/flutlink/core/SettingsStore.kt`
      liegt vollständig in `commonMain` (Flow-basiert, persistiert über
      den plattformgelieferten `KeyValueStorage`); Android-Actual in
      `androidMain/…/core/AndroidStorages.kt`, iOS-Actual
      (`IosDefaultsStorage`) in `iosMain/…/core/IosStorages.kt`.
- [x] **iOS-Parität (Langläufer): Compose-UI nach `commonMain` heben bzw.
      iOS-Placeholder ersetzen.**
      → erledigt (verifiziert 2026-08-24, Review Lauf 17): die gesamte
      UI (Login, Dateien, Admin, Einstellungen) liegt in `commonMain`
      (45 Kotlin-Dateien vs. 7 in androidMain), Strings zweisprachig in
      `commonMain/composeResources/values{,-de}/strings.xml`;
      plattformgebundene Dienste als Actuals: Keychain-Token-Speicher
      (`IosKeychainStorage`, SecItem-API) + NSUserDefaults
      (`IosStorages.kt`), Document-Picker/QuickLook/Share
      (`PlatformActuals.kt`, `PlatformUi.ios.kt`); dokumentiert in
      `kmp/README.md` („Stand der iOS-Parität“). Zwei-Wege-Sync bleibt
      bewusst Desktop-only.

Weiter offen aus dieser Liste: „Desktop-JVM: Token-Speicher härten“
(`FileKeyValueStorage` legt Tokens weiterhin als 600er-Datei unter
`$XDG_STATE_HOME/flutlink` ab; Keyring-Anbindung als Follow-up).


## Review-Abschnitte aus todo.md (2026-08-24 archiviert)

### Fix-Lauf 2026-08-23 (II) — KMP: AGP 9/Gradle 9-Migration, compileSdk 37, commonMain-Ausbau (Issues #293/#317/#318)

Umgesetzt in den lokalen Commits `4cefb51` (Modul-Split + Toolchain) und
`ef705c9` (Build-Fixes: eigener Namespace/Paket für `:android-app`,
res/values-Platzierung, okio in commonMain, `Char.code`); Details im Archiv
(Abschnitt „Fix-Lauf 2026-08-23 (II) … abgeschlossen").

- [x] **#317 (Gradle 9 + AGP 9):** gradle-wrapper 8.13 → **9.7.1**; AGP
      8.13.2 → **9.3.1** mit der von AGP 9 vorgeschriebenen Migration: der
      Android-Einstiegspunkt (Manifest, Launcher-Ressourcen,
      `FlutLinkApplication`/`MainActivity`, Signing/R8/BuildConfig mit
      `FLUTCLOUD_URL`) zog in das neue Modul `kmp/android-app` (built-in
      Kotlin; Compose-Compiler liefert AGP), `:shared` ist jetzt eine
      Bibliothek unter `com.android.kotlin.multiplatform.library`
      (Single-Variant, Host-Tests per `withHostTestBuilder`). Dependabot-PR
      #305 damit obsolet (von Dependabot geschlossen).
- [x] **#318 (compileSdk 37):** `compileSdk = 37` in beiden Modulen;
      multiplatformLifecycle 2.10.0 → **2.11.0**; CI (`kmp.yml` +
      `kmp-ios-build`-Action) installiert **`platforms;android-37.0`**
      (API-37-Plattformen tragen die neue Extension-Level-Namensgebung,
      `platforms;android-37` existiert nicht). Dependabot-PR #313 damit
      überholt.
- [x] **#293 (commonMain ausbauen + Desktop-JVM-Client):** `FlutCloudApi`
      (inkl. OCS-/Share-/Links-Endpunkten) und `WebDavApi` wurden auf
      Ktor 3 + okio portiert und liegen komplett in `commonMain`
      (handgeschriebener Mini-XML-Pull-Parser ersetzt xpp3,
      Percent-Encoding ohne URLEncoder, Basic-Auth ohne okhttp-Credentials;
      expect/actual für HTTP-Engine, Logging, FileSystem). `AccountMeta`/
      `AccountStore` (neuer `KeyValueStorage`-Vertrag) und `SessionManager`
      sind common; Android behält EncryptedSharedPreferences/DataStore als
      Actuals. `jvmMain` enthält den funktionalen headless Desktop-Client
      (Task `:shared:desktopCli`: `whoami`/`ls` gegen
      FLUTCLOUD_URL/FLUTCLOUD_USERNAME/FLUTCLOUD_TOKEN aus der Umgebung,
      Datei-basierte Stores unter `$XDG_CONFIG_HOME`/`$XDG_STATE_HOME`).
      Zusätzlich exportiert `iosMain` jetzt `MainKt.MainViewController()`
      (Datei `Main.kt`) — das behebt den seit dem KMP-Fold-in rot
      iOS-IPA-Job in `build.yml` (`cannot find 'MainKt' in scope`).
      Tests: `androidUnitTest` → `androidHostTest`, 30 Tests grün;
      `kmp/README.md` + `AGENTS.md` beschreiben den neuen Stand.
- [x] Verwaltung: iOS-Issues #232–#242/#244 sind geschlossen (not planned).

Neu offen aus diesem Lauf:

- [x] Push bestätigen: `kmp.yml` (build/test/jvm/ios/lint) sowie
      `build.yml`/`release.yml` (APK/IPA-Artefakte) nach dem AGP-9-Umbau
      grün bekommen. → erledigt 2026-08-23: alle Workflows auf HEAD
      (`22f36ed`) grün — kmp.yml ✓, build.yml inkl. **iOS-IPA-Job
      (erstmals seit dem Fold-in)** und Android-APK ✓, Lint ✓; lokal
      vorab verifiziert waren 30/30 KMP-Tests, compileKotlinJvm,
      compileKotlinIosArm64 und processDebugResources.
- [ ] Issues #293/#317/#318 nach dem Push mit Commit-Referenz schließen.
- [ ] Desktop-JVM: Token-Speicher härten — OS-Keyring-Anbindung statt
      600er-Datei unter `$XDG_STATE_HOME/flutlink` (siehe
      `FileKeyValueStorage`), Parität zum Tauri-Client (`keyring`).
- [ ] `SettingsStore` nach `commonMain` heben (DataStore Preferences ist
      multiplatform; der `Context`-Delegate bleibt androidMain-actual) —
      Voraussetzung für Einstellungen im späteren iOS-/Desktop-UI.
- [ ] iOS-Parität (Langläufer): die Compose-UI aus `androidMain`
      (R.string-i18n, EncryptedSharedPreferences, SAF-Aktionen) nach
      `commonMain` heben bzw. den iOS-Placeholder ersetzen; dokumentiert
      in `kmp/README.md` („Stand der iOS-Parität").


### Fix-Lauf 2026-08-23 — L15/L16-Katalog geschlossen (Commit `59de00d`) — v1.0.0 release-ready

Der komplette Befundkatalog aus Lauf 15 und Lauf 16 ist mit Commit `59de00d`
(„fix: address review findings L15/L16 …") umgesetzt; die Einzelnachweise
stehen im Archiv (Abschnitt „Fix-Lauf 2026-08-23 …"). Verifikation frisch
auf Windows ausgeführt: `cargo fmt --check` grün;
`cargo clippy --all-targets -- -D warnings` grün; `cargo test` →
**99 passed / 0 failed** (nach `cargo clean` mit `CARGO_INCREMENTAL=0` +
`CARGO_PROFILE_TEST_DEBUG=0` wegen Plattenplatz); `npm run build` grün,
Haupt-Chunk 117 kB, keine Chunk-Size-Warnung. Damit sind auch alle im
Lauf-16-Urteil vor dem Tag empfohlenen Punkte (L15-S*, F4/F5, N16-1,
L16-F1) erledigt.

**Urteil (v1-Readiness, aktualisiert 2026-08-23):** Keine offenen Befunde
mehr — FlutLink v1.0.0 ist release-bereit. Die Versionen stehen konsistent
auf 1.0.0 (`package.json`, `src-tauri/tauri.conf.json`, `src-tauri/Cargo.toml`
inkl. sauberer Paket-Metadaten, `Cargo.lock`, KMP `versionName`); die
Release-Pipeline baut Desktop/APK/IPA + AltStore-Quellen mit Security-Gate
und Draft→Publish-Finale. Verbleibend ist nur der verwaltungstechnische
Punkt unten.


### Fix-Lauf 2026-08-22 — GitHub-Issues #225–#230, #243, #255-Rest, #267

Alle offenen GitHub-Issues bearbeitet; die Code-Fixes sind gegen HEAD
verifiziert (`cargo fmt --check` / `cargo clippy --all-targets -- -D warnings`
/ `cargo test` grün; `npm run build` grün — jetzt **ohne** Chunk-Size-Warnung,
s. L12-N6; `:shared:compileKotlinJvm` grün). Die neun abgehakten Punkte
(L12-N1–N6, #243, #255-Rest, K3/#267) sind in `archived-todo.md`
verschoben (Abschnitt „Fix-Lauf 2026-08-22 … abgeschlossen").

- [x] iOS-Issues #232–#242/#244: mit #255 obsolet (Swift-Port entfernt) —
      werden als „not planned" geschlossen; Parität lebt in `android/`+`kmp/`.
      → erledigt 2026-08-23: alle zwölf Issues sind geschlossen (geprüft
      via `gh issue list`).

### Issue #255 — iOS-Port (Swift) entfernt, ersetzt durch KMP (2026-08-20)

Der iOS-Port (`ios/`, Swift + SwiftUI) wurde **entfernt**, weil `kmp/` ihn
ersetzt (siehe `kmp/README.md`). Gelöscht: `ios/` (inkl. `ios/README.md`,
`project.yml`), `scripts/build-ipa.sh`, `ClassicSource.json`/`PALSource.json`
(AltStore-Quellen für den iOS-Test-Port) und `.github/ISSUE_TEMPLATE/ios.yml`.
`AGENTS.md` und `kmp/README.md` sind angepasst. Damit sind die noch offenen
iOS-Befunde aus Lauf 13 **obsolet** und abgehakt: I1-5 (FLUTCLOUD_URL im
Info.plist), I1-6 (Dateiaktionen), I1-10 (RAM-Upload/-Download). Details im
Archiv. Nachtrag: Die iOS-CI-Reste sind inzwischen entfernt —
`.github/workflows/ios.yml` gelöscht, `ios`-/`upload-ios`-Jobs aus
`release.yml` entfernt (referenzierten gelöschte Pfade und failten sonst bei
jedem Release).

### Review 2026-08-20 (Lauf 14, Fokus KMP — neue Befunde)

Verifikation frisch ausgeführt: `cargo test --manifest-path
src-tauri/Cargo.toml` → **83 passed / 0 failed**; `npm run build`
(vue-tsc + vite) grün (nur die bekannte Chunk-Size-Warnung, L12-N6).
**Der KMP-Build ist dagegen kaputt:** `cd kmp && ./gradlew
:shared:testDebugUnitTest` (und `:shared:assembleDebug`) failen bei HEAD
(`52447bd`) mit `Unresolved reference`-Fehlern in `SettingsStore.kt`. Der in
todo.md/archivierte Claim „Build + 30 Tests grün" (Issue #246) gilt damit
**nicht** für den aktuellen Stand (im Archiv gegen `d75b4c0` verifiziert; der
Katalog-Umbau `d183b27` entfernte die nötige Dependency, Merge `bacc4f0`/
PR #247 brachte das auf main). Gegenstand dieses Laufs: das komplette
KMP-Subprojekt (`kmp/`; Kotlin 2.3.21, AGP 8.13.2, `androidTarget()` +
`jvm()` + iOS-Targets). Neu gefunden — die Build-Blocker K1/K2 und der
README-Widerspruch K4 wurden in diesem Lauf (Issue #248, „gh actions/
Workflows auf die kmp-Version") behoben: `:shared:compileDebugKotlinAndroid`,
`:shared:compileKotlinJvm` und `:shared:testDebugUnitTest` sind grün
(Details im Archiv):

- [x] **K1 (Build, hoch):** KMP-Android-Kompilierung ist kaputt —
      `SettingsStore.kt:13,19,21-23,40-54` (`core/`) nutzt
      `androidx.datastore.preferences.*` (`preferencesDataStore`,
      `stringPreferencesKey`/`booleanPreferencesKey`/`intPreferencesKey`,
      `edit`), aber die Dependency `androidx-datastore-preferences` fehlt im
      Versionskatalog (`kmp/gradle/libs.versions.toml`) **und** in
      `androidMain.dependencies` (`kmp/shared/build.gradle.kts`). `d183b27`
      entfernte sie (mit `xpp3`) aus dem Katalog, der Code nutzt sie weiter →
      `:shared:compileDebugKotlinAndroid` failt (30 Fehler, alle in
      `SettingsStore.kt`). `android/gradle/libs.versions.toml` hat sie als
      `datastore = "1.2.1"`. Fix: `androidx-datastore-preferences` (1.2.1) in
      Katalog + `androidMain.dependencies` aufnehmen.
      → erledigt: `androidx-datastore-preferences` (1.2.1) im Katalog und in
      `androidMain.dependencies` aufgenommen; `:shared:compileDebugKotlinAndroid`
      grün (Details im Archiv).
- [x] **K2 (Tests, hoch):** Die JVM-Unit-Tests wären auch nach K1 rot:
      `WebDavApi.parseMultistatus` (`WebDavApi.kt:499-501`) nutzt
      `XmlPullParserFactory.newInstance()`; die `androidUnitTest`-Dependencies
      enthalten nur `junit` + `ktor-client-okhttp` — **kein `xpp3`**.
      `android/` deklariert dafür explizit `testImplementation(libs.xpp3)`
      (dieselben Tests). Ohne XmlPull-Implementierung wirft die Mockable-
      Android-JAR bei den 7 `WebDavApiTest`-Fällen „not mocked". Fix:
      `xpp3:xpp3:1.1.4c` als `androidUnitTest`-Dependency ergänzen.
      → erledigt: `xpp3` (1.1.4c) im Katalog und in
      `androidUnitTest.dependencies` ergänzt; `:shared:testDebugUnitTest`
      grün (Details im Archiv).
- [x] **K3 (CI, mittel):** `kmp/` hat **keinerlei** CI-Abdeckung —
      `android.yml` triggert nur auf `android/**`, `build.yml`/`lint.yml` nur
      auf Frontend/Rust. Der kaputte KMP-Build (K1) wird von keinem Workflow
      erkannt und landete so ungetestet auf main (`bacc4f0`). Fix:
      `kmp/**` in die `android.yml`-Paths aufnehmen oder einen KMP-Job
      (`:shared:assembleDebug` + `:shared:testDebugUnitTest` +
      `:shared:compileKotlinJvm`) ergänzen.
      → erledigt 2026-08-22 (#267): eigener Workflow
      `.github/workflows/kmp.yml` (build/tests/jvm/lint) + Issue-Template
      `.github/ISSUE_TEMPLATE/kmp.yml` (s. Fix-Lauf oben).
- [x] **K4 (Doku, minor):** `kmp/README.md:41-44` behauptete „iOS-Targets sind
      bewusst nicht eingerichtet", obwohl `kmp/shared/build.gradle.kts:20-32`
      `iosX64()/iosArm64()/iosSimulatorArm64()` deklariert (Framework-Binary,
      `iosMain.dependencies` mit `ktor-client-darwin`). README widersprach
      dem Build-Skript; die Targets sind aktuell funktionslos (kein
      `iosMain`-Quellcode). Zuerst via PR #256 („Fixed K4/K5") durch eine
      README-Anpassung behoben; mit Issue #255 (PR #263) erneut angepasst,
      weil `ios/` entfernt wurde: die README beschreibt die iOS-Targets
      jetzt als Ersatz für den entfernten Swift-iOS-Port.
- [x] **K5 (Architektur, minor):** `commonMain` enthält nur 4 Dateien
      (`AuthSession.kt`, `ApiException.kt`, `JsonUtil.kt`, `dto/Models.kt`);
      der gesamte übrige Code liegt in `androidMain` (Android-APIs: OkHttp,
      Context, SharedPreferences, Compose). `jvmMain`/`iosMain` sind leer —
      der „Multiplatform"-Mehrwert ist derzeit nur der JVM-Kompilier-Check
      (`:shared:compileKotlinJvm`, grün). Die README-Aussage „stellt den
      gemeinsamen Kotlin-Code in einem KMP-Modul bereit" überschätzte den
      Stand. Fix: README korrigiert (tatsächlicher `commonMain`-Stand +
      Desktop-JVM-Client als Folgearbeit) — siehe Archiv.
- [x] **K6 (Bug, mittel, aus android/ übernommen):** Admin-Suche filtert
      nicht: `AdminScreen.kt:106-112` bindet die Search-TextField an
      `vm.search.value` (`onValueChange = { vm.search.value = it }`), aber es
      gibt keinen Trigger (kein `LaunchedEffect(search)`, kein Debounce), der
      `vm.loadUsers()` bei Eingabe aufruft; `loadUsers`
      (`AdminViewModel.kt:33-52`) läuft nur beim Mount
      (`LaunchedEffect(Unit)`, `AdminScreen.kt:86`). Desktop `AdminPanel.vue`
      sucht bei jeder Eingabe. Fix: `LaunchedEffect(vm.search.value)` mit
      Debounce → `loadUsers()`.
      → erledigt: `AdminScreen.kt` nutzt `LaunchedEffect(search)` mit
      300-ms-Debounce (`delay(300)`) vor `loadUsers()`; die Suche filtert
      live bei jeder Eingabe (android/ + kmp/).
- [x] **K7 (Bug, mittel, aus android/ übernommen):** Impersonation-Lücke beim
      „Öffnen": `FilesViewModel.downloadAndOpen` (`FilesViewModel.kt:162-189`)
      reicht `targetUser` **nicht** an `downloadToFile` weiter — anders als
      `downloadAndShare` (`:239-246`) und `downloadToDownloads` (`:202-211`).
      Beim Admin-Impersonation-Browsing lädt „Open" die Datei aus dem eigenen
      Namespace des Admins statt aus dem des Zielnutzers (falsche Datei/404).
      Fix: `targetUser = targetUser.value` ergänzen.
      → erledigt (siehe Archiv „Review 2026-08-20 (Lauf 14, Issue-Fix K7)").
- [x] **K8 (Perf, minor, aus android/ übernommen):** `ListCache.kt` hat keinen
      Maximalbestand/keine Eviction — jede (Account, Pfad)-Kombination wird
      dauerhaft als JSON im App-`filesDir` gehalten. Desktop `cache.rs`
      evicted LRU (`MAX_CACHE_ENTRIES=500`); iOS-Befund I1-11 nennt dasselbe.
      Fix: Bestand begrenzen (mtime/LRU).
      → erledigt 2026-08-20 (siehe Archiv): `ListCache.kt` in `android/` und
      `kmp/` evicted per mtime/LRU (`MAX_CACHE_ENTRIES = 500`, Eviction
      nach jedem `write`, `touch` auf `read`).
- [x] **K9 (Perf, mittel, aus android/ übernommen):** `AdminViewModel.loadPage`
      (`AdminViewModel.kt:72-81`) holt pro Seite 200 Benutzer-IDs und danach
      200 Einzel-`getUser`-OCS-Requests (N+1); `AdminScreen.kt:86` lädt beim
      Mount ohne Suchbegriff den ersten Block. Desktop verlangt einen
      Suchbegriff (D3/U-R8-12, L12-N1). Fix: Suchpflicht analog Desktop oder
      Detail-Batch.
      → erledigt: Suchpflicht analog Desktop (D3/U-R8-12): `loadUsers()`
      bricht bei leerem Suchbegriff ab (leert Liste, keine OCS-Requests);
      `AdminScreen.kt` zeigt bei leerer Suche den Hinweis
      `search_users_required`. Kein ungefilterter N+1-Block beim Mount mehr
      (android/ + kmp/).

**KMP-Fix-Lauf (Issue „IOS Build doesnt build" → Kommentar „/oc fix it
(kmp)"):** K1, K2, K4 und K6–K9 wurden in diesem Lauf umgesetzt (Details im
Archiv-Abschnitt „KMP-Fix 2026-08-20"). K3 (CI) bleibt offen: Workflow-Dateien
sind für automatisierte Läufe tabu (Security-Regel) — der `opencode-todo-issues`-
Workflow soll die KMP-Folgearbeit beim nächsten Lauf als Issue erfassen. K5
(Architektur) bleibt offen: `commonMain` enthält weiterhin nur 4 Dateien; eine
Aufteilung von `androidMain` in gemeinsamen Code ist als Folgearbeit zu planen.

**todo.md-Nachprüfung (Schritt 5):** Die iOS-Befunde des Laufs 13 wurden in
den Fix-Commits `33f3cd9` („resolve GH issues #232-244 …"), `78fbc24`,
`0c78139`, `006fee3`, `c1ecd9f`, `319592a`, `fea2cd2`, `3ae4868` und
`23eda61`–`88ee296` adressiert. Per Code-Inspektion (Xcode-Build auf dem
Linux-Runner nicht möglich) sind **erledigt** und in `archived-todo.md`
verschoben: I1-1 (`viewModel.search("")`, `FilesView.swift:57`), I1-2
(`setTargetUser`, `FilesViewModel.swift:49`), I1-3 (`@StateObject`,
`HomeView.swift:13-24`), I1-4 (`accounts` aus `sessionManager`,
`SettingsViewModel.swift:27,32`), I1-7 (`Localizable.strings` en+de), I1-8
(kein Auto-Load beim Appear, `AdminView.swift:76`), I1-9 (`contextMenu` in
`searchResultsList`, `FilesView.swift:140`), I1-11 (`evictIfNeeded`,
`ListCache.swift:38`), I1-12 (Namespace-Guard, `WebDavApi.swift:45-46`).
**Weiter offen (Restlücken):** I1-5, I1-6 und I1-10 waren die verbliebenen
iOS-Befunde — sie sind mit Issue #255 **obsolet** (der Swift-iOS-Port `ios/`
wurde entfernt, s. oben). Ebenfalls erledigt: der
Lauf-13-Hinweis `anomalyco/opencode/github@latest` — alle drei opencode-
Workflows pinnen jetzt auf den vollen SHA `31406ccc… # v1.18.18`
(`opencode.yml:92`, `opencode-todo-issues.yml:38`, `opencode-review.yml:38`).
Desktop-Frontend-Punkte L12-N1 … L12-N6 sind unverändert offen (L12-N1:
`AdminPanel.vue:188` ruft weiter `adminListUsers(query)` ohne Limit/Offset;
L12-N2: `SettingsModal.vue:144` löscht ohne Confirm; L12-N3:
`FileExplorer.vue:648` `adminListUsers("")`; L12-N4: `files.ts` setzt
`error` und rethrowt; L12-N5: doppelter Komparator
`FileExplorer.vue:54`/`EntryList.vue:52`; L12-N6: keine Code-Splitting-
Maßnahme).

Keine neuen Befunde im Desktop-Backend (`commands.rs`, `webdav.rs`, `ocs.rs`,
`sync.rs`, `accounts.rs`, `error.rs`, `cache.rs`, `updater.rs`) über die
bekannten Punkte hinaus; der Android-Port ist von K6–K9 ebenfalls betroffen
(gleicher Code).

**GitHub-Issues (Schritt 6, nur lokale Quellen — keine gh/API-Aufrufe):**
Der Merge-Branch `opencode/issue246-20260820094635` → PR #247 (`bacc4f0`)
belegt die Umsetzung von Issue #246 (KMP-Subprojekt) — diese Umsetzung ist
allerdings mit K1/K2/K3 nicht bau-/testbar. `33f3cd9` belegt die iOS-Issues
#232–#244 (s. oben; Restlücken I1-5/I1-6/I1-10 sind mit Issue #255 obsolet,
iOS-Port entfernt). Der
`opencode-todo-issues`-Workflow sollte die KMP-Folgearbeit (K1–K9) beim
nächsten Lauf als Issues erfassen.

### KMP-Subprojekt (Issue #246) — erledigt 2026-08-20

Kotlin-Multiplatform-Subprojekt `kmp/` erstellt und den gesamten Kotlin-Code
aus `android/` übernommen (`commonMain`/`androidMain`/`androidUnitTest`).
Build + 30 Tests grün, `cargo fmt`/`cargo clippy` verifiziert. Details in
`archived-todo.md` („Review 2026-08-20 (Issue #246 …)"). Offene Folgearbeit
(nicht Teil dieses Tickets): Desktop-JVM-Client in `kmp/shared` aufbauen.

### Review 2026-08-18 (Lauf 13, Fokus iOS — neue Befunde)

Verifikation in diesem Lauf frisch durchgeführt: `cargo test --manifest-path
src-tauri/Cargo.toml` → **83 passed / 0 failed** (Tauri-Linux-Systemdeps
`libwebkit2gtk-4.1-dev`/`libgtk-3-dev`/… nachinstalliert); `cargo fmt --check`
grün; `cargo clippy --all-targets -- -D warnings` grün; `npm run build`
(vue-tsc + vite) grün (nur die bekannte Chunk-Size-Warnung, L12-N6). Der
iOS-Build (`xcodebuild`) ist auf dem Linux-Runner **nicht ausführbar** — die
Befunde I1-1/I1-2 beruhen auf Swift-Semantik (s. u.) und müssten im `ios.yml`-
CI-Build bestätigt werden. Gegenstand dieses Laufs: der komplette iOS-Port
(`ios/`, Test-Port, 26 Swift-Dateien) gegen Desktop-Parität, Swift-Korrektheit,
Security (Keychain/FLUTCLOUD_URL) und CI. Neu gefunden:

- [x] **I1-1 (Build, hoch):** Die iOS-App kompiliert bei HEAD **nicht** —
      `FilesView.swift:57` (`Button("search".localized) { viewModel.search = " " }`)
      weist einer **Methode** einen String zu: `FilesViewModel.search(_:)`
      (`FilesViewModel.swift:293`) ist eine `func`, keine Property → Compile-
      Fehler „cannot assign to property: 'search' is a method". Der Menüpunkt
      „Search" müsste `viewModel.search("")` aufrufen bzw. die SearchBar fokussieren.
- [x] **I1-2 (Build, hoch):** `AdminView.swift:42` ruft `viewModel.setTargetUser(user.id)`
      auf — `AdminViewModel` hat **keine** solche Methode (nur `FilesViewModel.swift:49`,
      kein `extension AdminViewModel` im Projekt) → Compile-Fehler „value of type
      'AdminViewModel' has no member 'setTargetUser'". Damit failt der `ios.yml`-Build
      (last commit `0c78139` berührt nur `Models.swift`; die Fehler stammen aus dem
      Ursprungs-Commit `7f1a95f` und wurden von den „fix(ios): … compilation errors"-
      Commits `ba4c484`/`c854dad` nicht abgedeckt). Fix: Impersonation-Flow über
      geteilten State verdrahten (siehe I1-3).
- [x] **I1-3 (Architektur, mittel):** `HomeView.swift:15-17` erzeugt **bei jeder**
      `body`-Auswertung neue ViewModel-Instanzen (`private var filesVM: FilesViewModel
      { FilesViewModel(sessionManager:) }`). Jeder Re-Render (Tab-Wechsel, Accent-Slider-
      Zug in den Settings, Kontowechsel) liefert frische VMs → Dateiliste, Ordnerpfad,
      Suche, Shares und Quota setzen zurück; `FilesView` verliert laufend den Zustand.
      Fix: `@StateObject` einmalig im Parent erzeugen (z. B. in `init`) bzw. die VMs
      persistent in `SessionManager`/`AppContainer` halten.
- [x] **I1-4 (Bug, mittel):** `SettingsViewModel.accounts` (`SettingsViewModel.swift:11`)
      wird **nie** befüllt (kein Assignment, keine Spiegelung von
      `sessionManager.accounts`). `SettingsView.swift:16`/`:65` sieht daher immer leer
      aus → die Account-Sektion zeigt dauerhaft „not_signed_in", Kontenwechsel/-entfernen
      sind unbenutzbar. Fix: `accounts` aus `sessionManager` spiegeln (didSet-Publisher
      oder onAppear-Sync).
- [x] **I1-5 (Policy, mittel):** `FLUTCLOUD_URL` erreicht die iOS-App **nicht**:
      `project.yml:32` setzt nur die Build-Setting `FLUTCLOUD_URL: "$(FLUTCLOUD_URL)"`,
      aber `Info.plist` enthält **keinen** `FLUTCLOUD_URL`-Key. Damit ist
      `Bundle.main.object(forInfoDictionaryKey: "FLUTCLOUD_URL")` (`LoginViewModel.swift:27,42,76`)
      immer `nil` → `urlLocked` immer `false`, die Abweichungs-Checks greifen nie.
      `ios/README.md:124-132` verspricht das Gegenteil. Anders als Android (BuildConfig
      via `app/build.gradle.kts`, A9-11 erledigt) gibt es keine wirksame FlutCloud-only-
      Erzwingung. Fix: `FLUTCLOUD_URL` als `$(FLUTCLOUD_URL)`-Entry in `Info.plist`
      (bzw. `INFOPLIST_KEY_FLUTCLOUD_URL`) und `env.FLUTCLOUD_URL` im `ios.yml` setzen.
- [x] **I1-6 (Feature, mittel):** Dateiaktionen „Open"/„Download"/„Share" enden im
      Leeren: `downloadAndOpen` (`FilesViewModel.swift:113-129`) und `downloadAndShare`
      (`:150-164`) setzen `downloadedData`/`shareData`, aber **keine** View/Sheet/QuickLook/
      `UIActivityViewController` präsentiert sie; `FilesView.swift:100-102` `.onChange(of:
      downloadedData)` ruft sofort `clearDownloaded()`. `downloadToDownloads` (`:131-148`)
      schreibt nur ins Temp-Verzeichnis (kein Downloads-Ordner, kein Share-Sheet). Die
      README-Behauptung „download + share via the iOS share sheet" ist nicht implementiert.
      Fix: `.sheet`/QuickLook bzw. `UIActivityViewController`-Bridge verdrahten.
- [x] **I1-7 (i18n, mittel):** Keinerlei Lokalisierungs-Ressourcen im iOS-Port — es gibt
      keine `Localizable.strings`/`.lproj`-Dateien. `.localized` (`LoginView.swift:95-97`,
      `NSLocalizedString(self, …)`) fällt auf den rohen Key zurück → lange Keys erscheinen
      wörtlich in der UI (z. B. „files_offline_banner", „impersonation_notice",
      „remove_account_confirm", „error_flutcloud_app_missing"). Zusätzlich hartkodiert
      englisch: `AdminView.swift:183` „Remove", `SettingsView.swift:23` „Admin",
      `:32-36` „Name"/„Version"/„Features"/„Load", `Components.swift:182` „Type".
      Desktop + Android sind lokalisiert (en/de), iOS nicht. Fix: `Localizable.strings`
      (en/de) anlegen und alle Keys + die hartkodierten Texte aufnehmen.
- [x] **I1-8 (Perf, mittel):** Admin-Tab lädt ohne Suchbegriff alle Benutzer:
      `AdminView.swift:75` `.onAppear { viewModel.loadUsers() }` mit leerem Suchfeld;
      `AdminViewModel.loadPage` (`:54-68`) holt pro Seite 200 Benutzer-IDs und danach
      **200 Einzel-`getUser`-OCS-Requests** (N+1). Desktop verlangt seit D3/U-R8-12 einen
      Suchbegriff. Fix: Suchpflicht analog Desktop oder `loadUsers()` beim Appear ohne
      Suchbegriff unterbinden.
- [x] **I1-9 (UX, minor):** Suchergebnisse sind nur lesbar — `searchResultsList`
      (`FilesView.swift:131-143`) rendert `FileRow` ohne `contextMenu`; Download/Share/
      Rename/Delete fehlen in Treffern. Desktop (und Android nach A9-15) erlauben die
      Aktionen. Fix: `contextMenu` auch in der Ergebnisliste anbieten.
- [x] **I1-10 (Perf/Robustheit, mittel):** Upload/Download komplett im RAM: der
      `fileImporter`-Handler (`FilesView.swift:92-99`) liest die Datei mit
      `Data(contentsOf:)` komplett ein, `WebDavApi.upload` (`:82-89`) setzt die ganze
      `Data` als `httpBody`, `downloadToFile` (`:105-117`) lädt per Download-Task auf
      Disk, danach aber `Data(contentsOf: tempURL)` wieder in den Speicher. Desktop
      streamt chunked (>10 MiB), Android 64-KiB-Puffer (A9-5). `uploadStream`
      (`WebDavApi.swift:91-101`) ist Dead Code und streamt nicht. Fix: echte
      `uploadTask`/Download-Streams; `uploadStream` implementieren oder entfernen.
- [x] **I1-11 (Cache, minor):** `ListCache.swift` hat kein Limit — pro (Account, Pfad)
      eine JSON-Datei, keine Eviction, kein Maximalbestand. Desktop `cache.rs` evicted
      LRU (`MAX_CACHE_ENTRIES=500`). Fix: Bestand begrenzen (mtime/LRU-basiert).
- [x] **I1-12 (Parität, minor):** Kein Impersonation-Namespace-Guard: Desktop
      `webdav.rs:160-167` verwirft SEARCH-/List-Ergebnisse, wenn der Server den
      `Impersonate-User`-Header ignoriert (Admin-Namespace-Pfade); iOS `WebDavApi.list`/
      `search` prüfen das nicht → ein Admin bekäme bei nicht ehrendem Server still die
      eigene Liste. Fix: Guard portieren.

Keine neuen Befunde im Desktop-Backend (`commands.rs`, `webdav.rs`, `ocs.rs`,
`sync.rs`, `accounts.rs`, `error.rs`, `cache.rs`, `updater.rs`) und im Vue-
Frontend über die Lauf-12-Punkte (L12-N1 … L12-N6) hinaus.

**R8-C1 und R7-7 sind inzwischen erledigt** (gegen `release.yml` verifiziert):
- R8-C1 (tauri-action-Pin): `release.yml:145` pinnt jetzt auf den vollen Commit-SHA
  `tauri-apps/tauri-action@1deb371b0cd8bd54025b384f1cd735e725c4060f # v1.0.0` statt `@v1`.
- R7-7 (Release-Draft): `release.yml:152` steht auf `releaseDraft: false` (+
  `prerelease: false`) — Releases werden automatisch publiziert, der manuelle
  Draft-Publish-Schritt entfällt.
Beide wurden in das Archiv verschoben (unten). Hinweis: In den drei opencode-
Workflows (`opencode.yml:92`, `opencode-todo-issues.yml:38`,
`opencode-review.yml:38`) bleibt `anomalyco/opencode/github@latest` ein
bewegliches `@latest`-Tag (Supply-Chain-Thema wie R8-C1, CI-Pinning).
→ erledigt (#243): alle drei Workflows pinnen auf
`31406ccc… # v1.18.18`; zusätzlich sind die `npx opencode-ai@latest`-Aufrufe
in `opencode-review.yml` auf `1.18.21` gepinnt (2026-08-22).

Verifikation dieses Laufs (frisch ausgeführt): `cargo test --manifest-path
src-tauri/Cargo.toml` → **83 passed / 0 failed**; `cargo fmt --check` grün;
`cargo clippy --all-targets -- -D warnings` grün; `npm run build` grün.
iOS-Build auf Linux nicht möglich (kein Xcode).

### Review 2026-08-18 (Lauf 12, ganzes Projekt — neue Befunde)

Verifikation in diesem Lauf frisch durchgeführt (Details am Abschnittsende).
Gegenstand: gesamtes Projekt (Backend `src-tauri/`, Frontend `src/`,
Stores/IPC, Workflows `.github/`). Die Lauf-11-Fixes D5/D6/D7 wurden gegen
den aktuellen Code nachgeprüft: D5 bestätigt (`package.json` pinnt
`"typescript": "~5.8.3"`, `npm run build` grün); D6 bestätigt (Android
`:app:assembleDebug` grün); D7 bestätigt (`values/` + `values-de/` haben
identische Key-Sets, Screens nutzen `stringResource`). Die zuletzt offenen
Android-Punkte A9-7, A9-10, A9-13 und A9-14 (sowie die A9-9-Restlücke)
wurden **während dieses Laufs** über die gemergten Issue-PRs (#202, #204,
#205, #210, #215, #196) eingebracht und hier im aktuellen Code verifiziert
(Details in „A9-Nachprüfung (Lauf 12)" unten). Offen bleiben nur R8-C1
(CI-Pin) und R7-7 (Release-Draft-Hinweis). Neu gefunden:

- [x] **L12-N1 (Perf, mittel):** AdminPanel-Pagination ist unerreichbarer
      toter Code und die Suche lädt alle Treffer in einer Blocking-Kette.
      `AdminPanel.vue:175-195` (`listUsers`) ruft `api.adminListUsers(query)`
      **ohne** `limit`/`offset`; `admin_list_users` (`commands.rs:1229-1248`)
      reicht `limit=None` durch, wodurch `ocs::list_users` (`ocs.rs:83-120`)
      alle Treffer-Seiten in einer Schleife holt und `has_more=false`
      zurückgibt. `hasMore`/`offset` werden dadurch nie gesetzt → `loadPage`/
      `loadMore` (`AdminPanel.vue:197-228`, PAGE=200-Paginierung) ist
      unerreichbarer toter Code; der U-R8-12-Fix („Seite für Seite") greift
      nie. Auf großen Instanzen blockiert eine Suche wie „a" hunderte OCS-
      Requests. Fix: `listUsers` auf `loadPage(false)` umstellen (Offset-
      Reset, `hasMore` aus `AdminUsersResult` setzen).
      → erledigt 2026-08-22 (#225, s. Fix-Lauf oben).
- [x] **L12-N2 (Policy, minor):** `SettingsModal.vue:144-151` (`remove`)
      löscht ein Konto ohne Bestätigungsdialog — inkonsistent mit F7, das in
      `App.vue:104` (`removeActive`) und `AccountBar.vue:27` (`remove`) via
      `window.confirm(t("deleteAccountConfirm"))` durchgesetzt wird. Ein
      Klick auf „Remove account" im Settings-Tab entfernt das Konto sofort.
      Fix: denselben Confirm wie F7 ergänzen.
      → erledigt 2026-08-22 (#226, s. Fix-Lauf oben).
- [x] **L12-N3 (Perf, mittel):** `FileExplorer.vue:645-669` (`loadAdminUsers`)
      ruft `api.adminListUsers("")` beim Mount und bei jedem Kontowechsel —
      ungefilterter Komplettabruf aller Benutzer (alle Seiten, `ocs::list_users`
      ohne Limit). Für die Impersonation-Dropdown nötig, aber auf großen
      Instanzen derselbe unbounded-Fetch, den D3/U-R8-12 für den Admin-Tab
      unterbinden. Fix: Paginierung oder Sucheingabe im Filter (mindestens
      Lazy-Load statt sofortiger Vollabruf).
      → erledigt 2026-08-22 (#227, s. Fix-Lauf oben).
- [x] **L12-N4 (UX, minor):** Doppelte Fehleranzeige bei der Suche:
      `src/stores/files.ts:151-173` (`searchFiles`) setzt `error.value` **und**
      wirft weiter (`throw e`); `FileExplorer.vue:97-103` (`runSearch`) fängt
      und zeigt zusätzlich einen Toast. Ein Suchfehler erscheint dadurch
      doppelt (Fehler-Banner + Toast). Fix: entweder nicht rethrowen oder im
      Aufrufer nicht zusätzlich tosten.
      → erledigt 2026-08-22 (#228, s. Fix-Lauf oben).
- [x] **L12-N5 (Code-Qualität, minor):** Sortierkomparator ist doppelt
      implementiert: `FileExplorer.vue:54-75` (`sortedEntries`) und
      `EntryList.vue:52-75` (`sortedEntries`) enthalten identische Logik
      (Ordner zuerst, dann `size`/`mtime`/`name`). Da `kbdIndex` die
      Explorer-Sortierung und die Anzeige die EntryList-Sortierung nutzt,
      können künftige Änderungen (z. B. veränderte Sortierreihenfolge)
      auseinanderdriften. Fix: Komparator in eine geteilte Utility
      (z. B. `src/lib/`) auslagern.
      → erledigt 2026-08-22 (#229, `src/lib/sort.ts`, s. Fix-Lauf oben).
- [x] **L12-N6 (Perf, minor):** Das Frontend-Bundle ist eine einzige große
      JS-Chunk (691 kB minifiziert, 154 kB gzip; `vite build` meldet
      „Some chunks are larger than 500 kB"). `main.ts` importiert alle
      `@material/web/*`-Module statisch; Login/Settings/Admin-Modale und der
      Sync-Panel wären Kandidaten für `defineAsyncComponent`/
      dynamische Imports („Beim Start nur das laden, was sichtbar ist").
      Fix: Code-Splitting (Routen/Modale lazy), `chunkSizeWarningLimit`
      nicht einfach erhöhen.
      → erledigt 2026-08-22 (#230, s. Fix-Lauf oben).

Keine neuen Befunde im Backend (`commands.rs`, `webdav.rs`, `ocs.rs`,
`sync.rs`, `updater.rs`, `accounts.rs`, `cache.rs`) und in den
Workflows/Actions über die bekannten Punkte hinaus.

**A9-Nachprüfung (Lauf 12) gegen den aktuellen Code** — die Lauf-9/11-
Befunde sind mit den während dieses Laufs gemergten PRs umgesetzt und
hier gegen den Stand von HEAD (`862798e`) verifiziert:

- A9-7 (Admin-Impersonation) → **umgesetzt** (PR #202): `WebDavApi.kt`
  setzt `Impersonate-User`-Header und reicht `targetUser` durch alle
  WebDAV-Methoden (`list`/`search`/`exists`/`download`/`upload`/`mkdir`/
  `rename`/`delete`), `FilesViewModel.kt` führt `targetUser`-StateFlow +
  `setTargetUser`; `FilesScreen.kt:253` verdrahtet die Auswahl in der UI.
- A9-9 (Share-Optionen) → **umgesetzt** (PR #204): `FlutCloudApi.createShare`
  (`shareType`/`shareWith`/`password`/`expireDate`/`publicUpload`),
  `SharingDialog` in `FilesScreen.kt` mit Link/User/Group-Tab und
  Empfängerfeld.
- A9-10 (Quota-Freieingabe) → **umgesetzt** (PR #205): `AdminScreen.kt`
  `CustomQuotaDialog` (Wert + Einheit) + `quota_custom`-Strings.
- A9-13 (Brand-Themes + Accent) → **umgesetzt** (PRs #210/#215 + Fix
  `862798e`): `Theme.kt` mappt `operationflut`/`midnight`, `Color.kt` hat
  `operationflutScheme`/`midnightScheme` (Accent-Hue), `SettingsScreen.kt`
  Theme-Auswahl + Accent-Slider.
- A9-14 (`preview()` Dead Code) → **erledigt**: die Funktion existiert in
  `WebDavApi.kt` nicht mehr (durch den Share-Optionen-Umbau entfernt).
- A9-1 (Sync-Doku) → in Lauf 11 aufgelöst; PR #196 ergänzte die
  Sync-Dokumentation.

**GitHub-Issues (Schritt 6, nur lokale Quellen — keine gh/API-Aufrufe):**
Die während dieses Laufs gemergten Branches `opencode/issue196`,
`opencode/issue202`, `opencode/issue204`, `opencode/issue205`,
`opencode/issue210`, `opencode/issue215` (git log bis HEAD `862798e`)
belegen, dass die zugehörigen Issues umgesetzt wurden (Sync-Doku #196,
Impersonation #202, Share-Optionen #204, Quota-Freieingabe #205,
Brand-Themes #210/#215). Die offene Issue-Liste selbst ist per gh/API in
diesem Lauf nicht abrufbar (Verbot); ein Re-Sync durch den
`opencode-todo-issues`-Workflow beim nächsten Lauf ist wie in Lauf 11
empfohlen (veraltete Issues #192–#211 schließen bzw. referenzieren).
Die Issue-Templates wurden zwischenzeitlich überarbeitet
(`.github/ISSUE_TEMPLATE/`: `bug_report.yml`, `feature_request.yml`,
`android.yml`, `config.yml` — commit `0d1c469`).

Verifikation dieses Laufs (frisch ausgeführt): `cargo test --manifest-path
src-tauri/Cargo.toml` → **83 passed / 0 failed** (Tauri-Linux-Systemdeps
`libwebkit2gtk-4.1-dev`/`libgtk-3-dev`/… nachinstalliert, `glib-2.0.pc`
wieder gefunden); `cargo clippy --all-targets --manifest-path
src-tauri/Cargo.toml -- -D warnings` grün; `cargo fmt --manifest-path
src-tauri/Cargo.toml --all --check` grün; `npm run build` (vue-tsc + vite)
grün (nur Chunk-Size-Warnung, s. L12-N6); Android
`cd android && ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
(D6 bestätigt).

### Review 2026-08-17 (Lauf 11, Fokus Desktop-UI ≈ Android-UI — neue Befunde)

Verifikation in diesem Lauf frisch durchgeführt: `cargo test --manifest-path
src-tauri/Cargo.toml` → **83 passed / 0 failed** (Tauri-Linux-Systemdeps
`libwebkit2gtk-4.1-dev`/`libgtk-3-dev` nachinstalliert); `cargo fmt
--check` grün; `cargo clippy --all-targets -- -D warnings` grün. Zu
Laufbeginn schlugen **`npm run build`** (s. D5) und der **Android-Build**
(s. D6) fehl — beide Regressionen waren in CI bestätigt und sind mit diesem
Lauf behoben (Details bei D5/D6). Wichtiger Kontext: Der Lauf-10-Bericht
behauptet „keine Commits seit Lauf 9, git status sauber, alle 16
A9-Befunde unverändert offen" — **das stimmt für den aktuellen Stand
nicht**: seit Lauf 9 wurden u. a. die Merges PR #132 (Android i18n) und
PR #127 (Ordner-Navigation) sowie der dependabot-Bump
`typescript-7.0.2` in `main` gemerged (`git log`: `8012480`,
`74ff834`, `79f4e4b`). Die Android-Feature-Parität (Issue #136) und
Android-i18n (AC2) sind im Archiv als umgesetzt dokumentiert und im Code
nachweisbar. Die A9-Punkte wurden daher gegen den **aktuellen** Code
nachgeprüft (Ergebnis in der Liste unten).

Neu gefunden:

- [x] **D5 (Build/CI, hoch):** Frontend-Build ist kaputt —
      `package.json` pinnt `"typescript": "~7.0.2"` zusammen mit
      `"vue-tsc": "^3.3.5"`. TypeScript 7.0.2 (installiert: 7.0.2) hat den
      Subpath `./lib/tsc` aus seinen `exports` entfernt; vue-tsc 3.3.9
      (`resolveTscPath`, `node_modules/vue-tsc/index.js:73`) benötigt ihn
      aber zwingend → `ERR_PACKAGE_PATH_NOT_EXPORTED` beim `vue-tsc
      --noEmit`. Lokal reproduziert, und CI bestätigt es: `build`
      (run 32024685413) und `Lint` (run 32024685371) failen auf `main`
      seit dem Merge der Review-Commits. Fix: TypeScript auf eine
      vue-tsc-kompatible Linie zurückstufen (z. B. `~5.9.3`, wie vor dem
      dependabot-Bump) oder vue-tsc/@volar auf eine TS-7-fähige Version
      heben; dependabot-Bump war der Auslöser (`8012480 Merge …/typescript-7.0.2`).
      → erledigt: `package.json` steht auf `"typescript": "~5.8.3"`; `npm run
      build` (vue-tsc + vite) ist grün.
- [x] **D6 (Build/CI, hoch):** Android-Build ist kaputt —
      `FilesViewModel.kt:144` weist `error.value` einen rohen `String`
      zu („The folder name must not contain '/', '.' or '..'."), das Feld
      ist aber `MutableStateFlow<UiMessage?>` → Kotlin-Compile-Fehler
      `Assignment type mismatch: actual type is 'String', but 'UiMessage?'
      was expected` in `:app:compileDebugKotlin`. CI bestätigt: `android`
      (run 32024685436) failt. Fix: `UiMessage` mit einem Key aus
      `strings.xml` verwenden (die Meldung gehört zusätzlich lokalisiert,
      s. D7).
      → erledigt: `FilesViewModel.mkdir` emittiert `UiMessage(R.string.error_invalid_folder_name)`;
      `./gradlew :app:assembleDebug` + `:app:lintDebug` sind grün.
- [x] **D7 (i18n, mittel):** Android-i18n ist **unvollständig** — der
      Archiv-Claim AC2 („alle UI-Texte in `strings.xml`") stimmt nicht:
      aktuelle Screens der Feature-Parität (Issue #136) enthalten ~20
      hartkodierte englische Strings, teilweise obwohl die Keys bereits in
      `strings.xml`/`values-de` existieren:
      - `LoginScreen.kt:89,95` „Sign in"/„Register" (Keys `sign_in`/
        `register` existieren, werden nicht genutzt), `:118` „New
        username"/„Username" (`new_username`/`username`), `:126`
        „Password"/„App token / password" (`app_token`), `:138` „Display
        name (optional)" (`display_name_optional`), `:144` „Admin
        credentials" (`admin_credentials`), `:152` „Admin username"
        (`admin_username`), `:160` „Admin password" (`admin_password`),
        `:167` Register-Hinweistext (`register_description`), `:197`
        „Register"/„Sign in".
      - `FilesScreen.kt:533` „Existing shares", `:539` „Loading shares…",
        `:541` „No shares yet", `:551` „New public link", `:592`
        „password", `:593` „expires $it", `:599` „Revoke", `:603-608`
        `shareLabel` „User"/„Group"/„Public link"/„Share", `:622` „Offline
        — showing cached data".
      - `AdminScreen.kt:169` „Load more", `:267` „Manage groups", `:332`
        „Groups —", `:339` „No groups", `:361` „Remove", `:370` „Group
        name", `:374-377` Gruppen-Hinweistext, `:386` „Create group",
        `:393` „Add to group", `:397` „Close".
      - `FilesViewModel.kt:144` hartkodierte Fehlermeldung (s. D6),
        `AdminViewModel.kt:63` „Could not reach the server: …" und `:65`
        rohes `e.message` — inkonsistent mit `loadUsers` (nutzt
        `networkUiMessage`/`toUiMessage`).
      Fix: alle Texte auf `stringResource`-Keys umstellen, fehlende Keys
      (Share-/Gruppen-Dialog, Offline-Banner) in `values/` + `values-de/`
      ergänzen.
      → erledigt: alle genannten Screens/ViewModels nutzen `stringResource`
      bzw. `UiMessage`/`networkUiMessage`/`toUiMessage`; `values/` und
      `values-de/` enthalten dieselben Keys (Abgleich grün).

**A9-Nachprüfung gegen den aktuellen Code** (Lauf-9-Liste unten bleibt
formal bestehen; hier der aktuelle Status):

- A9-1 (Sync fehlt) → durch Dokumentation aufgelöst: `android/README.md`
  „Consciously not on mobile" dokumentiert den Zwei-Wege-Sync als bewusste
  Produktentscheidung (Desktop-only, Phase-2-Roadmap).
- A9-2 (Android ohne Lokalisierung) → **umgesetzt**: alle UI-Texte liegen in
  `values/strings.xml` + `values-de/strings.xml` (identische Key-Sets),
  ViewModels emittieren Ressourcen-IDs; Restlücken aus D7 sind geschlossen
  (siehe D7 → erledigt).
- A9-3 (Rename nur Ordner) → **umgesetzt**: `EntryRow` bietet „Rename" für
  Dateien **und** Ordner an (`FilesScreen.kt`, Dropdown ohne `isDir`-Guard).
- A9-4 (Suche ohne Debounce) → **umgesetzt**: `FilesViewModel.search`
  debounced mit `delay(300)` (analog Desktop).
- A9-5 (RAM-Loading) → **umgesetzt**: `WebDavApi.downloadToFile`/
  `uploadStream` (Streaming, 64-KiB-Puffer) und `FilesViewModel.kt`
  nutzen sie; `response.body?.bytes()`/`readAllBytes` nur noch als
  Fallback ohne Content-Length.
- A9-6 (Upload überschreibt still) → **umgesetzt**: `uploadStream` führt
  einen `exists()`-Check aus und zeigt bei Treffer den Overwrite-Confirm
  (`PendingUpload`/`confirmUpload`), analog Desktop-Q9.
- A9-7 (Impersonation fehlt) → **umgesetzt** (Lauf 12): `Impersonate-User`-
  Header + `targetUser`-Durchreichung in `WebDavApi.kt`, `targetUser`-
  StateFlow in `FilesViewModel.kt`, UI in `FilesScreen.kt`.
- A9-8 (Gruppenverwaltung fehlt) → **umgesetzt**: `GroupsDialog` +
  `AdminViewModel.addToGroup`/`removeFromGroup`/`createGroup` +
  OCS-Gruppen-Endpunkte.
- A9-9 (Shares nur Public-Link) → **teilweise**: Share-Dialog mit Liste +
  Widerruf jetzt vorhanden (`ShareDialog`, `vm.loadShares`/
  `vm.deleteShare`); Erstellung weiterhin nur Public-Link (`shareType = 3`,
  `FilesViewModel.kt:202`), User-/Gruppen-Shares und `publicUpload`
  fehlen → offen (reduziert).
- A9-10 (Quota nur Presets) → **umgesetzt** (Lauf 12): `AdminScreen.kt`
  `CustomQuotaDialog` mit freier Werteingabe (PR #205).
- A9-11 (FLUTCLOUD_URL nicht erzwungen) → **umgesetzt**: `app/build.gradle.kts`
  liest die Server-URL aus der `FLUTCLOUD_URL`-Umgebungsvariable (Fallback
  `-PflutcloudUrl`) und baut sie als `BuildConfig.FLUTCLOUD_URL` ein;
  `LoginScreen` sperrt das URL-Feld, `LoginViewModel.signIn`/`register`
  verwerfen abweichende URLs (`error_wrong_server_url`). Hinweis: Die
  GitHub-Actions (`android.yml`, `release.yml`, `build.yml`) wurden
  **bewusst nicht** angefasst (Workflows sind für automatisierte Läufe tabu);
  da die Gradle-Konfiguration die Umgebungsvariable direkt liest, greift ein
  späteres `env.FLUTCLOUD_URL` in den Workflows automatisch.
- A9-12 (Downloads in App-Files) → **umgesetzt**: Dateiaktion „Download"
  speichert in den öffentlichen **Downloads-Ordner** (MediaStore ab
  Android 10, Direktzugriff davor mit Laufzeit-Permission auf API 26–28),
  „Teilen" öffnet das **Share-Sheet** (`ShareSheet`/`ACTION_SEND`); „Öffnen"
  bleibt FileProvider/`ACTION_VIEW`.
- A9-13 (Theme nur system/light/dark) → **umgesetzt** (Lauf 12):
  `operationflut`/`midnight` + Accent-Hue in `Theme.kt`/`Color.kt`/
  `SettingsScreen.kt` (PRs #210/#215).
- A9-14 (preview() Dead Code) → **erledigt** (Lauf 12): Funktion in
  `WebDavApi.kt` entfernt.
- A9-15 (Suchergebnisse nur lesbar) → **umgesetzt**: `SearchResults` reicht
  `onDownload`/`onShareFile`/`onRename`/`onShareLink`/`onDelete`/
  `onJumpToPaired` an `EntryRow` durch — Aktionen in Suchtreffern sind aktiv.
- A9-16 (Offline-Cache fehlt) → **umgesetzt**: `listCache` +
  `OfflineBanner` („Offline — showing cached data", D7 betrifft den
  hartkodierten Text).

Weiterhin offen aus früheren Läufen: R8-C1 (tauri-action auf `@v1` gepinnt)
und R7-7 (Release-Draft manuell publizieren, als Schritt 3 dokumentiert).
→ erledigt/obsolet (Lauf 16): R8-C1 erledigt — SHA-Pin `release.yml:193`
(bereits Lauf 13 verifiziert); R7-7 obsolet — `releaseDraft: false`
(`release.yml:200`), Details im Review 2026-08-22 (Lauf 16).
D1–D4 (Lauf 10) sind im aktuellen Code verifiziert und **erledigt**: D1
`updater.rs` emittiert `UpdateStatus { code: "checksum_warning" }` statt
String; D2 die hartkodierten deutschen Notification-Texte sind entfernt
(englische Texte, `sync.rs:1195-1204` + `updater.rs:534-539`); D3
`SettingsModal.vue` verlangt wie `AdminPanel.vue` einen Suchbegriff
(`searchUsersRequired`); D4 der Archiv-Dateiname ist in `todo.md` selbst
konsolidiert (Abschnitt „## Archiv (erledigt)").

**GitHub-Issues (nur gelesen):** Die offenen Issues #192–#211 (am
2026-08-17 vom `opencode-todo-issues`-Workflow aus dem Lauf-9/10-Stand der
todo.md erzeugt) spiegeln die veraltete Befundlage: #203 (Gruppen), #200
(RAM-Loading), #208 (Offline-Cache) und #196 (Sync) beschreiben Punkte, die
im aktuellen Code umgesetzt bzw. dokumentiert sind (s. Archiv-Abschnitt
Lauf 11). **#206 (Downloads) ist mit diesem Lauf umgesetzt** (Downloads-Ordner
+ Share-Sheet + `FLUTCLOUD_URL`-Preconfig, s. Archiv-Abschnitt Issue #206).
Zusätzlich zum bekannten D5/D6 stehen #192 (update://status), #193
(i18n-Notifications), #194 (AdminPanel `adminListUsers("")`), #195
(Archiv-Dateiname), #154 (tauri-action @v1) und #155 (Release-Draft) offen —
D1–D4 sind im Code nachgewiesen erledigt (s. oben), inhaltlich identisch zu
R8-C1/R7-7. Ein Abgleich Issues ↔ todo.md wäre beim nächsten Workflow-Lauf
sinnvoll (veraltete Issues schließen bzw. referenzieren).

### Review 2026-08-17 (Lauf 10, automatisierter Review — neue Befunde)

Verifikation in diesem Lauf frisch durchgeführt: `cargo test --manifest-path
src-tauri/Cargo.toml` → 78 passed / 0 failed; `npm run build` (vue-tsc +
vite) grün (Frontend-Deps per `npm ci` nachinstalliert; die Tauri-Linux-
Systemdeps `libwebkit2gtk-4.1-dev`/`libgtk-3-dev`/… wurden für den Testlauf
nachinstalliert — ohne sie bricht `cargo test` mit „glib-2.0 not found" ab).
Fokus: IPC-Commands, Updater-/Sync-Notifications, Admin-UI, CI-Workflows.
Alle 16 offenen Android-Befunde (A9-1 … A9-16) sowie R8-C1 und R7-7 sind
unverändert offen (keine Commits seit Lauf 9, `git status` sauber). Neu
gefunden:

- [x] **D1 (Bug, minor):** `update://status`-Payload ist typinkonsistent —
      `updater.rs:360` (`download_update`) emittiert bei fehlender GitHub-
      Checksumme einen plain `String` („checksum unavailable, skipping
      verification"), `download_and_install_update` (`updater.rs:567-598`)
      emittiert dagegen `UpdateStatus { code, asset_name }`-Objekte. Die
      Listener sind auf genau eine der beiden Formen getypt und rendern die
      jeweils andere falsch: `src/App.vue:159-160` lauscht `listen<string>`
      → Objekt wird zu „[object Object]" im Update-Banner;
      `SettingsModal.vue:163-165` lauscht `listen<UpdateStatus>` → beim
      String-Event ist `e.payload.code` `undefined`, die Warnung wird still
      verschluckt. Die sicherheitsrelevante Meldung „Checksumme nicht
      verifizierbar" erreicht damit nie die UI (nur `eprintln!`). Fix: in
      `download_update` ein `UpdateStatus { code: "checksum_unavailable",
      asset_name: None }`-Objekt emittieren und in `App.vue` den String-Cast
      auf das Objekt-Schema umstellen.
- [x] **D2 (i18n, minor):** Native OS-Notifications im Backend sind hartkodiert
      deutsch, obwohl die UI seit N14 über Codes/`translateError` lokalisiert:
      `sync.rs:1192-1204` (`notify(…, "FlutLink Sync", "{files_done} Datei(en)
      erfolgreich synchronisiert.")` bzw. „konnten nicht synchronisiert
      werden.") und `updater.rs:534-539` (`check_update`, „Version {} ist
      verfügbar (aktuell: {})."). Bei englischer UI erscheinen deutsche
      System-Notifications. Fix: Sprache der aktiven UI ins Backend spiegeln
      (persistierte `lang` aus `src/stores/ui.ts` → `AppState`) oder Codes
      statt Freitext emittieren und das Frontend übersetzen lassen.
- [x] **D3 (Perf, mittel):** `SettingsModal.vue:112` (`loadUsers`) ruft
      `api.adminListUsers("")` — beim Öffnen des Admin-Tabs werden ALLE
      Benutzer der Instanz geladen (`ocs.rs::list_users` paginiert mit
      Offset durch alle Seiten; `admin_list_users`, commands.rs:1226-1240
      reicht `""` ungefiltert durch). Das widerspricht direkt U-R8-12, das
      `AdminPanel.vue` auf Suchpflicht (`searchUsersRequired`) umgestellt
      hat — auf großen Instanzen hängt der Admin-Tab minutenlang. Fix:
      Suchbegriff verlangen (wie in `AdminPanel.vue`) oder die Liste in der
      UI paginieren.
- [x] **D4 (Konsistenz, minor):** Workflow-Prompts referenzieren
      `archived-todo.md` (`opencode.yml:120`, `opencode-review.yml:66`),
      die Aufgabe/dieser Lauf sagt `archived-todos.md`, während der
      `todo.md`-Kopf erklärt, es ersetze `archived-todo.md` und das Archiv
      liege im selben Dokument (Abschnitt „## Archiv (erledigt)"). Drei
      verschiedene Namen für denselben Zweck — Reviews archivieren dadurch
      inkonsistent. Fix: eine Konvention festlegen (Archiv-Sektion in
      `todo.md` beibehalten) und die Prompt-Texte anpassen.

Keine neuen Befunde in den Workflows/Actions über die bekannten Punkte
R8-C1 (tauri-action nur auf `@v1` gepinnt, `release.yml:135`) und R7-7
(Release-Draft muss manuell publiziert werden) hinaus. Keine Code-Änderungen
in diesem Lauf; `cargo fmt`/`cargo clippy`-Stand unverändert.

### Review 2026-08-16 (Lauf 9, Fokus Desktop-UI ≈ Android-UI — neue Befunde)

Fokus dieses Laufs: Parität zwischen Desktop-UI (Vue/`FileExplorer.vue`,
`SyncPanel.vue`, `AdminPanel.vue`, `SettingsModal.vue`) und dem Android-Port
(`android/`). Verifikation frisch und grün: `cargo test --manifest-path
src-tauri/Cargo.toml` → 76 passed / 0 failed; `cargo clippy --all-targets
--manifest-path src-tauri/Cargo.toml -- -D warnings` grün; `cargo fmt --check`
grün; `npm run build` (vue-tsc + vite) grün; `./gradlew :app:assembleDebug`
in `android/` grün. `android/README.md` und `AGENTS.md` behaupten, der
Android-Port „spiegle den Desktop-Funktionsumfang" — das trifft laut
Befunden **nicht** zu: Neu gefunden:

- [x] **A9-1 (Feature, hoch):** Android-Port hat **keinerlei Sync-Funktion** —
      der Desktop-Kern (`SyncPanel.vue` + `sync.rs`, Zwei-Wege-Sync mit
      Journal/Planner/Worker) fehlt komplett. `HomeScreen.kt` kennt nur die
      Tabs Files/Admin/Settings (kein Sync-Tab); im gesamten Android-Code
      existiert kein Sync-Äquivalent. Damit widerspricht der Port der
      AGENTS.md-Aussage („spiegelt den Desktop-Funktionsumfang"). Fix:
      Sync-Tab + Sync-Engine (oder Funktionsumfang in README korrigieren).
      → durch Dokumentation aufgelöst (android/README.md „Consciously not
      on mobile"), siehe Lauf 11.
- [x] **A9-2 (i18n, mittel):** Android komplett ohne Lokalisierung —
      `android/app/src/main/res/values/strings.xml` enthält nur `app_name`;
      sämtliche UI-Texte sind hartkodiert englisch (`FilesScreen.kt`,
      `LoginScreen.kt`, `AdminScreen.kt`, `SettingsScreen.kt`). Der Desktop
      lokalisiert alles über `src/lib/i18n.ts` (en/de). AGENTS.md fordert
      i18n für alle UI-Texte. Fix: Android-`strings.xml`-Ressourcen + Werte
      nachziehen.
- [x] **A9-3 (Bug, minor):** Android erlaubt **Rename nur für Ordner** —
      `FilesScreen.kt` `EntryRow` (Z. 375-384) zeigt „Rename" nur
      `if (entry.isDir)`; Dateien sind auf Android nicht umbenennbar.
      Desktop erlaubt Rename für Dateien und Ordner (`FileExplorer.vue`
      `startRename`). Fix: Menüpunkt auch für Dateien anbieten.
- [x] **A9-4 (Perf, minor):** Android-Suche ohne Debounce —
      `FilesScreen.kt` `SearchBar` (Z. 280-293) ruft `onValueChange =
      { vm.search(it) }` bei **jedem** Tastendruck → pro Zeichen ein
      SEARCH-Request mit `depth: infinity`. Desktop debounced 300 ms
      (`FileExplorer.vue` Z. 31/47-60). Fix: Debounce in
      `FilesViewModel.search`.
- [x] **A9-5 (Robustheit, mittel):** Android lädt Dateien komplett in den
      RAM: Upload via `readAllBytes` (`FilesScreen.kt` Z. 523-524), Download
      via `response.body?.bytes()` (`WebDavApi.download` Z. 121-135). Desktop
      streamt (chunked upload > 10 MiB, `stream_to_file`). Große Dateien →
      OOM auf Android. Fix: Streaming (OkHttp ResponseBody → Datei), optional
      Chunked-Upload-Parität. → umgesetzt (downloadToFile/uploadStream, 64-KiB
      Puffer; nur noch Fallback ohne Content-Length), siehe Lauf 11.
- [x] **A9-6 (Bug, Datenverlust-Risiko, mittel):** Android-Upload
      überschreibt existierende Dateien still — der Desktop-Fix **Q9**
      (Existenz-Check `webdav::exists` + `AppError::TargetExists` +
      Overwrite-Confirm in `FileExplorer.vue`) wurde **nicht** portiert.
      `WebDavApi.exists()` (Z. 84-102) existiert, wird aber von
      `FilesViewModel.upload` (Z. 173-187) nie aufgerufen. Fix:
      `exists`-Check + Bestätigungs-Dialog vor dem PUT.
- [x] **A9-7 (Feature, mittel):** Admin-Impersonation fehlt auf Android —
      Desktop erlaubt Admins das Browsen fremder Nutzer (`webdav_list`
      `target_user` + `FileExplorer.vue` adminViewAll, `Impersonate-User`-
      Header in `webdav.rs`); `AdminScreen.kt`/`WebDavApi.kt` haben weder
      `target_user`-Parameter noch `Impersonate-User`-Support. Fix:
      Impersonation im Admin-Screen (Zugriff auf alle Nutzer-Dateien).
      → umgesetzt (`Impersonate-User`-Header + `targetUser` in
      `WebDavApi.kt`/`FilesViewModel.kt`, UI in `FilesScreen.kt`),
      siehe Lauf 12.
- [x] **A9-8 (Feature, mittel):** Admin-Gruppen-Verwaltung fehlt auf Android —
      Desktop hat seit Q3 `admin_list_groups`/`admin_create_group`/
      `admin_add_group_member`/`admin_remove_group_member` + UI in
      `AdminPanel.vue`; Android-`AdminScreen.kt`/`FlutCloudApi.kt` haben
      keine Gruppen-Endpunkte. Auch fehlen displayname/email/password-
      Bearbeitung (Desktop `admin_edit_user`). Fix: Gruppen-API + UI portieren.
      → umgesetzt (GroupsDialog + AdminViewModel-Gruppenaktionen), siehe Lauf 11.
- [x] **A9-9 (Feature, minor):** Android-Shares nur Public-Link —
      `FilesViewModel.createPublicShare` (Z. 153-171) hartkodiert
      `shareType = 3`; User-/Gruppen-Shares (Desktop P1) und
      `publicUpload`-Option fehlen. `FlutCloudApi.listShares`/
      `deleteShare` (Z. 245-253) existieren zwar, werden aber von keiner
      UI aufgerufen (Dead Code) → kein Share-Management (Liste/Widerruf,
      Desktop P2). Fix: Share-Optionen + Verwaltung portieren.
      → umgesetzt (Share-Dialog mit Link/User/Group-Typ, Empfängerfeld,
      `publicUpload`-Checkbox; `listShares`/`deleteShare` in der UI
      verdrahtet), siehe Lauf 12.
- [x] **A9-10 (Feature, minor):** Android-Quota-Verwaltung nur Presets
      (unlimited/1/5/10 GB); Desktop erlaubt seit Q8 freie Werteingabe
      („custom"). Fix: Freieingabe ergänzen.
      → umgesetzt (`CustomQuotaDialog` in `AdminScreen.kt`), siehe Lauf 12.
- [x] **A9-11 (Policy, mittel):** Android-Login erzwingt `FLUTCLOUD_URL`
      nicht — `LoginViewModel` (Z. 28-33, 37) nimmt jede editierbare URL
      (Default aus `BuildConfig.FLUTCLOUD_URL`) und prüft nur die
      FlutCloud-App-Capability (`verifyServer`); Desktop erzwingt die exakte
      `.env`-URL via `assert_flutcloud_url` (`flutcloud.rs`). AGENTS.md
      verspricht „ausschließlich `$FLUTCLOUD_URL`". Fix: URL gegen
      `BuildConfig.FLUTCLOUD_URL` validieren oder URL-Feld sperren.
- [x] **A9-12 (UX, minor):** Android speichert Downloads in den App-Files
      (`saveToAppStorage`, `FilesViewModel` Z. 96-102) statt im Download-
      Ordner bzw. mit Öffnen/Teilen; kein Share-Sheet. Desktop öffnet
      Dateien direkt (P8). Fix: `ACTION_VIEW`/MediaStore-Download + Teilen.
      → umgesetzt (FileOpener/ACTION_VIEW, FileProvider), siehe Lauf 11.
- [x] **A9-13 (Design, minor):** Android-Theme deckt nur system/light/dark +
      dynamic color ab (`Theme.kt`); Desktop bietet die FlutCloud-Brand-
      Themes `operationflut`/`midnight` + Accent-Hue-Slider (U8). Fix:
      Brand-Themes + Accent-Hue auf Android portieren.
      → umgesetzt (`operationflut`/`midnight`-Schemes in `Color.kt` +
      `Theme.kt`, Accent-Hue-Slider in `SettingsScreen.kt`), siehe Lauf 12.
- [x] **A9-14 (Dead Code, minor):** `WebDavApi.preview()` (Z. 185-201) wird
      nirgends aufgerufen — Thumbnails existieren nur im Desktop
      (`webdav_thumbnail` + `thumbs`-Cache). Fix: Entweder Thumbnails in
      `FilesScreen` nutzen oder `preview()` entfernen.
      → erledigt (Funktion entfernt), siehe Lauf 12.
- [x] **A9-15 (UX, minor):** Android-Suchergebnisse sind nur lesbar —
      `FilesScreen.kt` `SearchResults` (Z. 315-318) übergibt `onRename`/
      `onShare`/`onDelete`/`onJumpToPaired` als No-op-`{}`. Desktop erlaubt
      in Suchtreffern weiterhin Aktionen. Fix: Aktionen in Treffern
      freischalten oder Hinweis ergänzen.
- [x] **A9-16 (Feature, minor):** Kein Offline-Cache auf Android — Desktop
      hat seit Q2 den Listing-Cache (`cache.rs`, Stale-Flag + Offline-Banner
      in `FileExplorer.vue`); Android zeigt bei Netzwerkausfall nur Fehler.
      Fix: Listing-Cache + Offline-Banner portieren.
      → umgesetzt (listCache + OfflineBanner), siehe Lauf 11.

Nicht erledigt aus früheren Läufen (weiter offen): R8-C1 (tauri-action auf
`@v1` gepinnt), R7-7 (Release-Draft-Hinweis). U-R8-1 bis U-R8-12 und R8-B1
sind dagegen umgesetzt (im Lauf-8-Abschnitt abgehakt, Details im Archiv).

### Review 2026-08-16 (Lauf 8, Fokus UX — neue Befunde)

Verifikation in diesem Lauf frisch durchgeführt und grün: `cargo test --manifest-path
src-tauri/Cargo.toml` → 76 passed / 0 failed; `cargo clippy --all-targets
--manifest-path src-tauri/Cargo.toml -- -D warnings` grün; `cargo fmt --check` grün;
`npm run build` (vue-tsc + vite) grün. Fokusbereich UX: Dateibrowser
(`FileExplorer.vue`/`EntryList.vue`), Dialoge (Login/Settings/Admin/Share), Sync-Panel,
Stores (`files.ts`/`ui.ts`) und die zugehörigen Backend-Commands. Neu gefunden:

- [x] **U-R8-1 (UX, Bug, mittel):** Tastatur-Navigation ist unsichtbar und weicht bei
      Sortierung von der Anzeige ab. → umgesetzt (siehe Archiv).
- [x] **U-R8-2 (UX, Bug, mittel):** Transfer-Fortschrittsleiste bleibt nach
      Einzel-Datei-Operationen dauerhaft stehen. → umgesetzt (siehe Archiv).
- [x] **U-R8-3 (UX, Bug, minor):** Grid-Ansicht: Doppelklick schaltet die Auswahl
      wieder ab. → umgesetzt (siehe Archiv).
- [x] **U-R8-4 (UX, minor):** Share-Badge (`sharesByPath`-Zähler) fehlt in der
      Grid-Ansicht. → umgesetzt (siehe Archiv).
- [x] **U-R8-5 (UX, minor):** Update-Banner überlagert den Header ohne
      Layout-Ausgleich. → umgesetzt (siehe Archiv).
- [x] **U-R8-6 (UX, minor):** Thumbnail-Cache und Share-State wachsen über
      Navigationen unbegrenzt. → umgesetzt (siehe Archiv).
- [x] **U-R8-7 (UX, minor):** `AccountBar.vue` Filter-Hinweis nutzt hartkodierte
      Farben statt M3-Tokens. → umgesetzt (siehe Archiv).
- [x] **U-R8-8 (UX, minor):** Theme-FOUC beim Start mit „System Default".
      → umgesetzt (siehe Archiv).
- [x] **U-R8-9 (UX, minor):** Accent-Slider in den Settings startet mit dem
      falschen Default. → umgesetzt (siehe Archiv).
- [x] **U-R8-10 (UX/Validierung, mittel):** „Neuer Ordner" erlaubt `/` im Namen →
      versehentliches Anlegen von Ordnerketten. → umgesetzt (siehe Archiv).
- [x] **U-R8-11 (UX, minor):** Login-/Registrier-Formular wird nach Erfolg bzw.
      Schließen nicht geleert. → umgesetzt (siehe Archiv).
- [x] **U-R8-12 (UX, minor):** AdminPanel lädt beim „Benutzer auflisten" ohne
      Suchbegriff alle Benutzer (keine UI-Pagination). → umgesetzt (siehe Archiv).
- [x] **R8-B1 (Backend, Bug, minor):** `webdav_bulk_download` überschreibt
      gleichnamige Dateien aus verschiedenen Ordnern. → umgesetzt (siehe Archiv).
- [x] **R8-C1 (CI, minor):** `release.yml` (Z. 135) pinnt `tauri-apps/tauri-action`
      nur auf `@v1` (bewegliches Tag). Für Supply-Chain-Härtung auf einen
      vollständigen Commit-SHA pinnen (wie bei den übrigen Drittanbieter-Actions).
      → im automatisierten Lauf offen gelassen (Workflow-Dateien sind von der
      Aufgabe ausgenommen; weiterhin offen). → erledigt in Lauf 13 verifiziert:
      `release.yml:145` pinnt den vollen SHA `1deb371b… # v1.0.0` (siehe Archiv).

### Review 2026-08-16 (Lauf 7, Release-Review v1 — neue Befunde)

Verifikation in diesem Lauf frisch durchgeführt und grün: `cargo test --manifest-path
src-tauri/Cargo.toml` → 69 passed / 0 failed; `cargo clippy --all-targets
--manifest-path src-tauri/Cargo.toml -- -D warnings` grün; `cargo fmt --check` grün;
`npm run build` (vue-tsc + vite) grün. i18n-Keys aller Komponenten gegen `src/lib/i18n.ts`
geprüft (vollständig), keyring-Nutzung (`accounts.rs`, Linux-Secret-Service-Hints),
CSP und Capabilities (`capabilities/default.json`) sind sauber. Neu gefunden:

- [x] **R7-1 (Bug, mittel):** `assert_flutcloud_url` normalisiert die `.env`-URL nicht —
      `src-tauri/src/flutcloud.rs:43` vergleicht `normalized` mit dem **rohen**
      `flutcloud_url()?`-Wert (`eq_ignore_ascii_case`). Enthält `FLUTCLOUD_URL` ein
      nachgestelltes `/` (z. B. `https://flutcloud.de/`), schlägt jeder `account_add`/
      `register_user`/`load_accounts` mit `NotFlutCloud` fehl — Login komplett unmöglich.
      Fix: `normalize_url(&flutcloud_url()?)` auf der env-Seite anwenden; der bestehende
      Test `accepts_the_flutcloud_url_with_and_without_slash` (`flutcloud.rs:81-88`)
      deckt nur den Instanz-Slash ab, nicht den env-Slash. → umgesetzt (siehe Archiv).
- [x] **R7-2 (Security-Hardening, minor):** `updater.rs:256` übernimmt den `asset_name` aus
      der GitHub-API-Antwort ungeprüft als lokalen Dateinamen (`tmp_dir.join(&info.asset_name)`).
      GitHub verhindert `/` in Asset-Namen praktisch, aber Pfadtrenner (Windows `\`) oder `..`
      könnten die Download-Datei außerhalb von `flutlink_update/` ablegen. Fix: Trenner im
      Namen ablehnen bzw. mit `Path::file_name()` vergleichen. → umgesetzt (siehe Archiv).
- [x] **R7-3 (Robustheit, minor):** `updater.rs` `install_update` ruft `path.to_str().unwrap()`
      auf (Z. 368, 398, 400, 430, 440, 452) — Panic, falls der remote kommende Asset-Name
      nicht UTF-8 ist. `unwrap_or("")` verwenden (konsistent zum `extension()`-Handling
      direkt darüber). → umgesetzt (siehe Archiv).
- [x] **R7-4 (UX-Regression-Risiko, minor):** `src/lib/ripple.ts:25-26` setzt bei jedem
      `pointerdown` inline `position: relative; overflow: hidden` auf dem Host und
      überschreibt damit bewusst gesetzte Styles (z. B. `position: fixed` bzw. Dropdown-/
      Sticky-Kontexte) → Tooltips/Menüs können abgeschnitten werden. Stattdessen die Regeln
      global per CSS-Klasse am Ripple-Span anwenden oder nur setzen, wenn noch kein
      Inline-Style existiert. → umgesetzt (siehe Archiv).
- [x] **R7-5 (Release-Prozess):** Version steht überall auf `0.1.0` (`package.json`,
      `src-tauri/Cargo.toml`, `src-tauri/tauri.conf.json`). Für ein „v1"-Release auf `1.0.0`
      anheben, sonst heißen die Assets `FlutLink_0.1.0_…` und der Tag `v0.1.0` (`release.yml`
      leitet `tagName: v__VERSION__` aus `tauri.conf.json` ab). → umgesetzt (siehe Archiv).
- [x] **R7-6 (Release-Prozess):** Working Tree enthält uncommittete Änderungen
      (`docs/README.md`, `docs/README-de.md`, `docs/de/flutcloud-app.md`, `docs/de/sync.md`,
      `docs/en/flutcloud-app.md`, `flutcloud-app/appinfo/info.xml` — Author-E-Mail,
      Nextcloud 28–31 → 28–37). Vor dem Tag/Release committen. → umgesetzt (siehe Archiv).
- [x] **R7-7 (Hinweis):** `release.yml` veröffentlicht als Draft (`releaseDraft: true`);
      `check_for_update` überspringt Drafts und Prereleases (`updater.rs:204-206`). Nach dem
      Build muss der Draft manuell publiziert werden, sonst erhalten Bestandskunden das
      v1-Update nicht. → als Schritt 3 im Release-Vorgang dokumentiert
      (`docs/de/development.md` + `docs/en/development.md`), siehe Archiv.

### Aktueller Stand (2026-08-16)

Alle in den Review-Läufen 2–6 (2026-08-13 bis 2026-08-15) erfassten Punkte
sind umgesetzt und im [Archiv](#archiv-erledigt) dokumentiert. Am 2026-08-16
zusätzlich gegen den Code verifiziert und als umgesetzt bestätigt: P5/U1
(Rename in Unterordnern, `rename_new_path` mit `rsplit_once` + Tests),
P7/U4 (`busyPath`-Guards, `ref<string | null>`), U2 (`account_switch` emittiert
`accounts-changed`), U5 (`sync.trigger` propagiert Fehler), U6/U7 (Confirm-
Dialog bzw. Login-Modus-Reset), F1 (`enabled` in `ADMIN_EDIT_KEYS`),
F3 (Updater räumt Teildownloads auf), F5 (`getVersion()`),
F6 (`LoadAccountsResult.dropped`), F7 (Auto-Update-Check + Update-Banner in
`App.vue`), F8 (Signing-Plan in `release.yml`, opencode auf Version gepinnt),
F9 (SHA-Warnung bei fehlendem Digest), N16 (= F5/F7), P12/N3 (`release.yml`
Prompt-Injection-Schutz), P13/N4 (`build.yml` paths-ignore ohne `.github/**`)
und Q1 (native OS-Notifications).

Im Review 2026-08-16 (Lauf 8, Fokus UX) gefundene Punkte
U-R8-1 bis U-R8-12 und R8-B1 sind umgesetzt (Details im
[Archiv](#archiv-erledigt)): U-R8-1 (Tastatur-Navigation jetzt sichtbar und mit
der Sortierung konsistent — `kbdIndex` + Sortierzustand in `EntryList.vue`),
U-R8-2 (Transfer-Leiste wird nach Einzel-Operationen geleert), U-R8-3
(Grid-Doppelklick wählt nicht mehr ab), U-R8-4 (Share-Badge in der Grid-
Kachel), U-R8-5 (Update-Banner als In-Flow-Element statt Overlay), U-R8-6
(Thumbnail-/Share-State-Prune beim Ordnerwechsel), U-R8-7 (AccountBar-Hinweis
auf M3-Tokens), U-R8-8 (Theme-FOUC behoben), U-R8-9 (Accent-Default aus
`themeDefaultHue`), U-R8-10 („Neuer Ordner" + `webdav_mkdir` validieren den
Namen), U-R8-11 (Login-Formular wird geleert), U-R8-12 (AdminPanel verlangt
einen Suchbegriff) und R8-B1 (`webdav_bulk_download` erhält die relative
Verzeichnisstruktur). R8-C1 (CI-Pin `tauri-action`) bleibt offen — Workflow-
Dateien sind von der automatisierten Aufgabe ausgenommen.
(→ erledigt seit Lauf 13, in Lauf 16 bestätigt: voller SHA in
`release.yml:193`.)

Checks: `cargo test --manifest-path src-tauri/Cargo.toml` → 78 passed /
0 failed; `cargo clippy --all-targets --manifest-path src-tauri/Cargo.toml
-- -D warnings` grün; `cargo fmt --check` grün; `npm run build` grün;
Android `./gradlew :app:assembleDebug` in `android/` grün.

Am 2026-08-16 ist zusätzlich der **Android-Client** (siehe Archiv) als neue
Komponente hinzugekommen: `android/` ist ein Kotlin/Jetpack-Compose-
Mirror des Desktop-Clients (FlutCloud-only-Policy, WebDAV/OCS, M3-Expressive-
Theme, EncryptedSharedPreferences-Token). Keine offenen Punkte mehr.

Ebenfalls am 2026-08-16 umgesetzt: der Offline-Cache (`cache.rs`) wächst nicht
mehr unbegrenzt — Maximalbestand (`MAX_CACHE_ENTRIES = 500`) mit
LRU-Aging-Eviction und Rotationstests (Details im [Archiv](#archiv-erledigt)).

Am 2026-08-16 ist die **Android-Feature-Parität (Issue #136)** umgesetzt
(siehe Archiv): Share-Liste + Widerruf in der Datei-UI, Gruppenverwaltung im
Admin-Bereich, Offline-Cache für Ordner-Listings, Registrierungs-Flow und
„Öffnen mit externer App". Zwei-Wege-Sync ist eine **bewusste
Produktentscheidung** (mobile bleibt Desktop-Feature, dokumentiert in
`android/README.md`).

Ebenfalls am 2026-08-16 umgesetzt: **Android-i18n** (AC2, siehe Archiv) —
alle UI-Texte liegen in `res/values/strings.xml` (en) + `res/values-de/`
(de) und werden über die Gerätesprache (Ressourcen-Qualifier) aufgelöst;
ViewModels emittieren Ressourcen-IDs statt englischer Fehler-/Toast-Texte.

### Review-Verlauf (alle Punkte umgesetzt — Details im Archiv)

- Review 2026-08-16 (Lauf 8, Fokus UX) — U-R8-1 bis U-R8-12, R8-B1 umgesetzt;
  R8-C1 (CI-Pin) offen.
- Review 2026-08-16 (Lauf 7, Release-Review v1) — R7-1 bis R7-6 umgesetzt,
  R7-7 als Hinweis dokumentiert.
- Review 2026-08-15 (Lauf 6, Fokus Phase 3 & 4) — Q2, Q3, Q5–Q9, P1–P17,
  U9–U11, N1–N16 umgesetzt.
- Review 2026-08-14 (Lauf 5, Fokus Browsing & Link-Sharing) — P1–P17, U9–U11,
  N1–N16 umgesetzt.
- Review 2026-08-14 (Lauf 4, Fokus Bugs & Errors) — N1–N9, U1–U7, F1–F10
  umgesetzt.
- Review 2026-08-14 (Lauf 3, Fokus UI) — U1–U7 umgesetzt.
- Review 2026-08-14 (Lauf 2, v1.0.0-Bereitschaft) — F1–F10 umgesetzt.
## Erledigt (2026-08-26, Review-Läufe 20–21 abgeschlossen — Abschnitte verschoben)

Die Abschnitte „Review 2026-08-25 (Lauf 20)“ und „Review 2026-08-25 (Lauf 21)“
wurden am 2026-08-26 (Review Lauf 22, HEAD `ecd5ee2`) nach hier verschoben.
Alle Befunde sind im SaaS-UI-Umbau umgesetzt (Commits `9855d7f`…`ecd5ee2`,
Issues #362–#368/#371–#375) und dort gegen den Code verifiziert — mit drei
Ausnahmen, die offen bleiben und in `todo.md` unter dem Lauf-22-Abschnitt
weitergeführt werden: **L20-N2**, **L20-N3** und **L21-N4** (in den unten
kopierten Original-Abschnitten entsprechend markiert). Weiterhin offen aus
diesen Läufen bleibt außerdem „Desktop-JVM: Token-Speicher härten“ sowie die
Performance-Analyse (dort wurde **U2** erledigt und ins Archiv übernommen).

### Performance-Analyse — erledigt (2026-08-26)

- [x] **U2 (Grid): Hover-Overlay erzwingt GPU-Compositing** —
      `EntryList.vue:271`, `hidden`→`flex` pro Item. Fix: Shared Overlay
      via CSS `:has()` oder ein Element.
      → Umgesetzt in #363: das Overlay bleibt montiert und faded nur über
      `opacity`/`group-hover` (`EntryList.vue:270-277`, Kommentar
      „U2/#363“); kein Display-Flip pro Item mehr.

---

## Review 2026-08-25 (Lauf 21, Fokus Desktop-UI-Overhaul „Material → Modern SaaS“ — neue Befunde)

Gegenstand: Bewertung der Desktop-UI (`src/`) gegen das Ziel des UI-Overhauls
— weg von Material (M3-Tokens, `@material/web`-Komponenten, Ripple) hin zu
einem modernen SaaS-Look: clean, weniger verschnörkelt, klarer strukturiert.
Plus die Standard-Bereiche (IPC-Commands, WebDAV/OCS-Anbindung, Keyring,
Fehler-/State-Management, CI) und die Nachprüfung aller offenen L20-Befunde.

Ausgangslage für den Fokus: Der Umbau hat noch nicht begonnen — die UI ist
vollständig Material-basiert: `@material/web` als Runtime-Dependency
(`package.json:14`), ~30 Komponenten-Importe in 9 SFCs (`App.vue`,
`FileExplorer.vue`, `AdminPanel.vue`, `SettingsModal.vue`, `SyncPanel.vue`,
`ToastStack.vue`, `WelcomeScreen.vue`, `LoginModal.vue`, `GuestBrowser.vue`),
komplettes M3-Token-System in `style.css` (`--m3-*`/`--md-sys-*`, State
Layers, Elevation, Shape „mirrors Android Shape.kt“), globaler Ripple
(`main.ts:16` + `lib/ripple.ts`) und der `md-`-Custom-Element-Carve-out in
`vite.config.ts:13-15`. Die Befunde unten sind Bugs/Inkonsistenzen, die in
den Umbau übernommen werden würden, bzw. konkrete Hebel dafür.

Neu gefunden:

- [x] **L21-F1 (Bug, mittel): Password-Ein-/Ausblenden im Login- und
      Register-Dialog ist toter Code — der Button wechselt sein Label, das
      Feld bleibt immer maskiert.** In `LoginModal.vue` togglen die
      `md-text-button`s `showPassword = !showPassword`
      (`LoginModal.vue:229-231`, Register `:271-273`, Admin-Passwort
      `:304-306`), aber alle drei Passwort-Felder behalten das **statische**
      `type="password"` (`:220-227`, `:262-269`, `:296-303`) — nichts liest
      `showPassword`/`showAdminPassword` (`LoginModal.vue:70-71`) zur
      Feldtyp-Änderung. `AdminPanel.vue:543` zeigt die korrekte Bindung
      (`:type="showPassword ? 'text' : 'password'"`). Fix: dasselbe
      dynamische `:type`-Binding im LoginModal.
- [x] **L21-F2 (Bug, mittel): Grid-Hover-Overlay hat keinen Hintergrund —
      `bg-scrim/50` kompiliert zu nichts.** `EntryList.vue:271` legt das
      Hover-Overlay der Grid-Karten auf `bg-scrim/50`; `style.css` definiert
      aber nur das Material-Web-Token `--md-sys-color-scrim`
      (`style.css:273`) und **kein** Tailwind-Token `--color-scrim` — die
      Utility existiert daher nicht im Build (verifiziert in
      `dist/assets/*.css`: einziges „scrim“-Vorkommen ist das md-Sys-Token).
      Folge: Die sechs Hover-Aktionsbuttons (open/download/link/share/
      rename/delete, `EntryList.vue:275-317`) schweben ohne Abdunkelung
      direkt über Thumbnail und Dateiname — Kontrastproblem, besonders im
      Bright-Daylight-Theme. Fix: `--color-scrim` als `@theme`-Token
      definieren (oder `bg-black/50` nutzen) — beim SaaS-Restyling ohnehin
      obsolet, bis dahin echter visueller Bug.
- [x] **L21-F3 (Bug, minor): Die Cancel-Buttons im Login-/Register-Formular
      sind implizit Submit-Buttons — der Klick erzeugt einen Phantom-Submit,
      dessen Fehlermeldung beim nächsten Öffnen sichtbar ist.**
      `@material/web`-Buttons sind form-assoziierte Submitter mit Default
      `type="submit"` (`node_modules/@material/web/labs/behaviors/
      form-submitter.js`: `this.type = 'submit'`, Click → `form.requestSubmit()`
      sofern nicht defaultPrevented). Die Cancel-Buttons in `LoginModal.vue`
      (`:240` und `:316`) tragen kein `type="button"` — anders als die
      FileExplorer-Dialoge seit L15-F1 (`FileExplorer.vue:1332`, `:1363`) —
      und `close()` ruft nicht `preventDefault()`. Ablauf beim Klick:
      `close()` resettet Formular + schließt (`resetForm` + `emit("close")`),
      danach feuert der implizite Submit → `submit()`/`submitRegister()`
      läuft mit leeren Feldern und setzt `formError = t("requiredFields")`
      (`:125-128`/`:152-155`) — **nach** dem `resetForm()` von `close()`.
      Da `resetForm` nur an den close/done-Klickpfaden hängt, steht beim
      nächsten Öffnen des Dialogs der „Bitte Benutzername und Passwort
      ausfüllen.“-Kasten sofort drin. Fix: `type="button"` auf beiden
      Cancel-Buttons (die Primär-Buttons `:243`/`:319` feuern ebenfalls
      doppelt, sind aber über den `submitting`-Guard abgesichert).
- [x] **L21-F4 (UX-Konsistenz, minor): Der globale Escape-Stack deckt nicht
      alle Overlays ab — Kontomenü (App.vue) und GuestBrowser-Dialoge
      reagieren nicht auf Escape.** `lib/escape.ts` wird von
      `FileExplorer.vue:655-660`, `LoginModal.vue:103-113` und
      `SettingsModal.vue:221-231` genutzt; fehlen tun: das Konto-Menü in
      `App.vue` (`accountMenu`, `:38`, Dropdown `:328-378` — nur
      Außenklick-Backdrop `:378`) sowie in `GuestBrowser.vue` der
      Kategorie-Dialog (`showCategoryDialog`, `:37`, Markup `:455-487`,
      nur `@click.self`) und das Kategorie-Zuweisungs-Dropdown
      (`assigningToken`, `:42`, Markup `:352-375`) — letzteres hat nicht
      einmal einen Außenklick-Closer und bleibt offen, bis man erneut auf
      den Edit-Button klickt. Fix: `registerEscapeCloser` auch dort +
      Außenklick fürs Zuweisungs-Dropdown.
- [x] **L21-N1 (Design-Konsistenz, minor): Zwei Checkbox-Sprachen nebeneinander
      — Material-`md-checkbox` vs. natives `<input>`.** `md-checkbox`:
      Select-all-Bar `FileExplorer.vue:1073`, publicUpload `:1464`,
      followSymlinks `SyncPanel.vue:108`. Nativ
      (`class="accent-primary"`): Zeilen-Checkbox `EntryList.vue:119-124`,
      Grid-Karten `:235-242`, prefixless-Checkbox `GuestBrowser.vue:471-475`.
      Im Datei-Tab sehen Toolbar-Checkbox und Zeilen-Checkbox unterschiedlich
      aus. Für „clean & structured“ gehört genau eine Kontrolle ins System
      (SaaS-typisch: nativ + Tailwind-Restyle) — im Umbau entscheiden und
      vereinheitlichen.
- [x] **L21-N2 (Semantik/A11y/Fragilität, minor): Die Haupt-Navigation ist
      ein md-filled/md-outlined-Button-Paar mit Token-Chirurgie statt einer
      echten Tab-Leiste; die md-tabs in Login/Settings desynchronisieren bei
      Tastaturnavigation.** `App.vue:254-306` rendert je Tab
      `md-filled-button` (aktiv — via `.nav-tab-active`,
      `style.css:439-445`, das Container-Farbe/-Shape des filled-Buttons auf
      transparent/0 drückt und nur einen Unterstrich lässt) bzw.
      `md-outlined-button` (inaktiv): 12 Template-Elemente statt eines
      Loops, keine `role="tablist"`/`aria-selected`-Semantik, und der
      Aktiv-Zustand hängt am Überschreiben interner MD-Tokens. Zusätzlich
      binden `LoginModal.vue:196-199` und `SettingsModal.vue:254-258` ihre
      `md-tabs` nur über `@click` an `mode`/`tab` — Pfeiltasten-Navigation
      im `md-tabs` ändert den aktiven Tab, ohne das Ref zu setzen (markierter
      Tab und gezeigtes Formular laufen auseinander). Fix im Umbau: native
      Tab-Leiste mit Unterstrich-Indikator (oder `change`-Event der md-tabs
      hören).
- [x] **L21-N3 (Feature-Idee, minor): Ansicht- und Sortierpräferenzen werden
      nicht persistiert — jede Sitzung startet in Liste/A–Z.**
      `FileExplorer.vue:33` (`viewMode`), `:53-54` (`sortKey`/`sortAsc`)
      sind lokale refs: zurückgesetzt bei jedem App-Start **und** jedem
      Tab-Wechsel (das `v-if` in `App.vue:384-386` zerstört die Komponente).
      `stores/ui.ts` persistiert lang/theme/accentHue/guestMode bereits per
      localStorage — Layout-Präferenzen gehören für ein modernes
      SaaS-File-Browsing dorthin.
- [ ] **L21-N4 (Struktur, mittel) — WEITER OFFEN, Führung jetzt unter Review 2026-08-26/Lauf 22 in todo.md:** `FileExplorer.vue` ist ein
      1480-Zeilen-Monolith — vor einem sauberen Restyling zerlegen.**
      In einer Datei: Toolbar/Breadcrumbs/Upload-Zeile (`:875-965`),
      Admin-Impersonation-Bar (`:967-1016`), vier Hinweis-Banner
      (`:1018-1052`), Fehlerbanner (`:1054-1056`), Search-Bar
      (`:1058-1069`), Select-all-Bar (`:1071-1091`), Transfer-Progress
      (`:1093-1108`), vier Empty-States (`:1110-1130`), Split-View mit
      2×`EntryList` (`:1132-1218`), Listen-/Grid-Instanzen (`:1220-1272`),
      Kontextmenü (`:1274-1312`) und New-Folder/Rename/Share-Dialoge
      (`:1314-1477`) plus ~850 Zeilen Script-Logik (Suche, Bulk, Shares,
      Thumbs, Keyboard, Drag&Drop, Escape-Verkabelung). Kandidaten:
      `FilesToolbar.vue`, `ImpersonationBar.vue`, `ShareDialog.vue`,
      `Rename/NewFolderDialog.vue`, `ContextMenu.vue`. Derselbe Befund für
      `AdminPanel.vue` (666 Zeilen: Suchliste + Detailformular + Quota in
      einer Komponente).
- [x] **L21-N5 (Bundle/Perf, minor): `@material/web` stellt aktuell grob die
      Hälfte des JS-Payloads — der SaaS-Umbau senkt die Bundlegröße
      automatisch.** `dist/assets` umfasst ~540 kB roh; größter Einzel-Chunk
      ist `redispatch-event` (**156 kB**) — ausschließlich
      form-assoziations-Interne von @material/web —, dazu
      `select-option` (65 kB), `outlined-text-field` (59 kB),
      `primary-tab`/`static-html` etc. Ein Ersatz der `md-*`-Controls durch
      native Elemente + Tailwind würde diese Chunks streichen und zugleich
      `typescaleStyles`-Adoption (`main.ts:10-14`), `initRipple`
      (`main.ts:16`, `lib/ripple.ts`), den `--md-sys-*`-Tokenblock
      (`style.css:241-290`), die Dependency `@material/web`
      (`package.json:14`) und den `isCustomElement`-Carve-out
      (`vite.config.ts:13-15`) überflüssig machen.
- [x] **L21-N6 (Copy/i18n, minor): Der Akzentfarben-Hinweis bewirbt
      ausdrücklich „Material-You“.** `accentColorHint` nennt „A
      Material-You-style accent“ (`i18n.ts:89-90`) bzw. „Ein Akzent im
      Material-You-Stil“ (`:420-421`) — wenn das Designsystem Richtung
      Modern SaaS geht, muss dieser Text (und die Entscheidung, ob die
      Hue-Seed-Palette als Feature bleibt) Teil des Umbaus sein.

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs:241-294` `generate_handler!` deckungsgleich mit `src/lib/ipc.ts`,
inkl. aller `guest_*`-Wrapper, alle typisiert), Keyring (`accounts.rs`
save/load/delete + Linux-Hint-Mapping + Quarantäne kaputter `accounts.json`
über `persist.rs`), Fehler-Serialisierung und i18n-Mapping (`error.rs::code()`
↔ `ERROR_CODE_KEYS` `i18n.ts:687-706` vollständig inkl.
`walk_incomplete`/`account_missing`, `updateStatusText`-Map), Offline-Cache
(`cache.rs` atomic write + Quarantäne), WebDAV-Layer (`webdav.rs`:
Impersonation-Namespace-Guards, Chunked-v2 mit Session-Cleanup, temp+rename,
TOCTOU/If-Match), OCS-Layer (`ocs.rs`: `verify_share_owner`,
Dedup-Pagination mit Progress-Guard, einzelne Form-Encoding-Kette),
Guest-Backend (`guest.rs`: Token/Pfad-Validierung, anonymous Probes),
Tray/CLI (`lib.rs` Tray-Rebuild, Close-to-Tray, CLI-Flags).

Verifikation frisch ausgeführt: Tauri-Linux-Systemdeps waren
nachzuinstallieren; danach `cargo fmt --all --check` grün;
`cargo clippy --all-targets -- -D warnings` grün;
`cargo test --manifest-path src-tauri/Cargo.toml` → **108 passed /
0 failed**; `npm run build` (vue-tsc + vite) grün (Index-Chunk ~119 kB,
Code-Splitting wirksam). Die F2/F3-Behauptungen sind gegen den gebauten
Output bzw. die installierte `@material/web`-Quelle verifiziert, nicht nur
gegen den Quelltext.

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `23eda5a`)

Seit Lauf 20 sind nur die Report-Commits gelandet (`6bec8de`, Merge
`23eda5a` = PR #360) — kein Anwendungscode-Change. **Alle offenen Einträge
wurden gegen den aktuellen Code nachgeprüft und sind weiterhin offen** —
nichts ist erledigt, also gibt es dieses Mal nichts nach
`archived-todo.md` zu verschieben:

- L20-F1 bestätigt: `GuestBrowser` nur im Logged-out-Zweig
  (`App.vue:395-424`), Watcher beendet Gastmodus bei Anmeldung
  (`App.vue:192-199`), Admin-Controls hängen an `accounts.active`
  (`GuestBrowser.vue:21`). ✓ offen
- L20-F2 bestätigt: `lockedPaths` wird nur von `toggleLock` befüllt
  (`GuestBrowser.vue:226-244`); kein List-Locks-Endpunkt in `commands.rs`
  (`guest_admin_lock_path`/`unlock_path` geben Locks nur als Änderungs-
  antwort zurück, `commands.rs:1396-1419`) noch in `ipc.ts`. ✓ offen
- L20-F3 bestätigt: `deleteCategory` fragt nicht
  (`GuestBrowser.vue:161-170`, Chip-„×“ `:307-315`). ✓ offen
- L20-N1 bestätigt: `enter()` setzt Share + ruft `navigateTo("/")`
  (`GuestBrowser.vue:90-93`), das bei gesetztem `busyPath` abbricht
  (`:96`), ohne `entries.value = []`. ✓ offen
- L20-N2 bestätigt: `verify_guest_server` mappt fehlendes Feature auf
  `AppError::FlutCloudAppMissing` (`guest.rs:130-135`). ✓ offen
- L20-N3 bestätigt: PowerShell-Parse-Check filtert weiterhin nur
  `install-flutcloud-app.ps1` (`flutcloud.yml:94`). ✓ offen
- L20-N4 bestätigt: `Share` in `ipc.ts:44-54` führt `uidOwner` weiterhin
  nicht (Rust: `state.rs:130-133`). ✓ offen
- „Desktop-JVM: Token-Speicher härten“ bleibt offen:
  `FileKeyValueStorage.kt:13-14` dokumentiert die Keyring-Anbindung weiter
  als Follow-up („not encrypted at rest“). ✓ offen
- Performance-Analyse unverändert vorhanden und bestätigt: R1
  (sequenzielles BFS, `sync.rs` Remote-Walk-Loop), R2 (`plan_ops`
  Union-BTreeSet), R3 (`evict_oldest` Vollscan mit `read_dir`+Sort pro
  Write, `cache.rs:57-82`), N1+F2 (`loadAllShares` pro Navigation ohne
  Pfadfilter, `FileExplorer.vue:784`), F1 (Doppelsortierung
  `FileExplorer.vue:56-58` + `EntryList.vue:53-55`), U2 (Hover-Overlay
  `EntryList.vue:271`), U3 (`formatMtime` pro Entry, `EntryList.vue:47-51`),
  N2 (Thumbnail-Requests ohne Concurrency-Limiter,
  `FileExplorer.vue:303-312`), U5 (`<thead>` ohne `v-once`,
  `EntryList.vue:85-105`). ✓ offen

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe sind in diesem Lauf
verboten): `git log f9b7351..HEAD` zeigt ausschließlich den Review-20-
Bericht (`6bec8de`, PR #360) — keine Issue-Fixes seit dem letzten Lauf.
Der `opencode-todo-issues`-Workflow sollte beim nächsten Lauf die offenen
L20-Befunde (weiterhin unverrichtet) **und** die neuen L21-Befunde oben
als Issues erfassen; die L17-/L19-/CP-Punkte sind bereits alle umgesetzt
(s. Archiv). Ob parallel offene Issues entstanden sind oder veralten, ist
hier nicht prüfbar.

## Review 2026-08-25 (Lauf 20, Fokus Guest-Mode + Desktop-Basis — neue Befunde)

Gegenstand: der seit Lauf 19 neu hinzugekommene Code — Gast-Zugriff auf
vollständig öffentliche Shares (`src-tauri/src/guest.rs`, `persist.rs` neu,
Guest-/Guest-Admin-Commands in `commands.rs`, Registrierung in `lib.rs`,
`GuestBrowser.vue`, `ui.ts` guestMode) — plus die Standard-Bereiche
(IPC-Commands, WebDAV/OCS-Anbindung, Keyring, Fehler-/State-Management,
Frontend `src/`, CI/Workflows unter `.github/`) und die Nachprüfung der
offenen L17-/L19-/CP-/Perf-Befunde gegen HEAD (`f9b7351`).

Neu gefunden:

- [x] **L20-F1 (Bug/Feature-Lücke, mittel): Die gesamte Gast-Admin-
      Verwaltung ist im Desktop-Client unerreichbar — `GuestBrowser`
      rendert nur ohne aktives Konto, ihre Admin-Controls benötigen aber
      genau eines.** `App.vue:395-424` zeigt `GuestBrowser` ausschließlich
      im `v-else`-Zweig von `v-if="accounts.active"` (also ausgeloggt), und
      der Watcher `App.vue:192-199` beendet den Gastmodus sofort bei jeder
      Anmeldung (`ui.setGuestMode(false)`). Alle Admin-Controls in
      `GuestBrowser.vue` hängen dagegen an `isAdmin = !!accounts.active?.isAdmin`
      (`GuestBrowser.vue:21`): Kategorie anlegen/löschen (:259, :307-315),
      Share-Zuweisung (:345-375) und Ordner-Sperren (:431-439) sind damit
      im Desktop-UI toter Code — inklusive der dahinterliegenden Commands
      `guest_admin_set_category`/`guest_admin_delete_category`/
      `guest_admin_assign_category`/`guest_admin_unassign_category`/
      `guest_admin_lock_path`/`guest_admin_unlock_path`
      (`commands.rs:1345-1419`, registriert in `lib.rs:270-275`). Nur der
      KMP-Client hat einen erreichbaren Pfad (eigene Admin-Gast-UI).
      Fix: die Gast-Admin-Verwaltung im angemeldeten Zustand anbieten
      (z. B. Sektion im Admin-Tab) oder Gastmodus neben aktivem Konto
      zulassen.
- [x] **L20-F2 (Bug, mittel): Der Sperrzustand wird nie geladen — bereits
      serverseitig gesperrte Gast-Ordner erscheinen immer entsperrt.**
      `lockedPaths` (`GuestBrowser.vue:45-47`) wird ausschließlich durch
      die Antworten von `toggleLock` befüllt (`GuestBrowser.vue:226-244`);
      beim Betreten eines Shares wird nichts nachgeladen, und weder
      Backend noch `ipc.ts` bieten überhaupt einen „Locks auflisten“-Endpunkt
      an (`lock_path`/`unlock_path` liefern die Liste nur als Antwort auf
      eine Änderung, `commands.rs:1396-1419`). Folge: Das „Locked“-Badge
      (`GuestBrowser.vue:422`) taucht für bestehende Sperren nie auf,
      `isLocked()` (`:215-224`) meldet fälschlich „entsperrt“, und ein
      Admin, der auf einen bereits gesperrten Ordner klickt, sendet erneut
      LOCK statt UNLOCK. Fix: Command zum Auslesen der Locks ergänzen und
      beim Share-Betreten laden.
- [x] **L20-F3 (UX-Konsistenz, minor): `deleteCategory` löscht ohne
      Bestätigungsdialog.** Das kleine „×" auf dem Kategorie-Chip
      (`GuestBrowser.vue:307-315`) ruft direkt
      `api.guestAdminDeleteCategory(name)` auf (`:161-170`) — inkonsistent
      zum etablierten Confirm-Muster (Konto F7, Datei `deleteConfirm`,
      Sync-Ordner L19-F3); serverseitig fällt die Zuweisung aller Shares
      dieser Kategorie weg. Fix: `window.confirm` wie in `SyncPanel.remove`.
- [x] **L20-N1 (Race/UX, minor): `enter()` während einer laufenden
      Aktion zeigt die Einträge des vorherigen Share unter neuem Namen.**
      `enter` (`GuestBrowser.vue:90-93`) setzt `share.value` und ruft
      `navigateTo("/")` auf — das bricht bei gesetztem `busyPath` stillschweigend
      ab (`:96`), ohne die Liste zu leeren. Klickt man während eines
      Downloads/Ladevorgangs auf eine andere Share-Karte (die Karten sind
      nicht disabled), bleibt die alte Dateiliste unter dem neuen Share-Titel
      stehen. Fix: in `enter()` `entries.value = []` setzen oder `enter` an
      `busyPath` koppeln.
- [ ] **L20-N2 (Fehlermeldung, minor) — WEITER OFFEN, Führung jetzt unter Review 2026-08-26/Lauf 22 in todo.md:** `verify_guest_server` meldet „App
      nicht installiert oder deaktiviert“, wenn die App nur zu alt für den
      Gast-Feature-Flag ist.** `guest.rs:130-135` mappt das fehlende
      `complete-public-shares`-Feature auf `AppError::FlutCloudAppMissing`,
      dessen Text (`error.rs:125-132`) einen Installationsfehler behauptet —
      obwohl `verify_server` kurz zuvor die App erfolgreich erkannt hat.
      Fix: eigener Fehlercode/-text („FlutCloud-App zu alt für Gastzugriff“).
- [ ] **L20-N3 (CI, minor) — WEITER OFFEN, Führung jetzt unter Review 2026-08-26/Lauf 22 in todo.md:** Der PowerShell-Parse-Check prüft nur
      `install-flutcloud-app.ps1` — der README-One-Liner-Einstiegspunkt
      `scripts/install-flutlink.ps1` bleibt ungeprüft.**
      `.github/workflows/flutcloud.yml:92` enumeriert
      `Get-ChildItem scripts -Filter 'install-flutcloud-app.ps1'`; die
      Bash-Seite wurde mit L17-N2 auf alle `.sh` ausgeweitet, die
      PowerShell-Seite nicht auf alle `.ps1`. Fix: `-Filter '*.ps1'`.
- [x] **L20-N4 (Typing, minor): `Share` in `ipc.ts` kennt `uidOwner`
      nicht.** Das Rust-Modell serialisiert `uid_owner` → `uidOwner`
      (`state.rs:130-133`, Guard-Feld für Impersonation), die TS-Schnittstelle
      (`ipc.ts:44-54`) führt es nicht — das KMP-Pendant (`Share.uidOwner`)
      hat es. Aktuell folgenlos (Backend guardet selbst), aber jede
      künftige Frontend-Nutzung würde still `undefined` lesen. Fix: Feld
      `uidOwner?: string | null` ergänzen.

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs:241-294` deckungsgleich mit `src/lib/ipc.ts`, inkl. aller
`guest_*`-Wrapper, alle typisiert), Keyring (`accounts.rs` save/load/delete +
Linux-Hint + Quarantäne/Atomic über `persist.rs`), Fehler-Serialisierung
(`error.rs` code/message/detail), Offline-Cache (`cache.rs` atomic_write +
LRU-Eviction + Miss bei kaputtem File), WebDAV-Layer (`webdav.rs`:
Impersonation-Guards, Chunked-v2 inkl. Session-Cleanup, temp+rename,
TOCTOU/If-Match), OCS-Layer (`ocs.rs`: Share-Owner-Verifizierung,
Dedup-Pagination mit Progress-Guard, einzelne Form-Encoding-Kette),
Sync-Engine (`sync.rs`: fail-closed, dirty-dirs, Journal-Quarantäne,
atomic persist), FlutCloud-only-Policy (`flutcloud.rs` URL-Lock +
Capability-Probe), Updater (`updater.rs` seit Lauf 19 unverändert),
Tray/CLI (`lib.rs`) sowie Theme/i18n-Grundlagen (`ui.ts`-Persistenz inkl.
guestMode, `i18n.ts`: alle neuen `guest*`-Keys en+de vorhanden,
`escape.ts`-Stack korrekt).

Verifikation frisch ausgeführt (HEAD `f9b7351`): Tauri-Linux-Systemdeps
waren nachzuinstallieren; danach `cargo fmt --all --check` grün;
`cargo clippy --all-targets -- -D warnings` grün;
`cargo test --manifest-path src-tauri/Cargo.toml` → **108 passed /
0 failed** (+5 gegenüber Lauf 19: persist/guest-Tests);
`npm run build` (vue-tsc + vite) grün (Haupt-Chunk ~119 kB, Code-Splitting
weiterhin wirksam, `GuestBrowser` eigener Chunk).

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `f9b7351`)

Seit Lauf 19 sind 14 Commits hinzugekommen. **Alle** offenen Befunde der
Läufe 17–19 sind im Code umgesetzt und wurden hier gegen den aktuellen Stand
verifiziert — die drei zugehörigen Review-Abschnitte sind komplett nach
`archived-todo.md` verschoben. Stichproben der Verifikation:

- L17-F1: Confirm jetzt **vor** `busyPath` (`FileExplorer.vue:135-141`). ✓
- L17-F2: `validate_dav_path` (lesen) vs. `validate_writable_dav_path`
  (schreiben) getrennt (`commands.rs:595-622`, Tests :1699-1731). ✓
- L17-F3: `persist.rs` (atomic_write + quarantine) von `accounts.rs:69-74`,
  `accounts.rs:104-110` und `sync.rs:1258-1261`/`:1318-1321` genutzt. ✓
- L17-F4: `errWalkIncomplete` en+de + Mapping (`i18n.ts:303/:634/:705`). ✓
- L17-N1/N2/N3/N4: gemeinsamer setup-android-Pin (`kmp.yml:129` =
  `action.yml:30`, v4.0.1); ShellCheck über alle `.sh`
  (`flutcloud.yml:79-87`); Login-Validierung (`LoginModal.vue:122`);
  `list_groups` bricht an Rohseitenlänge ab (`ocs.rs:309-327`). ✓
- L19-F1: alle vier `EntryList`-Instanzen binden
  `@download/@delete/@share` + `:thumbs/:shares-by-path/:searching`
  (`FileExplorer.vue:1150-1270`). ✓
- L19-F2: Bulk-Delete validiert vorab (`commands.rs:927-932`). ✓
- L19-F3/F4/F5/F6/F7/N1: Confirm in `SyncPanel.vue:64-67`; Rename-Dialog
  bleibt bei invalidem Namen offen (`FileExplorer.vue:479-484`);
  `nameInput`-Reset (`:426-441`); `Number.isFinite`-Guard
  (`AdminPanel.vue:282-284`); Banner nutzt `updateStatusText`
  (`App.vue:170-177`); globaler Escape-Stack (`lib/escape.ts`). ✓
- CP-F1/F2/F3/N1–N5 (KMP-Parität): Share-Impersonation + Owner-Guard
  (`FlutCloudOcs.kt:155-171`), Rename-Validierung
  (`FilesViewModel.kt:331-339`), Chunked-Upload v2
  (`WebDavApi.kt:281-331`), Gruppen-Pagination (`FlutCloudOcs.kt:119`),
  Account-Filter/tokenMissing (`SessionManager.kt`), Thumbnails/ZIP/Bulk
  (`WebDavApi.kt:434-498`), Quota-Cache (`FilesViewModel.kt:190-199`),
  Namespace-Guard nur für Schreibzugriffe (`WebDavApi.kt:52-77`). ✓

Weiter offen (unverändert, bestätigt):

- [ ] „Desktop-JVM: Token-Speicher härten“ —
  `FileKeyValueStorage.kt:8-15` dokumentiert die Keyring-Anbindung
  weiterhin als Follow-up.
- [ ] Performance-Analyse unten: R1 (sequenzielles BFS,
  `sync.rs:397-441`), R2 (Union-BTreeSet, `sync.rs:563-566`), R3
  (`evict_oldest` Vollscan, `cache.rs:57-82`), N1+F2 (`loadAllShares`
  pro Navigation, `FileExplorer.vue:784/:812`), F1 (Doppelt-Sortierung,
  `EntryList.vue:53-54`), U2 (Hover-Overlay `:271`), U3 (`formatMtime`
  pro Entry, `EntryList.vue:47-51`), N2 (Thumbnail-Requests ohne
  Concurrency-Limiter, `FileExplorer.vue:303-312`), U5 (`<thead>` ohne
  `v-once`) — allesamt im aktuellen Code unverändert vorhanden.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe sind in diesem Lauf
verboten): `git log dd7fdb7..HEAD` zeigt 14 Commits — den Guest-Mode-Umbau
(`acf45a1`, `86aa931`/PR #357 zu Issue #355), FlutCloud-App-Arbeiten
(öffentliche Share-Seiten, Kategorien/Sperren: `efd519c`, `3083335`,
`8755800`, `2600aa2`, `20c2711`, `58e268a`, `aa70bbb`), Deploy-Skript
(`11a1b07`), Performance-Analyse-Doku (`06117f6`) und den Android-Update-
Fix `f9b7351` (PR #359, entspricht dem Archiv-Eintrag „Update fails on
android"). Ob parallel offene Issues entstanden sind oder veralten, ist
hier nicht prüfbar — der `opencode-todo-issues`-Workflow sollte beim
nächsten Lauf die L20-Befunde oben als Issues erfassen; die L17-/L19-/CP-
Punkte sind inzwischen alle umgesetzt und dürfen aus den bestehenden
Issues entfernt werden.



## Erledigt (2026-08-28, Lauf 27 — KMP-F15: Theme-/Komponenten-Entscheidung)

- [x] **KMP-F15 (Bereinigung, minor — im Zuge des Reverts gleich mit):
      Entscheidung dokumentiert + tote Komponenten entfernt.** Die
      Entscheidung steht in `kmp/README.md` „Theme-Entscheidung (KMP-F15)":
      `Material3ExpressiveTheme` wird der neue Wrapper in `FlutLinkTheme`
      (`Theme.kt:46-80`) mit Brand-`colorScheme`/`typography`/`shapes` +
      `motionScheme = MotionScheme.expressive()` (sobald KMP-F13 die
      Material3-Pre-Release geliefert hat); entfallen beim Revert
      (`FlutBadge`, `FlutPill`, `FlutSegmentedControl`, `FlutCard`,
      `FlutGhostButton`, `FlutOutlineButton`, `FlutPrimaryButton`,
      `FlutIconButton`) samt M3-Mapping, bleiben die Utility-Komponenten
      (`ErrorBanner`/`EmptyState`/`QuotaBar`/`FileMetaLine`/`ScrollableColumn`/
      `SectionHeader`/`Breadcrumb`/`SectionLabel`/`fileIcon`).
      `dynamicColor = true` bleibt Default; `Color.kt` (Brand-Paletten) und
      der Akzent-Hue-Slider (`SettingsScreen.kt:200-226`) bleiben
      unangetastet. Die bereits ungenutzten `FlutCard`/`FlutIconButton` sind
      aus `Components.kt` entfernt. Der mechanische Swap selbst ist Aufgabe
      von KMP-F14.

## Erledigt (2026-08-28, Lauf 26 — L24-F1: Sync-Log-fmt/clippy CI-Blocker)

- [x] **L24-F1 (Bug, hoch / CI-Blocker): `cargo clippy` + `cargo fmt` sind
      auf HEAD nicht grün.** `4c2f25e` vereinfacht die redundante Closure in
      `sync.rs` (`sync.rs:1339` → `.map_err(AppError::Json)`) und formatiert
      die beiden `Move*.Conflict`-Match-Arme (`sync.rs:1173-1184`) neu;
      `cargo fmt --check` ist auf HEAD `0585c2b` grün (verifiziert). Der
      zugehörige Teil „Sync-Log-Command nicht registriert" (Lauf-25-F3a) ist
      ebenfalls behoben (`sync_log_list`/`sync_log_clear` in `lib.rs` + UI).
      Hinweis: `cargo clippy` konnte bei Lauf 26 umgebungsbedingt (fehlendes
      `gobject-2.0`) nicht erneut laufen; die gemeldete `redundant_closure`
      ist aber entfernt. Offen bleibt der Sync-Log-Append-/Trunkierungs-Bug
      (in `todo.md`, L24-F3b).

## Erledigt (2026-08-28, KMP-F13 — Material3-Dependency auf Expressive-Support)

- [x] **KMP-F13 (Blocker/Feasibility, hoch): `Material3Expressive` war in der
      aktuell genutzten Material3-Abhängigkeit nicht verfügbar.**
      `kmp/shared/build.gradle.kts` nutzte ausschließlich das Plugin-DSL-
      `api(compose.material3)`, das in Compose Multiplatform 1.11.1 auf die
      stabile Material3-Version (`org.jetbrains.compose.material3:material3:1.9.0`,
      Jetpack M3 1.4.0-stable) auflöst — in der die Expressive-API entfernt ist.
      Behoben: `composeMaterial3 = "1.9.0-alpha04"` in
      `kmp/gradle/libs.versions.toml` (mit Erklär-Kommentar zur
      Plugin-Entkopplung) verankert und `kmp/shared/build.gradle.kts` auf
      `api(libs.compose.material3)` umgestellt. Verifiziert mit
      `cd kmp && ./gradlew :shared:build` (Android + JVM grün; iOS-Targets nur
      auf macOS kompilierbar). Ein `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
      ist an keiner Stelle nötig, da der aktuelle UI-Code keine Expressive-API
      nutzt — es wird beim UI-Revert (KMP-F14) an den neuen
      `Material3ExpressiveTheme`-/`MotionScheme`-Nutzungsstellen ergänzt.
