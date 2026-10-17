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
import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat

class LegacyPublicMultiSelectPreferenceDialogFragment : MultiSelectListPreferenceDialogFragmentCompat() {
    private val platformWindow = LegacyPreferenceDialogWindow(this, LegacyPreferenceDialogKind.MULTI_SELECT)
    private val newValues = HashSet<String>()
    private var changed = false
    private var entries: Array<CharSequence>? = null
    private var entryValues: Array<CharSequence>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platformWindow.onCreate(savedInstanceState)
        newValues.clear()
        if (savedInstanceState == null) {
            val preference = getPreference() as MultiSelectListPreference
            newValues.addAll(preference.values)
            changed = false
            entries = preference.entries
            entryValues = preference.entryValues
        } else {
            newValues.addAll(checkNotNull(savedInstanceState.getStringArrayList(STATE_VALUES)))
            changed = savedInstanceState.getBoolean(STATE_CHANGED)
            entries = savedInstanceState.getCharSequenceArray(STATE_ENTRIES)
            entryValues = savedInstanceState.getCharSequenceArray(STATE_ENTRY_VALUES)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Preserve the inherited metadata/hierarchy, then replace stale superclass working state.
        outState.putStringArrayList(STATE_VALUES, ArrayList(newValues))
        outState.putBoolean(STATE_CHANGED, changed)
        outState.putCharSequenceArray(STATE_ENTRIES, entries)
        outState.putCharSequenceArray(STATE_ENTRY_VALUES, entryValues)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = platformWindow.createDialog(
        bind = { content -> super.onBindDialogView(content) },
        prepare = { builder ->
            val values = checkNotNull(entryValues)
            val checked = BooleanArray(values.size) { newValues.contains(values[it].toString()) }
            builder.setMultiChoiceItems(checkNotNull(entries), checked) { _, which, isChecked ->
                changed = if (isChecked) newValues.add(values[which].toString()) || changed else newValues.remove(values[which].toString()) || changed
            }
        },
    )

    override fun onDialogClosed(positiveResult: Boolean) {
        val close = platformWindow.takeClose(positiveResult) ?: return
        if (close.positive && changed) {
            val preference = checkNotNull(close.preference) as MultiSelectListPreference
            if (preference.callChangeListener(newValues)) preference.values = newValues
        }
        changed = false
        // Reset the original private dirty bit without committing its stale private set.
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
        private const val STATE_VALUES = "MultiSelectListPreferenceDialogFragmentCompat.values"
        private const val STATE_CHANGED = "MultiSelectListPreferenceDialogFragmentCompat.changed"
        private const val STATE_ENTRIES = "MultiSelectListPreferenceDialogFragmentCompat.entries"
        private const val STATE_ENTRY_VALUES = "MultiSelectListPreferenceDialogFragmentCompat.entryValues"
    }
}
