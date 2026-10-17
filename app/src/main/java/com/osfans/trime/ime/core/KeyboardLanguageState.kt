/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

/** Only explicit language actions persist this preference; layouts are resolved afresh. */
enum class ManualLanguageMode(val storedValue: String) {
    UNSET("unset"),
    NATIVE("native"),
    ASCII("ascii"),
    ;

    val asciiMode: Boolean?
        get() = when (this) {
            UNSET -> null
            NATIVE -> false
            ASCII -> true
        }

    companion object {
        fun fromStored(value: String): ManualLanguageMode? = entries.firstOrNull { it.storedValue == value }

        fun fromAscii(value: Boolean): ManualLanguageMode = if (value) ASCII else NATIVE
    }
}

internal enum class KeyboardModeEffect {
    COMMIT,
    COMMIT_ON_CHANGE,
    PRESERVE_COMPOSITION,
    MODE_ONLY,
}

/** An accepted native command is immutable. UI revisions never cancel a valid native command. */
internal data class KeyboardModeCommand(
    val editorToken: Long,
    val schemaId: String,
    val revision: Long,
    val asciiMode: Boolean,
    val effect: KeyboardModeEffect,
    val remembersLanguage: Boolean,
) {
    fun acceptsNative(editor: Long, schema: String): Boolean = editorToken > 0 && editorToken == editor && schemaId == schema
}

internal data class KeyboardModeResult(val applied: Boolean, val asciiMode: Boolean)

/** Main-thread intent state. The service's existing FIFO owns all native operations. */
internal class KeyboardLanguageState(initial: ManualLanguageMode = ManualLanguageMode.UNSET) {
    var remembered: ManualLanguageMode = initial
        private set

    private var editorToken = 0L
    private var schemaId = ""
    private var revision = 0L
    private var settledRevision = 0L
    private var rememberedRevision = 0L
    private var desiredAsciiMode: Boolean? = null
    private var confirmedRemembered = initial

    fun reloadRemembered(value: ManualLanguageMode) {
        remembered = value
        confirmedRemembered = value
    }

    fun plannedAscii(editor: Long, schema: String, nativeAscii: Boolean): Boolean = if (editorToken == editor && schemaId == schema) desiredAsciiMode ?: nativeAscii else nativeAscii

    fun accept(
        editor: Long,
        schema: String,
        nativeAscii: Boolean,
        targetAscii: Boolean?,
        restricted: Boolean,
        rememberLanguage: Boolean,
        effect: KeyboardModeEffect,
    ): KeyboardModeCommand {
        if (editorToken != editor || schemaId != schema) {
            editorToken = editor
            schemaId = schema
            settledRevision = revision
            desiredAsciiMode = nativeAscii
        }
        val ascii = if (restricted) true else targetAscii ?: !plannedAscii(editor, schema, nativeAscii)
        val remember = rememberLanguage && !restricted
        revision += 1
        desiredAsciiMode = ascii
        if (remember) {
            remembered = ManualLanguageMode.fromAscii(ascii)
            rememberedRevision = revision
        }
        return KeyboardModeCommand(editor, schema, revision, ascii, effect, remember)
    }

    fun acceptsUi(command: KeyboardModeCommand, editor: Long, schema: String): Boolean = command.acceptsNative(editor, schema) && editorToken == editor && schemaId == schema && command.revision == revision

    /** Returns whether this completion may repaint. A failed latest manual action rolls back its preference. */
    fun complete(command: KeyboardModeCommand, result: KeyboardModeResult, editor: Long, schema: String): Boolean {
        if (!command.acceptsNative(editor, schema) || editorToken != editor || schemaId != schema) return false
        if (command.revision <= settledRevision) return false
        settledRevision = maxOf(settledRevision, command.revision)
        if (result.applied && command.remembersLanguage) {
            confirmedRemembered = ManualLanguageMode.fromAscii(command.asciiMode)
        }
        if (!result.applied && command.remembersLanguage && rememberedRevision == command.revision) {
            remembered = confirmedRemembered
        }
        if (!acceptsUi(command, editor, schema)) return false
        desiredAsciiMode = result.asciiMode
        return true
    }

    /** An old OptionMessage must not replace a newer accepted intent. Read the current cache, not its payload. */
    fun synchronizeNative(editor: Long, schema: String, nativeAscii: Boolean): Boolean? {
        if (editorToken != editor || schemaId != schema || settledRevision < revision) return null
        desiredAsciiMode = nativeAscii
        return nativeAscii
    }
}

/** Uses current resolver outputs, never a persisted layout ID from another schema/theme. */
internal fun rememberedMainKeyboard(
    remembered: ManualLanguageMode,
    nativeKeyboard: String,
    asciiKeyboard: String,
    legacyKeyboard: String,
): String = when (remembered) {
    ManualLanguageMode.UNSET -> legacyKeyboard
    ManualLanguageMode.NATIVE -> nativeKeyboard
    ManualLanguageMode.ASCII -> asciiKeyboard
}

/** A temporary snapshot can retain its layout only within the same loaded theme and schema. */
internal fun canReuseMainLayout(
    savedSchema: String,
    savedThemeIdentity: Int,
    savedKeyboard: String,
    currentSchema: String,
    currentThemeIdentity: Int,
    availableKeyboards: Collection<String>,
): Boolean = savedSchema == currentSchema && savedThemeIdentity != 0 &&
    savedThemeIdentity == currentThemeIdentity && savedKeyboard in availableKeyboards

/** Called inside postRimeJob. Its identity guard deliberately does not inspect the latest UI revision. */
internal suspend fun applyKeyboardMode(
    command: KeyboardModeCommand,
    currentEditor: () -> Long,
    currentSchema: () -> String,
    composing: () -> Boolean,
    asciiMode: () -> Boolean,
    commitComposition: suspend () -> Boolean,
    setAsciiMode: suspend (Boolean) -> Unit,
): KeyboardModeResult {
    fun rejected() = KeyboardModeResult(false, asciiMode())
    if (!command.acceptsNative(currentEditor(), currentSchema())) return rejected()
    when (command.effect) {
        KeyboardModeEffect.COMMIT, KeyboardModeEffect.COMMIT_ON_CHANGE -> {
            // Native Rime chooses the text, including T9's raw-to-syllable mapping.
            if (command.effect == KeyboardModeEffect.COMMIT || asciiMode() != command.asciiMode) {
                if (!commitComposition() && composing()) return rejected()
            }
        }

        KeyboardModeEffect.PRESERVE_COMPOSITION -> {
            if (composing()) return KeyboardModeResult(true, asciiMode())
        }

        KeyboardModeEffect.MODE_ONLY -> {
            // A restore must not clear composition left intact by a failed earlier language commit.
            if (composing() && asciiMode() != command.asciiMode) return rejected()
        }
    }
    if (!command.acceptsNative(currentEditor(), currentSchema())) return rejected()
    if (command.effect == KeyboardModeEffect.COMMIT || asciiMode() != command.asciiMode) {
        setAsciiMode(command.asciiMode)
    }
    return KeyboardModeResult(true, command.asciiMode)
}
