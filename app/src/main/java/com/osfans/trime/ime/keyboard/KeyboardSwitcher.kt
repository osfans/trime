package com.osfans.trime.ime.keyboard

import android.content.res.Configuration
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.appContext
import timber.log.Timber

/** Manages [Keyboard]s and their status. **/
object KeyboardSwitcher {
    private val theme get() = ThemeManager.activeTheme
    private val allKeyboardIds get() = theme.allKeyboardIds
    private val keyboardCache: MutableMap<String, Keyboard> = mutableMapOf()
    private var currentKeyboardId: String? = null
    private var lastKeyboardId: String? = null
    private var lastLockKeyboardId: String? = null
    private val keyboardPrefs = KeyboardPrefs()
    lateinit var currentKeyboard: Keyboard

    private fun getKeyboard(name: String): Keyboard {
        if (keyboardCache.containsKey(name)) {
            return keyboardCache[name]!!
        }
        val keyboard = Keyboard(name)
        keyboardCache[name] = keyboard
        return keyboard
    }

    init {
        newOrReset()
    }

    @JvmStatic
    fun newOrReset() {
        Timber.d("Switching keyboard back to .default ...")
        currentKeyboardId = null
        lastKeyboardId = null
        lastLockKeyboardId = null
        keyboardCache.clear()
        switchKeyboard(".default")
    }

    fun switchKeyboard(name: String?) {
        val currentIdx = theme.allKeyboardIds.indexOf(currentKeyboardId)
        var mappedName =
            when (name) {
                ".default" -> autoMatch()
                ".prior" ->
                    try {
                        theme.allKeyboardIds[currentIdx - 1]
                    } catch (e: IndexOutOfBoundsException) {
                        currentKeyboardId
                    }
                ".next" ->
                    try {
                        theme.allKeyboardIds[currentIdx + 1]
                    } catch (e: IndexOutOfBoundsException) {
                        currentKeyboardId
                    }
                ".last" -> lastKeyboardId
                ".last_lock" -> lastLockKeyboardId
                ".ascii" -> {
                    with(currentKeyboard.asciiKeyboard) {
                        if (this in allKeyboardIds) this else currentKeyboardId
                    }
                }
                else -> {
                    if (name.isNullOrEmpty()) {
                        if (currentKeyboard.isLock) currentKeyboardId else lastLockKeyboardId
                    } else {
                        name
                    }
                }
            }

        if (mappedName.isNullOrEmpty()) mappedName = autoMatch()

        // 切换到 mini 键盘
        val deviceKeyboard = appContext.resources.configuration.keyboard
        if (AppPrefs.defaultInstance().theme.useMiniKeyboard && deviceKeyboard != Configuration.KEYBOARD_NOKEYS) {
            if ("mini" in allKeyboardIds) mappedName = "mini"
        }
        // 切换到横屏布局
        if (keyboardPrefs.isLandscapeMode()) {
            val landscapeKeyboard = getKeyboard(mappedName).landscapeKeyboard
            if (landscapeKeyboard != null && landscapeKeyboard in allKeyboardIds) {
                mappedName = landscapeKeyboard
            }
        }
        // 应用键盘布局
        Timber.i(
            "Switched keyboard from $currentKeyboardId " +
                "to $mappedName (deviceKeyboard=$deviceKeyboard).",
        )
        currentKeyboardId =
            mappedName.apply {
                currentKeyboard = getKeyboard(this)
                lastKeyboardId = this
                if (currentKeyboard.isLock) lastLockKeyboardId = this
            }
    }

    /**
     * .default 自动匹配键盘布局
     * */
    private fun autoMatch(): String {
        // 主题的布局中包含方案id，直接采用
        val currentSchemaId = Rime.getCurrentRimeSchema()
        if (currentSchemaId in allKeyboardIds) {
            return currentSchemaId
        }
        // 获取方案中的 alphabet（包含所有用到的按键
        val alphabet = SchemaManager.getActiveSchema().alphabet
        if (alphabet.isNullOrEmpty()) return "default"

        val layout =
            if (alphabet.matches(Regex("^[a-z]+$"))) {
                // 包含 26 个字母
                "qwerty"
            } else if (alphabet.matches(Regex("^[a-z,./;]+$"))) {
                // 包含 26 个字母和,./;
                "qwerty_"
            } else if (alphabet.matches(Regex("^[a-z0-9]+$"))) {
                // 包含 26 个字母和数字键
                "qwerty0"
            } else {
                null
            }
        if (layout != null && layout in allKeyboardIds) return layout

        Timber.d("Could not find keyboard layout $layout, fallback to default")
        return "default"
    }
}
