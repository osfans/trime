/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.PopupWindow
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject
import splitties.views.backgroundColor

@InputScope
@Inject
class PreeditModule(
    context: Context,
    theme: Theme,
    rime: RimeSession,
    private val bar: QuickBar,
) : InputBroadcastReceiver {
    private val textBackColor = ColorManager.getColor("text_back_color")

    private val topLeftCornerRadiusOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                val radius = theme.generalStyle.layout.roundCorner
                val width = view.width
                val height = view.height
                val rect = Rect(-radius.toInt(), 0, width, (height + radius).toInt())
                outline.setRoundRect(rect, radius)
            }
        }

    val ui =
        PreeditUi(context, theme, setupPreeditView = {
            textBackColor?.let { backgroundColor = it }
        }).apply {
            root.alpha = theme.generalStyle.layout.alpha
            root.outlineProvider = topLeftCornerRadiusOutlineProvider
            root.clipToOutline = true
            preedit.setOnCursorMoveListener { position ->
                rime.launchOnReady { it.moveCursorPos(position) }
            }
        }

    private val window =
        PopupWindow(ui.root).apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            animationStyle = 0
        }

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        // TODO: 临时修复状态栏与悬浮窗同时显示，后续需优化：考虑分离数据或寻找更好的实现方式
        if (candidatesMode == PopupCandidatesMode.FORCE_SHOW) return

        ui.update(ctx.composition)
        if (ctx.composition.length > 0) {
            val (x, y) = intArrayOf(0, 0).also { bar.view.getLocationInWindow(it) }
            window.showAtLocation(bar.view, Gravity.START or Gravity.TOP, x, y)
            ui.root.post {
                window.update(x, y - ui.root.height, -1, -1)
            }
        } else {
            window.dismiss()
        }
    }

    fun onDetached() {
        window.dismiss()
    }
}
