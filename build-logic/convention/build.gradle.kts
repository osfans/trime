// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.attributes.Attribute

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization")
    `java-gradle-plugin`
}

group = "com.osfans.trime.build_logic"

// Our plugin JARs need no external-plugin backport. Select their original
// project artifacts so Gradle keeps the included-build producer dependencies.
val backport = Attribute.of("com.osfans.trime.gradle-visible-backport", String::class.java)
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        attributes.attribute(backport, "backported")
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.snakeyaml)
    testImplementation(kotlin("test"))
    testImplementation("org.ow2.asm:asm:9.9")
}

gradlePlugin {
    plugins {
        register("androidAppConvention") {
            id = "com.osfans.trime.app-convention"
            implementationClass = "AndroidAppConventionPlugin"
        }
        register("dataChecksums") {
            id = "com.osfans.trime.data-checksums"
            implementationClass = "DataChecksumsPlugin"
        }
        register("nativeAppConvention") {
            id = "com.osfans.trime.native-app-convention"
            implementationClass = "NativeAppConventionPlugin"
        }
        register("nativeCacheHash") {
            id = "com.osfans.trime.native-cache-hash"
            implementationClass = "NativeCacheHashPlugin"
        }
        register("openccData") {
            id = "com.osfans.trime.opencc-data"
            implementationClass = "OpenCCDataPlugin"
        }
    }
}
