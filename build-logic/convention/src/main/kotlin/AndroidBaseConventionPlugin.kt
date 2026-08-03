/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

open class AndroidBaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.withType<KotlinCompile> {
            compilerOptions {
                // https://youtrack.jetbrains.com/issue/KT-55947
                jvmTarget.set(JvmTarget.JVM_11)
                // https://youtrack.jetbrains.com/issue/KT-73255/Change-defaulting-rule-for-annotations
                freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }
    }
}
