// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.View
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.core.CandidateListItem
import com.osfans.trime.core.Rime
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.text.TextInputManager
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import timber.log.Timber

@InputScope
@Inject
class LiquidKeyboard(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val theme: Theme,
) : BoardWindow.BarBoardWindow(), ResidentWindow, ClipboardHelper.OnClipboardUpdateListener {
    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    private lateinit var currentBoardType: SymbolBoardType
    private lateinit var currentBoardAdapter: RecyclerView.Adapter<*>

    private val simpleAdapter by lazy {
        val itemWidth = context.dp(theme.liquid.getInt("single_width"))
        val columnCount = context.resources.displayMetrics.widthPixels / itemWidth
        SimpleAdapter(theme, columnCount).apply {
            setHasStableIds(true)
            setListener { position ->
                if (position in beans.indices) {
                    val bean = beans[position]
                    when (currentBoardType) {
                        SymbolBoardType.SYMBOL -> service.inputSymbol(bean.text)
                        else -> {
                            service.commitText(bean.text)
                            if (currentBoardType != SymbolBoardType.HISTORY) {
                                symbolHistory.insert(bean.text)
                                symbolHistory.save()
                            }
                        }
                    }
                }
            }
        }
    }

    private val varLengthAdapter by lazy {
        CandidateAdapter(theme).apply {
            setListener { position ->
                when (currentBoardType) {
                    SymbolBoardType.CANDIDATE -> {
                        TextInputManager.instanceOrNull()
                            ?.onCandidatePressed(position)
                        if (Rime.isComposing) {
                            val candidates = Rime.candidatesWithoutSwitch
                            updateCandidates(candidates.toList())
                            keyboardView.scrollToPosition(0)
                        } else {
                            service.selectLiquidKeyboard(-1)
                        }
                    }
                    SymbolBoardType.SYMBOL -> service.inputSymbol(this.text)
                    SymbolBoardType.TABS -> {
                        val realPosition = TabManager.tabTags.indexOfFirst { it.text == this.text }
                        select(realPosition)
                    }
                    else -> service.currentInputConnection?.commitText(this.text, 1)
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
        LiquidLayout(context, service, theme).apply {
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
            flexDirection = FlexDirection.ROW // 主轴为水平方向，起点在左端。
            flexWrap = FlexWrap.WRAP // 按正常方向换行
            justifyContent = JustifyContent.FLEX_START // 交叉轴的起点对齐
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
        symbolHistory.load()
        val data = TabManager.selectTabByIndex(i)
        when (tag.type) {
            SymbolBoardType.CLIPBOARD -> initDbData { ClipboardHelper.getAll() }
            SymbolBoardType.COLLECTION -> initDbData { CollectionHelper.getAll() }
            SymbolBoardType.DRAFT -> initDbData { DraftHelper.getAll() }
            SymbolBoardType.CANDIDATE -> initVarLengthKeys(Rime.candidatesWithoutSwitch.toList())
            SymbolBoardType.SYMBOL,
            SymbolBoardType.VAR_LENGTH,
            SymbolBoardType.TABS,
            -> {
                val items =
                    data.map {
                        val text = if (tag.type == SymbolBoardType.SYMBOL) it.label else it.text
                        CandidateListItem("", text)
                    }
                initVarLengthKeys(items)
            }
            else -> initFixData(data)
        }
    }

    private fun initFixData(data: List<SimpleKeyBean>) {
        if (onAdapterChange(simpleAdapter)) {
            keyboardView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                adapter = simpleAdapter
                setItemViewCacheSize(10)
                setHasFixedSize(true)
                // 添加分割线
                // 设置添加删除动画
                // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
                isSelected = true
            }
        }

        when (currentBoardType) {
            SymbolBoardType.HISTORY ->
                simpleAdapter.updateBeans(symbolHistory.toOrderedList().map(::SimpleKeyBean))
            else ->
                simpleAdapter.updateBeans(data)
        }
        keyboardView.scrollToPosition(0)
    }

    private fun initDbData(data: suspend () -> List<DatabaseBean>) {
        if (onAdapterChange(dbAdapter)) {
            dbAdapter.type = currentBoardType
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

    private fun initVarLengthKeys(data: List<CandidateListItem>) {
        if (onAdapterChange(varLengthAdapter)) {
            // 设置布局管理器
            keyboardView.apply {
                layoutManager = flexboxLayoutManager
                adapter = varLengthAdapter
                setItemViewCacheSize(50)
                setHasFixedSize(false)
                isSelected = true
            }
        }
        varLengthAdapter.updateCandidates(data)
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

    private fun onAdapterChange(adapter: RecyclerView.Adapter<*>): Boolean {
        return (!::currentBoardAdapter.isInitialized || currentBoardAdapter != adapter).also {
            if (it) currentBoardAdapter = adapter
        }
    }
}
