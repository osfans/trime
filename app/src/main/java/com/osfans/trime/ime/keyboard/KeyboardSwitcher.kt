package com.osfans.trime.ime.keyboard

import android.content.res.Configuration
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.appContext
import com.osfans.trime.util.config.ConfigMap
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import timber.log.Timber

/** Manages [Keyboard]s and their status. **/
object KeyboardSwitcher {
    var currentId: Int = 0
    var lastId: Int = 0
    var lastLockId: Int = 0

    private var currentDisplayWidth: Int = 0

    private val theme = ThemeManager.getActiveTheme()
    private val availableKeyboardIds = theme.o("style/keyboards")?.decode(ListSerializer(String.serializer()))
        ?.map { remapKeyboardId(it) }?.distinct() ?: listOf()
    private val availableKeyboards = availableKeyboardIds
        .mapNotNull decode@{
            val keyboard = runCatching {
                Keyboard(theme, remapKeyboardId(it))
            }.getOrElse { e ->
                Timber.w("Failed to decode keyboard definition $it: ${e.message}")
                return@decode null
            }
            return@decode keyboard
        }

    /** To get current keyboard instance. **/
    @JvmStatic
    val currentKeyboard: Keyboard get() = availableKeyboards[currentId]

    fun switchKeyboard(name: String?) {
        val i = when (name) {
            ".default" -> 0
            ".prior" -> currentId - 1
            ".next" -> currentId + 1
            ".last" -> lastId
            ".last_lock" -> lastLockId
            ".ascii" -> {
                val asciiKeyboard = availableKeyboards[currentId].asciiKeyboard
                if (asciiKeyboard.isNullOrEmpty()) { currentId } else { availableKeyboardIds.indexOf(asciiKeyboard) }
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
        var mini = -1
        if (AppPrefs.defaultInstance().themeAndColor.useMiniKeyboard) {
            if (i == 0 && "mini" in availableKeyboardIds) {
                if (deviceKeyboard != Configuration.KEYBOARD_NOKEYS) {
                    mini = availableKeyboardIds.indexOf("mini")
                }
            }
        }

        if (currentId >= 0 && availableKeyboards[currentId].isLock) {
            lastLockId = currentId
        }
        lastId = currentId
        currentId = if (mini >= 0) mini else i
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
    }

    private fun remapKeyboardId(name: String): String {
        val presetKeyboards = theme.o("preset_keyboards")?.configMap
        val remapped = if (".default" == name) {
            val currentSchemaId = Rime.getCurrentRimeSchema()
            val shortSchemaId = currentSchemaId.split('_')[0]
            if (presetKeyboards?.containsKey(shortSchemaId) == true) {
                return shortSchemaId
            } else {
                val alphabet = SchemaManager.getActiveSchema().alphabet
                val twentySix = "qwerty"
                if (!alphabet.isNullOrEmpty() && presetKeyboards?.containsKey(alphabet) == true) {
                    return alphabet
                } else {
                    if (!alphabet.isNullOrEmpty() && (alphabet.contains(",") || alphabet.contains(";"))) {
                        twentySix + "_"
                    } else if (!alphabet.isNullOrEmpty() && (alphabet.contains("0") || alphabet.contains("1"))) {
                        twentySix + "0"
                    } else {
                        twentySix
                    }
                }
            }
        } else {
            name
        }
        if (presetKeyboards?.containsKey(remapped) == false) {
            Timber.w("Cannot find keyboard definition '$remapped', fallback to default")
            val defaultMap = presetKeyboards.get<ConfigMap>("default")
                ?: throw IllegalStateException("The default keyboard definition is missing!")
            if (defaultMap.containsKey("import_preset")) {
                return defaultMap.getValue("import_preset")?.getString() ?: "default"
            }
        }
        return remapped
    }
}
