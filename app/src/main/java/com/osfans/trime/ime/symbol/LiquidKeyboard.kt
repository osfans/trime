package com.osfans.trime.ime.symbol

import android.content.Context
import android.view.View
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
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
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
    private lateinit var currentBoardType: SymbolKeyboardType
    private lateinit var currentBoardAdapter: RecyclerView.Adapter<*>

    private val simpleAdapter by lazy {
        val itemWidth = context.dp(theme.liquid.getInt("single_width"))
        val columnCount = context.resources.displayMetrics.widthPixels / itemWidth
        SimpleAdapter(theme, columnCount).apply {
            setHasStableIds(true)
            setListener { position ->
                if (position < beans.size) {
                    val bean = beans[position]
                    if (currentBoardType === SymbolKeyboardType.SYMBOL) {
                        service.inputSymbol(bean.text)
                        return@setListener
                    } else {
                        service.commitText(bean.text)
                        if (currentBoardType !== SymbolKeyboardType.HISTORY) {
                            symbolHistory.insert(bean.text)
                            symbolHistory.save()
                        }
                        return@setListener
                    }
                }
            }
        }
    }

    private val candidateAdapter by lazy {
        CandidateAdapter(theme).apply {
            setListener { position ->
                TextInputManager.getInstance()
                    .onCandidatePressed(position)
                if (Rime.isComposing) {
                    val candidates = Rime.candidatesWithoutSwitch
                    updateCandidates(candidates.toList())
                    notifyItemRangeChanged(0, candidates.size)
                    keyboardView.scrollToPosition(0)
                } else {
                    service.selectLiquidKeyboard(-1)
                }
            }
        }
    }

    private val varLengthAdapter by lazy {
        CandidateAdapter(theme).apply {
            setListener { position ->
                val data = TabManager.selectTabByIndex(TabManager.currentTabIndex)
                if (position < data.size) {
                    val bean = data[position]
                    if (currentBoardType === SymbolKeyboardType.SYMBOL) {
                        service.inputSymbol(bean.text)
                        return@setListener
                    } else if (currentBoardType === SymbolKeyboardType.TABS) {
                        val tag = TabManager.tabTags.find { SymbolKeyboardType.hasKey(it.type) }
                        val truePosition = TabManager.getTabSwitchPosition(position)
                        if (tag != null) {
                            Timber.d(
                                "TABS click: position = $position, truePosition = $truePosition, tag.text = ${tag.text}",
                            )
                            if (tag.type === SymbolKeyboardType.NO_KEY) {
                                if (tag.command == KeyCommandType.EXIT) {
                                    service.selectLiquidKeyboard(-1)
                                }
                            } else if (TabManager.isAfterTabSwitch(truePosition)) {
                                // tab的位置在“更多”的右侧，不滚动tab，焦点仍然在”更多“上
                                select(truePosition)
                            } else {
                                service.selectLiquidKeyboard(truePosition)
                            }
                        }
                        return@setListener
                    }
                }
                service.currentInputConnection?.commitText(data[position].text, 1)
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
        LiquidLayout(context, theme).apply {
            liquidLayout = this
            tabsUi.apply {
                setTabs(TabManager.tabTags)
                setOnTabClickListener { i ->
                    select(i)
                }
            }
        }

    override fun onCreateBarView() = liquidLayout.tabsUi.root

    override fun onAttached() {}

    override fun onDetached() {}

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

    fun select(i: Int): SymbolKeyboardType {
        if (TabManager.currentTabIndex == i) return currentBoardType
        val tag = TabManager.tabTags[i]
        currentBoardType = tag.type
        liquidLayout.tabsUi.activateTab(i)
        symbolHistory.load()
        val data by lazy { TabManager.selectTabByIndex(i) }
        when (tag.type) {
            SymbolKeyboardType.CLIPBOARD,
            SymbolKeyboardType.COLLECTION,
            SymbolKeyboardType.DRAFT,
            -> initDbData()
            SymbolKeyboardType.CANDIDATE -> initCandidates()
            SymbolKeyboardType.VAR_LENGTH,
            SymbolKeyboardType.SYMBOL,
            SymbolKeyboardType.TABS,
            -> initVarLengthKeys(data)
            else -> initFixData(data)
        }
        return tag.type
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
            SymbolKeyboardType.HISTORY ->
                simpleAdapter.updateBeans(symbolHistory.toOrderedList().map(::SimpleKeyBean))
            else ->
                simpleAdapter.updateBeans(data)
        }
        keyboardView.scrollToPosition(0)
    }

    private fun initDbData() {
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
            val all =
                when (currentBoardType) {
                    SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.getAll()
                    SymbolKeyboardType.COLLECTION -> CollectionHelper.getAll()
                    SymbolKeyboardType.DRAFT -> DraftHelper.getAll()
                    else -> emptyList()
                }
            dbAdapter.updateBeans(all)
        }
        // 注册剪贴板更新监听器
        ClipboardHelper.addOnUpdateListener(this)
    }

    private fun initCandidates() {
        if (onAdapterChange(candidateAdapter)) {
            // 设置布局管理器
            keyboardView.apply {
                layoutManager = flexboxLayoutManager
                adapter = candidateAdapter
                setItemViewCacheSize(50)
                setHasFixedSize(false)
                isSelected = true
            }
        }

        candidateAdapter.updateCandidates(Rime.candidatesWithoutSwitch.toList())
        keyboardView.scrollToPosition(0)
    }

    private fun initVarLengthKeys(data: List<SimpleKeyBean>) {
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

        val candidates =
            if (currentBoardType === SymbolKeyboardType.SYMBOL) {
                data.map { b -> CandidateListItem("", b.label) }
            } else {
                data.map { b -> CandidateListItem("", b.text) }
            }
        varLengthAdapter.updateCandidates(candidates)
    }

    /**
     * 实现 OnClipboardUpdateListener 中的 onUpdate
     * 当剪贴板内容变化且剪贴板视图处于开启状态时，更新视图.
     */
    override fun onUpdate(text: String) {
        val selected = TabManager.currentTabIndex
        // 判断液体键盘视图是否已开启，-1为未开启
        if (selected >= 0) {
            val tag = TabManager.tabTags[selected]
            if (tag.type == SymbolKeyboardType.CLIPBOARD) {
                Timber.v("OnClipboardUpdateListener onUpdate: update clipboard view")
                service.lifecycleScope.launch {
                    dbAdapter.updateBeans(ClipboardHelper.getAll())
                }
            }
        }
    }

    private fun onAdapterChange(adapter: RecyclerView.Adapter<*>): Boolean {
        return (!::currentBoardAdapter.isInitialized || currentBoardAdapter != adapter).also {
            if (it) currentBoardAdapter = adapter
        }
    }
}
