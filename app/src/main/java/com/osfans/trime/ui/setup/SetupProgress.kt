/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import android.content.Context
import androidx.core.content.edit
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.appContext

/** Only wizard checkpoints are stored here; trial text and Rime configuration are never stored. */
internal object SetupProgress {
    private val preferences get() = appContext.getSharedPreferences("trime_setup", Context.MODE_PRIVATE)

    val started get() = preferences.getBoolean("started", false)
    val inputConfirmed get() = preferences.getBoolean("input_confirmed", false)
    val trialCompleted get() = preferences.getBoolean("trial_completed", false)
    val deferred get() = preferences.getBoolean("deferred", false)

    fun begin() = preferences.edit { putBoolean("started", true) }

    fun confirmInput(confirmed: Boolean) = preferences.edit {
        putBoolean("input_confirmed", confirmed)
        if (!confirmed) {
            putBoolean("trial_completed", false)
            putBoolean("deferred", false)
        }
    }

    fun completeTrial() = preferences.edit { putBoolean("trial_completed", true) }

    fun defer() = preferences.edit { putBoolean("deferred", true) }

    fun needsInputSteps() = needsInputSetup(
        started,
        AppPrefs.defaultInstance().profile.freshInputSetupPending.getValue(),
        inputConfirmed,
        trialCompleted,
        deferred,
    )
}
