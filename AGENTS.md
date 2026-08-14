# FlutLink — FlutCloud Desktop Client

FlutLink ist ein Tauri v2 Desktop-Client für den FlutCloud-Server (Sync-,
Freigabe- und Admin-Funktionen). FlutLink ist **kein** generischer
Nextcloud-Client: Er verbindet sich ausschließlich mit
`$FLUTCLOUD_URL` (aus der lokalen `.env`, nicht hart kodiert) und nur mit
Servern, die die FlutCloud-Nextcloud-App
(`flutcloud-app/`) ausführen. Das Rust-Backend übernimmt alle HTTP-Requests
(WebDAV + OCS), die XML/JSON-Verarbeitung und die Schlüsselbund-Ablage
(keyring). Das Frontend ist Vue 3 + TypeScript + Tailwind v4 und läuft in
einem Tauri WebView.

## Struktur

- `src/` — Vue 3 Frontend (Komponenten, Pinia-Stores in `src/stores/`,
  Tauri-IPC-Wrapper in `src/lib/ipc.ts`, i18n in `src/lib/i18n.ts`,
  UI-Persistenz in `src/stores/ui.ts`)
- `src-tauri/` — Rust-Backend:
  - `lib.rs` — Command-Registry (`tauri::generate_handler!`), Plugin-Setup,
    System-Tray (`setup_tray`), Close-to-Tray (`on_window_event`) und
    CLI-Handling (`tauri-plugin-cli`; Events `flutlink:cli-open`,
    `sync-folders-changed`)
  - `commands.rs` — alle `#[tauri::command]`-Handler
  - `flutcloud.rs` — FlutCloud-only-Durchsetzung: fester Server-URL
    (`FLUTCLOUD_URL`) + Capability-Probe (`verify_server`)
  - `accounts.rs` — Persistenz/Konfig-Datei & Keyring-Token-Ablage
  - `state.rs` — `AppState` (Account-Liste, HTTP-Client, `sync: Arc<SyncEngine>`)
    & Serde-Modelle (`SyncFolder`, `SyncFolderStatus`)
  - `sync.rs` — Zwei-Wege-Sync-Engine (Journal, Planner, Executor, Worker,
    Persistenz, Unit-Tests); Details in `docs/de/sync.md` (EN: `docs/en/sync.md`)
  - `error.rs` — `AppError`/`AppResult` (serialisiert nach JSON fürs Frontend)
  - `nextcloud/webdav.rs` — PROPFIND-Parser, `Impersonate-User`-Support,
    Transfer-Helper (`put_file`/`get_file`/`delete`/`make_collection`)
  - `nextcloud/ocs.rs` — OCS Provisioning API (User-Liste, Details, Quota, Shares)
- `flutcloud-app/` — Nextcloud-Server-App (PHP) für die Nicht-Standard-Funktionen
  des FlutCloud-Servers (Capability, `resources`/`parts`-Virtuelle-Links,
  Projektordner). FlutLink verbindet sich nur mit Servern, die diese App
  ausführen.
- `scripts/` — PowerShell-Installationsskripte, via `curl ... | iex` nutzbar:
  `install-flutlink.ps1` (Desktop-Client vom GitHub-Release),
  `install-flutcloud-app.ps1` (Nextcloud-App auf dem Server)
- `.github/workflows/` — CI (build/lint/checks) + automatisierte Reviews
- `.opencode/` — opencode-Konfiguration (Agent `reviewer`, Commands)
- `docs/` — zweisprachige Architektur-Dokumentation (`docs/en/` + `docs/de/`,
  Index in `docs/README.md`)

## Befehle

- `npm run dev` — Vite-Devserver
- `npm run build` — `vue-tsc --noEmit` + `vite build` (Frontend-Check)
- `npm run tauri dev` — App lokal starten
- `cargo fmt --manifest-path src-tauri/Cargo.toml`
- `cargo clippy --all-targets --manifest-path src-tauri/Cargo.toml -- -D warnings`
- `cargo test --manifest-path src-tauri/Cargo.toml` (Unit-Tests in
  `nextcloud/`-Modulen)

## Konventionen

- Vue: `<script setup lang="ts">`, Kompositions-API, Pinia `defineStore`.
  `tsconfig` ist strict mit `noUnusedLocals`/`noUnusedParameters` — Code muss
  kompilierfrei sauber sein.
- Neue Backend-Commands: Funktion in `commands.rs` + Registrierung in
  `lib.rs` + Wrapper in `src/lib/ipc.ts`.
- Serde-Modelle nutzen `#[serde(rename_all = "camelCase")]` (Rust
  snake_case ↔ TS camelCase).
- i18n: Alle neuen UI-Texte bekommen einen Schlüssel in
  `src/lib/i18n.ts` (en + de). Sprache/Design über `src/stores/ui.ts`
  (localStorage-persistiert). Designs werden über CSS-Variablen in
  `src/style.css` unter `[data-theme]` definiert.
- Administrator-Impersonation: `webdav_list` akzeptiert optional `target_user`;
  der Backend-Command verweigert das für Nicht-Admins (`AppError::Forbidden`)
  und setzt den `Impersonate-User`-Header in `webdav::list`.

## Verifikation

Vor dem Abschluss einer Änderung immer ausführen:
`cargo fmt --check`, `cargo clippy --all-targets -- -D warnings`,
`cargo test` (jeweils mit `--manifest-path src-tauri/Cargo.toml`) und
`npm run build`.
