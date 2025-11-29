/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.popup

import android.graphics.Rect

sealed class PopupAction {

    abstract val viewId: Int

    data class PreviewAction(
        override val viewId: Int,
        val content: String,
        val bounds: Rect,
    ) : PopupAction()

    data class PreviewUpdateAction(
        override val viewId: Int,
        val content: String,
    ) : PopupAction()

    data class DismissAction(
        override val viewId: Int,
    ) : PopupAction()

    data class ShowKeyboardAction(
        override val viewId: Int,
        val keys: List<String>,
        val bounds: Rect,
    ) : PopupAction()

    data class ChangeFocusAction(
        override val viewId: Int,
        val x: Float,
        val y: Float,
        var outResult: Boolean = false,
    ) : PopupAction()

    data class TriggerAction(
        override val viewId: Int,
        var outAction: String? = null,
    ) : PopupAction()
}
