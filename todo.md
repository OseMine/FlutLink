# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.
Am 2026-08-25 sind zusätzlich die kompletten Review-Abschnitte der Läufe
17–19 (L17-*/L19-*/CP-* — allesamt im Code umgesetzt) nach
`archived-todo.md` verschoben; offen blieben nur „Desktop-JVM: Token-Speicher
härten“ und die Performance-Analyse. Am 2026-08-26 sind die Abschnitte der
Läufe 20 und 21 gefolgt (nahezu komplett umgesetzt, Reste unten geführt).

## Review 2026-08-26 (Lauf 22, Fokus SaaS-UI-Umbau + ganze Projektbasis — neue Befunde)

Gegenstand: die Umsetzung des UI-Overhauls „Material → Modern SaaS“ seit
Lauf 21 (Commits `9855d7f`, `d6f596d`, `4fd1166`, `70262c2`, `91310f8`,
`28a9078`, `ecd5ee2`: `@material/web` entfernt, SaaS-Token-System in
`style.css`, Stroke-Icons via `Icon.vue`, native Tab-Leisten, erreichbarer
Gast-Admin-Tab #372, Locks-Read-Endpunkt #373) plus die Standard-Bereiche
(IPC-Commands, WebDAV/OCS-Anbindung, Keyring, Fehler-/State-Management,
CI/Workflows unter `.github/`) und die Nachprüfung aller offenen
L20-/L21-Befunde gegen HEAD (`ecd5ee2`).

Neu gefunden:

- [ ] **L22-F1 (Bug, hoch): `npm run build` schlägt auf HEAD fehl — toter
      Accent-Code im SettingsModal verstößt gegen `noUnusedLocals`.**
      vue-tsc meldet `src/components/SettingsModal.vue(95,10): 'applyAccent'
      is declared but its value is never read` und `(99,10)` für
      `resetAccent` (frisch verifiziert). Hintergrund: Der SaaS-Umbau hat
      die Accent-Sektion aus dem Template entfernt (der Theme-Block
      `SettingsModal.vue:399-419` geht direkt zu Updates über), die
      Skript-Reste blieben jedoch liegen: `accentValue`
      (`SettingsModal.vue:83`), `applyAccent` (:95-97), `resetAccent`
      (:99-102) und der open-Watcher (:104-112) sind unerreichbar. Damit ist
      auch das Feature selbst weg: Die i18n-Keys
      `accentColor`/`accentColorHint`/`accentReset` (`i18n.ts:88-93` en,
      `:424-429` de) sind ungenutzt, `ui.setAccentHue` (`ui.ts:84-88`) ist
      nicht mehr aufrufbar — ein vor dem Umbau persistierter Hue
      (`flutlink.accentHue`) wirkt aber weiter (`App.vue:51-54` setzt
      `--accent-hue`) und kann weder geändert noch zurückgesetzt werden.
      Fix: Entweder Accent-Sektion (inkl. Hue-Regler/Reset) wieder ins
      Template aufnehmen oder Skript-Reste + State + App.vue-Override +
      i18n-Keys vollständig entfernen.
- [ ] **L22-F2 (Bug/Konsistenz, mittel): Bulk-/Drag&Drop-Uploads fehlt der
      TOCTOU-Überschreibschutz des Einzel-Uploads.** `upload_tree`
      (`commands.rs:1081-1108`) und der Datei-Zweig von
      `webdav_upload_local_paths` (`commands.rs:1165-1187`) prüfen
      `webdav::exists()` und PUTten anschließend ohne Bedingung — der
      Einzel-Upload `webdav_upload_file` sichert dieselbe Situation
      stattdessen über `put_file_params { forbid_overwrite: !overwrite }`
      ab (`If-None-Match: *` bzw. Chunked-`Overwrite: F`,
      `commands.rs:690-707`, `webdav.rs:353-366`/:487-489). Folge: Eine
      zwischen Check und PUT angelegte Zieldatei wird im Bulk-Pfad still
      überschrieben (Q9-Verhalten verletzt), und >10-MiB-Dateien laufen dort
      nie durch den gesicherten Chunked-Pfad mit Session-Cleanup. Fix: auch
      beide Bulk-Pfade auf `put_file_params` mit
      `forbid_overwrite: !overwrite` umstellen.
- [ ] **L22-F3 (Race/UX, minor): `saveField` im AdminPanel kann ungespeicherte
      Eingaben eines schneller ausgewählten Users wegwerfen.** Nach dem PUT
      refetcht `saveField("displayname"/"email")` ohne Sequenz-Guard:
      `selected.value = await api.adminGetUser(selected.value.id)`
      (`AdminPanel.vue:254-257`). `selected.value.id` wird erst nach dem
      Await gelesen — wurde zwischenzeitlich ein anderer User ausgewählt
      (der `selectUser`-Pfad hat seinen eigenen `selectSeq`-Guard,
      `AdminPanel.vue:213-236`), überschreibt die späte Antwort
      `edits.displayName`/`edits.email` des neu ausgewählten Users mit
      dessen Server-Stand und löscht begonnene Eingaben. Fix: denselben
      Sequenz-Guard verwenden bzw. das Refetch-Ergebnis verwerfen, wenn sich
      die Auswahl inzwischen geändert hat.
- [ ] **L22-N1 (Cleanup, minor): Toter `md-`-Carve-out in vite.config.ts.**
      Nach dem Material-Ausbau (`@material/web` aus `package.json`
      entfernt, keine `md-*`-Elemente mehr in `src/` — grep frisch
      verifiziert) ist `isCustomElement: (tag) => tag.startsWith("md-")`
      (`vite.config.ts:11-17`) obsolet; letzter Restposten aus L21-N5.

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs:241-295` deckungsgleich mit `src/lib/ipc.ts`, inkl. des neuen
`guest_admin_list_locks`-Wrappers, alle typisiert), Keyring (`accounts.rs`
save/load/delete + Linux-Hint + Quarantäne/Atomic über `persist.rs`),
Fehler-Serialisierung und i18n-Mapping (`error.rs::code()` ↔
`ERROR_CODE_KEYS` `i18n.ts:697-716`, alle Guest-Copy-Keys en+de vorhanden),
Offline-Cache (`cache.rs` atomic write + Quarantäne kaputter Files),
WebDAV-Layer (`webdav.rs`: Impersonation-Namespace-Guards, Chunked-v2 mit
Session-Cleanup, temp+rename, TOCTOU/If-Match im Einzel-Upload), OCS-Layer
(`ocs.rs`: `verify_share_owner`, Dedup-Pagination mit Progress-Guard,
einzelne Form-Encoding-Kette), Guest-Backend (`guest.rs`: Token/Pfad-
Validierung, anonymer Ping-Probe, Locks-Read-Endpunkt #373), Sync-Engine
(`sync.rs`, inkl. `remove_folders_for_account` beim Konto-Löschen,
`commands.rs:337-339`), FlutCloud-only-Policy (`flutcloud.rs`), Tray/CLI
(`lib.rs`), Updater („installing“-Status wird vor dem blockierenden Install
gemitet, `updater.rs:609-619` — Update-Banner und SettingsModal empfangen
ihn noch), Theme/i18n/Escape-Grundlagen sowie CI (build/lint/kmp/release/
flutcloud — abgesehen vom weiter offenen L20-N3).

Verifikation frisch ausgeführt (HEAD `ecd5ee2`): Tauri-Linux-Systemdeps
waren nachzuinstallieren; danach `cargo fmt --all --check` grün;
`cargo clippy --all-targets -- -D warnings` grün;
`cargo test --manifest-path src-tauri/Cargo.toml` → **109 passed /
0 failed** (+1 gegenüber Lauf 21: `parses_lock_list_from_ocs_envelope`);
`npm run build` → **fehlerhaft** (siehe L22-F1: zwei TS6133-Fehler;
die Rust-Seite und der Rest des Frontends sind unverändert sauber).

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `ecd5ee2`)

Seit dem Lauf-21-Report (`ec385da`, gemerged als PR #361) sind 10 Commits
hinzugekommen: die beiden Merges `454431f`/`c380d61`, der Gradle-
Toolchain-Fix `1ba0f6b` (kmp-CI) sowie die sechs UI-Commits oben. Sie setzen
**alle** offenen L20-/L21-Befunde um bis auf die drei unten genannten. Die
kompletten Abschnitte „Review 2026-08-25 (Lauf 20)“
und „(Lauf 21)“ sind nach `archived-todo.md` verschoben (dort als erledigt
markiert); in `todo.md` bleiben die Reste:

- [ ] **L20-N2 (weiter offen)**: `verify_guest_server` mappt „App zu alt
      für das Gast-Feature-Flag“ weiterhin auf
      `AppError::FlutCloudAppMissing` (`guest.rs:130-135`), dessen Text
      (`error.rs:125-132`) einen Installationsfehler behauptet — obwohl
      `verify_server` die App zuvor erfolgreich erkannt hat. Fix: eigener
      Fehlercode/-text („FlutCloud-App zu alt für Gastzugriff“).
- [ ] **L20-N3 (weiter offen)**: Der PowerShell-Parse-Check prüft weiterhin
      nur `install-flutcloud-app.ps1` (`.github/workflows/flutcloud.yml:94`,
      `-Filter 'install-flutcloud-app.ps1'`); der README-Einstiegspunkt
      `scripts/install-flutlink.ps1` bleibt ungeprüft. Fix:
      `-Filter '*.ps1'`.
- [ ] **L21-N4 (weiter offen)**: `FileExplorer.vue` ist weiterhin ein
      ~1500-Zeilen-Monolith (Toolbar/Breadcrumbs/Impersonation-Bar/Banner/
      Select-all/Transfer-Progress/Empty-States/Split-View/Kontextmenü/drei
      Dialoge plus ~850 Zeilen Script-Logik, `FileExplorer.vue:856-1494`);
      `AdminPanel.vue` ebenso (~640 Zeilen: Suchliste + Detailformular +
      Quota in einer Komponente). Der SaaS-Umbau hat beide nicht zerlegt —
      Kandidaten bleiben `FilesToolbar.vue`, `ImpersonationBar.vue`,
      `ShareDialog.vue`, `ContextMenu.vue`.

Stichproben der erledigten Punkte (Einzelnachweise jetzt im Archiv):
L20-F1 (#372: embedded-Gast-Tab `App.vue:390-396` + Watcher
`App.vue:210-222`), L20-F2 (#373: `guest.rs::list_locks` +
`guest_admin_list_locks` + Laden beim Share-Betreten
`GuestBrowser.vue:98-111`), L20-F3 (#374: Confirm in
`GuestBrowser.vue:181-194`), L20-N1 (#375: `enter()` guardt `busyPath` und
leert Entries, `GuestBrowser.vue:90-97`), L20-N4 (`Share.uidOwner` in
`ipc.ts:47`), L21-F1 (#362: dynamische `:type`-Bindungen
`LoginModal.vue:239/:285/:318`), L21-F2 (`--color-scrim` als
`@theme`-Token, `style.css:39`), L21-F3 (#364: `type="button"` auf beiden
Cancel-Buttons, `LoginModal.vue:261/:340`), L21-F4 (#365: Escape-Stack +
Außenklick für Kontomenü/Kategorie-Dialog/Zuweisungs-Dropdown,
`App.vue:224-236`, `GuestBrowser.vue:274-295`), L21-N1 (#366: einheitliche
native `.checkbox`-Klasse überall), L21-N2 (#367: echte Tablisten mit
`role="tablist"`/`aria-selected` in `App.vue:287-302`, `LoginModal.vue:191-210`,
`SettingsModal.vue:249-277`), L21-N3 (#368: `filesView`-Persistenz in
`ui.ts:15-44`, computed-Bindings `FileExplorer.vue:20-33`), L21-N5
(Material komplett entfernt), L21-N6 (#371: Akzent-Hint ohne
Material-You-Verweis, `i18n.ts:89-92/:425-428`).

Weiter offen (unverändert, bestätigt):

- [ ] „Desktop-JVM: Token-Speicher härten“ —
  `kmp/shared/src/jvmMain/kotlin/com/flutcloud/flutlink/desktop/FileKeyValueStorage.kt:14`
  dokumentiert die Keyring-Anbindung weiterhin als Follow-up („not encrypted
  at rest“).
- Performance-Analyse: R1 (sequenzielles BFS, `sync.rs:388-446`), R2
  (Union-BTreeSet, `sync.rs:557-568`), R3 (`evict_oldest` Vollscan,
  `cache.rs:57-82`), N1+F2 (`loadAllShares` pro Navigation ohne Pfadfilter,
  `FileExplorer.vue:769-785`), F1 (Doppelt-Sortierung: Parent sortiert für
  die Keyboard-Navigation `FileExplorer.vue:54-56`, Child erneut fürs
  Rendering `EntryList.vue:53-55`), U3 (`formatMtime` pro Entry,
  `EntryList.vue:47-51`), N2 (Thumbnail-Requests ohne Concurrency-Limiter,
  `FileExplorer.vue:782-783`), U5 (`<thead>` ohne `v-once`,
  `EntryList.vue:85-106`) — allesamt unverändert vorhanden. **U2 ist
  erledigt** (#363: Overlay bleibt montiert und faded nur über Opacity,
  `EntryList.vue:270-277`) und wurde in die Performance-Analyse-Migration
  ins Archiv übernommen.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe sind in diesem Lauf
verboten): `git log ec385da..HEAD` zeigt die zehn oben genannten Commits;
die UI-Commits referenzieren in Messages und Code-Kommentaren die
Issue-Nummern #362–#368 und #371–#375 — genau die L20-/L21-Befunde, die der
`opencode-todo-issues`-Workflow nach Lauf 20/21 angelegt haben dürfte. Die
Fixes dazu sind damit alle gelanded; die zugehörigen Issues wären zu
schließen (hier nicht prüfbar). Ob parallel weitere offene Issues entstanden
sind oder veralten, ist hier nicht feststellbar; der Workflow sollte beim
nächsten Lauf die vier neuen L22-Befunde oben als Issues erfassen.

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
- [ ] **N2 (Thumbnails): 50 gleichzeitige HTTP-Requests** —
      `FileExplorer.vue:303-312`, fire-and-forget. Fix: Concurrency-Limiter
      (max 4-6).
- [ ] **U5 (Rendering): `<thead>` wird bei jedem Entry-Change neu gerendert**
      — `EntryList.vue:86-105`. Fix: `v-once`.
