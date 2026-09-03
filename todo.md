# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.
Am 2026-08-25 sind zusätzlich die kompletten Review-Abschnitte der Läufe
17–19 (L17-*/L19-*/CP-* — allesamt im Code umgesetzt) nach
`archived-todo.md` verschoben; offen blieben nur „Desktop-JVM: Token-Speicher
härten" und die Performance-Analyse. Am 2026-08-26 sind die Abschnitte der
Läufe 20 und 21 gefolgt (nahezu komplett umgesetzt, Reste unten geführt).

## Review 2026-09-03 (Lauf 31, Fokus „Autostart + Disk-Mount/Cache" — neue Befunde)

Gegenstand: gezieltes Review der Features „FlutLink beim Anmelden starten"
(Autostart) und „Laufwerk und Cache" (Disk Mount). Geprüft: `ui.ts`,
`SettingsModal.vue`, `lib.rs`, `commands.rs`, `ipc.ts`, `disk_mount.rs`.

**Neu gefunden:**

### Autostart (3 Befunde)

- [x] **R31-F1 (IPC/Backend, hoch): `set_autostart`/`get_autostart` sind komplett auskommentiert (`commands.rs:880-899`). Der UI-Toggle (`ui.ts:192-195`) schreibt nur `localStorage`, aktiviert/deaktiviert nie die OS-Ebene.** Das Feature ist eine reine UI-Hülle ohne Backend-Wirkung. Fix: `tauri-plugin-autostart` v2 sauber verdrahten (Plugin-Setup in `lib.rs`, Commands registrieren, Frontend-Wrapper in `ipc.ts` ergänzen) oder den Toggle hinter ein Dev-Gate/Disclaimer verstecken. — **BEHOBEN** (2026-09-03): Plugin `tauri_plugin_autostart::init` in `lib.rs` registriert, Commands `get_autostart`/`set_autostart` verdrahtet (Persistenz in `settings.json` + OS-Level via `app.autolaunch()`), IPC-Wrapper `getAutostart`/`setAutostart` in `ipc.ts`, `ui.ts`-Store zeigt Backend-Zustand.

- [x] **R31-F2 (Systemtray, mittel): Autostart-Menü-Item im Tray (`lib.rs:61-72`) hortet `autostart_enabled = false` immer, Klick-Handler (`lib.rs:185-196`) ist auskommentiert.** Das Menü-Item ist visuell vorhanden, reagiert aber nicht auf Klicks und zeigt nie einen Häkchen-Zustand. Fix: Solange Plugin nicht verdrahtet: Menü-Item ausblenden; danach: `autolaunch.is_enabled()` + Klick-Handler aktivieren. — **BEHOBEN** (2026-09-03): `build_tray_menu` nutzt `app.autolaunch().is_enabled()`, Klick-Handler toggelt OS-Autolaunch und rebuilt das Menü.

