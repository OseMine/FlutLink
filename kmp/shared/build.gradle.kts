import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    // iOS targets mirror the desktop client's feature set on Apple devices.
    // They can only be compiled on macOS/Xcode hosts; the CI (android.yml /
    // jvm builds) only exercises androidTarget() + jvm() on Linux.
    listOf(
        iosX64(),
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
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)

            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
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
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.security.crypto)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

android {
    namespace = "com.flutcloud.flutlink"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flutcloud.flutlink.kmp"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        // Optional compile-time FlutCloud server URL (never hard-coded in
        // source). Mirrors the android subproject: the `FLUTCLOUD_URL`
        // environment variable takes precedence and falls back to the local
        // `-PflutcloudUrl=…` Gradle property for development builds.
        val flutcloudUrl: String = System.getenv("FLUTCLOUD_URL")?.takeIf { it.isNotBlank() }
            ?: providers.gradleProperty("flutcloudUrl").orNull.orEmpty()
        buildConfigField(
            "String",
            "FLUTCLOUD_URL",
            "\"$flutcloudUrl\""
        )

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// The Compose Multiplatform plugin generates a `Res` class for the shared
// composeResources (see src/commonMain/composeResources); expose it under the
// app namespace so common code can reference `com.flutcloud.flutlink.resources.*`.
compose.resources {
    packageOfResClass = "com.flutcloud.flutlink.resources"
}