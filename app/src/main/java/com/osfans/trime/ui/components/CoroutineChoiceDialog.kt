// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.components

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoroutineChoiceDialog(
    context: Context,
    private val scope: LifecycleCoroutineScope,
) {
    private val builder = AlertDialog.Builder(context)
    var items: Array<CharSequence> = arrayOf()
    var checkedItem: Int = -1
    var checkedItems: BooleanArray = booleanArrayOf()
    var title: String = ""
    var emptyMessage: String = ""
    var initDispatcher: CoroutineDispatcher = Dispatchers.Main
    var postiveDispatcher: CoroutineDispatcher = Dispatchers.Main

    private lateinit var onInitListener: ActionListener
    private lateinit var onPositiveListener: ActionListener

    fun interface ActionListener {
        fun onAction()
    }

    fun onInit(listener: ActionListener): CoroutineChoiceDialog {
        onInitListener = listener
        return this
    }

    fun onOKButton(listener: ActionListener): CoroutineChoiceDialog {
        onPositiveListener = listener
        return this
    }

    private suspend fun init() =
        withContext(initDispatcher) {
            onInitListener.onAction()
        }

    private suspend fun positive() =
        withContext(postiveDispatcher) {
            onPositiveListener.onAction()
        }

    private fun build() {
        with(builder) {
            setNegativeButton(android.R.string.cancel, null)
            if (title.isNotEmpty()) setTitle(title)
            if (items.isNotEmpty()) {
                if (checkedItems.isNotEmpty()) {
                    setMultiChoiceItems(items, checkedItems) { _, id, isChecked ->
                        checkedItems[id] = isChecked
                    }
                } else if (checkedItem > -1) {
                    setSingleChoiceItems(items, checkedItem) { _, id ->
                        checkedItem = id
                    }
                }
                setPositiveButton(android.R.string.ok) { _, _ ->
                    scope.launch {
                        positive()
                    }
                }
            } else {
                setMessage(emptyMessage)
            }
        }
    }

    suspend fun create(): AlertDialog {
        init()
        build()
        return builder.create()
    }
}
