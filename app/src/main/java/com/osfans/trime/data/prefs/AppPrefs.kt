/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ime.candidates.compact.CompactCandidateMode
import com.osfans.trime.ime.candidates.popup.PopupCandidatesLayout
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PopupPosition
import com.osfans.trime.ime.core.InlinePreeditMode
import com.osfans.trime.util.InputMethodUtils
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

    private fun <T : PreferenceDelegateProvider> T.register() = this.apply {
        registerProvider { this }
    }

    val internal = Internal(shared)
    val general = General(shared).register()
    val profile = Profile(shared).register()
    val keyboard = Keyboard(shared).register()
    val candidates = Candidates(shared).register()
    val clipboard = Clipboard(shared).register()
    val advanced = Advanced(shared).register()

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

        fun defaultInstance(): AppPrefs = defaultInstance
            ?: throw UninitializedPropertyAccessException(
                """
                    Default preferences not initialized! Make sure to call initDefault()
                    before accessing the default preferences.
                """.trimIndent(),
            )
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
            const val INLINE_PREEDIT_MODE = "inline_preedit_mode"
            const val ASCII_SWITCH_TIPS = "ascii_switch_tips"
            const val INLINE_SUGGESTIONS = "inline_suggestions"
            const val PREFERRED_VOICE_INPUT = "preferred_voice_input"
        }

        val inlinePreeditMode = enum(R.string.inline_preedit_mode, INLINE_PREEDIT_MODE, InlinePreeditMode.DISABLE)
        val asciiSwitchTips = switch(R.string.ascii_switch_tips, ASCII_SWITCH_TIPS, true)
        val inlineSuggestions = switch(R.string.inline_suggestions, INLINE_SUGGESTIONS, true)

        val preferredVoiceInput = list(
            R.string.preferred_voice_input,
            PREFERRED_VOICE_INPUT,
            "",
            { InputMethodUtils.voiceInputMethods().map { it.first.packageName } },
            { ctx ->
                InputMethodUtils.voiceInputMethods().map { it.first.loadLabel(ctx.packageManager) }
            },
        )
    }

    /**
     *  Wrapper class of keyboard settings.
     */
    class Keyboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.virtual_keyboard) {
        companion object {
            const val LANDSCAPE_MODE = "keyboard_landscape_mode"
            const val SPLIT_SPACE_PERCENT = "keyboard_split_space"

            const val USE_SOFT_CURSOR = "use_soft_cursor"
            const val HIDE_INPUT_BAR = "hide_input_bar"

            const val SOUND_ON_KEYPRESS = "sound_on_keypress"
            const val KEY_SOUND_VOLUME = "sound_volume"
            const val USE_CUSTOM_SOUND_EFFECT = "custom_sound_effect_enabled"
            const val CUSTOM_SOUND_EFFECT = "custom_sound_effect_name"
            const val VIBRATE_ON_KEY_PRESS = "vibrate_on_key_press"
            const val VIBRATE_ON_KEY_RELEASE = "vibrate_on_key_release"
            const val VIBRATE_ON_KEY_REPEAT = "vibrate_on_key_repeat"
            const val VIBRATION_DURATION = "vibration_duration"
            const val VIBRATION_AMPLITUDE = "vibration_amplitude"
            const val SPEAK_ON_KEYPRESS = "speak_on_keypress"
            const val SPEAK_ON_COMMIT = "speak_on_commit"
            const val POPUP_ON_KEY_PRESS = "show_key_popup"
            const val SWIPE_ENABLED = "swipe_enabled"
            const val SWIPE_TRAVEL = "key_swipe_travel"
            const val SWIPE_VELOCITY = "key_swipe_velocity"
            const val LONG_PRESS_TIMEOUT = "key_long_press_timeout"
            const val REPEAT_INTERVAL = "key_repeat_interval"
            const val DOUBLE_TAP_TIMEOUT = "key_double_tap_timeout"
            const val SLIDE_STEP_SIZE = "key_slide_step_size"

            const val HOOK_CTRL_A = "hook_ctrl_a"
            const val HOOK_CTRL_CV = "hook_ctrl_cv"
            const val HOOK_CTRL_LR = "hook_ctrl_lr"
            const val HOOK_CTRL_ZY = "hook_ctrl_zy"
            const val HOOK_SHIFT_SPACE = "hook_shift_space"
            const val HOOK_SHIFT_NUM = "hook_shift_num"
            const val HOOK_SHIFT_SYMBOL = "hook_shift_symbol"
            const val HOOK_SHIFT_ARROW = "hook_shift_arrow"

            const val MAX_SPAN_COUNT = "max_span_count"
            const val MAX_SPAN_COUNT_LANDSCAPE = "max_span_count_landscape"
            const val HORIZONTAL_CANDIDATE_MODE = "horizontal_candidate_mode"
        }

        enum class LandscapeMode(override val stringRes: Int) : PreferenceDelegateEnum {
            NEVER(R.string.never),
            LANDSCAPE(R.string.landscape_only),
            WIDE(R.string.wide_or_landscape),
            ALWAYS(R.string.always),
        }

        val landscapeMode = enum(R.string.enable_landscape_mode, LANDSCAPE_MODE, LandscapeMode.NEVER)
        val splitSpacePercent = int(
            R.string.split_space_percent,
            SPLIT_SPACE_PERCENT,
            100,
            0,
            200,
            "%",
        )

        val useSoftCursor = switch(R.string.use_soft_cursor, USE_SOFT_CURSOR, true)

        val hideInputBar = switch(R.string.hide_input_bar, HIDE_INPUT_BAR, false)

        val soundOnKeyPress = switch(R.string.sound_on_keypress, SOUND_ON_KEYPRESS, false)
        val soundVolume = int(
            R.string.sound_volume,
            KEY_SOUND_VOLUME,
            0,
            0,
            100,
            "%",
            defaultLabel = R.string.system_default,
        ) { soundOnKeyPress.getValue() }

        val useCustomSoundEffect = switch(
            R.string.custom_sound_effect_enabled,
            USE_CUSTOM_SOUND_EFFECT,
            false,
        ) { soundOnKeyPress.getValue() }
        val customSoundEffect = string(
            R.string.custom_sound_effect_name,
            CUSTOM_SOUND_EFFECT,
            "",
        ) { soundOnKeyPress.getValue() && useCustomSoundEffect.getValue() }

        val vibrateOnKeyPress = switch(R.string.vibrate_on_key_press, VIBRATE_ON_KEY_PRESS, false)
        val vibrateOnKeyRelease = switch(
            R.string.vibrate_on_key_release,
            VIBRATE_ON_KEY_RELEASE,
            false,
        ) { vibrateOnKeyPress.getValue() }

        val vibrateOnKeyRepeat = switch(
            R.string.vibrate_on_key_repeat,
            VIBRATE_ON_KEY_REPEAT,
            false,
        ) { vibrateOnKeyPress.getValue() }

        val vibrationDuration = int(
            R.string.vibration_duration,
            VIBRATION_DURATION,
            0,
            0,
            100,
            "ms",
            defaultLabel = R.string.system_default,
        ) { vibrateOnKeyPress.getValue() }

        val vibrationAmplitude = int(
            R.string.vibration_amplitude,
            VIBRATION_AMPLITUDE,
            0,
            0,
            255,
            defaultLabel = R.string.system_default,
        ) { vibrateOnKeyPress.getValue() }

        val speakOnKeyPress = switch(R.string.speak_on_keypress, SPEAK_ON_KEYPRESS, false)
        val speakOnCommit = switch(R.string.speak_on_commit, SPEAK_ON_COMMIT, false)
        val popupOnKeyPress = switch(R.string.popup_on_key_press, POPUP_ON_KEY_PRESS, false)
        val swipeEnabled = switch(R.string.key_swipe_enabled, SWIPE_ENABLED, true)
        val swipeTravel = int(
            R.string.key_swipe_travel,
            SWIPE_TRAVEL,
            30,
            0,
            400,
            "dp",
            10,
        ) { swipeEnabled.getValue() }

        val swipeVelocity = int(
            R.string.key_swipe_velocity,
            SWIPE_VELOCITY,
            800,
            0,
            10000,
            "dp/s",
            100,
        ) { swipeEnabled.getValue() }

        val longPressTimeout = int(
            R.string.key_long_press_timeout,
            LONG_PRESS_TIMEOUT,
            300,
            100,
            1000,
            "ms",
            10,
        )

        val repeatInterval = int(
            R.string.key_repeat_interval,
            REPEAT_INTERVAL,
            30,
            10,
            100,
            "ms",
            10,
        )

        val doubleTapTimeout = int(
            R.string.key_double_tap_timeout,
            DOUBLE_TAP_TIMEOUT,
            300,
            100,
            1000,
            "ms",
            10,
        )

        val slideStepSize = int(
            R.string.key_slide_step_size,
            SLIDE_STEP_SIZE,
            24,
            0,
            100,
            "dp",
        )

        val horizontalCandidateMode = enum(R.string.horizontal_candidate_style, HORIZONTAL_CANDIDATE_MODE, CompactCandidateMode.AUTO_FILL)

        val maxSpanCount = int(
            R.string.max_span_count,
            MAX_SPAN_COUNT,
            6,
            1,
            10,
            enableUiOn = {
                shared.getString(HORIZONTAL_CANDIDATE_MODE, null) ==
                    CompactCandidateMode.AUTO_FILL.name
            },
        )

        val maxSpanCountLandscape = int(
            R.string.max_span_count_landscape,
            MAX_SPAN_COUNT_LANDSCAPE,
            8,
            4,
            12,
            enableUiOn = {
                shared.getString(HORIZONTAL_CANDIDATE_MODE, null) ==
                    CompactCandidateMode.AUTO_FILL.name
            },
        )

        val hookCtrlA = switch(R.string.hook_ctrl_a, HOOK_CTRL_A, false)
        val hookCtrlCV = switch(R.string.hook_ctrl_cv, HOOK_CTRL_CV, false)
        val hookCtrlLR = switch(R.string.hook_ctrl_lr, HOOK_CTRL_LR, false)
        val hookCtrlZY = switch(R.string.hook_ctrl_zy, HOOK_CTRL_ZY, false)
        val hookShiftSpace = switch(R.string.hook_shift_space, HOOK_SHIFT_SPACE, false)
        val hookShiftNum = switch(R.string.hook_shift_num, HOOK_SHIFT_NUM, false)
        val hookShiftSymbol = switch(R.string.hook_shift_symbol, HOOK_SHIFT_SYMBOL, false)
        val hookShiftArrow = switch(R.string.hook_shift_arrow, HOOK_SHIFT_ARROW, true)
    }

    class Candidates(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.candidates_window) {
        companion object {
            const val MODE = "show_candidates_window"
            const val LAYOUT = "candidates_layout"
            const val POSITION = "candidates_window_position"
        }

        val mode = enum(R.string.show_candidates_window, MODE, PopupCandidatesMode.DISABLED)
        val layout = enum(R.string.candidates_layout, LAYOUT, PopupCandidatesLayout.AUTOMATIC)
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
    ) : PreferenceDelegateOwner(shared, R.string.clipboard) {
        companion object {
            const val CLIPBOARD_LISTENING = "clipboard_listening"
            const val CLIPBOARD_LIMIT = "clipboard_clipboard_limit"
            const val CLIPBOARD_COMPARE_RULES = "clipboard_clipboard_compare"
            const val CLIPBOARD_OUTPUT_RULES = "clipboard_clipboard_output"
            const val CLIPBOARD_SUGGESTION = "clipboard_suggestion"
            const val CLIPBOARD_SUGGESTION_TIMEOUT = "clipboard_suggestion_timeout"
            const val CLIPBOARD_RETURN_AFTER_PASTE = "clipboard_return_after_paste"
        }
        val clipboardListening = switch(R.string.clipboard_listening, CLIPBOARD_LISTENING, true)
        val clipboardLimit = int(
            R.string.clipboard_limit,
            CLIPBOARD_LIMIT,
            10,
        ) { clipboardListening.getValue() }
        val clipboardCompareRules = editText(
            R.string.clipboard_compare_rules,
            CLIPBOARD_COMPARE_RULES,
            "",
            R.string.a_regular_expression_per_line,
        ) { clipboardListening.getValue() }
        val clipboardOutputRules = editText(
            R.string.clipboard_output_rules,
            CLIPBOARD_OUTPUT_RULES,
            "",
            R.string.a_regular_expression_per_line,
        ) { clipboardListening.getValue() }
        val clipboardSuggestion = switch(
            R.string.clipboard_suggestion,
            CLIPBOARD_SUGGESTION,
            true,
        ) { clipboardListening.getValue() }
        val clipboardSuggestionTimeout = int(
            R.string.clipboard_suggestion_timeout,
            CLIPBOARD_SUGGESTION_TIMEOUT,
            20,
            0,
            100,
            "s",
        ) { clipboardListening.getValue() && clipboardSuggestion.getValue() }
        val clipboardReturnAfterPaste = switch(
            R.string.clipboard_return_after_paste,
            CLIPBOARD_RETURN_AFTER_PASTE,
            true,
        ) { clipboardListening.getValue() }
    }

    class Advanced(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.advanced) {
        companion object {
            const val UI_MODE = "ui_mode"
            const val SHOW_APP_ICON = "show_app_icon"
        }

        enum class UiMode(override val stringRes: Int) : PreferenceDelegateEnum {
            AUTO(R.string.automatic),
            LIGHT(R.string.light),
            DARK(R.string.dark),
        }

        val uiMode = enum(R.string.ui_mode, UI_MODE, UiMode.AUTO)
        val showAppIcon = switch(
            R.string.show_app_icon,
            SHOW_APP_ICON,
            true,
            R.string.only_available_on_some_roms,
        )
    }
}
