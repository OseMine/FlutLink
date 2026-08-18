# FlutLink Todo

Ein Tracking-Datei des Projekts: offene Punkte und datierte Review-Abschnitte.
Erledigte Punkte werden in `archived-todo.md` verschoben.

## Offen

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
- A9-7 (Impersonation fehlt) → **weiter offen**: kein `target_user`/
  `Impersonate-User` in `WebDavApi.kt`/`FilesScreen.kt`.
- A9-8 (Gruppenverwaltung fehlt) → **umgesetzt**: `GroupsDialog` +
  `AdminViewModel.addToGroup`/`removeFromGroup`/`createGroup` +
  OCS-Gruppen-Endpunkte.
- A9-9 (Shares nur Public-Link) → **teilweise**: Share-Dialog mit Liste +
  Widerruf jetzt vorhanden (`ShareDialog`, `vm.loadShares`/
  `vm.deleteShare`); Erstellung weiterhin nur Public-Link (`shareType = 3`,
  `FilesViewModel.kt:202`), User-/Gruppen-Shares und `publicUpload`
  fehlen → offen (reduziert).
- A9-10 (Quota nur Presets) → **weiter offen**: `AdminScreen.kt` kennt
  nur unlimited/1/5/10 GB, keine Freieingabe.
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
- A9-13 (Theme nur system/light/dark) → **weiter offen**: kein
  `operationflut`/`midnight` + Accent-Hue auf Android.
- A9-14 (preview() Dead Code) → **weiter offen**: `WebDavApi.kt:261`
  `preview()` ohne Aufrufer; Android zeigt keine Thumbnails.
- A9-15 (Suchergebnisse nur lesbar) → **umgesetzt**: `SearchResults` reicht
  `onDownload`/`onShareFile`/`onRename`/`onShareLink`/`onDelete`/
  `onJumpToPaired` an `EntryRow` durch — Aktionen in Suchtreffern sind aktiv.
- A9-16 (Offline-Cache fehlt) → **umgesetzt**: `listCache` +
  `OfflineBanner` („Offline — showing cached data", D7 betrifft den
  hartkodierten Text).

Weiterhin offen aus früheren Läufen: R8-C1 (tauri-action auf `@v1` gepinnt)
und R7-7 (Release-Draft manuell publizieren, als Schritt 3 dokumentiert).
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
- [ ] **A9-7 (Feature, mittel):** Admin-Impersonation fehlt auf Android —
      Desktop erlaubt Admins das Browsen fremder Nutzer (`webdav_list`
      `target_user` + `FileExplorer.vue` adminViewAll, `Impersonate-User`-
      Header in `webdav.rs`); `AdminScreen.kt`/`WebDavApi.kt` haben weder
      `target_user`-Parameter noch `Impersonate-User`-Support. Fix:
      Impersonation im Admin-Screen (Zugriff auf alle Nutzer-Dateien).
- [x] **A9-8 (Feature, mittel):** Admin-Gruppen-Verwaltung fehlt auf Android —
      Desktop hat seit Q3 `admin_list_groups`/`admin_create_group`/
      `admin_add_group_member`/`admin_remove_group_member` + UI in
      `AdminPanel.vue`; Android-`AdminScreen.kt`/`FlutCloudApi.kt` haben
      keine Gruppen-Endpunkte. Auch fehlen displayname/email/password-
      Bearbeitung (Desktop `admin_edit_user`). Fix: Gruppen-API + UI portieren.
      → umgesetzt (GroupsDialog + AdminViewModel-Gruppenaktionen), siehe Lauf 11.
- [ ] **A9-9 (Feature, minor):** Android-Shares nur Public-Link —
      `FilesViewModel.createPublicShare` (Z. 153-171) hartkodiert
      `shareType = 3`; User-/Gruppen-Shares (Desktop P1) und
      `publicUpload`-Option fehlen. `FlutCloudApi.listShares`/
      `deleteShare` (Z. 245-253) existieren zwar, werden aber von keiner
      UI aufgerufen (Dead Code) → kein Share-Management (Liste/Widerruf,
      Desktop P2). Fix: Share-Optionen + Verwaltung portieren.
- [ ] **A9-10 (Feature, minor):** Android-Quota-Verwaltung nur Presets
      (unlimited/1/5/10 GB); Desktop erlaubt seit Q8 freie Werteingabe
      („custom"). Fix: Freieingabe ergänzen.
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
- [ ] **A9-13 (Design, minor):** Android-Theme deckt nur system/light/dark +
      dynamic color ab (`Theme.kt`); Desktop bietet die FlutCloud-Brand-
      Themes `operationflut`/`midnight` + Accent-Hue-Slider (U8). Fix:
      Brand-Themes + Accent-Hue auf Android portieren.
- [ ] **A9-14 (Dead Code, minor):** `WebDavApi.preview()` (Z. 185-201) wird
      nirgends aufgerufen — Thumbnails existieren nur im Desktop
      (`webdav_thumbnail` + `thumbs`-Cache). Fix: Entweder Thumbnails in
      `FilesScreen` nutzen oder `preview()` entfernen.
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
- [ ] **R8-C1 (CI, minor):** `release.yml` (Z. 135) pinnt `tauri-apps/tauri-action`
      nur auf `@v1` (bewegliches Tag). Für Supply-Chain-Härtung auf einen
      vollständigen Commit-SHA pinnen (wie bei den übrigen Drittanbieter-Actions).
      → im automatisierten Lauf offen gelassen (Workflow-Dateien sind von der
      Aufgabe ausgenommen; weiterhin offen).

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
