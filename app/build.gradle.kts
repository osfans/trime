@file:Suppress("UnstableApiUsage")

import android.databinding.tool.ext.capitalizeUS
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.common.hash.Hashing
import com.google.common.io.Files
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.io.StringWriter
import java.util.Properties
import java.util.TimeZone
import java.util.Date
import java.text.SimpleDateFormat

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.cookpad.android.plugin.license-tools") version "1.2.8"
    kotlin("plugin.serialization") version "1.7.20"
    id("com.google.devtools.ksp") version "1.7.20-1.0.8"
}

fun exec(cmd: String): String = ByteArrayOutputStream().use {
    project.exec {
        commandLine = cmd.split(" ")
        standardOutput = it
    }
    it.toString().trim()
}
fun envOrDefault(env: String, default: () -> String): String {
    val v = System.getenv(env)
    return if (v.isNullOrBlank()) default() else v
}

val gitUserOrCIName = envOrDefault("CI_NAME") {
    exec("git config user.name")
}
val gitVersionName = exec("git describe --tags --long --always")
val gitHashShort = exec("git rev-parse --short HEAD")
val gitWorkingOrCIBranch = envOrDefault("CI_BRANCH") {
    exec("git symbolic-ref --short HEAD")
}
val gitRemoteUrl = exec("git remote get-url origin")
    .replaceFirst("^git@github\\.com:", "https://github.com/")
    .replaceFirst("\\.git\$", "")

fun buildInfo(): String {
    val writer = StringWriter()
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(System.currentTimeMillis()))
    writer.append("Builder: ${gitUserOrCIName}\\n")
    writer.append("Build Time: $time UTC\\n")
    writer.append("Build Version Name: ${gitVersionName}\\n")
    writer.append("Git Hash: ${gitHashShort}\\n")
    writer.append("Git Branch: ${gitWorkingOrCIBranch}\\n")
    writer.append("Git Repo: $gitRemoteUrl")
    val info = writer.toString()
    println(info)
    return info
}

