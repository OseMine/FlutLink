# FlutLink $GITHUB_REF_NAME — Release-Notes

## Neu
- Android-Client: **Download** von Dateien mit anschließendem Öffnen in einer externen App sowie Download in den öffentlichen Downloads-Ordner.
- Android-Client: **Teilen** von Dateien über das native Android Share-Sheet.
- Android-Client: Server-URL wird beim Build aus `FLUTCLOUD_URL` in `BuildConfig.FLUTCLOUD_URL` eingebettet (Fallback: `-PflutcloudUrl`); das URL-Feld im Login-Screen ist dann gesperrt.
- Android-Client: Überschreiben-Bestätigung beim Upload über das Storage Access Framework.

## Behoben
- Keine Bugfixes in diesem Release.

## Verbessert
- CI: `opencode-review.yml` aktualisiert.

## Hinweise
- Die CI-Installer sind derzeit nicht signiert. macOS Gatekeeper und Windows SmartScreen können Warnungen anzeigen.
- Details und Build-Anleitung für den Android-Client: `android/README.md`.