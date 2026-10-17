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

import android.os.Bundle
import androidx.preference.MultiSelectListPreference

class PublicMultiSelectPreferenceDialogFragment : PublicPreferenceDialogFragment() {
    private val newValues = HashSet<String>()
    private var changed = false
    private var entries: Array<CharSequence>? = null
    private var entryValues: Array<CharSequence>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val preference = checkNotNull(currentPreference()) as MultiSelectListPreference
            entries = checkNotNull(preference.entries) { "MultiSelectListPreference requires entries" }
            entryValues = checkNotNull(preference.entryValues) { "MultiSelectListPreference requires entryValues" }
            newValues.addAll(preference.values)
        } else {
            newValues.addAll(checkNotNull(savedInstanceState.getStringArrayList(STATE_VALUES)))
            changed = savedInstanceState.getBoolean(STATE_CHANGED)
            entries = savedInstanceState.getCharSequenceArray(STATE_ENTRIES)
            entryValues = savedInstanceState.getCharSequenceArray(STATE_ENTRY_VALUES)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_VALUES, ArrayList(newValues))
        outState.putBoolean(STATE_CHANGED, changed)
        outState.putCharSequenceArray(STATE_ENTRIES, entries)
        outState.putCharSequenceArray(STATE_ENTRY_VALUES, entryValues)
    }

    override fun prepareDialogBuilder(builder: android.app.AlertDialog.Builder) {
        val values = checkNotNull(entryValues)
        val checked = BooleanArray(values.size) { newValues.contains(values[it].toString()) }
        builder.setMultiChoiceItems(checkNotNull(entries), checked) { _, which, isChecked ->
            changed = if (isChecked) newValues.add(values[which].toString()) || changed else newValues.remove(values[which].toString()) || changed
        }
    }

    override fun onDialogClosed(positive: Boolean) {
        if (positive && changed) {
            val preference = currentPreference() as? MultiSelectListPreference
            if (preference != null && preference.callChangeListener(newValues)) preference.values = newValues
        }
        changed = false
    }

    companion object {
        private const val STATE_VALUES = "MultiSelectListPreferenceDialogFragmentCompat.values"
        private const val STATE_CHANGED = "MultiSelectListPreferenceDialogFragmentCompat.changed"
        private const val STATE_ENTRIES = "MultiSelectListPreferenceDialogFragmentCompat.entries"
        private const val STATE_ENTRY_VALUES = "MultiSelectListPreferenceDialogFragmentCompat.entryValues"
    }
}
