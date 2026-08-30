# FlutLink v1.3.1

Patch-Release für Desktop (Tauri) und mobilen Client (KMP).

## Neu

- KMP: Dateilisten-Ansichtsmodus (Liste/Raster) wird gespeichert und über Tabs sowie App-Neustarts hinweg beibehalten.
- KMP: Admin-Tab wird für Nicht-Administratoren als gesperrt angezeigt (Parität zum Desktop).
- KMP: Lokalisierung für Dateilisten-Ansichtsmodus und Breadcrumb-Wurzellabel.
- Release-Workflow: Vorab-Prüfung, ob die Update-Signatur-Secrets gesetzt sind, mit verständlicher Fehlermeldung statt kryptischem Asset-Fehler beim Publish.

## Behoben

- Beschädigte `settings.json` wird nun in Quarantäne gestellt statt beim nächsten Speichern stillschweigend überschrieben.
- Headless-Modus (`--download`/`--list`) validiert jetzt den Remote-Pfad.
- QuickLook: Vor/Zurück ist an den Listenrändern deaktiviert; veraltete Thumbnails werden ignoriert.
- Thumbnail-Daten-URLs sind auf `image/*` (MIME-Whitelist) beschränkt – verhindert, dass serverseitig manipulierte Nicht-Bild-Inhalte ins Frontend gelangen.
- Identische Toasts werden dedupliziert.
- Updater: Der „installing“-Status wird vor der Installation gesendet (wurde unter Windows verschluckt); auf macOS/Linux startet sich die App nach dem Update selbst neu.

## Verbessert

- `settings.json` wird nur noch bei tatsächlichen Änderungen geschrieben; `share_seen` wird beim ersten Listing gesetzt (keine Benachrichtigungs-Flut bei Neuinstallationen); O(n)-Pruning.
- `history::clear` ist gegen `record_open` abgesichert und entfernt verwaiste Temp-Dateien des atomaren Schreibens.
- Release-Workflow: Fehler bei der KI-gestützten Release-Notes-Erstellung blockieren den Release nicht mehr (Fallback-Text wird genutzt).

## Hinweise

- Die CI-Installer sind derzeit nicht signiert. macOS Gatekeeper und Windows SmartScreen können Warnungen anzeigen.
- Der Zwei-Wege-Sync ist Desktop-only (nicht im mobilen Client verfügbar).