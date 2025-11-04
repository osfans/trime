// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("unused")
class NativeAppConventionPlugin : NativeBaseConventionPlugin() {
    private val Project.librimeVersion: String
        get() =
            runCmd(
                "git -C app/src/main/jni/librime describe " +
                    "--tags --long --always --exclude=latest",
            )

    private val Project.openccVersion: String
        get() =
            runCmd(
                "git -C app/src/main/jni/OpenCC describe --tags --long --always",
            )

    override fun apply(target: Project) {
        super.apply(target)

        target.extensions.configure<BaseAppModuleExtension> {
            packaging {
                jniLibs {
                    useLegacyPackaging = true
                }
            }
            defaultConfig {
                buildConfigField("String", "LIBRIME_VERSION", "\"${target.librimeVersion}\"")
                buildConfigField("String", "OPENCC_VERSION", "\"${target.openccVersion}\"")
            }
        }
    }
}
