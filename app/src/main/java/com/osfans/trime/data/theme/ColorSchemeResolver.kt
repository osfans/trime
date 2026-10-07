/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.v2.ColorSchemas

/**
 * Pure scheme-selection logic: picks the light or dark palette from the
 * follow-system-day-night preference, the explicit mode selection and the
 * current night state.
 *
 * A V2 theme declares exactly two palettes ([ColorSchemas.light] and
 * [ColorSchemas.dark]); there is no extra scheme to choose from.
 */
internal object ColorSchemeResolver {
    /** The explicit selection value that requests the dark palette. */
    private const val DARK = "dark"

    /**
     * @param schemas the light/dark palettes of the active theme.
     * @param selectedSchemeId the user's explicit choice: `"light"`, `"dark"`,
     *   or a legacy scheme id that no longer exists (treated as light).
     * @return the palette in use for the current mode.
     */
    fun resolve(
        schemas: ColorSchemas,
        selectedSchemeId: String,
        followSystemDayNight: Boolean,
        isNightMode: Boolean,
    ): ColorScheme =
        when {
            followSystemDayNight -> if (isNightMode) schemas.dark else schemas.light
            selectedSchemeId == DARK -> schemas.dark
            else -> schemas.light
        }
}
