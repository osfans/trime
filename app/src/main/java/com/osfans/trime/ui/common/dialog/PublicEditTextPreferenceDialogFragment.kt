/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.osfans.trime.ui.common.dialog

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class PublicEditTextPreferenceDialogFragment : PublicPreferenceDialogFragment() {
    private var draft: CharSequence? = null
    private var selectionStart = -1
    private var selectionEnd = -1
    private var editText: EditText? = null
    private var showImeRequest: Runnable? = null
    private var bindingOwner: Lifecycle? = null
    private var bindingObserver: LifecycleEventObserver? = null
    private var editorBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            draft = (checkNotNull(currentPreference()) as PublicBoundEditTextPreference).text
        } else {
            draft = savedInstanceState.getCharSequence(STATE_TEXT)
            selectionStart = savedInstanceState.getInt(STATE_SELECTION_START, -1)
            selectionEnd = savedInstanceState.getInt(STATE_SELECTION_END, -1)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        editText?.takeIf { editorBound }?.let {
            draft = it.text.toString()
            selectionStart = it.selectionStart
            selectionEnd = it.selectionEnd
        }
        outState.putCharSequence(STATE_TEXT, draft)
        outState.putInt(STATE_SELECTION_START, selectionStart)
        outState.putInt(STATE_SELECTION_END, selectionEnd)
    }

    override fun bindDialogView(view: View, restoring: Boolean) {
        super.bindDialogView(view, restoring)
        val editor = checkNotNull(view.findViewById<EditText>(android.R.id.edit)) {
            "Dialog view must contain an EditText with android.R.id.edit"
        }
        cancelPendingBinding()
        editText = editor
        editorBound = false
        val preference = currentPreference() as? PublicBoundEditTextPreference
        if (preference != null) {
            bindEditor(editor, preference, restoring)
            return
        }
        val owner = currentOwner()
        if (owner == null || owner.lifecycle.currentState != Lifecycle.State.INITIALIZED) {
            cancelForMissingOwner()
            return
        }
        // Restored sibling order must not replace the previous target-fragment lifecycle dependency.
        // ON_CREATE is dispatched only after this exact owner's onCreatePreferences has returned.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_CREATE || event == Lifecycle.Event.ON_DESTROY) {
                cancelPendingBinding()
                if (editText === editor && !editorBound) {
                    val restored = currentPreference() as? PublicBoundEditTextPreference
                    if (event == Lifecycle.Event.ON_CREATE && restored != null) {
                        bindEditor(editor, restored, restoring)
                        if (dialog?.isShowing == true) requestIme(editor)
                    } else {
                        cancelForMissingOwner()
                        if (dialog?.isShowing == true) dismiss()
                    }
                }
            }
        }
        bindingOwner = owner.lifecycle
        bindingObserver = observer
        owner.lifecycle.addObserver(observer)
    }

    private fun bindEditor(editor: EditText, preference: PublicBoundEditTextPreference, restoring: Boolean) {
        editorBound = true
        editor.requestFocus()
        editor.setText(draft)
        editor.setSelection(editor.text.length)
        preference.bindDialogEditText(editor)
        if (restoring) {
            editor.setText(draft)
            if (selectionStart >= 0 && selectionEnd >= 0) {
                editor.setSelection(selectionStart.coerceAtMost(editor.length()), selectionEnd.coerceAtMost(editor.length()))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!canStartDialog) return
        val editor = checkNotNull(editText) { "EditText preference requires a custom dialog layout containing android.R.id.edit" }
        if (editorBound) requestIme(editor)
    }

    private fun requestIme(editor: EditText) {
        cancelImeRequest()
        editor.requestFocus()
        if (Build.VERSION.SDK_INT >= 30) {
            dialog?.window?.let { WindowCompat.getInsetsController(it, editor).show(WindowInsetsCompat.Type.ime()) }
        } else {
            val deadline = SystemClock.uptimeMillis() + 1_000L
            val request = object : Runnable {
                override fun run() {
                    if (editText !== editor || !editor.isFocused || dialog?.isShowing != true ||
                        SystemClock.uptimeMillis() >= deadline
                    ) {
                        cancelImeRequest()
                        return
                    }
                    val manager = editor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (manager.showSoftInput(editor, 0)) {
                        cancelImeRequest()
                    } else {
                        editor.postDelayed(this, 50L)
                    }
                }
            }
            showImeRequest = request
            editor.post(request)
        }
    }

    private fun cancelPendingBinding() {
        bindingObserver?.let { bindingOwner?.removeObserver(it) }
        bindingObserver = null
        bindingOwner = null
    }

    private fun cancelImeRequest() {
        showImeRequest?.let { editText?.removeCallbacks(it) }
        showImeRequest = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        cancelPendingBinding()
        cancelImeRequest()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        cancelPendingBinding()
        cancelImeRequest()
        super.onDestroyView()
        editText = null
    }

    override fun onDialogClosed(positive: Boolean) {
        if (!positive || !editorBound) return
        val preference = currentPreference() as? PublicBoundEditTextPreference ?: return
        val value = checkNotNull(editText).text.toString()
        if (preference.callChangeListener(value)) preference.text = value
    }

    companion object {
        private const val STATE_TEXT = "EditTextPreferenceDialogFragment.text"
        private const val STATE_SELECTION_START = "trime.public.preference.selection_start"
        private const val STATE_SELECTION_END = "trime.public.preference.selection_end"
    }
}
