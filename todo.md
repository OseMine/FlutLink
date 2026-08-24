# FlutLink Todo

Tracking-Datei des Projekts: offene Punkte. Erledigte Punkte wandern nach
`archived-todo.md`. Am 2026-08-24 wurden alle datierten Review-Abschnitte
dorthin verschoben; die offenen Issues #293/#317/#318 sind geschlossen.

## Offen

- [ ] Desktop-JVM: Token-Speicher härten — OS-Keyring-Anbindung statt
      600er-Datei unter `$XDG_STATE_HOME/flutlink` (siehe
      `FileKeyValueStorage`), Parität zum Tauri-Client (`keyring`).
- [ ] `SettingsStore` nach `commonMain` heben (DataStore Preferences ist
      multiplatform; der `Context`-Delegate bleibt androidMain-actual) —
      Voraussetzung für Einstellungen im späteren iOS-/Desktop-UI.
- [ ] iOS-Parität (Langläufer): die Compose-UI aus `androidMain`
      (R.string-i18n, EncryptedSharedPreferences, SAF-Aktionen) nach
      `commonMain` heben bzw. den iOS-Placeholder ersetzen; dokumentiert
      in `kmp/README.md` („Stand der iOS-Parität").
