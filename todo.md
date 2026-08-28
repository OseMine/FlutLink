# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.
Am 2026-08-25 sind zusätzlich die kompletten Review-Abschnitte der Läufe
17–19 (L17-*/L19-*/CP-* — allesamt im Code umgesetzt) nach
`archived-todo.md` verschoben; offen blieben nur „Desktop-JVM: Token-Speicher
härten" und die Performance-Analyse. Am 2026-08-26 sind die Abschnitte der
Läufe 20 und 21 gefolgt (nahezu komplett umgesetzt, Reste unten geführt).

## Feature-Ideen und Verbesserungsvorschläge (2026-08-27)

Basierend auf der vollständigen Code-Review aller Backend- und Frontend-Dateien.
Sortiert nach Umsetzungsaufwand (klein → groß).

### Quick Wins (klein, 1–2 Tage)

- [x] **Suche: `d:eq` → `d:contains`** — `webdav.rs:202-205` nutzt den
      XML-Operator `d:eq` für exakten Name-Match; `d:contains` oder `d:like`
      würde echte Teilstring-Suche ermöglichen. Betroffen:
      `search_request_body()`. (#401)
- [x] **Admin-Panel: Debounce für Suche** — Jeder Tastendruck bei
      `AdminUserList.vue` löst sofortigen `admin_list_users`-Aufruf aus.
      Ein 300ms-Debounce (wie in FileExplorer `searchFiles`) reduziert die
      Serverlast bei schneller Eingabe. (#398)
- [x] **i18n: `retry`-Key wird nirgends genutzt** — `i18n.ts:334/675`
      definiert `retry` (en+de), aber kein Component nutzt ihn. Network-
      Error-Toasts könnten einen Retry-Button erhalten (`ipc.ts:186-202`). (#399)
- [x] **Bulk-Upload: Fortschrittsanzeige pro Datei** — `transfer_progress()`
      in `commands.rs:868-897` wird für Single-File-Uploads genutzt, aber
      `webdav_upload_local_paths` emittiert kein `file://progress`-Event
      pro Datei (nur `upload_tree` für rekursive Uploads). Für den
      nicht-rekursiven Datei-Zweig (`commands.rs:1157-1179`) fehlt der
      Progress-Callback. (#403)
- [x] **CLI: `--download` und `--list`-Befehle** — Aktuell gibt es nur
      `--sync`, `--path`, `--url`, `--tray` (`lib.rs:148-192`). Headless-
      Nutzung (z.B. Skripting) könnte `--download <remote> <local>` und
      `--list <path>` gebrauchen. (#400)

### Medium Features (3–7 Tage)

- [ ] **Selective Sync (`.flutlinkignore`)** — Sync-Engine (`sync.rs`)
      synchronisiert den gesamten Ordner. Ein Ignore-Mechanismus
      (ähnlich `.gitignore`) pro Sync-Ordner würde `node_modules/`, `*.tmp`
      etc. ausschließen. Erfordert: Filter in `list_local`/`plan_ops`,
      UI-Setting in `SyncPanel.vue`, Persistenz in `SyncFolder`. (#402)
- [ ] **Dateiversionen (Nextcloud Versions-API)** — OCS-Endpunkt
      `/apps/files_versions/` anzeigen, ältere Versionen herunterladen/
      wiederherstellen. Neue Komponente `VersionDialog.vue` + IPC-Commands
      `webdav_list_versions`, `webdav_restore_version`. (#404 — Low feasibility)
- [x] **Freigabe-Benachrichtigungen** — Tauri Notification-Plugin wird
      bereits initialisiert (`tauri_plugin_notification::init()` in
      `lib.rs:203`), aber nur für Update-Meldungen genutzt. Ein periodically
      Check (ähnlich `refresh_admin_flags`) könnte neue Freigaben erkennen. (#410)
- [x] **Datei-Schnellvorschau (Quick Look)** — Leertaste-Taste → Overlay-
      Vorschau für Bilder/PDFs/Texte. Die `preview`-API (`webdav.rs:693-739`)
      liefert bereits Thumbnails; eine erweiterte Vorschau (höhere Auflösung,
      Inline-Renderer) wäre eine natürliche Erweiterung. (#405)
- [x] **Kopieren/Verschieben zwischen Ordnern** — WebDAV COPY/MOVE über
      neue IPC-Commands `webdav_copy`, `webdav_move`. Aktuell gibt es nur
      `webdav_rename` (`commands.rs:1241-1265`) und Upload/Download. (#411)
- [x] **Share-Editing (Passwort, Ablauf, Berechtigungen)** — OCS
      `PUT /shares/{id}` ermöglicht nachträgliche Änderungen. Aktuell gibt
      es nur Create/Delete (`commands.rs:514-586`). Neue Commands
      `webdav_edit_share` + erweitertes `ShareDialog.vue`. (#406)
- [x] **Quota-Warnung (Desktop-Notification)** — `account_storage`
      (`commands.rs:464-488`) lädt den Quota-Wert bereits. Ein periodischer
      Check (z.B. alle 30 Min.) mit Schwellwert >90% könnte eine
      Notification emittieren. (#413)
- [x] **Sync-Protokoll / Historie** — Die Journal-Daten (`sync.rs`
      `Journal`/`JournalEntry`) existieren bereits. Eine UI-Ansicht
      (letzte Sync-Aktionen, Konflikte) wäre ein leichtes Add-on. (#407)
- [x] **Ordner-Lesezeichen** — Schnellzugriff auf häufig besuchte Ordner.
      Persistenz im `ui.ts` Store (wie `filesView`), Anzeige im
      `EntryList.vue` Sidebar-Bereich. (#416)
- [x] **Globale Tastenkürzel** — Strg/Cmd+F (Suche), Strg/Cmd+N (Ordner),
      Entf (Löschen), F5 (Refresh). Die Escape-Stack-Infrastruktur
      (`src/lib/escape.ts`) existiert bereits. (#408)
- [x] **Share-Link-Vorschau mit QR-Code** — Beim Erstellen eines Share-Links:
      Vorschau + Copy-Button + optionaler QR-Code (Canvas-basiert). (#409/#423)

### Large Features (1–3 Wochen)

- [ ] **Offline-Bearbeitung mit Conflict-Resolution-UI** — Bei Konflikten
      (beide Seiten geändert): Inline-Diff-Ansicht (Textdateien) oder
      „meine Version / Server-Version / Beide behalten". Die Sync-Engine
      (`sync.rs`) erzeugt bereits Konflikt-Kopien; eine UI dafür fehlt. (#417 — Major)
- [ ] **Virtuelle Dateisystem-Integration (VFS)** — On-Demand-Dateizugriff
      via FUSE/WinFSP. Desktop-only (Plattform-Gründe, s. `kmp/README.md`).
      Big-Picture-Feature, erfordert native Integration pro Plattform. (#414 — Infeasible)
- [ ] **WebSocket/SSE für Live-Updates** — Statt polling-basiertem Refresh
      (aktuell: `listen("accounts-changed")` / `listen("sync-status")`):
      Server-seitige Echtzeit-Events für Dateiänderungen, Shares, Admin-
      Aktionen. (#418 — Infeasible)
- [ ] **2FA-Unterstützung (TOTP/WebAuthn)** — Nextcloud unterstützt 2FA;
      aktuell wird nur App-Passwort genutzt. Erfordert OAuth2-Flow oder
      erweiterte App-Passwort-Generierung. (#412 — Infeasible)
- [ ] **Automatisches Token-Rotieren** — Periodisches Erneuern des
      App-Passworts über die Nextcloud Security-API. (#415)
- [ ] **Gruppen-Bulk-Verwaltung** — Mehrere Benutzer gleichzeitig einer
      Gruppe zuweisen/entfernen. Aktuell: Einzel-Aktionen
      (`commands.rs:1574-1598`). (#425)
- [x] **Französisch / Spanisch als weitere Sprachen** — Tauri-User-Base
      international; die i18n-Infrastruktur (`src/lib/i18n.ts`) ist
      erweiterbar. (#419)
- [ ] **Tauri Updater-Plugin als Fallback** — Aktuell: eigene GitHub-API-
      Abfrage (`updater.rs`). Das Tauri Updater-Plugin bietet
      Signaturverifikation out-of-the-box.
- [ ] **Admin-Aktivitäts-Log** — Nextcloud Activity-API
      (`/ocs/v2.php/activity/events`) für Benutzer-Aktivitäten anzeigen. (#420)

### UI / UX Verbesserungen

- [x] **Datei-Sync-Status in der Dateiliste** — Icon/Label für lokale
      Dateien die Teil eines Sync-Ordners sind (synced, pending, conflict). (#421)
- [ ] **Drag & Drop zwischen Accounts** — Dateien vom einen Konto auf das
      andere ziehen (Multi-Account-Infrastruktur vorhanden). (#422)
- [x] **Share-Link mit QR-Code** — Beim Share-Erstellen: QR-Code generieren. (#409/#423)
- [x] **Passwort-Stärke-Anzeige** — Visuelle Stärkeanzeige beim Share-
      Passwort (Pattern existiert bereits im Admin-Panel). (#424)
- [x] **Admin: Kontingent-Warnungen im Panel** — Visualisierung wenn
      Quota >90% (Progress Bar existiert bereits in `QuotaEditor.vue`). (#426)
- [x] **Datei-Historie (zuletzt geöffnet)** — Basierend auf
      `open_cache_dir`-Aktionen eine „Zuletzt geöffnete Dateien"-Liste. (#427)
- [x] **System-Tray: Quick-Actions** — Sync auslösen, Uploads pausieren,
      Online/Offline-Status im Tray-Kontextmenü. (#428)

## Review 2026-08-28 (Lauf 25, Fokus KMP Mobile UI — neue Befunde)

Gegenstand: die komplette KMP-Mobile-UI (`kmp/shared/src/commonMain/kotlin/.../ui/`,
~6.000 Zeilen in 22 Dateien: `FlutLinkRoot.kt`, `navigation/AppNavigation.kt`,
`HomeScreen.kt`, `files/FilesScreen.kt` (1.166 Z.), `admin/AdminScreen.kt`,
`guest/GuestScreen.kt`, `settings/SettingsScreen.kt` + ViewModels +
`composeResources/values{,;-de}/strings.xml`) gegen die Desktop-Features
(`src/`, IPC-Commands, `commands.rs`, `ocs.rs`). Auftrag: UI „looks off and
not clean", fehlende Tabs, fehlende Desktop-Admin-Features auf Mobile
abgleichen. Zusätzlich der Standard-Bereich (IPC/State/CI) und die
Nachprüfung der L24-Befunde gegen HEAD `3d55fbb`.

Verifikation: In dieser CI-Umgebung nicht vollständig ausführbar — `cargo
clippy`/`cargo test` scheitern am fehlenden System-Toolchain-paket `glib-2.0`
(pkg-config), `npm run build` an fehlendem `node_modules` (`vue-tsc: not
found`). **ABER:** `cargo fmt --check` läuft und schlägt **auf HEAD weiterhin
fehl** (Diff in `commands.rs:853` und `sync.rs:1170/1177`) — L24-F1 ist unverändert
verifiziert (siehe Nachprüfung unten). Die KMP-Befunde sind reine
Quelltext-Verifikation (README des KMP-Moduls: Pull-Test `:shared:build`
nur auf macOS/Xcode für iOS möglich; Android-Gradle-Build hier nicht ausgeführt).

### Neu gefunden (Fokus KMP Mobile UI)

- [ ] **KMP-F1 (Feature-Lücke, hoch — direkt aus dem Auftrag): Mobile Admin
      fehlt die komplette `admin_edit_user`-Funktionalität des Desktops
      (E-Mail / DisplayName / Passwort).** `AdminViewModel.kt` (230 Z.,
      komplett gelesen) kennt nur `createUser`/`deleteUser`/`setQuota`/
      `setEnabled`/`addToGroup`/`removeFromGroup`/`createGroup`; `AdminScreen.kt`
      bietet dafür maximal Quota-/Gruppen-/Enable-Dropdowns. Der Desktop kann
      über `admin_edit_user` (`commands.rs:1781`, Whitelist `ADMIN_EDIT_KEYS`:
      displayname/email/password/quota/language/locale/enabled) Nutzerdaten
      editieren (`AdminUserDetails.vue`, 211 Z.). Die Mobile-OCS-API bietet das
      bereits an — `FlutCloudOcs.kt:76 updateUser(session, userId, key, value)` —
      wird aber nur für `quota`/`enabled` genutzt (`setUserQuota` :86,
      `setEnabled` in `AdminViewModel.kt:166`). Fix: `editUser()`-Methoden +
      „Details"-Dialog (E-Mail/DisplayName/Passwort) im `AdminScreen`, damit
      alle Desktop-Admin-Features auch auf Mobile vorhanden sind.
- [ ] **KMP-F2 (Bug, mittel): Grid-Ansicht rendert keinerlei Aktionen —
      `EntryGridItem` deklariert `menuOpen` + 6 Callbacks, nutzt sie aber nie.**
      `FilesScreen.kt:806-899`: Die Parameter `onDownload`/`onShareFile`/
      `onRename`/`onShareLink`/`onDelete`/`onJumpToPaired` und der State-
      `menuOpen` (Zeile 821) werden akzeptiert und von der Aufrufstelle
      (`FilesScreen.kt:428-442`) gefüttert, aber im Grid-Item nie gezeichnet —
      ein `DropdownMenu` existiert nicht. Grid-Einträge sind dadurch nur
      „öffnen" + Long-Press-Select; Download/Share/Löschen sind im Grid-Modus
      unerreichbar (die Desktop-Grid-Hover-Buttons in `EntryList.vue:275-317`
      haben hier kein Gegenstück). Fix: Ellipsis-`DropdownMenu` (wie
      `EntryRow`, Zeile 745+) ins Grid-Item einbauen oder Callbacks/State
      entfernen.
- [ ] **KMP-F3 (UX-Limit, mittel): Admin-Userliste ist ohne Suchbegriff leer —
      es gibt keinen „Alle anzeigen"-Pfad.** `AdminViewModel.loadUsers()`
      (`AdminViewModel.kt:48-58`) returned bei leerem `search` früh und leert
      `users`; `AdminScreen.kt:138-142` (`LaunchedEffect(search)`) ruft bei
      leerem Feld `clearSearch()`. Der Desktop-`admin_list_users`
      (`commands.rs:1638`) erlaubt leere Suche („everything"). Auf Mobile muss
      ein Admin erst mindestens einen Buchstaben tippen, um irgendeinen User zu
      sehen. Fix: leere Suche = erste Seite laden (limit 200), nicht leeren.
- [ ] **KMP-F4 (Race, minor): `loadUsers` vs. `loadMore` teilen `offset`/
      `users` unsynchronisiert.** `AdminViewModel.kt:78-94` startet
      `loadPage(append=true)` mit dem gemeinsamen `offset`, während ein neuer
      `LaunchedEffect(search)`-Zyklus `loadUsers` → `loadPage(append=false)`
      anstoßen kann; eine langsame loadMore-Antwort hängt ihren Block danach
      (leere/doppelte Seiten), und `createUser`/`setQuota`/`setEnabled`
      (Zeilen 114/148/167) rufen `loadUsers()` auf, was bei leerem Suchfeld
      die gerade geleerte Liste erneut leert. Fix: Sequenz-/Generations-Guard
      pro Request (Desktop-Pattern aus `AdminPanel.vue saveField`/`selectSeq`,
      Review L22-F3) + Abbruch untergeordneter Lade-Coroutines.
- [ ] **KMP-F5 (Cleanup, minor): Unlokalisierte UI-Literale.** `"Files"` in
      `buildBreadcrumbSegments` (`FilesScreen.kt:539`) und `"List"`/`"Grid"`
      im `ViewMode`-Enum (`FilesScreen.kt:162-163`) sind hart kodiert; der
      Desktop lokalisiert beides (`viewList`/`viewGrid`, `files`). Fix:
      Ressourcen-Keys statt String-Literale.
- [ ] **KMP-F6 (UX, minor): Doppelte App-Chrome — Desktop-Header-Reproduktion
      auf Mobile.** `HomeScreen.kt:77-156` rendert ein Desktop-Style-Surface
      (Logo-Zeile + Tab-Zeile mit Unterstrich-`Box`), und jeder Screen
      (FilesScreen `TopAppBar`, AdminScreen `TopAppBar`, SettingsScreen
      `TopAppBar`) fügt einen zweiten Header darunter ein — zwei gestapelte
      Title-Bars („looks off"). Mobile-Konvention: echte Material-3-`
      NavigationBar` (Bottom-Tabs) + eine einzige `TopAppBar` pro Screen;
      derzeit ist die Tab-Zeile zudem kein `NavigationBar`, sondern ein
      handgebauter Desktop-Tabstrip ohne `role`/Semantik.
- [ ] **KMP-F7 (Konsistenz, minor): Nicht-Admins sehen den Admin-Tab gar
      nicht; Desktop zeigt ihn als gesperrt mit Hinweis.** `HomeScreen.kt:112`
      `val visible = tab != Tab.Admin || isAdmin` blendet Admin komplett aus;
      im Desktop rendert `App.vue` `navItems` den Admin-Tab als gesperrt
      (Lock-Icon, disabled, `adminLockedTitle`/`adminLockedText`). Fix: gleiche
      „lock"-Darstellung auf Mobile, damit die Tab-Leiste konsistent bleibt.
- [ ] **KMP-F8 (Doku, minor): README/Archiv beschreiben `iosMain` als
      „Placeholder-UI", das ist veraltet.** `kmp/README.md:38-39`; tatsächlich
      hostet `kmp/shared/src/iosMain/kotlin/com/flutcloud/flutlink/Main.kt`
      die volle geteilte Compose-UI via `FlutLinkRoot` (feat-commit `2dce6e1`).
- [ ] **KMP-F9 (Feature-Lücke, mittel — Desktop-Parität): Mobile fehlen
      Copy/Move, QR-Code, QuickLook** — Desktop `webdav_copy`/`webdav_move`
      (#411, `commands.rs:1383/1410`), `QrCode.vue` (#409) und `QuickLook.vue`
      (#405) haben kein Mobile-Pendant: weder eine `copy`/`move`-Route in
      `FlutCloudApi`/`FilesViewModel.kt` noch Clipboard-/QR-Zugriff auf den
      Share-Link (`link_created`-Toast ist die einzige Rückgabe,
      `FilesScreen.kt:283-288`) noch eine Vollbild-Preview. Reihenfolge nach
      KMP-F1/F2 einplanen.

Keine neuen Befunde im Desktop/Kern dieses Laufs: IPC-Registry
(`lib.rs:364-427` vs. `ipc.ts`), `sync_log_list`/`sync_log_clear`
nachträglich registriert (Kommentar in L24-F3), Keyring, Fehler-
Serialisierung, WebDAV/OCS-Layer, Guest-Backend, Sync-Engine — alle
unverändert zu Lauf 24.

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `3d55fbb`)

Die L24-Befunde wurden gegen den neuen HEAD nachgeprüft; nur zwei sind
teilweise/verbessert, keiner komplett abgeschlossen:

- **L24-F1** (clippy `redundant_closure` `sync.rs:1332` + fmt-Diff
  `sync.rs:1170/1177`, `commands.rs:853`): **unverändert offen** —
  `sync.rs:1332` hat weiter `.map_err(|e| AppError::Json(e))`, und
  `cargo fmt --check` schlägt auf HEAD mit exakt diesen Diffs fehl
  (frisch verifiziert).
- **L24-F2** (Share-Notify-Schalter erreicht Backend nie): **unverändert
  offen** — `ui.ts:148-151` schreibt weiterhin nur `localStorage`, kein
  `api.setShareNotify`-Aufruf (grep bestätigt: einziger Aufrufer ist
  `SettingsModal.vue:409` → `ui.setShareNotify`).
- **L24-F3** (Sync-Log): **teilweise umgesetzt** — Commit `3d55fbb` ergänzt
  `sync_log_list`/`sync_log_clear` (`commands.rs:854-872`), `SyncLog.vue`
  (184 Z.) und i18n-Keys (en/de/fr/es). **Der Append-Reihenfolgen-/Trunkierungs-
  Bug bleibt:** `load_sync_log` (`sync.rs:1337-1347`) reverset (neueste zuerst),
  `append_sync_log` (`sync.rs:1322-1334`) pusht ans Ende der
  neueste-zuerst-Liste und drainet beim Überlauf vorn → verwirft die
  neuesten Einträge; der persistierte Stand wird bei jedem Append invertiert
  (drei Appends → `[E2, E1, E3]`).
- **L24-F4…L24-N7** (Settings-Lost-Update, Retry-Idempotenz, QuickLook-Race,
  Share-Edit-Permissions/Expiry, Copy/Move-Normalisierung, Sync-Log-
  Write-Amplification, settings.json-Quarantäne, CLI-Validierung,
  history-clear-Race, QuickLook-Ränder, Thumbnail-MIME, Impersonation-Toast):
  **alle unverändert offen** — die einzigen Commits seit Lauf 24 sind
  `ee8c360` (todo.md) und `3d55fbb` (Sync-Log, oben eingeordnet).

Zu verschieben: keine — kein L24-/L25-Befund ist vollständig erledigt;
die beiden Teilfortschritte (L24-F3a) sind oben dokumentiert.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe verboten). `git log`
seit Lauf 24 (`197df7f..HEAD`) enthält genau zwei Commits: `3d55fbb`
(Sync-Log-Listing/Clear, PR-Merge `7fe8879` #429 — referenziert **keine**
neue Issue-Nummer, nur #429 als PR) und `ee8c360` (todo.md-Update des
Review-Workflows). Die umgesetzte Hälfte von L24-F3 entspricht dem Issue
#407 (Sync-Protokoll). Es sind damit keine neuen Issue-Referenzen
aufgetaucht, die die offenen L24-Befunde (F1/F2/F3b/F4–N7) schließen;
die L25-KMP-Befunde (v.a. KMP-F1, die im Auftrag geforderte
Desktop-Admin-Parität) sollten vom `opencode-todo-issues`-Workflow beim
nächsten Lauf als Issues erfasst werden. Ob parallel weitere offene Issues
entstanden sind oder veralten, ist hier nicht feststellbar.

## Review 2026-08-28 (Lauf 24, Fokus Feature-Reihe #399–#428 — neue Befunde)

Gegenstand: die seit Lauf 23 (HEAD `1e82994`) eingelandeten Feature-Commits
(`197df7f`…`26612a1`, 24 Commits: Sync-Log, Sync-Synced-Paths, Share-
Notifications, Tastenkürzel, QrCode/QuickLook, Copy/Move, Datei-Historie,
Retry-Mechanismus, CLI `--download`/`--list`, Install-Skripte, KMP-UI-
Overhaul) plus die Standard-Bereiche (IPC-Commands, WebDAV/OCS, Keyring,
Fehler-/State-Management, CI) und die Nachprüfung der offenen Punkte gegen
HEAD `197df7f`.

Verifikation frisch ausgeführt (Tauri-Linux-Systemdeps nachinstalliert):
`cargo test --manifest-path src-tauri/Cargo.toml` → **116 passed / 0 failed**
(+9 gegenüber Lauf 23: Sync-Log/Share-Notify/File-History-Tests);
`npm run build` (vue-tsc + vite) **grün** (Haupt-Chunk ~279 kB / gzip 91 kB).
**ABER:** `cargo clippy --all-targets -- -D warnings` und
`cargo fmt --all --check` **schlagen auf HEAD fehl** (siehe L24-F1 unten) —
die CI würde aktuell rot laufen.

### Neu gefunden

- [ ] **L24-F1 (Bug, hoch / CI-Blocker): `cargo clippy` + `cargo fmt` sind
      auf HEAD nicht grün.** (a) clippy `redundant_closure`:
      `sync.rs:1332` `.map_err(|e| AppError::Json(e))` → `.map_err(AppError::Json)`.
      (b) `cargo fmt --check`: Formatdiff in `sync.rs:1177` (die beiden
      `Move*.Conflict`-Match-Arme) und `sync.rs:1328-1334` (der
      `serde_json::to_string_pretty`-Aufruf). Beides stammt aus dem neuen
      Sync-Log-Code (`197df7f`). Ohne Fix scheitert die Rust-CI
      (`clippy`/`fmt`-Job) und jeder Release-Build. Fix: `cargo fmt` ausführen
      + die Closure vereinfachen.
- [ ] **L24-F2 (Bug, hoch): Der Share-Notification-Schalter ist rein
      dekorativ — er erreicht das Backend nie.** `SettingsModal.vue:409`
      ruft nur `ui.setShareNotify(...)` auf; das schreibt lediglich
      `localStorage["flutlink.shareNotify"]` (`ui.ts:148-149`) und ruft
      **nie** `api.setShareNotify` (der Wrapper `ipc.ts:360` hat keinerlei
      Aufrufer, per grep verifiziert). Das Backend-Flag
      `settings.share_notify_enabled` (default `true`), das in
      `check_share_notifications` (`settings.rs:82`) das eigentliche Gate
      bildet, bleibt damit unverändert `true` — der User kann die
      Notifications **nicht** abschalten, egal wie der Schalter steht (und
      die beiden Flags können auseinanderdriften). Fix: in
      `ui.setShareNotify` zusätzlich `await api.setShareNotify(value)` und die
      Backend-Einstellung beim Start lesbar machen (`get_settings`-Command),
      damit der Schalter den echten Zustand anzeigt.
- [ ] **L24-F3 (Bug, mittel — Sync-Log doppelt defekt): Die
      `append_sync_log`-Trunkierung wirft die falschen Einträge weg und
      verkantet die Reihenfolge; und das Feature ist komplett unerreichbar.**
      `load_sync_log` (`sync.rs:1337-1347`) kehrt die Liste um (neueste zuerst);
      `append_sync_log` (`sync.rs:1322-1334`) pusht den neuen Eintrag ans
      **Ende** dieser neueste-zuerst-Liste und drainet beim Überlauf
      (`MAX_SYNC_LOG_ENTRIES=200`) **vorn** — es verwirft also die neuesten
      und behält die ältesten, exakt das Gegenteil von „letzte N behalten".
      Zusätzlich wird der persistierte Stand bei jedem Append umgekehrt neu
      geschrieben (drei Appends → `[E2, E1, E3]`). Außerdem existiert **kein**
      `#[tauri::command]`, das `load_sync_log` ausstellt (weder in `commands.rs`
      noch in `lib.rs` `generate_handler!` noch in `ipc.ts`) — die Log-Daten
      sind toter, nur-schreibender Bestand. Fix: Speicherung beibehalten
      append-Order (alt→neu), beim UI-Serving erst reversen; und einen
      `sync_log`-Command + Wrapper + SyncPanel-Ansicht ergänzen (#407).
- [ ] **L24-F4 (Race, mittel): Lost-Update auf `settings.json` zwischen
      Sync-Worker und `set_share_notify`.** `SyncEngine::run_all`
      (`sync.rs:1786-1788`) macht `load` → `check_share_notifications(...)`
      → `save` mit einem `await` (Netzwerk-I/O über alle Konten) dazwischen;
      `set_share_notify` (`commands.rs:848-852`) macht eine eigene
      separate read-modify-write. Ein Toggle während des Worker-Fensters wird
      von dessen veralteter Kopie rückgängig gemacht (und umgekehrt wird
      `share_seen`-Fortschritt beim Toggle überschrieben). Dazu kollidieren
      beide Schreiber auf denselben festen Temp-Namen `tmp-{pid}`
      (`persist.rs:15`): `File::create` des einen truncat e die offene Datei
      des anderen, der verlierende rename kann eine leere Datei einrollen.
      Fix: Settings-Zugriff hinter `Mutex<AppSettings>` in `AppState`
      serialisieren (load→modify→save als kritischer Abschnitt) und
      `persist.rs` pro Write eindeutigen Temp-Namen (uuid/counter+pid) nutzen
      (wie `webdav.rs` es beim Download bereits tut).
- [ ] **L24-F5 (Robustheit, mittel): Der Retry-Mechanismus verwirft das
      Ergebnis und ist nicht idempotenz-sicher.** `retryLast()`
      (`ipc.ts:34-45`) spielt `invoke(failed.cmd, failed.args)` nach, verwirft
      aber den Rückgabewert — der ursprüngliche Aufrufer (z.B. `files.refresh()
      → webdav_list`) hat seinen Fehlerzustand bereits gesetzt und bekommt das
      frische Ergebnis nie; „Retry erfolgreich" hinterlässt die UI im alten
      fehlerhaften Zustand. Zudem puffert `tauri()` (`ipc.ts:16-30`) **jede**
      `http`-Klasse, auch nicht-idempotente Mutationen (`webdav_delete`,
      `webdav_copy/move`, `webdav_create_share/update_share`, Uploads): ein
      Retry nach verlorener Antwort wendet die Mutation doppelt an. Fix: Das
      Retry-Ergebnis an den Original-Caller zurückleiten (bzw. nach Erfolg
      den betroffenen View-Store refreshen) und Retry nur für idempotente
      Lese-Commands anbieten (oder Caller stellt `idempotent`-Flag).
- [ ] **L24-F6 (Race, mittel): QuickLook zeigt beim schnellen Blättern das
      Thumbnail des vorherigen Eintrags.** `refreshQuickLookImage`
      (`FileExplorer.vue:497-507`) captured `entry` beim Aufruf und weist
      das `await files.getThumbnail(entry.path)`-Ergebnis später dem einzigen
      `quickLookImage`-Ref zu. Blättert man schnell A→B (Prev/Next in
      `quickLookStep`, `:521-527`), überschreibt As späte Antwort
      `quickLookImage` während B angezeigt wird. Fix: Zuweisung guarden
      (`if (entry.path === quickLookEntry.value?.path) …`) oder Request-
      Generationszähler.
- [ ] **L24-F7 (Bug, mittel): Share-Bearbeitung setzt bei jedem Edit die
      Berechtigungen zurück und kann ein Ablaufdatum nie entfernen.**
      `submitEdit` (`ShareDialog.vue:98-107`) sendet `publicUpload` (Boolean)
      **immer** mit; das Backend (`commands.rs:618`, `public_upload.map(…15/1)`)
      schreibt deshalb bei jedem Edit `permissions=15` oder `1` und wäscht
      damit zwischenliegende Rechte (z.B. create-only, 2–14) zugunsten von
      read-only aus. Und `expireDate: editExpiry.value || undefined`
      (`ShareDialog.vue:103`) wandelt ein geleertes Datum (`""`, falsy) zu
      `undefined` — der Backend-Pfad „leeres expireDate löscht" wird nie
      erreicht, Sperren lassen sich so nicht entfernen. Fix: `publicUpload`
      nur senden, wenn sich der Wert geändert hat (sonst Verhalten „nicht
      anfassen"); `expireDate: editExpiry.value === "" ? "" : (… )`.
- [ ] **L24-F8 (Robustheit, mittel): Copy/Move-Pfadkomposition ohne
      Normalisierung.** `move_dest_path` (`commands.rs:1349-1359`) fügt
      `"{dest_folder}/{name}"` wörtlich an: ein `dest_folder` mit nachgestelltem
      `/` (das Dialogfeld ist editierbar) ergibt `"/B//name"`, ein `source`
      mit `/` ein leeres `name`/`"/B/"` — beides lässt `validate_writable_dav_path`
      (`commands.rs:658-668`, prüft nur absolute + kein `..`) durch, genauso wie
      das Verschieben eines Ordners in seinen eigenen Unterbaum (`/A`→`/A/A`).
      Fix: `dest_folder` trailendes `/` trimmen, leeren Namen ablehnen,
      `dest == source`/dest-in-source für Ordner verhindern; leere Segmente in
      `validate_dav_path` verbieten.
- [ ] **L24-N1 (Perf, mittel): Sync-Log wird pro geplantem Op komplett
      neu geschrieben (Write-Amplification).** `append_sync_log` wird je
      Plan-Op aufgerufen (`sync.rs:1217-1226`, bis zu `MAX_OPS_PER_PASS=200`
      pro Ordner) und macht bei jedem Aufruf `load` (ganze Datei) +
      `to_string_pretty` + `atomic_write` (ganze Datei) + `create_dir_all`.
      Dazu toter No-op-`match result { … }` mit lauter Unit-Armen
      (`sync.rs:1227-1231`). Fix: Log-Einträge im Pass sammeln und einmal
      flushen; No-op-Match entfernen.
- [ ] **L24-N2 (Robustheit, minor): Kaputtes `settings.json` wird still
      überschrieben statt einkarantäniert; erster Tick benachrichtigt für alle
      Bestandsshares.** `settings::load` (`settings.rs:56-66`) gibt bei
      korrumpierter Datei Defaults zurück, ohne die Datei zu quarantänen
      (anders als Journal/Persist-Pattern), und der Worker `save`t
      anschließend bedingungslos — die kaputte Datei verschwindet spurlos.
      Außerdem startet `share_seen` leer, sodass der **erste** Tick nach
      Fresh-Install bzw. gelöschter Datei für **jeden** existierenden Share
      eine Notification feuert (`settings.rs:88-112`); `seen.retain`
      (`:113`) ist O(n²) und leert `share_seen` bei temporär leerer Liste
      (→ Re-Notifications). Fix: Quarantäne beim Corrupt-Pfad, nur speichern
      wenn geändert, `share_seen` beim ersten Listing ohne Meldung seeden.
- [ ] **L24-N3 (Validierung, minor): Headless-CLI `--download`/`--list`
      umgehen die Pfadvalidierung aller IPC-Commands.** `lib.rs:247/284`
      reichen `remote`/`path` unvalidiert an `get_file`/`list` durch (kein
      `validate_dav_path`, `..`/leere Segmente möglich) und der Prozess
      beendet sich nach dem Ausdruck nicht (nur mit `--tray` wird das Fenster
      versteckt; kein Exit-Code). Fix: `validate_dav_path` in beiden CLI-
      Pfaden aufrufen; für Headless-`--download`/`--list` Fenster ausblenden
      bzw. nach Output beenden.
- [ ] **L24-N4 (Race, minor): `history::clear` vs. `record_open`.**
      `record_open` schreibt atomar (temp+rename), `clear` macht `remove_file`
      (`history.rs:87-93`). Eine Clear während eines in-flight Open kann das
      „geleerte" Journal per rename wiederbeleben. Beides bleibt unsynchronisiert
      (gleiche Klasse wie L24-F4). Fix: hinter denselben Lock / Clear entfernt
      auch verwaiste Temp-Dateien.
- [ ] **L24-N5 (Race, minor): QuickLook-Prev/Next-Buttons sind auch an den
      Rändern aktiv** (`FileExplorer.vue:1458-1459` nutzen
      `sortedEntries.length > 1` statt `quickLookIndex` an 0/letztem) und
      wrappen über das Modulo — Zustand wirkt falsch aktiviert. Fix:
      `canPrev = quickLookIndex > 0` / `canNext = quickLookIndex < len-1`
      (oder bewusst wrappen lassen und Labels anpassen).
- [ ] **L24-N6 (Security/Defense, minor): Thumbnail-`data:`-URL übernimmt
      den Server-Content-Type ungeprüft.** `commands.rs:921-925` baut
      `data:{content_type};base64,…` aus der Server-Antwort (SVG möglich) in
      ein `<img>` in QuickLook. Moderne Browser blockieren Skripte in
      `<img>`-SVG großteils, aber eine Mime-Whitelist (png/jpeg/webp, sonst
      Fallback) wäre robuster. (`webdav.rs:728-738` liefert den Typ.)
- [ ] **L24-N7 (UX/Konsistenz, minor): `ImpersonationBar.vue:28-33` zeigt bei
      leeren Such-Enter/Retry erneut einen Info-Toast** — bei schnellem
      Klicken wiederholte Infos. Hinweis lieber einmalig/inline statt Toast.

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs:389-406` registriert alle neuen Commands außer dem fehlenden
`sync_log` (L24-F3) — `file_history_list/clear`, `set_share_notify`,
`sync_synced_paths`, `webdav_copy/move/edit_share` korrekt; die TS-Wrapper in
`ipc.ts` (inkl. `webdavCopy`/`webdavMove` camelCase-Args) passen), Keyring
(`accounts.rs`), Fehler-Serialisierung (`error.rs` ↔ `ERROR_CODE_KEYS`
inkl. `flutcloud_app_too_old` aus L23-F2 — in den Feature-Commits nachgezogen),
Offline-Cache (`cache.rs`), WebDAV-Layer (`webdav.rs`: Impersonation, Chunked
v2, TOCTOU/If-Match — unverändert), OCS-Layer (`ocs.rs`:
`build_share_update_form` postet nur gesetzte Felder korrekt, Permission-Map
konsistent), Guest-Backend (`guest.rs`), Sync-Engine-Kern (`sync.rs`
fail-closed, SyncLog ohne Befund außer L24-F3/L24-N1), FlutCloud-only-Policy,
Updater, Tray/CLI (bis auf L24-N3), i18n (`shortcuts.ts`/QuickLook/QrCode/
MoveTarget i18n-Keys en+de vorhanden).

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `197df7f`)

Die im Lauf-23-Abschnitt als offen markierten Punkte sind überprüft:

- **L23-F1** (Complier-Fehler `Path::parent()` Result→Option): Mit Lauf-24
  umgesetzt — `cargo test` baut und läuft (116 passed), `cargo clippy`
  meldet nur noch das neue L24-F1. ✓ erledigt.
- **L23-F2** (`flutcloud_app_too_old`-Frontend-Mapping): Im Feature-Zweig
  nachgezogen (i18n `errFlutcloudAppTooOld` + `ERROR_CODE_KEYS`).
  ✓ erledigt (verifiziert in der Verifikation oben „Fehler-Serialisierung").
- **„Desktop-JVM: Token-Speicher härten"** (`## Offen` + Lauf 20/21): bleibt
  **offen** — `kmp/shared/src/jvmMain/.../FileKeyValueStorage.kt:14`
  dokumentiert die Keyring-Anbindung weiterhin als Follow-up.
- **„CI security gate auf v1.2.0"** (`## Offen`): bleibt offen — reine
  Vorab-Prüfung, ob die 7 AI-Befunde actionable sind; kein neuer Befund.
- **Performance-Analyse**: alle Punkte als umgesetzt markiert (v1.2.0).

Zu verschieben: Die im Abschnitt „Erledigt (2026-08-26, Review-Läufe 20–21)"
und oben stehenden L20–L23-Punkte sind bereits abgehakt; keine neuen
Abschnitte sind komplett abzuschließen — die L24-Befunde oben bleiben offen.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe verboten):
`git log 1e82994..HEAD` zeigt die 24 oben genannten Feature-Commits; die
Messages referenzieren Issue-Nummern **#399–#428** (die Items der
„Feature-Ideen"-Sektion). Viele davon sind im Code als umgesetzt verifiziert
(Retry #399, CLI #400, Suche `d:contains` #401, Quick-Look #405, Share-Editing
#406, Sync-Log #407, Kürzel #408/#423, Share-Notify #410, Copy/Move #411,
Quota-Warnung #413, Lesezeichen #416, Sprachen #419, Sync-Status #421,
QR-Code #423/#409, Passwort-Stärke #424, Quota-Warnung-Panel #426, Historie
#427, Tray #428) — die zugehörigen Issues wären zu schließen (hier nicht
prüfbar). Mehrere L24-Befunde betreffen genau diese neuen Features (F2=#410,
F3=#407, F5=#399, F7=#406, F8=#411) — hier noch nacharbeiten. Ob parallel
weitere offene Issues entstanden sind oder veralten, ist nicht feststellbar;
der `opencode-todo-issues`-Workflow sollte beim nächsten Lauf die L24-Befunde
oben als Issues erfassen.

## Review 2026-08-26 (Lauf 23, Release-v1.2.0-Vorabprüfung — neue Befunde)

Gegenstand: Vollständige Code-Review gegen HEAD `1e82994` (Merge PRs #395–397,
Admin-Panel-Split, i18n-Ergänzungen) zur Vorbereitung von Release v1.2.0.
Geprüft: IPC-Commands, WebDAV/OCS-Anbindung, Keyring, Fehler-/State-Management,
CI/Workflows, Admin-Panel-Refactoring, alle offenen L20–L22-Befunde.

Neu gefunden:

- [x] **L23-F1 (Bug, kritisch / Release-Blocker): `cargo test` und
      `cargo clippy` schlagen fehl — `PathBuf::parent()` gibt
      `Option<&Path>` zurück, wird aber als `Result` gematcht.**
      `open_cache_dir().parent()` in `commands.rs:760` und `:1333` wird mit
      `if let Ok(parent)` geparsed; `Path::parent()` gibt jedoch
      `Option<&Path>` zurück, nicht `Result`. Compiler-Fehler:
      `error[E0308]: mismatched types — expected Option<&Path>, found Result`.
      Eingeführt durch `ee2974f` (AES-256-GCM-Encryption). Fix: `Ok(parent)`
      → `Some(parent)` an beiden Stellen. Ohne Fix kein `cargo test`,
      `cargo clippy` oder Release-Build möglich.
- [x] **L23-F2 (Bug/UX, mittel): `flutcloud_app_too_old` fehlt im
      Frontend-Fehlercode-Mapping.** `AppError::FlutCloudAppTooOld`
      (`error.rs:77`) sendet den Code `"flutcloud_app_too_old"`, das
      `ERROR_CODE_KEYS`-Mapping (`i18n.ts:703-723`) kennt jedoch nur
      `"flutcloud_app_missing"`. Folge: Wenn `verify_guest_server`
      (`guest.rs:130-135`) die App als zu alt erkennt, zeigt das Frontend
      den generischen `"errUnknown"`-Text statt der spezifischen Meldung
      „FlutCloud-App zu alt für Gastzugriff". Fix: Eintrag
      `flutcloud_app_too_old: "errFlutcloudAppTooOld"` in `ERROR_CODE_KEYS`
      + i18n-Keys `errFlutcloudAppTooOld` in en + de.

### Nachprüfung offener Befunde (Lauf 22 + älter) gegen HEAD `1e82994`

Alle vier L22-Befunde sind seit dem letzten Lauf behoben (Commits `d188d41`,
`5040e9f`, `9b5acf0`, PRs #395–397):

- [x] **L22-F1** (Build-Fehler, toter Accent-Code): Behoben — `npm run build`
      (`vue-tsc --noEmit` + `vite build`) läuft fehlerfrei (3 s); toter
      Accent-Code (`applyAccent`/`resetAccent`/`accentValue`), `setAccentHue`,
      `App.vue`-Override und i18n-Keys sind vollständig entfernt.
- [x] **L22-F2** (Bulk-Upload-TOCTOU): Behoben — `upload_tree`
      (`commands.rs:1096-1108`) und Datei-Zweig von `webdav_upload_local_paths`
      (`commands.rs:1174-1187`) nutzen jetzt `put_file_params` mit
      `forbid_overwrite: !overwrite`.
- [x] **L22-F3** (saveField-Race): Behoben — `saveField`
      (`AdminPanel.vue:199`) captured `const seq = selectSeq` und prüft
      `seq !== selectSeq` nach jedem Await (`:215`, `:218`, `:226`); `saveEdits`
      (`:238-242`) reicht Selektions-Guard zusätzlich per `selected.value.id`
      weiter.
- [x] **L22-N1** (Toter `md-`-Carve-out in vite.config.ts): Behoben —
      `isCustomElement`/`md-`-Referenz vollständig entfernt.
- [x] **L20-N2** (guest `verify_guest_server` falscher Fehlercode): Behoben —
      eigener `AppError::FlutCloudAppTooOld` mit eigenem Text
      (`error.rs:167-174`), `guest.rs:134` nutzt ihn korrekt. (Nur das
      Frontend-Mapping fehlt noch, siehe L23-F2.)
- [x] **L20-N3** (PowerShell-Parse-Check nur für eine PS1): Behoben —
      `flutcloud.yml:96` nutzt jetzt `-Filter '*.ps1'` statt
      `-Filter 'install-flutcloud-app.ps1'`.
- [x] **L21-N4** (Monolith-Komponenten): Teilweise behoben — AdminPanel von
      ~640 auf 427 Zeilen zerlegt; drei Sub-Komponenten ausgegliedert
      (`AdminUserList.vue`: 75 Z., `AdminUserDetails.vue`: 211 Z.,
      `QuotaEditor.vue`: 153 Z.). `FileExplorer.vue` von ~1500 auf 1130 Z.
      reduziert; further splitting remains optional for future.

### Verifikation (HEAD `1e82994`)

| Prüfung                       | Ergebnis                |
|-------------------------------|-------------------------|
| `npm run build`               | **grün** (17 s)         |
| `cargo fmt --all --check`     | **grün**                |
| `cargo clippy --all-targets`  | **grün**                |
| `cargo test`                  | **grün** (107 passed / 0 failed) |

### Admin-Panel-UI-Review (implementiert seit Lauf 22)

Alle Items aus dem Admin-Panel-UI-Review-Sektion sind im Code umgesetzt:
`h-full`-Wrapper (`AdminPanel.vue:388`), Header-Struktur
(`AdminUserDetails.vue:53-81`), Quota-Editor-Redesign mit Progress Bar
(`QuotaEditor.vue:104-152`), Passwort-Bestätigung + Eye-Icon
(`AdminUserDetails.vue:116-152`), i18n-Keys (`confirmPassword`/`passwordsMismatch`
en+de vorhanden).

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs:241-295` deckungsgleich mit `ipc.ts`, alle typisiert), Keyring
(`accounts.rs` save/load/delete + Linux-Hint + Quarantäne/Atomic),
Fehler-Serialisierung (`error.rs::code()` ↔ `ERROR_CODE_KEYS` — bis auf
L23-F2 vollständig), Offline-Cache (`cache.rs` atomic write + Quarantäne),
WebDAV-Layer (Impersonation, Chunked-v2 mit Session-Cleanup, TOCTOU/If-Match),
OCS-Layer (`verify_share_owner`, Dedup-Pagination), Guest-Backend (`guest.rs`:
Locks-Read-Endpunkt, `FlutCloudAppTooOld`), Sync-Engine (`sync.rs`),
FlutCloud-only-Policy (`flutcloud.rs`), Tray/CLI (`lib.rs`), Updater,
Theme/i18n/Escape-Grundlagen, CI/Workflows (build/lint/kmp/release/flutcloud).

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

- [x] **L22-F1 (Bug, hoch): `npm run build` schlägt auf HEAD fehl — toter
      Accent-Code im SettingsModal verstößt gegen `noUnusedLocals`.**
      Bereinigt: Die Skript-Reste (`accentValue`, `applyAccent`, `resetAccent`,
      open-Watcher), die i18n-Keys (`accentColor`/`accentColorHint`/`accentReset`),
      `ui.setAccentHue` und der `App.vue`-Hue-Override waren bereits in einem
      vorherigen Commit entfernt. `npm run build` (`vue-tsc --noEmit` +
      `vite build`) läuft fehlerfrei; `cargo fmt --check` grün.
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
- [x] **L22-F2 (Bug/Konsistenz, mittel): Bulk-/Drag&Drop-Uploads fehlt der
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
- [x] **L22-F3 (Race/UX, minor): `saveField` im AdminPanel kann ungespeicherte
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
- [x] **L22-N1 (Cleanup, minor): Toter `md-`-Carve-out in vite.config.ts.**
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

- [x] **L20-N2 (weiter offen)**: `verify_guest_server` mappt „App zu alt
      für das Gast-Feature-Flag" weiterhin auf
      `AppError::FlutCloudAppMissing` (`guest.rs:130-135`), dessen Text
      (`error.rs:125-132`) einen Installationsfehler behauptet — obwohl
      `verify_server` die App zuvor erfolgreich erkannt hat. Fix: eigener
      Fehlercode/-text („FlutCloud-App zu alt für Gastzugriff").
      **Behoben**: Eigener `AppError::FlutCloudAppTooOld` mit passendem Text
      (`error.rs:167-174`, `guest.rs:134`). Nur das Frontend-Mapping fehlt
      noch (siehe L23-F2).
- [x] **L20-N3 (weiter offen)**: Der PowerShell-Parse-Check prüft weiterhin
      nur `install-flutcloud-app.ps1` (`.github/workflows/flutcloud.yml:94`,
      `-Filter 'install-flutcloud-app.ps1'`); der README-Einstiegspunkt
      `scripts/install-flutlink.ps1` bleibt ungeprüft. Fix:
      `-Filter '*.ps1'`.
      **Behoben**: `flutcloud.yml:96` nutzt jetzt `-Filter '*.ps1'`.
- [x] **L21-N4 (weiter offen)**: `FileExplorer.vue` ist weiterhin ein
      ~1500-Zeilen-Monolith (Toolbar/Breadcrumbs/Impersonation-Bar/Banner/
      Select-all/Transfer-Progress/Empty-States/Split-View/Kontextmenü/drei
      Dialoge plus ~850 Zeilen Script-Logik, `FileExplorer.vue:856-1494`);
      `AdminPanel.vue` ebenso (~640 Zeilen: Suchliste + Detailformular +
      Quota in einer Komponente). Der SaaS-Umbau hat beide nicht zerlegt —
      Kandidaten bleiben `FilesToolbar.vue`, `ImpersonationBar.vue`,
      `ShareDialog.vue`, `ContextMenu.vue`.
      **Teilweise behoben**: AdminPanel auf 427 Z. reduziert + 3 Sub-Komponenten
      (`AdminUserList` 75 Z., `AdminUserDetails` 211 Z., `QuotaEditor` 153 Z.).
      `FileExplorer.vue` auf 1130 Z. reduziert; further splitting remains
      optional for future releases.

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

- [ ] „Desktop-JVM: Token-Speicher härten" —
  `kmp/shared/src/jvmMain/kotlin/com/flutcloud/flutlink/desktop/FileKeyValueStorage.kt:14`
  dokumentiert die Keyring-Anbindung weiterhin als Follow-up („not encrypted
  at rest").
- [x] Performance-Analyse: R1, R2, R3, N1+F2, F1, U3, N2, U5 — alle in
  v1.2.0 umgesetzt.

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
- [ ] CI security gate auf v1.2.0: 5.3/10 < min 7.0 — 7 AI-Befunde
      (pre-existing: FileKeyValueStorage, update-nc.sh, commands.rs,
      guest.rs, ShareDialog.vue). Prüfen ob direktive actionable items
      oder zu low-priority für Hotfix.

## Admin-Panel UI-Review (Issue: Layout, Formular & UX-Verbesserungen)

Implementiert am 2026-08-26:

- [x] **Linke Spalte: volle Höhe** — `h-full` auf Wrapper (`AdminPanel.vue:389`)
      und Card (`AdminUserList.vue:23`) damit die Liste nicht nach 2 Einträgen
      abschneidet.
- [x] **Header-Struktur** — `justify-between` + innere Gruppierung Avatar+Name
      (`AdminUserDetails.vue:53-81`). „Benutzer löschen" bekommt dezenten
      roten Border/BG statt schwebendes `btn-danger`.
- [x] **Kontingent-Redundanz** — Preset-Dropdown entfernt; nur noch
      Eingabefeld (Wert) + Einheiten-Dropdown (MB/GB/Unlimited)
      (`QuotaEditor.vue`).
- [x] **Kontingent-Speichern** — Button von `btn-primary` auf `btn-outline`
      umgestellt, visuell de-emphasiert.
- [x] **Fortschrittsanzeige** — Progress Bar unter Quota-Statistik (grün bei
      ≤70%, amber bei 71-90%, rot bei >90%).
- [x] **Passwort: Augensymbol** — Eye/Eye-off Icon inline im Input statt
      separatem Text-Button; zwei Icons zu `Icon.vue` hinzugefügt.
- [x] **Passwort: Bestätigung** — Zweites Input „Passwort bestätigen" +
      Validierung; zentraler Save-Button bleibt deaktiviert bis Passwörter
      übereinstimmen.
- [x] **i18n** — `confirmPassword`/`passwordsMismatch` in en + de.

## Performance-Analyse (ergänzt 2026-08-25, umgesetzt in v1.2.0)

### High Priority

- [x] **R1 (Sync): Remote-Listing ist sequenzielles BFS** — umgesetzt:
      `tokio::sync::Semaphore(4)` + `futures_util::future::join_all` in
      `list_remote` (`sync.rs`). `std::mem::take` statt `drain(..).collect()`.
- [x] **N1+F2 (Shares): `loadAllShares()` ruft ALLE Shares pro Navigation**
      — bereits behoben: `loadAllShares(path)` mit Pfadfilter
      (`FileExplorer.vue`), OCS filtert serverseitig.

### Medium Priority

- [x] **F1 (Sort): Doppelte Sortierung** — behoben: Parent sortiert already,
      `EntryList.vue` sortiert nicht mehr doppelt. `sortEntries`-Import
      entfernt.
- [x] **R3 (Cache): `evict_oldest` liest bei jedem Write alle Files** —
      umgesetzt: Batch-Eviction (10% pro Aufruf, `cache.rs`). Test aktualisiert.
- [x] **R2 (Sync): `plan_ops` allokiert Union-BTreeSet** — umgesetzt:
      `BTreeSet<&str>` statt `BTreeSet<String>`, Key-Referenzen statt Kopien
      (`sync.rs`).

### Low Priority

- [x] **U3 (Rendering): `formatMtime` erstellt pro Entry ein Date-Objekt**
      — umgesetzt: `mtimeCache` computed Map in `EntryList.vue`.
- [x] **N2 (Thumbnails): 50 gleichzeitige HTTP-Requests** — bereits behoben:
      Thumbnail-Semaphore max 6 (`FileExplorer.vue`).
- [x] **U5 (Rendering): `<thead>` wird bei jedem Entry-Change neu gerendert**
      — umgesetzt: `v-once` auf `<thead>` in `EntryList.vue`.
