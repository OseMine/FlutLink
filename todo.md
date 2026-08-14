# FlutLink Todo

Offene Punkte. Erledigte Punkte sind nach `archived-todo.md` verschoben.

## Review 2026-08-14 (Lauf 3, Fokus UI)

Details zu allen Punkten: `reports/review-2026-08-14.md` (Abschnitt „Lauf 3").
Alle Punkte aus Lauf 2 (F1–F10) sind weiterhin offen (kein Code-Change seit
Lauf 2).

- [ ] **U1 (Bug, mittel):** Umbenennen in Unterordnern schlägt immer fehl —
      `webdav_rename` (`src-tauri/src/commands.rs:466-467`) baut `new_path`
      ohne führenden Slash (`parent = path.rsplit('/').nth(1)` →
      `"Documents/neu.txt"`), `validate_dav_path` lehnt ab. Nur Root-Rename
      funktioniert. Fix: `path.rsplit_once('/')` / Slash beibehalten + Test.
- [ ] **U2 (Bug, UI):** Nach Konto-Wechsel über das UI erscheinen mehrere
      Konten als „aktiv" — `accounts.switchTo` (`src/stores/accounts.ts:90-101`)
      nutzt `sync()` statt `load()`, und `account_switch`
      (`src-tauri/src/commands.rs:260-273`) emittiert kein `accounts-changed`
      (nur der Tray-Pfad `lib.rs:115-128`). Fix: `await load()` oder Event
      emittieren.
- [ ] **U3 (Bug/UX, mittel):** Share-Link — bei `navigator.clipboard.writeText`
      -Fehler (v. a. Linux/WebKitGTK) zeigt `FileExplorer.vue:198-208`
      (`createLink`) ein ✗, obwohl der Share erstellt wurde; die URL ist dann
      verloren. Fix: URL bei Clipboard-Fehler trotzdem anzeigen (Toast/
      Klick-zum-Kopieren) oder `plugin-clipboard-manager`.
- [ ] **U4 (Bug, minor):** `uploadFiles` (`FileExplorer.vue:123-142`) setzt
      `busyPath = ""` → falsy → Re-Entry-Guards in `open`/`download` (Z. 94,
      109) greifen nicht; Mehrupload bricht beim ersten Fehler ab und der
      Erfolgs-Toast fehlt. Fix: Token/Set verwenden, Fehler pro Datei sammeln.
- [ ] **U5 (UX, minor):** „Sync now" (`SyncPanel.vue:59-62`) toastet „Sync
      started." auch wenn `sync.trigger()` (store `sync.ts:72-78`) einen
      Fehler verschluckt hat. Fix: Fehler propagieren, nur bei Erfolg toasten.
- [ ] **U6 (UX, minor):** Konto-Entfernung ohne Bestätigungsdialog
      (`AccountBar.vue:129-134`, `App.vue:60-70`) — im Gegensatz zu
      Datei-/User-Löschungen. Fix: `confirm()` + i18n-Key.
- [ ] **U7 (UX, minor):** „Add account" (`App.vue:111`) öffnet den LoginDialog
      im zuletzt gewählten Modus (Register bleibt Register, `LoginModal.vue:38-44`).
      Fix: `openLogin('login')` verwenden.

## Review 2026-08-14 (Lauf 2, v1.0.0-Bereitschaft)

Details zu allen Punkten: `reports/review-2026-08-14.md`.

- [ ] **F1 (Blockierer):** AdminPanel-Button „enabled" funktioniert nie —
      `adminEditUser(id, "enabled", …)` in `src/components/AdminPanel.vue:143`
      vs. `ADMIN_EDIT_KEYS`-Whitelist ohne `enabled` in
      `src-tauri/src/commands.rs:550-570`. Entweder `enabled` in die Whitelist
      (ocs::update_user unterstützt es) oder Button entfernen.
- [ ] **F2 (Blockierer):** Total-Timeout 60 s (state.rs:112-113) bricht große
      WebDAV-Transfers ab; 30 s (updater.rs:526-527) bricht
      Installer-Downloads ab. Auf `connect_timeout`/`read_timeout` umstellen
      (reqwest 0.13).
- [ ] **F4 (Blockierer):** Doppel-URL-Encoding in `ocs::create_share`
      (ocs.rs:262-273): `encode_segments` + `req.form()` → Shares für Pfade mit
      Leerzeichen/Umlauten/Sonderzeichen schlagen fehl. Raw-Pfad ins Formular,
      Roundtrip-Test ergänzen.
- [ ] **F5:** About-Tab zeigt hartkodierte Version „0.1.0"
      (SettingsModal.vue:237) — via `getVersion()` auflösen.
- [ ] **F3:** Updater räumt partielle Downloads bei Stream-Fehlern nicht auf
      (updater.rs, Stream-Schleife um 260-283, analog B18).
- [ ] **F6:** `load_accounts` verwirft still Konten bei fehlender/abweichender
      FLUTCLOUD_URL (accounts.rs:84-94) — Hinweis/Status an Frontend
      durchreichen.
- [ ] **F7:** Automatischer Update-Check beim App-Start (aktuell nur manuell in
      SettingsModal) — nicht-blockierendes Update-Banner.
- [ ] **F8:** Signing/Notarisierung nur dokumentiert (release.yml:116-141,
      Fallback ad-hoc/unsigned) + `opencode.yml` pinnt
      `anomalyco/opencode/github@latest` nicht (auf Tag pinnen).
- [ ] **F9:** SHA-256-Prüfung still übersprungen, wenn GitHub keinen
      Asset-Digest liefert (updater.rs:302-310) — Warnung/Log ergänzen.
- [ ] **F10:** Sync: gleiche lokale Ordnernamen kollidieren beim remote_path
      (commands.rs:618-633) — i18n-Fehltext präzisieren (kein Blocker).

## Offen

- Keine weiteren offenen GitHub-Issues (#6-#44 inkl. Wiki sind geschlossen).
