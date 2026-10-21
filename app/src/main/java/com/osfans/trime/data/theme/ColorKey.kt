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
    KEYBOARD_NORMAL_BACK_COLOR,
    KEYBOARD_NORMAL_BORDER_COLOR,
    CANDIDATE_NORMAL_BACK_COLOR,
    CANDIDATE_NORMAL_BORDER_COLOR,
    CANDIDATE_NORMAL_SEPARATOR_COLOR,
    CANDIDATE_NORMAL_TEXT_COLOR,
    COMMENT_NORMAL_TEXT_COLOR,
    PREEDIT_SELECTED_BACK_COLOR,
    CANDIDATE_SELECTED_BACK_COLOR,
    CANDIDATE_BUTTON_NORMAL_BACK_COLOR,
    CANDIDATE_BUTTON_PRESSED_BACK_COLOR,
    CANDIDATE_BUTTON_NORMAL_TEXT_COLOR,
    CANDIDATE_BUTTON_PRESSED_TEXT_COLOR,
    CANDIDATE_SELECTED_TEXT_COLOR,
    COMMENT_SELECTED_TEXT_COLOR,
    KEY_PRESSED_BACK_COLOR,
    KEY_PRESSED_BORDER_COLOR,
    KEY_PRESSED_TEXT_COLOR,
    KEY_PRESSED_SYMBOL_COLOR,
    LABEL_SELECTED_TEXT_COLOR,
    FUNCTION_KEY_PRESSED_BACK_COLOR,
    FUNCTION_KEY_PRESSED_BORDER_COLOR,
    FUNCTION_KEY_PRESSED_TEXT_COLOR,
    FUNCTION_KEY_PRESSED_SYMBOL_COLOR,
    FUNCTION_KEY_ON_PRESSED_BACK_COLOR,
    FUNCTION_KEY_ON_PRESSED_BORDER_COLOR,
    FUNCTION_KEY_ON_PRESSED_TEXT_COLOR,
    FUNCTION_KEY_ON_PRESSED_SYMBOL_COLOR,
    POPUP_SELECTED_BACK_COLOR,
    POPUP_SELECTED_TEXT_COLOR,
    PREEDIT_SELECTED_TEXT_COLOR,
    KEY_NORMAL_BACK_COLOR,
    PREVIEW_NORMAL_BACK_COLOR,
    PREVIEW_NORMAL_TEXT_COLOR,
    KEY_NORMAL_BORDER_COLOR,
    KEY_NORMAL_TEXT_COLOR,
    KEY_NORMAL_SYMBOL_COLOR,
    LABEL_NORMAL_TEXT_COLOR,
    LIQUID_KEYBOARD_NORMAL_BACK_COLOR,
    LONG_TEXT_NORMAL_BACK_COLOR,
    LONG_TEXT_NORMAL_TEXT_COLOR,
    FUNCTION_KEY_NORMAL_BACK_COLOR,
    FUNCTION_KEY_NORMAL_BORDER_COLOR,
    FUNCTION_KEY_NORMAL_TEXT_COLOR,
    FUNCTION_KEY_NORMAL_SYMBOL_COLOR,
    FUNCTION_KEY_ON_BACK_COLOR,
    FUNCTION_KEY_ON_BORDER_COLOR,
    FUNCTION_KEY_ON_TEXT_COLOR,
    FUNCTION_KEY_ON_SYMBOL_COLOR,
    POPUP_NORMAL_BACK_COLOR,
    POPUP_NORMAL_TEXT_COLOR,
    ROOT_NORMAL_BACK_COLOR,
    KEY_NORMAL_SHADOW_COLOR,
    PREEDIT_NORMAL_BACK_COLOR,
    PREEDIT_NORMAL_TEXT_COLOR,
    ;

    /** The color key as it appears in theme YAML files. */
    val key: String = name.lowercase()

    companion object {
        private val byKey = entries.associateBy { it.key }

        /**
         * The typed [ColorKey] for a YAML color key, or null for keys that
         * only a theme defines.
         */
        fun from(key: String): ColorKey? = byKey[key] ?: LegacyColorAliases.resolve(key)?.let(byKey::get)

        /**
         * Built-in fallback chains previously hardcoded in ColorManager:
         * when a scheme does not define a key, its chain entry points at the
         * key to try next. The scheme itself is always consulted first.
         */
        internal val builtinFallbackColors: Map<ColorKey, ColorKey> =
            mapOf(
                CANDIDATE_NORMAL_TEXT_COLOR to PREEDIT_NORMAL_TEXT_COLOR,
                COMMENT_NORMAL_TEXT_COLOR to CANDIDATE_NORMAL_TEXT_COLOR,
                KEYBOARD_NORMAL_BORDER_COLOR to KEYBOARD_NORMAL_BACK_COLOR,
                CANDIDATE_NORMAL_SEPARATOR_COLOR to KEYBOARD_NORMAL_BORDER_COLOR,
                PREEDIT_SELECTED_TEXT_COLOR to PREEDIT_NORMAL_TEXT_COLOR,
                PREEDIT_SELECTED_BACK_COLOR to KEYBOARD_NORMAL_BACK_COLOR,
                CANDIDATE_SELECTED_TEXT_COLOR to PREEDIT_SELECTED_TEXT_COLOR,
                CANDIDATE_SELECTED_BACK_COLOR to PREEDIT_SELECTED_BACK_COLOR,
                CANDIDATE_BUTTON_NORMAL_BACK_COLOR to CANDIDATE_NORMAL_BACK_COLOR,
                CANDIDATE_BUTTON_PRESSED_BACK_COLOR to CANDIDATE_SELECTED_BACK_COLOR,
                CANDIDATE_BUTTON_NORMAL_TEXT_COLOR to CANDIDATE_NORMAL_TEXT_COLOR,
                CANDIDATE_BUTTON_PRESSED_TEXT_COLOR to CANDIDATE_SELECTED_TEXT_COLOR,
                LABEL_SELECTED_TEXT_COLOR to CANDIDATE_SELECTED_TEXT_COLOR,
                COMMENT_SELECTED_TEXT_COLOR to COMMENT_NORMAL_TEXT_COLOR,
                KEY_PRESSED_BACK_COLOR to CANDIDATE_SELECTED_BACK_COLOR,
                KEY_PRESSED_BORDER_COLOR to KEY_NORMAL_BORDER_COLOR,
                KEY_PRESSED_TEXT_COLOR to CANDIDATE_SELECTED_TEXT_COLOR,
                KEY_PRESSED_SYMBOL_COLOR to COMMENT_SELECTED_TEXT_COLOR,
                FUNCTION_KEY_PRESSED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                FUNCTION_KEY_ON_PRESSED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                FUNCTION_KEY_PRESSED_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                FUNCTION_KEY_ON_PRESSED_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                FUNCTION_KEY_PRESSED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                FUNCTION_KEY_ON_PRESSED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                FUNCTION_KEY_PRESSED_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                FUNCTION_KEY_ON_PRESSED_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                KEY_NORMAL_BACK_COLOR to KEYBOARD_NORMAL_BACK_COLOR,
                KEY_NORMAL_BORDER_COLOR to KEYBOARD_NORMAL_BORDER_COLOR,
                KEY_NORMAL_TEXT_COLOR to CANDIDATE_NORMAL_TEXT_COLOR,
                KEY_NORMAL_SYMBOL_COLOR to COMMENT_NORMAL_TEXT_COLOR,
                LABEL_NORMAL_TEXT_COLOR to CANDIDATE_NORMAL_TEXT_COLOR,
                FUNCTION_KEY_NORMAL_BACK_COLOR to KEY_NORMAL_BACK_COLOR,
                FUNCTION_KEY_NORMAL_BORDER_COLOR to KEY_NORMAL_BORDER_COLOR,
                FUNCTION_KEY_NORMAL_TEXT_COLOR to KEY_NORMAL_TEXT_COLOR,
                FUNCTION_KEY_NORMAL_SYMBOL_COLOR to KEY_NORMAL_SYMBOL_COLOR,
                FUNCTION_KEY_ON_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                FUNCTION_KEY_ON_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                FUNCTION_KEY_ON_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                FUNCTION_KEY_ON_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                POPUP_NORMAL_BACK_COLOR to KEY_NORMAL_BACK_COLOR,
                POPUP_NORMAL_TEXT_COLOR to KEY_NORMAL_TEXT_COLOR,
                POPUP_SELECTED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                POPUP_SELECTED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                KEY_NORMAL_SHADOW_COLOR to KEYBOARD_NORMAL_BORDER_COLOR,
                ROOT_NORMAL_BACK_COLOR to KEYBOARD_NORMAL_BACK_COLOR,
                CANDIDATE_NORMAL_BACK_COLOR to KEYBOARD_NORMAL_BACK_COLOR,
                LIQUID_KEYBOARD_NORMAL_BACK_COLOR to KEYBOARD_NORMAL_BACK_COLOR,
                PREEDIT_NORMAL_BACK_COLOR to KEYBOARD_NORMAL_BACK_COLOR,
                LONG_TEXT_NORMAL_TEXT_COLOR to KEY_NORMAL_TEXT_COLOR,
                LONG_TEXT_NORMAL_BACK_COLOR to KEY_NORMAL_BACK_COLOR,
            )

        /** The fallback hop for a key, or null when the key is a chain terminal. */
        fun fallbackOf(key: ColorKey): ColorKey? = builtinFallbackColors[key]
    }
}

