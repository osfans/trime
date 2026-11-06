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
import com.osfans.trime.data.clip.ClipboardEntity
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.util.DeviceUtils
import com.osfans.trime.util.item
import splitties.resources.styledColor
import kotlin.math.min

abstract class ClipboardAdapter(
    private val scope: ThemeScope,
    private val maskSensitive: Boolean,
) : PagingDataAdapter<ClipboardItem, ClipboardAdapter.ViewHolder>(diffCallback) {
    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<ClipboardItem>() {
                override fun areItemsTheSame(
                    oldItem: ClipboardItem,
                    newItem: ClipboardItem,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: ClipboardItem,
                    newItem: ClipboardItem,
                ): Boolean = oldItem == newItem
            }

        private fun excerptText(
            str: String,
            mask: Boolean,
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
                    if (mask) {
                        append(ClipboardEntity.BULLET.repeat(excerptEnd - start))
                    } else {
                        append(str.substring(start, excerptEnd))
                    }
                    break
                } else {
                    val end = min(excerptEnd, lineBreak)
                    // append one line exactly
                    if (mask) {
                        append(ClipboardEntity.BULLET.repeat(end - start))
                    } else {
                        appendLine(str.substring(start, end))
                    }
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
    ): ViewHolder = ViewHolder(ClipboardBeanUi(parent.context, scope))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val item = getItem(position) ?: return
        with(holder.ui) {
            refreshColors()
            setBean(excerptText(item.text, item.mask && maskSensitive), item.pinned)
            root.setOnClickListener {
                onPaste(item)
            }
            root.setOnLongClickListener {
                val popup = PopupMenu(ctx, it)
                val menu = popup.menu
                val iconTint = ctx.styledColor(android.R.attr.colorControlNormal)
                menu.item(R.string.edit, R.drawable.ic_baseline_edit_24, iconTint) {
                    onEdit(item.id)
                }
                menu.item(R.string.share, R.drawable.ic_baseline_share_24, iconTint) {
                    onShare(item)
                }
                menu.item(R.string.word_segment, R.drawable.ic_baseline_view_comfy_24, iconTint) {
                    onSegment(item)
                }

                if (enableClipActions) {
                    // Favorites are plain text once stored, so a sensitive clip must never get there.
                    if (!item.mask) {
                        menu.item(R.string.collect, R.drawable.ic_baseline_star_24, iconTint) {
                            onFavorite(item)
                        }
                    }
                    if (item.pinned) {
                        menu.item(R.string.simple_key_unpin, R.drawable.ic_outline_push_pin_24, iconTint) {
                            onUnpin(item.id)
                        }
                    } else {
                        menu.item(R.string.simple_key_pin, R.drawable.ic_baseline_push_pin_24, iconTint) {
                            onPin(item.id)
                        }
                    }
                }
                menu.item(R.string.delete, R.drawable.ic_baseline_delete_24, iconTint) {
                    onDelete(item.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !DeviceUtils.isSamsungOneUI) {
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

    abstract fun onPaste(item: ClipboardItem)

    open fun onPin(id: Int) {}

    open fun onUnpin(id: Int) {}

    abstract fun onEdit(id: Int)

    abstract fun onShare(item: ClipboardItem)

    abstract fun onSegment(item: ClipboardItem)

    open fun onFavorite(item: ClipboardItem) {}

    abstract fun onDelete(id: Int)

    abstract val enableClipActions: Boolean

    fun dismissPopupMenu() {
        popupMenu?.dismiss()
        popupMenu = null
    }

    /** Re-colors the visible rows after a scheme switch; rows re-apply colors on bind. */
    fun refreshColors() {
        notifyDataSetChanged()
    }
}
