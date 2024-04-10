// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import android.content.Context
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.util.InputMethodUtils

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
                Permissions -> R.string.setup__request_permission_hint
                Enable -> R.string.setup__enable_ime_hint
                Select -> R.string.setup__select_ime_hint
            },
        )

    fun getButtonText(context: Context) =
        context.getText(
            when (this) {
                Permissions -> R.string.setup__request_permission
                Enable -> R.string.setup__enable_ime
                Select -> R.string.setup__select_ime
            },
        )

    fun isDone() =
        when (this) {
            Permissions -> AppPrefs.defaultInstance().profile.isUserDataDirChosen()
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
