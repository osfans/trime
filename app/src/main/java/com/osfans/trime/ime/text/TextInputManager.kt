package com.osfans.trime.ime.text

import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import com.osfans.trime.Rime
import com.osfans.trime.common.startsWithAsciiChar
import com.osfans.trime.databinding.InputRootBinding
import com.osfans.trime.ime.core.EditorInstance
import com.osfans.trime.ime.core.Preferences
import com.osfans.trime.ime.core.Speech
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.keyboard.Event
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.keyboard.KeyboardView
import com.osfans.trime.util.ShortcutUtils
import com.osfans.trime.util.StringUtils.findNextSection
import com.osfans.trime.util.StringUtils.findPrevSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import timber.log.Timber

/**
 * TextInputManager is responsible for managing everything which is related to text input. All of
 * the following count as text input: character, numeric (+advanced), phone and symbol layouts.
 *
 * All of the UI for the different keyboard layouts are kept under the same container element and
 * are separated from media-related UI. The core [Trime] will pass any event defined in
 * [Trime.EventListener] through to this class.
 *
 * TextInputManager is also the hub in the communication between the system, the active editor
 * instance and the CandidateView.
 */
class TextInputManager private constructor() :
    CoroutineScope by MainScope(),
    Trime.EventListener,
    KeyboardView.OnKeyboardActionListener,
    Candidate.EventListener {

    private val trime get() = Trime.getService()
    private val prefs get() = Preferences.defaultInstance()
    private val activeEditorInstance: EditorInstance
        get() = trime.activeEditorInstance
    private val keyboardSwitcher: KeyboardSwitcher
        get() = trime.keyboardSwitcher
    private val imeManager: InputMethodManager
        get() = trime.imeManager

    private var mainKeyboardView: KeyboardView? = null

    var candidateView: Candidate? = null
    var needSendUpRimeKey: Boolean = false
    var shouldUpdateRimeOption: Boolean = true
    var performEnterAsLineBreak: Boolean = false
    var isComposable: Boolean = false
    var isAsciiMode: Boolean = true
    var isTempAsciiMode: Boolean = true
    var shouldResetAsciiMode: Boolean = false

    companion object {
        /** Delimiter regex for key property group, their format like `{property_1: value_1, property_2: value_2}` */
        private val DELIMITER_PROPERTY_GROUP = """^(\{[^{}]+\}).*$""".toRegex()
        /** Delimiter regex for property key tag, its format like `Escape: ` following a property group like above */
        private val DELIMITER_PROPERTY_KEY = """^((\{Escape\})?[^{}]+).*$""".toRegex()
        private var instance: TextInputManager? = null

        @Synchronized
        fun getInstance(): TextInputManager {
            if (instance == null) {
                instance = TextInputManager()
            }
            return instance!!
        }
    }

    init {
        trime.addEventListener(this)
    }

    override fun onInitializeInputUi(uiBinding: InputRootBinding) {
        super.onInitializeInputUi(uiBinding)

        mainKeyboardView = uiBinding.main.mainKeyboardView.also {
            it.setOnKeyboardActionListener(this)
            it.setShowHint(!Rime.getOption("_hide_key_hint"))
            it.reset(trime)
        }

        candidateView = uiBinding.main.candidateView.candidates.also {
            it.setCandidateListener(this)
            it.setShowComment(!Rime.getOption("_hide_comment"))
            it.reset(trime)
        }
    }

    override fun onDestroy() {
        candidateView?.setCandidateListener(null)
        candidateView = null

        mainKeyboardView?.setOnKeyboardActionListener(null)
        mainKeyboardView = null

        cancel()
        instance = null
    }

    override fun onStartInputView(instance: EditorInstance, restarting: Boolean) {
        super.onStartInputView(instance, restarting)
        isComposable = false
        performEnterAsLineBreak = false
        var tempAsciiMode = false
        val inputAttrsRaw = instance.editorInfo!!.inputType
        val keyboardType = when (inputAttrsRaw and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> {
                "number"
            }
            InputType.TYPE_CLASS_TEXT -> {
                when (inputAttrsRaw and InputType.TYPE_MASK_VARIATION) {
                    InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> {
                        null.also { performEnterAsLineBreak = true }
                    }
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> {
                        tempAsciiMode = true
                        ".ascii"
                    }
                    else -> null.also { isComposable = true }
                }
            }
            else -> {
                if (inputAttrsRaw <= 0) return
                null.also { isComposable = inputAttrsRaw > 0 }
            }
        }
        keyboardSwitcher.let {
            it.resize(trime.maxWidth)
            // Select a keyboard based on the input type of the editing field.
            it.switchToKeyboard(keyboardType)
        }
        Rime.get(trime)
        Rime.setOption("ascii_mode", tempAsciiMode)
        isComposable = isComposable && !Rime.isEmpty()
        if (!trime.onEvaluateInputViewShown()) {
            // Show candidate view when using physical keyboard
            trime.setCandidatesViewShown(isComposable)
        }
    }

    override fun onPress(keyEventCode: Int) {
        trime.inputFeedbackManager?.let {
            it.keyPressVibrate()
            it.keyPressSound(keyEventCode)
            it.keyPressSpeak(keyEventCode)
        }
    }

    override fun onRelease(keyEventCode: Int) {
        if (needSendUpRimeKey) {
            if (shouldUpdateRimeOption) {
                Rime.setOption("soft_cursors", prefs.keyboard.softCursorEnabled)
                Rime.setOption("_horizontal", trime.imeConfig.getBoolean("horizontal"))
                shouldUpdateRimeOption = false
            }
            Rime.onKey(Event.getRimeEvent(keyEventCode, Rime.META_RELEASE_ON))
            activeEditorInstance.commitTextFromRime()
        }
    }

    override fun onEvent(event: Event?) {
        event ?: return
        if (!event.commit.isNullOrEmpty()) {
            // Directly commit the text and don't dispatch to Rime
            activeEditorInstance.commitText(event.commit, false)
            return
        }
        if (!event.text.isNullOrEmpty()) {
            onText(event.text)
            return
        }
        when (event.code) {
            KeyEvent.KEYCODE_SWITCH_CHARSET -> { // Switch status
                Rime.toggleOption(event.toggle)
                activeEditorInstance.commitTextFromRime()
            }
            KeyEvent.KEYCODE_EISU -> { // Switch keyboard
                keyboardSwitcher.switchToKeyboard(event.select)
                /** Set ascii mode according to keyboard's settings, can not place into [Rime.onMessage] */
                isTempAsciiMode = keyboardSwitcher.asciiMode
                Rime.setOption("ascii_mode", isTempAsciiMode)
                trime.bindKeyboardToInputView()
                trime.updateComposing()
            }
            KeyEvent.KEYCODE_LANGUAGE_SWITCH -> { // Switch IME
                when {
                    event.select!!.contentEquals(".next") -> {
                        trime.switchToNextIme()
                    }
                    event.select.isNotEmpty() -> {
                        trime.switchToPrevIme()
                    }
                    else -> {
                        imeManager.showInputMethodPicker()
                    }
                }
            }
            KeyEvent.KEYCODE_FUNCTION -> { // Command Express
                val arg = String.format(
                    event.option,
                    activeEditorInstance.lastCommittedText,
                    Rime.RimeGetInput(),
                    "TODO()",
                    activeEditorInstance.getTextBeforeCursor(1024)
                )
                if (event.command == "liquid_keyboard") {
                    trime.selectLiquidKeyboard(arg)
                } else {
                    val textFromCommand = ShortcutUtils.call(trime, event.command, arg) as? String
                    if (textFromCommand != null) {
                        activeEditorInstance.commitText(textFromCommand)
                        trime.updateComposing()
                    }
                }
            }
            KeyEvent.KEYCODE_VOICE_ASSIST -> Speech(trime).startListening() // Speech Recognition
            KeyEvent.KEYCODE_SETTINGS -> { // Settings
                when (event.option) {
                    "theme" -> trime.showThemeDialog()
                    "color" -> trime.showColorDialog()
                    "schema" -> trime.showSchemaDialog()
                    else -> trime.launchSettings()
                }
            }
            KeyEvent.KEYCODE_PROG_RED -> trime.showColorDialog() // Color schemes
            else -> onKey(event.code, event.mask)
        }
    }

    override fun onKey(keyEventCode: Int, metaState: Int) {
        if (trime.handleKey(keyEventCode, metaState)) return
        if (keyEventCode >= Key.getSymbolStart()) {
            needSendUpRimeKey = false
            activeEditorInstance.commitText(Event.getDisplayLabel(keyEventCode))
            return
        }
        needSendUpRimeKey = false
        activeEditorInstance.sendDownUpKeyEvent(keyEventCode, metaState)
    }

    override fun onText(text: CharSequence?) {
        text ?: return
        if (!text.startsWithAsciiChar() && Rime.isComposing()) {
            Rime.commitComposition()
            activeEditorInstance.commitTextFromRime()
        }
        var textToParse = text
        while (textToParse!!.isNotEmpty()) {
            var target: String
            val escapeTagMatcher = DELIMITER_PROPERTY_KEY.toPattern().matcher(text)
            val propertyGroupMatcher = DELIMITER_PROPERTY_GROUP.toPattern().matcher(text)
            when {
                escapeTagMatcher.matches() -> {
                    target = escapeTagMatcher.group(1) ?: ""
                    Rime.onText(target)
                    if (!activeEditorInstance.commitTextFromRime() && !Rime.isComposing()) {
                        activeEditorInstance.commitText(target)
                    }
                    trime.updateComposing()
                }
                propertyGroupMatcher.matches() -> {
                    target = propertyGroupMatcher.group(1) ?: ""
                    onEvent(Event(target))
                }
                else -> {
                    target = text.substring(0..1)
                    onEvent(Event(target))
                }
            }
            textToParse = textToParse.substring(target.length)
        }
        needSendUpRimeKey = false
    }

    /**
     * Commits the pressed candidate and suggest the following words.
     */
    override fun onCandidatePressed(index: Int) {
        onPress(0)
        if (!Rime.isComposing()) {
            if (index >= 0) {
                Rime.toggleOption(index)
                trime.updateComposing()
            }
        } else if (prefs.other.clickCandidateAndCommit || index > 9) {
            if (Rime.selectCandidate(index)) {
                activeEditorInstance.commitTextFromRime()
            }
        } else if (index == 9) {

            trime.handleKey(KeyEvent.KEYCODE_0, 0)
        } else {
            trime.handleKey(KeyEvent.KEYCODE_1 + index, 0)
        }
    }

    override fun onCandidateSymbolPressed(arrow: String) {
        when (arrow) {
            Candidate.PAGE_UP_BUTTON -> onKey(KeyEvent.KEYCODE_PAGE_UP, 0)
            Candidate.PAGE_DOWN_BUTTON -> onKey(KeyEvent.KEYCODE_PAGE_DOWN, 0)
        }
    }

    /**
     * Handles edit actions, like Ctrl + V to paste, etc.
     */
    fun handleEditorAction(code: Int, metaState: Int) = activeEditorInstance.apply {
        Timber.i("Trime onKey: Handling edit actions...")
        val ic = inputConnection ?: return@apply
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                when (code) {
                    KeyEvent.KEYCODE_V -> { // Paste
                        if (metaState and KeyEvent.META_ALT_ON != 0 &&
                            metaState and KeyEvent.META_SHIFT_ON != 0
                        ) {
                            ic.performContextMenuAction(android.R.id.pasteAsPlainText)
                        }
                    }
                    KeyEvent.KEYCODE_S -> { // Share selected text
                        if (metaState and KeyEvent.META_ALT_ON != 0) {
                            ic.getSelectedText(0) ?: ic.performEditorAction(android.R.id.selectAll)
                            ic.performContextMenuAction(android.R.id.shareText)
                        }
                    }
                    KeyEvent.KEYCODE_Y -> {
                        ic.performContextMenuAction(android.R.id.redo)
                    }
                    KeyEvent.KEYCODE_Z -> {
                        ic.performContextMenuAction(android.R.id.undo)
                    }
                }
            }
        }
        when (code) {
            KeyEvent.KEYCODE_A -> ic.performContextMenuAction(android.R.id.selectAll)
            KeyEvent.KEYCODE_X -> ic.performContextMenuAction(android.R.id.cut)
            KeyEvent.KEYCODE_C -> ic.performContextMenuAction(android.R.id.copy)
            KeyEvent.KEYCODE_V -> ic.performContextMenuAction(android.R.id.paste)
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val et = ic.getExtractedText(ExtractedTextRequest().apply { token = 0 }, 0)
                val prevSection = et.text.findPrevSection(et.startOffset + et.selectionStart)
                ic.setSelection(prevSection, prevSection)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val et = ic.getExtractedText(ExtractedTextRequest().apply { token = 0 }, 0)
                val nextSection = et.text.findNextSection(et.startOffset + et.selectionEnd)
                ic.setSelection(nextSection, nextSection)
            }
        }
    }

    /**
     * Handles [KeyEvent] arrow and move key events
     */
    fun handleArrow(code: Int, count: Int = 1) = activeEditorInstance.apply {
        Timber.i("Trime onKey: Handling arrow actions...")
        val isShiftPressed = mainKeyboardView?.isShifted == true
        when (code) {
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT -> {
                sendDownUpKeyEvent(code, meta(shift = isShiftPressed), count)
            }
        }
    }

    /**
     * Handles [KeyEvent.KEYCODE_MENU] events
     */
    fun handleMenu(code: Int) {
        Timber.i("Trime onKey: Handling menu key...")
        if (code == KeyEvent.KEYCODE_MENU) {
            trime.showOptionsDialog()
        }
    }
}
