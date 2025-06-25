/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.deserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.osfans.trime.data.theme.ColorScheme

class ColorSchemeDeserializer : JsonDeserializer<ColorScheme>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): ColorScheme {
        val node = p.codec.readTree<JsonNode>(p)
        return node.properties().associate { (s, n) ->
            val rvalue =
                if (n.isNumber) {
                    "#" + n.asLong().toString(16)
                } else {
                    n.asText()
                }
            val nvalue =
                if (rvalue.startsWith("#") || rvalue.startsWith("0x", true)) {
                    val np = rvalue.replace("^#|^0x".toRegex(), "")
                    "#" +
                        when (np.length) {
                            in 1..2 -> np.padStart(2, '0').padEnd(8, '0')
                            in 3..6 -> np.padStart(6, '0')
                            else -> np.padStart(8, '0')
                        }
                } else {
                    rvalue
                }
            s to nvalue
        }
    }
}
