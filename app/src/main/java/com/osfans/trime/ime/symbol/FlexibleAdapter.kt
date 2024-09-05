// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.databinding.SimpleKeyItemBinding
import splitties.resources.drawable
import splitties.resources.styledColor
import kotlin.math.min

abstract class FlexibleAdapter(
    private val theme: Theme,
) : RecyclerView.Adapter<FlexibleAdapter.ViewHolder>() {
    private val mBeans = mutableListOf<DatabaseBean>()

    // 映射条目的 id 和其在视图中位置的关系
    // 以应对增删条目时 id 和其位置的相对变化
    // [<id, position>, ...]
    private val mBeansId = mutableMapOf<Int, Int>()
    val beans get() = mBeans

    fun updateBeans(beans: List<DatabaseBean>) {
        val sorted =
            beans.sortedWith { b1, b2 ->
                when {
                    // 如果 b1 置顶而 b2 没置顶，则 b1 比 b2 小，排前面
                    b1.pinned && !b2.pinned -> -1
                    // 如果 b1 没置顶而 b2 置顶，则 b1 比 b2 大，排后面
                    !b1.pinned && b2.pinned -> 1
                    // 如果都置顶了或都没置顶，则比较 id，id 大的排前面
                    else -> b2.id.compareTo(b1.id)
                }
            }
        val prevSize = mBeans.size
        mBeans.clear()
        notifyItemRangeRemoved(0, prevSize)
        mBeans.addAll(sorted)
        notifyItemRangeChanged(0, sorted.size)
        mBeansId.clear()
        mBeans.forEachIndexed { index: Int, (id): DatabaseBean ->
            mBeansId[id] = index
        }
    }

    private fun excerptText(
        str: String,
        lines: Int = 4,
        chars: Int = 128,
    ): String =
        buildString {
            val length = str.length
            var lineBreak = -1
            for (i in 1..lines) {
                val start = lineBreak + 1 // skip previous '\n'
                val excerptEnd = min(start + chars, length)
                lineBreak = str.indexOf('\n', start)
                if (lineBreak < 0) {
                    // no line breaks remaining, substring to end of text
                    append(str.substring(start, excerptEnd))
                    break
                } else {
                    val end = min(excerptEnd, lineBreak)
                    // append one line exactly
                    appendLine(str.substring(start, end))
                }
            }
        }

    override fun getItemCount(): Int = mBeans.size

    private val mTypeface = FontManager.getTypeface("long_text_font")
    private val mLongTextColor = ColorManager.getColor("long_text_color")
    private val mKeyTextColor = ColorManager.getColor("key_text_color")
    private val mKeyLongTextSize = theme.generalStyle.keyLongTextSize
    private val mLabelTextSize = theme.generalStyle.labelTextSize

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = SimpleKeyItemBinding.inflate(LayoutInflater.from(parent.context))
        binding.root.background =
            ColorManager.getDrawable(
                parent.context,
                "long_text_back_color",
                border = theme.generalStyle.keyBorder,
                "key_long_text_border",
                roundCorner = theme.generalStyle.roundCorner,
            )
        binding.simpleKey.apply {
            typeface = mTypeface
            (mLongTextColor ?: mKeyTextColor)?.let { setTextColor(it) }
            (mKeyLongTextSize.takeIf { it > 0f } ?: mLabelTextSize.takeIf { it > 0f })
                ?.let { textSize = it.toFloat() }
        }
        return ViewHolder(binding)
    }

    inner class ViewHolder(
        binding: SimpleKeyItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        val simpleKeyText = binding.simpleKey
        val simpleKeyPin = binding.simpleKeyPin
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        with(viewHolder) {
            val bean = mBeans[position]
            simpleKeyText.text = bean.text?.let { excerptText(it) }
            simpleKeyPin.visibility = if (bean.pinned) View.VISIBLE else View.INVISIBLE
            itemView.setOnClickListener {
                onPaste(bean)
            }
            itemView.setOnLongClickListener {
                val iconColor = it.context.styledColor(android.R.attr.colorControlNormal)
                val menu = PopupMenu(it.context, it)

                fun menuItem(
                    @StringRes title: Int,
                    @DrawableRes ic: Int,
                    callback: () -> Unit,
                ) {
                    menu.menu.add(title).apply {
                        icon = it.context.drawable(ic)?.apply { setTint(iconColor) }
                        setOnMenuItemClickListener {
                            callback()
                            true
                        }
                    }
                }
                menuItem(R.string.edit, R.drawable.ic_baseline_edit_24) {
                    onEdit(bean)
                }
                if (bean.pinned) {
                    menuItem(R.string.simple_key_unpin, R.drawable.ic_outline_push_pin_24) {
                        onUnpin(bean)
                        setPinStatus(bean.id, false)
                    }
                } else {
                    menuItem(R.string.simple_key_pin, R.drawable.ic_baseline_push_pin_24) {
                        onPin(bean)
                        setPinStatus(bean.id, true)
                    }
                }
                if (showCollectButton) {
                    menuItem(R.string.collect, R.drawable.ic_baseline_star_24) {
                        onCollect(bean)
                    }
                }
                menuItem(R.string.delete, R.drawable.ic_baseline_delete_24) {
                    onDelete(bean)
                    delete(bean.id)
                }
                if (beans.isNotEmpty()) {
                    menuItem(R.string.delete_all, R.drawable.ic_baseline_delete_sweep_24) {
                        onDeleteAll()
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    menu.setForceShowIcon(true)
                }
                menu.show()
                true
            }
        }
    }

    private fun delete(id: Int) {
        val position = mBeansId.getValue(id)
        mBeans.removeAt(position)
        mBeansId.remove(id)
        for (i in position until mBeans.size) {
            mBeansId[mBeans[i].id] = i
        }
        notifyItemRemoved(position)
    }

    private fun setPinStatus(
        id: Int,
        pinned: Boolean,
    ) {
        val position = mBeansId.getValue(id)
        mBeans[position] = mBeans[position].copy(pinned = pinned)
        // 置顶会改变条目的排列顺序
        updateBeans(mBeans)
    }

    abstract fun onPaste(bean: DatabaseBean)

    abstract fun onPin(bean: DatabaseBean)

    abstract fun onUnpin(bean: DatabaseBean)

    abstract fun onEdit(bean: DatabaseBean)

    abstract fun onCollect(bean: DatabaseBean)

    abstract fun onDelete(bean: DatabaseBean)

    abstract fun onDeleteAll()

    abstract val showCollectButton: Boolean
}
