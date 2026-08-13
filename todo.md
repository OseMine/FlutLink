# FlutLink Todo

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
