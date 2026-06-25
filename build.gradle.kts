// Top-level build file — configuration that applies to ALL subprojects.
// App-specific dependencies go in app/build.gradle.kts, NOT here.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
}
