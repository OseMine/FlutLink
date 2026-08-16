# Entwicklung

## Befehle

| Aufgabe | Befehl |
| --- | --- |
| Dev-Server + App | `npm run tauri dev` |
| Frontend-Typecheck/Build | `npm run build` (`vue-tsc --noEmit` + `vite build`) |
| Rust-Format-Check | `cargo fmt --check --manifest-path src-tauri/Cargo.toml` |
| Rust-Lint | `cargo clippy --all-targets --manifest-path src-tauri/Cargo.toml -- -D warnings` |
| Rust-Tests | `cargo test --manifest-path src-tauri/Cargo.toml` |
| Icons neu generieren | `npm run tauri icon app-icon.png` |

**Verifikation vor Abschluss jeder Änderung:** alle vier ausführen —
`cargo fmt --check`, `cargo clippy -D warnings`, `cargo test`, `npm run build`.

## Konventionen

- **Vue** — `<script setup lang="ts">`, Kompositions-API, Pinia
  `defineStore`. `tsconfig` ist strict mit
  `noUnusedLocals`/`noUnusedParameters`; Code muss kompilierfrei sauber sein.
- **Neue Backend-Commands** — Funktion in `commands.rs` ergänzen, in `lib.rs`
  registrieren (`tauri::generate_handler!`) und einen typisierten Wrapper in
  `src/lib/ipc.ts` anlegen.
- **Serde** — Modelle nutzen `#[serde(rename_all = "camelCase")]`
  (Rust `snake_case` ↔ TS `camelCase`).
- **i18n** — Jeder neue UI-Text bekommt einen Schlüssel in `src/lib/i18n.ts`
  (en **und** de). Sprache und Design werden über `src/stores/ui.ts`
  (localStorage) persistiert. Designs sind CSS-Variablen in `src/style.css`
  unter `[data-theme]`.
- **Admin-Impersonation** — `webdav_list` akzeptiert optional `target_user`;
  das Backend verweigert Nicht-Admins (`AppError::Forbidden`) und setzt den
  `Impersonate-User`-Header in `webdav::list`.

## IPC-Vertrag

Typisierte Wrapper leben in `src/lib/ipc.ts`. Die maßgebliche Command-Liste
ist die `invoke_handler`-Registrierung in `src-tauri/src/lib.rs`. Events:
`sync-status` (Status-Payload), `flutlink:cli-open` (URL-String),
`sync-folders-changed`.

## Dokumentation pflegen

Die Doku liegt in `docs/` mit `en/`- und `de/`-Ordnern. Beide Sprachversionen
müssen synchron bleiben — aktualisiere beim Ändern einer Seite immer die
Gegenseite und halte den Index in `docs/README.md` aktuell.

## Release-Vorgang

1. **Version prüfen/anheben** (`package.json`, `src-tauri/Cargo.toml` +
   `Cargo.lock`, `src-tauri/tauri.conf.json`). Davon hängen die Asset-Namen
   (`FlutLink_<version>_…`) und der Tag (`v<version>`) ab (`release.yml`).
2. **Tag pushen** (`git tag v1.0.0 && git push origin v1.0.0`): `release.yml`
   baut die Binaries für alle Plattformen und lädt die Assets hoch.
3. **Draft manuell publizieren (R7-7):** `release.yml` veröffentlicht als
   Draft (`releaseDraft: true`). `check_for_update` überspringt Drafts und
   Prereleases (`updater.rs`), daher erhalten Bestandskunden das Update erst,
   wenn der Draft nach erfolgreichem Build **manuell** publiziert wird:
   GitHub → Releases → `v<version>` → „Publish release".

## CI

`.github/workflows/` führt bei jedem Push/PR Build, Lint und Checks sowie
automatisierte opencode-Reviews aus. Halte `cargo clippy -D warnings` grün —
die CI behandelt Warnungen als Fehler.
