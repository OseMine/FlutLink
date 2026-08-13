# FlutLink Todo

## Review Lauf 2 2026-08-13 (Fokus: Features bis v1)

- [ ] **V1.1 — Bug:** `sync_add` (commands.rs:410–416) erlaubt kollidierende `remote_path` für gleichnamige lokale Ordner (`/home/a/Docs` + `/home/b/Docs` → beide `/FlutLink/Docs`); Duplikat-Check in `sync.rs::add_folder` (Z. 671–678) prüft nur `account_key`+`local_path`. Verifiziert per Logik-Replikation. Eindeutigen Remote-Namen ableiten oder `remote_path` in die Prüfung aufnehmen.
- [ ] **V1.2 — Bug:** Typ-Konflikt Datei↔Ordner in `sync.rs` (`decide` Z. 236–289, `exec_download` Z. 427–441): lokaler Ordner vs. Remote-Datei (mit Journal) → `DeleteRemote` (Remote-Datei wird gelöscht!); lokale Datei vs. Remote-Ordner → `Skip` (still ignoriert); Erst-Sync Ordner vs. Datei → Download bricht mit `File::create`-Fehler ab. Alle 3 Fälle verifiziert. Als Konflikt behandeln statt löschen/ignorieren.
- [ ] **V1.3 — Bug (Minor):** `sync.rs::set_paused` (Z. 716–732): Resume setzt `paused=false`, aber `state` bleibt „paused" bis zum nächsten Worker-Tick (bis 10 s). Bei Resume sofort auf „idle" setzen.
- [ ] **V1.4 — Feature:** `account_remove` (commands.rs:241–255) räumt Sync-Ordner des Kontos nicht ab; `sync.rs::run_all` (Z. 800–806) zeigt dann ewig „Account is no longer connected." Sync-Ordner (+ Journals) mitentfernen oder im UI als verwaist markieren/löschbar machen.
- [ ] **V1.5 — Feature:** Updater (`updater.rs::download_update` Z. 223–281) verifiziert den Download nicht (keine SHA-256-Checksumme); kein Auto-Update-Check beim Start (SettingsModal.vue `checkForUpdate` Z. 95–106). Checksumme prüfen, optional Auto-Check.
- [ ] **V1.6 — Feature (v1-Blocker):** Keine Dateioperationen im Dateibrowser: `FileExplorer.vue` `open` (Z. 21–23) tut bei Dateien nichts; `webdav.rs`-Helper (`put_file`/`get_file`/`delete`/`make_collection`) sind nicht als Commands exponiert (`commands.rs`, `ipc.ts`). Upload/Download/Öffnen/Rename/Mkdir/Delete bis v1. (Überlappt mit bestehendem B9/U2.)
- [ ] **V1.7 — Bug (Minor):** `flutcloud.rs::flutcloud_url` (Z. 17–28) cached den Fehlerfall in `OnceLock` — korrigierte `.env` wirkt erst nach Neustart. Nur Erfolgswert cachen.
- [ ] **V1.8 — Feature/Infrastruktur (v1-Blocker):** `release.yml` (Z. 121) baut macOS nur mit Ad-hoc-Signing (`APPLE_SIGNING_IDENTITY: "-"`), Windows ohne Code-Signing, keine Notarisierung. Developer-ID + Notarisierung (macOS) und Code-Signing (Windows) für Distribution einplanen.

## Review 2026-08-13 (automatisiertes Code-Review, Fokus UI + Backend)

