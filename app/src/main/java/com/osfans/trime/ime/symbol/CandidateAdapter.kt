package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
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
class CandidateAdapter(theme: Theme) : RecyclerView.Adapter<CandidateAdapter.ViewHolder>() {
    private val mCandidates = mutableListOf<CandidateListItem>()

    internal enum class CommentPosition {
        UNKNOWN,
        TOP,
        BOTTOM,
        RIGHT,
    }

    fun updateCandidates(candidates: List<CandidateListItem>) {
        val prevSize = mCandidates.size
        mCandidates.clear()
        notifyItemRangeRemoved(0, prevSize)
        mCandidates.addAll(candidates)
        notifyItemRangeInserted(0, candidates.size)
    }

    override fun getItemCount(): Int {
        return mCandidates.size
    }

    private val mCandidateTextSize = theme.style.getFloat("candidate_text_size").coerceAtLeast(1f)
    private val mCandidateFont = FontManager.getTypeface("candidate_font")
    private val mCandidateTextColor = ColorManager.getColor("candidate_text_color")
    private val mHilitedCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")
    private val mCommentPosition = theme.style.getInt("comment_position")
    private val mCommentTextSize = theme.style.getFloat("comment_text_size").coerceAtLeast(1f)
    private val mCommentFont = FontManager.getTypeface("comment_font")
    private val mCommentTextColor = ColorManager.getColor("comment_text_color")

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = LiquidEntryViewBinding.inflate(LayoutInflater.from(parent.context))
        binding.root.background =
            ColorManager.getDrawable(
                parent.context,
                "key_back_color",
                "key_border",
                "key_border_color",
                "round_corner",
            )
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
        when (CommentPosition.entries[mCommentPosition]) {
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val (comment, text) = mCandidates[position]
        holder.candidate.text = text
        holder.comment.text = comment

        // 如果设置了回调，则设置点击事件
        holder.itemView.setOnClickListener { listener?.invoke(position) }

        // 点击时产生背景变色效果
        holder.itemView.setOnTouchListener { _, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                mHilitedCandidateTextColor?.let { holder.candidate.setTextColor(it) }
            }
            false
        }
    }

    /** 添加 候选点击事件 Listener 回调 */
    private var listener: ((Int) -> Unit)? = null

    /** @param listener position
     * */
    fun setListener(listener: ((Int) -> Unit)?) {
        this.listener = listener
    }
}
