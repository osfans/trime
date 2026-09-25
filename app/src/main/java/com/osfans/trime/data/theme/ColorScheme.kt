/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * The colors one scheme declares, keyed by color key, exactly as it appears
 * under `preset_color_schemes` in the theme file.
 */
typealias ColorScheme = Map<String, String>

/**
 * Every color scheme a theme declares, keyed by scheme id.
 */
typealias PresetColorSchemes = Map<String, ColorScheme>
