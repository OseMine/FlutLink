# Development

## Commands

| Task | Command |
| --- | --- |
| Dev server + app | `npm run tauri dev` |
| Frontend typecheck/build | `npm run build` (`vue-tsc --noEmit` + `vite build`) |
| Rust format check | `cargo fmt --check --manifest-path src-tauri/Cargo.toml` |
| Rust lint | `cargo clippy --all-targets --manifest-path src-tauri/Cargo.toml -- -D warnings` |
| Rust tests | `cargo test --manifest-path src-tauri/Cargo.toml` |
| Regenerate icons | `npm run tauri icon app-icon.png` |

**Verification before finishing any change:** run all four — `cargo fmt
--check`, `cargo clippy -D warnings`, `cargo test`, `npm run build`.

## Conventions

- **Vue** — `<script setup lang="ts">`, Composition API, Pinia `defineStore`.
  `tsconfig` is strict with `noUnusedLocals`/`noUnusedParameters`; code must
  compile cleanly.
- **New backend commands** — add the function to `commands.rs`, register it in
  `lib.rs` (`tauri::generate_handler!`), and add a typed wrapper in
  `src/lib/ipc.ts`.
- **Serde** — models use `#[serde(rename_all = "camelCase")]`
  (Rust `snake_case` ↔ TS `camelCase`).
- **i18n** — every new UI string gets a key in `src/lib/i18n.ts` (en **and**
  de). Language and theme are persisted via `src/stores/ui.ts`
  (localStorage). Themes are CSS variables in `src/style.css` under
  `[data-theme]`.
- **Admin impersonation** — `webdav_list` accepts an optional `target_user`;
  the backend refuses non-admins (`AppError::Forbidden`) and sets the
  `Impersonate-User` header in `webdav::list`.

## IPC contract

Typed wrappers live in `src/lib/ipc.ts`. The canonical command list is the
`invoke_handler` registration in `src-tauri/src/lib.rs`. Events:
`sync-status` (status payload), `flutlink:cli-open` (URL string),
`sync-folders-changed`.

## Adding documentation

Docs live in `docs/` with `en/` and `de/` folders. Both language versions must
stay in sync — update the counterpart whenever you touch a page, and keep the
index in `docs/README.md` up to date.

## CI

`.github/workflows/` runs build, lint and checks on every push/PR, plus
automated opencode reviews. Keep `cargo clippy -D warnings` green — the CI
treats warnings as errors.
