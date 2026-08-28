# Installationsskripte (curl & iex)

FlutLink liefert drei Installationsskripte unter `scripts/` mit, plus ein
Root-`install.sh`-Wrapper. Alle lassen sich direkt aus dem GitHub-Repository
ausführen, ohne es zu klonen:

| Skript | Ziel | Shell |
| --- | --- | --- |
| `install.sh` | automatische Auswahl: FlutCloud-Server-App oder FlutLink-Client | bash |
| `install-client.ps1` | FlutLink-Desktop-Client (Windows, macOS, Linux) | PowerShell 7+ |
| `install-client.sh` | FlutLink-Desktop-Client (macOS, Linux) | bash |
| `install-nextcloud.sh` | FlutCloud-Nextcloud-App auf dem Server (Ubuntu/Debian) | bash |

Alle werden auf die gleiche Weise aufgerufen: Der Skripttext wird direkt von
der rohen GitHub-URL in die Shell gestreamt und im Speicher ausgeführt — es
wird nichts auf die Festplatte geschrieben, außer du machst es selbst.

## Root-`install.sh`-Wrapper

Es gibt einen einzelnen Einstiegspunkt im Repository-Root, der das passende
Installationsskript für dich wählt: Er installiert die
FlutCloud-Nextcloud-App, wenn er eine Nextcloud-Installation findet (einen
Ordner mit `occ` im aktuellen Verzeichnis, in einem Elternverzeichnis oder an
einem üblichen Ort wie `~/nextcloud` oder `/var/www/nextcloud`), sonst
installiert er den FlutLink-Desktop-Client:

```bash
curl -sSL https://raw.githubusercontent.com/OseMine/FlutLink/main/install.sh | bash
```

Um die Server-Installation zu erzwingen (oder auf dein Nextcloud zu zeigen),
übergib den Pfad — `--path` und `--nextcloud-root` sind gleichwertig:

```bash
curl -sSL https://raw.githubusercontent.com/OseMine/FlutLink/main/install.sh | bash -s -- --path ~/nextcloud
```

Client-Optionen (z. B. `--tag`, `--no-run`) werden an `install-client.sh`
durchgereicht.

## PowerShell: `iex (irm <url>)`

`irm` (Invoke-RestMethod) lädt den Skripttext herunter, `iex`
(Invoke-Expression) führt ihn aus:

```powershell
iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.ps1)
```

## PowerShell: `curl.exe ... | iex`

Der klassische Windows-Stil — `curl.exe` (das echte curl, **nicht** der
PowerShell-`curl`-Alias für `Invoke-WebRequest`) lädt das Skript herunter und
leitet es an `iex` weiter:

```powershell
curl.exe -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.ps1 | iex
```

- `-s` unterdrückt curls Fortschrittsausgabe, `-L` folgt den Redirects von
  GitHub zur Rohdatei. Ohne `-L` leitet GitHub zwar weiter, aber `curl.exe`
  folgt den Redirects nicht.
- In PowerShell ist `curl` allein ein Alias für `Invoke-WebRequest`; schreibe
  in Skripten immer `curl.exe`, damit das echte curl verwendet wird.

## bash: `curl ... | bash`

Auf macOS und Linux lautet das Pendant:

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.sh | bash
```

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-nextcloud.sh | bash
```

Die bash-Skripte brauchen ein POSIX-`bash` (auch das macOS-System-bash 3.2
funktioniert) und `curl`. Das Client-Skript benötigt zusätzlich `jq` oder
`python3`, um die GitHub-Release-Metadaten zu lesen (beides ist auf modernen
Systemen meist vorhanden).

## Was beim Ausführen passiert

1. `install-client.*` fragt die GitHub-API nach dem letzten Release ab,
   wählt den Installer für dein Betriebssystem/deine Architektur, lädt ihn
   herunter, prüft seinen SHA-256-Digest und führt ihn aus (AppImage/.deb
   unter Linux, `.dmg` unter macOS, `.exe`/`.msi` unter Windows).
2. `install-nextcloud.sh` findet die Nextcloud-Installation, lädt die
   App als `flutcloud-app.zip` aus dem letzten GitHub-Release herunter,
   kopiert sie nach `apps/flutcloud`, aktiviert die App mit `occ` und prüft
   sie. Bei interaktiver Ausführung (in einem Terminal) fragt das Skript
   zuerst, ob der erkannte Pfad korrekt ist, bzw. nach dem Pfad, in dem du
   Nextcloud installiert hast; gepipe-`curl | bash`-Läufe überspringen die
   Abfrage und nutzen den erkannten Pfad.

