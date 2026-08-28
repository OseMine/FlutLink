# Erste Schritte

FlutLink ist der Desktop-Client für den **FlutCloud**-Server, gebaut mit
**Tauri v2** — ein Rust-Backend, das direkt WebDAV und OCS spricht, und ein
Vue-3 + TypeScript + Tailwind-Frontend. FlutLink verbindet sich nur mit dem
FlutCloud-Server (der die `flutcloud`-Nextcloud-App ausführen muss); er ist
kein generischer Nextcloud-Client.

## FlutLink installieren

Am einfachsten installierst du die neueste FlutLink-Version auf Windows,
macOS oder Linux mit dem Installationsskript (PowerShell 7+):

```powershell
iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.ps1)
```

oder mit `curl`:

```powershell
curl.exe -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.ps1 | iex
```

Auf macOS und Linux funktioniert auch das native Bash-Installationsskript
ohne PowerShell:

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.sh | bash
```

Die Skripte laden den Installer für deine Plattform vom letzten GitHub-Release
herunter, prüfen die SHA-256-Checksumme und führen ihn aus. Um eine bestimmte
Version zu wählen oder den Installer nur herunterzuladen, speichere das Skript
und übergib Parameter:

```powershell
irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.ps1 -OutFile install-client.ps1
./install-client.ps1 -Tag v1.0.0 -NoRun
```

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.sh -o install-client.sh
./install-client.sh --tag v1.0.0 --no-run
```

Wie die `curl | iex`- / `curl | bash`-Einzeiler funktionieren, welche Optionen
es gibt und wie du Probleme behebst, findest du unter
[Installationsskripte](install-scripts.md).

Der Server muss die `flutcloud`-Nextcloud-App ausführen — installiere sie mit
[`install-nextcloud.sh`](flutcloud-app.md) (Bash, Ubuntu/Debian) — und die
FlutCloud-Server-URL muss über `FLUTCLOUD_URL` gesetzt sein (siehe
[FlutCloud-Konto hinzufügen](#flutcloud-konto-hinzufügen)).

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
3. Gib Benutzername und App-Passwort ein. Der Server ist fest auf den
   FlutCloud-Server eingestellt — `FLUTCLOUD_URL` aus deiner lokalen `.env`
   in der Entwicklung; offizielle Release-Builds haben die URL eingebaut.
4. Das Token wird im OS-Schlüsselbund gespeichert (Windows
   Anmeldeinformationsverwaltung, macOS-Schlüsselbund, Linux Secret Service).

Das Konto wird gegen `ocs/v2.php/cloud/user` geprüft und als **Admin**
markiert, wenn der Benutzer einer Admin-Gruppe angehört.

### Neues Konto registrieren

Statt dich mit einem bestehenden Konto anzumelden, kannst du in FlutLink auch
ein neues Konto **registrieren** (ohne E-Mail). Dafür werden einmalig die
FlutCloud-Admin-Zugangsdaten benötigt; das Konto wird über die
OCS-Provisioning-API erstellt und anschließend automatisch angemeldet.

Das **Passwort, das du bei der Registrierung wählst, wird dauerhaft zum
App-Passwort dieses Kontos**: FlutLink speichert es im OS-Schlüsselbund und
verwendet es für jeden Request — genau wie ein reguläres App-Passwort. Ein
separates App-Passwort muss nicht angelegt werden — das
Registrierungs-Passwort ist das App-Passwort.

Da das gespeicherte Token *das* Kontopasswort ist, wird das Token ungültig,
sobald das Kontopasswort später geändert wird (z. B. unter FlutCloud →
Einstellungen → Sicherheit → Passwort). Nach einem Passwortwechsel musst du das
Konto in FlutLink entfernen und neu hinzufügen (und dich mit dem neuen Passwort
oder einem frischen App-Passwort anmelden).

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
