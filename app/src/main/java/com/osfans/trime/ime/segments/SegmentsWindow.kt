/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.segments

import android.app.SearchManager
import android.content.ClipData
import android.content.Intent
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexboxLayoutManager
import com.osfans.trime.R
import com.osfans.trime.data.clip.FavoriteHelper
import com.osfans.trime.data.clip.markSensitive
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.util.NativeTokenizer
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.systemservices.clipboardManager
import timber.log.Timber

class SegmentsWindow(
    di: DI,
    private val source: String,
    private val sensitive: Boolean = false,
) : BoardWindow.BarBoardWindow(di) {
    private val service: TrimeInputMethodService by instance()
    private val scope: ThemeScope by instance()
    private val theme: Theme get() = scope.theme
    private val windowManager: BoardWindowManager by instance()

    override val title: String by lazy {
        context.getString(R.string.word_segment)
    }

    private val adapter by lazy {
        SegmentsAdapter(scope, { onSelectionChanged() }, source)
    }

    private val touchListener by lazy {
        DragSelectTouchListener(context, adapter) {
            onSelectionChanged()
        }
    }

    private val ui by lazy {
        SegmentsUi(context, scope).apply {
            recyclerView.apply {
                layoutManager = FlexboxLayoutManager(context).apply {
                    flexDirection = FlexDirection.ROW
                    flexWrap = FlexWrap.WRAP
                    alignItems = AlignItems.FLEX_START
                }
                adapter = this@SegmentsWindow.adapter
            }
            selectButton.setOnClickListener {
                if (adapter.isAllSelected) {
                    adapter.deselect()
                } else {
                    adapter.selectAll()
                }
                onSelectionChanged()
            }
            shareButton.apply {
                isEnabled = false
                setOnClickListener {
                    val text = adapter.joinedSegments
                    val target = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    val chooser = Intent.createChooser(target, null).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(chooser)
                }
            }
            searchButton.apply {
                isEnabled = false
                setOnClickListener {
                    val text = adapter.joinedSegments
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    service.startActivity(intent)
                }
            }
            starButton.apply {
                isEnabled = false
                setOnClickListener {
                    // Favorites are stored as plain text, so a sensitive clip must never get there.
                    if (sensitive) return@setOnClickListener
                    val text = adapter.joinedSegments
                    service.lifecycleScope.launch {
                        FavoriteHelper.addNewEntity(text)
                    }
                    ctx.toast(R.string.star_success)
                }
            }
            copyButton.apply {
                isEnabled = false
                setOnClickListener {
                    val text = adapter.joinedSegments
                    val clip = ClipData.newPlainText("", text)
                    // Keep the flag, or the copy would be recorded as a new plaintext row.
                    if (sensitive) clip.markSensitive()
                    clipboardManager.setPrimaryClip(clip)
                    windowManager.attachWindow(KeyboardWindow)
                }
            }
        }
    }

    private val toolbarButtons by lazy {
        val buttonSpacing = theme.toolBar.buttonSpacing
        FlexboxLayout(context).apply {
            flexDirection = FlexDirection.ROW_REVERSE
            alignItems = AlignItems.CENTER
            val buttons =
                buildList {
                    add(ui.selectButton)
                    add(ui.copyButton)
                    if (!sensitive) add(ui.starButton)
                    add(ui.searchButton)
                    add(ui.shareButton)
                }
            val size = theme.style.run { candidateViewHeight + commentHeight }
            buttons.forEachIndexed { index, button ->
                val lParams = FlexboxLayout.LayoutParams(dp(size), dp(size))
                if (index > 0) {
                    lParams.marginEnd = dp(buttonSpacing)
                }
                addView(button, lParams)
            }
        }
    }

    override fun onCreateBarView(): View = toolbarButtons

    private fun onSelectionChanged() {
        if (adapter.isAllSelected) {
            ui.setSelectButtonToDeselect()
        } else {
            ui.setSelectButtonToSelectAll()
        }

        val hasSelection = adapter.selectionSnapshot.isNotEmpty()
        ui.updateButtons(hasSelection)
        previewSelection(hasSelection)
    }

    /** Writes the selection into the editor, debounced so a drag previews once instead of on every touch move. */
    private fun previewSelection(hasSelection: Boolean) {
        previewJob?.cancel()
        previewJob =
            service.lifecycleScope.launch {
                delay(PREVIEW_DEBOUNCE_MS)
                val joined = if (hasSelection) adapter.joinedSegments else ""
                if (joined == previewedText) return@launch
                try {
                    if (!service.updateComposingText(joined)) return@launch
                } catch (e: Exception) {
                    Timber.e(e, "Failed to preview the selected segments")
                    return@launch
                }
                previewedText = joined
            }
    }

    private var viewCreated = false
    private var previewJob: Job? = null

    /** Last text written into the editor, to skip no-op updates. */
    private var previewedText = ""

    override fun onCreateView(): View {
        viewCreated = true
        return ui.root
    }

    override fun refreshColors() {
        if (!viewCreated) return
        ui.refreshColors()
        (ui.recyclerView.adapter as? SegmentsAdapter)?.notifyDataSetChanged()
    }

    override fun onAttached() {
        ui.recyclerView.addOnItemTouchListener(touchListener)
        service.lifecycleScope.launch {
            val segments = withContext(Dispatchers.Default) { NativeTokenizer.tokenize(source) }
            if (!windowManager.isAttached(this@SegmentsWindow)) return@launch
            adapter.submitList(segments)
        }
    }

    override fun onDetached() {
        previewJob?.cancel()
        previewJob = null
        ui.recyclerView.removeOnItemTouchListener(touchListener)
        try {
            service.currentInputConnection?.finishComposingText()
        } catch (e: Exception) {
            Timber.e(e, "Failed to finish the previewed composition")
        }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 150L
    }
}
