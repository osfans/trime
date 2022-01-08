package com.osfans.trime.ime.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.os.UserManagerCompat
import androidx.preference.PreferenceManager
import com.blankj.utilcode.util.PathUtils
import com.osfans.trime.R
import com.osfans.trime.ime.enums.InlineModeType
import com.osfans.trime.ime.landscapeinput.LandscapeInputUIMode
import java.lang.ref.WeakReference

/**
 * Helper class for an organized access to the shared preferences.
 */
class Preferences(
    context: Context
) {
    var shared: SharedPreferences = if (!UserManagerCompat.isUserUnlocked(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        context.createDeviceProtectedStorageContext().getSharedPreferences("shared_psfs", Context.MODE_PRIVATE)
    else
        PreferenceManager.getDefaultSharedPreferences(context)

    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext)

    private val cacheBoolean: HashMap<String, Boolean> = hashMapOf()
    private val cacheInt: HashMap<String, Int> = hashMapOf()
    private val cacheString: HashMap<String, String> = hashMapOf()

    val general = General(this)
    val keyboard = Keyboard(this)
    val looks = Looks(this)
    val conf = Configuration(this)
    val other = Other(this)

    /**
     * Checks the cache if an entry for [key] exists, else calls [getPrefInternal] to retrieve the
     * value. The type is automatically derived from the given [default] value.
     * @return The value for [key] or [default].
     */
    private inline fun <reified T> getPref(key: String, default: T): T {
        return when {
            false is T -> {
                (cacheBoolean[key] ?: getPrefInternal(key, default)) as T
            }
            0 is T -> {
                (cacheInt[key] ?: getPrefInternal(key, default)) as T
            }
            "" is T -> {
                (cacheString[key] ?: getPrefInternal(key, default)) as T
            }
            else -> null as T
        }
    }

    /**
     * Fetches the value for [key] from the shared preferences, puts the value into the
     * corresponding cache and returns it.
     * @return The value for [key] or [default].
     */
    private inline fun <reified T> getPrefInternal(key: String, default: T): T {
        return when {
            false is T -> {
                val value = shared.getBoolean(key, default as Boolean)
                cacheBoolean[key] = value
                value as T
            }
            0 is T -> {
                val value = shared.getInt(key, default as Int)
                cacheInt[key] = value
                value as T
            }
            "" is T -> {
                val value = shared.getString(key, default as String) ?: (default as String)
                cacheString[key] = value
                value as T
            }
            else -> null as T
        }
    }

    /**
     * Sets the [value] for [key] in the shared preferences, puts the value into the corresponding
     * cache and returns it.
     */
    private inline fun <reified T> setPref(key: String, value: T) {
        when {
            false is T -> {
                shared.edit().putBoolean(key, value as Boolean).apply()
                cacheBoolean[key] = value as Boolean
            }
            0 is T -> {
                shared.edit().putInt(key, value as Int).apply()
                cacheInt[key] = value as Int
            }
            "" is T -> {
                shared.edit().putString(key, value as String).apply()
                cacheString[key] = value as String
            }
        }
    }

    companion object {
        private var defaultInstance: Preferences? = null

        @Synchronized
        fun initDefault(context: Context): Preferences {
            val instance = Preferences(context.applicationContext)
            defaultInstance = instance
            return instance
        }

        fun defaultInstance(): Preferences {
            return defaultInstance
                ?: throw UninitializedPropertyAccessException(
                    """
                    Default preferences not initialized! Make sure to call initDefault()
                    before accessing the default preferences.
                    """.trimIndent()
                )
        }
    }

    /**
     * Tells the [PreferenceManager] to set the defined preferences to their default values, if
     * they have not been initialized yet.
     */
    fun initDefaultPreferences() {
        try {
            applicationContext.get()?.let { context ->
                PreferenceManager.setDefaultValues(context, R.xml.keyboard_preference, true)
                PreferenceManager.setDefaultValues(context, R.xml.looks_preference, true)
                PreferenceManager.setDefaultValues(context, R.xml.conf_preference, true)
                PreferenceManager.setDefaultValues(context, R.xml.other_preference, true)
            }
        } catch (e: Exception) {
            e.fillInStackTrace()
        }
    }

    /**
     * Syncs the system preference values and clears the cache.
     */
    fun sync() {
        cacheBoolean.clear()
        cacheInt.clear()
        cacheString.clear()
    }

    class General(private val prefs: Preferences) {
        companion object {
            const val LAST_VERSION_NAME = "general__last_version_name"
        }
        var lastVersionName: String
            get() = prefs.getPref(LAST_VERSION_NAME, "")
            set(v) = prefs.setPref(LAST_VERSION_NAME, v)
    }

    /**
     *  Wrapper class of keyboard preferences.
     */
    class Keyboard(private val prefs: Preferences) {
        companion object {
            const val INLINE_PREEDIT_MODE = "keyboard__inline_preedit"
            const val SOFT_CURSOR_ENABLED = "keyboard__soft_cursor"
            const val FLOATING_WINDOW_ENABLED = "keyboard__show_window"
            const val POPUP_KEY_PRESS_ENABLED = "keyboard__show_key_popup"
            const val SWITCHES_ENABLED = "keyboard__show_switches"
            const val SWITCH_ARROW_ENABLED = "keyboard__show_switch_arrow"
            const val FULLSCREEN_MODE = "keyboard__fullscreen_mode"

            const val SOUND_ENABLED = "keyboard__key_sound"
            const val SOUND_VOLUME = "keyboard__key_sound_volume"
            const val SOUND_PACKAGE = "keyboard__key_sound_package"

            const val VIBRATION_ENABLED = "keyboard__key_vibration"
            const val VIBRATION_DURATION = "keyboard__key_vibration_duration"
            const val VIBRATION_AMPLITUDE = "keyboard__key_vibration_amplitude"

            const val SPEAK_KEY_PRESS_ENABLED = "keyboard__speak_key_press"
            const val SPEAK_COMMIT_ENABLED = "keyboard__speak_commit"

            const val SWIPE_TRAVEL = "keyboard__key_swipe_travel"
            const val LONG_PRESS_TIMEOUT = "keyboard__key_long_press_timeout"
            const val REPEAT_INTERVAL = "keyboard__key_repeat_interval"
        }
        var inlinePreedit: InlineModeType
            get() = InlineModeType.fromString(prefs.getPref(INLINE_PREEDIT_MODE, "preview"))
            set(v) = prefs.setPref(INLINE_PREEDIT_MODE, v)
        var fullscreenMode: LandscapeInputUIMode
            get() = LandscapeInputUIMode.fromString(prefs.getPref(FULLSCREEN_MODE, "auto_show"))
            set(v) = prefs.setPref(FULLSCREEN_MODE, v)
        var softCursorEnabled: Boolean = false
            get() = prefs.getPref(SOFT_CURSOR_ENABLED, true)
            private set
        var popupWindowEnabled: Boolean = false
            get() = prefs.getPref(FLOATING_WINDOW_ENABLED, true)
            private set
        var popupKeyPressEnabled: Boolean = false
            get() = prefs.getPref(POPUP_KEY_PRESS_ENABLED, false)
            private set
        var switchesEnabled: Boolean = false
            get() = prefs.getPref(SWITCHES_ENABLED, true)
            private set
        var switchArrowEnabled: Boolean = false
            get() = prefs.getPref(SWITCH_ARROW_ENABLED, true)
            private set
        var soundEnabled: Boolean = false
            get() = prefs.getPref(SOUND_ENABLED, false)
            private set
        var soundPackage: String
            get() = prefs.getPref(SOUND_PACKAGE, "")
            set(v) = prefs.setPref(SOUND_PACKAGE, v)
        var soundVolume: Int = 0
            get() = prefs.getPref(SOUND_VOLUME, 100)
            private set
        var vibrationEnabled: Boolean = false
            get() = prefs.getPref(VIBRATION_ENABLED, false)
            private set
        var vibrationDuration: Int = 0
            get() = prefs.getPref(VIBRATION_DURATION, 10)
            private set
        var vibrationAmplitude: Int = 0
            get() = prefs.getPref(VIBRATION_AMPLITUDE, -1)
            private set
        var swipeTravel: Int = 0
            get() = prefs.getPref(SWIPE_TRAVEL, 80)
            private set
        var longPressTimeout: Int = 0
            get() = prefs.getPref(LONG_PRESS_TIMEOUT, 20)
            private set
        var repeatInterval: Int = 0
            get() = prefs.getPref(REPEAT_INTERVAL, 4)
            private set
        var isSpeakKey: Boolean
            get() = prefs.getPref(SPEAK_KEY_PRESS_ENABLED, false)
            set(v) = prefs.setPref(SPEAK_KEY_PRESS_ENABLED, v)
        var isSpeakCommit: Boolean
            get() = prefs.getPref(SPEAK_COMMIT_ENABLED, false)
            set(v) = prefs.setPref(SPEAK_COMMIT_ENABLED, v)
    }

    /**
     *  Wrapper class of keyboard appearance preferences.
     */
    class Looks(private val prefs: Preferences) {
        companion object {
            const val SELECTED_THEME = "looks__selected_theme"
            const val SELECTED_COLOR = "looks__selected_color_scheme"
        }
        var selectedTheme: String
            get() = prefs.getPref(SELECTED_THEME, "trime")
            set(v) = prefs.setPref(SELECTED_THEME, v)
        var selectedColor: String
            get() = prefs.getPref(SELECTED_COLOR, "default")
            set(v) = prefs.setPref(SELECTED_COLOR, v)
    }

    /**
     *  Wrapper class of configuration settings.
     */
    class Configuration(private val prefs: Preferences) {
        companion object {
            const val SHARED_DATA_DIR = "conf__shared_data_dir"
            const val USER_DATA_DIR = "conf__user_data_dir"
            const val SYNC_BACKGROUND_ENABLED = "conf__sync_background"
            const val LAST_SYNC_STATUS = "conf__last_sync_status"
            const val LAST_SYNC_TIME = "conf__last_sync_time"
            val SDCARD_PATH_PREFIX: String = PathUtils.getExternalStoragePath()
        }
        var sharedDataDir: String
            get() = prefs.getPref(SHARED_DATA_DIR, "$SDCARD_PATH_PREFIX/rime")
            set(v) = prefs.setPref(SHARED_DATA_DIR, v)
        var userDataDir: String
            get() = prefs.getPref(USER_DATA_DIR, "$SDCARD_PATH_PREFIX/rime")
            set(v) = prefs.setPref(USER_DATA_DIR, v)
        var syncBackgroundEnabled: Boolean
            get() = prefs.getPref(SYNC_BACKGROUND_ENABLED, false)
            set(v) = prefs.setPref(SYNC_BACKGROUND_ENABLED, v)
        var lastSyncStatus: Boolean
            get() = prefs.getPref(LAST_SYNC_STATUS, false)
            set(v) = prefs.setPref(LAST_SYNC_STATUS, v)
        var lastSyncTime: Long
            get() = prefs.getPref(LAST_SYNC_TIME, 0).toLong()
            set(v) = prefs.setPref(LAST_SYNC_TIME, v)
    }

    /**
     *  Wrapper class of configuration settings.
     */
    class Other(private val prefs: Preferences) {
        companion object {
            const val UI_MODE = "other__ui_mode"
            const val SHOW_APP_ICON = "other__show_app_icon"
            const val SHOW_STATUS_BAR_ICON = "other__show_status_bar_icon"
            const val DESTROY_ON_QUIT = "other__destroy_on_quit"
            const val SELECTION_SENSE = "other__selection_sense"
            const val CLICK_CANDIDATE_AND_COMMIT = "other__click_candidate_and_commit"
            const val CLIPBOARD_COMPARE_RULES = "other__clipboard_compare"
            const val CLIPBOARD_OUTPUT_RULES = "other__clipboard_output"
            const val DRAFT_OUTPUT_RULES = "other__draft_output"
            const val DRAFT_LIMIT = "other__draft_limit"
            const val CLIPBOARD_LIMIT = "other__clipboard_limit"
        }
        var uiMode: String
            get() = prefs.getPref(UI_MODE, "auto")
            set(v) = prefs.setPref(UI_MODE, v)
        var showAppIcon: Boolean
            get() = prefs.getPref(SHOW_APP_ICON, true)
            set(v) = prefs.setPref(SHOW_APP_ICON, v)
        var selectionSense: Boolean
            get() = prefs.getPref(SELECTION_SENSE, true)
            set(v) = prefs.setPref(SELECTION_SENSE, v)
        var clickCandidateAndCommit: Boolean
            get() = prefs.getPref(CLICK_CANDIDATE_AND_COMMIT, true)
            set(v) = prefs.setPref(CLICK_CANDIDATE_AND_COMMIT, v)
        var showStatusBarIcon: Boolean = false
            get() = prefs.getPref(SHOW_STATUS_BAR_ICON, false)
            private set
        var destroyOnQuit: Boolean = false
            get() = prefs.getPref(DESTROY_ON_QUIT, false)
            private set
        var clipboardCompareRules: String
            get() = prefs.getPref(CLIPBOARD_COMPARE_RULES, "")
            set(v) = prefs.setPref(CLIPBOARD_COMPARE_RULES, v)
        var clipboardOutputRules: String
            get() = prefs.getPref(CLIPBOARD_OUTPUT_RULES, "")
            set(v) = prefs.setPref(CLIPBOARD_OUTPUT_RULES, v)
        var draftOutputRules: String
            get() = prefs.getPref(DRAFT_OUTPUT_RULES, "")
            set(v) = prefs.setPref(DRAFT_OUTPUT_RULES, v)
        var clipboardLimit: String
            get() = prefs.getPref(CLIPBOARD_LIMIT, "50")
            set(v) = prefs.setPref(CLIPBOARD_LIMIT, v)
        var draftLimit: String
            get() = prefs.getPref(DRAFT_LIMIT, "20")
            set(v) = prefs.setPref(DRAFT_LIMIT, v)
    }
}