android {
    namespace = "com.osfans.trime"
    compileSdk = 33
    ndkVersion = "24.0.8215888"

    defaultConfig {
        applicationId  = "com.osfans.trime"
        minSdk = 21
        targetSdk = 33
        versionCode = 20230501
        versionName = "3.2.12"

        multiDexEnabled = true
        setProperty("archivesBaseName", "trime-$versionName")
        buildConfigField("String", "BUILD_GIT_HASH", "\"${gitHashShort}\"")
        buildConfigField("String", "BUILD_GIT_REPO", "\"${gitRemoteUrl}\"")
        buildConfigField("String", "BUILD_VERSION_NAME", "\"${gitVersionName}\"")
        buildConfigField("String", "BUILD_INFO", "\"${buildInfo()}\"")
    }

    signingConfigs {
        create("release") {
            val keyPropFile = rootProject.file("keystore.properties")
            if (keyPropFile.exists()) {
                val props = Properties()
                props.load(keyPropFile.inputStream())

                storeFile = rootProject.file(props["storeFile"]!!)
                storePassword = props["storePassword"] as? String
                keyAlias = props["keyAlias"] as? String
                keyPassword = props["keyPassword"] as? String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            //proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-android.txt"
            signingConfig = signingConfigs.getByName("release")

            resValue("string", "trime_app_name", "@string/app_name_release")
        }
        debug {
            applicationIdSuffix = ".debug"

            resValue("string", "trime_app_name", "@string/app_name_debug")
        }
    }

    // Use prebuilt JNI library if the "app/prebuilt" exists
    //
    // Steps to generate the prebuilt directory:
    // $ ./gradlew app:assembleRelease
    // $ cp --recursive app/build/intermediates/stripped_native_libs/universalRelease/out/lib app/prebuilt
    if (file("prebuilt").exists()) {
        sourceSets.getByName("main").jniLibs.srcDirs(setOf("prebuilt"))
    } else {
        externalNativeBuild.cmake.path("src/main/jni/CMakeLists.txt")
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // hack workaround lint gradle 8.0.2
    lintOptions {
        isCheckReleaseBuilds = false
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
        }
    }

    splits {
        // Configures multiple APKs based on ABI.
        abi {
            // Enables building multiple APKs per ABI.
            isEnable = true

            // By default all ABIs are included, so use reset() and include to specify that we only
            // want APKs for x86 and x86_64.

            // Resets the list of ABIs that Gradle should create APKs for to none.
            reset()

            // Specifies a list of ABIs that Gradle should create APKs for.
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

            // Specifies that we do not want to also generate a universal APK that includes all ABIs.
            isUniversalApk = false
        }
    }
}

kotlin {
    sourceSets.configureEach {
        kotlin.srcDir("$buildDir/generated/ksp/$name/kotlin/")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val generateDataChecksum by tasks.register<DataChecksumsTask>("generateDataChecksum") {
    inputDir.set(file("src/main/assets"))
    outputFile.set(file("src/main/assets/checksums.json"))
    dependsOn(tasks.findByName("generateLicenseJson"))
}

android.applicationVariants.all {
    val variantName = name.capitalizeUS()
    tasks.findByName("merge${variantName}Assets")?.dependsOn(generateDataChecksum)
}

tasks.register<Delete>("cleanGeneratedAssets") {
    delete(file("src/main/assets/checksums.json"))
    delete(file("src/main/assets/licenses.json"))
}.also { tasks.clean.dependsOn(it) }

tasks.register<Delete>("cleanCxxIntermediates") {
    delete(file(".cxx"))
}.also { tasks.clean.dependsOn(it) }

dependencies {
    ksp(project(":codegen"))
    implementation("com.blankj:utilcodex:1.31.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("cat.ereza:customactivityoncrash:2.4.0")
    implementation("com.github.getActivity:XXPermissions:16.2")
    implementation("com.charleskorn.kaml:kaml:0.49.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.5.4")
    implementation("androidx.navigation:navigation-fragment-ktx:${Extra.navVersion}")
    implementation("androidx.navigation:navigation-ui-ktx:${Extra.navVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Extra.kotlinVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Extra.kotlinCoroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Extra.kotlinCoroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.louiscad.splitties:splitties-bitflags:${Extra.splittiesVersion}")
    implementation("com.louiscad.splitties:splitties-systemservices:${Extra.splittiesVersion}")
    implementation("com.louiscad.splitties:splitties-views-dsl:${Extra.splittiesVersion}")
    implementation("com.louiscad.splitties:splitties-views-dsl-constraintlayout:${Extra.splittiesVersion}")
    implementation("com.louiscad.splitties:splitties-views-dsl-recyclerview:${Extra.splittiesVersion}")
    implementation("com.louiscad.splitties:splitties-views-recyclerview:${Extra.splittiesVersion}")
    implementation("androidx.room:room-runtime:${Extra.roomVersion}")
    ksp("androidx.room:room-compiler:${Extra.roomVersion}")
    implementation("androidx.room:room-ktx:${Extra.roomVersion}")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
}

abstract class DataChecksumsTask : DefaultTask() {
    @get:Incremental
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val file by lazy { outputFile.get().asFile }

    private fun serialize(map: Map<String, String>) {
        file.deleteOnExit()
        file.writeText(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(
                    mapOf<Any, Any>(
                        "sha256" to Hashing.sha256()
                            .hashString(
                                map.entries.joinToString { it.key + it.value },
                                Charset.defaultCharset()
                            )
                            .toString(),
                        "files" to map
                    )
                )
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserialize(): Map<String, String> =
        ((JsonSlurper().parseText(file.readText()) as Map<Any, Any>))["files"] as Map<String, String>

    companion object {
        fun sha256(file: File): String =
            Files.asByteSource(file).hash(Hashing.sha256()).toString()
    }

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        val map =
            file.exists()
                .takeIf { it }
                ?.runCatching {
                    deserialize()
                        // remove all old dirs
                        .filterValues { it.isNotBlank() }
                        .toMutableMap()
                }
                ?.getOrNull()
                ?: mutableMapOf()

        fun File.allParents(): List<File> =
        if (parentFile == null || parentFile.path in map)
            listOf()
        else
            listOf(parentFile) + parentFile.allParents()
        inputChanges.getFileChanges(inputDir).forEach { change ->
            if (change.file.name == file.name)
                return@forEach
            logger.log(LogLevel.DEBUG, "${change.changeType}: ${change.normalizedPath}")
            val relativeFile = change.file.relativeTo(file.parentFile)
            val key = relativeFile.path
            if (change.changeType == ChangeType.REMOVED) {
                map.remove(key)
            } else {
                map[key] = sha256(change.file)
            }
        }
        // calculate dirs
        inputDir.asFileTree.forEach {
            it.relativeTo(file.parentFile).allParents().forEach { p ->
                map[p.path] = ""
            }
        }
        serialize(map.toSortedMap())
    }
}
