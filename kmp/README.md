# FlutLink KMP (Kotlin Multiplatform)

`kmp/` ist **der** mobile FlutLink-Client: eine Kotlin-Multiplatform-Codebasis
mit Android-, JVM- und iOS-Targets. Er übernimmt den Funktionsumfang des
Desktop-Clients (Tauri) und ist kein separates Produkt — der frühere separate
Android-Port (`android/`) und der Swift-iOS-Port (`ios/`) sind hier
aufgegangen.

> Bewusste Ausnahme (wie beim Desktop-Client): Zwei-Wege-Sync ist
> Desktop-only.

## Struktur

```
kmp/
├── settings.gradle.kts          — Gradle-Repo-Config (google/mavenCentral)
├── build.gradle.kts             — Plugin-Aliase (apply false)
├── gradle/libs.versions.toml    — Versionskatalog (AGP, Kotlin, Compose, Ktor)
├── gradle/wrapper/              — Gradle 9.7.1 Wrapper
├── android-app/                 — Android-Einstiegsmodul (APK):
│   ├── build.gradle.kts         — Application-Plugin (AGP 9, eingebautes Kotlin),
│   │                              Signing (KEYSTORE_PATH), R8, BuildConfig
│   ├── proguard-rules.pro       — Release-Minify-Regeln (DTOs, OkHttp, Serialization)
│   └── src/main/                — Manifest, res/, FlutLinkApplication, MainActivity
├── shared/
│   ├── build.gradle.kts         — KMP-Modul (Android-Library via AGP-KMP-Plugin
│   │                              + jvm + iOS); Compose-Ressourcen, Host-Tests
│   └── src/
│       ├── commonMain/kotlin/   — plattformagnostischer Kern & Datenstack:
│       │                          AuthSession, ApiException, JsonUtil, dto/Models,
│       │                          AccountStore, SessionManager, FlutCloudApi,
│       │                          WebDavApi, MiniXmlParser
│       ├── jvmMain/kotlin/      — Headless-Desktop-JVM-Glue (`desktopCli`-Task)
│       ├── androidMain/         — Compose-UI + plattformgebundene Stores/APIs
│       │                          (EncryptedSharedPreferences, SAF, Updater)
│       ├── androidUnitTest/     — JVM-Unit-Tests (WebDAV/OCS-Parsing, Stores;
│       │                          Task `testAndroidHostTest`)
│       └── iosMain/kotlin/      — iOS-Einstiegspunkt (Main →
│                                  ComposeUIViewController mit Placeholder-UI)
└── iosApp/                      — Xcode-Hülle für den iOS-Build
    ├── Config.xcconfig          — App-Name, Bundle-ID, Version
    ├── iosApp/                  — SwiftUI-Shell (iOSApp.swift, ContentView.swift,
    │                              Assets.xcassets inkl. App-Icon), hostet den
    │                              Compose-ViewController
    └── iosApp.xcodeproj/        — Projekt + geteiltes Scheme; Script-Build-Phase
                                   ruft ./gradlew :shared:link*Framework* auf und
                                   bettet Shared.framework ein
```

## Targets

- **Android** — APK-Einstiegsmodul `:android-app`
  (`com.flutcloud.flutlink`, compileSdk 37 / targetSdk 36, minSdk 26);
  der Multiplatform-Code liegt als `:shared`-Library bei.
  Release-Builds sind minifiziert (R8) und werden per Umgebungsvariable
  `KEYSTORE_PATH` (+ `_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`)
  signiert; ohne Keystore entsteht ein unsigniertes APK.
  Debug-Builds nutzen die Suffixe `.debug`/`-debug`.
- **JVM** — Validierung des gemeinsamen Codes auf der JVM
  (`jvmMain` enthält derzeit nur Glue-Code).
- **iOS** — `iosArm64()` + `iosSimulatorArm64()` (Framework `Shared`,
  statisch). `iosX64()` ist bewusst nicht deklariert — Compose
  Multiplatform 1.11.0 hat die x64-Apple-Artefakte eingestellt. Die Xcode-Hülle
  (`iosApp/`) linkt das Framework zu einer App und produziert in CI eine
  **unsignierte IPA** (Ad-hoc-Signatur ist Aufgabe des Nutzers).

### Stand der iOS-Parität

