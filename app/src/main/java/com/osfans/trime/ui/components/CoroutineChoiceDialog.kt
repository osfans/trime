package com.osfans.trime.ui.components

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoroutineChoiceDialog(
    context: Context,
    @StyleRes themeResId: Int,
) : CoroutineScope by (context as LifecycleOwner).lifecycleScope {
    private val builder = AlertDialog.Builder(context, themeResId)
    var items: Array<CharSequence> = arrayOf()
    var checkedItem: Int = -1
    var checkedItems: BooleanArray = booleanArrayOf()
    var title: String = ""
    var emptyMessage: String = ""
    var initDispatcher: CoroutineDispatcher = Dispatchers.Main
    var postiveDispatcher: CoroutineDispatcher = Dispatchers.Main

    private lateinit var onInitListener: ActionListener
    private lateinit var onPositiveListener: ActionListener

    constructor(context: Context) : this(context, 0)

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
                    launch {
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
