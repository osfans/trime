/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.AlertDialog
import android.content.DialogInterface
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import org.json.JSONObject

/** Public View actions exercise real dialog callbacks; these are not claimed as physical touches. */
internal class PublicPreferenceProbeUi(private val c: PublicPreferenceProbeContext) {
    fun text(value: String, start: Int = 1, end: Int = 3) = c.main {
        val editor = checkNotNull(c.dialog().findViewById<EditText>(android.R.id.edit))
        editor.setText(value)
        editor.setSelection(start, end)
    }
    fun editor(value: String, start: Int, end: Int, opaque: Boolean) = c.main {
        val field = checkNotNull(c.dialog().findViewById<EditText>(android.R.id.edit))
        check(field is AppCompatEditText) { "The actual M/default short EditText inflater path was not retained" }
        check(field.text.toString() == value && field.selectionStart == start && field.selectionEnd == end)
        if (opaque) {
            check(field.hint.toString() == "opaque-${c.memory.token}")
            check(field.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT)
            check(field.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        } else {
            check(field.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER)
            check(field.keyListener != null)
        }
        c.report.put(
            "last_actual_editor",
            JSONObject().put("class", field.javaClass.name)
                .put("input_type", field.inputType).put("selection_start", field.selectionStart).put("selection_end", field.selectionEnd),
        )
    }
    fun button(which: Int) {
        c.main {
            val dialog = c.dialog() as AlertDialog
            val button = checkNotNull(dialog.getButton(which))
            check(button.isShown && button.isEnabled)
            check(button.performClick())
        }
        c.awaitDismissed()
    }
    fun negative() = button(DialogInterface.BUTTON_NEGATIVE)
    fun positive() = button(DialogInterface.BUTTON_POSITIVE)
    fun item(index: Int, dismisses: Boolean) {
        c.main {
            val list = (c.dialog() as AlertDialog).listView
            check(index in 0 until list.count)
            val child = checkNotNull(list.getChildAt(index - list.firstVisiblePosition))
            check(child.isShown && list.performItemClick(child, index, list.adapter.getItemId(index)))
        }
        if (dismisses) c.awaitDismissed()
    }
    fun choices(expected: Set<Int>, noPositiveButton: Boolean = false) = c.main {
        val dialog = c.dialog() as AlertDialog
        val actual = (0 until dialog.listView.count).filter { dialog.listView.isItemChecked(it) }.toSet()
        check(actual == expected)
        if (noPositiveButton) check(dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isShown != true)
    }
    fun actualOldEditor(value: String, start: Int, end: Int) = c.main {
        // Before migration this is a genuine AndroidX AppCompat AlertDialog, not a platform cast.
        val field = checkNotNull(c.dialog().findViewById<EditText>(android.R.id.edit))
        check(field.text.toString() == value && field.selectionStart == start && field.selectionEnd == end)
    }
    fun focusListForSave(): Pair<Int, Boolean> = c.main {
        val list = when (val dialog = c.dialog()) {
            is AlertDialog -> dialog.listView
            is androidx.appcompat.app.AlertDialog -> dialog.listView
            else -> error("Expected a list dialog")
        }
        check(list.requestFocus()) { "List did not accept public focus request before save" }
        val actual = list.id to list.hasFocus()
        check(actual == (R.id.select_dialog_listview to true)) {
            "List save baseline did not own the expected focused hierarchy key: $actual"
        }
        actual
    }
    fun assertRestoredListFocus(expected: Pair<Int, Boolean>) = c.main {
        val list = when (val dialog = c.dialog()) {
            is AlertDialog -> dialog.listView
            is androidx.appcompat.app.AlertDialog -> dialog.listView
            else -> error("Expected a list dialog")
        }
        val actual = list.id to list.hasFocus()
        check(actual == expected && actual == (R.id.select_dialog_listview to true)) {
            "Saved list hierarchy identity/focus was not restored: expected=$expected actual=$actual"
        }
    }
    fun oldChoices(expected: Set<Int>) = c.main {
        val dialog = c.dialog() as androidx.appcompat.app.AlertDialog
        check((0 until dialog.listView.count).filter { dialog.listView.isItemChecked(it) }.toSet() == expected)
    }
    fun oldItem(index: Int) = c.main {
        val list = (c.dialog() as androidx.appcompat.app.AlertDialog).listView
        val child = checkNotNull(list.getChildAt(index - list.firstVisiblePosition))
        check(list.performItemClick(child, index, list.adapter.getItemId(index)))
    }
}