- [ ] **F1/B1** Konto-Identifikation auf `username` + `instanceUrl` umstellen: `src/stores/accounts.ts` (Z. 51, 74), `src-tauri/src/state.rs` (`remove`/`set_active`), `src-tauri/src/commands.rs` (`account_switch`, `account_remove`). Bug: gleicher Username auf zwei Servern wird falsch behandelt.
- [ ] **B3** Eindeutige Konfliktkopie-Namen im Sync: `src-tauri/src/sync.rs` (`conflict_name`, `exec_upload_conflict`) — zweiter Konflikt überschreibt erste Kopie.
- [ ] **B2** `register_user` Teilfehler behandeln (Konto existiert bereits, wenn README/Projektordner-Erstellung fehlschlägt): `src-tauri/src/commands.rs` Z. 133–204.
- [ ] **F3** Race Condition im Dateibrowser: Requests sequenzieren + Loading-Feedback bei bestehenden Entries: `src/stores/files.ts`, `src/components/FileExplorer.vue`.
- [ ] **B4** Leere Ordner synchronisieren (walk_local sammelt nur Dateien; decide skippt leere Remote-Ordner): `src-tauri/src/sync.rs`.
- [ ] **B5** `statuses()` beim App-Start aus `sync-folders.json` initialisieren: `src-tauri/src/sync.rs` Z. 735–746.
- [ ] **B6** `delete_token`-Fehler in `account_remove` nicht verschlucken: `src-tauri/src/commands.rs` Z. 235.
- [ ] **B7** WebDAV-Transfer-Helper (`remote_url`) um `target_user`-Impersonation erweitern: `src-tauri/src/nextcloud/webdav.rs`.
- [ ] **B8** Whitelist für `admin_edit_user`-Keys (displayname/email/password/enabled/quota): `src-tauri/src/commands.rs` Z. 339–351.
- [ ] **B9** Transfer-/Datei-Commands exponieren (upload/download/delete/mkdir/rename/search) in `commands.rs` + `src/lib/ipc.ts` (Grundlage für UI-Dateioperationen).
- [ ] **B11** `tmp_path()` eindeutig machen für parallele Downloads: `src-tauri/src/nextcloud/webdav.rs` Z. 241–248.

### UI „Google Drive mit FlutLink-Farben"
- [ ] **U1** Dateibrowser: Grid/Listen-Umschalter, sortierbare Spalten, Mehrfachauswahl, Kontextmenü, Hover-Aktionen: `src/components/FileExplorer.vue`.
- [ ] **U2** Dateioperationen in der UI: Upload, Download/Öffnen, Umbenennen, Neuer Ordner, Löschen (Backend: B9).
- [ ] **U3** Zentrale FlutLink-Farbvariablen statt hartkodierter `indigo-*`-Klassen; helles Theme; `src/style.css`, `src/App.vue`.
- [ ] **F2** System-Theme-Auswahl implementieren oder entfernen: `src/App.vue` Z. 37–39, 49–51 (aktuell No-Op).
- [ ] **F4** Doppeltes „frei" in Speicheranzeige: `src/components/AccountBar.vue` Z. 44–52/122.
- [ ] **F5** Fehlende i18n-Keys: „Hide"/„Show" (`LoginModal.vue`), „Close" (`SettingsModal.vue`), „Email" (`AdminPanel.vue` Z. 353), „Home" (`src/stores/files.ts` Z. 14).
- [ ] **F6** Unbehandelte Promise-Rejection bei `accounts.remove` in `src/App.vue` Z. 185 (try/catch + Toast).

