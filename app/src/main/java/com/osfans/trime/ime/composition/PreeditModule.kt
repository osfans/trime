/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.backgroundColor

@InputScope
@Inject
class PreeditModule(
    context: Context,
    theme: Theme,
    rime: RimeSession,
) : InputBroadcastReceiver {
    private val topLeftCornerRadiusOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                val radius = context.dp(theme.generalStyle.layout.roundCorner)
                val width = view.width
                val height = view.height
                val rect = Rect(-radius.toInt(), 0, width, (height + radius).toInt())
                outline.setRoundRect(rect, radius)
            }
        }

    val ui =
        PreeditUi(
            context,
            theme,
            setupPreeditView = {
                backgroundColor = ColorManager.getColor("text_back_color")
            },
            onMoveCursor = { pos -> rime.launchOnReady { it.moveCursorPos(pos) } },
        ).apply {
            root.alpha = theme.generalStyle.layout.alpha / 255f
            root.outlineProvider = topLeftCornerRadiusOutlineProvider
            root.clipToOutline = true
            root.visibility = View.INVISIBLE
        }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(ui.root)

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        // TODO: 临时修复状态栏与悬浮窗同时显示，后续需优化：考虑分离数据或寻找更好的实现方式
        if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) return

        ui.update(ctx.composition)
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
        if (ctx.composition.length > 0) {
            touchEventReceiverWindow.show()
        } else {
            touchEventReceiverWindow.dismiss()
        }
    }
}
