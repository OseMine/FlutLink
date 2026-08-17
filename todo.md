# FlutLink Todo

Einzige Tracking-Datei des Projekts: offene Punkte, datierte Review-Abschnitte
und das Archiv erledigter Punkte in einem Dokument. Ersetzt die früheren
Dateien `archived-todo.md` und `reports/review-*.md`.

## Offen

### Review 2026-08-17 (Lauf 10, Fokus IPC/Updater-UI & Verifikation — neue Befunde)

Verifikation in diesem Lauf frisch und grün: `cargo test --manifest-path
src-tauri/Cargo.toml` → 78 passed / 0 failed; `cargo clippy --all-targets
--manifest-path src-tauri/Cargo.toml -- -D warnings` grün; `cargo fmt --check`
grün; `npm run build` (vue-tsc + vite) grün. Fokusbereich: Updater-Events
(`update://status`/`update://progress`), IPC-Wrapper (`src/lib/ipc.ts`),
Update-Banner (`App.vue`) vs. Settings-Dialog (`SettingsModal.vue`), Keyring
(`accounts.rs`) und Offline-Cache (`cache.rs`/`commands.rs`). Neu gefunden:

- [ ] **A10-1 (Bug, mittel):** `App.vue` (Z. 159-161) lauscht auf
      `update://status` als `listen<string>` und weist `updateBannerStatus`
      den rohen Payload zu; das Backend emittiert aber ein
      `UpdateStatus`-Objekt `{ code, assetName }` (`updater.rs:105-113`,
      serde camelCase). Der Update-Banner (Z. 200-201) zeigt damit
      `[object Object]` statt eines lokalisierten Status an. `SettingsModal.vue`
      (Z. 163-167) verarbeitet dasselbe Event korrekt als
      `listen<UpdateStatus>` mit Code-Mapping (`updateStatusText`, Z. 178-190).
      Fix: In `App.vue` denselben `UpdateStatus`-Typ nutzen und über
      `translateError`/Code-Mapping lokalisieren (oder `updateStatusText`
      wiederverwenden).
- [ ] **A10-2 (Konsistenz, minor):** `updater.rs:360` emittiert im
      Checksumme-fehlt-Pfad einen **rohen String**
      (`"checksum unavailable, skipping verification"`) auf `update://status`,
      während alle anderen Emits ein `UpdateStatus`-Objekt senden
      (Z. 568, 581, 593). `SettingsModal.vue` `updateStatusText` mappt
      unbekannte Codes auf `""` → der Hinweis verschwindet still in der UI
      (nur noch `eprintln!` im Log). Fix: Auch hier `UpdateStatus`
      (z. B. `code: "checksum_unavailable"`) emittieren oder auf den
      `checking`-Pfad verzichten; ggf. i18n-Key ergänzen.
- [ ] **A10-3 (CI, bestätigt weiter offen):** R8-C1 unverändert — `release.yml`
      Z. 135 pinnt `tauri-apps/tauri-action@v1` weiterhin nur auf das
      bewegliche Tag (übrige Drittanbieter-Actions sind ebenfalls nur
      Major-Tags: `actions/checkout@v7`, `actions/setup-node@v7`). Kein
      SHA-Pinning, keine Änderung in diesem Lauf (Workflow-Dateien sind von
      der Aufgabe ausgenommen).

- [ ] **A10-4 (i18n, minor):** Datums-/Zeitformate ignorieren die UI-Sprache:
      `EntryList.vue:49` (`formatMtime`) und `SyncPanel.vue:24` rufen
      `toLocaleString()` **ohne** Locale-Argument auf → das WebView nutzt die
      OS-Locale statt `ui.lang` (en/de). Bei deutscher UI auf englischem
      System (und umgekehrt) zeigen Liste/Sync-Panel das Datum in der falschen
      Sprache. Fix: `date.toLocaleString(ui.lang)` (bzw. `"de"`/`"en"`)
      durchreichen.

Keine weiteren neuen Befunde in diesem Lauf: Der Rest der offenen Punkte
(A9-1 bis A9-16) wurde gegen den aktuellen Android-Code verifiziert und ist
**weiterhin offen** (kein Sync-Tab, `strings.xml` nur `app_name`, Rename
weiterhin nur für Ordner in `FilesScreen.kt:375`, keine Suche-Debounce, Upload
weiterhin `readAllBytes`/`body.bytes()`, kein `exists`-Check vor PUT, kein
`target_user`/`Impersonate-User`, keine Gruppen-API, nur `shareType = 3`
(`FlutCloudApi.kt:232`), keine Quota-Freieingabe, kein `assert_flutcloud_url`
(`BuildConfig.FLUTCLOUD_URL` ungenutzt), Downloads in App-Files, keine
Brand-Themes, `WebDavApi.preview()` tot, Suchtreffer No-op, kein Offline-Cache).

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

