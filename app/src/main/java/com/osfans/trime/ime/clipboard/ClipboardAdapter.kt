/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.os.Build
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.item
import splitties.resources.styledColor
import kotlin.math.min

abstract class ClipboardAdapter(
    private val theme: Theme,
) : PagingDataAdapter<DatabaseBean, ClipboardAdapter.ViewHolder>(diffCallback) {
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

    private var popupMenu: PopupMenu? = null

    class ViewHolder(
        val ui: ClipboardBeanUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(ClipboardBeanUi(parent.context, theme))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val bean = getItem(position) ?: return
        with(holder.ui) {
            setBean(excerptText(bean.text ?: ""), bean.pinned)
            root.setOnClickListener {
                onPaste(bean)
            }
            root.setOnLongClickListener {
                val popup = PopupMenu(ctx, it)
                val menu = popup.menu
                val iconTint = ctx.styledColor(android.R.attr.colorControlNormal)
                menu.item(R.string.edit, R.drawable.ic_baseline_edit_24, iconTint) {
                    onEdit(bean.id)
                }

                if (enableCollection) {
                    menu.item(R.string.collect, R.drawable.ic_baseline_star_24, iconTint) {
                        onCollect(bean)
                    }
                    if (bean.pinned) {
                        menu.item(R.string.simple_key_unpin, R.drawable.ic_outline_push_pin_24, iconTint) {
                            onUnpin(bean.id)
                        }
                    } else {
                        menu.item(R.string.simple_key_pin, R.drawable.ic_baseline_push_pin_24, iconTint) {
                            onPin(bean.id)
                        }
                    }
                }
                menu.item(R.string.delete, R.drawable.ic_baseline_delete_24, iconTint) {
                    onDelete(bean.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    popup.setForceShowIcon(true)
                }
                popup.setOnDismissListener { p ->
                    if (p === popupMenu) popupMenu = null
                }
                popupMenu?.dismiss()
                popupMenu = popup
                popup.show()
                true
            }
        }
    }

    abstract fun onPaste(bean: DatabaseBean)

    open fun onPin(id: Int) {}

    open fun onUnpin(id: Int) {}

    abstract fun onEdit(id: Int)

    open fun onCollect(bean: DatabaseBean) {}

    abstract fun onDelete(id: Int)

    abstract val enableCollection: Boolean
}
