# Todo-Liste FlutLink

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