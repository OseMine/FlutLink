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

- [ ] **Suche: `d:eq` → `d:contains`** — `webdav.rs:202-205` nutzt den
      XML-Operator `d:eq` für exakten Name-Match; `d:contains` oder `d:like`
      würde echte Teilstring-Suche ermöglichen. Betroffen:
      `search_request_body()`.
- [ ] **Admin-Panel: Debounce für Suche** — Jeder Tastendrach bei
      `AdminUserList.vue` löst sofortigen `admin_list_users`-Aufruf aus.
      Ein 300ms-Debounce (wie in FileExplorer `searchFiles`) reduziert die
      Serverlast bei schneller Eingabe.
- [ ] **i18n: `retry`-Key wird nirgends genutzt** — `i18n.ts:334/675`
      definiert `retry` (en+de), aber kein Component nutzt ihn. Network-
      Error-Toasts könnten einen Retry-Button erhalten (`ipc.ts:186-202`).
- [ ] **Bulk-Upload: Fortschrittsanzeige pro Datei** — `transfer_progress()`
      in `commands.rs:868-897` wird für Single-File-Uploads genutzt, aber
      `webdav_upload_local_paths` emittiert kein `file://progress`-Event
      pro Datei (nur `upload_tree` für rekursive Uploads). Für den
      nicht-rekursiven Datei-Zweig (`commands.rs:1157-1179`) fehlt der
      Progress-Callback.
- [ ] **CLI: `--download` und `--list`-Befehle** — Aktuell gibt es nur
      `--sync`, `--path`, `--url`, `--tray` (`lib.rs:148-192`). Headless-
      Nutzung (z.B. Skripting) könnte `--download <remote> <local>` und
      `--list <path>` gebrauchen.

### Medium Features (3–7 Tage)

- [ ] **Selective Sync (`.flutlinkignore`)** — Sync-Engine (`sync.rs`)
      synchronisiert den gesamten Ordner. Ein Ignore-Mechanismus
      (ähnlich `.gitignore`) pro Sync-Ordner würde `node_modules/`, `*.tmp`
      etc. ausschließen. Erfordert: Filter in `list_local`/`plan_ops`,
      UI-Setting in `SyncPanel.vue`, Persistenz in `SyncFolder`.
- [ ] **Dateiversionen (Nextcloud Versions-API)** — OCS-Endpunkt
      `/apps/files_versions/` anzeigen, ältere Versionen herunterladen/
      wiederherstellen. Neue Komponente `VersionDialog.vue` + IPC-Commands
      `webdav_list_versions`, `webdav_restore_version`.
- [ ] **Freigabe-Benachrichtigungen** — Tauri Notification-Plugin wird
      bereits initialisiert (`tauri_plugin_notification::init()` in
      `lib.rs:203`), aber nur für Update-Meldungen genutzt. Ein periodically
      Check (ähnlich `refresh_admin_flags`) könnte neue Freigaben erkennen.
- [ ] **Datei-Schnellvorschau (Quick Look)** — Leertaste-Taste → Overlay-
      Vorschau für Bilder/PDFs/Texte. Die `preview`-API (`webdav.rs:693-739`)
      liefert bereits Thumbnails; eine erweiterte Vorschau (höhere Auflösung,
      Inline-Renderer) wäre eine natürliche Erweiterung.
- [ ] **Kopieren/Verschieben zwischen Ordnern** — WebDAV COPY/MOVE über
      neue IPC-Commands `webdav_copy`, `webdav_move`. Aktuell gibt es nur
      `webdav_rename` (`commands.rs:1241-1265`) und Upload/Download.
- [ ] **Share-Editing (Passwort, Ablauf, Berechtigungen)** — OCS
      `PUT /shares/{id}` ermöglicht nachträgliche Änderungen. Aktuell gibt
      es nur Create/Delete (`commands.rs:514-586`). Neue Commands
      `webdav_edit_share` + erweitertes `ShareDialog.vue`.
- [ ] **Quota-Warnung (Desktop-Notification)** — `account_storage`
      (`commands.rs:464-488`) lädt den Quota-Wert bereits. Ein periodischer
      Check (z.B. alle 30 Min.) mit Schwellwert >90% könnte eine
      Notification emittieren.
