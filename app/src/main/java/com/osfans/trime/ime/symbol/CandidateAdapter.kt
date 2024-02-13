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
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.LiquidEntryViewBinding
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.topOfParent

// 显示长度不固定，字体大小正常的内容。用于类型 CANDIDATE, VAR_LENGTH
class CandidateAdapter(private val theme: Theme) : RecyclerView.Adapter<CandidateAdapter.ViewHolder>() {
    private val mCandidates = mutableListOf<CandidateListItem>()

    internal enum class CommentPosition {
        UNKNOWN,
        TOP,
        BOTTOM,
        RIGHT,
    }

    fun updateCandidates(candidates: List<CandidateListItem>) {
        mCandidates.clear()
        mCandidates.addAll(candidates)
        notifyItemRangeChanged(0, candidates.size)
    }

    override fun getItemCount(): Int {
        return mCandidates.size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = LiquidEntryViewBinding.inflate(LayoutInflater.from(parent.context))
        val candidate = binding.candidate
        val comment = binding.comment
        val isCommentHidden = Rime.getRimeOption("_hide_comment")
        if (!isCommentHidden) {
            when (CommentPosition.entries[theme.style.getInt("comment_position")]) {
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
        }
        binding.comment.visibility = View.GONE
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
        holder.candidate.apply {
            this.text = text
            textSize = theme.style.getFloat("candidate_text_size").coerceAtLeast(1f)
            typeface = FontManager.getTypeface("candidate_font")
            theme.colors.getColor("candidate_text_color")?.let { setTextColor(it) }
        }
        holder.comment.apply {
            this.text = comment
            textSize = theme.style.getFloat("comment_text_size").coerceAtLeast(1f)
            typeface = FontManager.getTypeface("comment_font")
            theme.colors.getColor("comment_text_color")?.let { setTextColor(it) }
        }

        //  点击前后必须使用相同类型的背景，或者全部为背景图，或者都为背景色
        // 如果直接使用background，会造成滚动时部分内容的背景填充错误的问题
        holder.itemView.background =
            theme.colors.getDrawable(
                "key_back_color",
                "key_border",
                "key_border_color",
                "round_corner",
                null,
            )

        // 如果设置了回调，则设置点击事件
        holder.itemView.setOnClickListener { listener?.invoke(position) }

        // 点击时产生背景变色效果
        holder.itemView.setOnTouchListener { _, motionEvent: MotionEvent ->
            val hilited = theme.colors.getColor("hilited_candidate_text_color")
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                hilited?.let { holder.candidate.setTextColor(it) }
            }
            false
        }
    }

    /** 添加 候选点击事件 Listener 回调 *  */
    private var listener: ((Int) -> Unit)? = null

    /** @param listener position
     * */
    fun setListener(listener: ((Int) -> Unit)?) {
        this.listener = listener
    }
}
