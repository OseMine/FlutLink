# FlutLink-Dokumentation

[Englische Doku](README.md)
Willkommen zur FlutLink-Dokumentation. FlutLink ist ein Tauri-v2-Desktop-Client
für den FlutCloud-Server — Dateibrowser, Freigaben, Administration und
Zwei-Wege-Sync, wobei der komplette HTTP-Stack (WebDAV + OCS) im Rust-Backend
liegt. FlutLink verbindet sich ausschließlich mit dem FlutCloud-Server und
lehnt Server ohne die FlutCloud-Nextcloud-App (`flutcloud-app/`) ab.

Die Dokumentation wird in **zwei Sprachen** (Englisch und Deutsch) gepflegt.
Jede Seite existiert in beiden Varianten und muss synchron bleiben.

## Aufbau

```
docs/
├── README.md               # dieser Index
├── README-de.md            # deutscher Index
├── en/                     # English
│   ├── getting-started.md  # Voraussetzungen, Dev-Setup, erster Start
│   ├── features.md         # Dateien-, Admin- und Sync-Tab — Tray & CLI
│   ├── architecture.md     # Frontend/Backend-Schichten, Module, Datenfluss
│   ├── sync.md             # Zwei-Wege-Sync-Engine (Journal, Planner, Worker)
│   ├── tray-and-cli.md     # System-Tray, Schließen in den Tray, CLI-Flags
│   ├── security.md         # Schlüsselbund, Tokens, Admin-Absicherung, Fehler
│   ├── flutcloud-app.md    # die serverseitige Nextcloud-App (Installation, API)
│   └── development.md      # Build, Test, Konventionen, IPC-Vertrag
└── de/                     # Deutsch (gleicher Aufbau wie en/)
    ├── getting-started.md
    ├── features.md
    ├── architecture.md
    ├── sync.md
    ├── tray-and-cli.md
    ├── security.md
    ├── flutcloud-app.md
    └── development.md
```

## Wo du anfangen solltest

| Wenn du … | Lese |
| --- | --- |
| FlutLink installieren und ausführen möchtest | [Erste Schritte](de/getting-started.md) |
| wissen möchtest, was die App kann | [Funktionen](de/features.md) |
| verstehen möchtest, wie der Code organisiert ist | [Architektur](de/architecture.md) |
| die Sync-Engine verstehen möchtest | [Sync-Engine](de/sync.md) |
| Tray / Kommandozeile nutzen möchtest | [Tray & CLI](de/tray-and-cli.md) |
| prüfen möchtest, wie Zugangsdaten behandelt werden | [Sicherheit](de/security.md) |
| die FlutCloud-Server-App einrichten möchtest | [FlutCloud-App](de/flutcloud-app.md) |
| Code oder Doku beitragen möchtest | [Entwicklung](de/development.md) |

Für Englisch nutze die entsprechende Seite unter `docs/en/`.

## Doku pflegen

- Aktualisiere **beide** Sprachversionen, wenn du eine Seite änderst; nutze das
  `de/`-Pendant als Referenz für die Übersetzung.
- Halte den Index dieser README synchron, wenn Seiten hinzukommen oder
  umbenannt werden.
- Führe den im Projekt beschriebenen Verifikationsablauf in
  [Entwicklung](de/development.md) aus, bevor du Änderungen abschließt.

## Projekt-Links

- [Projekt-README](../README.md) — Überblick und Schnellstart
- [Agent-Richtlinien](../AGENTS.md) — Repository-Struktur und Konventionen
