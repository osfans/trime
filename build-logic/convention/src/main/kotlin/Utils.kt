// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.api.Project

val json = jacksonObjectMapper()

inline fun envOrDefault(
    env: String,
    default: () -> String,
) = System.getenv(env)?.takeIf { it.isNotBlank() } ?: default()

inline fun Project.propertyOrDefault(
    prop: String,
    default: () -> String,
) = runCatching { property(prop)!!.toString() }.getOrElse {
    default()
}

internal inline fun Project.envOrProp(
    env: String,
    prop: String,
    block: () -> String,
) = envOrDefault(env) {
    propertyOrDefault(prop) {
        block()
    }
}

fun Project.envOrPropOrNull(
    env: String,
    prop: String,
) = System.getenv(env)?.takeIf { it.isNotBlank() }
    ?: runCatching { property(prop)!!.toString() }.getOrNull()
