/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * Canonical theme color keys. The YAML spelling is the lowercased enum name.
 *
 * The enum covers every key the built-in fallback chains can hop through and
 * every key the runtime requests; theme-defined keys that do not appear here
 * are resolved through [com.osfans.trime.data.theme.ColorTable]'s string path.
 */
enum class ColorKey {
    BACK_COLOR,
    BORDER_COLOR,
    CANDIDATE_BACKGROUND,
    CANDIDATE_BORDER_COLOR,
    CANDIDATE_SEPARATOR_COLOR,
    CANDIDATE_TEXT_COLOR,
    COMMENT_TEXT_COLOR,
    HILITED_BACK_COLOR,
    HILITED_CANDIDATE_BACK_COLOR,
    HILITED_CANDIDATE_BUTTON_COLOR,
    HILITED_CANDIDATE_TEXT_COLOR,
    HILITED_COMMENT_TEXT_COLOR,
    HILITED_KEY_BACK_COLOR,
    HILITED_KEY_BORDER_COLOR,
    HILITED_KEY_TEXT_COLOR,
    HILITED_KEY_SYMBOL_COLOR,
    HILITED_LABEL_COLOR,
    HILITED_OFF_KEY_BACK_COLOR,
    HILITED_OFF_KEY_BORDER_COLOR,
    HILITED_OFF_KEY_TEXT_COLOR,
    HILITED_OFF_KEY_SYMBOL_COLOR,
    HILITED_ON_KEY_BACK_COLOR,
    HILITED_ON_KEY_BORDER_COLOR,
    HILITED_ON_KEY_TEXT_COLOR,
    HILITED_ON_KEY_SYMBOL_COLOR,
    HILITED_POPUP_BACK_COLOR,
    HILITED_POPUP_TEXT_COLOR,
    HILITED_TEXT_COLOR,
    KEY_BACK_COLOR,
    KEY_BORDER_COLOR,
    KEY_TEXT_COLOR,
    KEY_SYMBOL_COLOR,
    KEYBOARD_BACK_COLOR,
    KEYBOARD_BACKGROUND,
    LABEL_COLOR,
    LIQUID_KEYBOARD_BACKGROUND,
    LONG_TEXT_BACK_COLOR,
    LONG_TEXT_COLOR,
    OFF_KEY_BACK_COLOR,
    OFF_KEY_BORDER_COLOR,
    OFF_KEY_TEXT_COLOR,
    OFF_KEY_SYMBOL_COLOR,
    ON_KEY_BACK_COLOR,
    ON_KEY_BORDER_COLOR,
    ON_KEY_TEXT_COLOR,
    ON_KEY_SYMBOL_COLOR,
    POPUP_BACK_COLOR,
    POPUP_TEXT_COLOR,
    ROOT_BACKGROUND,
    SHADOW_COLOR,
    TEXT_BACK_COLOR,
    TEXT_COLOR,
    ;

    /** The color key as it appears in theme YAML files. */
    val key: String = name.lowercase()

