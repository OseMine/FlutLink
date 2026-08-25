# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.

## Review 2026-08-24 (Lauf 19, Fokus Desktop UI — neue Befunde)

Gegenstand: Desktop-UI (`src/`: `App.vue`, `FileExplorer.vue`, `EntryList.vue`,
`AccountBar.vue`, `AdminPanel.vue`, `SyncPanel.vue`, `LoginModal.vue`,
`SettingsModal.vue`, `WelcomeScreen.vue`, `ToastStack.vue`, Pinia-Stores,
`lib/ipc.ts`, `lib/i18n.ts`, `lib/sort.ts`, `lib/format.ts`, `lib/ripple.ts`)
plus die Standard-Bereiche (IPC-Commands, WebDAV/OCS, Keyring,
Fehler-/State-Management, CI) und die Nachprüfung der offenen
L17-/L18-/CP-Befunde gegen HEAD (`dd7fdb7`).

Neu gefunden:

- [ ] **L19-F1 (Bug, mittel): Split-View + Grid-Ansicht: tote Hover-Buttons —
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
- [ ] **L19-F2 (Robustheit, mittel): `webdav_bulk_delete` validiert die Pfade
      erst innerhalb der Lösch-Schleife — ein geschützter Pfad mitten in der
      Auswahl führt zu einem teilweisen Bulk-Delete.** `commands.rs:914-919`
      ruft `validate_dav_path(path)?` erst pro Iteration; Pfade vor dem ersten
      ungültigen sind bereits gelöscht, wenn der Guard abbricht. Der
      Geschwister-Command `webdav_bulk_download` validiert dagegen alle
      Targets vorab (`commands.rs:1006-1008`). Fix: denselben
      Pre-Validierungsloop übernehmen (oder `validate_dav_path` für alle
      `paths` vor der Schleife ausführen).
- [ ] **L19-F3 (Bug/UX, mittel): Sync-Ordner werden ohne Bestätigungsdialog
      entfernt — der Klick löscht den Ordner **und** sein Sync-Journal
      unwiderruflich.** `SyncPanel.vue:64-71` (`remove`) ruft direkt
      `sync.remove(folderId)`; backendseitig wirft
      `SyncEngine::remove_folder` (`sync.rs:1364-1375`) Ordner, Status und
      Journal-Datei weg. Ein Versehen bedeutet: Sync stoppt, Journal weg —
      beim erneuten Hinzufügen läuft alles als Erst-Sync (Konfliktkopien-
      Risiko). Inkonsistent zum F7-Muster (Konto-Entfernung, Datei-Löschen,
      Share-Widerruf fragen jeweils per `window.confirm`). Fix: Bestätigung
      wie `deleteSelectedConfirm` ergänzen.
- [ ] **L19-F4 (UX-Konsistenz, minor): Der Rename-Dialog schließt auch bei
      invalidem Namen — die getippte Eingabe geht verloren.**
      `doRename` (`FileExplorer.vue:438-469`) zeigt zwar den Toast
      „Ordnername ungültig", aber das `finally` setzt `renameTarget = null`
      und schließt damit den Dialog; `createFolder` (`:413-431`) hält den
      Dialog bei demselben Fehler offen. Fix: Validierung vor dem
      Dialog-Close handhaben (Close nur bei Erfolg).
- [ ] **L19-F5 (Bug, minor): `nameInput` wird zwischen Neu-Ordner- und
      Rename-Dialog geteilt und bei Abbruch nicht geleert — der nächste
      Dialog startet mit Alt-Inhalt vorbelegt.** Abbruch-Pfade
      (`showNewFolder = false` Button `:1255-1257`, Rename-Cancel/
      Backdrop `:1273`, `:1286-1288`) setzen `nameInput` nicht zurück; nur
      der Erfolgs-Pfad von `createFolder` leert es (`:425`). Öffnet man nach
      einem abgebrochenen Rename „Neuer Ordner", ist das Feld mit dem alten
      Dateinamen vorbelegt und der Create-Button aktiviert
      (`:disabled="nameInput.trim().length === 0"`, `:1260`). Fix:
      `nameInput.value = ""` beim Öffnen/Abbrechen beider Dialoge.
