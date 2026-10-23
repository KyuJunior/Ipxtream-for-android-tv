import java.util.Properties

// =============================================================================
//  IPXtream TV — app/build.gradle.kts   (Full — Phases 1–6)
// =============================================================================

plugins {

    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // Kotlin 2.x Compose compiler plugin
}

// Version lives in /version.properties so the CI bump never rewrites this file.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

android {
    namespace  = "com.ipxtream.tv"
    compileSdk = 35

    defaultConfig {
        minSdk       = 23          // EncryptedSharedPreferences requires API 23
        targetSdk     = 35
        versionCode   = versionProps.getProperty("VERSION_CODE").toInt()
        versionName   = versionProps.getProperty("VERSION_NAME")
    }

    // Two release channels from one codebase (docs/play-store-plan.md). Flavor-only code lives in
    // src/github and src/play; anything that downloads/installs code or names a rights holder is github only.
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            // Sideloaded build: same package and key as every install so far, self-updates from GitHub releases.
            applicationId = "com.ipxtream.tv"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            buildConfigField("boolean", "MATCH_AUTO_PLAY", "true")
        }
        create("play") {
            dimension = "store"
            // Google Play build (owner decision D1): its own package, updated only by Play.
            applicationId = "com.xtream.kyu"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            // Matches never starts a channel the user hasn't confirmed (decision D3).
            buildConfigField("boolean", "MATCH_AUTO_PLAY", "false")
        }
    }

    buildFeatures {
        compose = true              // Enable Jetpack Compose
        buildConfig = true          // Generate BuildConfig for VERSION_NAME access
    }
    // composeOptions.kotlinCompilerExtensionVersion is NOT needed when using
    // the kotlin.plugin.compose Gradle plugin (Kotlin 2.x+). It is handled
    // automatically by the plugin matching the Kotlin version.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Known issues as of v1.0.58 live in the baseline; CI fails only on new ones.
        baseline     = file("lint-baseline.xml")
        abortOnError = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true   // android.util.Log etc. are no-ops in JVM tests
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        // Shared consistent signing configuration for CI and local development testing
        create("debugCI") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        val storePass = System.getenv("RELEASE_STORE_PASSWORD")
        val keyPass = System.getenv("RELEASE_KEY_PASSWORD")
        val keyAl = System.getenv("RELEASE_KEY_ALIAS")

        if (!storePass.isNullOrEmpty() && !keyPass.isNullOrEmpty() && !keyAl.isNullOrEmpty()) {
            create("release") {
                storeFile = file("../ipxtream_keystore.jks")
                storePassword = storePass
                keyAlias = keyAl
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            // R8 removes Compose's debug overhead and unused library code (the full icon set
            // alone is tens of MB). Saved data stays readable via app/proguard-rules.pro.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Fallback to custom consistent debug keys if production secrets are not available
                signingConfig = signingConfigs.getByName("debugCI")
            }
        }
        debug {
            // Force debug builds to also use the consistent debugCI keystore so they match
            signingConfig = signingConfigs.getByName("debugCI")
        }
    }
}

dependencies {

    // ─── Compose BOM ──────────────────────────────────────────────────────────
    // Pins ALL androidx.compose.* library versions to a tested, compatible set.
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ─── Compose core ─────────────────────────────────────────────────────────
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)          // M3 — TextField, LinearProgressIndicator, etc.
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation("androidx.compose.material:material-icons-extended")

    // ─── Jetpack Compose for TV ───────────────────────────────────────────────
    // TV Material provides Card, Surface, Button, FilterChip with built-in
    // D-Pad focus (scale / glow / border on focus) without manual onKeyEvent.
    implementation(libs.tv.material)

    // ─── Activity + Lifecycle Compose ─────────────────────────────────────────
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)      // LocalLifecycleOwner, collectAsStateWithLifecycle
    implementation(libs.lifecycle.viewmodel.compose)    // viewModel() Composable factory
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // ─── Kotlin Coroutines ────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ─── Encrypted Credentials (replaces Windows DPAPI) ───────────────────────
    implementation(libs.security.crypto)

    // ─── Networking — Retrofit2 + OkHttp + Gson ───────────────────────────────
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // ─── AndroidX Media3 / ExoPlayer ──────────────────────────────────────────
    implementation(libs.media3.exoplayer)           // Core player engine
    implementation(libs.media3.exoplayer.hls)       // HLS (M3U8) support for live streams
    implementation(libs.media3.ui)                  // PlayerView (wrapped in AndroidView)

    // ─── Image loading (Coil) ─────────────────────────────────────────────────
    implementation(libs.coil.compose)               // AsyncImage for posters + channel logos

    // ─── AndroidX Core ────────────────────────────────────────────────────────
    // Required for NotificationCompat (used in DownloadService)
    implementation("androidx.core:core-ktx:1.13.1")

    // ─── Debug tooling ────────────────────────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ─── Testing ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.android.junit.ext)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
