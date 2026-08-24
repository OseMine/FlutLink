# Archiv (erledigt)

Alle erledigten Aufgaben aus `todo.md`, sortiert nach Review/Lauf.


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
