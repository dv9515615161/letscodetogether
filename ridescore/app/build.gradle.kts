import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from keystore.properties (never committed) or, in CI,
// from environment variables. A build with neither still succeeds - it just
// produces an unsigned release, which is what an open pull request should get.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Blank counts as absent: a CI step that is skipped leaves its output as an
// empty string rather than unsetting it, and an empty keystore path would fail
// the build instead of falling back to an unsigned one.
fun secret(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStoreFile = secret("storeFile", "RIDESCORE_KEYSTORE")
val releaseStorePassword = secret("storePassword", "RIDESCORE_KEYSTORE_PASSWORD")
val releaseKeyAlias = secret("keyAlias", "RIDESCORE_KEY_ALIAS")
val releaseKeyPassword = secret("keyPassword", "RIDESCORE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.ridescore.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ridescore.app"
        minSdk = 26
        targetSdk = 35
        // Bumped per release. Play rejects a bundle whose versionCode is not
        // higher than the last one uploaded, so CI can override it.
        versionCode = (project.findProperty("ridescoreVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("ridescoreVersionName") as String?) ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A committed debug key, so every build - local or CI - signs with the
        // same certificate and a new APK installs over an older one instead of
        // failing with a signature mismatch. This is a debug key with Android's
        // well-known password; it grants nothing and protects nothing.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Play delivers per-device slices from one bundle, which is what keeps the
    // download small even though the OCR model is bundled.
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // OCR fallback only. Bundled model, runs fully on-device, no network calls.
    implementation(libs.mlkit.text.recognition)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
