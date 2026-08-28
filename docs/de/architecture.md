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
├── capabilities/default.json     # Fenster-Berechtigungen (core, opener, dialog)
└── tauri.conf.json               # App- + CLI-Plugin-Konfiguration, Bundling

flutcloud-app/                    # FlutCloud-Nextcloud-Server-App (PHP)
├── appinfo/                      # info.xml, OCS-Routen (api/v1/*)
├── lib/                          # Capabilities, ApiController, LinkService, GuestApi
└── composer.json                 # OCA\FlutCloud-Autoloading

kmp/                              # FlutLink-Mobiler Client (Kotlin Multiplatform)
│                                 # Port der Desktop-App nach Kotlin, von opencode generiert
├── shared/                       # KMP-Modul: Android-App + JVM + iOS-Targets
│   └── src/
│       ├── commonMain/           # Plattformagnostischer Kern (AuthSession, DTOs, JsonUtil)
│       ├── androidMain/          # Vollständige Android-App: Compose-UI (Login, Dateien, Admin,
│       │                         # Einstellungen), FlutCloudApi/WebDavApi, Stores, manifest/res
│       ├── androidUnitTest/      # JVM-Unit-Tests
│       └── iosMain/              # iOS-Einstiegspunkt (MainViewController → Compose-UI)
└── iosApp/                       # Xcode-Hülle für den iOS-Build (unsigniertes IPA in CI)
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

## Wichtige Commands

| Command | Backend | Zweck |
| --- | --- | --- |
| `account_add` | OCS `/cloud/user` + Admin-Probe | Anmeldedaten prüfen, Token im Schlüsselbund speichern, Konto hinzufügen/aktivieren |
| `account_switch` / `account_remove` / `account_list` | state | Multi-Konto-Lebenszyklus (Wechsel emittiert `accounts-changed`) |
| `account_storage` | WebDAV-Quota | Quota-Info (offline-gecacht via `cache.rs`) |
| `refresh_admin_flags` | OCS | Admin-Flag bei Start neu bewerten |
| `webdav_list` | WebDAV `PROPFIND` (Depth 1) | Ordner durchsuchen; Einträge `isResource` / `isPart`; Offline-Cache-Fallback |
| `webdav_search` | WebDAV `SEARCH` | Globale Dateisuche auf dem Server |
| `webdav_create_share` / `webdav_list_shares` / `webdav_delete_share` | OCS-Share-API | Öffentliche/Benutzer-/Gruppen-Shares erstellen, auflisten und widerrufen |
| `webdav_upload_file` / `webdav_download_file` / `open_remote_file` | WebDAV | Upload / Download / Öffnen über Cache-Verzeichnis (Admins können anderen Benutzer ansprechen) |
| `webdav_mkdir` / `webdav_rename` / `webdav_delete` | WebDAV | Ordner erstellen, umbenennen und löschen (Umbenennen validiert, Überschreiben geschützt) |
| `webdav_upload_local_paths` / `webdav_bulk_delete` / `webdav_bulk_download` | WebDAV | Drag & Drop Upload + Bulk-Operationen (`file://progress`-Events) |
| `webdav_download_zip` / `webdav_thumbnail` | WebDAV | Ordner-ZIP-Download / Bild-Miniaturansichten |
| `guest_verify_server` / `guest_list_shares` / `guest_list_entries` / `guest_download_file` / `guest_open_file` | Guest API (`guest.rs`) | Anonymer Gastzugriff auf öffentliche Freigaben |
| `guest_admin_*` (Kategorien, Locks) | Guest API (`guest.rs`) | Admin: Kategorien und rekursive Unterordner-Locks für öffentliche Freigaben verwalten |
| `admin_list_users` / `admin_get_user` / `admin_set_user_quota` / `admin_edit_user` / `admin_create_user` / `admin_delete_user` | OCS Provisioning API | Admin-Panel (nur Admin-Konten) |
| `admin_list_groups` / `admin_create_group` / `admin_add_group_member` / `admin_remove_group_member` | OCS Groups API | Gruppenverwaltung (nur Admin-Konten) |
| `sync_list` / `sync_add` / `sync_remove` / `sync_set_paused` | `sync.rs` | Zwei-Wege-Sync-Ordner verwalten |
| `sync_trigger` | `sync.rs` | Sofortigen Sync-Durchlauf auslösen |
| `check_update` / `download_and_install_update` | `updater.rs` | Update-Check (SHA-256-verifiziert) und Installation |

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
