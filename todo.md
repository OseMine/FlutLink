# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.
Am 2026-08-25 sind zusätzlich die kompletten Review-Abschnitte der Läufe
17–19 (L17-*/L19-*/CP-* — allesamt im Code umgesetzt) nach
`archived-todo.md` verschoben; offen blieben nur „Desktop-JVM: Token-Speicher
härten" und die Performance-Analyse. Am 2026-08-26 sind die Abschnitte der
Läufe 20 und 21 gefolgt (nahezu komplett umgesetzt, Reste unten geführt).
Am 2026-09-07 Cleanup: Alle vollständig erledigten Review-Abschnitte (Läufe
17–19, 22–23, 28, 31) und abgeschlossene Sektionen (Admin-Panel UI-Review,
Performance-Analyse) entfernt; nur offene [ ]-Punkte beibehalten.

## Review 2026-09-07 (Lauf 32, Fokus „Full Project Review gegen HEAD df388b9" — neue Befunde)

Gegenstand: vollständiges Review des Projekts (Desktop Tauri v2 Client) gegen
HEAD `df388b9`. Geprüft: IPC-Registry (`lib.rs` ↔ `ipc.ts`), WebDAV/OCS
(`nextcloud/webdav.rs`, `nextcloud/ocs.rs`), State/Fehler (`state.rs`,
`error.rs`), CI-Workflows (`.github/workflows/*.yml`), Frontend
(Komponenten, Stores, `ipc.ts`). Verifikation: `cargo test --manifest-path
src-tauri/Cargo.toml`, `npm run build`.

**Neu gefunden:**

- [ ] **(Erste Befunde folgen während des Review-Laufs.)**

## Review 2026-08-31 (Lauf 30, Fokus „Full Project Review: IPC, WebDAV/OCS, Keyring, State, CI" — neue Befunde)

Gegenstand: vollständiges Review des gesamten Projekts (Desktop Tauri v2 Client + KMP Mobile) gegen HEAD `7ba8ce3` (Tag v1.3.1) + WIP-Arbeitsbaum. Geprüft: IPC-Registry (`lib.rs` ↔ `ipc.ts`), WebDAV/OCS-Anbindung (`webdav.rs`, `ocs.rs`), Schlüsselbund-Verwaltung (`accounts.rs`, `keyring`), Fehler-/State-Management (`error.rs`, `state.rs`, `sync.rs`, `settings.rs`), CI-Workflows (`.github/workflows/*.yml`), Disk-Mount/VFS-WIP (`disk_mount.rs`), Frontend Stores/Components (`src/stores/*.ts`, `src/components/*.vue`). Verifikation: `cargo fmt --check` ✓ (Exit 0), `cargo clippy --all-targets --manifest-path src-tauri/Cargo.toml -- -D warnings` schlägt aufgrund fehlender Systemdeps (`glib-2.0`, `gobject-2.0`) fehl — **kein Code-Problem**; `npm run build` ✓ (122 Module, 287 kB gzip).

**Neu gefunden:**

