// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import Versions.cmakeVersion
import Versions.ndkVersion
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.task

open class NativeBaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")
        target.extensions.configure(CommonExtension::class.java) {
            ndkVersion = target.ndkVersion
            // Use prebuilt JNI library if the "app/prebuilt" exists
            //
            // Steps to generate the prebuilt directory:
            // $ ./gradlew app:assembleRelease
            // $ cp --recursive app/build/intermediates/stripped_native_libs/universalRelease/out/lib app/prebuilt
            if (target.file("prebuilt").exists()) {
                sourceSets.getByName("main").jniLibs.srcDirs(setOf("prebuilt"))
            } else {
                externalNativeBuild {
                    cmake {
                        version = target.cmakeVersion
                        path("src/main/jni/CMakeLists.txt")
                    }
                }
            }

            splits {
                abi {
                    isEnable = true
                    reset()
                    if (ApkRelease.run { target.buildApkRelease }) {
                        include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
                    } else {
                        include(*target.buildABI.split(',').toTypedArray())
                    }
                    isUniversalApk = false
                }
            }
        }
        registerCleanCxxTask(target)
    }

    private fun registerCleanCxxTask(project: Project) {
        project
            .task<Delete>("cleanCxxIntermediates") {
                delete(project.file(".cxx"))
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }
}
