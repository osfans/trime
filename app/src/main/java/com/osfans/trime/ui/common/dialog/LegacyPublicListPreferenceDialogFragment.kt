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
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat

class LegacyPublicListPreferenceDialogFragment : ListPreferenceDialogFragmentCompat() {
    private val platformWindow = LegacyPreferenceDialogWindow(this, LegacyPreferenceDialogKind.LIST)
    private var clickedIndex = -1
    private var entries: Array<CharSequence>? = null
    private var entryValues: Array<CharSequence>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platformWindow.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val preference = getPreference() as ListPreference
            clickedIndex = preference.findIndexOfValue(preference.value)
            entries = preference.entries
            entryValues = preference.entryValues
        } else {
            clickedIndex = savedInstanceState.getInt(STATE_INDEX)
            entries = savedInstanceState.getCharSequenceArray(STATE_ENTRIES)
            entryValues = savedInstanceState.getCharSequenceArray(STATE_VALUES)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Superclass private index still contains its initial snapshot; replace that same key.
        outState.putInt(STATE_INDEX, clickedIndex)
        outState.putCharSequenceArray(STATE_ENTRIES, entries)
        outState.putCharSequenceArray(STATE_VALUES, entryValues)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = platformWindow.createDialog(
        bind = { content -> super.onBindDialogView(content) },
        prepare = { builder ->
            builder.setSingleChoiceItems(checkNotNull(entries), clickedIndex) { dialog, which ->
                clickedIndex = which
                onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                dialog.dismiss()
            }
            builder.setPositiveButton(null, null)
        },
    )

    override fun onDialogClosed(positiveResult: Boolean) {
        val close = platformWindow.takeClose(positiveResult) ?: return
        if (close.positive && clickedIndex >= 0) {
            val preference = checkNotNull(close.preference) as ListPreference
            val value = checkNotNull(entryValues)[clickedIndex].toString()
            if (preference.callChangeListener(value)) preference.value = value
        }
        // Do not let the superclass's stale private index write another value.
        super.onDialogClosed(false)
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
        } finally {
            platformWindow.onDestroy()
        }
    }

    companion object {
        private const val STATE_INDEX = "ListPreferenceDialogFragment.index"
        private const val STATE_ENTRIES = "ListPreferenceDialogFragment.entries"
        private const val STATE_VALUES = "ListPreferenceDialogFragment.entryValues"
    }
}
