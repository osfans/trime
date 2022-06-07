package com.osfans.trime.ime.keyboard

import android.content.res.Configuration
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.Config
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.appContext
import timber.log.Timber

/** Manages [Keyboard]s and their status. **/
class KeyboardSwitcher {
    var currentId: Int = -1
    var lastId: Int = 0
    var lastLockId: Int = 0

    private var currentDisplayWidth: Int = 0

    lateinit var keyboards: Array<Keyboard>
    lateinit var keyboardNames: List<String>

    /** To get current keyboard instance. **/
    val currentKeyboard: Keyboard get() = keyboards[currentId]
    /** To get [currentKeyboard]'s ascii mode. **/
    val asciiMode: Boolean get() = currentKeyboard.asciiMode

    init {
        newOrReset()
    }

    fun newOrReset() {
        val methodName = "\t<TrimeInit>\t" + Thread.currentThread().stackTrace[2].methodName + "\t"
        Timber.d(methodName)
        val ims = Trime.getService()
        Timber.d(methodName + "getConfig")
        keyboardNames = Config.get(ims).keyboardNames
        Timber.d(methodName + "land")
        val land = (
            ims.resources.configuration.orientation
                == Configuration.ORIENTATION_LANDSCAPE
            )
        Timber.d(methodName + "getConfig")
        Config.get(ims).getKeyboardPadding(land)
        Timber.d("update KeyboardPadding: KeyboardSwitcher.init")

        Timber.d(methodName + "getKeyboards")
        keyboards = Array(keyboardNames.size) { i ->
            Keyboard(ims, keyboardNames[i])
        }
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
            name.isNullOrEmpty() -> if (keyboards[i].isLock) i else lastLockId
            name.contentEquals(".default") -> 0
            name.contentEquals(".prior") -> currentId - 1
            name.contentEquals(".next") -> currentId + 1
            name.contentEquals(".last") -> lastId
            name.contentEquals(".last_lock") -> lastLockId
            name.contentEquals(".ascii") -> {
                val asciiKeyboard = keyboards[i].asciiKeyboard
                if (asciiKeyboard == null || asciiKeyboard.isEmpty()) { i } else { keyboardNames.indexOf(asciiKeyboard) }
            }
            else -> keyboardNames.indexOf(name)
        }
        setKeyboard(i)
    }
    /**
     * Switch to a certain keyboard by given [name].
     */
    fun startKeyboard(name: String?) {
        var i = if (currentId.isValidId()) currentId else 0
        i = when {
            name.isNullOrEmpty() -> if (keyboards[i].isLock) i else lastLockId
            name.contentEquals(".default") -> 0
            name.contentEquals(".prior") -> currentId - 1
            name.contentEquals(".next") -> currentId + 1
            name.contentEquals(".last") -> lastId
            name.contentEquals(".last_lock") -> lastLockId
            name.contentEquals(".ascii") -> {
                val asciiKeyboard = keyboards[i].asciiKeyboard
                if (asciiKeyboard == null || asciiKeyboard.isEmpty()) { i } else { keyboardNames.indexOf(asciiKeyboard) }
            }
            else -> keyboardNames.indexOf(name)
        }

        if (i == 0 && keyboardNames.contains("mini")) {
            if (AppPrefs.defaultInstance().looks.useMiniKeyboard) {
                val realkeyboard = appContext.getResources().getConfiguration().keyboard
                if (realkeyboard != Configuration.KEYBOARD_NOKEYS) {
                    Timber.i("onStartInputView() configuration.keyboard=" + realkeyboard + ", keyboardType=" + i)
                    i = keyboardNames.indexOf("mini")
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
        if (lastId.isValidId() && keyboards[lastId].isLock) {
            lastLockId = lastId
        }
        currentId = if (id.isValidId()) id else 0
    }

    public fun getCurrentKeyboardName(): String {
        return keyboardNames.get(currentId)
    }

    private fun Int.isValidId() = this in keyboards.indices
}