- [ ] **R30-F1 (IPC/State, hoch): `set_share_notify` ist im Frontend nur optimistisch (`ui.ts:166-173`) — der `catch` schluckt Fehler still, aber der Toggle bleibt gesetzt.** Die Settings-Seite seeded erst beim Öffnen per `getSettings` neu (L24-F2). Wenn der IPC-Aufruf fehlschlägt, driftet `localStorage` vs. Backend (`settings.json`) auseinander; der Sync-Worker liest das Backend-Flag, das UI aber das stale `localStorage`-Flag. Fix: bei Fehler `shareNotify` zurückrollen oder `getSettings` nach Catch aufrufen, statt nur zu loggen.
- [ ] **R30-F2 (IPC/Commands, mittel): `get_settings` (`commands.rs:874-878`) liest unter dem `settings`-Mutex, klont aber das geladene `AppSettings` — das ist korrekt. Aber `set_share_notify` (`commands.rs:863-868`) lädt unter dem Lock **nicht** frisch (`lock()` lädt in `settings.rs:96` neu), sondern mutiert den Guard direkt.** Da `lock()` beim Worker *vor* jedem Check neu lädt (`settings.rs:110-113`), ist das Fenster für Lost Updates klein, aber existent: wenn `set_share_notify` und Worker fast gleichzeitig laufen, kann der Worker ein altes `share_seen` schreiben, das die neue Toggle-Änderung überdeckt. Fix: `set_share_notify` soll unter dem Lock **nicht** mutieren, sondern `lock()` aufrufen (was `load()` tut), dann mutieren, dann `save()`.
- [ ] **R30-F3 (Keyring, mittel): `accounts.rs` mappt Keyring-Fehler auf `AppError::Keyring` mit plattformspezifischem Hint (Linux). Aber `load_token` (`accounts.rs:50-56`) gibt bei *jedem* Keyring-Fehler `Err` zurück, auch wenn der Eintrag einfach nicht existiert (`keyring::Error::NoEntry`).** `load_accounts` (`accounts.rs:119-122`) behandelt das als `token_missing` (gut), aber `account_add`/`register_user` rufen `save_token` auf, das `NoEntry` nicht unterscheidet — der Fehler landet als generischer `Keyring`-Error im Frontend statt als „Keychain nicht verfügbar/locked". Fix: `save_token`/`load_token`/`delete_token` sollten `NoEntry` gesondert behandeln und als `AppError::App("Credential store entry missing")` o.ä. mappen.
- [ ] **R30-F4 (WebDAV, mittel): `webdav.rs:947-956` (`tmp_path`) nutzt einen statischen Atom-Counter + PID für Temp-Dateinamen.** Bei *parallelen* Downloads derselben Zieldatei (Sync-Engine kann Conflict-Copies parallel laden) kann der Counter-Race zwei Tasks denselben Suffix geben → `rename` überschreibt. Fix: `tokio::fs::File::create_new` (atomic create) oder UUID/Random-Bytes statt Counter.
- [ ] **R30-F5 (WebDAV/OCS, mittel): `ocs.rs:529-539` (`build_share_form`) sendet `path` **roh** (absichtlich, F4-Fix). Aber `shareWith`/`password`/`expireDate` werden **nicht** XML-escaped — OCS erwartet form-urlencoded, PHP decoded einmal.** Sonderzeichen in `shareWith` (Benutzername mit `&`, `#`, `+`) werden bei Form-Encoding korrekt transmitted; aber bei `password` mit `%`/`&` kann die PHP-Seite double-decode-Probleme haben. Nicht reproduzierbar, aber inkonsistent: `path` roh, Rest form-encoded. Fix: alle Felder konsistent roh senden (wie `path`), oder explizit dokumentieren warum `path` besonders ist.
- [ ] **R30-F6 (Sync-Engine, mittel): `sync.rs:1330-1344` (`flush_sync_log`) persistiert **oldest→newest** (Comment: L24-N1), aber `load_sync_log` (`sync.rs:1357-1362`) returned `newest first` via `reverse()`. `sync_log_list` command (`commands.rs:903-911`) truncated mit `limit` **nach** dem Reverse — korrekt.** Aber der Worker appended pro geplantem Op (`sync.rs:1229-1236`) und flushed **einmal pro Pass** (gut, L24-N1). Problem: `MAX_SYNC_LOG_ENTRIES = 200` (`sync.rs:70`), aber ein Pass kann `MAX_OPS_PER_PASS = 200` Ops erzeugen → Log wächst auf 400, dann drain auf 200. Bei vielen Fehler-Passes wächst das File kurzzeitig stark. Fix: `drain` bereits im Loop oder Batch-Limit pro Pass (z.B. 50 Entries/Pass) statt nur am Ende.
- [ ] **R30-F7 (Disk-Mount/VFS-WIP, hoch): `disk_mount.rs:53-135` (`mount_disk`) startet einen lokalen WebDAV-Server (`dav-server` + `hyper`) und mountet via OS-Tools (`net use`, `mount_webdav`, `gio`).** Aber: (a) kein Cleanup bei Absturz des Tauri-Prozesses (der `shutdown_tx` wird nie gesendet, Port/Prozess bleiben); (b) `mount_os_drive` auf Linux (`gio mount dav://…`) gibt kein persistentes Mount-Point zurück — der Rückgabewert ist die URL, nicht der Pfad (z.B. `/run/user/1000/gvfs/…`); Frontend kann den Pfad nicht anzeigen/öffnen; (c) Windows `net use Z: <url>` schlägt fehl, wenn WebClient-Dienst deaktiviert ist — kein Fallback/Error-Hint; (d) macOS `mount_webdav -S` verlangt `guest@` im URL, aber Auth ist `basic_auth(token, "")` — Server muss Anonymous erlauben; (e) `unmount_disk` wartet nicht auf Server-Shutdown (`shutdown_tx.send(())` fire-and-forget). Für v1.3.2: entweder komplett verdrahten (Process-Guard, Health-Check, Mount-Point-Auflösung) oder hinter Feature-Gate/Dev-Flag verstecken.
- [ ] **R30-F8 (Frontend/Store, mittel): `ui.ts:103-107` (`shareNotify`) und `diskMount`/`diskMountCachePath`/`autostart` sind reine `localStorage`-Keys ohne Backend-Sync.** `shareNotify` hat Backend (`settings.json`), aber UI toggled optimistisch + Fire-and-Forget. `diskMount`/`autostart` haben **kein** Backend-Pendant — ein Neustart verliert den Mount (der `DiskMountState` in `lib.rs:443` ist Runtime-only). Fix: `diskMount`/`autostart` entweder in `settings.json` persistieren oder als reine UI-Prefs kennzeichnen (nicht als „Feature an/aus").
- [ ] **R30-F9 (Frontend/Store, mittel): `accounts.ts:33-54` `load()` nutzt `loadSeq` Guard gegen Race — gut. Aber `loadStorage()` (`accounts.ts:56-71`) prüft `active.value` **nach** dem Await, nicht atomar mit dem `owner`-Guard.** Bei schnellem Account-Switch kann `loadStorage` für alten Account laufen und `storage` mit fremder Quota überschreiben. Fix: `owner`-Check **vor** dem Await oder `loadStorage` sequentiell nach `load()` awaiten (aktuell paralleles `await loadStorage()` am Ende von `load()` ohne Seq-Guard).
- [ ] **R30-F10 (Frontend, niedrig): `SettingsModal.vue:90-100` `filesApp` nutzt `navigator.userAgentData?.platform` (Client Hints) + Fallback auf `userAgent`.** Der Fallback `t("filesappUnknown")` ist i18n-konform (gut, R29-N2 fix). Aber `navigator.userAgentData` ist **Secure Context Only** (HTTPS/localhost) — auf `tauri://` oder `http://` im Dev-Modus `undefined`. Der Fallback auf `userAgent` greift, aber `userAgent` enthält `"MacIntel"` auf Windows (Edge/WebView2) → fälschlich „Finder". Fix: `window.navigator.platform` (deprecated, aber auf WebView2 zuverlässig) als zweiten Fallback vor `userAgent` nutzen.
- [ ] **R30-F11 (CI/Release, mittel): `.github/workflows/release.yml:123-136` `release-notes`-Job: `continue-on-error: true` (R28-N1) + Push auf `main` (R29-F2 Fix: Ref-Guard `if [[ "$GITHUB_REF" != refs/tags/v* ]]` hinzugefügt).** Aber der **Read-Step** (`steps.read.outputs.body`) liest `release-notes.md` per Heredoc — wenn OpenCode **leeren Output** liefert (Model-Fallback, Rate-Limit), ist `release-notes.md` leer/fehlt, `cat` schlägt fehl, `body` Output bleibt unset. `prepare-release` fällt auf Platzhalter zurück. Fix: `release-notes.md` Existenz/Non-Empty prüfen vor Heredoc, sonst Fallback-Body explizit setzen.
- [ ] **R30-F12 (CI/Release, niedrig): `.github/workflows/release.yml:547-559` `publish-release` Completeness-Gate prüft Patterns (`\.apk$`, `\.ipa$`, `classic\.json$`, `latest\.json$`, `\.sig$`, `flutcloud-app\.zip$`, Desktop-Suffixe).** Aber **Windows** baut **MSI + NSIS** (`build.yml:78`, `release.yml:275`) — Gate prüft nur `\.msi$` **oder** `.exe$` (NSIS heißt `*_x64-setup.exe`). Wenn Tauri nur MSI baut (Config-Änderung), fehlt `.exe` → Gate schlägt fehl. Fix: Gate auf `(\.msi|\.exe)$` erweitern oder Tauri-Config explizit beide erzwingen.
- [ ] **R30-F13 (KMP/AccountStore, mittel): `kmp/shared/src/commonMain/kotlin/.../core/AccountStore.kt:34-38` `saveToken` schreibt in `securePrefs` (Android Keystore / JVM file-based). Aber **kein** Counterpart zu `delete_token` beim Account-Remove — `AccountStore` hat `deleteToken`, aber `SessionManager`/`AccountViewModel` rufen es beim Logout/Remove nicht auf.** Tokens bleiben im Keystore/Filesystem orphaned. Fix: `AccountStore.deleteToken` bei `SessionManager.clearSession` / Account-Remove aufrufen.
- [ ] **R30-F14 (KMP/WebDAV, mittel): `kmp/shared/src/commonMain/kotlin/.../data/WebDavApi.kt` nutzt `khttp` (blocking) statt `ktor`/`okhttp` async — blockiert Coroutines auf IO-Dispatcher, aber alle Calls sind `withContext(Dispatchers.IO)`.** OK, aber `WebDavApi.list`/`search`/`putFile`/`getFile` haben **kein** Timeout-Config — hängt unbegrenzt bei Netzwerkproblemen. Desktop setzt `connect_timeout=30s`/`read_timeout=60s` (`state.rs:220-221`). Fix: `khttp` Request-Config mit Timeouts setzen.
- [ ] **R30-F15 (KMP/OCS, mittel): `kmp/shared/src/commonMain/kotlin/.../data/FlutCloudOcs.kt` `updateUser` (`FlutCloudOcs.kt:76`) sendet `key`/`value` roh im Form-Body — gleiche Double-Encoding-Problematik wie Desktop `ocs.rs:529` (R30-F5).** Desktop sendet `path` roh, Rest form-encoded; KMP sendet alles form-encoded. Inkonsistent. Fix: Desktop/KMP angleichen (beide roh oder beide form-encoded mit Doku).

**Re-Verifikation offener Befunde (aus L24/L25/L26/L28/L29):**

- [ ] **L24-F2** (Share-Notify erreicht Backend nie): **weiter offen** — `ui.ts:166-173` Fire-and-Forget, `getSettings` nur beim Settings-Öffnen (`SettingsModal.vue:113-116`).
- [ ] **L24-F3b** (Sync-Log Append/Trunkierung): **weiter offen** — `sync.rs:1330-1344` oldest→newest persist, `load_sync_log` reverse(), aber Worker appended pro Op und flush 1×/Pass (R30-F6 related).
- [ ] **L24-F4** (Lost-Update Settings): **teilweise** — `settings.rs:91-98` Mutex + Reload unter Lock, aber `set_share_notify` mutiert ohne Reload (R30-F2).
- [ ] **L24-F5** (Retry verwirft Ergebnis / nicht-idempotent gepuffert): **weiter offen** — `ipc.ts:17-40` `RETRY_SAFE_COMMANDS` Set, aber `retryLast` liefert Result nicht an ursprünglichen Caller (nur `onRetrySuccess` Event).
- [ ] **L24-F7** (Share-Edit `publicUpload` immer gesendet): **weiter offen** — `ShareDialog.vue:103-104` sendet `publicUpload` immer, `""` → `undefined`; Backend `commands.rs:618-623` mappt auf 15/1.
- [ ] **L24-F8** (move_dest_path ohne Trailing-Slash-Trim / validate_dav_path leere Segmente): **teilweise** — `commands.rs:1426-1437` `move_dest_path` trimmed `dest_folder` (`trim_end_matches('/')`), aber `validate_dav_path` (`commands.rs:641-666`) prüft `//` → leere Segmente blockiert; Move-in-sich-selbst geprüft (`validate_copy_move_dest` `dest == source`).
- [ ] **L24-N1** (Sync-Log Write-Amplification): **teilweise** — `flush_sync_log` 1×/Pass (gut), aber Batch-Limit fehlt (R30-F6).
- [ ] **KMP-F1** (Admin `editUser` fehlt): **weiter offen** — `AdminViewModel.kt` kein `editUser`, `FlutCloudOcs.updateUser` nur für quota/enabled genutzt.
- [ ] **KMP-F9** (Copy/Move/QR/QuickLook fehlen): **weiter offen**.

## Review 2026-08-30 (Lauf 29, Fokus „v1.3.2-Vorbereitung: Disk-Mount/VFS-WIP + offene L24-Befunde" — neue Befunde)

Gegenstand: Vorbereitung der v1.3.2 — Review des uncommitteten
Disk-Mount/VFS-WIP (`mount.rs`, `mount_default_cache`,
SettingsModal/SyncPanel/`ui.ts`/`ipc.ts`) sowie Re-Verifikation der offenen
L24-F/N-Befunde gegen HEAD `7ba8ce3` (Tag v1.3.1) + WIP-Arbeitsbaum; dazu
Prüfung der Release-CI nach dem v1.3.1-Lauf (release-notes-Job).

Verifikation (alles grün): `cargo fmt --check` ✓, `cargo clippy --all-targets
--manifest-path src-tauri/Cargo.toml -- -D warnings` ✓ (Exit 0), `cargo test`
→ 112 passed / 0 failed. `npm run build` ✓ (126 Module).

**Neu gefunden:**

- [ ] **R29-F3 (Feature-WIP, mittel): Der Disk-Mount-Schalter ist eine reine
  UI-Hülle ohne Backend-Wirkung.** `ui.ts` (`DISKMOUNT_KEY`,
  `DISKMOUNT_CACHE_KEY`) persistiert nur in localStorage; ein
  Mount/Unmount-`#[tauri::command]` existiert nicht — der einzige neue
  Command `mount_default_cache` (`commands.rs:1905-1909`, registriert
  `lib.rs:497`) liefert lediglich den Default-Pfad zurück. `unifuse` ist in
  `Cargo.toml` optional und laut Kommentar „not yet wired in";
  `mount.rs::default_cache_dir` erzeugt den Ordner nicht (`create_dir_all`
  fehlt) und vermischt `Ok(None)`-Fehler mit „noch kein Cache-Ordner
  gewählt" — ein Kippschalter-Umschalten bewirkt sichtbar nichts. Für
  v1.3.2: Mount/Unmount-Backend + Commands + UI-Verdrahtung nachziehen oder
  die Sektion hinter ein Feature/Dev-Gate hängen.

**Status Disk-Mount/VFS-WIP (uncommitted, v1.3.2-Kandidat):** `mount.rs`
(neu, `default_cache_dir` → `app_data_dir()/cache/mountcache`),
`mount_default_cache` + Registrierung (`lib.rs:497`), Wrapper
`api.mountDefaultCache` (`ipc.ts:505`), `diskMount`/`diskMountCachePath`
(`ui.ts`), Kippschalter + Cache-Ordner-Picker in `SettingsModal.vue` (Tab
„Über"), `SyncPanel.vue`-Rework mit `stateUnknown`-Fallback und Empty-State.
Sämtliche Disk-Mount-i18n-Keys sind in en/de/fr/es angelegt. Bewertung →
R29-F3 (nicht verdrahtet) und R29-N1/N2.

## Review 2026-08-28 (Lauf 25, Fokus KMP Mobile UI — offene Befunde)

Gegenstand: die komplette KMP-Mobile-UI (`kmp/shared/src/commonMain/kotlin/.../ui/`)
gegen die Desktop-Features (`src/`, IPC-Commands, `commands.rs`, `ocs.rs`).
Auftrag: UI „looks off and not clean", fehlende Tabs, fehlende Desktop-Admin-
Features auf Mobile abgleichen.

### Offene Punkte

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
- [ ] **KMP-F9 (Feature-Lücke, mittel — Desktop-Parität): Mobile fehlen
      Copy/Move, QR-Code, QuickLook** — Desktop `webdav_copy`/`webdav_move`
      (#411, `commands.rs:1383/1410`), `QrCode.vue` (#409) und `QuickLook.vue`
      (#405) haben kein Mobile-Pendant: weder eine `copy`/`move`-Route in
      `FlutCloudApi`/`FilesViewModel.kt` noch Clipboard-/QR-Zugriff auf den
      Share-Link (`link_created`-Toast ist die einzige Rückgabe,
      `FilesScreen.kt:283-288`) noch eine Vollbild-Preview. Reihenfolge nach
      KMP-F1/F2 einplanen.

## Review 2026-08-28 (Lauf 24, Fokus Feature-Reihe #399–#428 — offener Befund)

Gegenstand: die seit Lauf 23 eingelandeten Feature-Commits plus die
Standard-Bereiche und die Nachprüfung der offenen Punkte gegen HEAD `197df7f`.

### Offener Befund

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

## Feature-Ideen und Verbesserungsvorschläge (2026-08-27)

Basierend auf der vollständigen Code-Review aller Backend- und Frontend-Dateien.
Sortiert nach Umsetzungsaufwand (klein → groß).

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
- [ ] **Tauri Updater-Plugin als Fallback** — Aktuell: eigene GitHub-API-
      Abfrage (`updater.rs`). Das Tauri Updater-Plugin bietet
      Signaturverifikation out-of-the-box.
- [ ] **Admin-Aktivitäts-Log** — Nextcloud Activity-API
      (`/ocs/v2.php/activity/events`) für Benutzer-Aktivitäten anzeigen. (#420)

### UI / UX Verbesserungen

- [ ] **Drag & Drop zwischen Accounts** — Dateien vom einen Konto auf das
      andere ziehen (Multi-Account-Infrastruktur vorhanden). (#422)

## Offen

- [ ] Desktop-JVM: Token-Speicher härten — OS-Keyring-Anbindung statt
      600er-Datei unter `$XDG_STATE_HOME/flutlink` (siehe
      `FileKeyValueStorage`), Parität zum Tauri-Client (`keyring`).
- [ ] CI security gate auf v1.2.0: 5.3/10 < min 7.0 — 7 AI-Befunde
      (pre-existing: FileKeyValueStorage, update-nc.sh, commands.rs,
      guest.rs, ShareDialog.vue). Prüfen ob direktive actionable items
      oder zu low-priority für Hotfix.
- [ ] L21-N4: `FileExplorer.vue` ist ein ~1130-Zeilen-Monolith —
      vor einem sauberen Restyling zerlegen. Kandidaten:
      `FilesToolbar.vue`, `ImpersonationBar.vue`, `ShareDialog.vue`,
      `ContextMenu.vue`.
- [ ] Performance-Analyse: Benchmarks/Profiling-Daten fehlen im Repo
      (die Implementierungs-Punkte R1/R2/R3/N1+F2/F1/U3/N2/U5 sind
      umgesetzt, aber es gibt keine Messdaten zur Verifikation).