/**
 * The pre-S2 color keys of doc/Theme.md, so a theme written before the
 * `<role>_<state>_<part>_color` rename still resolves.
 */
object LegacyColorAliases {
    private val aliases: Map<String, String> =
        mapOf(
            "back_color" to "keyboard_normal_back_color",
            "keyboard_back_color" to "keyboard_normal_back_color",
            "keyboard_background" to "keyboard_normal_back_color",
            "root_background" to "root_normal_back_color",
            "border_color" to "keyboard_normal_border_color",
            "key_back_color" to "key_normal_back_color",
            "key_border_color" to "key_normal_border_color",
            "key_text_color" to "key_normal_text_color",
            "key_symbol_color" to "key_normal_symbol_color",
            "hilited_key_back_color" to "key_pressed_back_color",
            "hilited_key_border_color" to "key_pressed_border_color",
            "hilited_key_text_color" to "key_pressed_text_color",
            "hilited_key_symbol_color" to "key_pressed_symbol_color",
            "off_key_back_color" to "function_key_normal_back_color",
            "off_key_border_color" to "function_key_normal_border_color",
            "off_key_text_color" to "function_key_normal_text_color",
            "off_key_symbol_color" to "function_key_normal_symbol_color",
            "on_key_back_color" to "function_key_on_back_color",
            "on_key_border_color" to "function_key_on_border_color",
            "on_key_text_color" to "function_key_on_text_color",
            "on_key_symbol_color" to "function_key_on_symbol_color",
            "hilited_off_key_back_color" to "function_key_pressed_back_color",
            "hilited_off_key_border_color" to "function_key_pressed_border_color",
            "hilited_off_key_text_color" to "function_key_pressed_text_color",
            "hilited_off_key_symbol_color" to "function_key_pressed_symbol_color",
            "hilited_on_key_back_color" to "function_key_on_pressed_back_color",
            "hilited_on_key_border_color" to "function_key_on_pressed_border_color",
            "hilited_on_key_text_color" to "function_key_on_pressed_text_color",
            "hilited_on_key_symbol_color" to "function_key_on_pressed_symbol_color",
            "candidate_background" to "candidate_normal_back_color",
            "candidate_text_color" to "candidate_normal_text_color",
            "candidate_border_color" to "candidate_normal_border_color",
            "candidate_separator_color" to "candidate_normal_separator_color",
            "hilited_candidate_back_color" to "candidate_selected_back_color",
            "hilited_candidate_text_color" to "candidate_selected_text_color",
            "hilited_candidate_button_color" to "candidate_button_pressed_back_color",
            "candidate_button_selected_back_color" to "candidate_button_pressed_back_color",
            "hilited_back_color" to "preedit_selected_back_color",
            "hilited_text_color" to "preedit_selected_text_color",
            "comment_text_color" to "comment_normal_text_color",
            "hilited_comment_text_color" to "comment_selected_text_color",
            "popup_back_color" to "popup_normal_back_color",
            "popup_text_color" to "popup_normal_text_color",
            "hilited_popup_back_color" to "popup_selected_back_color",
            "hilited_popup_text_color" to "popup_selected_text_color",
            "label_color" to "label_normal_text_color",
            "hilited_label_color" to "label_selected_text_color",
            "text_color" to "preedit_normal_text_color",
            "text_back_color" to "preedit_normal_back_color",
            "text_normal_back_color" to "preedit_normal_back_color",
            "text_normal_text_color" to "preedit_normal_text_color",
            "text_selected_back_color" to "preedit_selected_back_color",
            "text_selected_text_color" to "preedit_selected_text_color",
            "long_text_color" to "long_text_normal_text_color",
            "long_text_back_color" to "long_text_normal_back_color",
            "shadow_color" to "key_normal_shadow_color",
            "panel_normal_back_color" to "liquid_keyboard_normal_back_color",
            "liquid_keyboard_background" to "liquid_keyboard_normal_back_color",
            "preview_back_color" to "preview_normal_back_color",
            "preview_text_color" to "preview_normal_text_color",
        )

    /** The S2 key for a legacy color key, or null when [name] is not a legacy key. */
    fun resolve(name: String): String? = aliases[name]

    /**
     * All legacy spellings that map to [canonical], so a theme written against
     * the old `doc/Theme.md` keys still feeds the canonical table slot.
     */
    fun aliasesOf(canonical: String): List<String> = aliases.entries.filter { it.value == canonical }.map { it.key }
}