### CI / Repo
- [ ] **C1** `ci.yml`: Clippy mit `-D warnings` oder Job durch `.github/actions/checks` ersetzen.
- [ ] **C2** `ci.yml` und `build.yml` zusammenlegen (Redundanz).
- [ ] **C4** Gemergte Dependabot-Branches löschen (`dependabot/*`, PRs #1–#4) — erfordert `gh`-Login.
- [ ] GitHub-Aufräumung: offene Issues mit todo.md abgleichen, offene PRs reviewen/mergen (Blockiert: kein `gh`-Login/GH_TOKEN in Umgebung).

## Review vom 2026-08-13 (neue Befunde)

- [ ] **B1 — Bug:** `account_add` (commands.rs:47–61): Erneutes Hinzufügen des aktiven Kontos setzt `is_active=false`, `current()` fällt auf ein anderes Konto zurück. `is_active` nach dem `upsert` bestimmen oder Status vom ersetzten Eintrag übernehmen.
- [ ] **B2 — Bug:** `src/App.vue` `resolveTheme` (Z. 37–39): Theme „system" wird immer auf `operationflut` abgebildet, `prefers-color-scheme` wird ignoriert. Dunkel-Präferenz auswerten oder Option entfernen.
- [ ] **B3 — Feature:** `webdav_create_share` (commands.rs:264–268) unterstützt kein `target_user`; Share-Erstellung im Impersonations-Modus zielt auf das Admin-Konto. `target_user` ergänzen.
- [ ] **B4 — Feature:** `list_users` (ocs.rs:79) limitiert hart auf 200 Benutzer; Pagination über `offset` implementieren.
- [ ] **B5 — Bug:** `ocs_current_user` (commands.rs:353–357) ist registriert, hat aber keinen IPC-Wrapper in `src/lib/ipc.ts` und keine Frontend-Nutzung. Nutzen oder entfernen.
- [ ] **B6 — Bug:** Event `sync-folders-changed` (lib.rs:92, CLI `--path`) wird nirgends gelauscht; Sync-Panel zeigt neue Ordner erst nach Reload. Listener + `sync.load()` ergänzen.
- [ ] **B7 — Doku/Feature:** README (Z. 63) behauptet „change notifications"; es gibt keinen Dateisystem-Watcher (keine notify-Crate). Doku korrigieren oder Watcher ergänzen.
- [ ] **B8 — Bug (Minor):** `accounts.ts` `add()` (Z. 47–61): Nach B1-Szenario laufen Store und Backend beim aktiven Konto auseinander. Nach B1-Fix `add()` um Reload ergänzen.
- [ ] **B9 — Robustheit:** `webdav.rs` `list` (Z. 39–50): Wenn der Server `Impersonate-User` ignoriert, entstehen kaputte Pfade; Namespace der hrefs mit `effective_user` abgleichen und klaren Fehler werfen.
- [ ] **B10 — i18n:** `AdminPanel.vue` `setQuota` (Z. 124) und `createUser` (Z. 173): zusammengesetzte Fehlerstrings mit hartkodierten Trenner; eigene i18n-Schlüssel mit Platzhaltern.
- [ ] **B11 — Feature:** `sync.rs` `decide`/`walk_local`/`plan_ops`: Leere lokale Ordner werden nicht remote erstellt, leere Remote-Ordner nie gelöscht (nur Datei-Sync).
- [ ] **B12 — CI:** `ci.yml` (Z. 60): Clippy ohne `-- -D warnings`, inkonsistent zu `checks`/`lint`-Actions und AGENTS.md. Vereinheitlichen.
- [ ] **B13 — CI/Security:** `opencode.yml`: Kommentar-Trigger mit `id-token: write` + `contents: write`; Prompt-Injection-Risiko minimieren, `id-token: write` entfernen, Prompt so formulieren, dass Kommentar nur Aufgabenbeschreibung ist.
- [ ] **B14 — Security:** `tauri.conf.json` (Z. 23): `"csp": null`; CSP (`default-src 'self'`) setzen.
- [ ] **B15 — Cleanup:** `@tauri-apps/plugin-opener` ist registriert (lib.rs:116), aber im Frontend ungenutzt. Nutzen oder entfernen.
- [ ] **B16 — CI:** `ci.yml` (Z. 37–46): Linux-System-Dependencies doppelt installiert (auch in `setup`-Action); auf die Action umstellen.
- [ ] **B17 — Robustheit:** `accounts.rs` `save_token`/`load_accounts`: Ohne Secret-Service (Linux) ist die App unbenutzbar; Hinweis/Fehlermeldung für Linux-Nutzer ergänzen.
- [ ] **B18 — Robustheit:** `webdav.rs` `get_file` (Z. 147–159): `.flutlink-<pid>.tmp`-Reste bei Stream-Fehlern nicht aufgeräumt; `remove_file(&tmp)` im Fehlerpfad ergänzen.
