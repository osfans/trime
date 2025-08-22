// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.databinding.LiquidEntryViewBinding
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

// 显示长度不固定，字体大小正常的内容。用于类型 TABS, VAR_LENGTH
class VarLengthAdapter(
    private val theme: Theme,
) : BaseQuickAdapter<Pair<String, String>, VarLengthAdapter.ViewHolder>() {
    private val mCandidateTextSize =
        theme.generalStyle.candidateTextSize
            .toFloat()
            .coerceAtLeast(1f)
    private val mCandidateFont = FontManager.getTypeface("candidate_font")
    private val mCandidateTextColor = ColorManager.getColor("candidate_text_color")
    private val mHilitedCandidateBackColor = ColorManager.getColor("hilited_candidate_back_color")
    private val mCommentPosition = theme.generalStyle.commentPosition
    private val mCommentTextSize =
        theme.generalStyle.commentTextSize
            .toFloat()
            .coerceAtLeast(1f)
    private val mCommentFont = FontManager.getTypeface("comment_font")
    private val mCommentTextColor = ColorManager.getColor("comment_text_color")

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = LiquidEntryViewBinding.inflate(LayoutInflater.from(parent.context))
        binding.root.run {
            background =
                StateListDrawable().apply {
                    addState(
                        intArrayOf(),
                        ColorManager.getDrawable(
                            "key_back_color",
                            "key_border_color",
                            dp(theme.generalStyle.candidateBorder),
                            dp(theme.generalStyle.roundCorner),
                            cache = false,
                        ),
                    )
                    addState(
                        intArrayOf(android.R.attr.state_pressed),
                        ColorDrawable(
                            mHilitedCandidateBackColor,
                        ),
                    )
                }

            minimumWidth = dp(40)
            val size = theme.generalStyle.candidatePadding
            setPaddingDp(size, 0, size, 0)
            layoutParams =
                FlexboxLayoutManager
                    .LayoutParams(wrapContent, wrapContent)
                    .apply { flexGrow = 1f }
        }
        binding.first.apply {
            textSize = mCandidateTextSize
            typeface = mCandidateFont
            mCandidateTextColor?.let { setTextColor(it) }
        }
        val isCommentHidden = Rime.getRimeOption("_hide_comment")
        if (isCommentHidden) return ViewHolder(binding)

        binding.second.apply {
            visibility = View.GONE
            textSize = mCommentTextSize
            typeface = mCommentFont
            setTextColor(mCommentTextColor)
        }
        val candidate = binding.first
        val comment = binding.first
        when (mCommentPosition) {
            GeneralStyle.CommentPosition.BOTTOM -> {
                candidate.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    centerHorizontally()
                }
                comment.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    below(candidate)
                    centerHorizontally()
                    bottomOfParent()
                }
            }

            GeneralStyle.CommentPosition.TOP -> {
                candidate.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    centerHorizontally()
                }
                comment.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topOfParent()
                    above(candidate)
                    centerHorizontally()
                }
            }
            else -> {}
        }
        return ViewHolder(binding)
    }

    class ViewHolder(
        binding: LiquidEntryViewBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        val first: TextView = binding.first
        val second: TextView = binding.second
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: Pair<String, String>?,
    ) {
        item?.run {
            holder.first.text = first
            holder.second.text = second
        }
    }
}
