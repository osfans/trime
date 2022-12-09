package com.osfans.trime.ime.keyboard

import android.content.res.Configuration
import com.blankj.utilcode.util.ScreenUtils
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.theme.Config
import com.osfans.trime.util.appContext
import timber.log.Timber

/** Manages [Keyboard]s and their status. **/
class KeyboardSwitcher {
    var currentId: Int = -1
    var lastId: Int = 0
    var lastLockId: Int = 0

    private var currentDisplayWidth: Int = 0

    private val theme = Config.get()
    val availableKeyboardNames = (theme.style.getObject("keyboards") as? List<String>)
        ?.map { theme.keyboards.getKeyboardName(it) }?.distinct() ?: listOf()
    val availableKeyboards = availableKeyboardNames.map { Keyboard(theme.keyboards.getKeyboardName(it)) }

    /** To get current keyboard instance. **/
    val currentKeyboard: Keyboard get() = availableKeyboards[currentId]
    /** To get [currentKeyboard]'s ascii mode. **/
    val asciiMode: Boolean get() = currentKeyboard.asciiMode

    init {
        newOrReset()
    }

    fun newOrReset() {
        val methodName = "\t<TrimeInit>\t" + Thread.currentThread().stackTrace[2].methodName + "\t"
        Timber.d(methodName)
        Timber.d(methodName + "getConfig")
        theme.getKeyboardPadding(ScreenUtils.isLandscape())
        Timber.d("update KeyboardPadding: KeyboardSwitcher.init")
        Timber.d(methodName + "setKeyboard")
        setKeyboard(0)
        Timber.d(methodName + "finish")
    }

    /**
     * Switch to a certain keyboard by given [name].
     */
    fun switchToKeyboard(name: String?) {
        var i = if (currentId.isValidId()) currentId else 0
        i = when {
            name.isNullOrEmpty() -> if (availableKeyboards[i].isLock) i else lastLockId
            name.contentEquals(".default") -> 0
            name.contentEquals(".prior") -> currentId - 1
            name.contentEquals(".next") -> currentId + 1
            name.contentEquals(".last") -> lastId
            name.contentEquals(".last_lock") -> lastLockId
            name.contentEquals(".ascii") -> {
                val asciiKeyboard = availableKeyboards[i].asciiKeyboard
                if (asciiKeyboard == null || asciiKeyboard.isEmpty()) { i } else { availableKeyboardNames.indexOf(asciiKeyboard) }
            }
            else -> availableKeyboardNames.indexOf(name)
        }
        setKeyboard(i)
    }
    /**
     * Switch to a certain keyboard by given [name].
     */
    fun startKeyboard(name: String?) {
        var i = if (currentId.isValidId()) currentId else 0
        i = when {
            name.isNullOrEmpty() -> if (availableKeyboards[i].isLock) i else lastLockId
            name.contentEquals(".default") -> 0
            name.contentEquals(".prior") -> currentId - 1
            name.contentEquals(".next") -> currentId + 1
            name.contentEquals(".last") -> lastId
            name.contentEquals(".last_lock") -> lastLockId
            name.contentEquals(".ascii") -> {
                val asciiKeyboard = availableKeyboards[i].asciiKeyboard
                if (asciiKeyboard == null || asciiKeyboard.isEmpty()) { i } else { availableKeyboardNames.indexOf(asciiKeyboard) }
            }
            else -> availableKeyboardNames.indexOf(name)
        }

        if (i == 0 && availableKeyboardNames.contains("mini")) {
            if (AppPrefs.defaultInstance().themeAndColor.useMiniKeyboard) {
                val realkeyboard = appContext.resources.configuration.keyboard
                if (realkeyboard != Configuration.KEYBOARD_NOKEYS) {
                    Timber.i("onStartInputView() configuration.keyboard=" + realkeyboard + ", keyboardType=" + i)
                    i = availableKeyboardNames.indexOf("mini")
                }
            }
        }

        setKeyboard(i)
    }

    /**
     * Change current display width when e.g. rotate the screen.
     */
    fun resize(displayWidth: Int) {
        if (currentId >= 0 && (displayWidth == currentDisplayWidth)) return

        currentDisplayWidth = displayWidth
        newOrReset()
    }

    private fun setKeyboard(id: Int) {
        Timber.d("\t<TrimeInit>\tsetKeyboard()\t" + currentId + "->" + id)
        lastId = currentId
        if (lastId.isValidId() && availableKeyboards[lastId].isLock) {
            lastLockId = lastId
        }
        currentId = if (id.isValidId()) id else 0
    }

    private fun Int.isValidId() = this in availableKeyboards.indices
}
