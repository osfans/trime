/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * Canonical theme color keys. The YAML spelling is the lowercased enum name.
 *
 * Keys name a role, an optional state and a part: `<role>_[state_]<part>_color`.
 * The default state is not spelled out, so `key_back_color` is the normal state
 * and `key_pressed_back_color` is the pressed one.
 *
 * The enum covers every key the built-in fallback chains can hop through and
 * every key the runtime requests; theme-defined keys that do not appear here
 * are resolved through [com.osfans.trime.data.theme.ColorTable]'s string path.
 */
enum class ColorKey {
    KEYBOARD_BACK_COLOR,
    BORDER_BACK_COLOR,
    CANDIDATE_BACK_COLOR,
    CANDIDATE_BORDER_COLOR,
    CANDIDATE_SEPARATOR_COLOR,
    CANDIDATE_TEXT_COLOR,
    CANDIDATE_SELECTED_BACK_COLOR,
    CANDIDATE_SELECTED_TEXT_COLOR,
    COMMENT_TEXT_COLOR,
    COMMENT_SELECTED_TEXT_COLOR,
    PREEDIT_BACK_COLOR,
    PREEDIT_TEXT_COLOR,
    PREEDIT_SELECTED_BACK_COLOR,
    PREEDIT_SELECTED_TEXT_COLOR,
    KEY_BACK_COLOR,
    KEY_BORDER_COLOR,
    KEY_TEXT_COLOR,
    KEY_SYMBOL_COLOR,
    KEY_PRESSED_BACK_COLOR,
    KEY_PRESSED_BORDER_COLOR,
    KEY_PRESSED_TEXT_COLOR,
    KEY_PRESSED_SYMBOL_COLOR,
    KEY_SHADOW_COLOR,
    FUNCTION_KEY_BACK_COLOR,
    FUNCTION_KEY_BORDER_COLOR,
    FUNCTION_KEY_TEXT_COLOR,
    FUNCTION_KEY_SYMBOL_COLOR,
    FUNCTION_KEY_PRESSED_BACK_COLOR,
    FUNCTION_KEY_PRESSED_BORDER_COLOR,
    FUNCTION_KEY_PRESSED_TEXT_COLOR,
    FUNCTION_KEY_PRESSED_SYMBOL_COLOR,
    FUNCTION_KEY_ON_BACK_COLOR,
    FUNCTION_KEY_ON_BORDER_COLOR,
    FUNCTION_KEY_ON_TEXT_COLOR,
    FUNCTION_KEY_ON_SYMBOL_COLOR,
    FUNCTION_KEY_ON_PRESSED_BACK_COLOR,
    FUNCTION_KEY_ON_PRESSED_BORDER_COLOR,
    FUNCTION_KEY_ON_PRESSED_TEXT_COLOR,
    FUNCTION_KEY_ON_PRESSED_SYMBOL_COLOR,
    POPUP_BACK_COLOR,
    POPUP_TEXT_COLOR,
    POPUP_SELECTED_BACK_COLOR,
    POPUP_SELECTED_TEXT_COLOR,
    LABEL_COLOR,
    LABEL_SELECTED_TEXT_COLOR,
    CANDIDATE_BUTTON_BACK_COLOR,
    CANDIDATE_BUTTON_TEXT_COLOR,
    CANDIDATE_BUTTON_SELECTED_BACK_COLOR,
    CANDIDATE_BUTTON_SELECTED_TEXT_COLOR,
    LIQUID_KEYBOARD_BACK_COLOR,
    ROOT_BACK_COLOR,
    LONG_TEXT_COLOR,
    LONG_TEXT_BACK_COLOR,
    PREVIEW_BACK_COLOR,
    PREVIEW_TEXT_COLOR,
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
                CANDIDATE_TEXT_COLOR to PREEDIT_TEXT_COLOR,
                COMMENT_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                BORDER_BACK_COLOR to KEYBOARD_BACK_COLOR,
                CANDIDATE_SEPARATOR_COLOR to BORDER_BACK_COLOR,
                PREEDIT_SELECTED_TEXT_COLOR to PREEDIT_TEXT_COLOR,
                PREEDIT_SELECTED_BACK_COLOR to KEYBOARD_BACK_COLOR,
                CANDIDATE_SELECTED_TEXT_COLOR to PREEDIT_SELECTED_TEXT_COLOR,
                CANDIDATE_SELECTED_BACK_COLOR to PREEDIT_SELECTED_BACK_COLOR,
                CANDIDATE_BUTTON_BACK_COLOR to CANDIDATE_BACK_COLOR,
                CANDIDATE_BUTTON_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                CANDIDATE_BUTTON_SELECTED_BACK_COLOR to CANDIDATE_SELECTED_BACK_COLOR,
                CANDIDATE_BUTTON_SELECTED_TEXT_COLOR to CANDIDATE_SELECTED_TEXT_COLOR,
                LABEL_SELECTED_TEXT_COLOR to CANDIDATE_SELECTED_TEXT_COLOR,
                COMMENT_SELECTED_TEXT_COLOR to COMMENT_TEXT_COLOR,
                KEY_PRESSED_BACK_COLOR to CANDIDATE_SELECTED_BACK_COLOR,
                KEY_PRESSED_BORDER_COLOR to KEY_BORDER_COLOR,
                KEY_PRESSED_TEXT_COLOR to CANDIDATE_SELECTED_TEXT_COLOR,
                KEY_PRESSED_SYMBOL_COLOR to COMMENT_SELECTED_TEXT_COLOR,
                FUNCTION_KEY_BACK_COLOR to KEY_BACK_COLOR,
                FUNCTION_KEY_BORDER_COLOR to KEY_BORDER_COLOR,
                FUNCTION_KEY_TEXT_COLOR to KEY_TEXT_COLOR,
                FUNCTION_KEY_SYMBOL_COLOR to KEY_SYMBOL_COLOR,
                FUNCTION_KEY_PRESSED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                FUNCTION_KEY_PRESSED_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                FUNCTION_KEY_PRESSED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                FUNCTION_KEY_PRESSED_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                FUNCTION_KEY_ON_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                FUNCTION_KEY_ON_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                FUNCTION_KEY_ON_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                FUNCTION_KEY_ON_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                FUNCTION_KEY_ON_PRESSED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                FUNCTION_KEY_ON_PRESSED_BORDER_COLOR to KEY_PRESSED_BORDER_COLOR,
                FUNCTION_KEY_ON_PRESSED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                FUNCTION_KEY_ON_PRESSED_SYMBOL_COLOR to KEY_PRESSED_SYMBOL_COLOR,
                KEY_BACK_COLOR to CANDIDATE_BACK_COLOR,
                KEY_BORDER_COLOR to BORDER_BACK_COLOR,
                KEY_TEXT_COLOR to CANDIDATE_TEXT_COLOR,
                KEY_SYMBOL_COLOR to COMMENT_TEXT_COLOR,
                KEY_SHADOW_COLOR to BORDER_BACK_COLOR,
                LABEL_COLOR to CANDIDATE_TEXT_COLOR,
                POPUP_BACK_COLOR to KEY_BACK_COLOR,
                POPUP_TEXT_COLOR to KEY_TEXT_COLOR,
                POPUP_SELECTED_BACK_COLOR to KEY_PRESSED_BACK_COLOR,
                POPUP_SELECTED_TEXT_COLOR to KEY_PRESSED_TEXT_COLOR,
                ROOT_BACK_COLOR to KEYBOARD_BACK_COLOR,
                CANDIDATE_BACK_COLOR to KEYBOARD_BACK_COLOR,
                LIQUID_KEYBOARD_BACK_COLOR to KEYBOARD_BACK_COLOR,
                PREEDIT_BACK_COLOR to KEYBOARD_BACK_COLOR,
                LONG_TEXT_COLOR to KEY_TEXT_COLOR,
                LONG_TEXT_BACK_COLOR to KEY_BACK_COLOR,
            )

        /** The fallback hop for a key, or null when the key is a chain terminal. */
        fun fallbackOf(key: ColorKey): ColorKey? = builtinFallbackColors[key]
    }
}

