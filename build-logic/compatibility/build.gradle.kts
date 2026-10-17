// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.attributes.Attribute

plugins {
    `kotlin-dsl`
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
    implementation("org.ow2.asm:asm-tree:9.9")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("gradleCompatibility") {
            id = "com.osfans.trime.gradle-compatibility"
            implementationClass = "GradleCompatibilityPlugin"
        }
    }
}
