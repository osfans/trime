// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseDifferAdapter
import com.osfans.trime.R
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.Theme
import splitties.resources.drawable
import splitties.resources.styledColor
import kotlin.math.min

abstract class FlexibleAdapter(
    private val theme: Theme,
) : BaseDifferAdapter<DatabaseBean, FlexibleAdapter.ViewHolder>(diffCallback) {
    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<DatabaseBean>() {
                override fun areItemsTheSame(
                    oldItem: DatabaseBean,
                    newItem: DatabaseBean,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: DatabaseBean,
                    newItem: DatabaseBean,
                ): Boolean = oldItem == newItem
            }

        private fun excerptText(
            str: String,
            lines: Int = 4,
            chars: Int = 128,
        ): String = buildString {
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
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(DatabaseItemUi(context, theme))

    class ViewHolder(
        val ui: DatabaseItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: DatabaseBean?,
    ) {
        with(holder.ui) {
            val bean = item ?: return
            setItem(excerptText(bean.text ?: ""), bean.pinned)
            root.setOnClickListener {
                onPaste(bean)
            }
            root.setOnLongClickListener {
                val iconColor = ctx.styledColor(android.R.attr.colorControlNormal)
                val menu = PopupMenu(ctx, it)

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
                    }
                } else {
                    menuItem(R.string.simple_key_pin, R.drawable.ic_baseline_push_pin_24) {
                        onPin(bean)
                    }
                }
                if (showCollectButton) {
                    menuItem(R.string.collect, R.drawable.ic_baseline_star_24) {
                        onCollect(bean)
                    }
                }
                menuItem(R.string.delete, R.drawable.ic_baseline_delete_24) {
                    onDelete(bean)
                }
                menuItem(R.string.delete_all, R.drawable.ic_baseline_delete_sweep_24) {
                    onDeleteAll()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    menu.setForceShowIcon(true)
                }
                menu.show()
                true
            }
        }
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
