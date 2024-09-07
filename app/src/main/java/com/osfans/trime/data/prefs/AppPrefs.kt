// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ime.enums.FullscreenMode
import com.osfans.trime.ime.enums.InlinePreeditMode
import com.osfans.trime.util.appContext
import java.lang.ref.WeakReference
import java.util.Calendar

/**
 * Helper class for an organized access to the shared preferences.
 */
class AppPrefs(
    private val shared: SharedPreferences,
) {
    private val applicationContext: WeakReference<Context> = WeakReference(appContext)

    val internal = Internal(shared)
    val keyboard = Keyboard(shared)
    val theme = Theme(shared)
    val profile = Profile(shared)
    val clipboard = Clipboard(shared)
    val other = Other(shared)

    companion object {
        private var defaultInstance: AppPrefs? = null

        fun initDefault(sharedPreferences: SharedPreferences): AppPrefs {
            val instance = AppPrefs(sharedPreferences)
            defaultInstance = instance
            return instance
        }

        fun defaultInstance(): AppPrefs =
            defaultInstance
                ?: throw UninitializedPropertyAccessException(
                    """
                    Default preferences not initialized! Make sure to call initDefault()
                    before accessing the default preferences.
                    """.trimIndent(),
                )
    }

    /**
     * Tells the [PreferenceManager] to set the defined preferences to their default values, if
     * they have not been initialized yet.
     */
    fun initDefaultPreferences() {
        try {
            applicationContext.get()?.let { context ->
                PreferenceManager.setDefaultValues(context, R.xml.keyboard_preference, true)
                PreferenceManager.setDefaultValues(context, R.xml.theme_color_preference, true)
                PreferenceManager.setDefaultValues(context, R.xml.profile_preference, true)
                PreferenceManager.setDefaultValues(context, R.xml.other_preference, true)
            }
        } catch (e: Exception) {
            e.fillInStackTrace()
        }
    }

    class Internal(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val PID = "general__pid"
        }

        var pid by int(PID, 0)
    }

    /**
     *  Wrapper class of keyboard settings.
     */
    class Keyboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val INLINE_PREEDIT_MODE = "keyboard__inline_preedit"
            const val SOFT_CURSOR_ENABLED = "keyboard__soft_cursor"
            const val FLOATING_WINDOW_ENABLED = "keyboard__show_window"
            const val POPUP_KEY_PRESS_ENABLED = "keyboard__show_key_popup"
            const val SWITCHES_ENABLED = "keyboard__show_switches"
            const val LANDSCAPE_MODE = "keyboard__landscape_mode"
            const val SPLIT_SPACE_PERCENT = "keyboard__split_space"
            const val SWITCH_ARROW_ENABLED = "keyboard__show_switch_arrow"
            const val FULLSCREEN_MODE = "keyboard__fullscreen_mode"

            const val HOOK_CTRL_A = "keyboard__hook_ctrl_a"
            const val HOOK_CTRL_CV = "keyboard__hook_ctrl_cv"
            const val HOOK_CTRL_LR = "keyboard__hook_ctrl_lr"
            const val HOOK_CTRL_ZY = "keyboard__hook_ctrl_zy"
            const val HOOK_SHIFT_SPACE = "keyboard__hook_shift_space"
            const val HOOK_SHIFT_NUM = "keyboard__hook_shift_num"
            const val HOOK_SHIFT_SYMBOL = "keyboard__hook_shift_symbol"

            const val SOUND_ENABLED = "keyboard__key_sound"
            const val SOUND_VOLUME = "keyboard__key_sound_volume"
            const val CUSTOM_SOUND_ENABLED = "keyboard__custom_key_sound"
            const val CUSTOM_SOUND_PACKAGE = "keyboard__key_sound_package"

            const val VIBRATION_ENABLED = "keyboard__key_vibration"
            const val VIBRATION_DURATION = "keyboard__key_vibration_duration"
            const val VIBRATION_AMPLITUDE = "keyboard__key_vibration_amplitude"

            const val SPEAK_KEY_PRESS_ENABLED = "keyboard__speak_key_press"
            const val SPEAK_COMMIT_ENABLED = "keyboard__speak_commit"

            const val SWIPE_ENABLED = "keyboard__swipe_enabled"
            const val SWIPE_TRAVEL = "keyboard__key_swipe_travel"
            const val SWIPE_VELOCITY = "keyboard__key_swipe_velocity"
            const val LONG_PRESS_TIMEOUT = "keyboard__key_long_press_timeout"
            const val REPEAT_INTERVAL = "keyboard__key_repeat_interval"
        }

        var inlinePreedit by enum(INLINE_PREEDIT_MODE, InlinePreeditMode.PREVIEW)
        var fullscreenMode by enum(FULLSCREEN_MODE, FullscreenMode.AUTO_SHOW)
        val softCursorEnabled by bool(SOFT_CURSOR_ENABLED, true)
        val popupWindowEnabled by bool(FLOATING_WINDOW_ENABLED, true)
        val popupKeyPressEnabled by bool(POPUP_KEY_PRESS_ENABLED, false)
        val switchesEnabled by bool(SWITCHES_ENABLED, true)
        val switchArrowEnabled by bool(SWITCH_ARROW_ENABLED, true)

        enum class LandscapeModeOption {
            NEVER,
            LANDSCAPE,
            AUTO,
            ALWAYS,
        }

        val landscapeModeOption by enum(LANDSCAPE_MODE, LandscapeModeOption.NEVER)
        val splitSpacePercent by int(SPLIT_SPACE_PERCENT, 100)

        val hookCtrlA by bool(HOOK_CTRL_A, false)
        val hookCtrlCV by bool(HOOK_CTRL_CV, false)
        val hookCtrlLR by bool(HOOK_CTRL_LR, false)
        val hookCtrlZY by bool(HOOK_CTRL_ZY, false)
        val hookShiftSpace by bool(HOOK_SHIFT_SPACE, false)
        val hookShiftNum by bool(HOOK_SHIFT_NUM, false)
        val hookShiftSymbol by bool(HOOK_SHIFT_SYMBOL, false)

        val soundEnabled by bool(SOUND_ENABLED, false)
        var customSoundEnabled by bool(CUSTOM_SOUND_ENABLED, false)
        var customSoundPackage by string(CUSTOM_SOUND_PACKAGE, "")
        val soundVolume by int(SOUND_VOLUME, 100)
        val vibrationEnabled by bool(VIBRATION_ENABLED, false)
        val vibrationDuration by int(VIBRATION_DURATION, 10)
        val vibrationAmplitude by int(VIBRATION_AMPLITUDE, -1)
        val swipeEnabled by bool(SWIPE_ENABLED, true)
        val swipeTravel by int(SWIPE_TRAVEL, 80)
        val swipeVelocity by int(SWIPE_VELOCITY, 800)
        val longPressTimeout by int(LONG_PRESS_TIMEOUT, 400)
        val repeatInterval by int(REPEAT_INTERVAL, 50)
        var isSpeakKey by bool(SPEAK_KEY_PRESS_ENABLED, false)
        var isSpeakCommit by bool(SPEAK_COMMIT_ENABLED, false)
    }

    /**
     *  Wrapper class of theme and color settings.
     */
    class Theme(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val SELECTED_THEME = "theme_selected_theme"
            const val SELECTED_COLOR = "theme_selected_color"
            const val AUTO_DARK = "theme_auto_dark"
            const val USE_MINI_KEYBOARD = "theme_use_mini_keyboard"
            const val NAVBAR_BACKGROUND = "navbar_background"
        }

        var selectedTheme by string(SELECTED_THEME, "trime")
        var selectedColor by string(SELECTED_COLOR, "default")
        val autoDark by bool(AUTO_DARK, false)
        val useMiniKeyboard by bool(USE_MINI_KEYBOARD, false)

        enum class NavbarBackground {
            NONE,
            COLOR_ONLY,
            FULL,
        }

        var navbarBackground by enum(NAVBAR_BACKGROUND, NavbarBackground.COLOR_ONLY)
    }

    /**
     *  Wrapper class of profile settings.
     */
    class Profile(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val USER_DATA_DIR = "profile_user_data_dir"
            const val SYNC_BACKGROUND_ENABLED = "profile_sync_in_background"
            const val TIMING_SYNC_ENABLED = "profile_timing_sync"
            const val TIMING_SYNC_TRIGGER_TIME = "profile_timing_sync_trigger_time"
            const val LAST_SYNC_STATUS = "profile_last_sync_status"
            const val LAST_BACKGROUND_SYNC = "profile_last_background_sync"
        }

        var userDataDir by string(USER_DATA_DIR, DataManager.defaultDataDirectory.path)
        var syncBackgroundEnabled by bool(SYNC_BACKGROUND_ENABLED, false)
        var timingSyncEnabled by bool(TIMING_SYNC_ENABLED, false)
        var timingSyncTriggerTime by long(TIMING_SYNC_TRIGGER_TIME, Calendar.getInstance().timeInMillis + 1200000L)
        var lastSyncStatus by bool(LAST_SYNC_STATUS, false)
        var lastBackgroundSync by string(LAST_BACKGROUND_SYNC, "")
    }

    class Clipboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val CLIPBOARD_COMPARE_RULES = "clipboard_clipboard_compare"
            const val CLIPBOARD_OUTPUT_RULES = "clipboard_clipboard_output"
            const val DRAFT_OUTPUT_RULES = "clipboard_draft_output"
            const val DRAFT_EXCLUDE_APP = "clipboard_draft_exclude_app"
            const val DRAFT_LIMIT = "clipboard_draft_limit"
            const val CLIPBOARD_LIMIT = "clipboard_clipboard_limit"
        }

        var clipboardCompareRules by string(CLIPBOARD_COMPARE_RULES, "")
        var clipboardOutputRules by string(CLIPBOARD_OUTPUT_RULES, "")
        var draftOutputRules by string(DRAFT_OUTPUT_RULES, "")
        var clipboardLimit by int(CLIPBOARD_LIMIT, 10)
        var draftLimit by int(DRAFT_LIMIT, 10)
        var draftExcludeApp by string(DRAFT_EXCLUDE_APP, "")
    }

    /**
     *  Wrapper class of configuration settings.
     */
    class Other(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val UI_MODE = "other__ui_mode"
            const val SHOW_APP_ICON = "other__show_app_icon"
            const val SHOW_STATUS_BAR_ICON = "other__show_status_bar_icon"
        }

        enum class UiMode {
            AUTO,
            LIGHT,
            DARK,
        }

        var uiMode by enum(UI_MODE, UiMode.AUTO)
        var showAppIcon by bool(SHOW_APP_ICON, true)
        val showStatusBarIcon by bool(SHOW_STATUS_BAR_ICON, false)
    }
}
