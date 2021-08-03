package com.osfans.trime.ime.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.preference.PreferenceManager
import com.blankj.utilcode.util.PathUtils
import com.osfans.trime.R
import java.lang.ref.WeakReference

/**
 * Helper class for an organized access to the shared preferences.
 */
class Preferences(
    context: Context,
    val shared: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
) {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext)

    private val cacheBoolean: HashMap<String, Boolean> = hashMapOf()
    private val cacheInt: HashMap<String, Int> = hashMapOf()
    private val cacheString: HashMap<String, String> = hashMapOf()

    val keyboard = Keyboard(this)
    val appearance = Appearance(this)
    val input = Configuration(this)
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
                ?: throw UninitializedPropertyAccessException("""
                    Default preferences not initialized! Make sure to call initDefault()
                    before accessing the default preferences.
                """.trimIndent())
        }
    }

    /**
     * Tells the [PreferenceManager] to set the defined preferences to their default values, if
     * they have not been initialized yet.
     */
    fun initDefaultPreferences() {
        applicationContext.get()?.let { context ->
            PreferenceManager.setDefaultValues(context, R.xml.keyboard_preference, true)
            PreferenceManager.setDefaultValues(context, R.xml.appearance_preference, true)
            PreferenceManager.setDefaultValues(context, R.xml.input_preference, true)
            PreferenceManager.setDefaultValues(context, R.xml.other_preference, true)
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

    /**
     *  Wrapper class of keyboard preferences.
     */
    class Keyboard(private val prefs: Preferences) {
        companion object {
            const val INLINE_PREEDIT_MODE =      "inline_preedit"
            const val SOFT_CURSOR_ENABLED =      "soft_cursor"
            const val FLOATING_WINDOW_ENABLED =  "show_window"
            const val POPUP_KEY_PRESS_ENABLED =  "show_preview"
            const val SWITCHES_ENABLED =         "show_switches"

            const val SOUND_ENABLED =          "key_sound"
            const val SOUND_VOLUME =           "key_sound_volume"

            const val VIBRATION_ENABLED =      "key_vibrate"
            const val VIBRATION_DURATION =     "key_vibrate_duration"
            const val VIBRATION_AMPLITUDE =    "key_vibrate_amplitude"

            const val SPEAK_KEY_ENABLED =      "speak_key"
            const val SPEAK_COMMIT_ENABLED =   "speak_commit"

            const val LONG_PRESS_TIMEOUT =     "longpress_timeout"
            const val REPEAT_INTERVAL =        "repeat_interval"
        }
        var inlinePreedit: String = ""
            get() =  prefs.getPref(INLINE_PREEDIT_MODE, "preview")
            private set
        var softCursorEnabled: Boolean = false
            get() = prefs.getPref(SOFT_CURSOR_ENABLED, true)
            private set
        var floatingWindowEnabled: Boolean = false
            get() = prefs.getPref(FLOATING_WINDOW_ENABLED, true)
            private set
        var popupKeyPressEnabled: Boolean = false
            get() = prefs.getPref(POPUP_KEY_PRESS_ENABLED, false)
            private set
        var switchesEnabled: Boolean = false
            get() = prefs.getPref(SWITCHES_ENABLED, true)
            private set
        var soundEnabled: Boolean = false
            get() = prefs.getPref(SOUND_ENABLED, false)
            private set
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
        var longPressTimeout: Int = 0
            get() = prefs.getPref(LONG_PRESS_TIMEOUT, 20)
            private set
        var repeatInterval: Int = 0
            get() = prefs.getPref(REPEAT_INTERVAL, 40)
            private set
        var isSpeakKey: Boolean
            get() =  prefs.getPref(SPEAK_KEY_ENABLED, false)
            set(v) = prefs.setPref(SPEAK_KEY_ENABLED, v)
        var isSpeakCommit: Boolean
            get() =  prefs.getPref(SPEAK_COMMIT_ENABLED, false)
            set(v) = prefs.setPref(SPEAK_COMMIT_ENABLED, v)
    }

    /**
     *  Wrapper class of keyboard appearance preferences.
     */
    class Appearance(private val prefs: Preferences) {
        companion object {
            const val SELECTED_THEME = "pref_selected_theme"
            const val SELECTED_COLOR = "pref_selected_color_scheme"
        }
        var selectedTheme: String
            get() =  prefs.getPref(SELECTED_THEME, "trime")
            set(v) = prefs.setPref(SELECTED_THEME, v)
        var selectedColor: String
            get() =  prefs.getPref(SELECTED_COLOR, "default")
            set(v) = prefs.setPref(SELECTED_COLOR, v)
    }

    /**
     *  Wrapper class of configuration settings.
     */
    class Configuration(private val prefs: Preferences) {
        companion object {
            const val SHARED_DATA_DIR =            "shared_data_dir"
            const val USER_DATA_DIR =              "user_data_dir"
            const val SYNC_BACKGROUND_ENABLED =    "pref_sync_bg"
            const val LAST_SYNC_STATUS =           "last_sync_status"
            const val LAST_SYNC_TIME =             "last_sync_time"
            val SDCARD_PATH_PREFIX: String = PathUtils.getExternalStoragePath()
        }
        var sharedDataDir: String
            get() =  prefs.getPref(SHARED_DATA_DIR, "$SDCARD_PATH_PREFIX/rime")
            set(v) = prefs.setPref(SHARED_DATA_DIR, v)
        var userDataDir: String
            get() =  prefs.getPref(USER_DATA_DIR, "$SDCARD_PATH_PREFIX/rime")
            set(v) = prefs.setPref(USER_DATA_DIR, v)
        var syncBackgroundEnabled: Boolean
            get() = prefs.getPref(SYNC_BACKGROUND_ENABLED, false)
            set(v) = prefs.setPref(SYNC_BACKGROUND_ENABLED, v)
        var lastSyncStatus: Boolean = false
            get() = prefs.getPref(LAST_SYNC_STATUS, false)
            private set
        var lastSyncTime: Long = 0
            get() = prefs.getPref(LAST_SYNC_TIME, 0).toLong()
            private set
    }

    /**
     *  Wrapper class of configuration settings.
     */
    class Other(private val prefs: Preferences) {
        companion object {
            const val UI_MODE = "pref__settings_theme"
            const val SHOW_APP_ICON = "pref__others__show_app_icon"
            const val SHOW_STATUS_BAR_ICON = "pref_notification_icon"
            const val DESTROY_ON_QUIT = "pref_destroy_on_quit"
        }
        var uiMode: String
            get() =  prefs.getPref(UI_MODE, "auto")
            set(v) = prefs.setPref(UI_MODE, v)
        var showAppIcon: Boolean
            get() =  prefs.getPref(SHOW_APP_ICON, true)
            set(v) = prefs.setPref(SHOW_APP_ICON, v)
        var showStatusBarIcon: Boolean = false
            get() = prefs.getPref(SHOW_STATUS_BAR_ICON, false)
            private set
        var destroyOnQuit: Boolean = false
            get() = prefs.getPref(DESTROY_ON_QUIT, false)
            private set
    }
}