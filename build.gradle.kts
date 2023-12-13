/**
 * The buildscript block is where you configure the repositories and
 * dependencies for Gradle itself--meaning, you should not include dependencies
 * for your modules here. For example, this block includes the Android plugin for
 * Gradle as a dependency because it provides the additional instructions Gradle
 * needs to build Android app modules.
 */

plugins {
    id("com.android.application") version Versions.androidGradlePlugin apply false
    id("com.android.library") version Versions.androidGradlePlugin apply false
    kotlin("android") version Versions.kotlin apply false
    id("com.diffplug.spotless") version "6.23.3"
    id("com.mikepenz.aboutlibraries.plugin") version Versions.aboutlibraries apply false
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
