# Sicherheit

## Anmeldedaten erreichen nie den Renderer

- Tokens werden über die `keyring`-Crate im **OS-Schlüsselbund** gespeichert
  (Windows Anmeldeinformationsverwaltung, macOS-Schlüsselbund, Linux Secret
  Service).
- Zur Laufzeit liegen Tokens in Rust-verwaltetem Speicher in `AppState`; das
  Frontend bekommt sie nie zu sehen.
- `accounts.json` (App-Data-Verzeichnis) persistiert **nur Metadaten**:
  Benutzername, Instanz-URL, Anzeigename, Admin-Flag, Aktiv-Flag. Konten ohne
  Keyring-Token werden beim Start übersprungen.

## Registrierungs-Passwort = App-Passwort

Konten, die über den **Registrieren**-Flow erstellt wurden, melden sich mit dem
bei der Registrierung gewählten Passwort an: Es wird im OS-Schlüsselbund
gespeichert und als App-Passwort für jeden Request verwendet. Für ein frisch
registriertes Konto muss kein separates App-Passwort angelegt werden.

Da das gespeicherte Token *das* Kontopasswort ist, macht ein Passwortwechsel
auf dem Server das Token ungültig. Nach einem Passwortwechsel muss das Konto in
FlutLink entfernt und neu hinzugefügt werden (siehe
[Erste Schritte](getting-started.md)).

## Der gesamte HTTP-Verkehr bleibt in Rust

WebDAV- und OCS-Requests setzt das Backend ab, das bedeutet:

- Keine CORS-Probleme und keine Cross-Origin-Exposition im Webview.
- Eigene Methoden wie `PROPFIND` funktionieren wie vorgesehen.
- Anmeldedaten werden in Rust gesetzt und nie geloggt.

## Admin-Gating

- Das Admin-Flag wird bei der Anmeldung per OCS (`user_group_details`)
  erkannt.
- Admin-Commands (`admin_list_users`, `admin_get_user`,
  `admin_set_user_quota`, `admin_edit_user`, `admin_create_user`,
  `admin_delete_user`) sind nur für Admin-Konten erlaubt.
- **Impersonation:** `webdav_list` akzeptiert optional `target_user`. Das
  Backend verweigert den Aufruf für Nicht-Admins mit `AppError::Forbidden` und
  setzt für Admins den `Impersonate-User`-Header auf den WebDAV-Request. Das
  Frontend zeigt beim Durchsuchen fremder Benutzerdateien einen
  „Admin-Impersonation"-Hinweis.

## FlutCloud-only-Durchsetzung

FlutLink verbindet sich ausschließlich mit dem FlutCloud-Server und lehnt
alle anderen ab:

- Die Server-URL wird aus `FLUTCLOUD_URL` in `.env`
  (`src-tauri/src/flutcloud.rs`) gelesen und über `get_flutcloud_url` an das
  Frontend weitergegeben. CI-Release-Builds baken die URL zur Kompilierzeit
  in die Binaries ein (`option_env!`), sodass installierte Apps ohne lokale
  `.env` funktionieren.
- Der mobile Client (`kmp/`) bakt dieselbe URL in
  `BuildConfig.FLUTCLOUD_URL` aus der Umgebungsvariable `FLUTCLOUD_URL`
  (Fallback: `-PflutcloudUrl` Gradle-Property) und sperrt das
  Server-Eingabefeld im Login, wenn eine URL einkompiliert ist.
- `account_add` / `register_user` lehnt andere URLs ab
  (`AppError::NotFlutCloud`).
- Vor dem Verbinden wird der OCS-Capabilities-Endpoint auf die
  `flutcloud`-Capability geprüft (`AppError::FlutCloudAppMissing` bei
  Fehlen).
- Konten, die für andere Server persistiert sind, werden beim Start
  entfernt.

## Fehlerbehandlung

- Alle Commands geben einen serialisierten `AppError { code, message }`
  zurück; das Frontend zeigt ihn als Inline-Fehler oder Toast an
  (`invokeError` in `src/lib/ipc.ts`).
- Geheimnisse tauchen nie in Fehlermeldungen auf.

## Webview-Härtung

- `capabilities/default.json` gewährt nur die Mindestberechtigungen
  (`core:default`, `opener:default`, `dialog:default`).
- Origin: Es wird nur das gebündelte Frontend bzw. der Dev-Server geladen;
  eine CSP (`default-src 'self'`, siehe `src-tauri/tauri.conf.json`) begrenzt,
  was der Webview laden darf — vor Remote-Inhalten ggf. verschärfen.
