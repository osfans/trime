// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.util.ShortcutUtils
import kotlinx.coroutines.launch

class DbAdapter(
    private val context: Context,
    private val service: TrimeInputMethodService,
    theme: Theme,
) : FlexibleAdapter(theme) {
    var type = SymbolKeyboardType.CLIPBOARD

    override fun onPaste(bean: DatabaseBean) {
        service.commitText(bean.text)
    }

    override fun onPin(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.pin(bean.id)
                SymbolKeyboardType.COLLECTION -> CollectionHelper.pin(bean.id)
                SymbolKeyboardType.DRAFT -> DraftHelper.pin(bean.id)
                else -> return@launch
            }
        }
    }

    override fun onUnpin(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.unpin(bean.id)
                SymbolKeyboardType.COLLECTION -> CollectionHelper.unpin(bean.id)
                SymbolKeyboardType.DRAFT -> DraftHelper.unpin(bean.id)
                else -> return@launch
            }
        }
    }

    override fun onDelete(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.delete(bean.id)
                SymbolKeyboardType.COLLECTION -> CollectionHelper.delete(bean.id)
                SymbolKeyboardType.DRAFT -> DraftHelper.delete(bean.id)
                else -> return@launch
            }
        }
    }

    override fun onEdit(bean: DatabaseBean) {
        bean.text?.let { ShortcutUtils.launchLiquidKeyboardEdit(context, type, bean.id, it) }
    }

    override fun onCollect(bean: DatabaseBean) {
        service.lifecycleScope.launch { CollectionHelper.insert(DatabaseBean(text = bean.text)) }
    }

    // FIXME: 这个方法可能实现得比较粗糙，需要日后改进
    override fun onDeleteAll() {
        fun deleteAll() {
            if (beans.all { it.pinned }) {
                // 如果没有未置顶的条目，则删除所有已置顶的条目
                service.lifecycleScope.launch {
                    when (type) {
                        SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.deleteAll(false)
                        SymbolKeyboardType.COLLECTION -> CollectionHelper.deleteAll(false)
                        SymbolKeyboardType.DRAFT -> DraftHelper.deleteAll(false)
                        else -> return@launch
                    }
                }
                updateBeans(emptyList())
            } else {
                // 如果有已置顶的条目，则删除所有未置顶的条目
                service.lifecycleScope.launch {
                    when (type) {
                        SymbolKeyboardType.CLIPBOARD -> {
                            ClipboardHelper.deleteAll()
                            updateBeans(ClipboardHelper.getAll())
                        }

                        SymbolKeyboardType.COLLECTION -> {
                            CollectionHelper.deleteAll()
                            updateBeans(CollectionHelper.getAll())
                        }

                        SymbolKeyboardType.DRAFT -> {
                            DraftHelper.deleteAll()
                            updateBeans(DraftHelper.getAll())
                        }

                        else -> return@launch
                    }
                }
            }
        }

        val confirm =
            AlertDialog.Builder(context)
                .setTitle(R.string.delete_all)
                .setMessage(R.string.liquid_keyboard_ask_to_delete_all)
                .setPositiveButton(R.string.ok) { _, _ ->
                    deleteAll()
                }
                .setNegativeButton(R.string.cancel, null)
                .create()
        service.inputView?.showDialog(confirm)
    }

    override val showCollectButton: Boolean = type != SymbolKeyboardType.COLLECTION
}
