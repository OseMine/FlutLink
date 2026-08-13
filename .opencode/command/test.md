---
description: Full FlutLink check suite (fmt, clippy, tests, frontend build) and fix any failures
---

Führe die komplette Verifikations-Suite von FlutLink aus und behebe alle
Fehler, bis alles grün ist:

1. `cargo fmt --check --manifest-path src-tauri/Cargo.toml`
2. `cargo clippy --all-targets --manifest-path src-tauri/Cargo.toml -- -D warnings`
3. `cargo test --manifest-path src-tauri/Cargo.toml`
4. `npm run build`

Behebe gefundene Fehler im entsprechenden Code (Rust unter src-tauri/,
Frontend unter src/) und führe die Suite erneut aus, bis alle Schritte
fehlerfrei durchlaufen. Fasse die Ergebnisse am Ende zusammen.
