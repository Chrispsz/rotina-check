plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)          // Kotlin 2.x Compose compiler plugin
    alias(libs.plugins.android.junit5)          // JUnit 5 support for Android unit tests
}

android {
    namespace = "com.chrispsz.rotinacheck"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chrispsz.rotinacheck"
        // minSdk 34 (Android 14+): every exact-alarm / notification API is
        // stable, java.time is always present, no desugaring, no SDK guards.
        minSdk = 34
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Public repo → no keystore is committed. The debug-signed release
            // APK stays installable and keeps the tag→release pipeline simple.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // JUnit 5 platform for all local unit tests.
        unitTests.all { it.useJUnitPlatform() }
    }

    lint {
        abortOnError = true
        disable += "GradleDependency"

        // Advisory upgrade-train checks, disabled under an explicit policy:
        // they re-fire on EVERY new Android/AGP release regardless of code
        // quality. Upgrade cadence is managed deliberately instead.
        disable += "OldTargetApi"
        disable += "AndroidGradlePluginVersion"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    // ---- AndroidX core + lifecycle ------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)       // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)     // viewModel() in Compose

    // ---- Compose (BOM-managed) ------------------------------------------------------
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // ---- Persistence: DataStore (the only storage layer, 100% local) ---------------
    implementation(libs.androidx.datastore.preferences)

    // ---- Coroutines ------------------------------------------------------------------
    implementation(libs.kotlinx.coroutines.android)

    // ---- JUnit 5 (local unit tests) ----------------------------------------------------------
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.org.json)      // same org.json API as the platform, for JVM tests
    testRuntimeOnly(libs.junit.platform.launcher)
}
