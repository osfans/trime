/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewOutlineProvider
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.horizontalPadding

class PreeditDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {

    private val context: ContextThemeWrapper by instance()
    private val scope: ThemeScope by instance()
    private val rime: RimeSession by instance()

    private val theme: Theme
        get() = scope.theme

    val ui =
        PreeditUi(
            context,
            scope,
            setupPreeditView = {
                val startRadius = dp(theme.preedit.topStartRadius)
                val endRadius = dp(theme.preedit.topEndRadius)
                val radii = if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                    floatArrayOf(startRadius, startRadius, endRadius, endRadius, 0f, 0f, 0f, 0f)
                } else {
                    floatArrayOf(endRadius, endRadius, startRadius, startRadius, 0f, 0f, 0f, 0f)
                }
                background = GradientDrawable().apply {
                    setColor(scope.colors.textBackColor)
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = radii
                }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                horizontalPadding = dp(theme.preedit.horizontalPadding)
            },
            onMoveCursor = { pos -> rime.launchOnReady { it.moveCursorPos(pos) } },
        ).apply {
            root.alpha = theme.preedit.alpha
            root.visibility = View.INVISIBLE
        }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(ui.root)

    override fun onCompositionUpdate(data: CompositionProto) {
        ui.update(data)
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
        if (data.length > 0) {
            touchEventReceiverWindow.show()
        } else {
            touchEventReceiverWindow.dismiss()
        }
    }
}
