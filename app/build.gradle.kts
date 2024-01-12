@file:Suppress("UnstableApiUsage")

import org.gradle.configurationcache.extensions.capitalized

plugins {
    id("com.osfans.trime.native-app-convention")
    id("com.osfans.trime.data-checksums")
    id("com.osfans.trime.native-cache-hash")
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.osfans.trime"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId  = "com.osfans.trime"
        minSdk = 21
        targetSdk = 33
        versionCode = 20240301
        versionName = "3.2.17"

        multiDexEnabled = true
        setProperty("archivesBaseName", "$applicationId-$buildVersionName")
        buildConfigField("String", "BUILDER", "\"${project.builder}\"")
        buildConfigField("long", "BUILD_TIMESTAMP", project.buildTimestamp)
        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${project.buildCommitHash}\"")
        buildConfigField("String", "BUILD_GIT_REPO", "\"${project.buildGitRepo}\"")
        buildConfigField("String", "BUILD_VERSION_NAME", "\"${project.buildVersionName}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            //proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-android.txt"
            signingConfig = with(ApkRelease) {
                if (project.buildApkRelease) {
                    signingConfigs.create("release") {
                        storeFile = file(project.storeFile!!)
                        storePassword = project.storePassword
                        keyAlias = project.keyAlias
                        keyPassword = project.keyPassword
                    }
                } else null
            }

            resValue("string", "trime_app_name", "@string/app_name_release")
        }
        debug {
            applicationIdSuffix = ".debug"

            resValue("string", "trime_app_name", "@string/app_name_debug")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    // hack workaround lint gradle 8.0.2
    lint {
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

kotlin {
    sourceSets.configureEach {
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/$name/kotlin"))
    }
}

aboutLibraries {
    configPath = "app/licenses"
    excludeFields = arrayOf("generated", "developers", "organization", "scm", "funding", "content")
    fetchRemoteLicense = false
    fetchRemoteFunding = false
    includePlatform = false
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android.applicationVariants.all {
    val variantName = name.capitalized()
    tasks.findByName("generateDataChecksums")?.also {
        tasks.getByName("merge${variantName}Assets").dependsOn(it)
    }
}

dependencies {
    ksp(project(":codegen"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.flexbox)
    implementation(libs.kaml)
    implementation(libs.timber)
    implementation(libs.utilcode)
    implementation(libs.xxpermissions)
    implementation(libs.splitties.bitflags)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.coordinatorlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.aboutlibraries.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.junit)
}
