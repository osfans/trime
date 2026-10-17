// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.content.Context
import android.view.KeyEvent
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.KeyValue
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object SchemaPickerRegression {
    suspend fun verify(context: Context, rime: RimeApi) {
        val owner = withContext(Dispatchers.Main) {
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }
            }
        }
        val originalSchema = rime.selectedSchemaId()
        val escape = KeyValue.fromKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)).value
        try {
            rime.clearComposition()
            check(rime.selectSchema("luna_pinyin"))
            val choices = rime.selectedSchemata()
            val target = choices.indexOfFirst { it.id == "luna_pinyin_t9" }
            check(target >= 0)
            check(rime.processKey(KeyValue.fromKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F4)).value))
            check(rime.isEmpty()) { "F4 must expose the temporary .default schema for this regression" }
            withContext(Dispatchers.Main) {
                val dialog = EnabledSchemaPickerDialog.build(rime, owner.lifecycleScope, context)
                try {
                    dialog.create()
                    val list = checkNotNull(dialog.listView)
                    val adapter = checkNotNull(list.adapter)
                    check(adapter.count == choices.size) { "F4 hid the enabled schema list" }
                    choices.forEachIndexed { index, item -> check(adapter.getItem(index).toString() == item.name) }
                    check(list.performItemClick(adapter.getView(target, null, list), target, adapter.getItemId(target)))
                } finally {
                    dialog.dismiss()
                }
            }
            withTimeout(30_000) {
                while (rime.selectedSchemaId() != choices[target].id) delay(50)
            }
            withContext(Dispatchers.Main) {
                val empty = object : RimeApi by rime {
                    override suspend fun selectedSchemata(): Array<SchemaItem> = emptyArray()
                }
                val dialog = EnabledSchemaPickerDialog.build(empty, owner.lifecycleScope, context)
                try {
                    dialog.create()
                    check(dialog.findViewById<TextView>(android.R.id.message).text == context.getString(R.string.no_schema_to_select))
                } finally {
                    dialog.dismiss()
                }
            }
        } finally {
            rime.processKey(escape)
            rime.clearComposition()
            rime.selectSchema(originalSchema)
            withContext(Dispatchers.Main) { owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
        }
    }
}