## Parameter übergeben

Bei direkter Pipe laufen die Skripte mit Standardwerten (letztes Release,
automatische Erkennung). Um Parameter zu übergeben, lade das Skript zuerst in
eine Datei herunter und führe es dann aus:

```powershell
irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.ps1 -OutFile install-client.ps1
./install-client.ps1 -Tag v1.0.0 -NoRun
```

```bash
curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-client.sh -o install-client.sh
./install-client.sh --tag v1.0.0 --no-run
```

### `install-client.*`

| PowerShell | bash | Bedeutung |
| --- | --- | --- |
| `-Tag v1.0.0` | `--tag v1.0.0` | Bestimmtes Release statt des letzten installieren |
| `-DownloadDir <dir>` | `--dir <dir>` | Verzeichnis für den heruntergeladenen Installer |
| `-NoRun` | `--no-run` | Nur herunterladen (und prüfen), nicht installieren |
| `-NoVerify` | `--no-verify` | SHA-256-Prüfung überspringen (nicht empfohlen) |

### `install-nextcloud.sh`

| bash | Bedeutung |
| --- | --- |
| `--nextcloud-root <pfad>` | Nextcloud-Ordner (enthält `occ`); wenn nicht angegeben, wird er automatisch erkannt und interaktiv bestätigt |
| `--ref <tag-oder-branch>` | Bestimmtes Release oder einen Branch installieren: ein Release-Tag nutzt dessen `flutcloud-app.zip` (mit Fallback auf die getaggten Quellen), ein Branch die aktuellen Branch-Quellen |
| `--web-user <benutzer>` | Webserver-Benutzer (Standard `www-data`) |
| `--docker-container <id>` | occ über `docker exec` ausführen |
| `--composer` | Composer-Autoloader erzeugen |
| `--no-sudo` | occ/chown direkt ausführen (als `www-data` oder root) |
| `--skip-verify` | `app:list`-Prüfung danach überspringen |

## Sicherheitshinweise

- Die Pipe führt aus, was die URL liefert. Prüfe immer, dass die URL der
  offizielle Pfad `raw.githubusercontent.com/OseMine/FlutLink/...` ist, und
  schau dir das Skript einmal an, bevor du es ausführst — speichere es in eine
  Datei und lies es, oder `irm <url> | Get-Content`, um den Text vor der
  Ausführung zu prüfen.
- Die Skripte laden nur von der GitHub-Releases-API und prüfen den von GitHub
  veröffentlichten SHA-256-Digest; sie fragen nie nach Passwörtern und
  speichern keine Zugangsdaten.

## Fehlerbehebung

- **`curl : The term 'curl' is not recognized`** → du bist in PowerShell ohne
  `curl.exe`; nutze stattdessen `iex (irm <url>)`.
- **`Invoke-RestMethod` scheitert an GitHub-Redirects/TLS** → stelle sicher,
  dass PowerShell 7+ läuft (`$PSVersionTable.PSVersion`) und TLS 1.2+
  aktiviert ist.
- **Ausführungsrichtlinie** — `iex (irm ...)` wird von der
  Ausführungsrichtlinie nicht blockiert (es ist ein Ausdruck, keine
  Skriptdatei). Ist eine gespeicherte Skriptdatei blockiert, führe
  `./install-client.ps1` nach `Set-ExecutionPolicy -Scope Process Bypass`
  für die aktuelle Sitzung aus.
- **`jq`/`python3` fehlt** (Linux/macOS-Client-Skript) → installiere eines
  davon, z. B. `sudo apt install jq` unter Ubuntu oder `brew install jq`
  unter macOS.
- **Permission denied bei der Server-App-Installation** — das Skript erhöht
  automatisch auf `sudo`, wenn `nextcloud/apps` für deinen Benutzer nicht
  beschreibbar ist (typisch bei `/var/www/nextcloud`); führe es von einem
  Konto mit `sudo`-Rechten aus oder übergib `--no-sudo`, wenn du bereits als
  `www-data` oder root läufst.
- **Proxy/Offline-Server** → lade die Skripte und die Assets manuell herunter
  und führe sie aus Dateien aus; die Skripte brauchen keine weiteren
  Netzwerkverbindungen außer GitHub.

Siehe [Erste Schritte](getting-started.md) für die Einzeiler und
[FlutCloud-App](flutcloud-app.md) für die serverseitige Installation.
