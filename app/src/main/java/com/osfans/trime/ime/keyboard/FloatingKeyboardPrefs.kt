/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.SharedPreferences
import com.osfans.trime.data.prefs.PreferenceDelegateOwner

/**
 * Persisted state of the floating keyboard card.
 *
 * Scale and position are written by the card's own resize and drag controls,
 * so they need storage but no settings UI.
 */
class FloatingKeyboardPrefs(
    shared: SharedPreferences,
) : PreferenceDelegateOwner(shared) {
    companion object {
        /** Placeholder value meaning "no saved position yet". */
        const val UNSET_FRACTION = -1f

        const val PORTRAIT_FLOAT = "keyboard_floating_keyboard"
        const val SCALE_PORTRAIT = "keyboard_floating_keyboard_scale_portrait"
        const val SCALE_LANDSCAPE = "keyboard_floating_keyboard_scale_landscape"
        const val COLLAPSED = "keyboard_floating_keyboard_collapsed"
        const val X_PORTRAIT = "keyboard_floating_keyboard_x_portrait"
        const val Y_PORTRAIT = "keyboard_floating_keyboard_y_portrait"
        const val X_LANDSCAPE = "keyboard_floating_keyboard_x_landscape"
        const val Y_LANDSCAPE = "keyboard_floating_keyboard_y_landscape"
    }

    /** Whether portrait starts in the floating card; landscape always floats. */
    val portraitFloat = bool(PORTRAIT_FLOAT, false)

    /** Card width as a percentage of the portrait keyboard width. */
    val scalePortrait = int(SCALE_PORTRAIT, 100)
    val scaleLandscape = int(SCALE_LANDSCAPE, 100)

    val collapsed = bool(COLLAPSED, false)

    /** Card position as a fraction of the available interval, per orientation. */
    val xPortrait = float(X_PORTRAIT, UNSET_FRACTION)
    val yPortrait = float(Y_PORTRAIT, UNSET_FRACTION)
    val xLandscape = float(X_LANDSCAPE, UNSET_FRACTION)
    val yLandscape = float(Y_LANDSCAPE, UNSET_FRACTION)
}
