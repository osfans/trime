// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

@InputScope
@Inject
class LiquidWindow(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
    private val windowManager: BoardWindowManager,
    lazyCommonKeyboardActionListener: Lazy<CommonKeyboardActionListener>,
) : BoardWindow.BarBoardWindow(),
    ResidentWindow,
    ClipboardHelper.OnClipboardUpdateListener {
    override val showTitle = false

    private val commonKeyboardActionListener by lazyCommonKeyboardActionListener
    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    private lateinit var currentBoardType: SymbolBoardType
    private lateinit var currentBoardAdapter: RecyclerView.Adapter<*>

    init {
        TabManager.setTabExited()
    }

    private val mainAdapter by lazy {
        LiquidAdapter(theme) {
            when (currentBoardType) {
                SymbolBoardType.SYMBOL -> triggerSymbolInput(this.text)
                SymbolBoardType.TABS -> {
                    val realPosition = TabManager.tabTags.indexOfFirst { it.text == this.text }
                    select(realPosition)
                }
                else -> {
                    service.commitText(this.text)
                    if (currentBoardType != SymbolBoardType.HISTORY) {
                        symbolHistory.insert(this.text)
                        symbolHistory.save()
                    }
                }
            }
        }
    }

    private val dbAdapter by lazy {
        DbAdapter(context, service, theme)
    }

    private val mainLayoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.SPACE_AROUND
            alignItems = AlignItems.BASELINE
        }
    }

    private val dbLayoutManager by lazy {
        StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    private val keyboardView by lazy {
        liquidLayout.boardView
    }

    override fun onCreateView(): View = LiquidLayout(context, theme, commonKeyboardActionListener).apply {
        liquidLayout = this
        tabsUi.apply {
            setTabs(TabManager.tabTags)
            setOnTabClickListener { i ->
                select(i)
            }
        }
    }

    override fun onCreateBarView() = liquidLayout.tabsUi.root

    override fun onAttached() {
        // 注册剪贴板更新监听器
        ClipboardHelper.addOnUpdateListener(this)
    }

    override fun onDetached() {
        ClipboardHelper.removeOnUpdateListener(this)
    }

    fun select(i: Int) {
        val tag = TabManager.tabTags[i]

        fun loadDbData() = when (tag.type) {
            SymbolBoardType.CLIPBOARD -> initDbData { ClipboardHelper.getAll() }
            SymbolBoardType.COLLECTION -> initDbData { CollectionHelper.getAll() }
            SymbolBoardType.DRAFT -> initDbData { DraftHelper.getAll() }
            else -> null
        }

        if (TabManager.currentTabIndex == i) {
            loadDbData() ?: return
            return
        }

        currentBoardType = tag.type
        liquidLayout.tabsUi.activateTab(i)
        val data = TabManager.selectTabByIndex(i)
        when (tag.type) {
            SymbolBoardType.CLIPBOARD,
            SymbolBoardType.COLLECTION,
            SymbolBoardType.DRAFT,
            -> loadDbData()
            SymbolBoardType.SYMBOL,
            SymbolBoardType.VAR_LENGTH,
            SymbolBoardType.TABS,
            -> submitData(data)
            SymbolBoardType.HISTORY -> {
                symbolHistory.load()
                submitData(symbolHistory.toOrderedList().map { LiquidKeyboard.KeyItem(it) })
            }
            else -> submitData(data)
        }
    }

    private fun submitData(data: List<LiquidKeyboard.KeyItem>) {
        if (onAdapterChange(mainAdapter)) {
            keyboardView.apply {
                layoutManager = mainLayoutManager
                adapter = mainAdapter
                setItemViewCacheSize(10)
                setHasFixedSize(true)
                // 添加分割线
                // 设置添加删除动画
                // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
                isSelected = true
            }
        }
        mainAdapter.submitList(data)
        keyboardView.scrollToPosition(0)
    }

    private fun initDbData(data: suspend () -> List<DatabaseBean>) {
        dbAdapter.type = currentBoardType
        if (onAdapterChange(dbAdapter)) {
            keyboardView.apply {
                layoutManager = dbLayoutManager
                adapter = dbAdapter
            }
        }

        service.lifecycleScope.launch {
            dbAdapter.submitList(data()) {
                keyboardView.post { keyboardView.scrollToPosition(0) }
            }
        }
    }

    private fun triggerSymbolInput(symbol: String) {
        commonKeyboardActionListener.listener.onPress(KeyEvent.KEYCODE_UNKNOWN)
        rime.launchOnReady {
            val (isAsciiMode, isAsciiPunch) = it.statusCached.run { isAsciiMode to isAsciiPunch }
            if (isAsciiMode) it.setRuntimeOption("ascii_mode", false)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", false)
            commonKeyboardActionListener.listener.onText("{Escape}$symbol")
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", true)
            ContextCompat.getMainExecutor(service).execute {
                TabManager.setTabExited()
                windowManager.attachWindow(KeyboardWindow)
            }
        }
    }

    /**
     * 实现 OnClipboardUpdateListener 中的 onUpdate
     * 当剪贴板内容变化且剪贴板视图处于开启状态时，更新视图.
     */
    override fun onUpdate(bean: DatabaseBean) {
        if (currentBoardType != SymbolBoardType.CLIPBOARD) return
        initDbData { ClipboardHelper.getAll() }
    }

    private fun onAdapterChange(adapter: RecyclerView.Adapter<*>): Boolean = (!::currentBoardAdapter.isInitialized || currentBoardAdapter != adapter).also {
        if (it) currentBoardAdapter = adapter
    }
}
