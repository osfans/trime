/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * Pure scheme-selection logic: picks the active color scheme from the
 * selected scheme id, the follow-system-day-night preference and the current
 * night state. Extracted from ColorManager so it is unit-testable.
 */
internal object ColorSchemeResolver {
    private const val DEFAULT_SCHEME = "default"
    private const val LIGHT_SCHEME_KEY = "light_scheme"
    private const val DARK_SCHEME_KEY = "dark_scheme"

    /**
     * An explicit selection is kept until the user picks another one, also
     * across mode switches and restarts: only a scheme declaring day/night
     * links follows the mode, and a link pointing nowhere falls back to the
     * scheme itself.
     *
     * @param schemes color schemes of a theme that is known to be usable;
     *   [ThemeLoader] refuses a theme declaring none, so this map is never empty.
     * @return the colors of the scheme in use
     */
    fun resolve(
        schemes: PresetColorSchemes,
        selectedSchemeId: String,
        followSystemDayNight: Boolean,
        isNightMode: Boolean,
    ): ColorScheme {
        require(schemes.isNotEmpty()) { "The theme defines no color scheme" }
        fun scheme(id: String): ColorScheme? = schemes[id]
        fun linkedScheme(source: ColorScheme): ColorScheme? {
            val linkKey = if (isNightMode) DARK_SCHEME_KEY else LIGHT_SCHEME_KEY
            return source[linkKey]?.let { scheme(it) }
        }
        val defaultScheme = scheme(DEFAULT_SCHEME) ?: schemes.values.first()
        if (!followSystemDayNight) {
            return scheme(selectedSchemeId) ?: defaultScheme
        }
        val selected = scheme(selectedSchemeId) ?: return linkedScheme(defaultScheme) ?: defaultScheme
        val lightSchemeId = selected[LIGHT_SCHEME_KEY]
        val darkSchemeId = selected[DARK_SCHEME_KEY]
        return when {
            lightSchemeId != null && darkSchemeId != null ->
                // Both are set: pick by the current mode.
                scheme(if (isNightMode) darkSchemeId else lightSchemeId)
                    ?: linkedScheme(defaultScheme)
                    ?: selected

            lightSchemeId != null ->
                // Light scheme only: this is a dark scheme.
                if (isNightMode) selected else scheme(lightSchemeId) ?: selected

            darkSchemeId != null ->
                // Dark scheme only: this is a light scheme.
                if (isNightMode) scheme(darkSchemeId) ?: selected else selected

            // No links: the scheme is its own light and dark variant, so an
            // explicit choice is kept instead of following the default scheme.
            else -> selected
        }
    }
}
