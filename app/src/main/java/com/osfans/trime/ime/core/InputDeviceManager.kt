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
import com.osfans.trime.ime.composition.CandidatesView

class InputDeviceManager(
    private val onChange: (Boolean, Boolean) -> Unit,
) {
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null

    private val candidatesViewMode by AppPrefs.defaultInstance().candidates.mode

    private val alwaysShowCandidatesView: Boolean
        get() = candidatesViewMode == PopupCandidatesMode.ALWAYS_SHOW

    private fun setupInputViewCallback(isVirtual: Boolean) {
        val iv = inputView ?: return
        iv.handleMessages = isVirtual
        iv.visibility = if (isVirtual) View.VISIBLE else View.GONE
    }

    private fun setupCandidatesViewCallback(isVirtual: Boolean) {
        val cv = candidatesView ?: return
        val shouldSetupView = !isVirtual || alwaysShowCandidatesView
        cv.handleMessages = shouldSetupView
        if (!shouldSetupView) {
            cv.visibility = View.GONE
        }
    }

    private fun setupViewCallbacks(isVirtual: Boolean) {
        setupInputViewCallback(isVirtual)
        setupCandidatesViewCallback(isVirtual)
    }

    var useVirtualKeyboard = true
        private set(value) {
            val newUseCandidatesView = !value || alwaysShowCandidatesView
            if (field == value && useCandidatesView == newUseCandidatesView) {
                return
            }
            field = value
            useCandidatesView = newUseCandidatesView
            setupViewCallbacks(value)
            // fire change AFTER updating InputView(s),
            // make the view(s) ready for incoming events during `onChange`
            onChange(value, newUseCandidatesView)
        }

    var useCandidatesView = false

    fun setInputView(inputView: InputView) {
        this.inputView = inputView
        setupInputViewCallback(this.useVirtualKeyboard)
    }

    fun setCandidatesView(candidatesView: CandidatesView) {
        this.candidatesView = candidatesView
        setupCandidatesViewCallback(this.useVirtualKeyboard)
    }

    private var startedInputView = false
    private var isNullInputType = true

    /**
     * @return should use virtual keyboard or should use candidates view
     */
    fun evaluateOnStartInputView(
        info: EditorInfo,
        service: TrimeInputMethodService,
    ): BooleanArray {
        startedInputView = true
        isNullInputType = info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL
        useVirtualKeyboard =
            when (candidatesViewMode) {
                PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
                PopupCandidatesMode.INPUT_DEVICE -> useVirtualKeyboard
                PopupCandidatesMode.ALWAYS_SHOW -> true
                PopupCandidatesMode.DISABLED -> true
            }
        return booleanArrayOf(useVirtualKeyboard, useCandidatesView)
    }

    /**
     * @return should force show input views
     */
    fun evaluateOnKeyDown(
        e: KeyEvent,
        service: TrimeInputMethodService,
    ): Boolean {
        if (startedInputView) {
            // filter out back/home/volume buttons and combination keys
            if (e.unicodeChar != 0) {
                // evaluate virtual keyboard visibility when pressing physical keyboard while InputView visible
                evaluateOnKeyDownInner(service)
            }
            // no need to force show InputView since it's already visible
            return false
        } else {
            // force show InputView when focusing on text input (likely inputType is not TYPE_NULL)
            // and pressing any digit/letter/punctuation key on physical keyboard
            val showInputView = !isNullInputType && e.unicodeChar != 0
            if (showInputView) {
                evaluateOnKeyDownInner(service)
            }
            return showInputView
        }
    }

    private fun evaluateOnKeyDownInner(service: TrimeInputMethodService) {
        useVirtualKeyboard = when (candidatesViewMode) {
            PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
            PopupCandidatesMode.INPUT_DEVICE -> false
            PopupCandidatesMode.ALWAYS_SHOW -> false
            PopupCandidatesMode.DISABLED -> true
        }
    }

    fun evaluateOnViewClicked(service: TrimeInputMethodService) {
        if (!startedInputView) return
        useVirtualKeyboard = when (candidatesViewMode) {
            PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
            else -> true
        }
    }

    fun evaluateOnUpdateEditorToolType(
        toolType: Int,
        service: TrimeInputMethodService,
    ) {
        if (!startedInputView) return
        useVirtualKeyboard = when (candidatesViewMode) {
            PopupCandidatesMode.SYSTEM_DEFAULT -> service.superEvaluateInputViewShown()
            PopupCandidatesMode.INPUT_DEVICE ->
                // switch to virtual keyboard on touch screen events, otherwise preserve current mode
                if (toolType == MotionEvent.TOOL_TYPE_FINGER || toolType == MotionEvent.TOOL_TYPE_STYLUS) {
                    true
                } else {
                    useVirtualKeyboard
                }
            PopupCandidatesMode.ALWAYS_SHOW -> true
            PopupCandidatesMode.DISABLED -> true
        }
    }

    fun onFinishInputView() {
        startedInputView = false
    }
}