/**
 * The pre-unification color keys of doc/Theme.md, so a theme written before the
 * `<role>_[state_]<part>_color` rename still resolves. The `hilited_*` family is
 * not carried over: those spellings are replaced by the canonical state prefix.
 */
object LegacyColorAliases {
    private val aliases: Map<String, String> =
        mapOf(
            "back_color" to "candidate_back_color",
            "keyboard_background" to "keyboard_back_color",
            "root_background" to "root_back_color",
            "border_color" to "border_back_color",
            "off_key_back_color" to "function_key_back_color",
            "off_key_border_color" to "function_key_border_color",
            "off_key_text_color" to "function_key_text_color",
            "off_key_symbol_color" to "function_key_symbol_color",
            "on_key_back_color" to "function_key_on_back_color",
            "on_key_border_color" to "function_key_on_border_color",
            "on_key_text_color" to "function_key_on_text_color",
            "on_key_symbol_color" to "function_key_on_symbol_color",
            "candidate_background" to "candidate_back_color",
            "candidate_button_normal_back_color" to "candidate_button_back_color",
            "candidate_button_normal_text_color" to "candidate_button_text_color",
            "text_color" to "preedit_text_color",
            "text_back_color" to "preedit_back_color",
            "text_normal_back_color" to "preedit_back_color",
            "text_normal_text_color" to "preedit_text_color",
            "text_selected_back_color" to "preedit_selected_back_color",
            "text_selected_text_color" to "preedit_selected_text_color",
            "shadow_color" to "key_shadow_color",
            "panel_back_color" to "liquid_keyboard_back_color",
            "panel_normal_back_color" to "liquid_keyboard_back_color",
            "liquid_keyboard_background" to "liquid_keyboard_back_color",
        )

    /** The canonical key for a legacy color key, or null when [name] is not a legacy key. */
    fun resolve(name: String): String? = aliases[name]

    /**
     * All legacy spellings that map to [canonical], so a theme written against
     * the old `doc/Theme.md` keys still feeds the canonical table slot.
     */
    fun aliasesOf(canonical: String): List<String> = aliases.entries.filter { it.value == canonical }.map { it.key }
}
