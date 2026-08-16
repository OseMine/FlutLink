# FlutLink Documentation

[German docs](README-de.md)

Welcome to the FlutLink documentation. FlutLink is a Tauri v2 desktop client
for the FlutCloud server — file browsing, sharing, administration and two-way
sync, with the entire HTTP stack (WebDAV + OCS) living in the Rust backend.
FlutLink connects exclusively to the FlutCloud server and rejects servers
without the FlutCloud Nextcloud app (`flutcloud-app/`).

The documentation is maintained in **two languages** (English and German).
Every page exists in both variants and must stay in sync.

## Structure

```
docs/
├── README.md               # this index
├── README-de.md            # German index
├── en/                     # English
│   ├── getting-started.md  # prerequisites, dev setup, first run
│   ├── install-scripts.md  # using the curl/iex install scripts
│   ├── features.md         # Files, Admin, Sync tabs — tray & CLI overview
│   ├── architecture.md     # frontend/backend layers, modules, data flow
│   ├── sync.md             # two-way sync engine (journal, planner, worker)
│   ├── tray-and-cli.md     # system tray, close-to-tray, CLI flags
│   ├── security.md         # keychain, tokens, admin gating, error handling
│   ├── flutcloud-app.md    # the server-side Nextcloud app (install, API)
│   └── development.md      # build, test, conventions, IPC contract
└── de/                     # Deutsch (same structure as en/)
    ├── getting-started.md
    ├── install-scripts.md
    ├── features.md
    ├── architecture.md
    ├── sync.md
    ├── tray-and-cli.md
    ├── security.md
    ├── flutcloud-app.md
    └── development.md
```

## Where to start

| If you want to… | Read |
| --- | --- |
| Install and run FlutLink | [Getting started](en/getting-started.md) |
| Use the curl/iex install one-liners | [Install scripts](en/install-scripts.md) |
| Know what the app can do | [Features](en/features.md) |
| Understand how the code is organized | [Architecture](en/architecture.md) |
| Understand the sync engine | [Sync engine](en/sync.md) |
| Use the tray / command line | [Tray & CLI](en/tray-and-cli.md) |
| Review how credentials are handled | [Security](en/security.md) |
| Set up the FlutCloud server app | [FlutCloud app](en/flutcloud-app.md) |
| Contribute code or docs | [Development](en/development.md) |

For German, use the corresponding page under `docs/de/`, or the
[German index](README-de.md).

## Editing the docs

- Update **both** language versions whenever you change a page; use the `de/`
  mirror as reference for the German translation.
- Keep this README's index in sync when adding or renaming pages.
- Follow the project's verification flow described in
  [Development](en/development.md) before finishing changes.

## Project links

- [Project README](../README.md) — overview and quick start
- [Agent guidelines](../AGENTS.md) — repository structure and conventions
