// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import android.content.Context
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.PopupMenu
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.adapter.CompactCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxVerticalDecoration
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.dsl.recyclerview.recyclerView
import kotlin.math.max

@InputScope
@Inject
class CompactCandidateModule(
    val context: Context,
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
    val bar: QuickBar,
) : InputBroadcastReceiver {
    private val _unrolledCandidateOffset =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val unrolledCandidateOffset = _unrolledCandidateOffset.asSharedFlow()

    fun refreshUnrolled() {
        runBlocking {
            _unrolledCandidateOffset.emit(adapter.before + view.childCount)
        }
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesUpdated,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (adapter.run { isLastPage && itemCount == layoutManager.childCount }),
        )
    }

    val adapter by lazy {
        CompactCandidateViewAdapter(theme).apply {
            setOnItemClickListener { _, _, position ->
                rime.launchOnReady { it.selectCandidate(before + position) }
            }
            setOnItemLongClickListener { _, view, position ->
                showCandidateAction(before + position, items[position].text, view)
                true
            }
        }
    }

    val layoutManager by lazy {
        object : FlexboxLayoutManager(context) {
            override fun canScrollHorizontally(): Boolean = false

            override fun canScrollVertically(): Boolean = false

            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                refreshUnrolled()
            }
        }
    }

    private val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.generalStyle.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            ColorManager.getColor("candidate_separator_color")?.let { paint.color = it }
        }
    }

    val view by lazy {
        context.recyclerView(R.id.candidate_view) {
            adapter = this@CompactCandidateModule.adapter
            layoutManager = this@CompactCandidateModule.layoutManager
            addItemDecoration(FlexboxVerticalDecoration(separatorDrawable))
        }
    }

    private var candidateActionMenu: PopupMenu? = null

    fun showCandidateAction(
        idx: Int,
        text: String,
        view: View,
    ) {
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        service.lifecycleScope.launch {
            InputFeedbackManager.keyPressVibrate(view, longPress = true)
            candidateActionMenu =
                PopupMenu(context, view).apply {
                    menu
                        .add(
                            buildSpannedString {
                                bold {
                                    fun coloredOrNot(action: SpannableStringBuilder.() -> Unit) =
                                        ColorManager.getColor("hilited_candidate_text_color")?.let {
                                            color(it) { action() }
                                        } ?: action(this)
                                    coloredOrNot { append(text) }
                                }
                            },
                        ).apply {
                            isEnabled = false
                        }
                    menu.add(R.string.forget_this_word).setOnMenuItemClickListener {
                        rime.runIfReady { forgetCandidate(idx) }
                        true
                    }
                    setOnDismissListener {
                        candidateActionMenu = null
                    }
                    show()
                }
        }
    }
}
