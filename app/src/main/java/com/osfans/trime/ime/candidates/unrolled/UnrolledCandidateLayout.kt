// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.performance.InputLatencyLog
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class UnrolledCandidateLayout(
    context: Context,
    private val scope: ThemeScope,
) : ConstraintLayout(context) {
    private val theme: Theme
        get() = scope.theme

    var onCandidatesDrawn: (() -> Unit)? = null
    var onCandidatesLaidOut: (() -> Unit)? = null

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        onCandidatesLaidOut?.invoke()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (InputLatencyLog.enabled && isShown && isLaidOut) onCandidatesDrawn?.invoke()
    }

    val recyclerView =
        recyclerView {
            isVerticalScrollBarEnabled = false
        }

    init {
        id = R.id.unrolled_candidate_view
        background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.style.candidateBorder),
                dp(theme.style.candidateBorderRound),
            )

        add(
            recyclerView,
            lParams {
                centerInParent()
            },
        )
    }

    fun resetPosition() {
        recyclerView.scrollToPosition(0)
    }

    /** Restyles the container background after a scheme switch. */
    fun refreshColors() {
        background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.style.candidateBorder),
                dp(theme.style.candidateBorderRound),
            )
    }
}
