// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.SimpleItemOneBinding
import com.osfans.trime.databinding.SimpleItemRowBinding
import splitties.dimensions.dp

class SimpleAdapter(
    private val theme: Theme,
    private val columnSize: Int,
) : RecyclerView.Adapter<SimpleAdapter.ViewHolder>() {
    private val mBeans = mutableListOf<SimpleKeyBean>()
    private val mBeansByRows = mutableListOf<List<SimpleKeyBean>>()

    fun updateBeans(beans: List<SimpleKeyBean>) {
        val prevSize = mBeansByRows.size
        mBeans.clear()
        notifyItemRangeRemoved(0, prevSize)
        mBeans.addAll(beans)
        mBeansByRows.clear()
        mBeansByRows.addAll(beans.chunked(columnSize))
        notifyItemRangeInserted(0, mBeansByRows.size)
    }

    override fun getItemCount(): Int = mBeansByRows.size

    override fun getItemId(position: Int): Long = position * 1000L

    private val mSingleWidth = theme.liquidKeyboard.singleWidth
    private val mSingleHeight = theme.liquidKeyboard.keyHeight
    private val mStringMarginX = theme.liquidKeyboard.marginX
    private val mTextSize = theme.generalStyle.labelTextSize
    private val mTextColor = ColorManager.getColor("key_text_color")
    private val mTypeface = FontManager.getTypeface("key_font")

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding =
            SimpleItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val width = parent.dp(mSingleWidth)
        val height = parent.dp(mSingleHeight)
        val marginX = parent.dp(mStringMarginX).toInt()
        val bindings = mutableListOf<SimpleItemOneBinding>()
        for (i in 0 until columnSize) {
            val sub = SimpleItemOneBinding.inflate(LayoutInflater.from(parent.context), null, false)
            bindings.add(sub)
            val layoutParams = LinearLayout.LayoutParams(width, height)
            layoutParams.setMargins(marginX, 0, marginX, 0)
            binding.wrapper.addView(sub.root, layoutParams)
        }
        val holder = ViewHolder(binding, bindings)

        holder.simpleKeyTexts.forEachIndexed { index, textView ->
            holder.wrappers[index].tag = index
            textView.apply {
                textSize = mTextSize
                setTextColor(mTextColor)
                typeface = mTypeface
                gravity = Gravity.CENTER
                ellipsize = TextUtils.TruncateAt.MARQUEE
                background =
                    ColorManager.getDrawable(
                        "key_back_color",
                        "key_border_color",
                        dp(theme.generalStyle.keyBorder),
                        dp(theme.generalStyle.roundCorner),
                        cache = false,
                    )
            }
        }
        return holder
    }

    class ViewHolder(
        binding: SimpleItemRowBinding,
        views: List<SimpleItemOneBinding>,
    ) : RecyclerView.ViewHolder(binding.root) {
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
                        listener?.invoke(bean[index], position * columnSize + view.tag as Int)
                    }
                }
        }
    }

    /** 添加OnItemClickListener回调 */
    private var listener: (SimpleKeyBean.(Int) -> Unit)? = null

    fun setListener(listener: (SimpleKeyBean.(Int) -> Unit)?) {
        this.listener = listener
    }
}
