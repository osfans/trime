package com.osfans.trime.ime.symbol

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.SimpleItemOneBinding
import com.osfans.trime.databinding.SimpleItemRowBinding
import splitties.dimensions.dp
import splitties.views.dsl.core.add

class SimpleAdapter(private val theme: Theme, private val columnSize: Int) : RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {
    private val mBeans = mutableListOf<SimpleKeyBean>()
    private val mBeansByRows = mutableListOf<List<SimpleKeyBean>>()
    val beans: List<SimpleKeyBean>
        get() = mBeans

    fun updateBeans(beans: List<SimpleKeyBean>) {
        mBeans.clear()
        mBeans.addAll(beans)
        mBeansByRows.clear()
        mBeansByRows.addAll(beans.chunked(columnSize))
        notifyItemRangeChanged(0, mBeans.size)
    }

    override fun getItemCount(): Int {
        return mBeansByRows.size
    }

    override fun getItemId(position: Int): Long {
        return position * 1000L
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = SimpleItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val size = parent.dp(theme.liquid.getInt("single_width"))
        val bindings = mutableListOf<SimpleItemOneBinding>()
        for (i in 0 until columnSize) {
            val sub = SimpleItemOneBinding.inflate(LayoutInflater.from(parent.context), null, false)
            bindings.add(sub)
            binding.wrapper.addView(sub.root, size, size)
        }
        val holder = ViewHolder(binding, bindings)
        for ((i, textView) in holder.simpleKeyTexts.withIndex()) {
            holder.wrappers[i].tag = i
            textView.apply {
                theme.style.getFloat("label_text_size").takeIf { it > 0f }?.let { textSize = it }
                theme.colors.getColor("key_text_color")?.let { setTextColor(it) }
                typeface = FontManager.getTypeface("key_font")
                gravity = Gravity.CENTER
                ellipsize = TextUtils.TruncateAt.MARQUEE
                background =
                    theme.colors.getDrawable(
                        "key_back_color",
                        "key_border",
                        "key_border_color",
                        "round_corner",
                        null,
                    )
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
        for ((i, keyText) in holder.simpleKeyTexts.withIndex()) {
            keyText.text = ""
            if (i < bean.size) {
                holder.wrappers[i].visibility = View.VISIBLE
                keyText.text = bean[i].label
            } else {
                holder.wrappers[i].visibility = View.INVISIBLE
            }
            holder.wrappers[i]
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
