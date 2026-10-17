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

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import androidx.preference.EditTextPreferenceDialogFragmentCompat

/** Only restored legacy built-in EditText dialogs use this superclass-preserving adapter. */
class LegacyPublicEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {
    private val platformWindow = LegacyPreferenceDialogWindow(this, LegacyPreferenceDialogKind.EDIT_TEXT)
    private val ime = LegacyPreferenceDialogIme()
    private var editor: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platformWindow.onCreate(savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = platformWindow.createDialog(bind = { content ->
        // The original method owns its private editor and lawfully reads an opaque callback.
        super.onBindDialogView(content)
        platformWindow.recordBoundPreference()
        editor = content.findViewById(android.R.id.edit)
    })

    override fun onStart() {
        super.onStart()
        editor?.let { ime.show(requireDialog(), it) }
    }

    override fun onDismiss(dialog: DialogInterface) {
        ime.cancel()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        ime.cancel()
        super.onDestroyView()
        editor = null
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        val close = platformWindow.takeClose(positiveResult) ?: return
        super.onDialogClosed(close.positive)
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
        } finally {
            try {
                ime.cancel()
            } finally {
                platformWindow.onDestroy()
            }
        }
    }
}
