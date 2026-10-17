/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.systemservices.inputMethodManager

/** No theme, native engine, dictionary, clipboard or learning dependency. */
@SuppressLint("ViewConstructor")
class EngineUnavailableView(
    context: Context,
    failed: Boolean,
    retry: () -> Unit,
    commit: (String) -> Unit,
    delete: () -> Unit,
    enter: () -> Unit,
) : LinearLayout(context) {
    private var enterKey: ImageButton? = null

    fun updateEnterAction(action: Int?, label: CharSequence) {
        enterKey?.apply {
            contentDescription = label
            val icon = when (action) {
                EditorInfo.IME_ACTION_SEARCH -> "cmd_magnify"
                EditorInfo.IME_ACTION_SEND -> "cmd_send"
                EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_GO -> "cmd_arrow_right"
                EditorInfo.IME_ACTION_PREVIOUS -> "cmd_arrow_left"
                EditorInfo.IME_ACTION_DONE -> "cmd_check"
                else -> "cmd_keyboard_return"
            }
            setImageDrawable(IconicsDrawable(context, icon).apply { sizeDp = 24 })
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(242, 242, 242))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(
            TextView(context).apply {
                text = context.getString(if (failed) R.string.ime_unavailable else R.string.ime_preparing)
                setTextColor(Color.BLACK)
                textSize = 14f
                setPadding(dp(8), dp(4), dp(8), dp(4))
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            ImageButton(context).apply {
                setImageResource(R.drawable.ic_baseline_refresh_reversed_24)
                imageTintList = ColorStateList.valueOf(Color.DKGRAY)
                contentDescription = context.getString(R.string.ime_retry)
                isEnabled = failed
                isFocusable = false
                setOnClickListener { retry() }
            },
            LayoutParams(dp(48), dp(48)),
        )
        header.addView(
            ImageButton(context).apply {
                setImageResource(R.drawable.ic_baseline_keyboard_24)
                imageTintList = ColorStateList.valueOf(Color.DKGRAY)
                contentDescription = context.getString(R.string.ime_switch)
                isFocusable = false
                setOnClickListener { inputMethodManager.showInputMethodPicker() }
            },
            LayoutParams(dp(48), dp(48)),
        )
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val rows = listOf(listOf("1", "2", "3", "DEL"), listOf("4", "5", "6", "@"), listOf("7", "8", "9", "-"), listOf(".", "0", " ", "ENTER"))
        rows.forEach { labels ->
            val row = LinearLayout(context)
            labels.forEach { label ->
                val action = when (label) {
                    "DEL" -> delete

                    "ENTER" -> enter

                    else -> {
                        { commit(label) }
                    }
                }
                val key = when (label) {
                    "DEL", "ENTER", " " -> ImageButton(context).apply {
                        if (label == "ENTER") enterKey = this
                        val icon = when (label) {
                            "DEL" -> "cmd_backspace_outline"
                            "ENTER" -> "cmd_keyboard_return"
                            else -> "cmd_keyboard_space"
                        }
                        setImageDrawable(IconicsDrawable(context, icon).apply { sizeDp = 24 })
                        contentDescription = context.getString(
                            when (label) {
                                "DEL" -> R.string.delete
                                "ENTER" -> R.string.ime_enter
                                else -> R.string.t9_space
                            },
                        )
                    }

                    else -> Button(context).apply {
                        text = label
                        textSize = 18f
                        // Keep the scaled label inside the fixed 48dp touch target.
                        setPadding(0, 0, 0, 0)
                        includeFontPadding = false
                        setSingleLine(true)
                        gravity = Gravity.CENTER
                    }
                }
                key.isFocusable = false
                key.setOnClickListener { action() }
                row.addView(key, LayoutParams(0, dp(48), 1f))
            }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        }
    }
}
