/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * The orientation-driven mode of the floating keyboard.
 *
 * Landscape always floats, but the user may dock it for the current landscape
 * session. Portrait uses the persisted preference, which defaults to the
 * normal docked keyboard.
 */
internal object FloatingKeyboardMode {
    fun shouldFloat(
        landscape: Boolean,
        dockedForSession: Boolean,
        portraitPreference: Boolean,
    ): Boolean = if (landscape) !dockedForSession else portraitPreference
}
