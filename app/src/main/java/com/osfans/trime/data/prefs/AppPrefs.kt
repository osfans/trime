// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import androidx.preference.PreferenceManager
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PopupPosition
import com.osfans.trime.ime.core.ComposingTextMode
import com.osfans.trime.util.appContext
import java.lang.ref.WeakReference

/**
 * Helper class for an organized access to the shared preferences.
 */
class AppPrefs(
    private val shared: SharedPreferences,
) {
    private val applicationContext: WeakReference<Context> = WeakReference(appContext)

    private val providers = mutableListOf<PreferenceDelegateProvider>()

    fun <T : PreferenceDelegateProvider> registerProvider(providerF: (SharedPreferences) -> T): T {
        val provider = providerF(shared)
        providers.add(provider)
        return provider
    }

    private fun <T : PreferenceDelegateProvider> T.register() =
        this.apply {
            registerProvider { this }
        }

    val internal = Internal(shared)
    val general = General(shared).register()
    val keyboard = Keyboard(shared)
    val profile = Profile(shared).register()
    val clipboard = Clipboard(shared)
    val other = Other(shared)

    val candidates = Candidates(shared).register()

    @Keep
    private val onSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            providers.forEach {
                it.notifyChange(key)
            }
        }

    companion object {
        private var defaultInstance: AppPrefs? = null

        fun initDefault(sharedPreferences: SharedPreferences): AppPrefs {
            val instance = AppPrefs(sharedPreferences)
            defaultInstance = instance
            sharedPreferences.registerOnSharedPreferenceChangeListener(
                defaultInstance().onSharedPreferenceChangeListener,
            )
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

        val pid = int(PID, 0)
    }

    class General(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.general) {
        companion object {
            const val COMPOSING_TEXT_MODE = "composing_text_mode"
        }

        val composingTextMode = enum(R.string.composing_text_mode, COMPOSING_TEXT_MODE, ComposingTextMode.DISABLE)
    }

    /**
     *  Wrapper class of keyboard settings.
     */
    class Keyboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val SOFT_CURSOR_ENABLED = "keyboard__soft_cursor"
            const val POPUP_KEY_PRESS_ENABLED = "keyboard__show_key_popup"
            const val HIDE_QUICK_BAR = "hide_quick_bar"
            const val LANDSCAPE_MODE = "keyboard__landscape_mode"
            const val SPLIT_SPACE_PERCENT = "keyboard__split_space"

            const val HOOK_CTRL_A = "keyboard__hook_ctrl_a"
            const val HOOK_CTRL_CV = "keyboard__hook_ctrl_cv"
            const val HOOK_CTRL_LR = "keyboard__hook_ctrl_lr"
            const val HOOK_CTRL_ZY = "keyboard__hook_ctrl_zy"
            const val HOOK_SHIFT_SPACE = "keyboard__hook_shift_space"
            const val HOOK_SHIFT_NUM = "keyboard__hook_shift_num"
            const val HOOK_SHIFT_SYMBOL = "keyboard__hook_shift_symbol"
            const val HOOK_SHIFT_ARROW = "keyboard__hook_shift_arrow"

            const val SOUND_ON_KEYPRESS = "sound_on_keypress"
            const val KEY_SOUND_VALUE = "sound_volume"
            const val SOUND_EFFECT_ENABLED = "custom_sound_effect_enabled"
            const val CUSTOM_SOUND_EFFECT = "custom_sound_effect_name"

            const val VIBRATE_ON_KEYPRESS = "vibrate_on_keypress"
            const val VIBRATION_DURATION = "vibration_duration"
            const val VIBRATION_AMPLITUDE = "vibration_amplitude"

            const val SPEAK_ON_KEYPRESS = "speak_on_keypress"
            const val SPEAK_ON_COMMIT = "speak_on_commit"

            const val SWIPE_ENABLED = "keyboard__swipe_enabled"
            const val SWIPE_TRAVEL = "keyboard__key_swipe_travel"
            const val SWIPE_VELOCITY = "keyboard__key_swipe_velocity"
            const val LONG_PRESS_TIMEOUT = "keyboard__key_long_press_timeout"
            const val REPEAT_INTERVAL = "keyboard__key_repeat_interval"
        }

        val softCursorEnabled = bool(SOFT_CURSOR_ENABLED, true)
        val popupKeyPressEnabled = bool(POPUP_KEY_PRESS_ENABLED, false)
        val hideQuickBar = bool(HIDE_QUICK_BAR, false)

        enum class LandscapeMode {
            NEVER,
            LANDSCAPE,
            AUTO,
            ALWAYS,
        }

        val landscapeMode = enum(LANDSCAPE_MODE, LandscapeMode.NEVER)
        val splitSpacePercent = int(SPLIT_SPACE_PERCENT, 100)

        val hookCtrlA = bool(HOOK_CTRL_A, false)
        val hookCtrlCV = bool(HOOK_CTRL_CV, false)
        val hookCtrlLR = bool(HOOK_CTRL_LR, false)
        val hookCtrlZY = bool(HOOK_CTRL_ZY, false)
        val hookShiftSpace = bool(HOOK_SHIFT_SPACE, false)
        val hookShiftNum = bool(HOOK_SHIFT_NUM, false)
        val hookShiftSymbol = bool(HOOK_SHIFT_SYMBOL, false)
        val hookShiftArrow = bool(HOOK_SHIFT_ARROW, true)

        val soundOnKeyPress = bool(SOUND_ON_KEYPRESS, false)
        val soundEffectEnabled = bool(SOUND_EFFECT_ENABLED, false)
        val customSoundEffect = string(CUSTOM_SOUND_EFFECT, "")
        val soundVolume = int(KEY_SOUND_VALUE, 100)
        val vibrateOnKeyPress = bool(VIBRATE_ON_KEYPRESS, false)
        val vibrationDuration = int(VIBRATION_DURATION, 10)
        val vibrationAmplitude = int(VIBRATION_AMPLITUDE, -1)
        val swipeEnabled = bool(SWIPE_ENABLED, true)
        val swipeTravel = int(SWIPE_TRAVEL, 80)
        val swipeVelocity = int(SWIPE_VELOCITY, 800)
        val longPressTimeout = int(LONG_PRESS_TIMEOUT, 400)
        val repeatInterval = int(REPEAT_INTERVAL, 50)
        val speakOnKeyPress = bool(SPEAK_ON_KEYPRESS, false)
        val speakOnCommit = bool(SPEAK_ON_COMMIT, false)
    }

    class Candidates(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.candidates_window) {
        companion object {
            const val MODE = "show_candidates_window"
            const val POSITION = "candidates_window_position"
        }

        val mode = enum(R.string.show_candidates_window, MODE, PopupCandidatesMode.DISABLED)
        val position = enum(R.string.candidates_window_position, POSITION, PopupPosition.BOTTOM_LEFT)
    }

    /**
     *  Wrapper class of profile settings.
     */
    class Profile(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val USER_DATA_DIR = "profile_user_data_dir"
            const val PERIODIC_BACKGROUND_SYNC = "periodic_background_sync"
            const val PERIODIC_BACKGROUND_SYNC_INTERVAL = "periodic_background_sync_interval"
            const val LAST_BACKGROUND_SYNC_STATUS = "last_background_sync_status"
            const val LAST_BACKGROUND_SYNC_TIME = "last_background_sync_time"
        }

        val userDataDir = string(USER_DATA_DIR, DataManager.defaultDataDir.path)
        val periodicBackgroundSync = bool(PERIODIC_BACKGROUND_SYNC, false)
        val periodicBackgroundSyncInterval = int(PERIODIC_BACKGROUND_SYNC_INTERVAL, 30)
        val lastBackgroundSyncStatus = bool(LAST_BACKGROUND_SYNC_STATUS, false)
        val lastBackgroundSyncTime = long(LAST_BACKGROUND_SYNC_TIME, 0L)
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

        val clipboardCompareRules = string(CLIPBOARD_COMPARE_RULES, "")
        val clipboardOutputRules = string(CLIPBOARD_OUTPUT_RULES, "")
        val draftOutputRules = string(DRAFT_OUTPUT_RULES, "")
        val clipboardLimit = int(CLIPBOARD_LIMIT, 10)
        val draftLimit = int(DRAFT_LIMIT, 10)
        val draftExcludeApp = string(DRAFT_EXCLUDE_APP, "")
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
        }

        enum class UiMode {
            AUTO,
            LIGHT,
            DARK,
        }

        val uiMode = enum(UI_MODE, UiMode.AUTO)
        val showAppIcon = bool(SHOW_APP_ICON, true)
    }
}
