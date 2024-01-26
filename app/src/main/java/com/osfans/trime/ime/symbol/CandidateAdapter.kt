package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.CandidateListItem
import com.osfans.trime.core.Rime
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.LiquidKeyItemBinding

// 显示长度不固定，字体大小正常的内容。用于类型 CANDIDATE, VAR_LENGTH
class CandidateAdapter(private val theme: Theme) : RecyclerView.Adapter<CandidateAdapter.ViewHolder>() {
    private val mCandidates: MutableList<CandidateListItem> = ArrayList()

    internal enum class CommentPosition {
        UNKNOWN,
        TOP,
        BOTTOM,
        RIGHT,
    }

    fun updateCandidates(candidates: List<CandidateListItem>?) {
        mCandidates.clear()
        mCandidates.addAll(candidates!!)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return mCandidates.size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = LiquidKeyItemBinding.inflate(LayoutInflater.from(parent.context))
        val isCommentHidden = Rime.getRimeOption("_hide_comment")
        if (!isCommentHidden) {
            val set = ConstraintSet()
            set.clone(binding.root)
            val commentPosition = CommentPosition.entries[theme.style.getInt("comment_position")]
            when (commentPosition) {
                CommentPosition.BOTTOM -> {
                    set.centerHorizontally(binding.comment.id, ConstraintSet.PARENT_ID)
                    set.centerHorizontally(binding.candidate.id, ConstraintSet.PARENT_ID)
                    set.connect(
                        binding.comment.id,
                        ConstraintSet.TOP,
                        binding.candidate.id,
                        ConstraintSet.BOTTOM,
                    )
                    set.connect(
                        binding.comment.id,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                    )
                }

                CommentPosition.TOP -> {
                    set.centerHorizontally(binding.comment.id, ConstraintSet.PARENT_ID)
                    set.centerHorizontally(binding.candidate.id, ConstraintSet.PARENT_ID)
                    set.connect(
                        binding.comment.id,
                        ConstraintSet.BOTTOM,
                        binding.candidate.id,
                        ConstraintSet.TOP,
                    )
                    set.connect(
                        binding.comment.id,
                        ConstraintSet.TOP,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.TOP,
                    )
                }

                CommentPosition.RIGHT, CommentPosition.UNKNOWN -> {}
                else -> {}
            }
            set.applyTo(binding.root)
        }
        binding.comment.visibility = View.GONE
        return ViewHolder(binding)
    }

    class ViewHolder(binding: LiquidKeyItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val candidate: TextView
        val comment: TextView

        init {
            candidate = binding.candidate
            comment = binding.comment
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val (comment, text) = mCandidates[position]
        val candidateFont = FontManager.getTypeface("candidate_font")
        val commentFont = FontManager.getTypeface("comment_font")
        holder.candidate.typeface = candidateFont
        holder.comment.typeface = commentFont
        holder.candidate.text = text
        if (holder.comment.visibility == View.VISIBLE) {
            holder.comment.text = comment
        }
        val candidateSize = theme.style.getFloat("candidate_text_size")
        val commentSize = theme.style.getFloat("comment_text_size")
        if (candidateSize > 0) holder.candidate.textSize = candidateSize
        if (commentSize > 0) holder.comment.textSize = commentSize
        val candidateColor = theme.colors.getColor("candidate_text_color")!!
        val commentColor = theme.colors.getColor("comment_text_color")!!
        holder.candidate.setTextColor(candidateColor)
        holder.comment.setTextColor(commentColor)

        //  点击前后必须使用相同类型的背景，或者全部为背景图，或者都为背景色
        // 如果直接使用background，会造成滚动时部分内容的背景填充错误的问题
        val background =
            theme.colors.getDrawable(
                "key_back_color",
                "key_border",
                "key_border_color",
                "round_corner",
                null,
            )
        if (background != null) holder.itemView.background = background

        // 如果设置了回调，则设置点击事件
        if (listener != null) {
            holder.itemView.setOnClickListener { view: View? -> listener!!(position) }
        }

        // 点击时产生背景变色效果
        holder.itemView.setOnTouchListener { view: View?, motionEvent: MotionEvent ->
            val hilited = theme.colors.getColor("hilited_candidate_text_color")!!
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                holder.candidate.setTextColor(hilited)
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
