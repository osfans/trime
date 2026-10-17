/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.broadcast

import android.view.inputmethod.EditorInfo
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.EditorPolicy
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

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
        val action = EditorPolicy.enterAction(info)
        if (action == null) {
            return theme.style.enterLabels.default
        } else {
            val actionLabel = info.actionLabel?.takeIf { action == info.actionId }
            // A custom actionId wins over imeOptions when the key is pressed.
            if (!actionLabel.isNullOrEmpty() && action == info.actionId) return actionLabel.toString()
            when (mode) {
                Mode.ACTION_LABEL_ONLY -> {
                    return actionLabel?.toString()?.takeIf { it.isNotEmpty() } ?: standardLabel(action)
                }

                Mode.ACTION_LABEL_PREFERRED -> {
                    return if (!actionLabel.isNullOrEmpty()) {
                        actionLabel.toString()
                    } else {
                        standardLabel(action)
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

    private fun standardLabel(action: Int): String = with(theme.style.enterLabels) {
        when (action) {
            EditorInfo.IME_ACTION_DONE -> done
            EditorInfo.IME_ACTION_GO -> go
            EditorInfo.IME_ACTION_NEXT -> next
            EditorInfo.IME_ACTION_PREVIOUS -> pre
            EditorInfo.IME_ACTION_SEARCH -> search
            EditorInfo.IME_ACTION_SEND -> send
            else -> default
        }
    }

    fun updateLabelOnEditorInfo(info: EditorInfo) {
        actionLabel = labelFromEditorInfo(info)
        if (keyLabel == actionLabel) return
        keyLabel = actionLabel
        broadcaster.onEnterKeyLabelUpdate(keyLabel)
    }
}
