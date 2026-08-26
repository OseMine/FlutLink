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
│   ├── FileExplorer.vue          # WebDAV-Browser, Suche, Freigaben, resources/parts
│   ├── EntryList.vue             # gemeinsame Listen-/Grid-Darstellung inkl. Pairing-Button
│   ├── AdminPanel.vue            # OCS-Benutzer- + Gruppenverwaltung, Impersonation
│   ├── AdminUserDetails.vue      # Benutzerdetail-Formular (Bearbeiten, Passwort, Quota)
│   ├── AdminUserList.vue         # Benutzerliste-Sidebar mit Suche
│   ├── QuotaEditor.vue           # Quota-Eingabe mit Presets und Fortschrittsanzeige
│   ├── SyncPanel.vue             # Sync-Ordner verwalten + Live-Status
│   ├── GuestBrowser.vue          # Anonymer Gastzugriff auf öffentliche Freigaben
│   ├── LoginModal.vue            # Anmeldung über Schlüsselbund
│   ├── SettingsModal.vue         # Sprache, Design, Über, Updates
│   ├── ShareDialog.vue           # Shares erstellen/auflisten/widerrufen pro Datei/Ordner
│   ├── ContextMenu.vue           # Rechtsklick-Kontextmenü für Dateien/Ordner
│   ├── FilesToolbar.vue          # Suche, Umschalter, Sortierung, Bulk-Aktionen
│   ├── ImpersonationBar.vue      # Admin-Impersonation-Hinweisleiste
│   ├── NewFolderDialog.vue       # Dialog „Neuer Ordner"
│   ├── RenameDialog.vue          # Dialog „Umbenennen"
│   ├── AppLogo.vue / WelcomeScreen.vue  # FlutLink-Branding
│   ├── ToastStack.vue            # Toast-Benachrichtigungen
│   └── Icon.vue                  # Dünnes Line-Icon-Set (Lucide-Stil)
├── lib/
│   ├── ipc.ts                    # typisierte invoke()-Wrapper für alle Commands
│   ├── i18n.ts                   # EN/DE-Wörterbücher, translate(), Fehler-Keys
│   ├── format.ts                 # Byte-Formatierung
│   ├── sort.ts                   # Sortiervergleiche für Dateieinträge
│   └── escape.ts                 # Keyboard-Escape-Handler-Stack (Modals/Menüs)
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
│   ├── cache.rs                  # Offline-Cache für Listings + Kontingente
│   ├── commands.rs               # alle #[tauri::command]-Handler
│   ├── flutcloud.rs              # FlutCloud-only-Durchsetzung (fester Server-URL + Capability-Probe)
│   ├── guest.rs                  # Gast-/Freigabe-API (anonymer Zugriff, Kategorien, Locks)
│   ├── persist.rs                # Atomare Schreib-Helfer (temp+rename) für Konfigdateien
│   ├── sync.rs                   # Zwei-Wege-Sync-Engine (Journal/Planner/Worker)
│   ├── updater.rs                # Update-Check, SHA-256-Download, Installation
│   └── nextcloud/
│       ├── mod.rs                # Auth-Request-Helper, URL/Encoding-Utilities
│       ├── webdav.rs             # PROPFIND + multistatus-Parsing, Transfers
│       └── ocs.rs                # OCS: User-Info, Admin-Probe, Provisioning, Shares
├── capabilities/default.json     # Fenster-Berechtigungen (core, opener, dialog, notification)
└── tauri.conf.json               # App- + CLI-Plugin-Konfiguration, Bundling

flutcloud-app/                    # FlutCloud-Nextcloud-Server-App (PHP)
├── appinfo/                      # info.xml, OCS-Routen (api/v1/*)
├── lib/                          # Capabilities, ApiController, LinkService, GuestApi
└── composer.json                 # OCA\FlutCloud-Autoloading
```

## FlutCloud-only

FlutLink ist **kein** generischer Nextcloud-Client. Er verbindet sich
ausschließlich mit dem FlutCloud-Server (die URL wird aus `FLUTCLOUD_URL` in
der lokalen `.env` gelesen oder von CI-Release-Builds zur Kompilierzeit
eingebaut — nie hart kodiert) und nur,
wenn dieser die FlutCloud-Nextcloud-App (`flutcloud-app/`) ausführt.
`flutcloud.rs` lehnt fremde URLs ab (`AppError::NotFlutCloud`) und prüft vor
jeder Konto-Erstellung den OCS-Capabilities-Endpoint auf die
`flutcloud`-Capability (`AppError::FlutCloudAppMissing`).

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
| `accounts-changed` | `()` | Konto gewechselt/entfernt → Kontenliste aktualisieren |
| `file://progress` | `TransferProgress` | Fortschritt pro Upload/Download |
| `update://progress` | `DownloadProgress` | Fortschritt des Update-Downloads |
| `update://status` | `string` | Statusmeldungen des Update-Lebenszyklus |

## Zustandsmodell (`state.rs`)

`AppState` hält die Kontenliste, einen gemeinsamen `reqwest::Client` und
`sync: Arc<SyncEngine>`. Serde-Modelle nutzen
`#[serde(rename_all = "camelCase")]`, damit Rust-`snake_case` automatisch auf
TS-`camelCase` abgebildet wird.
