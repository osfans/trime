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
import com.osfans.trime.core.CandidateListItem
import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.LiquidEntryViewBinding
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.topOfParent

// 显示长度不固定，字体大小正常的内容。用于类型 CANDIDATE, VAR_LENGTH
class CandidateAdapter(private val theme: Theme) : BaseQuickAdapter<CandidateListItem, CandidateAdapter.ViewHolder>() {
    enum class CommentPosition {
        UNKNOWN,
        TOP,
        BOTTOM,
        RIGHT,
    }

    private val mCandidateTextSize = theme.generalStyle.candidateTextSize.toFloat().coerceAtLeast(1f)
    private val mCandidateFont = FontManager.getTypeface("candidate_font")
    private val mCandidateTextColor = ColorManager.getColor("candidate_text_color")
    private val mHilitedCandidateBackColor = ColorManager.getColor("hilited_candidate_back_color")
    private val mCommentPosition = theme.generalStyle.commentPosition
    private val mCommentTextSize = theme.generalStyle.commentTextSize.toFloat().coerceAtLeast(1f)
    private val mCommentFont = FontManager.getTypeface("comment_font")
    private val mCommentTextColor = ColorManager.getColor("comment_text_color")

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = LiquidEntryViewBinding.inflate(LayoutInflater.from(parent.context))
        binding.root.background =
            StateListDrawable().apply {
                addState(
                    intArrayOf(),
                    ColorManager.getDrawable(
                        context = context,
                        key = "key_back_color",
                        border = theme.generalStyle.candidateBorder,
                        borderColorKey = "key_border_color",
                        roundCorner = theme.generalStyle.roundCorner,
                    ),
                )
                mHilitedCandidateBackColor?.let {
                    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(it))
                }
            }
        binding.candidate.apply {
            textSize = mCandidateTextSize
            typeface = mCandidateFont
            mCandidateTextColor?.let { setTextColor(it) }
        }
        val isCommentHidden = Rime.getRimeOption("_hide_comment")
        if (isCommentHidden) return ViewHolder(binding)

        binding.comment.apply {
            visibility = View.GONE
            textSize = mCommentTextSize
            typeface = mCommentFont
            mCommentTextColor?.let { setTextColor(it) }
        }
        val candidate = binding.candidate
        val comment = binding.comment
        when (mCommentPosition) {
            CommentPosition.BOTTOM -> {
                candidate.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    centerHorizontally()
                }
                comment.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    below(candidate)
                    centerHorizontally()
                    bottomOfParent()
                }
            }

            CommentPosition.TOP -> {
                candidate.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    centerHorizontally()
                }
                comment.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topOfParent()
                    above(candidate)
                    centerHorizontally()
                }
            }

            CommentPosition.RIGHT, CommentPosition.UNKNOWN -> {}
        }
        return ViewHolder(binding)
    }

    class ViewHolder(binding: LiquidEntryViewBinding) : RecyclerView.ViewHolder(binding.root) {
        val candidate: TextView = binding.candidate
        val comment: TextView = binding.comment
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: CandidateListItem?,
    ) {
        item?.run {
            holder.candidate.text = text
            holder.comment.text = comment
        }
    }
}
