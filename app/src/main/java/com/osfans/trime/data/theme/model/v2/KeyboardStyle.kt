/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model.v2

import kotlinx.serialization.Serializable

/**
 * Keyboard geometry and key styling, in the V2 theme format aligned with the
 * Hamster / 元书 skin standard. This groups the keyboard-related keys that the
 * legacy format spread flatly across `style`.
 */
@Serializable
data class KeyboardStyle(
    val autoCaps: Boolean = false,
    val horizontalGap: Int = 0,
    val padding: Int = 0,
    val paddingLeft: Int = 0,
    val paddingRight: Int = 0,
    val paddingBottom: Int = 0,
    val paddingLandscape: Int = 0,
    val paddingLandscapeBottom: Int = 0,
    val keyBorder: Int = 0,
    val keyHeight: Int = 0,
    val keyWidth: Float = 0f,
    val textSize: Float = 15f,
    val longTextSize: Float = 15f,
    val symbolTextSize: Float = 0f,
    val labelTextSize: Float = 0f,
    val textOffsetX: Float = 0f,
    val textOffsetY: Float = 0f,
    val symbolOffsetX: Float = 0f,
    val symbolOffsetY: Float = 0f,
    val hintOffsetX: Float = 0f,
    val hintOffsetY: Float = 0f,
    val pressOffsetX: Float = 0f,
    val pressOffsetY: Float = 0f,
    val height: Int = 0,
    val heightLandscape: Int = 0,
    val roundCorner: Float = 0f,
    val shadowRadius: Float = 0f,
    val verticalGap: Int = 0,
    val resetAsciiModeOnFocusChange: Boolean = false,
    val backgroundFolder: String = "backgrounds",
)
