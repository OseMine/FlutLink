import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.flutcloud.flutlink"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flutcloud.flutlink"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        // Optional compile-time FlutCloud server URL (never hard-coded in
        // source). Mirrors the desktop client: the `FLUTCLOUD_URL` environment
        // variable takes precedence (used by the release/build workflows) and
        // falls back to the local `-PflutcloudUrl=…` Gradle property for
        // development builds.
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

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { file(it) }
            if (keystoreFile?.exists() == true) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEYSTORE_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEYSTORE_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystoreFile = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }?.let { file(it) }
            if (keystoreFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.xpp3)
}