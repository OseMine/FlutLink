import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

kotlin {
    // Android target of the KMP module, configured through the AGP 9
    // `com.android.kotlin.multiplatform.library` plugin (single variant).
    // The APK entry point lives in the sibling :android-app module.
    android {
        namespace = "com.flutcloud.flutlink"
        compileSdk = 37
        minSdk = 26

        // Resource processing is opt-in with this plugin; the Android UI
        // lives here, so string resources must stay enabled.
        androidResources {
            enable = true
        }

        // Host-side unit tests (formerly androidUnitTest) are opt-in with
        // this plugin; enable them so the JVM test suite keeps running.
        withHostTestBuilder {}.configure {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    // iOS targets mirror the desktop client's feature set on Apple devices.
    // They can only be compiled on macOS/Xcode hosts; the CI (android.yml /
    // jvm builds) only exercises android target + jvm on Linux.
    // Note: iosX64 is not declared — Compose Multiplatform 1.11.0 dropped
    // x64 Apple artifacts (only 1.11.0-alpha01 publishes them), so resolving
    // commonMain deps for iosX64 fails on every host.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            // KMP-F13: The stable `compose.material3` alias (1.9.0) removed the
            // Material 3 Expressive API. Use the pre-release that still ships it
            // (`Material3ExpressiveTheme`, expressive `MotionScheme`, ...).
            api("org.jetbrains.compose.material3:material3:1.9.0-alpha04")
            api(compose.materialIconsExtended)
            api(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)

            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            // WebDavApi uses okio Paths/FileSystem directly in common code.
            implementation(libs.okio)
        }
        jvmMain.dependencies {
            // The Kotlin Compose compiler plugin is applied module-wide and runs
            // for every target; give the JVM target the Compose runtime so the
            // version check passes even though jvmMain only holds expect/actual
            // glue code.
            implementation(compose.runtime)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.swing)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.security.crypto)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.xpp3)
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}

// The Compose Multiplatform plugin generates a `Res` class for the shared
// composeResources (see src/commonMain/composeResources); expose it under the
// app namespace so common code can reference `com.flutcloud.flutlink.resources.*`.
compose.resources {
    packageOfResClass = "com.flutcloud.flutlink.resources"
}

// Headless Desktop-JVM client (see src/jvmMain/.../desktop/Main.kt). Runs the
// shared network stack against a FlutCloud server without any UI.
tasks.register<JavaExec>("desktopCli") {
    group = "flutlink"
    description = "Run the headless Desktop-JVM client CLI."
    mainClass.set("com.flutcloud.flutlink.desktop.MainKt")
    classpath(sourceSets.named("jvmMain").map { it.runtimeClasspath })
}
