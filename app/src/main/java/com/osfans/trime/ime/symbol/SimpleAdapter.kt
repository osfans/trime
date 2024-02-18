package com.osfans.trime.ime.symbol

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.SimpleItemOneBinding
import com.osfans.trime.databinding.SimpleItemRowBinding
import splitties.dimensions.dp

class SimpleAdapter(theme: Theme, private val columnSize: Int) : RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {
    private val mBeans = mutableListOf<SimpleKeyBean>()
    private val mBeansByRows = mutableListOf<List<SimpleKeyBean>>()
    val beans get() = mBeans

    fun updateBeans(beans: List<SimpleKeyBean>) {
        val prevSize = mBeans.size
        mBeans.clear()
        notifyItemRangeRemoved(0, prevSize)
        mBeans.addAll(beans)
        notifyItemRangeInserted(0, beans.size)
        mBeansByRows.clear()
        mBeansByRows.addAll(beans.chunked(columnSize))
    }

    override fun getItemCount(): Int {
        return mBeansByRows.size
    }

    override fun getItemId(position: Int): Long {
        return position * 1000L
    }

    private val mSingleWidth = theme.liquid.getInt("single_width")
    private val mTextSize = theme.style.getFloat("label_text_size")
    private val mTextColor = ColorManager.getColor("key_text_color")
    private val mTypeface = FontManager.getTypeface("key_font")
    private val mBackground =
        ColorManager.getDrawable(
            "key_back_color",
            "key_border",
            "key_border_color",
            "round_corner",
            null,
        )

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = SimpleItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val size = parent.dp(mSingleWidth)
        val bindings = mutableListOf<SimpleItemOneBinding>()
        for (i in 0 until columnSize) {
            val sub = SimpleItemOneBinding.inflate(LayoutInflater.from(parent.context), null, false)
            bindings.add(sub)
            binding.wrapper.addView(sub.root, size, size)
        }
        val holder = ViewHolder(binding, bindings)
        holder.simpleKeyTexts.forEachIndexed { index, textView ->
            holder.wrappers[index].tag = index
            textView.apply {
                mTextSize.takeIf { it > 0f }?.let { this.textSize = it }
                mTextColor?.let { setTextColor(it) }
                this.typeface = mTypeface
                this.gravity = Gravity.CENTER
                this.ellipsize = TextUtils.TruncateAt.MARQUEE
                this.background = mBackground
            }
        }
        return holder
    }

    class ViewHolder(binding: SimpleItemRowBinding, views: List<SimpleItemOneBinding>) : RecyclerView.ViewHolder(binding.root) {
        val simpleKeyTexts = views.map { it.root.getChildAt(0) as TextView }
        val wrappers = views.map { it.root.apply { getChildAt(1).visibility = View.GONE } }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val bean = mBeansByRows[position]
        holder.simpleKeyTexts.forEachIndexed { index, textView ->
            textView.text = ""
            if (index < bean.size) {
                holder.wrappers[index].visibility = View.VISIBLE
                textView.text = bean[index].label
            } else {
                holder.wrappers[index].visibility = View.INVISIBLE
            }
            holder.wrappers[index]
                .setOnClickListener { view: View ->
                    if (view.tag != null) {
                        listener?.invoke(position * columnSize + view.tag as Int)
                    }
                }
        }
    }

    /** 添加OnItemClickListener回调 */
    private var listener: ((Int) -> Unit)? = null

    fun setListener(listener: ((Int) -> Unit)?) {
        this.listener = listener
    }
}
