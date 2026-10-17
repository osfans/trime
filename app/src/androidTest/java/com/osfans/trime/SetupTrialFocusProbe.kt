/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.BaseInputConnection
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.ui.setup.SetupPage
import com.osfans.trime.ui.setup.SetupTrialEditText

/** Checks the real Window save boundary while the actual IME owns unfinished trial composition. */
object SetupTrialFocusProbe {
    fun verify(
        instrumentation: Instrumentation,
        activity: SetupActivity,
        trial: SetupTrialEditText,
        service: TrimeInputMethodService,
    ): Bundle {
        fun <T> main(block: () -> T): T {
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            return checkNotNull(outcome).getOrThrow()
        }
        val checkpoints = activity.getSharedPreferences("trime_setup", Context.MODE_PRIVATE)
        val previousCheckpoints = checkpoints.all.toMap()
        val text = main { trial.text.toString() }
        val selection = main { trial.selectionStart to trial.selectionEnd }
        val composition = main {
            val editable = checkNotNull(trial.text)
            BaseInputConnection.getComposingSpanStart(editable) to BaseInputConnection.getComposingSpanEnd(editable)
        }
        val token = main {
            check(activity.currentSetupPage == SetupPage.Try && activity.hasWindowFocus() && trial.hasFocus())
            check(service.currentInputEditorInfo?.fieldId == trial.id && service.currentInputConnection != null)
            check(service.isCurrentEditor(service.editorToken))
            service.editorToken
        }
        val inputViewShown = main { service.isInputViewShown }
        main {
            // Exercise the same focus guard with the real Window snapshot. Calling the entire
            // Activity save callback on a still-resumed Activity would stop Fragment lifecycles.
            val saved = activity.saveWithTrialFocusOwner { activity.window.saveHierarchyState() }
            check(!saved.isEmpty)
            check(trial.hasFocus()) { "Saving Setup exposed another focused view before returning" }
            check(trial.text.toString() == text && (trial.selectionStart to trial.selectionEnd) == selection)
        }
        instrumentation.waitForIdleSync()
        main {
            check(activity.hasWindowFocus() && trial.hasFocus()) { "Saving Setup lost the trial's actual focused window" }
            check(service.editorToken == token && service.isCurrentEditor(token)) { "Saving Setup restarted or replaced the actual IME editor" }
            check(service.currentInputEditorInfo?.fieldId == trial.id && service.currentInputConnection != null)
            check(service.isInputViewShown == inputViewShown) { "Saving Setup changed actual IME visibility" }
            check(trial.text.toString() == text && (trial.selectionStart to trial.selectionEnd) == selection)
            val editable = checkNotNull(trial.text)
            check(
                (BaseInputConnection.getComposingSpanStart(editable) to BaseInputConnection.getComposingSpanEnd(editable)) == composition,
            ) { "Saving Setup changed the unfinished editor composition" }
            check(!activity.isPageDone(SetupPage.Try))
        }
        check(checkpoints.all == previousCheckpoints) { "Saving trial focus changed wizard completion checkpoints" }
        return Bundle().apply {
            putBoolean("setup_trial_focus_saved_without_editor_switch", true)
            putBoolean("setup_trial_focus_draft_selection_composition_equal", true)
            putBoolean("setup_trial_focus_checkpoint_equal", true)
            putLong("setup_trial_focus_editor_token", token)
        }
    }
}
