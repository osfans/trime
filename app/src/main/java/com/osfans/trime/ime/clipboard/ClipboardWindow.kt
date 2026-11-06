/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import com.osfans.trime.R
import com.osfans.trime.data.clip.ClipboardHelper
import com.osfans.trime.data.clip.FavoriteHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.core.InputTabLayout
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.segments.SegmentsWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.ClipEditActivity
import com.osfans.trime.util.AppUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.views.recyclerview.verticalLayoutManager

class ClipboardWindow(di: DI, private val initialTab: Int = 0) : BoardWindow.BarBoardWindow(di) {

    private val service: TrimeInputMethodService by instance()
    private val windowManager: BoardWindowManager by instance()
    private val scope: ThemeScope by instance()
    private val theme: Theme get() = scope.theme

    private lateinit var clipboardLayout: ClipboardLayout
    private lateinit var clipboardPagesAdapter: ClipboardPagesAdapter
    private var wasAttached = false

    private val prefs = AppPrefs.defaultInstance().clipboard
    private val clipboardReturnAfterPaste by prefs.clipboardReturnAfterPaste
    private val clipboardMaskSensitive by prefs.clipboardMaskSensitive

    private val clipboardEntitiesPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { ClipboardHelper.allEntities() }
    }
    private val favoriteEntitiesPager by lazy {
        Pager(PagingConfig(pageSize = 16)) { FavoriteHelper.allEntities() }
    }
    private var clipboardEntitiesSubmitJob: Job? = null
    private var favoriteEntitiesSubmitJob: Job? = null

    private val clipboardEntitiesAdapter by lazy {
        object : ClipboardAdapter(scope, clipboardMaskSensitive) {
            override fun onPaste(item: ClipboardItem) {
                service.commitText(item.text)
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

            override fun onShare(item: ClipboardItem) {
                launchTextSharing(item.text)
            }

            override fun onSegment(item: ClipboardItem) {
                windowManager.attachWindow(SegmentsWindow(di, item.text, item.mask))
            }

            override fun onFavorite(item: ClipboardItem) {
                FavoriteHelper.addNewEntity(item.text)
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { ClipboardHelper.delete(id) }
            }

            override val enableClipActions: Boolean = true
        }
    }

    private val favoriteEntitiesAdapter by lazy {
        object : ClipboardAdapter(scope, false) {
            override fun onPaste(item: ClipboardItem) {
                service.commitText(item.text)
                if (clipboardReturnAfterPaste) {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            override fun onEdit(id: Int) {
                AppUtils.launchClipEdit(context, id, ClipEditActivity.FROM_COLLECTION)
            }

            override fun onShare(item: ClipboardItem) {
                launchTextSharing(item.text)
            }

            override fun onSegment(item: ClipboardItem) {
                windowManager.attachWindow(SegmentsWindow(di, item.text, item.mask))
            }

            override fun onDelete(id: Int) {
                service.lifecycleScope.launch { FavoriteHelper.delete(id) }
            }

            override val enableClipActions: Boolean = false
        }
    }

    private val clipboardPage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = clipboardEntitiesAdapter
            }
        }
    }

    private val favoritePage by lazy {
        ClipboardPageUi(context).apply {
            recyclerView.apply {
                layoutManager = verticalLayoutManager()
                adapter = favoriteEntitiesAdapter
            }
        }
    }

    override fun onCreateView() = ClipboardLayout(context, scope).apply {
        clipboardLayout = this
        clipboardPagesAdapter = object : ClipboardPagesAdapter() {
            override fun getItemCount(): Int = 2
            override fun onCreatePage(position: Int): ClipboardPageUi = when (position) {
                0 -> clipboardPage
                else -> favoritePage
            }
        }
        viewPager.apply {
            adapter = clipboardPagesAdapter
        }
        titleUi.apply {
            tabLayout.onConfigureTab(viewPager) { tabUi, position ->
                configureTab(tabUi, position)
            }
            deleteAllButton.setOnClickListener {
                val currentItem = viewPager.currentItem
                when (currentItem) {
                    0 -> promptDeleteAll {
                        ClipboardHelper.deleteAll(ClipboardHelper.haveUnpinned())
                    }

                    else -> promptDeleteAll {
                        FavoriteHelper.deleteAll()
                    }
                }
            }
        }
    }

    private fun launchTextSharing(text: String) {
        val target = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(target, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(chooser)
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

    private fun configureTab(
        tabUi: InputTabLayout.TabUi,
        position: Int,
    ) {
        val label =
            when (position) {
                0 -> R.string.clipboard
                else -> R.string.collection
            }
        tabUi.label.apply {
            setText(label)
            textSize = theme.style.candidateTextSize
            setTypeface(theme.fonts.candidate, Typeface.BOLD)
            setTextColor(scope.colors.keyTextColor)
        }
    }

    override fun onAttached() {
        wasAttached = true
        clipboardLayout.viewPager.setCurrentItem(initialTab, false)
        clipboardEntitiesSubmitJob = service.lifecycleScope.launch {
            clipboardEntitiesPager.flow
                .map { pagingData -> pagingData.map { it.toClipboardItem() } }
                .collect {
                    clipboardEntitiesAdapter.submitData(it)
                }
        }
        favoriteEntitiesSubmitJob = service.lifecycleScope.launch {
            favoriteEntitiesPager.flow
                .map { pagingData -> pagingData.map { it.toClipboardItem() } }
                .collect {
                    favoriteEntitiesAdapter.submitData(it)
                }
        }
    }

    override fun onDetached() {
        clipboardEntitiesAdapter.dismissPopupMenu()
        favoriteEntitiesAdapter.dismissPopupMenu()
        clipboardEntitiesSubmitJob?.cancel()
        favoriteEntitiesSubmitJob?.cancel()
    }

    override fun refreshColors() {
        if (!wasAttached) return
        clipboardLayout.titleUi.deleteAllButton.refreshColors()
        clipboardLayout.titleUi.tabLayout.reconfigureTabs { tabUi, position ->
            configureTab(tabUi, position)
        }
        clipboardEntitiesAdapter.refreshColors()
        favoriteEntitiesAdapter.refreshColors()
    }

    override fun onCreateBarView(): View = clipboardLayout.titleUi.root
}
