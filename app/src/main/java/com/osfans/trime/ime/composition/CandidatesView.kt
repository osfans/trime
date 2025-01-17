/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.popup.PagedCandidatesUi
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding
import splitties.views.verticalPadding

@SuppressLint("ViewConstructor")
class CandidatesView(
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
) : ConstraintLayout(service) {
    private val ctx = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    private var menu = RimeProto.Context.Menu()
    private var inputComposition = RimeProto.Context.Composition()

    private val preeditUi =
        PreeditUi(ctx, theme).apply {
            preedit.setOnCursorMoveListener { position ->
                rime.launchOnReady { it.moveCursorPos(position) }
            }
        }

    private val candidatesUi =
        PagedCandidatesUi(ctx, theme).apply {
            setOnClickListener { type, position ->
                when (type) {
                    PagedCandidatesUi.ClickType.CANDIDATE -> {
                        rime.launchOnReady { it.selectPagedCandidate(position) }
                    }
                    PagedCandidatesUi.ClickType.PREV_PAGE -> {
                        val value = RimeKeyMapping.keyCodeToVal(KeyEvent.KEYCODE_PAGE_UP)
                        rime.launchOnReady { it.processKey(value, 0u) }
                    }
                    PagedCandidatesUi.ClickType.NEXT_PAGE -> {
                        val value = RimeKeyMapping.keyCodeToVal(KeyEvent.KEYCODE_PAGE_DOWN)
                        rime.launchOnReady { it.processKey(value, 0u) }
                    }
                }
            }
        }

    fun update(ctx: RimeProto.Context) {
        inputComposition = ctx.composition
        menu = ctx.menu
        updateUi()
    }

    private fun evaluateVisibility(): Boolean =
        !inputComposition.preedit.isNullOrEmpty() ||
            menu.candidates.isNotEmpty()

    private fun updateUi() {
        if (evaluateVisibility()) {
            preeditUi.update(inputComposition)
            // if CandidatesView can be shown, rime engine is ready most of the time,
            // so it should be safety to get option immediately
            val isHorizontalLayout = rime.run { getRuntimeOption("_horizontal") }
            candidatesUi.update(menu, isHorizontalLayout)
        }
    }

    init {
        minWidth = dp(theme.generalStyle.layout.minWidth)
        verticalPadding = dp(theme.generalStyle.layout.marginX)
        horizontalPadding = dp(theme.generalStyle.layout.marginY)
        background =
            ColorManager.getDrawable(
                ctx,
                "candidate_background",
                theme.generalStyle.candidateBorder,
                "candidate_border_color",
                theme.generalStyle.candidateBorderRound,
            )
        add(
            preeditUi.root,
            lParams(wrapContent, wrapContent) {
                topOfParent()
                startOfParent()
            },
        )
        add(
            candidatesUi.root,
            lParams(matchConstraints, wrapContent) {
                matchConstraintMinWidth = wrapContent
                below(preeditUi.root)
                centerHorizontally()
                bottomOfParent()
            },
        )

        isFocusable = false
        layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
    }
}
