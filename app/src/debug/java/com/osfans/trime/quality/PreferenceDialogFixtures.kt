/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.quality

import android.os.Bundle
import android.os.Looper
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.BuildConfig
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.common.dialog.PublicBoundEditTextPreference
import com.osfans.trime.ui.main.settings.EditTextIntPreference

/** Debug-target only: survives same-process Activity.recreate, never writes files or user prefs. */
class PreferenceProbeMemory(val token: String) : PreferenceDataStore() {
    private val values = linkedMapOf<String, Any>()
    val attempts = mutableListOf<Pair<String, Any>>()
    val veto = mutableSetOf<String>()
    var bindCalls = 0
        private set

    init {
        values[key("string")] = "initial-$token"
        values[key("int")] = 30
        values[key("list")] = "a"
        values[key("multi")] = setOf("a")
    }

    fun key(kind: String): String = "probe.$token.$kind"
    fun value(kind: String): Any? = values[key(kind)]
    private fun validate(key: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(key in values) { "Unexpected fixture key" }
    }
    override fun getString(key: String, defValue: String?): String? {
        validate(key)
        return values[key] as? String ?: defValue
    }
    override fun putString(key: String, value: String?) {
        validate(key)
        values[key] = checkNotNull(value)
    }
    override fun getInt(key: String, defValue: Int): Int {
        validate(key)
        return values[key] as? Int ?: defValue
    }
    override fun putInt(key: String, value: Int) {
        validate(key)
        values[key] = value
    }
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        validate(key)
        val current = values[key] as? Set<*> ?: return defValues?.toSet()
        check(current.all { it is String })
        return current.filterIsInstance<String>().toSet()
    }
    override fun putStringSet(key: String, values: Set<String>?) {
        validate(key)
        this.values[key] = checkNotNull(values).toSet()
    }
    fun bind(editor: android.widget.EditText) {
        bindCalls++
        editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        editor.hint = "opaque-$token"
    }
    fun accept(kind: String, value: Any): Boolean {
        attempts += kind to if (value is Set<*>) value.toSet() else value
        return kind !in veto
    }
}

/** Registry is intentionally process-local. Process death is unsupported and fails, not repaired. */
object PreferenceProbeFixtures {
    private val states = mutableMapOf<String, PreferenceProbeMemory>()
    fun create(token: String): PreferenceProbeMemory {
        check(BuildConfig.DEBUG && token.matches(Regex("[0-9a-f-]{36}")))
        check(Looper.myLooper() == Looper.getMainLooper() && token !in states)
        return PreferenceProbeMemory(token).also { states[token] = it }
    }
    fun require(token: String): PreferenceProbeMemory = checkNotNull(states[token]) { "Fixture process/state was lost" }
    fun release(token: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        checkNotNull(states.remove(token))
    }
    fun contains(token: String): Boolean = token in states

    fun populate(owner: PreferenceFragmentCompat, legacy: Boolean) {
        val state = require(checkNotNull(owner.requireArguments().getString("fixture_token")))
        owner.preferenceManager.preferenceDataStore = state
        val context = owner.requireContext()
        owner.preferenceScreen = owner.preferenceManager.createPreferenceScreen(context).apply {
            fun add(kind: String, preference: Preference) {
                preference.key = state.key(kind)
                preference.title = "Synthetic $kind"
                preference.setOnPreferenceChangeListener { _, value -> state.accept(kind, checkNotNull(value)) }
                addPreference(preference)
            }
            val text = if (legacy) EditTextPreference(context) else PublicBoundEditTextPreference(context)
            text.setOnBindEditTextListener(state::bind)
            add("string", text)
            add(
                "int",
                EditTextIntPreference(context).apply {
                    min = 15
                    max = 60
                    setDefaultValue(30)
                },
            )
            add(
                "list",
                ListPreference(context).apply {
                    entries = arrayOf("First", "Second", "Third")
                    entryValues = arrayOf("a", "b", "c")
                },
            )
            add(
                "multi",
                MultiSelectListPreference(context).apply {
                    entries = arrayOf("First", "Second", "Third")
                    entryValues = arrayOf("a", "b", "c")
                },
            )
        }
    }
}

/** Calls inherited AndroidX display: real original classes/target state, no deprecated test setter. */
class OriginalPreferenceDialogFixture : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        PreferenceProbeFixtures.populate(this, legacy = true)
    }
}

/** Uses the actual migrated Padding display hook and production Int preference implementation. */
class MigratedPreferenceDialogFixture : PaddingPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        PreferenceProbeFixtures.populate(this, legacy = false)
    }
}
