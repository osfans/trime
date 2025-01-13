// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.graphics.drawable.ColorDrawable
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.pressHighlightDrawable
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.core.Ui

class SuggestionItemUi(
    override val ctx: Context,
    theme: Theme,
) : Ui {
    private val firstBackColorH = ColorManager.getColor("hilited_candidate_back_color")!!

    override val root = constraintLayout {}

    fun update(
        item: SuggestionViewItem,
        isHighlighted: Boolean,
    ) {
        root.removeAllViews()
        root.addView(item.view)

        root.background =
            if (isHighlighted) {
                ColorDrawable(firstBackColorH)
            } else {
                pressHighlightDrawable(firstBackColorH)
            }
    }
}
