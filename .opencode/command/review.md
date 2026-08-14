---
description: FlutLink-Review ausführen und datierte Review-Abschnitte in todo.md schreiben
agent: reviewer
---

Führe das FlutLink-Review durch, wie im reviewer-Agent beschrieben:

1. Lies README.md, package.json, vite.config.ts, src/ (Komponenten, Stores,
   lib/ipc.ts) und src-tauri/ (lib.rs, commands.rs, state.rs, error.rs,
   nextcloud/webdav.rs, nextcloud/ocs.rs) sowie .github/Workflows. Falls eine
   todo.md existiert, lies sie ebenfalls.
2. Finde Bugs, fehlende Features und Verbesserungen. Verifiziere Befunde mit
   `cargo test --manifest-path src-tauri/Cargo.toml` und `npm run build`.
3. Schreibe den Review so früh wie möglich als datierten Abschnitt
   („## Review YYYY-MM-DD …") in todo.md und ergänze ihn während des Reviews
   schrittweise.
4. Hänge neue Befunde als datierten Abschnitt oben in todo.md an (falls
   vorhanden), ohne bestehende Einträge zu verändern.
5. Ändere KEINEN Anwendungscode, nur todo.md. Keine Findings ohne
   konkrete Datei-/Funktionsnamen. Antworte auf Deutsch.