Die gesamte Compose-UI (Login, Dateien, Admin, Einstellungen) liegt in
`commonMain`; plattformgebundene Dienste werden über das
[`Platform`](shared/src/commonMain/kotlin/com/flutcloud/flutlink/core/Platform.kt)-
Interface injiziert:

| Funktion | Android | iOS |
| --- | --- | --- |
| Token-Speicher | EncryptedSharedPreferences (Keystore) | Keychain |
| Einstellungen | SharedPreferences | NSUserDefaults |
| Upload-Picker | SAF (`OpenDocument`) | `UIDocumentPickerViewController` |
| Download-Ziel | MediaStore Downloads / öffentlicher Ordner | Documents (Files-App) |
| Öffnen/Teilen | FileProvider + `ACTION_VIEW`/`ACTION_SEND` | QuickLook-Preview / Share-Sheet |
| Self-Update | GitHub-Release-APK | nicht verfügbar (Zeile in Einstellungen ausgeblendet) |

Zwei-Wege-Sync bleibt Desktop-only. Strings liegen zweisprachig in
`shared/src/commonMain/composeResources/values{,-de}/strings.xml`
(Generierung als `com.flutcloud.flutlink.resources.Res`).

## Befehle

```sh
cd kmp
./gradlew :android-app:assembleDebug    # Android-Debug-APK bauen
./gradlew :android-app:assembleRelease  # Android-Release-APK (unsigniert/minifiziert)
./gradlew :shared:testAndroidHostTest   # Android-Unit-Tests auf der JVM
./gradlew :shared:compileKotlinJvm      # JVM-Target kompilieren
./gradlew :shared:build                 # alles (android + jvm + Tests)

# iOS (nur macOS/Xcode):
./gradlew :shared:linkReleaseFrameworkIosArm64           # Device-Framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64    # Simulator-Framework
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/FlutLink.xcarchive archive \
  CODE_SIGNING_ALLOWED=NO CODE_SIGN_IDENTITY=""
# Unsignierte IPA aus dem Archiv:
mkdir -p iosApp/build/Payload && \
  cp -R build/FlutLink.xcarchive/Products/Applications/*.app iosApp/build/Payload/ && \
  (cd iosApp/build && zip -qry FlutLink-ios-unsigned.ipa Payload)
```

## Server-URL

Wie beim Desktop-Client wird die FlutCloud-URL zur Compile-Zeit in
`BuildConfig.FLUTCLOUD_URL` gebrannt: die Umgebungsvariable `FLUTCLOUD_URL`
hat Vorrang (genau so machen es es die Release-/Build-Workflows), lokal geht
auch `-PflutcloudUrl=…`. Ist eine URL eingebrannt, sperrt der Login-Screen
das Server-Feld — der Client verbindet sich dann ausschließlich mit diesem
Server.

## CI

- `.github/workflows/kmp.yml` — Build, Unit-Tests, JVM-Target und Lint auf
  Linux; iOS-Kompilierung der beiden Apple-Targets auf macOS.
- `.github/workflows/build.yml` — bei Push: unsigniertes Debug-APK
  (`.github/actions/kmp-android-build`) + unsignierte IPA und dSYM-Archiv
  (`.github/actions/kmp-ios-build`) als Artefakte; die dSYMs dienen der
  Symbolifizierung von `.ips`-Crashreports (mit `atos` gegen den passenden
  Build).
- `.github/workflows/release.yml` — signiertes Release-APK (Secrets
  `ANDROID_KEYSTORE*`) + unsignierte IPA, beide werden an das GitHub-Release
  angehängt; anschließend aktualisiert der `altstore`-Job die
  AltStore-Quellen (`altstore/classic.json`, `altstore/pal.json`) auf
  `main` und hängt beide JSON-Dateien ans Release an (Skript:
  `scripts/update-altstore-sources.mjs`).

## Hinweise

- Neue UI-Texte brauchen Schlüssel in
  `shared/src/commonMain/composeResources/values/strings.xml`
  (en) und `composeResources/values-de/strings.xml` (de); Zugriff über
  `com.flutcloud.flutlink.resources.Res.string.*`.
- Serde-Modelle: `dto/Models.kt` nutzt `kotlinx.serialization`
  (`@Serializable`) — keine Änderung nötig für KMP.
- `jvmMain` braucht die JetBrains-Compose-Runtime, weil das
  Compose-Compiler-Plugin modulweit greift (Version-Check), auch wenn
  `jvmMain` kein Compose enthält.
