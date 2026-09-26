/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.osfans.trime.util.string
import com.osfans.trime.util.yamlNode
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Reads a font that may be written as a scalar, as a list, or without a value
 * at all (`candidate_font:`), which used to be read as "no font".
 *
 * Contextual for the same reason as [LenientStringListSerializer]: an empty
 * value has to reach this serializer instead of being rejected by kaml first.
 */
@OptIn(InternalSerializationApi::class)
object MaybeStringListSerializer : KSerializer<MaybeStringList> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("MaybeStringList", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: MaybeStringList): Unit = throw UnsupportedOperationException("A font is only read from theme files")

    override fun deserialize(decoder: Decoder): MaybeStringList = when (val node = decoder.yamlNode()) {
        is YamlNull -> MaybeStringList.Empty
        is YamlScalar -> MaybeStringList.Scalar(node.content)
        is YamlList -> MaybeStringList.Sequence(node.items.mapNotNull { it.string })
        else -> throw SerializationException("Expect a scalar, a list or no value")
    }
}
