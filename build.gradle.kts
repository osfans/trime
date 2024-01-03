/**
 * The buildscript block is where you configure the repositories and
 * dependencies for Gradle itself--meaning, you should not include dependencies
 * for your modules here. For example, this block includes the Android plugin for
 * Gradle as a dependency because it provides the additional instructions Gradle
 * needs to build Android app modules.
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.spotless)
}

spotless {
    java {
        importOrder()
        removeUnusedImports()
        target("app/src/main/java/com/osfans/trime/**/*.java")
        googleJavaFormat("1.18.1")
    }
    kotlin {
        target("**/*.kt")
        ktlint("1.0.1")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
}