- [ ] **L19-F6 (Bug, minor): `setQuota` im Admin-Panel lässt `NaN` durch und
      sendet `"NaN"` als Quota an die OCS-API.** Das Quota-Feld bindet
      `edits.quotaValue = $event.target.valueAsNumber`
      (`AdminPanel.vue:632-640`) — bei geleertem/ungültigem Feld ist das
      `NaN`; die Prüfung in `setQuota` (`AdminPanel.vue:280-288`,
      `value === null || value <= 0`) greift bei `NaN` nicht (`NaN <= 0`
      ist false), sodass `String(Math.round(NaN))` = `"NaN"` an
      `admin_set_user_quota` geht und der Server-Fehltext statt des
      lokalisierten `quotaInvalid` erscheint. Fix: `!Number.isFinite(value)`
      mitprüfen.
- [ ] **L19-F7 (i18n, minor): Der Update-Banner zeigt rohe Backend-Statuscodes
      statt lokalisierter Texte.** `App.vue:166-171` rendert im
      Banner-Fortschritt `${e.payload.code}` (z. B. „downloading",
      „installing") als Klartext; nur `checksum_warning` wird übersetzt.
      Das SettingsModal hat mit `updateStatusText`
      (`SettingsModal.vue:206-222`) bereits die vollständige Code→Key-Map —
      der Banner sollte dieselbe Übersetzung nutzen.
- [ ] **L19-N1 (UX, minor): Escape schließt weder Kontextmenü noch Modals.**
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

- [ ] **CP-F1 (Paritäts-Bug, mittel): Share-API ohne Impersonation im
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
- [ ] **CP-F2 (Paritäts-Bug, mittel): `rename` validiert den neuen Namen
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
- [ ] **CP-F3 (Feature-Idee/Parität, mittel): Kein Chunked-Upload v2 mobil —
      große Uploads sind ein einzelner PUT.** Der Desktop lädt Dateien
      > 10 MiB über das Nextcloud-Chunked-v2-Protokoll hoch
      (`webdav.rs:22-31` Schwellen/Konstanten, `chunked_put_v2`
      `webdav.rs:390-502` inkl. `OC-Total-Length`-Quotavorprüfung und
      Session-Cleanup). KMP `WebDavApi.uploadStream` (`WebDavApi.kt:224-238`)
      streamt zwar aus dem Speicher heraus, aber als einen einzigen PUT —
      bei großen Dateien drohen Client-Timeouts, und die Server-Quota wird
      erst am Ende geprüft. Idee: Chunked-v2-Port nach `commonMain`.
- [ ] **CP-F4 (Sicherheit/Parität, mittel): Android-Self-Update lädt das APK
      ohne SHA-256-Prüfung herunter.** Desktop `updater.rs` gate't die
      Installation auf die im Release publizierte SHA-256-Prüfsumme. KMP:
      `UpdateChecker.checkForUpdate` (`UpdateChecker.kt:45-63`) nimmt das
      erste `.apk`-Asset ohne Prüfsummen-Bezug,
      `AndroidPlatform.downloadUpdate` (`AndroidPlatform.kt:116-139`)
      streamt das APK ungeprüft in den Cache und
      `Platform.downloadAndInstall` (`AppUpdater.kt:10-13`) übergibt es dem
      Package-Installer. Eine SHA-256-Implementierung liegt mit
      `data/Sha256.kt` bereits in `commonMain` (bislang nur für Cache-
      Dateinamen). Idee: Prüfsummen-Asset analog Desktop auswerten und vor
      `installUpdate` verifizieren.
- [ ] **CP-N1 (Bug/Parität, minor): Der L17-N4-Paginierungs-Bug von
      `list_groups` ist 1:1 in den KMP-Port kopiert.** `FlutCloudOcs.kt:92-111`
      (`listGroups`) bricht nach dem Dedup-Filter ab, sobald
      `groups.size < limit` (:107) — eine volle 200er-Seite mit nur einem
      Duplikat beendet das Paging vorzeitig; identisch zur Desktop-Stelle
      `ocs.rs:318` (`new_groups < LIMIT`, L17-N4). Fix gemeinsam umsetzen:
      Rohseitenlänge `< limit` zählen, `seen`-Schutz behalten.
- [ ] **CP-N2 (Parität, minor): Keine FlutCloud-only-Kontobereinigung beim
      Start im KMP — Fremd-Konten und tokenlose Konten bleiben stehen.**
      Der Desktop droppt Konten fremder Server beim Start und meldet
      Verworfenes/Fehlendes über `account_filter_info`
      (`AccountFilterInfo` in `ipc.ts:86-91,198-199`). KMP:
      `AccountStore.loadAccounts` (`AccountStore.kt:27-32`) lädt ungefiltert,
      `SessionManager.init`/`restoreSession`
      (`SessionManager.kt:21-57`) filtern weder noch melden; ein Konto mit
      verlorenem Token bleibt in der Liste und „Wechseln" endet stumm bei
      `session = null`. Idee: Filterung + `tokenMissing`-Reporting portieren.
- [ ] **CP-N3 (Feature-Ideen, minor): Drei Desktop-Features haben kein
      mobiles Pendant:** Bildvorschauen (`preview` in `webdav.rs:719-765`,
      Command `webdav_thumbnail` vs. nur `fileIcon`-Icons in
      `FilesScreen.kt:519-527`), Ordner-ZIP-Download (`download_zip_as`
      `webdav.rs:630-649` vs. fehlend in `WebDavApi.kt`) sowie
      Mehrfachauswahl/Bulk-Aktionen (Select-all/Bulk-Download/-Delete im
      Desktop-`FileExplorer.vue` vs. nur Einzeldatei-Dropdown in
      `FilesScreen.kt:544-602`). Alles Kandidaten für Paritäts-Läufe;
      Reihenfolge nach Nutzen: Bulk-Aktionen > Thumbnails > ZIP.
- [ ] **CP-N4 (Parität, minor): Quota ohne Offline-Cache mobil.** Desktop
      cached die Quota offline (`account_storage` + `cache.rs`); KMP
      `FilesViewModel.refreshQuota` (`FilesViewModel.kt:152-157`) setzt bei
      Netzwerkfehlern still auf `null` (QuotaBar leer, obwohl die letzte
      Quota bekannt war). Idee: letzte Quota in `ListCache`/Settings
      mitspeichern und als `offline=true`-Wert zeigen.
- [ ] **CP-N5 (Parität/Policy, minor): Kein Client-seitiger Schutz der
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

- [ ] **L17-F1 (Bug, mittel): `bulkDelete` in `FileExplorer.vue` lässt
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
- [ ] **L17-F2 (Bug/UX, mittel): `validate_dav_path` blockiert auch die
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
- [ ] **L17-F3 (Robustheit, minor): `accounts.json` und
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
- [ ] **L17-F4 (i18n, minor): Der Sync-`PassError`-Code
      `walk_incomplete` fehlt in `ERROR_CODE_KEYS` — die UI zeigt
      „Unbekannter Fehler." statt des Hinweises, dass Löschungen
      übersprungen wurden.** `sync.rs:1159-1164` erzeugt
      `{ code: "walk_incomplete", detail: Some("Some files could not be
      read. Deletions were skipped for safety.") }`; `i18n.ts:631-649`
      mappt den Code nicht → `translateError` fällt auf `errUnknown`
      zurück (`i18n.ts:652`), `SyncPanel.vue:23-25` rendert
      „Unbekannter Fehler."/"Unknown error.". Fix: Keys
      `errWalkIncomplete` (en+de) + Mapping ergänzen.
- [ ] **L17-N1 (CI, minor): Abweichende `setup-android`-Pins zwischen
      `kmp.yml` und der `kmp-ios-build`-Action.**
      `.github/workflows/kmp.yml:129` pinnt
      `android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699 # v4.0.1`,
      `.github/actions/kmp-ios-build/action.yml:30` dagegen
      `@9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407 # v3.2.2`. Der
      iOS-Kompilier-Check und der echte IPA-Build laufen damit gegen
      unterschiedliche Action-Versionen (alle übrigen Pins im Repo sind
      konsistent auf volle SHAs gesetzt). Fix: einen gemeinsamen Pin
      verwenden.
- [ ] **L17-N2 (CI, minor): Kein ShellCheck/Syntax-Check für die
      Haupt-Installationsskripte.** `.github/workflows/flutcloud.yml:79-82`
      lintet ausschließlich `scripts/install-flutcloud-app.sh`; die
      README-One-Liner-Pfade `scripts/install-flutlink.sh`, Root-`install.sh`
      und `scripts/opencode-with-fallback.sh` (Release-Pipeline,
      `release.yml:108`) haben keinerlei Bash-Lint-Abdeckung. Fix: dieselben
      `bash -n` + `shellcheck -S warning`-Schritte auf alle `.sh` ausdehnen.
- [ ] **L17-N3 (UX-Konsistenz, minor): Der Login-Tab validiert leere
      Formularfelder nicht client-seitig.** `LoginModal.vue:99-120`
      (`submit`) prüft nur `serverUrl` und schickt leere
      Benutzername/Token-Felder ans Backend (OCS-Fehltext statt dem
      lokalisierten `requiredFields`-Hinweis, den `submitRegister`
      (`LoginModal.vue:128-131`) zeigt. Fix: dieselbe Prüfung wie im
      Register-Tab ergänzen.
- [ ] **L17-N4 (Konsistenz, minor): Paginierungs-Guard von
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

## Offen

- [ ] Desktop-JVM: Token-Speicher härten — OS-Keyring-Anbindung statt
      600er-Datei unter `$XDG_STATE_HOME/flutlink` (siehe
      `FileKeyValueStorage`), Parität zum Tauri-Client (`keyring`).

## Performance-Analyse (ergänzt 2026-08-25)

### High Priority

- [ ] **R1 (Sync): Remote-Listing ist sequenzielles BFS** — `sync.rs:388-446`
      macht N sequenzielle HTTP-Requests (Depth: 1 pro Directory). Fix:
      `tokio::sync::Semaphore` mit 4-8 Permits für paralleles Listing.
- [ ] **N1+F2 (Shares): `loadAllShares()` ruft ALLE Shares pro Navigation**
      — `FileExplorer.vue:585-599` + Watcher `:773-788` ohne Pfadfilter.
      Fix: `listShares(files.currentPath)` übergeben.

### Medium Priority

- [ ] **F1 (Sort): Doppelte Sortierung** — `FileExplorer.vue:56-58` sortiert,
      `EntryList.vue:53-55` sortiert erneut. Fix: Parent sortiert, Child
      bekommt vorsortierte Entries.
- [ ] **R3 (Cache): `evict_oldest` liest bei jedem Write alle Files** —
      `cache.rs:57-82`: 500 `metadata()`-Syscalls + Sort pro Navigation.
      Fix: Atomic-Counter, Batch-Eviction (10% auf einmal).
- [ ] **R2 (Sync): `plan_ops` allokiert Union-BTreeSet** — `sync.rs:563-566`
      O(N) Memory + O(N log N). Fix: `merge` auf BTreeMap-Keys statt
      Union-Set.

### Low Priority

- [ ] **U3 (Rendering): `formatMtime` erstellt pro Entry ein Date-Objekt**
      — `EntryList.vue:47-51`. Fix: Mtimes vorformattieren/cachen.
- [ ] **U2 (Grid): Hover-Overlay erzwingt GPU-Compositing** —
      `EntryList.vue:271`, `hidden`→`flex` pro Item. Fix: Shared Overlay
      via CSS `:has()` oder ein Element.
- [ ] **N2 (Thumbnails): 50 gleichzeitige HTTP-Requests** —
      `FileExplorer.vue:303-312`, fire-and-forget. Fix: Concurrency-Limiter
      (max 4-6).
- [ ] **U5 (Rendering): `<thead>` wird bei jedem Entry-Change neu gerendert**
      — `EntryList.vue:86-105`. Fix: `v-once`.
