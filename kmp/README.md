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
├── gradle/wrapper/              — Gradle 8.13 Wrapper
├── shared/
│   ├── build.gradle.kts         — KMP-Modul (android application + jvm + iOS);
│   │                              Signing (KEYSTORE_PATH), R8, BuildConfig
│   ├── proguard-rules.pro       — Release-Minify-Regeln (DTOs, OkHttp, Serialization)
│   └── src/
│       ├── commonMain/kotlin/   — plattformagnostischer Kern:
│       │                          AuthSession, ApiException, JsonUtil, dto/Models
│       ├── androidMain/         — komplette Android-App (Manifest, res/, Compose-UI,
│       │                        Application/MainActivity, Stores, APIs)
│       ├── androidUnitTest/     — JVM-Unit-Tests (WebDAV/OCS-Parsing, Stores)
│       └── iosMain/kotlin/      — iOS-Einstiegspunkt (MainViewController →
│                                  ComposeUIViewController mit Placeholder-UI)
└── iosApp/                      — Xcode-Hülle für den iOS-Build
    ├── Config.xcconfig          — App-Name, Bundle-ID, Version
    ├── iosApp/                  — SwiftUI-Shell (iOSApp.swift, ContentView.swift,
    │                              Assets.xcassets), hostet den Compose-ViewController
    └── iosApp.xcodeproj/        — Projekt + geteiltes Scheme; Script-Build-Phase
                                   ruft ./gradlew :shared:link*Framework* auf und
                                   bettet Shared.framework ein
```

## Targets

- **Android** — `androidTarget()` als *Application*-Modul
  (`com.flutcloud.flutlink`, compileSdk/targetSdk 36, minSdk 26).
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

Der funktionsvolle Client-Code liegt in `androidMain` (EncryptedSharedPreferences,
Storage Access Framework, Android-Resourcen). Die Portierung dieser UI nach
`commonMain` (expect/actual für Keychain, File-Access, Resourcen) ist als
Folgearbeit notiert — bis dahin hostet das iOS-Framework einen
Placeholder-Screen; die Build-/CI-Infrastruktur (Framework, Xcode-Projekt,
IPA) ist vollständig eingerichtet.

## Befehle

```sh
cd kmp
./gradlew :shared:assembleDebug         # Android-Debug-APK bauen
./gradlew :shared:assembleRelease       # Android-Release-APK (unsigniert/minifiziert)
./gradlew :shared:testDebugUnitTest     # Android-Unit-Tests auf der JVM
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
  (`.github/actions/kmp-android-build`) + unsignierte IPA
  (`.github/actions/kmp-ios-build`) als Artefakte.
- `.github/workflows/release.yml` — signiertes Release-APK (Secrets
  `ANDROID_KEYSTORE*`) + unsignierte IPA, beide werden an das GitHub-Release
  angehängt.

## Hinweise

- Neue UI-Texte brauchen Schlüssel in `shared/src/androidMain/res/values/strings.xml`
  (en) und `res/values-de/strings.xml` (de).
- Serde-Modelle: `dto/Models.kt` nutzt `kotlinx.serialization`
  (`@Serializable`) — keine Änderung nötig für KMP.
- `jvmMain` braucht die JetBrains-Compose-Runtime, weil das
  Compose-Compiler-Plugin modulweit greift (Version-Check), auch wenn
  `jvmMain` kein Compose enthält.
