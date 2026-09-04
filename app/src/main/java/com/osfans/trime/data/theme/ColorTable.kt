/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import androidx.annotation.ColorInt
import com.osfans.trime.data.theme.model.ColorScheme
import java.util.EnumMap

/**
 * Pre-compiled color lookup table for one (theme, active scheme) pair.
 *
 * A color key resolves to the first non-empty value found while walking
 * scheme entries -> theme `fallback_colors` -> built-in fallback chains
 * ([ColorKey.fallbackOf]). Values are resolved once at table build time so
 * runtime lookups are O(1); the walk is replayable for theme-defined keys.
 */
internal class ColorTable private constructor(
    private val entries: EnumMap<ColorKey, Value>,
    val unresolvedKeys: List<ColorKey>,
    val invalidValues: List<ColorKey>,
) {
    /**
     * A resolved color key value. Image values are kept as raw strings; the
     * bitmap is decoded lazily so a missing file stays a runtime concern.
     */
    sealed interface Value {
        /** Chain exhausted: no usable value found for the key. */
        data object None : Value

        /** The value is a parseable color. */
        data class Color(@ColorInt val argb: Int) : Value

        /** The value names an image file under the theme background folder. */
        data class Image(val path: String) : Value
    }

    operator fun get(key: ColorKey): Value = entries[key] ?: Value.None

    companion object {
        private val IMAGE_SUFFIXES = arrayOf(".png", ".webp", ".jpg", ".gif")

        /** Whether a scheme value names an image file instead of a color. */
        fun isImageValue(value: String): Boolean = IMAGE_SUFFIXES.any { value.endsWith(it) }

        /**
         * The first non-empty value for [key], walking
         * scheme colors -> theme fallback_colors -> built-in fallback chains,
         * or null when the chain is exhausted or cyclic.
         *
         * This mirrors the resolution rules of the color table so that keys
         * defined only by a theme resolve exactly like built-in keys.
         */
        fun resolveRaw(
            key: String,
            schemeColors: Map<String, String>,
            fallbackColors: Map<String, String>,
        ): String? {
            var current = key
            val visited = HashSet<String>()
            while (visited.add(current)) {
                val value = schemeColors[current]
                if (!value.isNullOrEmpty()) return value
                val fallback = fallbackColors[current]
                if (!fallback.isNullOrEmpty()) {
                    current = fallback
                    continue
                }
                val altFallback = ColorKey.from(current)?.let(ColorKey::fallbackOf)
                if (altFallback != null) {
                    current = altFallback.key
                } else {
                    return null
                }
            }
            return null
        }

        /**
         * Builds the table for one (theme, scheme) pair.
         *
         * @param parseColor parses a color string, returning null when the
         * value is not a color (dependency-injected for JVM testability).
         */
        fun resolve(
            scheme: ColorScheme,
            fallbackColors: Map<String, String>,
            parseColor: (String) -> Int?,
        ): ColorTable {
            val entries = EnumMap<ColorKey, Value>(ColorKey::class.java)
            val unresolvedKeys = mutableListOf<ColorKey>()
            val invalidValues = mutableListOf<ColorKey>()
            for (key in ColorKey.entries) {
                val raw = resolveRaw(key.key, scheme.colors, fallbackColors)
                if (raw == null) {
                    unresolvedKeys += key
                    continue
                }
                val parsed = parseColor(raw)
                entries[key] =
                    when {
                        isImageValue(raw) -> Value.Image(raw)
                        parsed != null -> Value.Color(parsed)
                        else -> {
                            invalidValues += key
                            Value.None
                        }
                    }
            }
            return ColorTable(entries, unresolvedKeys, invalidValues)
        }
    }
}
