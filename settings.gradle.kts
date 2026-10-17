// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("com.osfans.trime.gradle-compatibility")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPackStorageAccess"
                    url = uri("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.getActivity")
                includeGroup("com.github.WhiredPlanck")
            }
        }
    }
}

rootProject.name = "trime"
include(":app")
include(":codegen")
