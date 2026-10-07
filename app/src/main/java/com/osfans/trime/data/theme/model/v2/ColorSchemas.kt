/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model.v2

import com.osfans.trime.data.theme.ColorScheme
import kotlinx.serialization.Serializable

/**
 * The color schemes of a V2 theme: exactly two palettes, a light (day) one and
 * a dark (night) one. No extra scheme is allowed — the theme format permits
 * only [light] and [dark], and the runtime picks one by the current mode.
 */
@Serializable
data class ColorSchemas(
    val light: ColorScheme = emptyMap(),
    val dark: ColorScheme = emptyMap(),
) {
    /** Whether both palettes are absent, leaving nothing to render with. */
    val isEmpty: Boolean
        get() = light.isEmpty() && dark.isEmpty()
}
