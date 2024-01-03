package com.osfans.trime.ime.core

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.ime.enums.InlineModeType
import com.osfans.trime.ime.text.TextInputManager
import timber.log.Timber

class EditorInstance(private val ims: InputMethodService) {
    val prefs get() = AppPrefs.defaultInstance()
    val inputConnection: InputConnection?
        get() = ims.currentInputConnection
    val editorInfo: EditorInfo?
        get() = ims.currentInputEditorInfo
    val cursorCapsMode: Int
        get() {
            val ic = inputConnection ?: return 0
            val ei = editorInfo ?: return 0
            return if (ei.inputType != EditorInfo.TYPE_NULL) {
                ic.getCursorCapsMode(ei.inputType)
            } else {
                0
            }
        }
    val textInputManager: TextInputManager
        get() = (ims as Trime).textInputManager

    var lastCommittedText: CharSequence = ""

    // 直接commit不做任何处理
    fun commitText(
        text: CharSequence,
        clearMeatKeyState: Boolean = false,
    ): Boolean {
        val ic = inputConnection ?: return false
        ic.commitText(text, 1)
        if (text.isNotEmpty()) {
            lastCommittedText = text
        }
        if (clearMeatKeyState) {
            ic.clearMetaKeyStates(KeyEvent.getModifierMetaStateMask())
            DraftHelper.onInputEventChanged()
        }
        return true
    }

    /**
     * Commits the text got from Rime.
     */
    fun commitRimeText(): Boolean {
        val commit = Rime.getRimeCommit()
        commit?.let { commitText(it.commitText) }
        Timber.i("\t<TrimeInput>\tcommitRimeText()\tupdateComposing")
        (ims as Trime).updateComposing()
        return commit != null
    }

    fun updateComposingText() {
        val ic = inputConnection ?: return
        val composingText =
            when (prefs.keyboard.inlinePreedit) {
                InlineModeType.INLINE_PREVIEW -> Rime.composingText
                InlineModeType.INLINE_COMPOSITION -> Rime.compositionText
                InlineModeType.INLINE_INPUT -> Rime.getRimeRawInput() ?: ""
                else -> ""
            }
        if (ic.getSelectedText(0).isNullOrEmpty() || composingText.isNotEmpty()) {
            ic.setComposingText(composingText, 1)
        }
    }

    /**
     * Gets [n] characters after the cursor's current position. The resulting string may be any
     * length ranging from 0 to n.
     *
     * @param n The number of characters to get after the cursor. Must be greater than 0 or this
     *  method will fail.
     * @return [n] or less characters after the cursor.
     */
    fun getTextAfterCursor(n: Int): String {
        val ic = inputConnection
        if (ic == null || n < 1) {
            return ""
        }
        return ic.getTextAfterCursor(n, 0)?.toString() ?: ""
    }

    /**
     * Gets [n] characters before the cursor's current position. The resulting string may be any
     * length ranging from 0 to n.
     *
     * @param n The number of characters to get before the cursor. Must be greater than 0 or this
     *  method will fail.
     * @return [n] or less characters before the cursor.
     */
    fun getTextBeforeCursor(n: Int): String {
        val ic = inputConnection
        if (ic == null || n < 1) {
            return ""
        }
        return ic.getTextBeforeCursor(n.coerceAtMost(1024), 0)?.toString() ?: ""
    }

    /** 獲得當前漢字：候選字、選中字、剛上屏字/光標前字/光標前所有字、光標後所有字
     * %s或者%1$s爲當前字符
     * %2$s爲當前輸入的編碼
     * %3$s爲光標前字符
     * %4$s爲光標前所有字符
     * */
    fun getActiveText(type: Int): String {
        if (type == 2) return Rime.getRimeRawInput() ?: "" // 當前編碼
        var s = Rime.composingText // 當前候選
        if (TextUtils.isEmpty(s)) {
            val ic = inputConnection
            var cs = if (ic != null) ic.getSelectedText(0) else null // 選中字
            if (type == 1 && TextUtils.isEmpty(cs)) cs = lastCommittedText // 剛上屏字
            if (TextUtils.isEmpty(cs) && ic != null) {
                cs = ic.getTextBeforeCursor(if (type == 4) 1024 else 1, 0) // 光標前字
            }
            if (TextUtils.isEmpty(cs) && ic != null) cs = ic.getTextAfterCursor(1024, 0) // 光標後面所有字
            if (cs != null) s = cs.toString()
        }
        return s
    }

    /**
     * Constructs a meta state integer flag which can be used for setting the `metaState` field when sending a KeyEvent
     * to the input connection. If this method is called without a meta modifier set to true, the default value `0` is
     * returned.
     *
     * @param ctrl Set to true to enable the CTRL meta modifier. Defaults to false.
     * @param alt Set to true to enable the ALT meta modifier. Defaults to false.
     * @param shift Set to true to enable the SHIFT meta modifier. Defaults to false.
     *
     * @return An integer containing all meta flags passed and formatted for use in a [KeyEvent].
     */
    fun meta(
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        sym: Boolean = false,
    ): Int {
        var metaState = 0
        if (ctrl) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (alt) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        if (shift) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        if (meta) {
            metaState = metaState or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        }
        if (sym) {
            metaState = metaState or KeyEvent.META_SYM_ON
        }

        return metaState
    }

    private fun sendDownKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int,
    ): Boolean {
        val ic = inputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    private fun sendUpKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int,
    ): Boolean {
        val ic = inputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    /**
     * Same as [InputMethodService.sendDownUpKeyEvents] but also allows to set meta state.
     *
     * @param keyEventCode The key code to send, use a key code defined in Android's [KeyEvent].
     * @param metaState Flags indicating which meta keys are currently pressed.
     * @param count How often the key is pressed while the meta keys passed are down. Must be greater than or equal to
     *  `1`, else this method will immediately return false.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun sendDownUpKeyEvent(
        keyEventCode: Int,
        metaState: Int = meta(),
        count: Int = 1,
    ): Boolean {
        if (count < 1) return false
        val ic = inputConnection ?: return false
        ic.clearMetaKeyStates(
            KeyEvent.META_FUNCTION_ON
                or KeyEvent.META_SHIFT_MASK
                or KeyEvent.META_ALT_MASK
                or KeyEvent.META_CTRL_MASK
                or KeyEvent.META_META_MASK
                or KeyEvent.META_SYM_ON,
        )
        ic.beginBatchEdit()
        val eventTime = SystemClock.uptimeMillis()
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT, 0)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT, 0)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT, 0)
        }
        if (metaState and KeyEvent.META_META_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT, 0)
        }

        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SYM, 0)
        }

        for (n in 0 until count) {
            sendDownKeyEvent(eventTime, keyEventCode, metaState)
            sendUpKeyEvent(eventTime, keyEventCode, metaState)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT, 0)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT, 0)
        }
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT, 0)
        }

        if (metaState and KeyEvent.META_META_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT, 0)
        }

        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SYM, 0)
        }

        ic.endBatchEdit()
        return true
    }
}
