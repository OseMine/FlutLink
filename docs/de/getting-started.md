# Erste Schritte

FlutLink ist der Desktop-Client für den **FlutCloud**-Server, gebaut mit
**Tauri v2** — ein Rust-Backend, das direkt WebDAV und OCS spricht, und ein
Vue-3 + TypeScript + Tailwind-Frontend. FlutLink verbindet sich nur mit dem
FlutCloud-Server (der die `flutcloud`-Nextcloud-App ausführen muss); er ist
kein generischer Nextcloud-Client.

## Voraussetzungen

| Werkzeug | Version | Hinweis |
| --- | --- | --- |
| Node.js | 20+ | npm für das Frontend |
| Rust | 1.85+ (stable) | über rustup |
| Tauri-Voraussetzungen | — | Plattform-Tooling, siehe [tauri.app](https://tauri.app/start/prerequisites/) |

Unter Windows gehören dazu die MSVC-Buildtools und WebView2. Unter Linux sind
die webkit2gtk-Systempakete erforderlich.

## Entwicklungssetup

```bash
npm install          # Frontend-Abhängigkeiten
npm run tauri dev    # startet Vite (Port 1420) + die Rust-App
```

Die App öffnet ein 1200×800-Fenster. Beim ersten Start kannst du dich direkt
anmelden oder erst einmal den Willkommensbildschirm erkunden.

## FlutCloud-Konto hinzufügen

1. Erstelle ein **App-Passwort** in FlutCloud:
   *Einstellungen → Sicherheit → App-Passwörter*.
2. Öffne FlutLink → **Anmelden**.
3. Gib Benutzername und App-Passwort ein. Der Server ist fest auf die
   `FLUTCLOUD_URL` aus deiner lokalen `.env` gesetzt.
4. Das Token wird im OS-Schlüsselbund gespeichert (Windows
   Anmeldeinformationsverwaltung, macOS-Schlüsselbund, Linux Secret Service).

Das Konto wird gegen `ocs/v2.php/cloud/user` geprüft und als **Admin**
markiert, wenn der Benutzer einer Admin-Gruppe angehört.

## Was du als Nächstes ausprobieren kannst

- Durchsuche deine Cloud-Dateien im **Dateien**-Tab.
- Füge im **Sync**-Tab einen lokalen Ordner hinzu — er wird nach
  `/FlutLink/<Ordner>` gespiegelt.
- Nutze die Kommandozeile: `flutlink --sync` oder `flutlink --path <Ordner>`
  (siehe [Tray & CLI](tray-and-cli.md)).
- Verbinde ein Admin-Konto und öffne den **Admin**-Tab.

## Produktionsbuild

```bash
npm run build             # vue-tsc + vite build (Frontend-Check)
cargo build --release --manifest-path src-tauri/Cargo.toml
npm run tauri build       # fertige gebündelte App
```
