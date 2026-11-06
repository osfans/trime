/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.TrimeInputMethodService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.setPaddingDp

/**
 * Lets the user search the clipboard history by keyword and paste a match.
 *
 * The search input lives in an attached dialog, so the keyword is committed
 * to the dialog's own field (through this IME) and never to the original
 * application's input field. Clearing the keyword restores the full history,
 * and an empty-result hint is shown when nothing matches.
 *
 * The collection (favorites) is intentionally not covered.
 */
class ClipboardSearchDialog(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val scope: ThemeScope,
    private val onPaste: (String) -> Unit,
) {
    private var filterJob: Job? = null

    private val beans = mutableListOf<DatabaseBean>()

    private val resultsAdapter =
        object : RecyclerView.Adapter<ResultViewHolder>() {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): ResultViewHolder = ResultViewHolder(ClipboardBeanUi(parent.context, scope))

            override fun onBindViewHolder(
                holder: ResultViewHolder,
                position: Int,
            ) {
                val bean = beans[position]
                holder.ui.setBean(bean.text ?: "", bean.pinned)
                holder.ui.root.setOnClickListener {
                    val text = bean.text ?: return@setOnClickListener
                    paste(text)
                }
            }

            override fun getItemCount(): Int = beans.size
        }

    private lateinit var dialog: AlertDialog
    private lateinit var emptyView: TextView
    private lateinit var listView: RecyclerView

    private fun paste(text: String) {
        dialog.dismiss()
        // Commit only after the dialog is gone and the original editor
        // has become the input target again. This must not be posted on a
        // dialog view: it detaches on dismiss and would never run.
        Handler(Looper.getMainLooper()).postDelayed({ onPaste(text) }, PASTE_DELAY_MS)
    }

    private fun submitQuery(keyword: String) {
        filterJob?.cancel()
        filterJob =
            service.lifecycleScope.launch {
                // A blank keyword matches everything, restoring the full history.
                val result = ClipboardHelper.filterBeans(keyword.trim())
                beans.clear()
                beans.addAll(result)
                resultsAdapter.notifyDataSetChanged()
                emptyView.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    fun show() {
        val searchInput =
            EditText(context).apply {
                hint = context.getString(R.string.search_clipboard)
                setHintTextColor(scope.colors.keySymbolColor)
                setTextColor(scope.colors.keyTextColor)
                textSize = 16f
                maxLines = 1
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                inputType = InputType.TYPE_CLASS_TEXT
                setPaddingDp(12, 8, 12, 8)
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) {}

                        override fun afterTextChanged(s: Editable?) {
                            submitQuery(s?.toString() ?: "")
                        }
                    },
                )
            }
        emptyView =
            TextView(context).apply {
                setText(R.string.clipboard_search_empty)
                setTextColor(scope.colors.keySymbolColor)
                textSize = 14f
                visibility = View.GONE
                setPaddingDp(12, 16, 12, 16)
            }
        listView =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = resultsAdapter
            }
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    searchInput,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    emptyView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    listView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        context.dp(240),
                    ),
                )
            }
        dialog =
            AlertDialog
                .Builder(context)
                .setView(content)
                .create()
        dialog.setOnDismissListener {
            filterJob?.cancel()
        }
        service.showDialog(dialog)
        searchInput.post { searchInput.requestFocus() }
        submitQuery("")
    }

    class ResultViewHolder(val ui: ClipboardBeanUi) : RecyclerView.ViewHolder(ui.root)

    companion object {
        private const val PASTE_DELAY_MS = 200L
    }
}
