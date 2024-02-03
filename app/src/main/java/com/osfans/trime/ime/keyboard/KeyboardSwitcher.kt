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
    private var currentKeyboardId = ".default"
    private var lastKeyboardId = ".default"
    private var lastLockKeyboardId = ".default"
    private var currentDisplayWidth: Int = 0
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
        currentKeyboardId = ".default"
        lastKeyboardId = ".default"
        lastLockKeyboardId = ".default"
        currentDisplayWidth = 0
        keyboardCache.clear()
        switchKeyboard(currentKeyboardId)
    }

    fun switchKeyboard(name: String?) {
        val currentIdx = theme.allKeyboardIds.indexOf(currentKeyboardId)
        var mappedName =
            when (name) {
                ".default" -> autoMatch(name)
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
                    val asciiKeyboard = currentKeyboard.asciiKeyboard
                    if (asciiKeyboard != null && asciiKeyboard in allKeyboardIds) {
                        asciiKeyboard
                    } else {
                        currentKeyboardId
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
        currentKeyboardId = mappedName
        currentKeyboard = getKeyboard(currentKeyboardId)

        if (currentKeyboard.isLock) {
            lastLockKeyboardId = currentKeyboardId
        }
        lastKeyboardId = currentKeyboardId
    }

    /**
     * .default 自动匹配键盘布局
     * */
    private fun autoMatch(name: String): String {
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

    /**
     * Change current display width when e.g. rotate the screen.
     */
    fun resize(displayWidth: Int) {
        if (displayWidth == currentDisplayWidth) return
        currentDisplayWidth = displayWidth
        newOrReset()
    }
}