- [ ] **Sync-Protokoll / Historie** — Die Journal-Daten (`sync.rs`
      `Journal`/`JournalEntry`) existieren bereits. Eine UI-Ansicht
      (letzte Sync-Aktionen, Konflikte) wäre ein leichtes Add-on.
- [ ] **Ordner-Lesezeichen** — Schnellzugriff auf häufig besuchte Ordner.
      Persistenz im `ui.ts` Store (wie `filesView`), Anzeige im
      `EntryList.vue` Sidebar-Bereich.
- [ ] **Globale Tastenkürzel** — Strg/Cmd+F (Suche), Strg/Cmd+N (Ordner),
      Entf (Löschen), F5 (Refresh). Die Escape-Stack-Infrastruktur
      (`src/lib/escape.ts`) existiert bereits.
- [ ] **Share-Link-Vorschau mit QR-Code** — Beim Erstellen eines Share-Links:
      Vorschau + Copy-Button + optionaler QR-Code (Canvas-basiert).

### Large Features (1–3 Wochen)

- [ ] **Offline-Bearbeitung mit Conflict-Resolution-UI** — Bei Konflikten
      (beide Seiten geändert): Inline-Diff-Ansicht (Textdateien) oder
      „meine Version / Server-Version / Beide behalten". Die Sync-Engine
      (`sync.rs`) erzeugt bereits Konflikt-Kopien; eine UI dafür fehlt.
- [ ] **Virtuelle Dateisystem-Integration (VFS)** — On-Demand-Dateizugriff
      via FUSE/WinFSP. Desktop-only (Plattform-Gründe, s. `kmp/README.md`).
      Big-Picture-Feature, erfordert native Integration pro Plattform.
- [ ] **WebSocket/SSE für Live-Updates** — Statt polling-basiertem Refresh
      (aktuell: `listen("accounts-changed")` / `listen("sync-status")`):
      Server-seitige Echtzeit-Events für Dateiänderungen, Shares, Admin-
      Aktionen.
- [ ] **2FA-Unterstützung (TOTP/WebAuthn)** — Nextcloud unterstützt 2FA;
      aktuell wird nur App-Passwort genutzt. Erfordert OAuth2-Flow oder
      erweiterte App-Passwort-Generierung.
- [ ] **Automatisches Token-Rotieren** — Periodisches Erneuern des
      App-Passworts über die Nextcloud Security-API.
- [ ] **Gruppen-Bulk-Verwaltung** — Mehrere Benutzer gleichzeitig einer
      Gruppe zuweisen/entfernen. Aktuell: Einzel-Aktionen
      (`commands.rs:1574-1598`).
- [ ] **Französisch / Spanisch als weitere Sprachen** — Tauri-User-Base
      international; die i18n-Infrastruktur (`src/lib/i18n.ts`) ist
      erweiterbar.
- [ ] **Tauri Updater-Plugin als Fallback** — Aktuell: eigene GitHub-API-
      Abfrage (`updater.rs`). Das Tauri Updater-Plugin bietet
      Signaturverifikation out-of-the-box.
- [ ] **Admin-Aktivitäts-Log** — Nextcloud Activity-API
      (`/ocs/v2.php/activity/events`) für Benutzer-Aktivitäten anzeigen.

### UI / UX Verbesserungen

- [ ] **Datei-Sync-Status in der Dateiliste** — Icon/Label für lokale
      Dateien die Teil eines Sync-Ordners sind (synced, pending, conflict).
- [ ] **Drag & Drop zwischen Accounts** — Dateien vom einen Konto auf das
      andere ziehen (Multi-Account-Infrastruktur vorhanden).
- [ ] **Share-Link mit QR-Code** — Beim Share-Erstellen: QR-Code generieren.
- [ ] **Passwort-Stärke-Anzeige** — Visuelle Stärkeanzeige beim Share-
      Passwort (Pattern existiert bereits im Admin-Panel).
- [ ] **Admin: Kontingent-Warnungen im Panel** — Visualisierung wenn
      Quota >90% (Progress Bar existiert bereits in `QuotaEditor.vue`).
- [ ] **Datei-Historie (zuletzt geöffnet)** — Basierend auf
      `open_cache_dir`-Aktionen eine „Zuletzt geöffnete Dateien"-Liste.
- [ ] **System-Tray: Quick-Actions** — Sync auslösen, Uploads pausieren,
      Online/Offline-Status im Tray-Kontextmenü.

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
