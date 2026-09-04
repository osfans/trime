/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.ColorScheme

/**
 * Pure scheme-selection logic: picks the active color scheme from the
 * selected scheme id, the follow-system-day-night preference and the current
 * night state. Extracted from ColorManager so it is unit-testable.
 */
internal object ColorSchemeResolver {
    private const val DEFAULT_SCHEME = "default"
    private const val LIGHT_SCHEME_KEY = "light_scheme"
    private const val DARK_SCHEME_KEY = "dark_scheme"

    fun resolve(
        schemes: List<ColorScheme>,
        selectedSchemeId: String,
        followSystemDayNight: Boolean,
        isNightMode: Boolean,
    ): ColorScheme {
        fun scheme(id: String): ColorScheme? = schemes.find { it.id == id }
        fun linkedScheme(source: ColorScheme): ColorScheme? {
            val linkKey = if (isNightMode) DARK_SCHEME_KEY else LIGHT_SCHEME_KEY
            return source.colors[linkKey]?.let { scheme(it) }
        }
        val defaultScheme = scheme(DEFAULT_SCHEME) ?: schemes.first()
        if (!followSystemDayNight) {
            return scheme(selectedSchemeId) ?: defaultScheme
        }
        val selected = scheme(selectedSchemeId)
        val resolved: ColorScheme? =
            if (selected == null) {
                linkedScheme(defaultScheme)
            } else {
                val lightSchemeId = selected.colors[LIGHT_SCHEME_KEY]
                val darkSchemeId = selected.colors[DARK_SCHEME_KEY]
                when {
                    lightSchemeId != null && darkSchemeId != null ->
                        // Both are set: pick by the current mode.
                        scheme(if (isNightMode) darkSchemeId else lightSchemeId)
                            ?: linkedScheme(defaultScheme)
                    lightSchemeId != null ->
                        // Light scheme only: this is a dark scheme.
                        if (isNightMode) selected else scheme(lightSchemeId) ?: linkedScheme(defaultScheme)
                    darkSchemeId != null ->
                        // Dark scheme only: this is a light scheme.
                        if (isNightMode) scheme(darkSchemeId) ?: linkedScheme(defaultScheme) else selected
                    else -> linkedScheme(defaultScheme)
                }
            }
        return resolved ?: defaultScheme
    }
}