- [ ] **A9-1 (Feature, hoch):** Android-Port hat **keinerlei Sync-Funktion** —
      der Desktop-Kern (`SyncPanel.vue` + `sync.rs`, Zwei-Wege-Sync mit
      Journal/Planner/Worker) fehlt komplett. `HomeScreen.kt` kennt nur die
      Tabs Files/Admin/Settings (kein Sync-Tab); im gesamten Android-Code
      existiert kein Sync-Äquivalent. Damit widerspricht der Port der
      AGENTS.md-Aussage („spiegelt den Desktop-Funktionsumfang"). Fix:
      Sync-Tab + Sync-Engine (oder Funktionsumfang in README korrigieren).
- [ ] **A9-2 (i18n, mittel):** Android komplett ohne Lokalisierung —
      `android/app/src/main/res/values/strings.xml` enthält nur `app_name`;
      sämtliche UI-Texte sind hartkodiert englisch (`FilesScreen.kt`,
      `LoginScreen.kt`, `AdminScreen.kt`, `SettingsScreen.kt`). Der Desktop
      lokalisiert alles über `src/lib/i18n.ts` (en/de). AGENTS.md fordert
      i18n für alle UI-Texte. Fix: Android-`strings.xml`-Ressourcen + Werte
      nachziehen.
- [ ] **A9-3 (Bug, minor):** Android erlaubt **Rename nur für Ordner** —
      `FilesScreen.kt` `EntryRow` (Z. 375-384) zeigt „Rename" nur
      `if (entry.isDir)`; Dateien sind auf Android nicht umbenennbar.
      Desktop erlaubt Rename für Dateien und Ordner (`FileExplorer.vue`
      `startRename`). Fix: Menüpunkt auch für Dateien anbieten.
- [ ] **A9-4 (Perf, minor):** Android-Suche ohne Debounce —
      `FilesScreen.kt` `SearchBar` (Z. 280-293) ruft `onValueChange =
      { vm.search(it) }` bei **jedem** Tastendruck → pro Zeichen ein
      SEARCH-Request mit `depth: infinity`. Desktop debounced 300 ms
      (`FileExplorer.vue` Z. 31/47-60). Fix: Debounce in
      `FilesViewModel.search`.
- [ ] **A9-5 (Robustheit, mittel):** Android lädt Dateien komplett in den
      RAM: Upload via `readAllBytes` (`FilesScreen.kt` Z. 523-524), Download
      via `response.body?.bytes()` (`WebDavApi.download` Z. 121-135). Desktop
      streamt (chunked upload > 10 MiB, `stream_to_file`). Große Dateien →
      OOM auf Android. Fix: Streaming (OkHttp ResponseBody → Datei), optional
      Chunked-Upload-Parität.
- [ ] **A9-6 (Bug, Datenverlust-Risiko, mittel):** Android-Upload
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
- [ ] **A9-8 (Feature, mittel):** Admin-Gruppen-Verwaltung fehlt auf Android —
      Desktop hat seit Q3 `admin_list_groups`/`admin_create_group`/
      `admin_add_group_member`/`admin_remove_group_member` + UI in
      `AdminPanel.vue`; Android-`AdminScreen.kt`/`FlutCloudApi.kt` haben
      keine Gruppen-Endpunkte. Auch fehlen displayname/email/password-
      Bearbeitung (Desktop `admin_edit_user`). Fix: Gruppen-API + UI portieren.
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
- [ ] **A9-11 (Policy, mittel):** Android-Login erzwingt `FLUTCLOUD_URL`
      nicht — `LoginViewModel` (Z. 28-33, 37) nimmt jede editierbare URL
      (Default aus `BuildConfig.FLUTCLOUD_URL`) und prüft nur die
      FlutCloud-App-Capability (`verifyServer`); Desktop erzwingt die exakte
      `.env`-URL via `assert_flutcloud_url` (`flutcloud.rs`). AGENTS.md
      verspricht „ausschließlich `$FLUTCLOUD_URL`". Fix: URL gegen
      `BuildConfig.FLUTCLOUD_URL` validieren oder URL-Feld sperren.
- [ ] **A9-12 (UX, minor):** Android speichert Downloads in den App-Files
      (`saveToAppStorage`, `FilesViewModel` Z. 96-102) statt im Download-
      Ordner bzw. mit Öffnen/Teilen; kein Share-Sheet. Desktop öffnet
      Dateien direkt (P8). Fix: `ACTION_VIEW`/MediaStore-Download + Teilen.
- [ ] **A9-13 (Design, minor):** Android-Theme deckt nur system/light/dark +
      dynamic color ab (`Theme.kt`); Desktop bietet die FlutCloud-Brand-
      Themes `operationflut`/`midnight` + Accent-Hue-Slider (U8). Fix:
      Brand-Themes + Accent-Hue auf Android portieren.
- [ ] **A9-14 (Dead Code, minor):** `WebDavApi.preview()` (Z. 185-201) wird
      nirgends aufgerufen — Thumbnails existieren nur im Desktop
      (`webdav_thumbnail` + `thumbs`-Cache). Fix: Entweder Thumbnails in
      `FilesScreen` nutzen oder `preview()` entfernen.
- [ ] **A9-15 (UX, minor):** Android-Suchergebnisse sind nur lesbar —
      `FilesScreen.kt` `SearchResults` (Z. 315-318) übergibt `onRename`/
      `onShare`/`onDelete`/`onJumpToPaired` als No-op-`{}`. Desktop erlaubt
      in Suchtreffern weiterhin Aktionen. Fix: Aktionen in Treffern
      freischalten oder Hinweis ergänzen.
- [ ] **A9-16 (Feature, minor):** Kein Offline-Cache auf Android — Desktop
      hat seit Q2 den Listing-Cache (`cache.rs`, Stale-Flag + Offline-Banner
      in `FileExplorer.vue`); Android zeigt bei Netzwerkausfall nur Fehler.
      Fix: Listing-Cache + Offline-Banner portieren.

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
- [ ] **R7-7 (Hinweis):** `release.yml` veröffentlicht als Draft (`releaseDraft: true`);
      `check_for_update` überspringt Drafts und Prereleases (`updater.rs:204-206`). Nach dem
      Build muss der Draft manuell publiziert werden, sonst erhalten Bestandskunden das
      v1-Update nicht. → beim Release manuell beachten (kein Code-Fix).

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

## Archiv (erledigt)

### Review 2026-08-16 (Lauf 8, Fokus UX — U-R8-1 bis U-R8-12, R8-B1)

- [x] **U-R8-1 (UX, Bug, mittel):** Tastatur-Navigation unsichtbar/in konsistent.
      Fix: `sortKey`/`sortAsc` in `FileExplorer.vue` zentralisiert, `kbdIndex` +
      Sortierzustand werden an `EntryList.vue` durchgereicht (Sortierung dort per
      Props, neues Event `toggleSort`); der fokussierte Eintrag erhält eine
      Highlight-Klasse (Liste: `bg-primary-container/70`, Grid: `ring-2
      ring-primary`). Tastatur und Anzeige operieren jetzt auf derselben
      sortierten Reihenfolge.
- [x] **U-R8-2 (UX, Bug, mittel):** Transfer-Fortschrittsleiste hing nach
      Einzel-Operationen. Fix: `FileExplorer.vue` ruft `files.clearTransfer()`
      nach `uploadFiles()`, `download()` und `downloadZip()` auf.
- [x] **U-R8-3 (UX, Bug, minor):** Grid-Doppelklick wählte ab. Fix:
      `EntryList.vue` Grid toggelt die Auswahl nur noch bei `$event.detail === 1`.
- [x] **U-R8-4 (UX, minor):** Share-Badge fehlte in der Grid-Ansicht. Fix:
      Zähler-Badge (`sharesByPath`) in die Grid-Kachel übernommen (oben links).
- [x] **U-R8-5 (UX, minor):** Update-Banner überlagerte den Header. Fix:
      `App.vue` legt Banner + Content in einer Flex-Column an — der Banner ist
      jetzt in-flow, kein Overlay mehr; Header-Buttons bleiben erreichbar.
- [x] **U-R8-6 (UX, minor):** `thumbs` und `shareState` wuchsen über
      Navigationen. Fix: `watch(() => files.entries)` prunt beide Maps auf die
      Pfade der aktuellen Einträge.
- [x] **U-R8-7 (UX, minor):** `AccountBar.vue` Filter-Hinweis nutzt jetzt
      M3-Tokens (`border-info bg-info-container/60 text-on-info-container`)
      statt `amber-*`-Klassen.
- [x] **U-R8-8 (UX, minor):** Theme-FOUC behoben. Fix: `resolvedTheme` wird
      initial synchron aus `ui.theme` + `matchMedia("(prefers-color-scheme:
      dark)")` abgeleitet (Helper `initialTheme`), bevor das `data-theme`-Watch
      greift.
- [x] **U-R8-9 (UX, minor):** Accent-Slider startet jetzt mit
      `ui.accentHue ?? themeDefaultHue()` (initiale Ref + beim Öffnen).
- [x] **U-R8-10 (UX/Validierung, mittel):** „Neuer Ordner" verweigert `/`, `\`,
      `.` und `..` in `createFolder` (Toast `folderNameInvalid`, i18n en/de);
      Backend `webdav_mkdir` validiert zusätzlich das Leaf-Segment mit
      `validate_rename_name` (Unit-Test `webdav_mkdir_leaf_must_be_a_single_name`).
- [x] **U-R8-11 (UX, minor):** `LoginModal.vue` setzt `form`,
      `showPassword`, `showAdminPassword` und `formError` beim Schließen
      (`close()`) und nach Erfolg (`done()`) zurück — kein vorausgefüllter Token
      mehr auf geteilten Geräten.
- [x] **U-R8-12 (UX, minor):** `AdminPanel.vue` `listUsers` verlangt einen
      Suchbegriff (`searchUsersRequired`, i18n en/de); interne Refresh-Aufrufe
      nach Anlegen/Löschen nutzen `listUsers(false)` ohne Zwang.
- [x] **R8-B1 (Backend, Bug, minor):** `webdav_bulk_download` überschreibt keine
      gleichnamigen Dateien mehr aus verschiedenen Ordnern. Fix: neuer Helper
      `bulk_local_path(dest, path)` erhält die relative Verzeichnisstruktur unter
      `dest_dir` (`/a/notes.txt` → `dest/a/notes.txt`); Datei-Targets legen ihren
      Parent-Ordner an. Unit-Test `bulk_local_path_preserves_relative_structure`.
- [ ] **R8-C1 (CI, minor, offen):** `release.yml` pinnt `tauri-action` nur auf
      `@v1`. Im automatisierten Lauf nicht angefasst (Workflow-Dateien sind von
      der Aufgabe ausgenommen) — weiterhin offen.

### Review 2026-08-16 (Android-Client)

- [x] **AC1 (Feature, neu):** Android-Mobile-Client `android/` umgesetzt —
      **Port des Desktop-Clients auf Kotlin + Jetpack Compose, generiert mit
      opencode** (kein separates Produkt; spiegelt den Desktop-Funktionsumfang,
      Änderungen müssen dort mitgezogen werden). Kotlin + Jetpack Compose
      (Material 3), App-Modul
      `com.flutcloud.flutlink` (compileSdk/targetSdk 36, minSdk 26, Java 17,
      AGP 8.13.2, Compose BOM 2025.06.00, OkHttp 5, kotlinx-serialization),
      Gradle-Wrapper 8.13. Backend-Anbindung über `FlutCloudApi` (OCS:
      User/Quota/Admin/Shares + FlutCloud-App-Capability-Probe) und
      `WebDavApi` (PROPFIND, SEARCH, Upload/Download, mkdir/rename/delete,
      `resources`/`parts`-Virtuallinks). Tokens liegen in
      `EncryptedSharedPreferences`, Metadaten in separaten Prefs (analog zum
      Keyring-Design des Desktop-Clients). UI: Login-, Files-, Admin- und
      Settings-Screens mit Navigation + ViewModels, M3-Theme in den
      FlutCloud-Farben. CI: `.github/workflows/android.yml` (assembleDebug +
      lintDebug). Verifikation: `./gradlew :app:assembleDebug` grün,
      `cargo fmt --check`/`cargo clippy -D warnings`/`cargo test`
      (72 passed)/`npm run build` grün; README-Strukturbaum aktualisiert,
      `android/README.md` ergänzt.

### Review 2026-08-16 (Android-Unit-Tests)

- [x] **AT1 (Feature, neu):** Unit-Tests für die kritischen Android-Module
      ergänzt (`android/app/src/test/`; vorher gab es keinerlei Tests). Abgedeckt:
      `WebDavApi.parseMultistatus` (absolute/relative hrefs, Namespace-Grenzen
      `admin` vs. `admin2`, Default-/präfix-Namespace, `resources`/`parts`-
      Klassifikation inkl. case-insensitive, URL-Decoding inkl. `+`-Erhaltung —
      Spiegel der Desktop-Tests in `src-tauri/src/nextcloud/webdav.rs`),
      `AccountStore`-Roundtrip (Metadaten, Tokens, Löschen, Key-Normalisierung,
      korruptes JSON), `SessionManager` (init/restore, add/switch/remove,
      Sign-out, Token-Wechsel) und `FlutCloudApi.parseOcs` (Meta-OK/Fehler,
      generische Fehlermeldung, invalid JSON).
- [x] **AT2 (Testbarkeit):** `AccountStore` akzeptiert die `SharedPreferences`-
      Instanzen jetzt per Primär-Konstruktor (Metadata-Prefs + Secure-Prefs);
      der `Context`-Konstruktor baut unverändert die echte
      `EncryptedSharedPreferences`-Ablage. Ein In-Memory-`SharedPreferences`-
      Fake (`InMemorySharedPreferences.kt`) macht Roundtrip + SessionManager
      ohne Robolectric auf der JVM testbar. `FlutCloudApi.parseOcs` von
      `private` auf `internal` gestellt. Produktionsverhalten unverändert.
- [x] **AT3 (Build/CI):** Test-Dependencies `junit` (4.13.2) und `xpp3`
      (1.1.4c, bringt die `org.xmlpull.v1`-API + MXParser für die JVM) in
      `android/gradle/libs.versions.toml` + `android/app/build.gradle.kts`
      ergänzt. Verifikation: `./gradlew :app:testDebugUnitTest` → 30 passed /
      0 failed; `assembleDebug` und `lintDebug` grün (0 errors). Der
      CI-Workflow `.github/workflows/android.yml` wurde bewusst nicht
      angefasst (Workflows sind tabu); `:app:testDebugUnitTest` lässt sich
      dort als zusätzlicher Schritt nachziehen.

### Review 2026-08-16 (Lauf 7, Release-Review v1 — R7-1 bis R7-6)

- [x] **R7-1 (Bug, mittel):** `assert_flutcloud_url` verglich die normalisierte
      Instanz-URL nur mit dem **rohen** `.env`-Wert (`flutcloud_url()?`); ein
      Trailing-Slash in `FLUTCLOUD_URL` (z. B. `https://flutcloud.de/`) ließ
      jeden `account_add`/`register_user`/`load_accounts` mit `NotFlutCloud`
      fehlschlagen — Login komplett unmöglich. Fix in `src-tauri/src/flutcloud.rs`:
      neue pure Helper-Funktion `urls_equal(a, b)` normalisiert beide Seiten
      (`strip_suffix('/')` + `to_ascii_lowercase`); `assert_flutcloud_url`
      wendet sie jetzt auf `flutcloud_url()?` an. Neuer Test
      `trailing_slash_on_env_side_still_matches`; der bestehende Test
      `accepts_the_flutcloud_url_with_and_without_slash` bleibt grün.
- [x] **R7-2 (Security-Hardening, minor):** `updater.rs` übernahm den
      `asset_name` aus der GitHub-API-Antwort ungeprüft als lokalen Dateinamen
      (`tmp_dir.join(&info.asset_name)`). Fix: neue pure Funktion
      `is_safe_asset_name(name)` akzeptiert nur leere-Pfad-freie Namen (nicht
      leer, kein `/` oder `\`, kein `..`, `Path::file_name()` muss den ganzen
      Namen ergeben); `download_update` weist Namen zurück, die außerhalb von
      `flutlink_update/` landen würden. Neue Unit-Tests `plain_asset_names_are_safe`
      und `path_like_asset_names_are_rejected`.
- [x] **R7-3 (Robustheit, minor):** Alle `path.to_str().unwrap()` im
      Updater-Installpfad (`install_update`) durch `unwrap_or("")` ersetzt
      (Zeilen ~368, 398, 400, 430 inkl. ditto-Argumente, 440 `detach`, 452
      Linux-`path_str`) — konsistent zum `extension()`-Handling; kein Panic
      mehr bei nicht-UTF-8-Pfaden.
- [x] **R7-4 (UX-Regression-Risiko, minor):** `src/lib/ripple.ts` setzte bei
      jedem `pointerdown` blind inline `position: relative; overflow: hidden`
      auf dem Host und überschrieb damit bewusst gesetzte Styles (`position:
      fixed`/`absolute`/`sticky`, Dropdown-/Sticky-Kontexte) → Tooltips/Menüs
      konnten abgeschnitten werden. Fix: Die Regeln werden nur noch gesetzt,
      wenn der Host sie nicht selbst etabliert (Computed-Style-Guard:
      `position === "static"` bzw. `overflow === "visible"`).
- [x] **R7-5 (Release-Prozess):** Version auf `1.0.0` angehoben
      (`package.json`, `src-tauri/Cargo.toml` + `Cargo.lock`,
      `src-tauri/tauri.conf.json`), damit die Assets `FlutLink_1.0.0_…`
      heißen und der Tag `v1.0.0` wird.
- [x] **R7-6 (Release-Prozess):** Working-Tree-Änderungen committet
      (`docs/README.md`, `docs/README-de.md`, `docs/de/flutcloud-app.md`,
      `docs/de/sync.md`, `docs/en/flutcloud-app.md`,
      `flutcloud-app/appinfo/info.xml` — Author-E-Mail, Nextcloud 28–31 →
      28–37).
- [x] **R7-7 (Hinweis, kein Fix):** `release.yml` veröffentlicht als Draft;
      `check_for_update` überspringt Drafts/Prereleases. Nach dem Build muss
      der Draft manuell publiziert werden, sonst erhalten Bestandskunden das
      v1-Update nicht — beim Release-Vorgang beachten.

### Review 2026-08-16 (Q1 / P12 / P13 / N3 / N4)

- [x] **Q1 (Phase 4, Feature, mittel):** Native Notifications umgesetzt
      (Issues #94). `tauri-plugin-notification` in `src-tauri/Cargo.toml`
      ergänzt und in `lib.rs` registriert. `sync.rs::run_all` erzeugt nach
      einem Pass eine OS-Notification bei Fehlern bzw. bei synchronisierten
      Dateien (Aggregat über alle Ordner; keine Spam-Notifications bei
      Leerläufen). `updater.rs::check_update` benachrichtigt, wenn eine neue
      Version verfügbar ist. README-Phase-4-Versprechen „native notifications"
      damit erfüllt.
- [x] **P12 / N3 (Security, bestätigt):** `release.yml` `release-notes`-Job
      gegen Prompt-Injection gehärtet (Issue #71): `$LOG` wird auf
      `--oneline`-Titel gekürzt, Steuerzeichen entfernt, auf 120 Zeichen
      begrenzt und der Prompt enthält einen expliziten „UNTRUSTED INPUT"-Block,
      der die Commit-Liste strikt als Daten deklariert (analog zur bereits
      entschärften `opencode.yml`).
- [x] **P13 / N4 (CI, bestätigt):** `build.yml` `paths-ignore` bereinigt
      (Issue #72): `.github/**` entfernt, sodass Workflow-/Action-Änderungen
      wieder CI auslösen; nur noch `*.md` wird ignoriert.

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
### Review 2026-08-16 (Q7)

- [x] **Q7 (Phase 3, Feature, minor):** Symlink-/Virtual-Link-Auflösung fehlte:
      `walk_local` übersprang Symlinks still (`sync.rs:196-198`),
      `resources`-Einträge wurden nie auf ihr Ziel aufgelöst (kein
      Link-Target-Feld in `WebDavEntry`). Fix umgesetzt:
      **Symlink-Following-Option im Sync** — neues `follow_symlinks`-Flag auf
      `SyncFolder` (serde-default false), `walk_local` dereferenziert Links mit
      Kanonikal-Pfad-Zyklusschutz, Threading durch `sync_add`
      (`Option<bool>`), CLI-Aufruf, `ipc.ts`, `stores/sync.ts` und
      `SyncPanel.vue` (Checkbox + Status-Anzeige), i18n en/de.
      **Link-Auflösung im Backend** — `link_target: Option<String>` in
      `WebDavEntry`; `resolve_link_target` mappt `resources/<name>` →
      `/parts/<name>` und umgekehrt, Container-/Normalpfade → `None`;
      Tooltip im Dateibrowser. Unit-Tests (3 × `walk_local` mit echten
      Symlinks unter `#[cfg(unix)]`, `resolves_virtual_links`,
      Parse-Assertions). Verifikation: `cargo test` 53 passed / 0 failed,
      `cargo clippy --all-targets -- -D warnings` grün, `cargo fmt --check`
      grün, `npm run build` grün. Docs (README Phase 3, `features.md`/`sync.md`
      en+de) aktualisiert.
### Review 2026-08-16 (P8/N5)

- [x] **P8/N5 (Bug, bestätigt N5):** `open()` (`FileExplorer.vue:96-100`) legte
      jeden Download dauerhaft in `tempDir()` ab (kein Cleanup) — jedes Öffnen
      einer Datei füllte das Temp-Verzeichnis. Fix: Neuer Backend-Command
      `open_remote_file` (`commands.rs`) lädt in das eigene Cache-Verzeichnis
      `<tempDir>/flutlink-open`, räumt Reste vorheriger Öffnungen vor jedem
      Download auf (best-effort) und öffnet die Datei direkt aus Rust
      (`tauri-plugin-opener`). Der Cache wächst nicht mehr mit jedem Öffnen;
      `FileExplorer.vue` `open()` ruft nur noch `api.openRemoteFile` auf
      (Wrapper in `ipc.ts`, registriert in `lib.rs`).
### Review 2026-08-15 (P17)

- [x] **P17 (Feature, minor):** Browsing-UX-Lücken im `FileExplorer.vue`
      geschlossen. Fix umgesetzt:
  - „Zurück"-Button in der Breadcrumb-Navigation (`goBack`, deaktiviert im
    Home-Ordner; i18n `back`).
  - Tastatur-Navigation: Pfeiltasten bewegen den Fokus (Highlight über
    `kbdIndex`), Enter = öffnen, Entf/Rücktaste = löschen (fokussierter
    Eintrag bzw. Auswahl); nur wenn kein INPUT/TEXTAREA/SELECT fokussiert ist.
  - Link-Button in der Grid-Ansicht pro Kachel (inkl. ✓/⧉-Status nach
    Erstellung); Einzelklick wählt jetzt aus.
  - Ordner-ZIP-Download: neuer Backend-Command `webdav_download_zip`
    (`commands.rs` + `lib.rs` + `ipc.ts` + `files.ts`), WebDAV-Get auf den
    Ordner mit `Accept: application/zip` (dokumentierte Nextcloud-Extension),
    Atomic-Write + Progress-Events über `stream_to_file` (aus
    `get_file_as_progress` extrahiert). In Listen-/Grid-Zeile und Kontextmenü
    (i18n `downloadZip`).
  - Thumbnails: neuer Backend-Command `webdav_thumbnail` holt
    `/index.php/core/preview.png?file=…&x=…&y=…` (Basic-Auth,
    Impersonate-User) und liefert eine base64-data-URL; Frontend lädt
    Thumbnails für `image/*`-Einträge lazy (Cache `thumbs`), Anzeige in
    Listen- und Grid-Ansicht mit Icon-Fallback.
  Verifikation: `cargo fmt --check`, `cargo clippy --all-targets -D warnings`,
  `cargo test` (49 passed), `npm run build` — alle grün.
### Review 2026-08-16 (P14)

- [x] **P14 (Feature, minor):** Globale Dateisuche fehlte (WebDAV-SEARCH).
      Fix umgesetzt: `webdav::search` in `nextcloud/webdav.rs` (SEARCH auf
      `/remote.php/dav/` mit `displayname`-„contains" über `depth: infinity`,
      XML-Escaping der Suchterme, `Impersonate-User`-Support + Namespace-Guard,
      Unit-Tests) + `webdav_search`-Command in `commands.rs` (in `lib.rs`
      registriert, Admin-Check für `target_user`) + `webdavSearch`-Wrapper in
      `src/lib/ipc.ts` + `searchFiles`/`clearSearch`/`displayEntries` im
      `files`-Store + UI-Suchfeld mit 300-ms-Debounce im `FileExplorer.vue`
      (Suchergebnis-Banner, Speicherort-Anzeige, Navigieren in Treffer).
      i18n-Keys `searchPlaceholder`/`searchResults`/`searching`/
      `noSearchResults`/`clearSearch` (en + de). Verifikation: `cargo fmt`
      grün, `cargo clippy -D warnings` grün, `cargo test` 51 passed,
      `npm run build` grün.
### Review 2026-08-16 (P15)

- [x] **P15 (Bug/Robustheit, minor):** `is_admin` wurde nur beim
      Account-Add/Register einmal ermittelt (`commands.rs:56-58`, Z. 224-226)
      und dauerhaft in `accounts.json` gespeichert. `ocs::is_admin`
      (`ocs.rs:59-71`) schluckte alle Fehler (`Err(_) => Ok(false)`) →
      transiente Netzwerkfehler beim Login markierten ein Admin-Konto dauerhaft
      als Nicht-Admin. Fix: Admin-Status wird beim App-Start neu evaluiert —
      `commands::refresh_admin_flags` (Spawn in `lib.rs` setup) prüft jedes
      gespeicherte Konto per OCS und überschreibt das Flag **nur bei Erfolg**
      (`AppState::set_is_admin`, in-place ohne Reorder; persistiert + emittiert
      `accounts-changed` nur bei Änderung). `ocs::is_admin` propagiert jetzt
      Fehler statt `Err(_) => Ok(false)`, und `account_add`/`register_user`
      behalten bei einem transienten Probe-Fehler den bisherigen Admin-Status
      (statt `false` zu speichern).
### Review 2026-08-16 (Q6)

- [x] **Q6 (Phase 3, Feature, mittel):** `resources`/`parts`-Dual-Pane-Workflow
      fehlte: Backend klassifizierte korrekt (`webdav.rs::classify`, Flags
      `is_resource`/`is_part`), die UI zeigte nur Badges (`FileExplorer.vue`),
      kein Pairing virtueller Links (`resources`) mit ihren schreibbaren
      Teilen (`parts`), kein „virtual ↔ real"-Navigationsfluss. Fix umgesetzt:
      `WebDavEntry` trägt jetzt `paired_path` (`state.rs`), berechnet in
      `webdav.rs::paired_path` (ersetzt das erste `resources`/`parts`-Segment,
      Unit-Tests ergänzt). Frontend: `src/stores/files.ts` liefert `pairOf()`
      und den Split-View-Store (`splitView`, `pairedPath`, `pairedEntries`),
      neue `src/components/EntryList.vue` (gemeinsame Listen-/Grid-Darstellung
      inkl. Sortierung und „↔"-Pairing-Button), `FileExplorer.vue` mit
      Pairing-Leiste („Virtual ↔ Real", dynamische Pane-Labels), Split-View-
      Button und Dual-Pane-Layout. Verifikation: `cargo test` → 50 passed,
      `npm run build` grün.
### Review 2026-08-15 (P10/N6)

- [x] **P10/N6 (Bug, minor):** Sync-Skip-Regel `should_skip_name`
      (`sync.rs:172-175`) war asymmetrisch: Lokale versteckte Dateien
      (`.env`, `.gitignore`) wurden nie hochgeladen, remote vorhandene
      versteckte Dateien wurden beim Erst-Sync heruntergeladen. Fix:
      `list_remote` skippt Einträge jetzt über das gemeinsame Prädikat
      `should_skip_rel` (letztes Pfadsegment gegen `should_skip_name`) —
      beide Sync-Richtungen behandeln versteckte Namen einheitlich. Neue
      Unit-Tests: `should_skip_rel_filters_hidden_names_on_both_sides` und
      `hidden_files_are_skipped_in_both_sync_directions` (walk_local-Integrität).
      Verifikation: `cargo fmt --check` grün, `cargo clippy --all-targets
      -- -D warnings` grün, `cargo test` → 51 passed / 0 failed.
### Review 2026-08-16 (N1)

- [x] **N1 (Bug, Datenverlust, mittel/hoch):** `webdav_rename` delegierte an
      `webdav::rename_as`, das den MOVE mit `Overwrite: T` sendete
      (`nextcloud/webdav.rs:359`) → „a.txt" → „b.txt" überschrieb b.txt
      stillschweigend. Fix: `rename_as` sendet jetzt `Overwrite: F` und mappt
      den 412-Status auf einen neuen `AppError::TargetExists` (Code
      `target_exists`, Meldung „Ziel existiert bereits"); Frontend rendert über
      den neuen i18n-Schlüssel `errTargetExists` (en + de) den Hinweis per
      Toast in `doRename` (`FileExplorer.vue`). Unit-Test
      `target_exists_serializes_with_code_and_name_detail` in `error.rs`.
      Verifikation: `cargo fmt --check`, `cargo clippy -D warnings`,
      `cargo test` (50 passed), `npm run build` grün.
### Review 2026-08-16 (P16)

- [x] **P16 (Bug/Robustheit, minor):** `relative_path`
      (`webdav.rs:641-658`) fand bei absoluten hrefs (mit Scheme/Host) kein
      `base_path` und erzeugte Pfade wie `/https:/host/...`; der
      Namespace-Guard (`webdav.rs:88-97`) prüfte nur das Präfix `/remote.php/`
      und griff bei der absoluten Form nicht. Fix: Neuer `href_path` strippt
      Scheme + Host aus absoluten hrefs, `find_base_path` matcht `base_path`
      nur an Pfadgrenzen (kein Fehltreffer bei `/remote.php/dav/files/admin2`),
      und der Guard nutzt `is_namespace_mismatch`, das sowohl die relative
      (`/remote.php/…`) als auch die geleakte absolute Form (`/https:/…`,
      `/http:/…`) erkennt. Vier neue Unit-Tests
      (`handles_absolute_hrefs`, `relative_path_keeps_base_path_boundaries`,
      `detects_namespace_mismatch_for_both_href_forms`,
      `parses_absolute_hrefs`).
### Review 2026-08-16 (Q4)

- [x] **Q4 (Phase 3, Feature, mittel/hoch):** Chunked Uploads/Downloads mit
      Progress fehlten. Fix umgesetzt: **WebDAV-Chunked-Upload v2** in
      `put_file_as_progress` (`webdav.rs`): Dateien > 10 MiB laufen über
      `chunked_put_v2` (MKCOL-Session unter `/remote.php/dav/uploads/` mit
      `Destination`-Header → nummerierte Chunk-PUTs in 10-MiB-Blöcken
      (5 MiB..5 GiB, serverkonform, per Kompilierzeit-`assert` abgesichert)
      → finaler MOVE von `.file` mit `X-OC-MTime` + `OC-Total-Length`);
      kleinere Dateien behalten den einfachen PUT. Progress-Callback ist
      durch die Transfer-Helper gezogen (`ProgressStream`,
      `put_file_as_progress`, `get_file_as_progress`) und emittiert
      `file://progress`-Events (`transfer_progress`, `commands.rs`) für
      Upload und Download. Große Dateien brechen nicht mehr am 60-s-Total-
      Timeout ab (F2): `state.rs` nutzt `connect_timeout`/`read_timeout`
      ohne Gesamtlimit; jede Chunk-Anfrage ist eigenständig. Fehlgeschlagene
      Chunk-Sessions werden per DELETE aufgeräumt (kein Quota-Leak). Test
      `transfer_ids_are_unique` ergänzt. Verifikation: `cargo fmt --check`,
      `cargo clippy --all-targets -D warnings`, `cargo test` → 50 passed,
      `npm run build` grün.
### Review 2026-08-16 (P9/N2)

- [x] **P9/N2 (Bug, Robustheit):** `list_users` (`ocs.rs:75-119`) paginiert
      per Offset ohne Fortschritts-Guard → Endlosschleife, wenn der Server den
      `offset`-Parameter ignoriert (gleiche Seite erneut, `count == LIMIT`).
      Fix: Duplikat-Erkennung als Abbruchbedingung. Der Loop trackt jetzt eine
      `HashSet<String>` (`seen`) über den neuen Helper `progress_count`; liefert
      eine Seite keine neuen Benutzer, wird abgebrochen statt `offset += 200`
      endlos weiterzulaufen. Unit-Tests `progress_guard_stops_on_repeated_page`
      und `progress_guard_counts_partial_new_users` ergänzt.
### Review 2026-08-16 (Q2)

- [x] **Q2 (Phase 4, Feature, mittel):** Kein Offline-Cache im Dateibrowser.
      Fix umgesetzt: Neues Backend-Modul `src-tauri/src/cache.rs` persistiert
      Listing- und Quota-Daten im AppData-Verzeichnis (Unterordner `cache/`,
      Dateinamen aus namespace+path gehasht → kein Pfad-Escape). `webdav_list`
      (`commands.rs`) schreibt jedes erfolgreiche Listing in den Cache und
      liefert bei Netzwerkfehlern (`AppError::is_network()`, nur
      `AppError::Http`) das zuletzt gecachte Listing statt eines Fehlers/leeren
      Ordners zurück — als `WebDavListResult { entries, stale }`.
      `account_storage` speichert/liest analog die Quota
      (`StorageResult { quota, stale }`). Frontend: `src/stores/files.ts`
      hält jetzt ein `offline`-Flag (aus `stale`), `FileExplorer.vue` zeigt
      bei Offline einen Indikator-Banner („Offline – zeige
      zwischengespeicherte Daten", Icon `cloud_off`, i18n `offline`/
      `offlineHint` en+de). Cache-Namespace inkludiert Account + gebrowsten
      Benutzer (kein Kollidieren zwischen Konten/Impersonation).
      Verifikation: `cargo fmt --check`, `cargo clippy -D warnings`,
      `cargo test`, `npm run build` grün.
### Review 2026-08-16 (N9)

- [x] **N9 (Doku/UX, minor):** `register_user` (`commands.rs:156-253`) speichert
      das echte Kontopasswort als Keyring-Token (`save_token`, Z. 245), während
      der Login-Flow ein App-Passwort erwartet. Passwortwechsel macht das Token
      ungültig. Fix: `docs/en/getting-started.md` + `docs/de/getting-started.md`
      (neuer Abschnitt „Registering a new account" / „Neues Konto registrieren"),
      `docs/en/security.md` + `docs/de/security.md` (Abschnitt
      „Registration password = app password" / „Registrierungs-Passwort =
      App-Passwort") und der i18n-`initHint`-Text (en + de) stellen jetzt klar,
      dass das Registrierungs-Passwort dauerhaft das App-Passwort ist und ein
      Passwortwechsel das gespeicherte Token ungültig macht (Konto muss entfernt
      und neu hinzugefügt werden).
### Review 2026-08-16 (P11/N8)

- [x] **P11/N8 (Perf, minor):** `ensure_collection` wurde doppelt pro Sync-Pass
      ausgeführt — `run_all` (`sync.rs:1106-1107`) und nochmals `run_pass`
      (`sync.rs:738`) → unnötige MKCOL-Requests auf jedem Tick. Fix: Der Aufruf
      in `run_all` wurde entfernt; `run_pass` (einziger Aufrufer ist `run_all`)
      übernimmt das `ensure_collection` weiterhin vor dem Pass. Verifikation:
      `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings` grün,
      `cargo test` 49 passed.
### Review 2026-08-16 (Q9)

- [x] **Q9 (Bug, Datenverlust-Risiko, mittel):** Upload überschrieb eine
      existierende Zieldatei still: `webdav_upload_file` sendete einen
      ungeprüften PUT (`put_file_as`, `webdav.rs:135-146`), `uploadFiles`
      (`FileExplorer.vue:123-142`) prüfte nicht und meldete nur „Uploaded.".
      Fix: Neuer Existenz-Check `webdav::exists` (PROPFIND, Depth 0) in
      `nextcloud/webdav.rs`; `webdav_upload_file`, `upload_tree` und
      `webdav_upload_local_paths` in `commands.rs` prüfen vor dem PUT und
      liefern den neuen `AppError::TargetExists` (Code `target_exists`,
      Serialize-Test in `error.rs`). `overwrite`-Parameter (Tauri-Command +
      `src/lib/ipc.ts` + `src/stores/files.ts`) erlaubt bewusstes Ersetzen.
      UI-Confirm in `FileExplorer.vue` (`uploadFiles` pro Datei mit
      `uploadOverwriteConfirm`, `dropUpload` pauschal mit
      `uploadOverwriteAllConfirm`; `uploadSkipped` beim Ablehnen). i18n
      en/de ergänzt (`errTargetExists` in `ERROR_CODE_KEYS`). Sync-Engine
      bleibt unverändert (darf weiter überschreiben). Verifikation:
      `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test`
      (50 passed), `npm run build` grün.
### Review 2026-08-16 (N11)

- [x] **N11 (Bug, minor):** `webdav_rename` (`commands.rs:819-842`) akzeptierte
      `/` im `new_name` → „Rename" wurde still zu einem Move in einen
      Unterordner. Fix: neue `validate_rename_name` (`commands.rs:403-414`)
      lehnt `/`, `.`, `..` und leere Namen direkt am `new_name` ab (nicht nur
      am zusammengesetzten Pfad, den `validate_dav_path` bereits prüfte);
      `webdav_rename` ruft sie vor `rename_new_path` auf. Unit-Tests
      `validate_rename_name_accepts_plain_names` /
      `validate_rename_name_rejects_slashes_and_dots` ergänzt. Verifikation:
      `cargo fmt --check` grün, `cargo clippy --all-targets -D warnings` grün,
      `cargo test` 51 passed / 0 failed.
### Review 2026-08-16 (N13)

- [x] **N13 (Cleanup, minor):** `api.accountActive` in `src/lib/ipc.ts:111` hatte
      keinen Frontend-Aufrufer (Dead Code). Fix: Komplette Kette entfernt —
      `accountActive`-Wrapper aus `src/lib/ipc.ts` (Z. 147) gestrichen,
      Backend-Command `account_active` (`commands.rs:278-281`) entfernt und
      aus der Command-Registry (`lib.rs:228`) deregistriert. Der
      `accounts`-Store leitet `active` bereits aus `accountList()` ab
      (`src/stores/accounts.ts:34`) und braucht den separaten Call nicht.
### Review 2026-08-16 (Q8)

- [x] **Q8 (Phase 4, Feature, minor):** Quota-Presets fehlten in
      `AdminPanel.vue` (nur freie MB/GB-Eingabe + „Unlimited"). Fix:
      Preset-Select (1/5/10 GB, unlimited, benutzerdefiniert) in der
      Quota-Verwaltung; Auswahl eines Presets setzt Wert/Einheit, manuelle
      Eingabe bleibt möglich und wechselt zurück auf „custom"; beim Laden
      eines Benutzers wird das passende Preset vorausgewählt. Verifikation:
      `npm run build` grün (vue-tsc + vite), `cargo fmt --check`, `cargo
      clippy --all-targets -- -D warnings` und `cargo test` grün.
### Review 2026-08-16 (Q3)

- [x] **Q3 (Phase 4, Feature, mittel):** Gruppen-Verwaltung umgesetzt.
      `ocs.rs` kennt jetzt die OCS-Gruppen-Endpunkte (`list_groups` mit
      Duplikat-Guard gegen Offset-ignorierende Server, `create_group` über
      `POST /cloud/groups`, `add_group_member` über
      `POST /cloud/groups/{id}/users`, `remove_group_member` über
      `DELETE /cloud/groups/{id}/users/{uid}`). In `commands.rs` sind
      `admin_list_groups`/`admin_create_group`/`admin_add_group_member`/
      `admin_remove_group_member` (alle mit Admin-Check) hinzugekommen und in
      `lib.rs` registriert; `src/lib/ipc.ts` hat die passenden Wrapper.
      `AdminPanel.vue` verwaltet Gruppen jetzt interaktiv: Gruppen der
      ausgewählten Person mit Entfernen-Button, Eingabefeld zum Hinzufügen in
      eine Gruppe und Button zum Anlegen einer neuen Gruppe (Toast-Feedback,
      i18n en/de).

### Review 2026-08-16 (U10)

- [x] **U10 (UX, minor):** Grid-View (`FileExplorer.vue:495-532`): Single-Click
      auf eine Kachel wählte nichts aus (nur die Checkbox), Download/Link/
      Delete fehlten pro Kachel (nur Open/Rename), kein Hover-Preview.
      Fix umgesetzt: Single-Click auf eine Kachel toggelt jetzt die Auswahl
      (Google-Drive-Verhalten), Doppelklick öffnet weiterhin. Alle Aktionen
      (Open, Download nur für Dateien, Link, Rename, Delete) liegen jetzt in
      einem Hover-Overlay (`group-hover`-Overlay über der Kachel mit
      Icon-Buttons). Der Link-Button respektiert den Share-Status
      (done → erneut kopieren statt neuen Share erzeugen). Hover-Preview:
      Titel-Tooltip zeigt Name, Größe, Änderungsdatum und resource/part-Status
      (`entryPreview`). Rechtsklick wählt die Kachel zusätzlich an (ohne eine
      bestehende Mehrfachauswahl zu verwerfen, wenn sie die Kachel enthält).
      Verifikation: `npm run build` grün (vue-tsc + vite).

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
