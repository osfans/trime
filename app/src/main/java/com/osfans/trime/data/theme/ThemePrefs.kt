/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.osfans.trime.R
import com.osfans.trime.data.prefs.PreferenceDelegateEnum
import com.osfans.trime.data.prefs.PreferenceDelegateOwner

/**
 * Theme configuration: theme selection, color schemes, font/type-size
 * overrides, keyboard appearance, and key sound & vibration. The keyboard
 * appearance and sound/vibration settings were previously scattered under the
 * "virtual keyboard" preferences and are consolidated here.
 */
class ThemePrefs(
    sharedPrefs: SharedPreferences,
) : PreferenceDelegateOwner(sharedPrefs, R.string.theme) {
    // === 主题选择与配色 ===

    val selectedTheme =
        string(
            R.string.selected_theme,
            SELECTED_THEME,
            "trime",
            R.string.selected_theme_summary,
        )

    val normalModeColor =
        string(
            R.string.normal_mode_color,
            NORMAL_MODE_COLOR,
            "light",
            R.string.normal_mode_color_summary,
        )

    val followSystemDayNight =
        switch(
            R.string.follow_system_day_night_color,
            FOLLOW_SYSTEM_DAY_NIGHT,
            false,
        )

    enum class NavbarBackground(
        override val stringRes: Int,
    ) : PreferenceDelegateEnum {
        NONE(R.string.navbar_bkg_none),
        COLOR_ONLY(R.string.navbar_bkg_color_only),
        FULL(R.string.navbar_bkg_full),
    }

    val navbarBackground =
        enum(
            R.string.navbar_background,
            NAVBAR_BACKGROUND,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                NavbarBackground.FULL
            } else {
                NavbarBackground.COLOR_ONLY
            },
            enableUiOn = { Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM },
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                sharedPreferences.edit {
                    remove(this@apply.key)
                }
            }
        }

    // === 字体与字号（覆盖主题值，0 / 空 = 跟随主题） ===

    val keyTextSize =
        int(
            R.string.key_text_size,
            KEY_TEXT_SIZE,
            0,
            0,
            100,
            "sp",
            defaultLabel = R.string.follow_theme,
            useMinAsDefault = true,
        )

    val candidateTextSize =
        int(
            R.string.candidate_text_size,
            CANDIDATE_TEXT_SIZE,
            0,
            0,
            100,
            "sp",
            defaultLabel = R.string.follow_theme,
            useMinAsDefault = true,
        )

    val commentTextSize =
        int(
            R.string.comment_text_size,
            COMMENT_TEXT_SIZE,
            0,
            0,
            100,
            "sp",
            defaultLabel = R.string.follow_theme,
            useMinAsDefault = true,
        )

    val keyFont = editText(R.string.key_font, KEY_FONT, "")
    val candidateFont = editText(R.string.candidate_font, CANDIDATE_FONT, "")
    val commentFont = editText(R.string.comment_font, COMMENT_FONT, "")

    // === 键盘外观（从虚拟键盘移入） ===

    enum class LandscapeMode(override val stringRes: Int) : PreferenceDelegateEnum {
        NEVER(R.string.never),
        LANDSCAPE(R.string.landscape_only),
        WIDE(R.string.wide_or_landscape),
        ALWAYS(R.string.always),
    }

    val landscapeMode = enum(R.string.enable_landscape_mode, LANDSCAPE_MODE, LandscapeMode.NEVER)

    val splitSpacePercent =
        int(
            R.string.split_space_percent,
            SPLIT_SPACE_PERCENT,
            100,
            0,
            200,
            "%",
        )

    val keyboardHeightRatio =
        int(
            R.string.keyboard_height_ratio,
            KEYBOARD_HEIGHT_RATIO,
            0,
            0,
            100,
            "%",
            defaultLabel = R.string.follow_theme,
            useMinAsDefault = true,
        )

    val useSoftCursor = switch(R.string.use_soft_cursor, USE_SOFT_CURSOR, true)

    val hideInputBar = switch(R.string.hide_input_bar, HIDE_INPUT_BAR, false)
    val hideKeySymbol = switch(R.string.hide_key_symbol, HIDE_KEY_SYMBOL, false)
    val hideKeyHint = switch(R.string.hide_key_hint, HIDE_KEY_HINT, false)

    val popupOnKeyPress = switch(R.string.popup_on_key_press, POPUP_ON_KEY_PRESS, false)
    val expandKeypressArea = switch(R.string.expand_keypress_area_to_edge, EXPAND_KEYPRESS_AREA, false)

    // === 按键音 ===

    val soundOnKeyPress = switch(R.string.sound_on_keypress, SOUND_ON_KEYPRESS, false)

    val soundVolume =
        int(
            R.string.sound_volume,
            KEY_SOUND_VOLUME,
            10,
            0,
            100,
            "%",
            defaultLabel = R.string.system_default,
        ) { soundOnKeyPress.getValue() }

    val useCustomSoundEffect =
        switch(
            R.string.custom_sound_effect_enabled,
            USE_CUSTOM_SOUND_EFFECT,
            false,
        ) { soundOnKeyPress.getValue() }

    val customSoundEffect =
        string(
            R.string.custom_sound_effect_name,
            CUSTOM_SOUND_EFFECT,
            "",
        ) { soundOnKeyPress.getValue() && useCustomSoundEffect.getValue() }

    // === 震动 ===

    val vibrateOnKeyPress = switch(R.string.vibrate_on_key_press, VIBRATE_ON_KEY_PRESS, false)

    val vibrateOnKeyRelease =
        switch(
            R.string.vibrate_on_key_release,
            VIBRATE_ON_KEY_RELEASE,
            false,
        ) { vibrateOnKeyPress.getValue() }

    val vibrateOnKeyRepeat =
        switch(
            R.string.vibrate_on_key_repeat,
            VIBRATE_ON_KEY_REPEAT,
            false,
        ) { vibrateOnKeyPress.getValue() }

    val vibrationDuration =
        int(
            R.string.vibration_duration,
            VIBRATION_DURATION,
            0,
            0,
            100,
            "ms",
            defaultLabel = R.string.system_default,
        ) { vibrateOnKeyPress.getValue() }

    val vibrationAmplitude =
        int(
            R.string.vibration_amplitude,
            VIBRATION_AMPLITUDE,
            0,
            0,
            255,
            defaultLabel = R.string.system_default,
        ) { vibrateOnKeyPress.getValue() }

    companion object {
        const val SELECTED_THEME = "selected_theme"
        const val NORMAL_MODE_COLOR = "normal_mode_color"
        const val FOLLOW_SYSTEM_DAY_NIGHT = "follow_system_day_night"
        const val NAVBAR_BACKGROUND = "navbar_background"

        const val KEY_TEXT_SIZE = "theme_key_text_size"
        const val CANDIDATE_TEXT_SIZE = "theme_candidate_text_size"
        const val COMMENT_TEXT_SIZE = "theme_comment_text_size"
        const val KEY_FONT = "theme_key_font"
        const val CANDIDATE_FONT = "theme_candidate_font"
        const val COMMENT_FONT = "theme_comment_font"

        const val LANDSCAPE_MODE = "keyboard_landscape_mode"
        const val SPLIT_SPACE_PERCENT = "keyboard_split_space"
        const val KEYBOARD_HEIGHT_RATIO = "keyboard_height_ratio"

        const val USE_SOFT_CURSOR = "use_soft_cursor"
        const val HIDE_INPUT_BAR = "hide_input_bar"
        const val HIDE_KEY_SYMBOL = "hide_key_symbol"
        const val HIDE_KEY_HINT = "hide_key_hint"

        const val SOUND_ON_KEYPRESS = "sound_on_keypress"
        const val KEY_SOUND_VOLUME = "sound_volume"
        const val USE_CUSTOM_SOUND_EFFECT = "custom_sound_effect_enabled"
        const val CUSTOM_SOUND_EFFECT = "custom_sound_effect_name"
        const val VIBRATE_ON_KEY_PRESS = "vibrate_on_key_press"
        const val VIBRATE_ON_KEY_RELEASE = "vibrate_on_key_release"
        const val VIBRATE_ON_KEY_REPEAT = "vibrate_on_key_repeat"
        const val VIBRATION_DURATION = "vibration_duration"
        const val VIBRATION_AMPLITUDE = "vibration_amplitude"

        const val POPUP_ON_KEY_PRESS = "show_key_popup"
        const val EXPAND_KEYPRESS_AREA = "expand_keypress_area"
    }
}
