/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.osfans.trime.R
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.util.InputMethodUtils

enum class SetupPage {
    Mode,
    Enable,
    Select,
    Input,
    Try,
    ;

    fun getStepText(context: Context) = context.getText(
        when (this) {
            Mode -> R.string.setup__step_one
            Enable -> R.string.setup__step_two
            Select -> R.string.setup__step_three
            Input -> R.string.setup__step_input
            Try -> R.string.setup__step_try
        },
    )

    fun getHintText(context: Context) = context.getText(
        when (this) {
            Mode -> R.string.setup__select_data_path_hint
            Enable -> R.string.setup__enable_ime_hint
            Select -> R.string.setup__select_ime_hint
            Input -> R.string.setup__input_hint
            Try -> R.string.setup__try_hint
        },
    )

    fun getButtonText(context: Context) = context.getText(
        when (this) {
            Mode -> R.string.setup__select_data_path
            Enable -> R.string.setup__enable_ime
            Select -> R.string.setup__select_ime
            Input -> R.string.setup__apply_input
            Try -> R.string.setup__open_trial
        },
    )

    fun getButtonAction(activity: FragmentActivity) {
        when (this) {
            Mode -> (activity as SetupActivity).pickDataPath()
            Enable -> InputMethodUtils.showImeEnablerActivity(activity)
            Select -> InputMethodUtils.showImePicker()
            Input -> (activity as SetupActivity).applyInputChoice()
            Try -> (activity as SetupActivity).openTrial()
        }
    }

    fun showActionButton(): Boolean = when (this) {
        Mode -> UserDataManager.usesExternalSync()
        Input, Try -> true
        else -> true
    }

    fun isDone() = when (this) {
        Mode -> UserDataManager.isStorageChoiceDone()
        Enable -> InputMethodUtils.checkIsTrimeEnabled()
        Select -> InputMethodUtils.checkIsTrimeSelected()
        Input -> SetupProgress.inputConfirmed
        Try -> SetupProgress.trialCompleted || SetupProgress.deferred
    }

    companion object {
        fun SetupPage.isLastPage() = this == entries.last()

        fun Int.isLastPage() = this == entries.size - 1

        // Upgrading users who already enabled and selected Trime do not acquire a new wizard.
        fun hasUndonePage() = listOf(Mode, Enable, Select).any { !it.isDone() } || SetupProgress.needsInputSteps()

        fun firstUndonePage() = entries.firstOrNull { !it.isDone() }
    }
}
