/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.broadcast

import android.view.inputmethod.EditorInfo
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject
import splitties.bitflags.hasFlag

@InputScope
@Inject
class EnterKeyLabelModule(
    private val broadcaster: InputBroadcaster,
    private val theme: Theme,
) {
    companion object {
        const val DEFAULT_LABEL = "⏎"
    }

    enum class Mode {
        ACTION_LABEL_NEVER,
        ACTION_LABEL_ONLY,
        ACTION_LABEL_PREFERRED,
        CUSTOM_PREFERRED,
    }

    val mode: Mode = runCatching { Mode.entries[theme.generalStyle.enterLabelMode] }.getOrDefault(Mode.ACTION_LABEL_NEVER)

    var keyLabel: String = DEFAULT_LABEL
        private set

    private var actionLabel: String = DEFAULT_LABEL

    private fun labelFromEditorInfo(info: EditorInfo): String {
        if (info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            return theme.generalStyle.enterLabel.default
        } else {
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val actionLabel = info.actionLabel
            when (mode) {
                Mode.ACTION_LABEL_ONLY -> {
                    return actionLabel.toString()
                }
                Mode.ACTION_LABEL_PREFERRED -> {
                    return if (!actionLabel.isNullOrEmpty()) {
                        actionLabel.toString()
                    } else {
                        theme.generalStyle.enterLabel.default
                    }
                }
                Mode.CUSTOM_PREFERRED,
                Mode.ACTION_LABEL_NEVER,
                -> {
                    return when (action) {
                        EditorInfo.IME_ACTION_DONE -> theme.generalStyle.enterLabel.done
                        EditorInfo.IME_ACTION_GO -> theme.generalStyle.enterLabel.go
                        EditorInfo.IME_ACTION_NEXT -> theme.generalStyle.enterLabel.next
                        EditorInfo.IME_ACTION_PREVIOUS -> theme.generalStyle.enterLabel.pre
                        EditorInfo.IME_ACTION_SEARCH -> theme.generalStyle.enterLabel.search
                        EditorInfo.IME_ACTION_SEND -> theme.generalStyle.enterLabel.send
                        else -> {
                            if (mode == Mode.ACTION_LABEL_NEVER) {
                                theme.generalStyle.enterLabel.default
                            } else {
                                if (!actionLabel.isNullOrEmpty()) {
                                    actionLabel.toString()
                                } else {
                                    theme.generalStyle.enterLabel.default
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateLabelOnEditorInfo(info: EditorInfo) {
        actionLabel = labelFromEditorInfo(info)
        if (keyLabel == actionLabel) return
        keyLabel = actionLabel
        broadcaster.onEnterKeyLabelUpdate(keyLabel)
    }
}
