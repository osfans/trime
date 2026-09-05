// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.LiquidData
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityCenter
import splitties.views.horizontalPadding
import splitties.views.recyclerview.horizontalLayoutManager

class LiquidTabsUi(
    override val ctx: Context,
    private val scope: ThemeScope,
) : Ui {
    private val theme: Theme get() = scope.theme

    inner class TabUi : Ui {
        override val ctx = this@LiquidTabsUi.ctx
        private val cornerRadius = ctx.dp(theme.generalStyle.candidateCornerRadius)

        val text =
            textView {
                textSize = theme.generalStyle.candidateTextSize
                typeface = FontManager.getTypeface("candidate_font")
                setTextColor(scope.colors.candidateTextColor)
            }

        override val root =
            view(::GestureFrame) {
                minimumWidth = dp(40)
                add(
                    text,
                    lParams {
                        gravity = gravityCenter
                        horizontalPadding = dp(theme.generalStyle.candidatePadding)
                    },
                )
                background =
                    roundedRippleDrawable(scope.colors.hilitedCandidateBackColor, cornerRadius)
            }

        fun setText(str: String) {
            text.text = str
        }

        fun setActive(active: Boolean) {
            val color =
                if (active) scope.colors.hilitedCandidateTextColor else scope.colors.candidateTextColor
            val contentColor =
                if (active) scope.colors.hilitedCandidateBackColor else Color.TRANSPARENT
            text.setTextColor(color)
            root.background =
                roundedRippleDrawable(scope.colors.hilitedCandidateBackColor, cornerRadius, contentColor)
        }
    }

    private var onTabClick: ((Int) -> Unit)? = null

    private class TabUiHolder(
        val ui: TabUi,
    ) : RecyclerView.ViewHolder(ui.root)

    private val adapter by lazy {
        object : BaseQuickAdapter<LiquidData.Tag, TabUiHolder>() {
            private var selected = -1

            override fun onCreateViewHolder(
                context: Context,
                parent: ViewGroup,
                viewType: Int,
            ) = TabUiHolder(TabUi())

            override fun onBindViewHolder(
                holder: TabUiHolder,
                position: Int,
                item: LiquidData.Tag?,
            ) {
                holder.ui.apply {
                    setText(item!!.label)
                    setActive(position == selected)
                    root.run {
                        layoutParams = ViewGroup.LayoutParams(wrapContent, matchParent)
                    }
                }
            }

            override fun submitList(list: List<LiquidData.Tag>?, commitCallback: Runnable?) {
                selected = -1
                super.submitList(list, commitCallback)
            }

            fun activateTab(position: Int) {
                if (position == selected) return
                notifyItemChanged(selected)
                selected = position
                notifyItemChanged(position)
            }
        }.apply {
            setOnItemClickListener { _, _, position ->
                onTabClick?.invoke(position)
            }
        }
    }

    override val root =
        recyclerView {
            layoutManager = horizontalLayoutManager()
            adapter = this@LiquidTabsUi.adapter
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }

    fun setTags(tags: List<LiquidData.Tag>) {
        adapter.submitList(tags)
    }

    fun activateTab(index: Int) {
        adapter.activateTab(index)
        root.post { root.scrollToPosition(index) }
    }

    fun setOnTabClickListener(listener: ((Int) -> Unit)? = null) {
        onTabClick = listener
    }

    /** Restyles the tabs after a scheme switch; rows re-apply colors on rebind. */
    fun refreshColors() {
        adapter.notifyDataSetChanged()
    }
}
