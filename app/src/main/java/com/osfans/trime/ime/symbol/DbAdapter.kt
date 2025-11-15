// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.launch

class DbAdapter(
    private val service: TrimeInputMethodService,
    private val theme: Theme,
    private val liquidWindow: LiquidWindow,
) : FlexibleAdapter(theme) {
    private val type: LiquidData.Type
        get() = liquidWindow.currentDataType

    override fun onPaste(bean: DatabaseBean) {
        service.commitText(bean.text ?: "")
    }

    override fun onPin(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                LiquidData.Type.CLIPBOARD -> ClipboardHelper.pin(bean.id)
                LiquidData.Type.COLLECTION -> CollectionHelper.pin(bean.id)
                else -> {}
            }
            refresh()
        }
    }

    override fun onUnpin(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                LiquidData.Type.CLIPBOARD -> ClipboardHelper.unpin(bean.id)
                LiquidData.Type.COLLECTION -> CollectionHelper.unpin(bean.id)
                else -> {}
            }
            refresh()
        }
    }

    override fun onDelete(bean: DatabaseBean) {
        service.lifecycleScope.launch {
            when (type) {
                LiquidData.Type.CLIPBOARD -> ClipboardHelper.delete(bean.id)
                LiquidData.Type.COLLECTION -> CollectionHelper.delete(bean.id)
                else -> {}
            }
            refresh()
        }
    }

    override fun onEdit(bean: DatabaseBean) {
        bean.text?.let { AppUtils.launchLiquidEdit(service, type, bean.id, it) }
    }

    override fun onCollect(bean: DatabaseBean) {
        service.lifecycleScope.launch { CollectionHelper.insert(DatabaseBean(text = bean.text)) }
    }

    // FIXME: 这个方法可能实现得比较粗糙，需要日后改进
    override fun onDeleteAll() {
        val confirm =
            AlertDialog
                .Builder(context)
                .setTitle(R.string.delete_all)
                .setMessage(R.string.liquid_keyboard_ask_to_delete_all)
                .setPositiveButton(R.string.ok) { _, _ ->
                    service.lifecycleScope.launch {
                        when (type) {
                            LiquidData.Type.CLIPBOARD -> ClipboardHelper.deleteAll(ClipboardHelper.haveUnpinned())
                            LiquidData.Type.COLLECTION -> CollectionHelper.deleteAll(CollectionHelper.haveUnpinned())
                            else -> {}
                        }
                        refresh()
                    }
                }.setNegativeButton(R.string.cancel, null)
                .create()
        service.showDialog(confirm)
    }

    override val showCollectButton: Boolean = type != LiquidData.Type.COLLECTION

    private suspend fun refresh() {
        when (type) {
            LiquidData.Type.CLIPBOARD -> submitList(ClipboardHelper.getAll())
            LiquidData.Type.COLLECTION -> submitList(CollectionHelper.getAll())
            else -> {}
        }
    }
}
