package com.osfans.trime.ui.setup

import android.content.Context
import com.osfans.trime.R
import com.osfans.trime.util.InputMethodUtils

enum class SetupPage {
    Enable,
    Select,
    ;

    fun getStepText(context: Context) =
        context.getText(
            when (this) {
                Enable -> R.string.setup__step_one
                Select -> R.string.setup__step_two
            },
        )

    fun getHintText(context: Context) =
        context.getText(
            when (this) {
                Enable -> R.string.setup__enable_ime_hint
                Select -> R.string.setup__select_ime_hint
            },
        )

    fun getButtonText(context: Context) =
        context.getText(
            when (this) {
                Enable -> R.string.setup__enable_ime
                Select -> R.string.setup__select_ime
            },
        )

    fun getButtonAction(context: Context) {
        when (this) {
            Enable -> InputMethodUtils.showImeEnablerActivity(context)
            Select -> InputMethodUtils.showImePicker()
        }
    }

    fun isDone() =
        when (this) {
            Enable -> InputMethodUtils.checkIsTrimeEnabled()
            Select -> InputMethodUtils.checkIsTrimeSelected()
        }

    companion object {
        fun SetupPage.isLastPage() = this == values().last()

        fun Int.isLastPage() = this == values().size - 1

        fun hasUndonePage() = values().any { !it.isDone() }

        fun firstUndonePage() = values().firstOrNull { !it.isDone() }
    }
}
