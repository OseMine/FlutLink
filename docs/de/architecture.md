# Architektur

FlutLink ist in ein Rust-Backend (`src-tauri/`) aufgeteilt, das den gesamten
HTTP-Verkehr übernimmt, und ein Vue-3-Frontend (`src/`), das Zustände rendert
und typisierte IPC-Aufrufe absetzt. Requests in Rust vermeiden CORS,
ermöglichen eigene HTTP-Methoden wie `PROPFIND` und halten Anmeldedaten aus
dem Renderer heraus.

## Modulübersicht

```
src/                              # Vue 3 + TypeScript + Tailwind v4
├── components/
│   ├── AccountBar.vue            # Kontowechsel, Speicher-Widget, hinzufügen/entfernen
│   ├── FileExplorer.vue          # WebDAV-Browser, resources/parts, Link-Freigabe
│   ├── AdminPanel.vue            # OCS-Benutzerverwaltung + Impersonation
│   ├── SyncPanel.vue             # Sync-Ordner verwalten + Live-Status
│   ├── LoginModal.vue            # Anmeldung über Schlüsselbund
│   ├── SettingsModal.vue         # Sprache, Design, Über
│   ├── AppLogo.vue / WelcomeScreen.vue  # FlutLink/OperationFlut-Branding
│   └── ToastStack.vue            # Toast-Benachrichtigungen
├── lib/
│   ├── ipc.ts                    # typisierte invoke()-Wrapper für alle Commands
│   ├── i18n.ts                   # EN/DE-Wörterbücher, translate()
│   └── format.ts                 # Byte-Formatierung
├── stores/
│   ├── accounts.ts               # Kontenliste, aktives Konto, Speicherkontingent
│   ├── files.ts                  # WebDAV-Listing-Zustand
│   ├── sync.ts                   # Sync-Status (auf Events abonniert)
│   └── ui.ts                     # Sprache + Design, in localStorage persistiert
└── App.vue                       # Shell: Seitenleiste + Dateien/Sync/Admin-Tabs

src-tauri/                        # Rust-Backend
├── src/
│   ├── main.rs / lib.rs          # Bootstrap, Plugins, Tray, CLI, Registry
│   ├── state.rs                  # AppState: reqwest-Client, Konten, Sync-Engine
│   ├── error.rs                  # AppError/AppResult (als JSON serialisiert)
│   ├── accounts.rs               # accounts.json-Metadaten + Keyring-Tokens
│   ├── commands.rs               # alle #[tauri::command]-Handler
│   ├── sync.rs                   # Zwei-Wege-Sync-Engine (Journal/Planner/Worker)
│   └── nextcloud/
│       ├── mod.rs                # Auth-Request-Helper, URL/Encoding-Utilities
│       ├── webdav.rs             # PROPFIND + multistatus-Parsing, Transfers
│       └── ocs.rs                # OCS: User-Info, Admin-Probe, Provisioning, Shares
├── capabilities/default.json     # Fenster-Berechtigungen (core, opener, dialog)
└── tauri.conf.json               # App- + CLI-Plugin-Konfiguration, Bundling
```

## Datenfluss Frontend ↔ Backend

1. Komponenten rufen **Pinia-Stores** (`src/stores/`) auf.
2. Stores rufen typisierte Wrapper in `src/lib/ipc.ts` (`api.*`) auf.
3. Wrapper `invoke()` einen in `lib.rs` registrierten Rust-Command.
4. Commands arbeiten auf `AppState` und geben Serde-Typen zurück (`camelCase`).
5. Fehler überqueren die Grenze als `AppError { code, message }`.

**Events** fließen in die andere Richtung per `app.emit`:

| Event | Payload | Zweck |
| --- | --- | --- |
| `sync-status` | `SyncFolderStatus[]` | aktualisierte Sync-Status an `stores/sync.ts` |
| `flutlink:cli-open` | `string` | Login-Dialog mit Server-URL öffnen |
| `sync-folders-changed` | `()` | Ordner per CLI hinzugefügt → Sync-Panel aktualisieren |

## Zustandsmodell (`state.rs`)

`AppState` hält die Kontenliste, einen gemeinsamen `reqwest::Client` und
`sync: Arc<SyncEngine>`. Serde-Modelle nutzen
`#[serde(rename_all = "camelCase")]`, damit Rust-`snake_case` automatisch auf
TS-`camelCase` abgebildet wird.
