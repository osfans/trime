/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle

/** Each ordinary stage is deliberately small; no legacy private/saved-state writes. */
internal object PublicPreferenceDialogProbe {
    private const val NEW = "com.osfans.trime.ui.common.dialog.Public"
    private const val OLD = "androidx.preference."
    private const val LEGACY = "com.osfans.trime.ui.common.dialog.LegacyPublic"

    fun run(instrumentation: Instrumentation, disposable: Boolean, expectedCode: String) {
        val c = PublicPreferenceProbeContext(instrumentation)
        try {
            c.prepare(disposable, expectedCode)
            val ui = PublicPreferenceProbeUi(c)
            freshString(c, ui)
            freshInteger(c, ui)
            freshList(c, ui)
            freshMulti(c, ui)
            legacyString(c, ui)
            legacyInteger(c, ui)
            legacyList(c, ui)
            legacyMulti(c, ui)
        } catch (error: Throwable) {
            c.failure = error
        } finally {
            finish(instrumentation, c)
        }
    }
    private fun finish(instrumentation: Instrumentation, c: PublicPreferenceProbeContext) {
        val result = try {
            c.cleanupAndReport()
        } catch (error: Throwable) {
            Bundle().apply {
                putBoolean("passed", false)
                putBoolean("automatic_reuse_allowed", false)
                putString("error", "Native cleanup/reporting failed; previous private report is not accepted")
                putString("error_type", error.javaClass.name)
                putBoolean("private_report_valid", false)
            }
        }
        instrumentation.finish(if (result.getBoolean("passed")) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
    private fun freshString(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        c.newOwner(legacy = false)
        val cls = NEW + "EditTextPreferenceDialogFragment"
        c.show("string", cls)
        val value = "fresh-draft-${c.token}"
        ui.text(value)
        val before = c.main { c.memory.bindCalls }
        c.capture("fresh-string-before-recreate")
        c.recreate(cls)
        ui.editor(value, 1, 3, opaque = true)
        c.main { check(c.memory.bindCalls == before + 1) }
        c.capture("fresh-string-restored")
        ui.positive()
        c.main {
            check(c.memory.value("string") == value)
            c.memory.veto += "string"
        }
        val vetoBefore = c.main { c.memory.attempts.size }
        c.show("string", cls)
        ui.text("veto-${c.token}")
        ui.positive()
        c.main {
            check(c.memory.value("string") == value && c.memory.attempts.size == vetoBefore + 1)
            check(c.memory.attempts.last() == ("string" to "veto-${c.token}"))
            c.memory.veto -= "string"
        }
        val negativeBefore = c.main { c.memory.attempts.size }
        c.show("string", cls)
        ui.text("cancel-${c.token}")
        ui.negative()
        c.main { check(c.memory.value("string") == value && c.memory.attempts.size == negativeBefore) }
        c.passed("fresh String actual short-tag widget opaque binder draft selection recreate positive veto and negative")
    }
    private fun freshInteger(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        val cls = NEW + "EditTextPreferenceDialogFragment"
        c.show("int", cls)
        ui.text("42", 0, 1)
        c.recreate(cls)
        ui.editor("42", 0, 1, opaque = false)
        ui.positive()
        c.main { check(c.memory.value("int") == 42 && c.memory.attempts.last() == ("int" to 42)) }
        c.show("int", cls)
        ui.text("7", 0, 1)
        ui.positive()
        c.main {
            check(c.memory.value("int") == 15 && c.memory.attempts.last() == ("int" to 15))
            c.memory.veto += "int"
        }
        val vetoBefore = c.main { c.memory.attempts.size }
        c.show("int", cls)
        ui.text("55", 0, 1)
        ui.positive()
        c.main {
            check(c.memory.value("int") == 15 && c.memory.attempts.size == vetoBefore + 1 && c.memory.attempts.last() == ("int" to 55))
            c.memory.veto -= "int"
        }
        val negativeBefore = c.main { c.memory.attempts.size }
        c.show("int", cls)
        ui.text("60", 0, 1)
        ui.negative()
        c.main { check(c.memory.value("int") == 15 && c.memory.attempts.size == negativeBefore) }
        c.passed("fresh production Int binder raw Integer draft recreate clamp Int-valued veto and negative")
    }
    private fun freshList(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        val cls = NEW + "ListPreferenceDialogFragment"
        c.show("list", cls)
        c.recreate(cls)
        ui.choices(setOf(0), noPositiveButton = true)
        ui.item(1, dismisses = true)
        c.main {
            check(c.memory.value("list") == "b")
            c.memory.veto += "list"
        }
        val vetoBefore = c.main { c.memory.attempts.size }
        c.show("list", cls)
        ui.item(2, dismisses = true)
        c.main {
            check(c.memory.value("list") == "b" && c.memory.attempts.size == vetoBefore + 1 && c.memory.attempts.last() == ("list" to "c"))
            c.memory.veto -= "list"
        }
        val negativeBefore = c.main { c.memory.attempts.size }
        c.show("list", cls)
        ui.negative()
        c.main { check(c.memory.value("list") == "b" && c.memory.attempts.size == negativeBefore) }
        c.passed("fresh List saved index entryValues auto-positive noOK veto and negative")
    }
    private fun freshMulti(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        val cls = NEW + "MultiSelectPreferenceDialogFragment"
        c.show("multi", cls)
        ui.item(1, dismisses = false)
        c.recreate(cls)
        ui.choices(setOf(0, 1))
        ui.positive()
        c.main {
            check(c.memory.value("multi") == setOf("a", "b"))
            c.memory.veto += "multi"
        }
        c.show("multi", cls)
        val before = c.main { c.memory.attempts.size }
        ui.item(2, dismisses = false)
        ui.item(2, dismisses = false)
        ui.positive()
        c.main {
            check(c.memory.attempts.size == before + 1 && c.memory.value("multi") == setOf("a", "b"))
            c.memory.veto -= "multi"
        }
        val negativeBefore = c.main { c.memory.attempts.size }
        c.show("multi", cls)
        ui.item(2, dismisses = false)
        ui.negative()
        c.main { check(c.memory.value("multi") == setOf("a", "b") && c.memory.attempts.size == negativeBefore) }
        c.passed("fresh synthetic MultiSelect working-state recreate toggle-back dirty veto and negative")
    }
    private fun legacyString(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        c.newOwner(legacy = true)
        val restored = LEGACY + "EditTextPreferenceDialogFragment"
        c.marker("legacy_edit_old_baseline_begin")
        c.show("string", OLD + "EditTextPreferenceDialogFragmentCompat")
        val value = "legacy-draft-${c.token}"
        ui.text(value)
        ui.actualOldEditor(value, 1, 3)
        val before = c.main { c.memory.bindCalls }
        c.capture("legacy-string-original")
        c.marker("legacy_edit_old_baseline_end_before_transition")
        c.recreate(restored)
        ui.editor(value, 1, 3, opaque = true)
        c.main { check(c.memory.bindCalls == before + 1) }
        c.capture("legacy-string-adapter")
        ui.positive()
        c.main { check(c.memory.value("string") == value) }
        c.marker("legacy_edit_cancel_old_baseline_begin")
        c.show("string", OLD + "EditTextPreferenceDialogFragmentCompat")
        ui.text("cancel-${c.token}")
        c.marker("legacy_edit_cancel_old_baseline_end_before_transition")
        val negativeBefore = c.main { c.memory.attempts.size }
        c.recreate(restored)
        ui.negative()
        c.main { check(c.memory.value("string") == value && c.memory.attempts.size == negativeBefore) }
        c.passed("genuine old Edit -> same-APK Main recreate -> legacy adapter preserves opaque bind draft selection positive and negative")
    }
    private fun legacyInteger(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        c.marker("legacy_int_old_baseline_begin")
        c.show("int", OLD + "EditTextPreferenceDialogFragmentCompat")
        ui.text("7", 0, 1)
        c.marker("legacy_int_old_baseline_end_before_transition")
        c.recreate(LEGACY + "EditTextPreferenceDialogFragment")
        ui.editor("7", 0, 1, opaque = false)
        ui.positive()
        c.main {
            check(c.memory.value("int") == 15 && c.memory.attempts.last() == ("int" to 15))
            c.memory.veto += "int"
        }
        // Opening from this original owner intentionally creates another genuine old baseline.
        c.marker("legacy_int_veto_old_baseline_begin")
        c.show("int", OLD + "EditTextPreferenceDialogFragmentCompat")
        ui.text("55", 0, 1)
        c.marker("legacy_int_veto_old_baseline_end_before_transition")
        val vetoBefore = c.main { c.memory.attempts.size }
        c.recreate(LEGACY + "EditTextPreferenceDialogFragment")
        ui.positive()
        c.main {
            check(c.memory.value("int") == 15 && c.memory.attempts.size == vetoBefore + 1 && c.memory.attempts.last() == ("int" to 55))
            c.memory.veto -= "int"
        }
        c.passed("genuine old production Int -> adapter retains String-to-Int clamp and original Int-valued veto")
    }
    private fun legacyList(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        c.marker("legacy_list_old_baseline_begin")
        c.show("list", OLD + "ListPreferenceDialogFragmentCompat")
        ui.oldChoices(setOf(0))
        val originalFocus = ui.focusListForSave()
        c.capture("legacy-list-original")
        c.marker("legacy_list_old_baseline_end_before_transition")
        c.recreate(LEGACY + "ListPreferenceDialogFragment")
        ui.choices(setOf(0), noPositiveButton = true)
        ui.assertRestoredListFocus(originalFocus)
        c.capture("legacy-list-adapter")
        ui.item(2, dismisses = true)
        c.main { check(c.memory.value("list") == "c") }
        c.passed("genuine old List initial saved index -> adapter entryValues auto-positive and noOK; no invented pending click")
    }
    private fun legacyMulti(c: PublicPreferenceProbeContext, ui: PublicPreferenceProbeUi) {
        val cls = LEGACY + "MultiSelectPreferenceDialogFragment"
        c.marker("legacy_multi_old_baseline_begin")
        c.show("multi", OLD + "MultiSelectListPreferenceDialogFragmentCompat")
        ui.oldItem(1)
        ui.oldChoices(setOf(0, 1))
        val originalFocus = ui.focusListForSave()
        c.capture("legacy-multi-original")
        c.marker("legacy_multi_old_baseline_end_before_transition")
        c.recreate(cls)
        ui.choices(setOf(0, 1))
        ui.assertRestoredListFocus(originalFocus)
        ui.item(2, dismisses = false)
        val adapterFocus = ui.focusListForSave()
        c.recreate(cls)
        ui.choices(setOf(0, 1, 2))
        ui.assertRestoredListFocus(adapterFocus)
        c.capture("legacy-multi-adapter-second-restore")
        ui.positive()
        c.main { check(c.memory.value("multi") == setOf("a", "b", "c")) }
        c.passed("genuine old Multi working state -> adapter -> adapter recreate preserves own saved set and positive")
    }
}
