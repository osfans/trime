/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:Suppress("UnstableApiUsage")

import java.util.regex.Pattern

plugins {
    id("com.osfans.trime.app-convention")
    id("com.osfans.trime.native-app-convention")
    id("com.osfans.trime.data-checksums")
    id("com.osfans.trime.native-cache-hash")
    id("com.osfans.trime.opencc-data")
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
}

// The default is the upstream release identity; never infer the manifest version from git describe.
val appVersionName = project.envOrPropOrNull("BUILD_VERSION_NAME", "buildVersionName") ?: "3.3.13"
require(appVersionName.isNotBlank()) { "A nonempty buildVersionName is required" }

val selectedTestBuildType = providers.gradleProperty("trime.testBuildType").orElse("debug").get()
require(selectedTestBuildType in setOf("debug", "release")) { "trime.testBuildType must be debug or release" }

android {
    testBuildType = selectedTestBuildType
    namespace = "com.osfans.trime"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.osfans.trime"
        minSdk = 21
        targetSdk = 37
        versionCode = 20261101
        versionName = appVersionName
        testInstrumentationRunner = if (selectedTestBuildType == "release") {
            "com.osfans.trime.UserDictionaryUpgradeInstrumentation"
        } else {
            "com.osfans.trime.StartupResponsivenessInstrumentation"
        }

        multiDexEnabled = true
        buildConfigField("String", "BUILDER", "\"${project.builder}\"")
        buildConfigField("long", "BUILD_TIMESTAMP", project.buildTimestamp)
        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${project.buildCommitHash}\"")
        buildConfigField("String", "BUILD_GIT_REPO", "\"${project.buildGitRepo}\"")
        buildConfigField("String", "BUILD_VERSION_NAME", "\"$appVersionName\"")
        // Explicit opt-in for the same release build used by both T08 benchmark cohorts.
        buildConfigField(
            "boolean",
            "PERFORMANCE_TRACING",
            providers.gradleProperty("trime.performanceTrace").map { it.toBooleanStrict().toString() }.orElse("false").get(),
        )
    }

    base {
        // https://www.norio.be/blog/archivesBaseName-removed-from-gradle9.html
        archivesName = "${android.defaultConfig.applicationId}-$appVersionName"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        resValues = true
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                project.signKeyFile?.let {
                    signingConfigs.create("release") {
                        storeFile = it
                        storePassword = project.signKeyStorePwd
                        keyAlias = project.signKeyAlias
                        keyPassword = project.signKeyPwd
                    }
                }

            resValue("string", "trime_app_name", "@string/app_name_release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            resValue("string", "trime_app_name", "@string/app_name_debug")
        }
        all {
            // remove META-INF/version-control-info.textproto
            @Suppress("UnstableApiUsage")
            vcsInfo.include = false
        }
    }

    sourceSets.getByName("androidTest") {
        if (selectedTestBuildType == "release") {
            // The ordinary suite references Debug-only editor fixtures. Release upgrade
            // acceptance uses the same two native Java classes through a dedicated root.
            java.directories.clear()
            kotlin.directories.clear()
            manifest.srcFile("src/upgradeTest/AndroidManifest.xml")
        }
        java.directories.add("src/sharedUpgradeTest/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        warningsAsErrors = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/*.version",
                    "/META-INF/*.kotlin_module", // cannot be excluded actually
                    "/META-INF/androidx/**",
                    // SDK Console ownership metadata is not used by these libraries at runtime.
                    "/META-INF/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/verification.properties",
                    "/META-INF/com/mikepenz/aboutlibraries-core/verification.properties",
                    "/DebugProbesKt.bin",
                    "/kotlin-tooling-metadata.json",
                )
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        // A test APK's applicationId is its OWN id. Bind both runners to the actual
        // tested app variant instead of assuming the special ${applicationId} value.
        variant.androidTest?.manifestPlaceholders?.put("trimeTestedApplicationId", variant.applicationId)
    }
}

aboutLibraries {
    collect {
        configPath.set(file("licenses").takeIf { it.exists() })
        fetchRemoteLicense.set(false)
        fetchRemoteFunding.set(false)
        includePlatform.set(false)
    }
    library {
        // JitPack publishes StorageAccess' Apache-2.0 license as "Other".
        // Exclude that metadata and restore the audited entry from app/licenses.
        exclusionPatterns.add(
            Pattern.compile("com.github.WhiredPlanck:StorageAccess", Pattern.LITERAL),
        )
    }
    export {
        excludeFields.set(
            setOf("generated", "developers", "organization", "scm", "funding", "content"),
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    ksp(project(":codegen"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.flexbox)
    implementation(libs.bravh)
    implementation(libs.timber)
    implementation(libs.xxpermissions)
    implementation(libs.kodein.di)
    implementation(libs.kaml)
    // Use KAML's existing parser events to reject oversized/deep imports before building a node tree.
    implementation(libs.snakeyaml.engine.kmp)
    implementation(libs.storageaccess)
    implementation(libs.splitties.bitflags)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.coordinatorlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.aboutlibraries.core)
    implementation(libs.iconics.core)
    implementation(libs.community.material.typeface) {
        artifact { type = "aar" }
    }

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.junit)
}

configurations {
    all {
        // remove Baseline Profile Installer or whatever it is...
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
}
