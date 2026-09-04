/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.ColorScheme
import com.osfans.trime.util.ColorUtils
import timber.log.Timber

/**
 * The runtime state of an activated theme: its config plus the currently
 * active color scheme, the pre-compiled [ColorTable] and the typed [colors]
 * view over it.
 *
 * A scope outlives scheme switches — switching schemes only rebuilds the
 * table and [colors] — but not theme switches: activating another theme
 * replaces the scope (see [ColorManager.attachTheme]). Views must therefore
 * re-read colors from [colors] (or through [ColorManager]) every time they
 * bind or draw; values read once at construction go stale on scheme changes.
 */
class ThemeScope internal constructor(
    val theme: Theme,
    private val parseColor: (String) -> Int? = { value ->
        runCatching { ColorUtils.parseColor(value) }.getOrNull()
    },
) {
    /** The currently active color scheme, or null before the first activation. */
    var activeColorScheme: ColorScheme? = null
        private set

    private var colorsValue: ThemeColors? = null

    internal var colorTable: ColorTable? = null
        private set

    /** Typed view over the colors of the active scheme. */
    val colors: ThemeColors
        get() = requireNotNull(colorsValue) { "ThemeScope is not initialized" }

    /** Activates [scheme], rebuilding the color table and [colors]. */
    internal fun updateScheme(scheme: ColorScheme) {
        activeColorScheme = scheme
        val table = ColorTable.resolve(scheme, theme.fallbackColors, parseColor)
        colorTable = table
        colorsValue = ThemeColors(table)
        if (table.unresolvedKeys.isNotEmpty()) {
            Timber.w("Unknown color key: %s", table.unresolvedKeys.joinToString { it.key })
        }
        if (table.invalidValues.isNotEmpty()) {
            Timber.w("Invalid color value: %s", table.invalidValues.joinToString { it.key })
        }
    }
}
