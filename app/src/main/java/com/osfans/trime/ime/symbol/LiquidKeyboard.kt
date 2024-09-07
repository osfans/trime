// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.chad.library.adapter4.util.setOnDebouncedItemClick
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.recyclerview.verticalLayoutManager
import timber.log.Timber

@InputScope
@Inject
class LiquidKeyboard(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
    private val commonKeyboardActionListener: CommonKeyboardActionListener,
) : BoardWindow.BarBoardWindow(),
    ResidentWindow,
    ClipboardHelper.OnClipboardUpdateListener {
    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    private lateinit var currentBoardType: SymbolBoardType
    private lateinit var currentBoardAdapter: RecyclerView.Adapter<*>

    private val simpleAdapter by lazy {
        val itemWidth = context.dp(theme.liquid.getInt("single_width"))
        val columnCount = context.resources.displayMetrics.widthPixels / itemWidth
        SimpleAdapter(theme, columnCount).apply {
            setHasStableIds(true)
            setListener {
                when (currentBoardType) {
                    SymbolBoardType.SYMBOL -> service.inputSymbol(this.text)
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
    }

    private val varLengthAdapter by lazy {
        VarLengthAdapter(theme).apply {
            setOnDebouncedItemClick { _, _, position ->
                val item = items[position]
                when (currentBoardType) {
                    SymbolBoardType.SYMBOL -> service.inputSymbol(item.first)
                    SymbolBoardType.TABS -> {
                        val realPosition = TabManager.tabTags.indexOfFirst { it.text == item.first }
                        select(realPosition)
                    }
                    else -> service.currentInputConnection?.commitText(item.first, 1)
                }
            }
        }
    }

    private val dbAdapter by lazy {
        DbAdapter(context, service, theme)
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidKeyboard

    private val keyboardView by lazy {
        liquidLayout.boardView
    }

    override fun onCreateView(): View =
        LiquidLayout(context, theme, commonKeyboardActionListener).apply {
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
        liquidLayout.updateLayoutParams {
            height = KeyboardSwitcher.currentKeyboard.keyboardHeight
        }
        // 注册剪贴板更新监听器
        ClipboardHelper.addOnUpdateListener(this)
    }

    override fun onDetached() {
        ClipboardHelper.removeOnUpdateListener(this)
    }

    /**
     * 使用FlexboxLayoutManager时调用此函数获取
     */
    private val flexboxLayoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            justifyContent = JustifyContent.SPACE_AROUND
            alignItems = AlignItems.FLEX_START
        }
    }

    /**
     * 使用 StaggeredGridLayoutManager 时调用此函数获取
     */
    private val oneColStaggeredGridLayoutManager by lazy {
        StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
    }

    fun select(i: Int) {
        if (TabManager.currentTabIndex == i) return
        val tag = TabManager.tabTags[i]
        currentBoardType = tag.type
        liquidLayout.tabsUi.activateTab(i)
        val data = TabManager.selectTabByIndex(i)
        when (tag.type) {
            SymbolBoardType.CLIPBOARD -> initDbData { ClipboardHelper.getAll() }
            SymbolBoardType.COLLECTION -> initDbData { CollectionHelper.getAll() }
            SymbolBoardType.DRAFT -> initDbData { DraftHelper.getAll() }
            SymbolBoardType.SYMBOL,
            SymbolBoardType.VAR_LENGTH,
            SymbolBoardType.TABS,
            -> {
                val items =
                    data.map {
                        val text = if (tag.type == SymbolBoardType.SYMBOL) it.label else it.text
                        text to ""
                    }
                initVarLengthKeys(items)
            }
            SymbolBoardType.HISTORY -> {
                symbolHistory.load()
                initFixData(symbolHistory.toOrderedList().map { SimpleKeyBean(it) })
            }
            else -> initFixData(data)
        }
    }

    private fun initFixData(data: List<SimpleKeyBean>) {
        if (onAdapterChange(simpleAdapter)) {
            keyboardView.apply {
                layoutManager = verticalLayoutManager()
                adapter = simpleAdapter
                setItemViewCacheSize(10)
                setHasFixedSize(true)
                // 添加分割线
                // 设置添加删除动画
                // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
                isSelected = true
            }
        }
        simpleAdapter.updateBeans(data)
        keyboardView.scrollToPosition(0)
    }

    private fun initDbData(data: suspend () -> List<DatabaseBean>) {
        dbAdapter.type = currentBoardType
        if (onAdapterChange(dbAdapter)) {
            keyboardView.apply {
                layoutManager = oneColStaggeredGridLayoutManager
                adapter = dbAdapter
                setItemViewCacheSize(10)
                setHasFixedSize(false)
                // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
                isSelected = true
            }
        }

        service.lifecycleScope.launch {
            dbAdapter.updateBeans(data())
        }
    }

    private fun initVarLengthKeys(data: List<Pair<String, String>>) {
        if (onAdapterChange(varLengthAdapter)) {
            // 设置布局管理器
            keyboardView.apply {
                layoutManager = flexboxLayoutManager
                adapter = varLengthAdapter
            }
        }
        varLengthAdapter.submitList(data)
    }

    /**
     * 实现 OnClipboardUpdateListener 中的 onUpdate
     * 当剪贴板内容变化且剪贴板视图处于开启状态时，更新视图.
     */
    override fun onUpdate(text: String) {
        if (currentBoardType == SymbolBoardType.CLIPBOARD) {
            Timber.v("OnClipboardUpdateListener onUpdate: update clipboard view")
            service.lifecycleScope.launch {
                dbAdapter.updateBeans(ClipboardHelper.getAll())
            }
        }
    }

    private fun onAdapterChange(adapter: RecyclerView.Adapter<*>): Boolean =
        (!::currentBoardAdapter.isInitialized || currentBoardAdapter != adapter).also {
            if (it) currentBoardAdapter = adapter
        }
}
