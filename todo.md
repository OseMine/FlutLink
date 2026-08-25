# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.
Am 2026-08-25 sind zusätzlich die kompletten Review-Abschnitte der Läufe
17–19 (L17-*/L19-*/CP-* — allesamt im Code umgesetzt) nach
`archived-todo.md` verschoben; offen blieben nur „Desktop-JVM: Token-Speicher
härten“ und die Performance-Analyse.

## Review 2026-08-25 (Lauf 20, Fokus Guest-Mode + Desktop-Basis — neue Befunde)

Gegenstand: der seit Lauf 19 neu hinzugekommene Code — Gast-Zugriff auf
vollständig öffentliche Shares (`src-tauri/src/guest.rs`, `persist.rs` neu,
Guest-/Guest-Admin-Commands in `commands.rs`, Registrierung in `lib.rs`,
`GuestBrowser.vue`, `ui.ts` guestMode) — plus die Standard-Bereiche
(IPC-Commands, WebDAV/OCS-Anbindung, Keyring, Fehler-/State-Management,
Frontend `src/`, CI/Workflows unter `.github/`) und die Nachprüfung der
offenen L17-/L19-/CP-/Perf-Befunde gegen HEAD (`f9b7351`).

Neu gefunden:

- [ ] **L20-F1 (Bug/Feature-Lücke, mittel): Die gesamte Gast-Admin-
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
- [ ] **L20-F2 (Bug, mittel): Der Sperrzustand wird nie geladen — bereits
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
- [ ] **L20-F3 (UX-Konsistenz, minor): `deleteCategory` löscht ohne
      Bestätigungsdialog.** Das kleine „×" auf dem Kategorie-Chip
      (`GuestBrowser.vue:307-315`) ruft direkt
      `api.guestAdminDeleteCategory(name)` auf (`:161-170`) — inkonsistent
      zum etablierten Confirm-Muster (Konto F7, Datei `deleteConfirm`,
      Sync-Ordner L19-F3); serverseitig fällt die Zuweisung aller Shares
      dieser Kategorie weg. Fix: `window.confirm` wie in `SyncPanel.remove`.
- [ ] **L20-N1 (Race/UX, minor): `enter()` während einer laufenden
      Aktion zeigt die Einträge des vorherigen Share unter neuem Namen.**
      `enter` (`GuestBrowser.vue:90-93`) setzt `share.value` und ruft
      `navigateTo("/")` auf — das bricht bei gesetztem `busyPath` stillschweigend
      ab (`:96`), ohne die Liste zu leeren. Klickt man während eines
      Downloads/Ladevorgangs auf eine andere Share-Karte (die Karten sind
      nicht disabled), bleibt die alte Dateiliste unter dem neuen Share-Titel
      stehen. Fix: in `enter()` `entries.value = []` setzen oder `enter` an
      `busyPath` koppeln.
- [ ] **L20-N2 (Fehlermeldung, minor): `verify_guest_server` meldet „App
      nicht installiert oder deaktiviert“, wenn die App nur zu alt für den
      Gast-Feature-Flag ist.** `guest.rs:130-135` mappt das fehlende
      `complete-public-shares`-Feature auf `AppError::FlutCloudAppMissing`,
      dessen Text (`error.rs:125-132`) einen Installationsfehler behauptet —
      obwohl `verify_server` kurz zuvor die App erfolgreich erkannt hat.
      Fix: eigener Fehlercode/-text („FlutCloud-App zu alt für Gastzugriff“).
- [ ] **L20-N3 (CI, minor): Der PowerShell-Parse-Check prüft nur
      `install-flutcloud-app.ps1` — der README-One-Liner-Einstiegspunkt
      `scripts/install-flutlink.ps1` bleibt ungeprüft.**
      `.github/workflows/flutcloud.yml:92` enumeriert
      `Get-ChildItem scripts -Filter 'install-flutcloud-app.ps1'`; die
      Bash-Seite wurde mit L17-N2 auf alle `.sh` ausgeweitet, die
      PowerShell-Seite nicht auf alle `.ps1`. Fix: `-Filter '*.ps1'`.
- [ ] **L20-N4 (Typing, minor): `Share` in `ipc.ts` kennt `uidOwner`
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
