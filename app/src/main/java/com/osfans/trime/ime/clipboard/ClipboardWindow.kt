/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.views.recyclerview.verticalLayoutManager

class ClipboardWindow : BoardWindow.BarBoardWindow() {

    private val service: TrimeInputMethodService by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val theme: Theme by di.instance()
    override val showTitle: Boolean = false

    private lateinit var clipboardLayout: ClipboardLayout
    private lateinit var clipboardPagesAdapter: ClipboardPagesAdapter

    private val prefs = AppPrefs.defaultInstance().clipboard
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste

    private val clipboardBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { ClipboardHelper.allBeans() }
    }
    private val collectionBeansPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { CollectionHelper.allBeans() }
    }
    private var clipboardBeansSubmitJob: Job? = null
    private var collectionBeansSubmitJob: Job? = null

    private val clipboardBeansAdapter by lazy {
        object : ClipboardAdapter(theme) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                service.commitText(text)
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onPin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.pin(id) }
            }

            override fun onUnpin(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.unpin(id) }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id, ClipEditActivity.FROM_CLIPBOARD)
            }

            override fun onCollect(bean: DatabaseBean) {
                service.lifecycleScope.launch {
                    CollectionHelper.addNewBean(bean.text ?: "")
                }
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.delete(id) }
            }

            override val enableCollection: Boolean = true
        }
    }

    private val collectionBeansAdapter by lazy {
        object : ClipboardAdapter(theme) {
            override fun onPaste(bean: DatabaseBean) {
                val text = bean.text ?: return
                service.commitText(text)
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id, ClipEditActivity.FROM_COLLECTION)
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { CollectionHelper.delete(id) }
            }

            override val enableCollection: Boolean = false
        }
    }

    private val clipboardPage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = clipboardBeansAdapter
            }
        }
    }

    private val collectionPage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = collectionBeansAdapter
            }
        }
    }

    override fun onCreateView() = ClipboardLayout(context, theme).apply {
        clipboardLayout = this
        clipboardPagesAdapter = object : ClipboardPagesAdapter() {
            override fun getItemCount(): Int = 2
            override fun onCreatePage(position: Int): ClipboardPageUi = when (position) {
                0 -> clipboardPage
                else -> collectionPage
            }
        }
        viewPager.apply {
            adapter = clipboardPagesAdapter
            setCurrentItem(0, false)
        }
        titleUi.apply {
            backButton.setOnClickListener {
                windowManager.attachWindow(KeyboardWindow)
            }
            tabLayout.onConfigureTab(viewPager) { tabUi, position ->
                val label = when (position) {
                    0 -> R.string.clipboard
                    else -> R.string.collection
                }
                tabUi.label.apply {
                    setText(label)
                    textSize = theme.generalStyle.candidateTextSize
                    setTypeface(FontManager.getTypeface("candidate_font"), Typeface.BOLD)
                    setTextColor(ColorManager.getColor("key_text_color"))
                }
            }
            deleteAllButton.setOnClickListener {
                val currentItem = viewPager.currentItem
                when (currentItem) {
                    0 -> promptDeleteAll {
                        ClipboardHelper.deleteAll(ClipboardHelper.haveUnpinned())
                    }
                    else -> promptDeleteAll {
                        CollectionHelper.deleteAll(CollectionHelper.haveUnpinned())
                    }
                }
            }
        }
    }

    private fun promptDeleteAll(action: suspend () -> Unit) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.delete_all)
            .setMessage(R.string.ask_to_delete_all)
            .setPositiveButton(R.string.ok) { _, _ ->
                service.lifecycleScope.launch {
                    action()
                }
            }.setNegativeButton(R.string.cancel, null)
            .create()
        service.showDialog(dialog)
    }

    override fun onAttached() {
        clipboardBeansSubmitJob = service.lifecycleScope.launch {
            clipboardBeansPager.flow.collect {
                clipboardBeansAdapter.submitData(it)
            }
        }
        collectionBeansSubmitJob = service.lifecycleScope.launch {
            collectionBeansPager.flow.collect {
                collectionBeansAdapter.submitData(it)
            }
        }
    }

    override fun onDetached() {
        clipboardBeansSubmitJob?.cancel()
        collectionBeansSubmitJob?.cancel()
    }

    override fun onCreateBarView(): View = clipboardLayout.titleUi.root
}
