/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException

object KeyActionTokenSerializer : YamlContentPolymorphicSerializer<KeyActionToken>(KeyActionToken::class) {
    override fun selectDeserializer(node: YamlNode): DeserializationStrategy<KeyActionToken> = when (node) {
        is YamlScalar -> KeyActionToken.Plain.serializer()
        is YamlMap -> KeyActionToken.Inline.serializer()
        else -> throw SerializationException("Expect a scalar or a map")
    }
}
