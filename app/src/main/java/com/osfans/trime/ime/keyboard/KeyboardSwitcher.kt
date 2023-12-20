package com.osfans.trime.ime.keyboard

import android.content.res.Configuration
import com.blankj.utilcode.util.ScreenUtils
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.appContext
import timber.log.Timber

/** Manages [Keyboard]s and their status. **/
object KeyboardSwitcher {
    var currentId: Int = 0
    var lastId: Int = 0
    var lastLockId: Int = 0

    private var currentDisplayWidth: Int = 0

    private val theme = Theme.get()
    lateinit var availableKeyboardIds: List<String>
    lateinit var availableKeyboards: List<Keyboard>

    /** To get current keyboard instance. **/
    @JvmStatic
    val currentKeyboard: Keyboard get() = availableKeyboards[currentId]

    init {
        newOrReset()
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun newOrReset() {
        Timber.d("Refreshing keyboard padding ...")
        theme.getKeyboardPadding(ScreenUtils.isLandscape())
        Timber.d("Switching keyboard back to .default ...")
        availableKeyboardIds = (theme.style.getObject("keyboards") as? List<String>)
            ?.map { theme.keyboards.remapKeyboardId(it) }?.distinct() ?: listOf()

        availableKeyboards =
            availableKeyboardIds.map {
                try {
                    Keyboard(theme.keyboards.remapKeyboardId(it))
                } catch (e: Exception) {
                    Keyboard("default")
                }
            }
    }

    fun switchKeyboard(name: String?) {
        val i =
            when (name) {
                ".default" -> 0
                ".prior" -> currentId - 1
                ".next" -> currentId + 1
                ".last" -> lastId
                ".last_lock" -> lastLockId
                ".ascii" -> {
                    val asciiKeyboard = availableKeyboards[currentId].asciiKeyboard
                    if (asciiKeyboard.isNullOrEmpty()) {
                        currentId
                    } else {
                        availableKeyboardIds.indexOf(asciiKeyboard)
                    }
                }
                else -> {
                    if (name.isNullOrEmpty()) {
                        if (availableKeyboards[currentId].isLock) currentId else lastLockId
                    } else {
                        availableKeyboardIds.indexOf(name)
                    }
                }
            }

        val deviceKeyboard = appContext.resources.configuration.keyboard
        if (currentId >= 0 && availableKeyboards[currentId].isLock) {
            lastLockId = currentId
        }
        lastId = currentId

        currentId = if (i >= availableKeyboardIds.size) 0 else i
        if ("mini" in availableKeyboardIds) {
            val mini = availableKeyboardIds.indexOf("mini")
            currentId =
                if (AppPrefs.defaultInstance().themeAndColor.useMiniKeyboard && deviceKeyboard != Configuration.KEYBOARD_NOKEYS) {
                    if (currentId == 0) mini else currentId
                } else {
                    if (currentId == mini) 0 else currentId
                }
        }

        Timber.i(
            "Switched keyboard from ${availableKeyboardIds[lastId]} " +
                "to ${availableKeyboardIds[currentId]} (deviceKeyboard=$deviceKeyboard).",
        )
    }

    /**
     * Change current display width when e.g. rotate the screen.
     */
    fun resize(displayWidth: Int) {
        if (currentId >= 0 && (displayWidth == currentDisplayWidth)) return

        currentDisplayWidth = displayWidth
        newOrReset()
    }
}
