package com.osfans.trime.ime.keyboard

import android.content.res.Configuration
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.setup.Config
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
        val ims = Trime.getService()
        keyboardNames = Config.get(ims).keyboardNames

        val land = (
            ims.resources.configuration.orientation
                == Configuration.ORIENTATION_LANDSCAPE
            )
        Config.get(ims).getKeyboardPadding(land)
        Timber.d("update KeyboardPadding: KeyboardSwitcher.init")

        keyboards = Array(keyboardNames.size) { i ->
            Keyboard(ims, keyboardNames[i])
        }
        setKeyboard(0)
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
     * Change current display width when e.g. rotate the screen.
     */
    fun resize(displayWidth: Int) {
        if (currentId >= 0 && (displayWidth == currentDisplayWidth)) return

        currentDisplayWidth = displayWidth
        newOrReset()
    }

    private fun setKeyboard(id: Int) {
        lastId = currentId
        if (lastId.isValidId() && keyboards[lastId].isLock) {
            lastLockId = lastId
        }
        currentId = if (id.isValidId()) id else 0
    }

    private fun Int.isValidId() = this in keyboards.indices
}
