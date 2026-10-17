// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    // Share one Kotlin plugin classpath between the isolated build-plugin modules.
    `kotlin-dsl` apply false
    kotlin("plugin.serialization") version embeddedKotlinVersion apply false
}
