# FlutLink KMP (Kotlin Multiplatform)

`kmp/` ist ein Kotlin-Multiplatform-Subprojekt, das den Kotlin-Code des
Android-Clients (`android/`) als Multiplattform-Modul spiegelt. Es ist kein
separates Produkt — es übernimmt den Funktionsumfang des Desktop-Clients
(Tauri) und des Android-Ports und stellt den gemeinsamen Kotlin-Code in einem
KMP-Modul bereit.

> Bewusste Ausnahme (wie beim Android-Port): Zwei-Wege-Sync ist Desktop-only.

## Struktur

```
kmp/
├── settings.gradle.kts          — Gradle-Repo-Config (google/mavenCentral)
├── build.gradle.kts             — Plugin-Aliase (apply false)
├── gradle/libs.versions.toml    — Versionskatalog (mirror android/)
├── gradle/wrapper/              — Gradle 8.13 Wrapper (kopiert aus android/)
└── shared/
    ├── build.gradle.kts         — KMP-Modul: androidTarget() + jvm()
    └── src/
        ├── commonMain/          — plattformagnostischer Kotlin-Code
        │   └── com/flutcloud/flutlink/
        │       ├── data/AuthSession.kt, ApiException.kt, JsonUtil.kt
        │       └── data/dto/Models.kt
        ├── androidMain/         — Android-spezifischer Kotlin-Code
        │   ├── AndroidManifest.xml
        │   ├── res/             — Strings, Themes, XML (aus android/)
        │   └── kotlin/com/flutcloud/flutlink/
        │       ├── AppContainer.kt, FlutLinkApplication.kt, MainActivity.kt
        │       ├── core/        — AccountStore, SessionManager, SettingsStore
        │       ├── data/        — FlutCloudApi, WebDavApi, HttpClientFactory, …
        │       └── ui/          — Compose-Screens (Home, Files, Admin, Settings)
        └── androidUnitTest/     — JVM-Unit-Tests (aus android/app/src/test)
```

## Targets

- `androidTarget()` — Android (compileSdk 36, minSdk 26), Package
  `com.flutcloud.flutlink.kmp`
- `jvm()` — JVM-Target; kompiliert `commonMain` (derzeit ohne
  `jvmMain`-Quellen, nur für die Plattform-Validierung der gemeinsamen
  Module).
- `iosX64()` / `iosArm64()` / `iosSimulatorArm64()` — iOS-Targets mit
  Framework-Binary (`Shared`); sie sind deklariert und kompilieren nur auf
  macOS/Xcode-Hosts, enthalten derzeit aber noch keinen `iosMain`-Code. Der
  produktive iOS-Port lebt weiterhin in `ios/` als Swift/SwiftUI-App.

Der Gradle-Wrapper (8.13) und der Versionskatalog sind bewusst identisch mit
`android/`, damit beide Projekte mit demselben Tooling bauen.

## Befehle

```sh
cd kmp
./gradlew :shared:assembleDebug        # Android-Debug-APK bauen
./gradlew :shared:testDebugUnitTest    # Android-Unit-Tests auf der JVM
./gradlew :shared:compileKotlinJvm     # JVM-Target kompilieren
./gradlew :shared:build                # alles (android + jvm + Tests)
```

## Hinweise

- Neue UI-Texte brauchen Schlüssel in `androidMain/res/values/strings.xml`
  (en) und `res/values-de/strings.xml` (de) — analog zu `android/`.
- Serde-Modelle: `dto/Models.kt` nutzt `kotlinx.serialization`
  (`@Serializable`) — keine Änderung nötig für KMP.
- Die Compose-BOM-Ausrichtung läuft über Gradle-`platform()` im
  `dependencies`-Block (das KMP-DSL hat den eigenen `platform`-Overload in
  Kotlin 2.3 entfernt, siehe KT-58759).
- `jvmMain` braucht die JetBrains-Compose-Runtime, weil das
  Compose-Compiler-Plugin modulweit greift (Version-Check), auch wenn
  `jvmMain` kein Compose enthält.