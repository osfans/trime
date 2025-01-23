/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.ComposingPopupWindow
import com.osfans.trime.util.monitorCursorAnchor

class InputDeviceManager {
    private var inputView: InputView? = null
    private var composingPopupWindow: ComposingPopupWindow? = null

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private fun setupInputViewCallback(isVirtual: Boolean) {
        inputView?.handleMessage = isVirtual
        inputView?.visibility = if (isVirtual) View.VISIBLE else View.GONE
    }

    private fun setupComposingPopupWindowCallback(isVirtual: Boolean) {
        val shouldSetupWindow = !isVirtual || candidatesMode == PopupCandidatesMode.ALWAYS_SHOW
        composingPopupWindow?.handleMessage = shouldSetupWindow
        composingPopupWindow?.useVirtualKeyboard = isVirtual
        // dismiss ComposingPopupWindow when entering virtual keyboard mode,
        // but preserve the visibility when entering physical keyboard mode (in case it's empty)
        if (!shouldSetupWindow) {
            composingPopupWindow?.dismiss()
        }
    }

    private fun setupComponentCallback(isVirtual: Boolean) {
        setupInputViewCallback(isVirtual)
        setupComposingPopupWindowCallback(isVirtual)
    }

    var isVirtualKeyboard = true
        private set(value) {
            field = value
            setupComponentCallback(value)
        }

    fun setComponents(
        inputView: InputView,
        compositionWindow: ComposingPopupWindow,
    ) {
        this.inputView = inputView
        this.composingPopupWindow = compositionWindow
        setupComponentCallback(this.isVirtualKeyboard)
    }

    private fun applyMode(
        service: TrimeInputMethodService,
        useVirtualKeyboard: Boolean,
    ) {
        if (useVirtualKeyboard == isVirtualKeyboard) {
            return
        }
        // monitor CursorAnchorInfo when switching to ComposingPopupWindow
        service.currentInputConnection.monitorCursorAnchor(!useVirtualKeyboard)
        isVirtualKeyboard = useVirtualKeyboard
    }

    private var startedInputView = false
    private var isNullInputType = true

    /**
     * @return should use virtual keyboard
     */
    fun evaluateOnStartInputView(
        info: EditorInfo,
        service: TrimeInputMethodService,
    ): Boolean {
        startedInputView = true
        isNullInputType = info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
        val useVirtualKeyboard =
            when (candidatesMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                PopupCandidatesMode.INPUT_DEVICE -> isVirtualKeyboard
                PopupCandidatesMode.ALWAYS_SHOW -> true
                PopupCandidatesMode.DISABLED -> true
            }
        applyMode(service, useVirtualKeyboard)
        return useVirtualKeyboard
    }

    /**
     * @return should force show input views
     */
    fun evaluateOnKeyDown(
        e: KeyEvent,
        service: TrimeInputMethodService,
    ): Boolean {
        if (startedInputView) {
            // filter out back/home/volume buttons
            if (e.isPrintingKey) {
                // evaluate virtual keyboard visibility when pressing physical keyboard while InputView visible
                evaluateOnKeyDownInner(service)
            }
            // no need to force show InputView since it's already visible
            return false
        } else {
            // force show InputView when focusing on text input (likely inputType is not TYPE_NULL)
            // and pressing any digit/letter/punctuation key on physical keyboard
            val showInputView = !isNullInputType && e.isPrintingKey
            if (showInputView) {
                evaluateOnKeyDownInner(service)
            }
            return showInputView
        }
    }

    private fun evaluateOnKeyDownInner(service: TrimeInputMethodService) {
        val useVirtualKeyboard =
            when (candidatesMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                PopupCandidatesMode.INPUT_DEVICE -> false
                PopupCandidatesMode.ALWAYS_SHOW -> false
                PopupCandidatesMode.DISABLED -> true
            }
        applyMode(service, useVirtualKeyboard)
    }

    fun evaluateOnViewClicked(service: TrimeInputMethodService) {
        val useVirtualKeyboard =
            when (candidatesMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                else -> true
            }
        applyMode(service, useVirtualKeyboard)
    }

    fun evaluateOnUpdateEditorToolType(
        toolType: Int,
        service: TrimeInputMethodService,
    ) {
        val useVirtualKeyboard =
            when (candidatesMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                PopupCandidatesMode.INPUT_DEVICE ->
                    toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_STYLUS
                PopupCandidatesMode.ALWAYS_SHOW -> false
                PopupCandidatesMode.DISABLED -> true
            }
        applyMode(service, useVirtualKeyboard)
    }

    fun onFinishInputView() {
        startedInputView = false
    }
}
