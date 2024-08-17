// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui.always.switches

import android.content.Context
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.gravityCenter

class SwitchUi(override val ctx: Context, private val theme: Theme) : Ui {
    var enabled: Int = -1

    private val label =
        textView {
            textSize = theme.generalStyle.candidateTextSize.toFloat()
            typeface = FontManager.getTypeface("candidate_font")
            ColorManager.getColor("candidate_text_color")?.let { setTextColor(it) }
        }

    override val root =
        frameLayout {
            add(label, lParams { gravity = gravityCenter })
        }

    fun setLabel(str: String) {
        label.text = str
    }
}
