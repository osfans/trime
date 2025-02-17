// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.PaintDrawable
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.rippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityCenter
import splitties.views.horizontalPadding
import splitties.views.recyclerview.horizontalLayoutManager

class LiquidTabsUi(
    override val ctx: Context,
    val theme: Theme,
) : Ui {
    inner class TabUi : Ui {
        override val ctx = this@LiquidTabsUi.ctx

        val text =
            textView {
                textSize = theme.generalStyle.candidateTextSize
                typeface = FontManager.getTypeface("candidate_font")
                setTextColor(ColorManager.getColor("candidate_text_color"))
            }

        override val root =
            frameLayout {
                add(
                    text,
                    lParams {
                        gravity = gravityCenter
                        horizontalPadding = dp(theme.generalStyle.candidatePadding)
                    },
                )
                background = rippleDrawable(ColorManager.getColor("hilited_candidate_back_color"))
            }

        fun setText(str: String) {
            text.text = str
        }

        fun setActive(active: Boolean) {
            val color =
                if (active) {
                    ColorManager.getColor(
                        "hilited_candidate_text_color",
                    )
                } else {
                    ColorManager.getColor("candidate_text_color")
                }
            val background = if (active) ColorManager.getColor("hilited_candidate_back_color") else Color.TRANSPARENT
            text.setTextColor(color)
            root.background =
                PaintDrawable(background).apply {
                    setCornerRadius(
                        theme.generalStyle.layout.roundCorner,
                    )
                }
        }
    }

    private var onTabClick: ((Int) -> Unit)? = null

    private class TabUiHolder(
        val ui: LiquidTabsUi.TabUi,
    ) : RecyclerView.ViewHolder(ui.root)

    private val adapter by lazy {
        object : BaseQuickAdapter<TabTag, TabUiHolder>() {
            private var selected = -1

            override fun onCreateViewHolder(
                context: Context,
                parent: ViewGroup,
                viewType: Int,
            ) = TabUiHolder(TabUi())

            override fun onBindViewHolder(
                holder: TabUiHolder,
                position: Int,
                item: TabTag?,
            ) {
                holder.ui.apply {
                    setText(item!!.text)
                    setActive(position == selected)
                    root.run {
                        layoutParams = ViewGroup.LayoutParams(wrapContent, matchParent)
                    }
                }
            }

            override fun submitList(list: List<TabTag>?) {
                selected = -1
                super.submitList(list)
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

    fun setTabs(tags: List<TabTag>) {
        adapter.submitList(tags)
    }

    fun activateTab(index: Int) {
        adapter.activateTab(index)
        root.post { root.scrollToPosition(index) }
    }

    fun setOnTabClickListener(listener: ((Int) -> Unit)? = null) {
        onTabClick = listener
    }
}