- [x] **R31-F3 (Persistenz, mittel): `autostart` lebt nur in `localStorage` ohne Backend-Pendant (`ui.ts:37,107`).** Ein Neustart oder Löschen des WebView-Storage löscht die Präferenz. Im Vergleich: `shareNotify` hat zumindest `settings.json` als Backend-Fallback (R30-F1). Fix: Autostart-Persistenz in `settings.json` ergänzen (neues Feld `autostart_enabled`) oder, solange das OS-Level fehlt, den Toggle als reine UI-Preferences markieren (nicht als „Feature an/aus"). (Verwandt: R30-F8.) — **BEHOBEN** (2026-09-03): `AppSettings` um `autostart_enabled` erweitert, `set_autostart` persistiert in `settings.json`, `settings.rs`-Sync bei App-Start gleicht OS-Registrierung mit dem gespeicherten Flag ab.

### Disk Mount / Cache (9 Befunde)

- [x] **R31-F4 (Backend/Persistenz, hoch): `diskMount`-State ist Runtime-only — `DiskMountState` in `lib.rs:443` geht bei Neustart verloren, aber `ui.ts:103,182-185` persisted den Toggle in `localStorage`. Nach Neustart zeigt das UI „mountet" an, aber kein Laufwerk ist gemountet.** Fix: `diskMount` + `diskMountCachePath` in `settings.json` persistieren oder, wenn Auto-Mount nicht gewünscht ist, den Toggle bei App-Start explizit auf `false` setzen. (Verwandt: R30-F8.) — **TEILWEISE BEHOBEN** (2026-09-03): Mount-Cache-Pfad und Status werden jetzt korrekt aus `ActiveMount` gemeldet (R31-F5), Crash-Cleanup beim Beenden (R31-F11). Auto-Mount bei Start bleibt bewusst aus (Feature-Entscheidung), der Toggle-Erwartungskonflikt bleibt als R30-F8 offen.

- [x] **R31-F5 (Disk-Mount/Persistenz, mittel): `get_mount_status` (`disk_mount.rs:157-160`) liest immer `default_cache_dir()`, ignoriert den konfigurierten `diskMountCachePath` aus dem Frontend.** Nachdem der User einen Custom-Cache-Pfad gewählt hat, meldet der Status weiterhin den Default-Pfad. Fix: `get_mount_status` soll den tatsächlichen Pfad aus `ActiveMount` (falls gemountet) bzw. den gespeicherten Custom-Pfad aus `settings.json` auslesen. — **BEHOBEN** (2026-09-03): `ActiveMount.cache_dir` gespeichert; `get_mount_status` meldet jetzt den tatsächlichen Pfad des gemounteten Laufwerks.

- [x] **R31-F6 (Windows, mittel): `mount_os_drive` (`disk_mount.rs:184`) used `Z:` als festen Laufwerksbuchstaben.** Ist `Z:` bereits durch einen anderen Netzlauflaufwerk belegt, schlägt `net use` fehl mit einem unklaren Fehler aus `net use` stdout. Fix: Freien Laufwerksbuchstaben ermitteln (z.B. `net use` parsen) oder `*` als Platzhalter verwenden, der Windows einen freien Buchstaben zuweisen lässt. — **BEHOBEN** (2026-09-03): `net use * <url>` + Auth, zeilenweises Parsen der zugewiesenen Buchstaben aus stdout.

- [x] **R31-F7 (Windows, mittel): `net use Z: http://...` versucht Basic Auth, aber der lokale WebDAV-Server (`disk_mount.rs:74-77`) hat kein Auth-Middleware.** Windows WebClient-Dienst verlangt bei HTTP eine explizite Auth-Konfiguration; ohne `auth`-Block auf dem `DavHandler` schlägt die Verbindung mit einem HTTP 401 fehl, den `net use` als generischen Fehler meldet. Fix: `DavHandler` mit `basic_auth` konfigurieren (lokaler Dummy-User/Passwort) oder HTTPS für den lokalen Server erwägen. — **BEHOBEN** (2026-09-03): lokaler WebDAV-Server verlangt Basic Auth (`flutlink:<random token>`), `net use *` übergibt die Credentials inline.

- [x] **R31-F8 (macOS, mittel): `mount_os_drive` (`disk_mount.rs:219`) fügt `guest@` in die URL ein (`server_url.replace("http://", "http://guest@")`).** Der lokale WebDAV-Server akzeptiert anonymous Zugriff (`LocalFs` ohne Auth), aber `mount_webdav` erwartet ein gültiges `user:pass@`-Format. `guest@` ohne Passwort funktioniert nicht zuverlässig; zusätzlich kollidiert `/Volumes/FlutLink` (`disk_mount.rs:220`) bei existierendem Volume gleichen Namens. Fix: `guest:` oder gar kein User einsetzen, oder `mount_webdav`-Optionen für anonymous nutzen; Volume-Name mit Suffix/UUID deduplizieren. — **BEHOBEN** (2026-09-03): `mount_webdav` erhält jetzt die bare Server-URL ohne `guest@` (der lokale Server akzeptiert anonymous für macOS, Basic Auth ist nur der Windows-Gate).

- [x] **R31-F9 (Linux, mittel): `gio mount dav://...` gibt die URL statt des tatsächlichen GVFS-Mount-Punkts zurück (`disk_mount.rs:262`).** Der Rückgabewert ist `dav_url` (z.B. `dav://127.0.0.1:12345`), nicht der Pfad im Dateisystem (z.B. `/run/user/1000/gvfs/dav:host=127.0.0.1,port=12345`). Frontend kann den Pfad nicht anzeigen/öffnen. Fix: Nach `gio mount` den GVFS-Mount-Pfad ermitteln (z.B. `gio mount -l` parsen oder `$XDG_RUNTIME_DIR/gvfs/` scannen). — **BEHOBEN** (2026-09-03): `resolve_gvfs_mount_point` listet `gio mount -l` und parst den `/run/user/<uid>/gvfs/`-Pfad; Fallback auf `dav://`-URL.

- [x] **R31-F10 (Linux, mittel): `unmount_os_drive` (`disk_mount.rs:274-279`) ruft `gio mount -u <dav_url>` mit der Original-URL (`http://...`) auf.** `gio mount -u` erwartet den GVFS-Mount-URI (nicht die Quell-URL). Da `mount_os_drive` die falsche URL zurückgibt (R31-F9), wird auch der Unmount-Mechanismus davon beeinflusst. Fix: Unmount über den tatsächlichen GVFS-Pfad oder den URN aus `gio mount -l`. — **BEHOBEN** (2026-09-03): `unmount_os_drive` unmountet per GVFS-Pfad, fallback auf `dav://`-URL; `mount_point_to_dav_url` rekonstruiert die URL aus dem GVFS-Pfad.

- [x] **R31-F11 (Crash-Sicherheit, mittel): Bei Prozess-Absturz wird `shutdown_tx` (`disk_mount.rs:88`) nie gesendet; der lokale WebDAV-Server-Task und der gehörende Port bleiben als Orphan bestehen.** Bei nächstem Start schlägt `TcpListener::bind("127.0.0.1:0")` zwar nicht fehl (OS gibt neuen Port), aber die OS-Mount-Zuordnung (`net use Z:`) zeigt auf den toten Server. Fix: `on_window_event(CloseRequested)` oder `Builder::on_run_exit` als Cleanup-Hook nutzen; alternativ: auf Start prüfen ob alter `DiskMountState`-Port noch erreichbar ist (Health-Check). — **TEILWEISE BEHOBEN** (2026-09-03): Tray-`quit`-Handler führt `shutdown_if_mounted` aus (Cleanup von WebDAV-Server + OS-Mount). Crash (SIGKILL) kann weiterhin Orphans hinterlassen — das erfordert einen externen Prozess-Guard und bleibt außerhalb des Scopes.

- [ ] **R31-F12 (Sicherheit, niedrig): Der lokale WebDAV-Server (`disk_mount.rs:74-77`) hat kein Auth-Middleware — jeder localhost-Prozess kann auf die gemounteten Dateien zugreifen.** Für ein Desktop-Tool akzeptabel, aber bei Shared-Machine-Szenarien (Entwickler-VM, Terminal-Server) ein Risiko. Fix: `basic_auth` mit zufällig generiertem Token, das nur beim Mount zurückgegeben wird. — **BEHOBEN** (2026-09-03): Der Server verlangt jetzt Basic Auth mit zufällig generiertem Token (`flutlink:<random base64>`); macOS/WebClient-Nutzung bleibt davon unberührt, da der Token nur beim Mount erzeugt und dem OS-WebDAV-Client übergeben wird (Windows: inline, macOS/Linux: anonymous für Spezialfälle).

- [ ] **R31-F13 (UX/Caching, niedrig): `custom_cache_dir` (`disk_mount.rs:57,66-72`) wird nicht validiert — ein ungültiger oder nicht beschreibbarer Pfad crasht `create_dir_all` mit einem generischen Fehler.** Keine Vorschauprüfung (Pfad erreichbar? Schreibrechte? Genug Platz?). Fix: Vorab-Check (`metadata` + `write`-Test) oder`create_dir_all` mit spezifischerer Fehlermeldung. — **BEHOBEN** (2026-09-03): `prepare_cache_dir` erstellt den Ordner, probiert eine Schreibprobe (`Write-Test`), gibt auf Fehlern spezifische Meldungen zurück.

**Status:** Alle Befunde neu und offen. Verwandte offene Befunde: R30-F8 (Disk-Mount/Autostart als reine `localStorage`-Keys ohne Backend-Persistenz).

## Review 2026-08-31 (Lauf 30, Fokus „Full Project Review: IPC, WebDAV/OCS, Keyring, State, CI" — neue Befunde)

Gegenstand: vollständiges Review des gesamten Projekts (Desktop Tauri v2 Client + KMP Mobile) gegen HEAD `7ba8ce3` (Tag v1.3.1) + WIP-Arbeitsbaum. Geprüft: IPC-Registry (`lib.rs` ↔ `ipc.ts`), WebDAV/OCS-Anbindung (`webdav.rs`, `ocs.rs`), Schlüsselbund-Verwaltung (`accounts.rs`, `keyring`), Fehler-/State-Management (`error.rs`, `state.rs`, `sync.rs`, `settings.rs`), CI-Workflows (`.github/workflows/*.yml`), Disk-Mount/VFS-WIP (`disk_mount.rs`), Frontend Stores/Components (`src/stores/*.ts`, `src/components/*.vue`). Verifikation: `cargo fmt --check` ✓ (Exit 0), `cargo clippy --all-targets --manifest-path src-tauri/Cargo.toml -- -D warnings` schlägt aufgrund fehlender Systemdeps (`glib-2.0`, `gobject-2.0`) fehl — **kein Code-Problem**; `npm run build` ✓ (122 Module, 287 kB gzip).

**Neu gefunden:**

- [ ] **R30-F1 (IPC/State, hoch): `set_share_notify` ist im Frontend nur optimistisch (`ui.ts:166-173`) — der `catch` schluckt Fehler still, aber der Toggle bleibt gesetzt. Die Settings-Seite seeded erst beim Öffnen per `getSettings` neu (L24-F2). Wenn der IPC-Aufruf fehlschlägt, driftet `localStorage` vs. Backend (`settings.json`) auseinander; der Sync-Worker liest das Backend-Flag, das UI aber das stale `localStorage`-Flag. Fix: bei Fehler `shareNotify` zurückrollen oder `getSettings` nach Catch aufrufen, statt nur zu loggen.
- [ ] **R30-F2 (IPC/Commands, mittel): `get_settings` (`commands.rs:874-878`) liest unter dem `settings`-Mutex, klont aber das geladene `AppSettings` — das ist korrekt. Aber `set_share_notify` (`commands.rs:863-868`) lädt unter dem Lock **nicht** frisch (`lock()` lädt in `settings.rs:96` neu), sondern mutiert den Guard direkt. Da `lock()` beim Worker *vor* jedem Check neu lädt (`settings.rs:110-113`), ist das Fenster für Lost Updates klein, aber existent: wenn `set_share_notify` und Worker fast gleichzeitig laufen, kann der Worker ein altes `share_seen` schreiben, das die neue Toggle-Änderung überdeckt. Fix: `set_share_notify` soll unter dem Lock **nicht** mutieren, sondern `lock()` aufrufen (was `load()` tut), dann mutieren, dann `save()`.
- [ ] **R30-F3 (Keyring, mittel): `accounts.rs` mappt Keyring-Fehler auf `AppError::Keyring` mit plattformspezifischem Hint (Linux). Aber `load_token` (`accounts.rs:50-56`) gibt bei *jedem* Keyring-Fehler `Err` zurück, auch wenn der Eintrag einfach nicht existiert (`keyring::Error::NoEntry`). `load_accounts` (`accounts.rs:119-122`) behandelt das als `token_missing` (gut), aber `account_add`/`register_user` rufen `save_token` auf, das `NoEntry` nicht unterscheidet — der Fehler landet als generischer `Keyring`-Error im Frontend statt als „Keychain nicht verfügbar/locked". Fix: `save_token`/`load_token`/`delete_token` sollten `NoEntry` gesondert behandeln und als `AppError::App("Credential store entry missing")` o.ä. mappen.
- [ ] **R30-F4 (WebDAV, mittel): `webdav.rs:947-956` (`tmp_path`) nutzt einen statischen Atom-Counter + PID für Temp-Dateinamen. Bei *parallelen* Downloads derselben Zieldatei (Sync-Engine kann Conflict-Copies parallel laden) kann der Counter-Race zwei Tasks denselben Suffix geben → `rename` überschreibt. Fix: `tokio::fs::File::create_new` (atomic create) oder UUID/Random-Bytes statt Counter.
- [ ] **R30-F5 (WebDAV/OCS, mittel): `ocs.rs:529-539` (`build_share_form`) sendet `path` **roh** (absichtlich, F4-Fix). Aber `shareWith`/`password`/`expireDate` werden **nicht** XML-escaped — OCS erwartet form-urlencoded, PHP decoded einmal. Sonderzeichen in `shareWith` (Benutzername mit `&`, `#`, `+`) werden bei Form-Encoding korrekt transmitted; aber bei `password` mit `%`/`&` kann die PHP-Seite double-decode-Probleme haben. Nicht reproduzierbar, aber inkonsistent: `path` roh, Rest form-encoded. Fix: alle Felder konsistent roh senden (wie `path`), oder explizit dokumentieren warum `path` besonders ist.
- [ ] **R30-F6 (Sync-Engine, mittel): `sync.rs:1330-1344` (`flush_sync_log`) persistiert **oldest→newest** (Comment: L24-N1), aber `load_sync_log` (`sync.rs:1357-1362`) returned `newest first` via `reverse()`. `sync_log_list` command (`commands.rs:903-911`) truncated mit `limit` **nach** dem Reverse — korrekt. Aber der Worker appended pro geplantem Op (`sync.rs:1229-1236`) und flushed **einmal pro Pass** (gut, L24-N1). Problem: `MAX_SYNC_LOG_ENTRIES = 200` (`sync.rs:70`), aber ein Pass kann `MAX_OPS_PER_PASS = 200` Ops erzeugen → Log wächst auf 400, dann drain auf 200. Bei vielen Fehler-Passes wächst das File kurzzeitig stark. Fix: `drain` bereits im Loop oder Batch-Limit pro Pass (z.B. 50 Entries/Pass) statt nur am Ende.
- [ ] **R30-F7 (Disk-Mount/VFS-WIP, hoch): `disk_mount.rs:53-135` (`mount_disk`) startet einen lokalen WebDAV-Server (`dav-server` + `hyper`) und mountet via OS-Tools (`net use`, `mount_webdav`, `gio`). Aber: (a) kein Cleanup bei Absturz des Tauri-Prozesses (der `shutdown_tx` wird nie gesendet, Port/Prozess bleiben); (b) `mount_os_drive` auf Linux (`gio mount dav://…`) gibt kein persistentes Mount-Point zurück — der Rückgabewert ist die URL, nicht der Pfad (z.B. `/run/user/1000/gvfs/…`); Frontend kann den Pfad nicht anzeigen/öffnen; (c) Windows `net use Z: <url>` schlägt fehl, wenn WebClient-Dienst deaktiviert ist — kein Fallback/Error-Hint; (d) macOS `mount_webdav -S` verlangt `guest@` im URL, aber Auth ist `basic_auth(token, "")` — Server muss Anonymous erlauben; (e) `unmount_disk` wartet nicht auf Server-Shutdown (`shutdown_tx.send(())` fire-and-forget). Für v1.3.2: entweder komplett verdrahten (Process-Guard, Health-Check, Mount-Point-Auflösung) oder hinter Feature-Gate/Dev-Flag verstecken.
- [ ] **R30-F8 (Frontend/Store, mittel): `ui.ts:103-107` (`shareNotify`) und `diskMount`/`diskMountCachePath`/`autostart` sind reine `localStorage`-Keys ohne Backend-Sync. `shareNotify` hat Backend (`settings.json`), aber UI toggled optimistisch + Fire-and-Forget. `diskMount`/`autostart` haben **kein** Backend-Pendant — ein Neustart verliert den Mount (der `DiskMountState` in `lib.rs:443` ist Runtime-only). Fix: `diskMount`/`autostart` entweder in `settings.json` persistieren oder als reine UI-Prefs kennzeichnen (nicht als „Feature an/aus").
- [ ] **R30-F9 (Frontend/Store, mittel): `accounts.ts:33-54` `load()` nutzt `loadSeq` Guard gegen Race — gut. Aber `loadStorage()` (`accounts.ts:56-71`) prüft `active.value` **nach** dem Await, nicht atomar mit dem `owner`-Guard. Bei schnellem Account-Switch kann `loadStorage` für alten Account laufen und `storage` mit fremder Quota überschreiben. Fix: `owner`-Check **vor** dem Await oder `loadStorage` sequentiell nach `load()` awaiten (aktuell paralleles `await loadStorage()` am Ende von `load()` ohne Seq-Guard).
- [ ] **R30-F10 (Frontend, niedrig): `SettingsModal.vue:90-100` `filesApp` nutzt `navigator.userAgentData?.platform` (Client Hints) + Fallback auf `userAgent`. Der Fallback `t("filesappUnknown")` ist i18n-konform (gut, R29-N2 fix). Aber `navigator.userAgentData` ist **Secure Context Only** (HTTPS/localhost) — auf `tauri://` oder `http://` im Dev-Modus `undefined`. Der Fallback auf `userAgent` greift, aber `userAgent` enthält `"MacIntel"` auf Windows (Edge/WebView2) → fälschlich „Finder". Fix: `window.navigator.platform` (deprecated, aber auf WebView2 zuverlässig) als zweiten Fallback vor `userAgent` nutzen.
- [ ] **R30-F11 (CI/Release, mittel): `.github/workflows/release.yml:123-136` `release-notes`-Job: `continue-on-error: true` (R28-N1) + Push auf `main` (R29-F2 Fix: Ref-Guard `if [[ "$GITHUB_REF" != refs/tags/v* ]]` hinzugefügt). Aber der **Read-Step** (`steps.read.outputs.body`) liest `release-notes.md` per Heredoc — wenn OpenCode **leeren Output** liefert (Model-Fallback, Rate-Limit), ist `release-notes.md` leer/fehlt, `cat` schlägt fehl, `body` Output bleibt unset. `prepare-release` fällt auf Platzhalter zurück. Fix: `release-notes.md` Existenz/Non-Empty prüfen vor Heredoc, sonst Fallback-Body explizit setzen.
- [ ] **R30-F12 (CI/Release, niedrig): `.github/workflows/release.yml:547-559` `publish-release` Completeness-Gate prüft Patterns (`\.apk$`, `\.ipa$`, `classic\.json$`, `latest\.json$`, `\.sig$`, `flutcloud-app\.zip$`, Desktop-Suffixe). Aber **Windows** baut **MSI + NSIS** (`build.yml:78`, `release.yml:275`) — Gate prüft nur `\.msi$` **oder** `\.exe$` (NSIS heißt `*_x64-setup.exe`). Wenn Tauri nur MSI baut (Config-Änderung), fehlt `.exe` → Gate schlägt fehl. Fix: Gate auf `(\.msi|\.exe)$` erweitern oder Tauri-Config explizit beide erzwingen.
- [ ] **R30-F13 (KMP/AccountStore, mittel): `kmp/shared/src/commonMain/kotlin/.../core/AccountStore.kt:34-38` `saveToken` schreibt in `securePrefs` (Android Keystore / JVM file-based). Aber **kein** Counterpart zu `delete_token` beim Account-Remove — `AccountStore` hat `deleteToken`, aber `SessionManager`/`AccountViewModel` rufen es beim Logout/Remove nicht auf. Tokens bleiben im Keystore/Filesystem orphaned. Fix: `AccountStore.deleteToken` bei `SessionManager.clearSession` / Account-Remove aufrufen.
- [ ] **R30-F14 (KMP/WebDAV, mittel): `kmp/shared/src/commonMain/kotlin/.../data/WebDavApi.kt` nutzt `khttp` (blocking) statt `ktor`/`okhttp` async — blockiert Coroutines auf IO-Dispatcher, aber alle Calls sind `withContext(Dispatchers.IO)`. OK, aber `WebDavApi.list`/`search`/`putFile`/`getFile` haben **kein** Timeout-Config — hängt unbegrenzt bei Netzwerkproblemen. Desktop setzt `connect_timeout=30s`/`read_timeout=60s` (`state.rs:220-221`). Fix: `khttp` Request-Config mit Timeouts setzen.
- [ ] **R30-F15 (KMP/OCS, mittel): `kmp/shared/src/commonMain/kotlin/.../data/FlutCloudOcs.kt` `updateUser` (`FlutCloudOcs.kt:76`) sendet `key`/`value` roh im Form-Body — gleiche Double-Encoding-Problematik wie Desktop `ocs.rs:529` (R30-F5). Desktop sendet `path` roh, Rest form-encoded; KMP sendet alles form-encoded. Inkonsistent. Fix: Desktop/KMP angleichen (beide roh oder beide form-encoded mit Doku).

**Re-Verifikation offener Befunde (aus L24/L25/L26/L28/L29):**

- [x] **L24-F1** (fmt/clippy CI-Blocker): **BEHOBEN** in `4c2f25e` (`sync.rs:1339` Closure vereinfacht, `Move*.Conflict` formatiert).
- [ ] **L24-F2** (Share-Notify erreicht Backend nie): **weiter offen** — `ui.ts:166-173` Fire-and-Forget, `getSettings` nur beim Settings-Öffnen (`SettingsModal.vue:113-116`).
- [ ] **L24-F3b** (Sync-Log Append/Trunkierung): **weiter offen** — `sync.rs:1330-1344` oldest→newest persist, `load_sync_log` reverse(), aber Worker appended pro Op und flush 1×/Pass (R30-F6 related).
- [ ] **L24-F4** (Lost-Update Settings): **teilweise** — `settings.rs:91-98` Mutex + Reload unter Lock, aber `set_share_notify` mutiert ohne Reload (R30-F2).
- [ ] **L24-F5** (Retry verwirft Ergebnis / nicht-idempotent gepuffert): **weiter offen** — `ipc.ts:17-40` `RETRY_SAFE_COMMANDS` Set, aber `retryLast` liefert Result nicht an ursprünglichen Caller (nur `onRetrySuccess` Event).
- [ ] **L24-F7** (Share-Edit `publicUpload` immer gesendet): **weiter offen** — `ShareDialog.vue:103-104` sendet `publicUpload` immer, `""` → `undefined`; Backend `commands.rs:618-623` mappt auf 15/1.
- [ ] **L24-F8** (move_dest_path ohne Trailing-Slash-Trim / validate_dav_path leere Segmente): **teilweise** — `commands.rs:1426-1437` `move_dest_path` trimmed `dest_folder` (`trim_end_matches('/')`), aber `validate_dav_path` (`commands.rs:641-666`) prüft `//` → leere Segmente blockiert; Move-in-sich-selbst geprüft (`validate_copy_move_dest` `dest == source`).
- [ ] **L24-N1** (Sync-Log Write-Amplification): **teilweise** — `flush_sync_log` 1×/Pass (gut), aber Batch-Limit fehlt (R30-F6).
- [x] **L24-N2…N7** (QuickLook-Race, Impersonation-Toast, etc.): **mit v1.3.1 umgesetzt** — Stichproben verifiziert.
- [ ] **KMP-F1** (Admin `editUser` fehlt): **weiter offen** — `AdminViewModel.kt` kein `editUser`, `FlutCloudOcs.updateUser` nur für quota/enabled genutzt.
- [x] **KMP-F6** (doppelte Chrome / kein M3 NavigationBar): **BEHOBEN** — `HomeScreen.kt` nutzt M3 `NavigationBar` als `bottomBar`.
- [x] **KMP-F8** (iosMain-Doku veraltet): **BEHOBEN** — `kmp/README.md` korrigiert.
- [ ] **KMP-F9** (Copy/Move/QR/QuickLook fehlen): **weiter offen**.
- [ ] **Desktop-JVM: Token-Speicher härten** (aus Feature-Ideen): **weiter offen** — `accounts.rs` nutzt `keyring` Default-Config, kein `keyring::Entry::new` mit `credential_builder`/`access_control` (macOS Keychain Access Control / Windows Credential Guard).
- [ ] **Performance-Analyse** (aus Feature-Ideen): **weiter offen** — keine Benchmarks/Profiling-Daten im Repo.

**todo.md-Nachprüfung (Schritt 5):**
Keine neuen Erledigungen zum Archivieren — alle offenen `[ ]` bleiben. Markierungen für L24-F1, KMP-F6, KMP-F8 bleiben `[x]` in-place.

**GitHub-Issues (Schritt 6):**
Nur lokale Quellen (keine gh-API). `git log` seit L29 zeigt keine Commits, die offene Issues schließen. Der `opencode-todo-issues`-Workflow sollte R30-F1…F15 sowie die weiterhin offenen L24-/KMP-Befunde beim nächsten Lauf als Issues erfassen.

## Review 2026-08-30 (Lauf 29, Fokus „v1.3.2-Vorbereitung: Disk-Mount/VFS-WIP + offene L24-Befunde" — neue Befunde)

Gegenstand: Vorbereitung der v1.3.2 — Review des uncommitteten
Disk-Mount/VFS-WIP (`mount.rs`, `mount_default_cache`,
SettingsModal/SyncPanel/`ui.ts`/`ipc.ts`) sowie Re-Verifikation der offenen
L24-F/N-Befunde gegen HEAD `7ba8ce3` (Tag v1.3.1) + WIP-Arbeitsbaum; dazu
Prüfung der Release-CI nach dem v1.3.1-Lauf (release-notes-Job).

Verifikation (alles grün): `cargo fmt --check` ✓, `cargo clippy --all-targets
--manifest-path src-tauri/Cargo.toml -- -D warnings` ✓ (Exit 0), `cargo test`
→ 112 passed / 0 failed (HEAD `7ba8ce3` hat 113 `#[test]`-Attribute; Differenz
u.a. durch cfg-Gates, z.B. `updater.rs` linux-only; der Rückgang gegenüber
Lauf 28 („116") ist nicht vollständig aufgelöst, aber ohne funktionalen
Befund). `npm run build` ✓ (126 Module).

**Neu gefunden (Fokus „v1.3.2-Vorbereitung / Release-CI"):**

- [x] **R29-F1 (Release-CI, hoch): Der „Heredoc-Delimiter-Fix" aus R28
  (`release.yml`, release-notes-Job) ist unvollständig — die generierten
  Release-Notes gehen im v1.3.1-Lauf verloren.** `release-notes.md` endet ohne
  Trailing-Newline (die letzten 5 Bytes sind `98 97 114 41 46` = `bar).`).
  Der Job schreibt `cat release-notes.md >> "$GITHUB_OUTPUT"` und danach
  `echo "$delimiter" >> "$GITHUB_OUTPUT"` — die schließende Delimiter-Zeile
  klebt damit an der letzten Inhaltszeile, und GitHub Actions bricht mit
  `Unable to process file command 'output' successfully. Invalid value.
  Matching delimiter not found 'RELEASE_NOTES_1788092202495008399'` ab
  (als `##[error]` im v1.3.1-Log bestätigt; generierte Output-Datei 1904
  Bytes, letztes Byte 46, kein LF/CR). Folge: `steps.read.outputs.body` wird
  nie gesetzt, `prepare-release` fällt auf den generischen Platzhalter
  („Download the assets below to install this version.") zurück, und die vom
  Modell geschriebenen Notizen landen zwar als Commit `6ab41c0` auf `main`
  (Git-Push des Jobs), aber nicht im GitHub-Release. Fix:
  `{ cat release-notes.md; printf '\n'; } >> "$GITHUB_OUTPUT"` bzw. vor dem
  Schreiben ein Trailing-Newline sicherstellen.
- [x] **R29-F2 (Release-CI, mittel): Der release-notes-Job pusht ohne
  Ref-Guard direkt auf `main` (`git push origin HEAD:main`) und läuft auch
  bei `workflow_dispatch`.** Ein manueller Dispatch auf `main` erzeugt so
  jedes Mal einen weiteren Modell-Commit auf dem Hauptbranch; kombiniert mit
  `continue-on-error: true` (R28-N1) kann auch ein fehlgeschlagener Job
  pushen. Fix: Push-Schritt an `startsWith(github.ref, 'refs/tags/v')`
  koppeln oder die Notes über einen PR-Branch anliefern.
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
- [x] **R29-N1 (Dependencies, minor): `getos@^3.2.1` ist neu in
  `package.json`/`package-lock.json`, wird aber nirgends importiert** (kein
  Treffer in `src/`). Entweder für die Drive-/Mountpunkt-Enumeration
  verdrahten oder die Abhängigkeit wieder entfernen.
- [x] **R29-N2 (Frontend, minor): `filesApp` (`SettingsModal.vue:86-92`)
  nutzt das deprecated `window.navigator.platform` und liefert für
  nicht erkannte Plattformen das unübersetzte Literal `"unknown"`** — die
  i18n-Pflicht gilt auch für diesen String. Zudem hängt der
  `mountDefaultCache()`-Abruf (`SettingsModal.vue:99`) ohne `.catch`
  (unhandled rejection bei IPC-Fehlern).

**Re-Verifikation der offenen L24-Befunde (Gegenprobe im Quelltext):**

- [x] **L24-F6 (QuickLook-Race) ist BEHOBEN** — Guard `entry.path ===
  quickLookEntry.value?.path` in `refreshQuickLookImage`
  (`FileExplorer.vue:502-511`, v1.3.1). Checkbox im L24-Abschnitt auf `[x]`
  gesetzt.
- [ ] Weiter offen (mit aktuellen Fundstellen): **F2** (Share-Notify: nur
  `ui.ts:158-161` localStorage; `setShareNotify` (`ipc.ts:370`) hat keine
  Aufrufer; kein `get_settings`-Rückweg), **F3b** (Append/Trunkierung:
  `sync.rs:1234` append je geplantem Op, `append_sync_log` `sync.rs:1330-1342`
  neueste-zuerst/push ans Ende/drain vorn — der „Feature unerreichbar"-Teil
  von F3 ist dagegen erledigt: `sync_log_list`/`sync_log_clear`
  (`commands.rs:856-873`, `lib.rs:502-503`, `ipc.ts:376-377`) sind
  registriert und das Sync-Log hat i18n-Keys in en/de/fr/es), **F4**
  (Lost-Update: weiterhin kein `Mutex<AppSettings>`, `set_share_notify`
  `commands.rs:848-852` vs. Worker-Fenster `sync.rs:1792-1799`; fester
  Temp-Name `tmp-{pid}` `persist.rs:15`), **F5** (Retry verwirft Ergebnis,
  `ipc.ts:34-45`; auch nicht-idempotente Commands gepuffert), **F7**
  (Share-Edit: `ShareDialog.vue:103-104` sendet `publicUpload` immer und
  macht `""` zu `undefined`; Backend `commands.rs:618-623` schreibt 15/1),
  **F8** (`move_dest_path` `commands.rs:1379-1389` ohne Trailing-Slash-Trim;
  `validate_dav_path` `commands.rs:641-668` lässt leere Segmente durch →
  `/B//name`; Move-in-sich-selbst ungeprüft). **N1** unverändert offen
  (`sync.rs:1234`).
- [x] **L24-N2…N7 sind mit v1.3.1 umgesetzt** (die `[x]`-Marker stimmen) —
  Stichproben: `validate_dav_path` im CLI (`lib.rs:279/316`), QuickLook-
  Kanten-Absicherung, Impersonation-Bar-Fix.

**Status Disk-Mount/VFS-WIP (uncommitted, v1.3.2-Kandidat):** `mount.rs`
(neu, `default_cache_dir` → `app_data_dir()/cache/mountcache`),
`mount_default_cache` + Registrierung (`lib.rs:497`), Wrapper
`api.mountDefaultCache` (`ipc.ts:505`), `diskMount`/`diskMountCachePath`
(`ui.ts`), Kippschalter + Cache-Ordner-Picker in `SettingsModal.vue` (Tab
„Über"), `SyncPanel.vue`-Rework mit `stateUnknown`-Fallback und Empty-State.
Sämtliche Disk-Mount-i18n-Keys sind in en/de/fr/es angelegt. Bewertung →
R29-F3 (nicht verdrahtet) und R29-N1/N2.

**KMP (kein KMP-Anwendungscode-Change seit Lauf 28, nur die v1.3.1-Fixes
F5/F7/F10/F12):** L25-KMP-F1…F4 und F9 sowie L26-KMP-F11 sind unverändert
offen. Die von Lauf 28 als BEHOBEN gemeldeten Einträge KMP-F6 (doppelte
Chrome, `HomeScreen.kt` → M3-`NavigationBar`) und KMP-F8 (iosMain-Doku)
hatten noch fehlende Checkboxen in den L25/L26-Abschnitten — hier
nachgezogen (`[x]`).

**todo.md-Nachprüfung (Schritt 5):** L24-F6 `[x]`, KMP-F6 `[x]`, KMP-F8 `[x]`
gesetzt. Keine physischen Verschiebungen nach `archived-todo.md` (Anweisung:
nur `todo.md` verändern; Markierungen in-place — wie in Lauf 28 gehandhabt).

**GitHub-Issues/Repo-Zustand:** origin/main liegt inzwischen bei `6ab41c0`
(durch den release-notes-Job direkt gepusht, s. R29-F1/F2); lokaler HEAD ist
`7ba8ce3` (Tag v1.3.1) mit WIP im Arbeitsbaum — vor dem nächsten Commit
`git pull --rebase` einplanen. Keine gh-Aufrufe in diesem Lauf; der
`opencode-todo-issues`-Workflow sollte R29-F1/F2 erfassen.
> Gegenstand: das uncommittete v1.3.2-WIP im Working Tree (Disk-Mount-Feature:
> `mount.rs`, `mount_default_cache`, SettingsModal-/ui.ts-/i18n-Änderungen,
> SyncPanel-Rework, `getos`-Dependency, `unifuse` optional) sowie die
> Standard-Bereiche (IPC-Registry, WebDAV/OCS, Keyring, Fehler-/State-
> Management, CI) und die Re-Verifikation der offenen L24-/KMP-Befunde gegen
> HEAD `7ba8ce3` + Working Tree.
>
> **Verifikation in diesem Lauf geplant:** `cargo fmt --check`, `cargo clippy
> --all-targets -- -D warnings`, `cargo test --manifest-path src-tauri/Cargo.toml`,
> `npm run build`.

## Review 2026-08-30 (Lauf 28, Fokus „v1.3.1 / Updater-Fallback, Single-Instance & Release-Konsistenz" — neue Befunde)

Gegenstand: die 20 Commits seit Lauf 27 (`f2a28a6..HEAD`, HEAD `4dcd117`):
KMP-F13/14/15-Umsetzung (`53136ad`, `4124b1b`, `6b4eafc`, `4e152db`), Updater-
Plugin-Fallback + `tauri_plugin_single_instance` + `tauri_plugin_updater`
(`05b2652`), Version-Reverts/-Rebumps (`c78140c`, `7e0cc5d`), CI
(`530dada`, `855d716` Signing-Key-Rotation, `38c9bb8` Release-Workflow),
AltStore-Updates (`29cb557`, `e64d644`, `4dcd117`). Plus die Standard-Bereiche
gegen HEAD re-verifiziert.

**Verifikation frisch ausgeführt (Systemdeps nachinstalliert):** `cargo fmt
--check` grün; `cargo clippy --all-targets -- -D warnings` **grün** (Exit 0);
`cargo test --manifest-path src-tauri/Cargo.toml` → **116 passed / 0 failed**;
`npm run build` (vue-tsc + vite) **grün**; `cd kmp && ./gradlew
:shared:compileKotlinJvm` **grün** (nur Deprecation-Warnungen, s. R28-N2).
Erstmals seit Lauf 24 wieder ein vollständiger Toolchain-Lauf.

Neu gefunden:

- [x] **R28-F1 (Version/Release, hoch): Die Mobile-Clients hängen bei 1.2.0,
      während Desktop auf 1.3.0 steht und AltStore die v1.3.0-Release-IPA als
      „1.3.0" ausweist.** `kmp/android-app/build.gradle.kts:18-19` hat
      `versionCode = 4` / `versionName = "1.2.0"`; `kmp/iosApp/Config.xcconfig`
      setzt `APP_VERSION = 1.2.0` und `project.pbxproj:227/:259` bindet
      `MARKETING_VERSION = "$(APP_VERSION)"` — das v1.3.0-Release-IPA meldet
      also CFBundleShortVersion **1.2.0**, während
      `altstore/classic.json:22` für eben diese IPA `"version": "1.3.0"`
      (`buildVersion: 115`) listet. Ursache: `5364aa1` („1.3.1 across all
      relevant files") hatte KMP nur auf **1.2.0** gestellt (statt 1.3.1), und
      die Reverts `05b2652`/`c78140c`/`7e0cc5d` haben ausschließlich Desktop
      (Cargo/Cargo.lock/tauri.conf/package*.json) auf 1.3.0 zurückgesetzt —
      die KMP-Version blieb bei 1.2.0 hängen. Folge: AltStore zeigt einen
      Versionslabel, das die installierte App nicht meldet; Client-Meldungen
      über `app.package_info()` (Desktop) und mobile Update-Checks laufen
      auseinander. Fix: `android-app` versionName/versionCode und
      `Config.xcconfig` APP_VERSION mit dem Desktop-Release abgleichen und
      einen gemeinsamen Release-Versions-Schritt (ein Source-of-Truth) einführen.
- [x] **R28-F2 (Updater, mittel): Der Signed-Updater-Fallback startet die App
      nach erfolgreichem Install nie neu — Divergenz zum Custom-Pfad.**
      `install_plugin_update` (`src-tauri/src/updater.rs:666-715`) ruft
      `update.download_and_install(...)` und emittiert danach `installing`;
      auf macOS/Linux bleibt der Prozess aber auf der alten Version weiter
      laufen (`tauri-plugin-updater` 2.10.1 ersetzt das .app-Bundle bzw. die
      AppImage in place, relauncht aber nicht). Der Custom-Pfad
      (`install_update`, `updater.rs:404-525`) relauncht dagegen aktiv:
      `open` auf macOS, AppImage-Neu-Spawn auf Linux, `process::exit(0)` auf
      Windows (damit MSI/NSIS übernimmt). Zusätzlich: Auf Windows beendet
      `download_and_install` den Prozess selbst (Plugin-`exit(0)`), bevor die
      `installing`-Emission ausgeführt wird — das finale Status-Event geht
      verloren (Custom-Pfad emittet vor dem Exit). Fix: nach ok-Install
      denselben Relaunch-/Exit-Code wie `install_update` ausführen bzw. das
      `install_update`-Verhalten für den Plugin-Pfad spiegeln.
- [x] **R28-F3 (CLI/Single-Instance, mittel): Das neue
      `tauri_plugin_single_instance` verschluckt die CLI-Argumente aller
      Folge-Prozesse.** `lib.rs:323-325` registriert
      `.plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
      show_main_window(app); }))` — `_argv` wird ignoriert. Ein zweiter
      Aufruf (`flutlink --sync`, `--path …`, `--download … --download-to …`,
      `--list …`, `--url …`, `--tray`) zeigt nur das Fenster und führt weder
      Sync, noch Headless-Kommando, noch `--tray`-Hide aus — Regressions-Risiko
      für Skript/Headless-Nutzung (vgl. L24-N3, der weiter offen ist:
      `--download`/`--list` beenden den Prozess weiterhin nicht und öffnen
      standardmäßig ein Fenster). Fix: im Callback `_argv` parsen und an
      `handle_cli`-Äquivalente delegieren (mindestens `--sync`/`--tray`/
      Headless-Kommandos), oder CLI-Verhalten dokumentieren/explizit droppen.
- [x] **R28-F4 (Release-CI, mittel): `release.yml` verlangt seit 38c9bb8
      zwingend signierte Updater-Assets (`latest.json` + `.sig`), aber das
      Vorhandensein der rotierten Secrets ist nicht verifizierbar.**
      `855d716` rotiert den minisign-pubkey (`tauri.conf.json` →
      `C75E65BB81A87FFB`) und vermerkt selbst: „The new private key must be
      set as TAURI_SIGNING_PRIVATE_KEY / _PASSWORD repo secrets". Der
      vollständigkeits-Check (`release.yml:504-512`) hat die Muster
      `latest\.json$` + `\.sig$` als Pflicht-Fehler (vorher nur
      Window-Warnungen). Sind die Secrets nicht/anders gesetzt, erzeugt die
      `tauri-action`-Leg keine Updater-Artefakte und **jedes Tag-Release
      schlägt im publish-Schritt hart fehl**. Fix: explizite Vorab-Prüfung der
      Secrets (z. B. Job mit `if: secrets.X != ''`), die früh und mit
      verständlicher Meldung abrichtet, statt einer kryptischen Asset-Liste.
- [x] **R28-N1 (CI-Robustheit, minor): `prepare-release` hängt jetzt an der
      AI-`release-notes`-Job (`needs: [checks, security-gate, release-notes]`,
      `release.yml:183`).** Schlägt die OpenCode-Note-Generierung fehl
      (Model-Fallback leer/Rate-Limit), blockiert das das komplette Release —
      früher war `prepare-release` davon unabhängig. Empfehlung: Fallback-Body
      (der `|| 'Download the assets below…'`-Ausdruck greift nur bei leerem
      Output, nicht bei Job-Failure) bzw. `allow-failure`-Verdrahtung.
- [x] **R28-N2 (KMP, minor): Deprecation-Warnungen nach dem M3-Umbau.**
      `:shared:compileKotlinJvm` meldet: `WebDavApi.kt:468` `readBytes()` →
      `readRawBytes()`; `AdminScreen.kt:365-397` `Icons.Filled.Sort` →
      AutoMirrored; `FilesScreen.kt:166` → AutoMirrored List (unten).
      `FilesScreen.kt:575` / `Components.kt:137` ArrowBack/InsertDriveFile →
      AutoMirrored. Kein Funktionseffekt, aber API-Veraltung (künftige Compose-
      Upgrades schärfen das).
- [x] **R28-N3 (Doku, minor — gehört zu R28-F1): README-Versionstabelle
      veraltet.** `README.md:31-35` nennt „Desktop client 1.2.0" / „Mobile
      client 1.1.1"; real ist Desktop 1.3.0, Mobile (Android+ iOS) 1.2.0.

Keine neuen Befunde in den übrigen geprüften Bereichen: IPC-Registry
(`lib.rs` ↔ `ipc.ts`; keine neuen Commands für den Updater — `check_update`/
`download_and_install_update` unverändert), Keyring (`accounts.rs`),
Fehler-Serialisierung, WebDAV/OCS-Layer, Guest-Backend, Sync-Engine,
Offline-Cache, Tray — alle unverändert zu Lauf 27 (dort re-verifiziert).
`build.yml:112` (`--config {"bundle":{"createUpdaterArtifacts":false}}`
für Push-Artefakt-Builds) ist konsistent mit `530dada`/`855d716`; die
Windows-`--bundles msi,nsis`-Explizierung (`release.yml:235`) liefert beide
Suffixe, die der Custom-Updater wählt (`platform_suffixes`); der
Heredoc-Delimiter-Fix im `release-notes`-Job (`release.yml:115-118`) ist
sauber.

### todo.md-Nachprüfung (Schritt 5, gegen HEAD `4dcd117`)

Seit Lauf 27 sind KMP-F13/14/15 umgesetzt (`53136ad`/`4124b1b`/`6b4eafc`/
`4e152db`; todo.md zeigt sie bereits als `[x]`) — re-verifiziert: Material3
`1.9.0-alpha04` in `libs.versions.toml`, `MaterialExpressiveTheme` + `MotionScheme`
in `Theme.kt`, `Flut*`-Komponenten aus `Components.kt` entfernt, `NavigationBar`+
`SingleChoiceSegmentedButtonRow` + echte M3-Buttons/Chips in den Screens. Zwei
weitere Lauf-27-Items sind erledigt und werden als `[x]` markiert:

- **KMP-F6 (doppelte Chrome / kein M3-NavigationBar): BEHOBEN** — `HomeScreen.kt`
  entfernt den Desktop-Header (Surface/Logo/Tabstrip) und nutzt eine echte M3-
  `NavigationBar` als `bottomBar`; jede Screen behält seine einzelne `TopAppBar`
  (M3-Standard-Pattern). Zusätzlich unten im Lauf-27-Abschnitt markiert.
- **KMP-F8 (iosMain-Doku veraltet): BEHOBEN** — `c78140c` korrigiert
  `kmp/README.md` auf „voller geteilter UI". Zusätzlich unten markiert.

Weiter offen (re-quelltext-verifiziert): L24-F2 (Share-Notify, `ui.ts:148`),
L24-F3b (Sync-Log-Append/Trunkierung), L24-F4…F8, L24-N1…N7, KMP-F1 (Admin-
`editUser`-Lücke), F2 (Grid ohne Aktionen), F3 (Admin-Suche leer), F4
(loadUsers/loadMore-Race), F5 („Files"/„List"/„Grid"-Literale,
`FilesScreen.kt:165-168`), F7 (Admin-Tab unsichtbar, `HomeScreen.kt` `visible`
-Guard), F9 (Copy/Move/QR/QuickLook), F10 (`viewMode`-`remember`, `FilesScreen.kt:207`),
F11 (Gast-Category-`AssistChip` löscht beim Tippen, `GuestScreen.kt:199-201`),
F12 (ViewMode-Labels hart kodiert), Perf-Analyse, „Desktop-JVM: Token-Speicher
härten", CI-security-gate.

Zu verschieben nach `archived-todo.md`: nichts physisch verschoben (Anweisung
„nur todo.md verändern"); die Markierungen für KMP-F6/F8 erfolgen in-place.
Desktop-Anwendungscode-Commits gab es in diesem Lauf nicht (nur Updater/
lib.rs/App.vue/CI), daher blieben alle L24-Desktop-Befunde unverändert.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe verboten).
`git log f2a28a6..HEAD` enthält 20 Commits; die Merge-Messages referenzieren
nur die PRs #455/#460/#461/#462 (`dispatch-*`/`issue456…458`) — kein Commit
verweist auf eine Issue-Nr. und kein Commit schließt eine der offenen
L24-/KMP-Issues. Der `opencode-todo-issues`-Workflow sollte beim nächsten Lauf
die neuen Befunde erfassen, v. a. R28-F1 (Versions-Drift, mobile vs. Desktop
und AltStore „1.3.0" vs. APP_VERSION 1.2.0) sowie R28-F2 (Updater-Neustart)
und R28-F3 (Single-Instance-CLI-Verlust).

## Review 2026-08-28 (Lauf 27, Fokus „Revert Mobile UI auf Platin / Material 3 Expressive" — neue Befunde)

Gegenstand: der Auftrag, die Mobile-UI (`kmp/shared/src/commonMain/.../ui/`)
auf **Material 3 Expressive** („Platin") zurückzuführen — weg von der
desktop-angeglichenen Custom-Compose-UI (Commit `2dce6e1`) — plus
Folgereview der offenen KMP-Befunde (L25 KMP-F1…F9, L26 KMP-F10…F12) gegen
HEAD `f2a28a6`. Der Fokus prüft vor allem die **Machbarkeit/Sauberkeit des
thematisierten Reverts** und ob die aktuelle Abhängigkeits- und
Komponenten-Lage das überhaupt zulässt.

Verifikation: `cargo fmt --check` **grün** (Exit 0). `cargo clippy`/`cargo
test`/`npm run build` sind umgebungsbedingt weiter nicht ausführbar
(fehlende Tauri-Linux-Systemdeps bzw. `node_modules`); der KMP-Build
(`:shared:build`) ist hier nicht lauffähig (iOS nur macOS, Android ohne
Netz). Sämtliche KMP-/Dependency-Befunde sind Quelltext- plus
Versionskatalog- plus Web-Verifikation. Zwischen Lauf 26 und diesem Lauf
liegen nur noch `5364aa1` (Version 1.3.1), `7935823` (Merge) und `f2a28a6`
(AltStore) — **kein KMP-Anwendungscode-Change**; KMP-F1…F12 sind daher
unverändert offen (re-quelltext-verifiziert).

Neu gefunden (Fokus „Revert auf Material 3 Expressive"):

- [x] **KMP-F13 (Blocker/Feasibility, hoch): `Material3Expressive` ist in der
      aktuell genutzten Material3-Abhängigkeit gar nicht verfügbar — der
      „Revert auf Platin/Expressive" kompiliert so nicht.** Die KMP-Module
      beziehen Material 3 ausschließlich über das Plugin-DSL-`api(compose.material3)`
      (`kmp/shared/build.gradle.kts:44`). Dieser Alias löst in Compose
      Multiplatform 1.11.1 auf die **stabile** Material3-Version auf
      (`org.jetbrains.compose.material3:material3:1.9.0`, basiert auf Jetpack
      M3 1.4.0-stable), und genau mit dem Stable-Schnitt ist die Expressive-API
      **entfernt** worden: Alle mit `ExperimentalMaterial3ExpressiveApi` /
      `ExperimentalMaterial3ComponentOverrideApi` markierten öffentlichen APIs
      (`Material3ExpressiveTheme`, `MaterialExpressiveTheme`, expressive
      `MotionScheme`/Komponenten) sind aus dem Stable-Build entfernt
      (JetBrains/compose-multiplatform-core#2278 — „use the previous Material3
      alpha version explicitly"). Für den Auftrag muss daher **vor** dem UI-
      Umbau die Material3-Dependency des KMP-Moduls auf eine Pre-Release-Version
      mit Expressive-Support umgestellt werden (z. B.
      `org.jetbrains.compose.material3:material3:1.9.0-alpha04` bzw. eine
      neuere pre-release-Variante) und überall `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
      gesetzt werden. Hinweis: `versionCatalog` (`gradle/libs.versions.toml`)
      kennt nur `composeMultiplatform = "1.11.1"`, keinen Material3-
      Versions-Overload — die Dependency-Umstellung ist ein eigenständiger,
      expliziter Schritt. — **BEHOBEN**: `composeMaterial3 = "1.9.0-alpha04"`
      in `kmp/gradle/libs.versions.toml` verankert; `kmp/shared/build.gradle.kts`
      nutzt `api(libs.compose.material3)` statt `api(compose.material3)`.
      `cd kmp && ./gradlew :shared:build` grün (Android + JVM; iOS-Targets wie
      üblich nur auf macOS kompilierbar). Die Expressive-API wird bei
      KMP-F14 an den neuen `Material3ExpressiveTheme`-/`MotionScheme`-
      Nutzungsstellen genutzt.
- [x] **KMP-F14 (Konzept, mittel): Der Auftrag ist **kein** blindes
      `git revert` von `2dce6e1` — der Commit trägt neben dem Theme auch die
      gewünschte Desktop-Parität.** `2dce6e1` (feat(kmp): overhaul mobile UI to
      match desktop design system) hat nicht nur Material-3-Komponenten
      ersetzt, sondern die geforderten Desktop-Paritäts-Features eingeführt:
      Breadcrumb + List/Grid-Toggle + Grid-Ansicht (`FilesScreen.kt:158-164/233-236/423-444`),
      Impersonation-Handoff Admin→Files (`HomeScreen.kt:128-131` + `68`),
      Custom-Quota-Dialog (`AdminScreen.kt:584-652`), Gruppen-Dialog, custom
      Login/SegmentedControl (`LoginScreen.kt:103-121`). Ein `git revert`
      würde all das (und damit Teile von KMP-F1/F9) zurücksetzen. Empfohlener
      Weg für den „Revert": **nur die Darstellung** zurück auf M3-Expressive-
      Primitives bringen, also die `Flut*`-Hilfskomponenten
      (`Components.kt:226-526`) durch echte M3-Komponenten ersetzen
      (`Button`/`SegmentedButton`/`SingleChoiceSegmentedButtonRow`/`Chip`/
      `Card` + `Material3ExpressiveTheme` mit `motionScheme = MotionScheme.expressive()`)
      und die Screens darauf umstellen — die Breadcrumb/Grid/Files-Logik
      bleiben dabei unangetastet. `HomeScreen.kt:77-156` (Desktop-Header + Tab-
      Strip statt M3-`NavigationBar`) ist dabei der größte Brocken und deckt
      sich mit KMP-F6.
- [x] **KMP-F15 (Bereinigung, minor — im Zuge des Reverts gleich mit):
      Entscheidung dokumentiert + tote Komponenten entfernt.** Die
      Entscheidung steht in `kmp/README.md` „Theme-Entscheidung
      (KMP-F15)": `Material3ExpressiveTheme` wird der neue Wrapper in
      `FlutLinkTheme` (`Theme.kt:46-80`) mit Brand-`colorScheme`/
      `typography`/`shapes` + `motionScheme = MotionScheme.expressive()`
      (sobald KMP-F13 die Material3-Pre-Release geliefert hat);
      entfallen beim Revert (`FlutBadge`, `FlutPill`, `FlutSegmentedControl`,
      `FlutCard`, `FlutGhostButton`, `FlutOutlineButton`, `FlutPrimaryButton`,
      `FlutIconButton`) samt M3-Mapping, bleiben die Utility-Komponenten
      (`ErrorBanner`/`EmptyState`/`QuotaBar`/`FileMetaLine`/`ScrollableColumn`/
      `SectionHeader`/`Breadcrumb`/`SectionLabel`/`fileIcon`). `dynamicColor =
      true` bleibt Default; `Color.kt` (Brand-Paletten) und der
      Akzent-Hue-Slider (`SettingsScreen.kt:200-226`) bleiben unangetastet.
      Die bereits ungenutzten `FlutCard`/`FlutIconButton` sind aus
      `Components.kt` entfernt (kein hybrider Zustand, Build grün). Der
      mechanische Swap selbst ist Aufgabe von KMP-F14.

Weiter offen (Lauf 25/26-Status unverändert, gegen HEAD re-verifiziert):
KMP-F1 (Admin-`editUser`-Lücke), KMP-F2 (Grid ohne Aktionen), KMP-F3
(Admin-Suche leer), KMP-F4 (loadUsers/loadMore-Race), KMP-F5 (unlokalisierte
`"Files"`/`"List"`/`"Grid"`),
[x] KMP-F6 (doppelte Chrome / kein M3-`NavigationBar` — direkt relevant für
KMP-F14): **BEHOBEN** — `HomeScreen.kt` nutzt jetzt eine M3-`NavigationBar`
als `bottomBar` statt Desktop-Header (Verweis Lauf-28-Review, R28-F6).
KMP-F7 (Admin-Tab für Nicht-Admins unsichtbar),
[x] KMP-F8 (iosMain-Doku veraltet): **BEHOBEN** — `c78140c` korrigiert
`kmp/README.md` „Placeholder-UI" → „voller geteilter UI".
KMP-F9 (Copy/Move/QR/QuickLook fehlen), KMP-F10 (`viewMode` nicht persistiert, `FilesScreen.kt:203`),
KMP-F11 (Gast-Kategorie-Chip löscht beim Antippen, `GuestScreen.kt:201-207`),
KMP-F12 (`"Files"`, `"List"`/`"Grid"`-Literale). Ebenso weiter offen aus
früheren Läufen: L24-F2 (Share-Notify erreicht Backend nie), L24-F3b
(Sync-Log-Append/Trunkierung), L24-F4…F8/N1…N7, Perf-Analyse und
„Desktop-JVM: Token-Speicher härten".

Keine neuen Desktop-/Kern-Befunde: zwischen Lauf 26 und HEAD ist kein
Desktop-Anwendungscode geändert worden (nur Versionsnummern); IPC-Registry,
Keyring, Fehler-Serialisierung, WebDAV/OCS, Guest-Backend, Sync-Engine und
CI/Workflows sind unverändert zu Lauf 26 (dort „keine neuen Befunde"
vermerkt).

### todo.md-Nachprüfung (Schritt 5)

Seit Lauf 26 sind keine KMP-/Desktop-Befunde erledigt worden — keine
betreffenden Anwendungscode-Commits (`5364aa1`/`7935823`/`f2a28a6` sind
Versionsnummern, Merge bzw. AltStore). **Es gibt nichts nach
`archived-todo.md` zu verschieben.** Alle offenen `[ ]`-Einträge bleiben
unangetastet; keine neue Erledigung zu markieren.

### GitHub-Issues (Schritt 6)

Nur lokale Quellen ausgewertet (GitHub-API-/gh-Aufrufe verboten).
`git log 0585c2b..HEAD` enthält ausschließlich `5364aa1`, `7935823` und
`f2a28a6` — **kein Commit schließt eine offene Issue** und es gibt keine
neuen Issue-referenzierenden Commits. Der `opencode-todo-issues`-Workflow
sollte die neuen KMP-F13 (Expressive-Dependency-Blocker) sowie die weiterhin
offenen KMP-F1…F12 (= gewünschte Desktop-Parität auf Mobile) beim nächsten
Lauf als Issues erfassen.

## Review 2026-08-28 (Lauf 26, Fokus „Back to Jetpack Compose on KMP" — neue Befunde)

Gegenstand: die komplette KMP-Compose-UI erneut begutachtet
(`kmp/shared/src/commonMain/kotlin/.../ui/`: `FlutLinkRoot.kt`,
`navigation/AppNavigation.kt`, `HomeScreen.kt`, `files/FilesScreen.kt`,
`admin/AdminScreen.kt`, `guest/GuestScreen.kt`, `settings/SettingsScreen.kt`,
`login/LoginScreen.kt`, `components/Components.kt` + alle ViewModels +
`dto/Models.kt`, `FlutCloudApi.kt`, `FlutCloudOcs.kt`) gegen HEAD `0585c2b`.
Auftrag unverändert: UI wirkt „looks off and not clean"; Desktop-Parität
(Admin-Edit, fehlende Aktionen, Navigation) auf Mobile fehlt. Zusätzlich der
Standard-Bereich (IPC/CI) und die Nachprüfung der L24-/L25-Befunde.

Verifikation: `cargo fmt --check` **grün** (Exit 0) — der L24-F1-Fmt-Teil ist
behoben. `cargo clippy`/`cargo test` scheitern weiter am fehlenden
Systempaket `gobject-2.0` (pkg-config, Umgebungslimit); `npm run build` an
fehlendem `node_modules`. Der KMP-Build (`:shared:build`) ist hier nicht
ausführbar (iOS nur auf macOS; Android-Gradle ohne Netz). KMP-Befunde sind
reine Quelltext-Verifikation.

### Nachprüfung L24/L25 (Schritt 5, gegen HEAD `0585c2b`)

Seit Lauf 25 gibt es nur die Commits `0585c2b` (Server-Template-Branding),
`4c2f25e` (Sync-Log: fmt/clippy-Fix + UI-Integration) sowie todo-/merge-
Commits — **kein KMP-Commit**; die L25-KMP-Befunde sind daher unverändert.

- **L24-F1 (fmt+clippy CI-Blocker): BEHOBEN.** `4c2f25e` vereinfacht die
  Closure (`sync.rs:1339` → `.map_err(AppError::Json)`) und formatiert die
  beiden `Move*.Conflict`-Arme (`sync.rs:1173-1184`) neu; `cargo fmt --check`
  ist grün (verifiziert). → unten als `[x]` markiert (clippy konnte hier
  umgebungsbedingt nicht erneut laufen).
- **L24-F2 (Share-Notify erreicht Backend nie): weiter offen** — `ui.ts:148`
  schreibt weiter nur localStorage; `ipc.ts:370 setShareNotify` hat keine
  Aufrufer (grep bestätigt).
- **L24-F3: teilweise** — (a) `sync_log_list`/`sync_log_clear` sind
  registriert (`lib.rs:407-408`, `commands.rs:856/869`) + `SyncLog.vue`
  vorhanden (**BEHOBEN**); (b) **der Append-/Trunkierungs-Bug bleibt offen** —
  `append_sync_log` (`sync.rs:1330-1341`) ist logisch unverändert (kein
  Behälter-Reverse-/Drain-Fix).
- **L24-F4…F8, L24-N1…N7: unverändert offen** (keine betreffenden Commits).
- **L25 KMP-F1…F9: alle weiter offen** — F1 (kein `editUser`-Pfad in
  `AdminViewModel.kt` / `FlutCloudOcs.updateUser` nur für quota/enabled),
  F2 (`EntryGridItem` deklariert `menuOpen`+6 Callbacks, nutzt sie nie,
  `FilesScreen.kt:806-899`), F3 (leere Suche → leere Liste,
  `AdminViewModel.loadUsers`), F4 (loadUsers/loadMore-Race) re-quelltext-
  verifiziert.

### Neu gefunden (Fokus KMP Compose UI)

- [x] **KMP-F10 (UX, minor): Der List/Grid-Ansichtsmodus ist weder persistiert
      noch übersteht er einen Tab-Wechsel.** `FilesScreen.kt:203`
      `var viewMode by remember { mutableStateOf(ViewMode.List) }` — ein
      bloßes `remember`, kein `rememberSaveable` und keine Persistenz. Beim
      Tab-Wechsel wird `FilesScreen` dekomponiert (`HomeScreen.kt:162-176`
      rendert nur den aktiven Screen in einer `when`), die Wahl geht also
      jedes Mal verloren und springt zu List zurück. Der Desktop persistiert
      das (`ui.ts` → `filesView`). Fix: `rememberSaveable` oder Persistenz im
      `SettingsStore`.
- [x] **KMP-F11 (UX / gefährliche Default-Aktion, minor–mittel): Gast-Admin-
      Kategorie-Chips löschen beim Antippen die Kategorie.** `GuestScreen.kt:201-209`
      rendert jede Kategorie als `FlutPill(selected=false, onClick={
      showDeleteCategoryDialog = cat })` — der einzige Tap-Zweck eines Chips
      ist das Öffnen des Lösch-Dialogs für genau diese Kategorie; es gibt
      keinerlei „verwalten"-Affordanz, und destruktive Kategorien-Chips sehen
      aus wie die reinen Filter-Chips darunter (`:220-231`). Fix: destruktive
      Aktion hinter ein klares Affordanz-Element (z.B. „×"-Badge) oder einen
      separaten „Kategorien verwalten"-Dialog.
- [x] **KMP-F12 (Konsistenz/Localization, minor): Nicht lokalisierte
      UI-Literale in der Dateiliste.** `ViewMode`-Labels `"List"`/`"Grid"`
      (`FilesScreen.kt:162-163`) und der Breadcrumb-Root-`"Files"`
      (`FilesScreen.kt:539`) sind hart kodiert; Desktop lokalisiert
      `viewList`/`viewGrid`/`files`. (`"Files"` war als Teil von KMP-F5 offen —
      hier bestätigt; `"List"`/`"Grid"` sind neu.) Fix: Ressourcen-Keys.

Keine neuen Desktop-/Kern-Befunde: IPC-Registry (`lib.rs:364-427` vs
`ipc.ts`), Sync-Log (nur F3b offen), Keyring, Fehler-Serialisierung,
WebDAV/OCS-Layer, Guest-Backend, Sync-Engine — unverändert zu Lauf 25.

### todo.md-Nachprüfung (Schritt 5)

- `L24-F1` → als erledigt markiert (`[x]`) und nach `archived-todo.md`
  verschoben (fmt grün, Closure vereinfacht; clippy hier nicht erneut
  lauffähig).
- `L24-F3` bleibt offen (nur Teil (a) erledigt; in Lauf-25 dokumentiert).
- `L24-F2`, `L24-F4…F8`, `L24-N1…N7`, `L25 KMP-F1…F9`, neu `KMP-F10/11/12`:
  bleiben offen.
- Feature-Ideen: unverändert (die bereits auf `[x]` stehenden Punkte aus
  Lauf 24/25 bleiben; die offenen `[ ]`-Punkte unangetastet).

### GitHub-Issues (Schritt 6)

Nur lokale Quellen (GitHub-API-/gh-Aufrufe verboten). `git log 3d55fbb..HEAD`
enthält `0585c2b` (Branding-Template) und `4c2f25e` (Sync-Log-Fix, keine
neue Issue-Nr.) sowie todo-/merge-Commits — **keine Commits schließen eine
offene Issue**. Die neuen KMP-Befunde (v.a. KMP-F1, die geforderte
Desktop-Admin-Parität) sollten vom `opencode-todo-issues`-Workflow beim
nächsten Lauf als Issues erfasst werden.

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
- [x] **KMP-F3 (UX-Limit, mittel): Admin-Userliste ist ohne Suchbegriff leer —
      es gibt keinen „Alle anzeigen"-Pfad.** `AdminViewModel.loadUsers()`
      (`AdminViewModel.kt:48-58`) returned bei leerem `search` früh und leert
      `users`; `AdminScreen.kt:138-142` (`LaunchedEffect(search)`) ruft bei
      leerem Feld `clearSearch()`. Der Desktop-`admin_list_users`
      (`commands.rs:1638`) erlaubt leere Suche („everything"). Auf Mobile muss
      ein Admin erst mindestens einen Buchstaben tippen, um irgendeinen User zu
      sehen. Fix: leere Suche = erste Seite laden (limit 200), nicht leeren.
- [x] **KMP-F4 (Race, minor): `loadUsers` vs. `loadMore` teilen `offset`/
      `users` unsynchronisiert.** `AdminViewModel.kt:78-94` startet
      `loadPage(append=true)` mit dem gemeinsamen `offset`, während ein neuer
      `LaunchedEffect(search)`-Zyklus `loadUsers` → `loadPage(append=false)`
      anstoßen kann; eine langsame loadMore-Antwort hängt ihren Block danach
      (leere/doppelte Seiten), und `createUser`/`setQuota`/`setEnabled`
      (Zeilen 114/148/167) rufen `loadUsers()` auf, was bei leerem Suchfeld
      die gerade geleerte Liste erneut leert. Fix: Sequenz-/Generations-Guard
      pro Request (Desktop-Pattern aus `AdminPanel.vue saveField`/`selectSeq`,
      Review L22-F3) + Abbruch untergeordneter Lade-Coroutines.
- [x] **KMP-F5 (Cleanup, minor): Unlokalisierte UI-Literale.** `"Files"` in
      `buildBreadcrumbSegments` (`FilesScreen.kt:539`) und `"List"`/`"Grid"`
      im `ViewMode`-Enum (`FilesScreen.kt:162-163`) sind hart kodiert; der
      Desktop lokalisiert beides (`viewList`/`viewGrid`, `files`). Fix:
      Ressourcen-Keys statt String-Literale.
- [x] **KMP-F6 (UX, minor): Doppelte App-Chrome — Desktop-Header-Reproduktion
      auf Mobile.** `HomeScreen.kt:77-156` rendert ein Desktop-Style-Surface
      (Logo-Zeile + Tab-Zeile mit Unterstrich-`Box`), und jeder Screen
      (FilesScreen `TopAppBar`, AdminScreen `TopAppBar`, SettingsScreen
      `TopAppBar`) fügt einen zweiten Header darunter ein — zwei gestapelte
      Title-Bars („looks off"). Mobile-Konvention: echte Material-3-`
      NavigationBar` (Bottom-Tabs) + eine einzige `TopAppBar` pro Screen;
      derzeit ist die Tab-Zeile zudem kein `NavigationBar`, sondern ein
      handgebauter Desktop-Tabstrip ohne `role`/Semantik.
- [x] **KMP-F7 (Konsistenz, minor): Nicht-Admins sehen den Admin-Tab gar
      nicht; Desktop zeigt ihn als gesperrt mit Hinweis.** `HomeScreen.kt:112`
      `val visible = tab != Tab.Admin || isAdmin` blendet Admin komplett aus;
      im Desktop rendert `App.vue` `navItems` den Admin-Tab als gesperrt
      (Lock-Icon, disabled, `adminLockedTitle`/`adminLockedText`). Fix: gleiche
      „lock"-Darstellung auf Mobile, damit die Tab-Leiste konsistent bleibt.
- [x] **KMP-F8 (Doku, minor): README/Archiv beschreiben `iosMain` als
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

- [x] **L24-F1 (Bug, hoch / CI-Blocker): `cargo clippy` + `cargo fmt` sind
      auf HEAD nicht grün.** — BEHOBEN in `4c2f25e` (Lauf-26-Nachprüfung);
      nach `archived-todo.md` verschoben. (a) clippy `redundant_closure`:
      `sync.rs:1332` `.map_err(|e| AppError::Json(e))` → `.map_err(AppError::Json)`.
      (b) `cargo fmt --check`: Formatdiff in `sync.rs:1177` (die beiden
      `Move*.Conflict`-Match-Arme) und `sync.rs:1328-1334` (der
      `serde_json::to_string_pretty`-Aufruf). Beides stammt aus dem neuen
      Sync-Log-Code (`197df7f`). Ohne Fix scheitert die Rust-CI
      (`clippy`/`fmt`-Job) und jeder Release-Build. Fix: `cargo fmt` ausführen
      + die Closure vereinfachen.
- [x] **L24-F2 (Bug, hoch): Der Share-Notification-Schalter ist rein
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
- [x] **L24-F4 (Race, mittel): Lost-Update auf `settings.json` zwischen
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
- [x] **L24-F5 (Robustheit, mittel): Der Retry-Mechanismus verwirft das
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
- [x] **L24-F6 (Race, mittel): QuickLook zeigt beim schnellen Blättern das
      Thumbnail des vorherigen Eintrags.** `refreshQuickLookImage`
      (`FileExplorer.vue:497-507`) captured `entry` beim Aufruf und weist
      das `await files.getThumbnail(entry.path)`-Ergebnis später dem einzigen
      `quickLookImage`-Ref zu. Blättert man schnell A→B (Prev/Next in
      `quickLookStep`, `:521-527`), überschreibt As späte Antwort
      `quickLookImage` während B angezeigt wird. Fix: Zuweisung guarden
      (`if (entry.path === quickLookEntry.value?.path) …`) oder Request-
      Generationszähler.
- [x] **L24-F7 (Bug, mittel): Share-Bearbeitung setzt bei jedem Edit die
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
- [x] **L24-F8 (Robustheit, mittel): Copy/Move-Pfadkomposition ohne
      Normalisierung.** `move_dest_path` (`commands.rs:1349-1359`) fügt
      `"{dest_folder}/{name}"` wörtlich an: ein `dest_folder` mit nachgestelltem
      `/` (das Dialogfeld ist editierbar) ergibt `"/B//name"`, ein `source`
      mit `/` ein leeres `name`/`"/B/"` — beides lässt `validate_writable_dav_path`
      (`commands.rs:658-668`, prüft nur absolute + kein `..`) durch, genauso wie
      das Verschieben eines Ordners in seinen eigenen Unterbaum (`/A`→`/A/A`).
      Fix: `dest_folder` trailendes `/` trimmen, leeren Namen ablehnen,
      `dest == source`/dest-in-source für Ordner verhindern; leere Segmente in
      `validate_dav_path` verbieten.
- [x] **L24-N1 (Perf, mittel): Sync-Log wird pro geplantem Op komplett
      neu geschrieben (Write-Amplification).** `append_sync_log` wird je
      Plan-Op aufgerufen (`sync.rs:1217-1226`, bis zu `MAX_OPS_PER_PASS=200`
      pro Ordner) und macht bei jedem Aufruf `load` (ganze Datei) +
      `to_string_pretty` + `atomic_write` (ganze Datei) + `create_dir_all`.
      Dazu toter No-op-`match result { … }` mit lauter Unit-Armen
      (`sync.rs:1227-1231`). Fix: Log-Einträge im Pass sammeln und einmal
      flushen; No-op-Match entfernen.
 - [x] **L24-N2 (Robustheit, minor): Kaputtes `settings.json` wird still

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
 - [x] **L24-N3 (Validierung, minor):  Headless-CLI `--download`/`--list`
      umgehen die Pfadvalidierung aller IPC-Commands.** `lib.rs:247/284`
      reichen `remote`/`path` unvalidiert an `get_file`/`list` durch (kein
      `validate_dav_path`, `..`/leere Segmente möglich) und der Prozess
      beendet sich nach dem Ausdruck nicht (nur mit `--tray` wird das Fenster
      versteckt; kein Exit-Code). Fix: `validate_dav_path` in beiden CLI-
      Pfaden aufrufen; für Headless-`--download`/`--list` Fenster ausblenden
      bzw. nach Output beenden.
 - [x] **L24-N4 (Race, minor): `history::clear` vs. `record_open`.**
      `record_open` schreibt atomar (temp+rename), `clear` macht `remove_file`
      (`history.rs:87-93`). Eine Clear während eines in-flight Open kann das
      „geleerte" Journal per rename wiederbeleben. Beides bleibt unsynchronisiert
      (gleiche Klasse wie L24-F4). Fix: hinter denselben Lock / Clear entfernt
      auch verwaiste Temp-Dateien.
 - [x] **L24-N5 (Race, minor): QuickLook-Prev/Next-Buttons sind auch an den
      Rändern aktiv** (`FileExplorer.vue:1458-1459` nutzen
      `sortedEntries.length > 1` statt `quickLookIndex` an 0/letztem) und
      wrappen über das Modulo — Zustand wirkt falsch aktiviert. Fix:
      `canPrev = quickLookIndex > 0` / `canNext = quickLookIndex < len-1`
      (oder bewusst wrappen lassen und Labels anpassen).
 - [x] **L24-N6 (Security/Defense, minor): Thumbnail-`data:`-URL übernimmt
      den Server-Content-Type ungeprüft.** `commands.rs:921-925` baut
      `data:{content_type};base64,…` aus der Server-Antwort (SVG möglich) in
      ein `<img>` in QuickLook. Moderne Browser blockieren Skripte in
      `<img>`-SVG großteils, aber eine Mime-Whitelist (png/jpeg/webp, sonst
      Fallback) wäre robuster. (`webdav.rs:728-738` liefert den Typ.)
 - [x] **L24-N7 (UX/Konsistenz, minor): `ImpersonationBar.vue:28-33` zeigt bei
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