    companion object {
        private val byKey = entries.associateBy { it.key }

        /**
         * The typed [ColorKey] for a YAML color key, or null for keys that
         * only a theme defines.
         */
        fun from(key: String): ColorKey? = byKey[key]

        /**
         * Built-in fallback chains previously hardcoded in ColorManager:
         * when a scheme does not define a key, its chain entry points at the
         * key to try next. The scheme itself is always consulted first.
         */
        internal val builtinFallbackColors: Map<ColorKey, ColorKey> =
            mapOf(
                CANDIDATE_TEXT_COLOR to TEXT_COLOR,
                COMMENT_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                BORDER_COLOR to BACK_COLOR,
                CANDIDATE_SEPARATOR_COLOR to BORDER_COLOR,
                HILITED_TEXT_COLOR to TEXT_COLOR,
                HILITED_BACK_COLOR to BACK_COLOR,
                HILITED_CANDIDATE_TEXT_COLOR to HILITED_TEXT_COLOR,
                HILITED_CANDIDATE_BACK_COLOR to HILITED_BACK_COLOR,
                HILITED_CANDIDATE_BUTTON_COLOR to HILITED_CANDIDATE_BACK_COLOR,
                HILITED_LABEL_COLOR to HILITED_CANDIDATE_TEXT_COLOR,
                HILITED_COMMENT_TEXT_COLOR to COMMENT_TEXT_COLOR,
                HILITED_KEY_BACK_COLOR to HILITED_CANDIDATE_BACK_COLOR,
                HILITED_KEY_BORDER_COLOR to KEY_BORDER_COLOR,
                HILITED_KEY_TEXT_COLOR to HILITED_CANDIDATE_TEXT_COLOR,
                HILITED_KEY_SYMBOL_COLOR to HILITED_COMMENT_TEXT_COLOR,
                HILITED_OFF_KEY_BACK_COLOR to HILITED_KEY_BACK_COLOR,
                HILITED_ON_KEY_BACK_COLOR to HILITED_KEY_BACK_COLOR,
                HILITED_OFF_KEY_BORDER_COLOR to HILITED_KEY_BORDER_COLOR,
                HILITED_ON_KEY_BORDER_COLOR to HILITED_KEY_BORDER_COLOR,
                HILITED_OFF_KEY_TEXT_COLOR to HILITED_KEY_TEXT_COLOR,
                HILITED_ON_KEY_TEXT_COLOR to HILITED_KEY_TEXT_COLOR,
                HILITED_OFF_KEY_SYMBOL_COLOR to HILITED_KEY_SYMBOL_COLOR,
                HILITED_ON_KEY_SYMBOL_COLOR to HILITED_KEY_SYMBOL_COLOR,
                KEY_BACK_COLOR to BACK_COLOR,
                KEY_BORDER_COLOR to BORDER_COLOR,
                KEY_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                KEY_SYMBOL_COLOR to COMMENT_TEXT_COLOR,
                LABEL_COLOR to CANDIDATE_TEXT_COLOR,
                OFF_KEY_BACK_COLOR to KEY_BACK_COLOR,
                OFF_KEY_BORDER_COLOR to KEY_BORDER_COLOR,
                OFF_KEY_TEXT_COLOR to KEY_TEXT_COLOR,
                OFF_KEY_SYMBOL_COLOR to KEY_SYMBOL_COLOR,
                ON_KEY_BACK_COLOR to HILITED_KEY_BACK_COLOR,
                ON_KEY_BORDER_COLOR to HILITED_KEY_BORDER_COLOR,
                ON_KEY_TEXT_COLOR to HILITED_KEY_TEXT_COLOR,
                ON_KEY_SYMBOL_COLOR to HILITED_KEY_SYMBOL_COLOR,
                POPUP_BACK_COLOR to KEY_BACK_COLOR,
                POPUP_TEXT_COLOR to KEY_TEXT_COLOR,
                HILITED_POPUP_BACK_COLOR to HILITED_KEY_BACK_COLOR,
                HILITED_POPUP_TEXT_COLOR to HILITED_KEY_TEXT_COLOR,
                SHADOW_COLOR to BORDER_COLOR,
                ROOT_BACKGROUND to BACK_COLOR,
                CANDIDATE_BACKGROUND to BACK_COLOR,
                KEYBOARD_BACK_COLOR to BORDER_COLOR,
                KEYBOARD_BACKGROUND to KEYBOARD_BACK_COLOR,
                LIQUID_KEYBOARD_BACKGROUND to KEYBOARD_BACK_COLOR,
                TEXT_BACK_COLOR to BACK_COLOR,
                LONG_TEXT_COLOR to KEY_TEXT_COLOR,
                LONG_TEXT_BACK_COLOR to KEY_BACK_COLOR,
            )

        /** The fallback hop for a key, or null when the key is a chain terminal. */
        fun fallbackOf(key: ColorKey): ColorKey? = builtinFallbackColors[key]
    }
}
