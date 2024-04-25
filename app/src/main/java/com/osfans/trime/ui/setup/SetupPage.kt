// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import android.content.Context
import com.osfans.trime.R
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.appContext
import com.osfans.trime.util.isStorageAvailable
import com.osfans.trime.util.requestExternalStoragePermission

enum class SetupPage {
    Permissions,
    Enable,
    Select,
    ;

    fun getStepText(context: Context) =
        context.getText(
            when (this) {
                Permissions -> R.string.setup__step_one
                Enable -> R.string.setup__step_two
                Select -> R.string.setup__step_three
            },
        )

    fun getHintText(context: Context) =
        context.getText(
            when (this) {
                Permissions -> R.string.setup__request_permmision_hint
                Enable -> R.string.setup__enable_ime_hint
                Select -> R.string.setup__select_ime_hint
            },
        )

    fun getButtonText(context: Context) =
        context.getText(
            when (this) {
                Permissions -> R.string.setup__request_permmision
                Enable -> R.string.setup__enable_ime
                Select -> R.string.setup__select_ime
            },
        )

    fun getButtonAction(context: Context) {
        when (this) {
            Permissions -> context.requestExternalStoragePermission()
            Enable -> InputMethodUtils.showImeEnablerActivity(context)
            Select -> InputMethodUtils.showImePicker()
        }
    }

    fun isDone() =
        when (this) {
            Permissions -> appContext.isStorageAvailable()
            Enable -> InputMethodUtils.checkIsTrimeEnabled()
            Select -> InputMethodUtils.checkIsTrimeSelected()
        }

    companion object {
        fun SetupPage.isLastPage() = this == entries.last()

        fun Int.isLastPage() = this == entries.size - 1

        fun hasUndonePage() = entries.any { !it.isDone() }

        fun firstUndonePage() = entries.firstOrNull { !it.isDone() }
    }
}
