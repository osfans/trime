/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.broadcast

import android.view.inputmethod.EditorInfo
import com.osfans.trime.data.theme.Theme
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.bitflags.hasFlag

class EnterKeyDisplayDelegate(override val di: DI) : DIAware {
    private val broadcaster: InputBroadcaster by instance()
    private val theme: Theme by instance()

    companion object {
        const val DEFAULT_LABEL = "Enter"
    }

    enum class Mode {
        ACTION_LABEL_NEVER,
        ACTION_LABEL_ONLY,
        ACTION_LABEL_PREFERRED,
        CUSTOM_PREFERRED,
    }

    val mode: Mode = runCatching { Mode.entries[theme.style.enterLabelMode] }.getOrDefault(Mode.ACTION_LABEL_NEVER)

    var keyLabel: String = DEFAULT_LABEL
        private set

    private var actionLabel: String = DEFAULT_LABEL

    private fun labelFromEditorInfo(info: EditorInfo): String {
        if (info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            return theme.style.enterLabels.default
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
                        theme.style.enterLabels.default
                    }
                }

                Mode.CUSTOM_PREFERRED,
                Mode.ACTION_LABEL_NEVER,
                -> {
                    return when (action) {
                        EditorInfo.IME_ACTION_DONE -> theme.style.enterLabels.done

                        EditorInfo.IME_ACTION_GO -> theme.style.enterLabels.go

                        EditorInfo.IME_ACTION_NEXT -> theme.style.enterLabels.next

                        EditorInfo.IME_ACTION_PREVIOUS -> theme.style.enterLabels.pre

                        EditorInfo.IME_ACTION_SEARCH -> theme.style.enterLabels.search

                        EditorInfo.IME_ACTION_SEND -> theme.style.enterLabels.send

                        else -> {
                            if (mode == Mode.ACTION_LABEL_NEVER) {
                                theme.style.enterLabels.default
                            } else {
                                if (!actionLabel.isNullOrEmpty()) {
                                    actionLabel.toString()
                                } else {
                                    theme.style.enterLabels.default
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
