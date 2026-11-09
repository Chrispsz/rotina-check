// Root build file — plugin classpath resolved via the version catalog, applied
// per-module. Intentionally lean: no KSP/Hilt/Room for this app (DataStore-only
// persistence keeps the APK tiny and the build fast).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.junit5) apply false
}
