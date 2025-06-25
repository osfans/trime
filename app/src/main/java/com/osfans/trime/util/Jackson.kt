/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

fun yamlMapper(initializer: YAMLMapper.Builder.() -> Unit = {}): YAMLMapper {
    val builder = YAMLMapper.builder()
    builder.initializer()
    return builder.build()
}

fun legacyYAMLMapper(): ObjectMapper =
    yamlMapper {
        propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        disable(
            DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        )
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        addModule(kotlinModule())
    }
