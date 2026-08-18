# Archived todo items

Abgeschlossene Punkte, aus `todo.md` hierher verschoben. Details zum jeweiligen
Lauf stehen im zugehörigen Abschnitt von `todo.md` (bzw. im
Issue-Kommentar), hier nur die Kurzreferenz.

## Lauf 12 (2026-08-18)

- [x] **A9-9 (Feature, minor):** Android-Shares nur Public-Link —
      `FilesViewModel.createPublicShare` hartkodierte `shareType = 3`;
      User-/Gruppen-Shares (Desktop P1) und `publicUpload`-Option fehlten.
      Fix: `FilesViewModel.createShare` (mirror von Desktop
      `webdav_create_share`) akzeptiert `shareType`/`shareWith`/
      `publicUpload` und verweigert User-/Gruppen-Shares ohne Empfänger
      (`share_recipient_required`); `ShareDialog` in `FilesScreen.kt` hat
      einen Link/User/Group-Typ-Selektor (FilterChips), Empfängerfeld für
      User/Gruppen und Passwort/Ablauf/`publicUpload`-Checkbox für Links;
      `listShares`/`deleteShare` waren bereits in der UI verdrahtet (Lauf 11)
      — kein Dead Code mehr. Neue i18n-Keys `new_share`, `share_recipient`,
      `share_recipient_required`, `share_public_upload` (en+de).
      Verifikation: `:app:assembleDebug`, `:app:testDebugUnitTest`,
      `:app:lintDebug` grün.