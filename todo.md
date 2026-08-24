# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.

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
      löschen“ bleiben dauerhaft wirkungslos: `bulkDownload` (:118),
      `dropUpload`/Drag-&-Drop (:152), `open` (:195), `download` (:240),
      `downloadZip` (:258) — jeweils `if (busyPath.value) return` — und
      der „Working…“-Indikator (`v-if="busyPath !== null"`, :1027)
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
      modified.“ (Stimulans ist eine Lese-Aktion.) Thumbnails schlagen
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
      „Unbekannter Fehler.“ statt des Hinweises, dass Löschungen
      übersprungen wurden.** `sync.rs:1159-1164` erzeugt
      `{ code: "walk_incomplete", detail: Some("Some files could not be
      read. Deletions were skipped for safety.") }`; `i18n.ts:631-649`
      mappt den Code nicht → `translateError` fällt auf `errUnknown`
      zurück (`i18n.ts:652`), `SyncPanel.vue:23-25` rendert
      „Unbekannter Fehler.“/„Unknown error.“. Fix: Keys
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

- [x] „`SettingsStore` nach `commonMain` heben“ → **erledigt**:
      `kmp/shared/src/commonMain/kotlin/com/flutcloud/flutlink/core/SettingsStore.kt`
      liegt vollständig in `commonMain` (Flow-basiert, persistiert über
      den plattformgelieferten `KeyValueStorage`; Android-Actual in
      `androidMain/…/core/AndroidStorages.kt`). Verschoben nach
      `archived-todo.md`.
- [x] „iOS-Parität (Langläufer)“ → **erledigt**: die Compose-UI
      (Login, Files, Admin, Settings) liegt komplett in `commonMain`
      (45 Kotlin-Dateien in commonMain vs. 7 in androidMain);
      plattformgebundene Dienste sind als Actuals umgesetzt
      (`iosMain/…/core/IosStorages.kt`: `IosKeychainStorage` via
      SecItem-API + `IosDefaultsStorage` via NSUserDefaults;
      `PlatformActuals.kt`, `PlatformUi.ios.kt`) und in
      `kmp/README.md` („Stand der iOS-Parität“) dokumentiert.
      Verschoben nach `archived-todo.md`.
- [ ] „Desktop-JVM: Token-Speicher härten“ → **weiter offen, bestätigt**:
      `kmp/shared/src/jvmMain/kotlin/com/flutcloud/flutlink/desktop/FileKeyValueStorage.kt:12-15`
      legt Tokens weiterhin als Properties-Datei mit 600-Rechten unter
      `$XDG_STATE_HOME/flutlink` ab; Kommentar nennt die Keyring-Anbindung
      ausdrücklich als Follow-up.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe sind in diesem
Lauf verboten): `git log` belegt die gemergten Dependabot-PRs #324
(okio 3.18.1), #325 (okhttp) und #326 (opencode/github 1.18.21) sowie
die Feature-Commits seit dem letzten Lauf (FLUTCLOUD_URL-Baking
`5357baf`, FlutCloud-App-Zip `79afc45`/`a5eed2f`, iOS-AltStore-Quellen
`8f5213b`, KMP-Update-Check `47ca9d2`); der todo.md-Kopf bestätigt, dass
#293/#317/#318 geschlossen sind. Ob darüber hinaus offene Issues
existieren oder veraltet sind, ist hier nicht prüfbar — der
`opencode-todo-issues`-Workflow sollte beim nächsten Lauf einen Re-Sync
machen und dabei die L17-Befunde oben als Issues erfassen.

## Offen

- [ ] Desktop-JVM: Token-Speicher härten — OS-Keyring-Anbindung statt
      600er-Datei unter `$XDG_STATE_HOME/flutlink` (siehe
      `FileKeyValueStorage`), Parität zum Tauri-Client (`keyring`).
