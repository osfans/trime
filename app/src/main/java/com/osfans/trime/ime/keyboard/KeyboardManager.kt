package com.osfans.trime.ime.keyboard

import android.content.Context
import com.osfans.trime.setup.Config

class KeyboardManager(
    context: Context,
    private var currentDisplayWidth: Int = -1
) {
    private val keyboards: Array<Keyboard?>
    private val keyboardNames: List<String> = Config.get(context).keyboardNames

    private var currentId: Int = -1
    private var lastId: Int = 0
    private var lastLockId: Int = 0

    companion object {
        private var instance: KeyboardManager? = null

        @Synchronized
        fun init(context: Context, displayWidth: Int) {
            if (instance == null) {
                instance = KeyboardManager(context, displayWidth)
            } else if (instance!!.currentId >= 0 && instance!!.currentDisplayWidth == displayWidth) {
                return
            } else {
                instance!!.currentDisplayWidth = displayWidth
            }
        }

        @Synchronized
        fun getInstance(): KeyboardManager {
            val defaultInstance = instance
            if (defaultInstance != null) {
                return defaultInstance
            } else {
                throw UninitializedPropertyAccessException(
                    "${KeyboardManager::class.simpleName} has not been initialized previously. Make sure to call init() before using getInstance()."
                )
            }
        }

        @Synchronized
        fun getInstanceOrNull(): KeyboardManager? = instance
    }

    constructor(context: Context) : this(context, -1)
    init {
        instance = this
        keyboards = arrayOfNulls(keyboardNames.size)
        for (i in keyboardNames.indices) {
            keyboards[i] =
                Keyboard(context, keyboardNames[i])
        }
        // use default keyboard
        switchToKeyboardInternal(0)
    }

    /**
     * To get the current keyboard instance.
     * @return [Keyboard] if it exists or null.
     */
    val currentKeyboard: Keyboard? get() = keyboards[currentId]
    /** Keyboard switcher by name.
     * @param name the specified name of the keyboard,
     *             the preset possible values are:
     *             [.default], [.prev], [.next], [.last],
     *             [.last_lock] or [.ascii]
     */
    fun switchToSpecifiedKeyboard(name: String) {
        var id = if (currentId.isValidId()) currentId else 0
        if (name.isEmpty()) {
            if (keyboards[id]?.isLock == false) id = lastLockId
        } else {
            id = when (name) {
                ".default" -> 0 // to default keyboard
                ".prev" -> currentId - 1 // to previous keyboard
                ".next" -> currentId + 1 // to next keyboard
                ".last" -> lastId // to last used keyboard
                ".last_lock" -> lastLockId // to last lock keyboard
                // to english/latin keyboard
                ".ascii" -> if (keyboards[id]?.asciiKeyboard?.isEmpty() == false) {
                    keyboardNames.indexOf(keyboards[id]?.asciiKeyboard)
                } else id
                else -> keyboardNames.indexOf(name) // to other keyboard
            }
        }
        switchToKeyboardInternal(id)
    }

    val asciiMode: Boolean get() = currentKeyboard?.asciiMode == true

    private fun switchToKeyboardInternal(id: Int) {
        lastId = currentId
        if (lastId.isValidId() && keyboards[lastId]?.isLock == true) {
            lastLockId = lastId
        }
        currentId = if (id.isValidId()) id else 0
    }

    private fun Int.isValidId(): Boolean = this in keyboards.indices
}
