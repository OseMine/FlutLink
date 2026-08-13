# FlutLink Documentation

Welcome to the FlutLink documentation. FlutLink is a Tauri v2 desktop client
for Nextcloud — file browsing, sharing, administration and two-way sync, with
the entire HTTP stack (WebDAV + OCS) living in the Rust backend.

The documentation is maintained in **two languages** (English and German).
Every page exists in both variants and must stay in sync.

## Structure

```
docs/
├── README.md               # this index
├── en/                     # English
│   ├── getting-started.md  # prerequisites, dev setup, first run
│   ├── features.md         # Files, Admin, Sync tabs — tray & CLI overview
│   ├── architecture.md     # frontend/backend layers, modules, data flow
│   ├── sync.md             # two-way sync engine (journal, planner, worker)
│   ├── tray-and-cli.md     # system tray, close-to-tray, CLI flags
│   ├── security.md         # keychain, tokens, admin gating, error handling
│   └── development.md      # build, test, conventions, IPC contract
└── de/                     # Deutsch (same structure as en/)
    ├── getting-started.md
    ├── features.md
    ├── architecture.md
    ├── sync.md
    ├── tray-and-cli.md
    ├── security.md
    └── development.md
```

## Where to start

| If you want to… | Read |
| --- | --- |
| Install and run FlutLink | [Getting started](en/getting-started.md) |
| Know what the app can do | [Features](en/features.md) |
| Understand how the code is organized | [Architecture](en/architecture.md) |
| Understand the sync engine | [Sync engine](en/sync.md) |
| Use the tray / command line | [Tray & CLI](en/tray-and-cli.md) |
| Review how credentials are handled | [Security](en/security.md) |
| Contribute code or docs | [Development](en/development.md) |

For German, use the corresponding page under `docs/de/`.

## Editing the docs

- Update **both** language versions whenever you change a page; use the `de/`
  mirror as reference for the German translation.
- Keep this README's index in sync when adding or renaming pages.
- Follow the project's verification flow described in
  [Development](en/development.md) before finishing changes.

## Project links

- [Project README](../README.md) — overview and quick start
- [Agent guidelines](../AGENTS.md) — repository structure and conventions
