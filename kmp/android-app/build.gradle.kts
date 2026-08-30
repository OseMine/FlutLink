plugins {
    alias(libs.plugins.android.application)
    // Supplies the Compose compiler for AGP 9's built-in Kotlin.
    alias(libs.plugins.kotlin.compose)
}

// Android entry point for the KMP client. Since AGP 9 the application plugin
// ships built-in Kotlin support, so this module applies no Kotlin plugin —
// the multiplatform code lives in :shared and is consumed as a library.
android {
    namespace = "com.flutcloud.flutlink.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.flutcloud.flutlink"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.3.1"

        // Optional compile-time FlutCloud server URL (never hard-coded in
        // source). The `FLUTCLOUD_URL` environment variable takes precedence
        // and falls back to the local `-PflutcloudUrl=…` Gradle property for
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

    // AGP 9 provides both Kotlin and the Compose compiler via built-in
    // support — no extra plugins needed here.
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
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
