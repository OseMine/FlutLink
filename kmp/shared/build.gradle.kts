import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            // The Kotlin Compose compiler plugin is applied module-wide and runs
            // for every target; give the JVM target the Compose runtime so the
            // version check passes even though jvmMain contains no Compose code.
            implementation(libs.compose.jb.runtime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.security.crypto)

            implementation(libs.okhttp)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.android)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.xpp3)
            implementation(libs.okhttp)
        }
    }
}

dependencies {
    // Compose BOM alignment must go through Gradle's `platform()` — the KMP
    // dependency DSL removed its own `platform` overload in Kotlin 2.3.
    add("androidMainImplementation", platform(libs.compose.bom))
    add("androidMainImplementation", libs.compose.ui)
    add("androidMainImplementation", libs.compose.ui.graphics)
    add("androidMainImplementation", libs.compose.ui.tooling.preview)
    add("androidMainImplementation", libs.compose.material3)
    add("androidMainImplementation", libs.compose.material.icons)
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