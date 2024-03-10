package com.osfans.trime.ime.symbol

import android.app.AlertDialog
import android.content.Context
import android.view.View
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.R
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
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.text.TextInputManager
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import com.osfans.trime.util.ShortcutUtils
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.recyclerview.recyclerView
import timber.log.Timber

@InputScope
@Inject
class LiquidKeyboard(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val theme: Theme,
) : BoardWindow.NoBarBoardWindow(), ResidentWindow, ClipboardHelper.OnClipboardUpdateListener {
    private val symbolHistory = SymbolHistory(180)
    private var adapterType: AdapterType = AdapterType.INIT
    private val simpleAdapter by lazy {
        val itemWidth = context.dp(theme.liquid.getInt("single_width"))
        val columnCount = context.resources.displayMetrics.widthPixels / itemWidth
        SimpleAdapter(theme, columnCount).apply {
            setHasStableIds(true)
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
        CandidateAdapter(theme)
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidKeyboard

    private val keyboardView =
        context.recyclerView {
            val space = dp(3)
            addItemDecoration(SpacesItemDecoration(space))
            setPadding(space)
        }

    override fun onCreateView(): View {
        return context.constraintLayout {
            add(
                keyboardView,
                lParams(matchParent, KeyboardSwitcher.currentKeyboard.keyboardHeight) {
                    centerInParent()
                },
            )
        }
    }

    override fun onAttached() {}

    override fun onDetached() {}

// 及时更新layoutManager, 以防在旋转屏幕后打开液体键盘crash

    /**
     * 使用FlexboxLayoutManager时调用此函数获取
     */
    private fun getFlexbox(): FlexboxLayoutManager {
        return FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW // 主轴为水平方向，起点在左端。
            flexWrap = FlexWrap.WRAP // 按正常方向换行
            justifyContent = JustifyContent.FLEX_START // 交叉轴的起点对齐
        }
    }

    /**
     * 使用StaggeredGridLayoutManager时调用此函数获取
     */
    private fun getOneColumnStaggeredGrid(): StaggeredGridLayoutManager {
        return StaggeredGridLayoutManager(
            1,
            StaggeredGridLayoutManager.VERTICAL,
        )
    }

    fun select(i: Int): SymbolKeyboardType {
        val tag = TabManager.tabTags[i]
        symbolHistory.load()
        when (tag.type) {
            SymbolKeyboardType.CLIPBOARD,
            SymbolKeyboardType.COLLECTION,
            SymbolKeyboardType.DRAFT,
            -> {
                TabManager.selectTabByIndex(i)
                initDbData(tag.type)
            }
            SymbolKeyboardType.CANDIDATE -> {
                TabManager.selectTabByIndex(i)
                initCandidates()
            }
            SymbolKeyboardType.VAR_LENGTH, SymbolKeyboardType.SYMBOL -> {
                initVarLengthKeys(i, TabManager.selectTabByIndex(i))
            }
            SymbolKeyboardType.TABS -> {
                TabManager.selectTabByIndex(i)
                initVarLengthKeys(i, TabManager.tabSwitchData)
                Timber.d("All tags in TABS: TabManager.tabSwitchData = ${TabManager.tabSwitchData}")
            }
            SymbolKeyboardType.HISTORY -> {
                TabManager.selectTabByIndex(i)
                initFixData(i)
            }
            else -> initFixData(i)
        }
        return tag.type
    }

    private fun initFixData(i: Int) {
        val tabTag = TabManager.tabTags[i]

        simpleAdapter.apply {
            setListener { position ->
                if (position < beans.size) {
                    val bean = beans[position]
                    if (tabTag.type === SymbolKeyboardType.SYMBOL) {
                        service.inputSymbol(bean.text)
                        return@setListener
                    } else {
                        service.commitText(bean.text)
                        if (tabTag.type !== SymbolKeyboardType.HISTORY) {
                            symbolHistory.insert(bean.text)
                            symbolHistory.save()
                        }
                        return@setListener
                    }
                }
            }
        }

        if (shouldChangeAdapter(AdapterType.SIMPLE)) {
            adapterType = AdapterType.SIMPLE
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

        when (tabTag.type) {
            SymbolKeyboardType.HISTORY ->
                simpleAdapter.updateBeans(symbolHistory.toOrderedList().map(::SimpleKeyBean))
            else ->
                simpleAdapter.updateBeans(TabManager.selectTabByIndex(i))
        }
        keyboardView.scrollToPosition(0)
        Timber.d("Tab #%s with bean size %s", i, simpleAdapter.itemCount)
    }

    private fun initDbData(type: SymbolKeyboardType) {
        val dbAdapter by lazy {
            object : FlexibleAdapter(theme) {
                override fun onPaste(bean: DatabaseBean) {
                    service.commitText(bean.text)
                }

                override fun onPin(bean: DatabaseBean) {
                    service.lifecycleScope.launch {
                        when (type) {
                            SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.pin(bean.id)
                            SymbolKeyboardType.COLLECTION -> CollectionHelper.pin(bean.id)
                            SymbolKeyboardType.DRAFT -> DraftHelper.pin(bean.id)
                            else -> return@launch
                        }
                    }
                }

                override fun onUnpin(bean: DatabaseBean) {
                    service.lifecycleScope.launch {
                        when (type) {
                            SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.unpin(bean.id)
                            SymbolKeyboardType.COLLECTION -> CollectionHelper.unpin(bean.id)
                            SymbolKeyboardType.DRAFT -> DraftHelper.unpin(bean.id)
                            else -> return@launch
                        }
                    }
                }

                override fun onDelete(bean: DatabaseBean) {
                    service.lifecycleScope.launch {
                        when (type) {
                            SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.delete(bean.id)
                            SymbolKeyboardType.COLLECTION -> CollectionHelper.delete(bean.id)
                            SymbolKeyboardType.DRAFT -> DraftHelper.delete(bean.id)
                            else -> return@launch
                        }
                    }
                }

                override fun onEdit(bean: DatabaseBean) {
                    bean.text?.let { ShortcutUtils.launchLiquidKeyboardEdit(context, type, bean.id, it) }
                }

                override fun onCollect(bean: DatabaseBean) {
                    service.lifecycleScope.launch { CollectionHelper.insert(DatabaseBean(text = bean.text)) }
                }

                // FIXME: 这个方法可能实现得比较粗糙，需要日后改进
                override fun onDeleteAll() {
                    fun deleteAll() {
                        if (beans.all { it.pinned }) {
                            // 如果没有未置顶的条目，则删除所有已置顶的条目
                            service.lifecycleScope.launch {
                                when (type) {
                                    SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.deleteAll(false)
                                    SymbolKeyboardType.COLLECTION -> CollectionHelper.deleteAll(false)
                                    SymbolKeyboardType.DRAFT -> DraftHelper.deleteAll(false)
                                    else -> return@launch
                                }
                            }
                            updateBeans(emptyList())
                        } else {
                            // 如果有已置顶的条目，则删除所有未置顶的条目
                            service.lifecycleScope.launch {
                                when (type) {
                                    SymbolKeyboardType.CLIPBOARD -> {
                                        ClipboardHelper.deleteAll()
                                        updateBeans(ClipboardHelper.getAll())
                                    }
                                    SymbolKeyboardType.COLLECTION -> {
                                        CollectionHelper.deleteAll()
                                        updateBeans(CollectionHelper.getAll())
                                    }
                                    SymbolKeyboardType.DRAFT -> {
                                        DraftHelper.deleteAll()
                                        updateBeans(DraftHelper.getAll())
                                    }
                                    else -> return@launch
                                }
                            }
                        }
                    }
                    val confirm =
                        AlertDialog.Builder(context)
                            .setTitle(R.string.delete_all)
                            .setMessage(R.string.liquid_keyboard_ask_to_delete_all)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                deleteAll()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .create()
                    service.inputView?.showDialog(confirm)
                }

                override val showCollectButton: Boolean = type != SymbolKeyboardType.COLLECTION
            }
        }

        if (shouldChangeAdapter(AdapterType.DB)) {
            adapterType = AdapterType.DB
            keyboardView.apply {
                layoutManager = getOneColumnStaggeredGrid()
                adapter = dbAdapter
                setItemViewCacheSize(10)
                setHasFixedSize(false)
                // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
                isSelected = true
            }
        }

        service.lifecycleScope.launch {
            val all =
                when (type) {
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
        if (shouldChangeAdapter(AdapterType.CANDIDATE)) {
            adapterType = AdapterType.CANDIDATE
            // 设置布局管理器
            keyboardView.apply {
                layoutManager = getFlexbox()
                adapter = candidateAdapter
                setItemViewCacheSize(50)
                setHasFixedSize(false)
                isSelected = true
            }
        }

        candidateAdapter.updateCandidates(Rime.candidatesWithoutSwitch.toList())
        keyboardView.scrollToPosition(0)
    }

    private fun initVarLengthKeys(
        i: Int,
        data: List<SimpleKeyBean>,
    ) {
        val tabTag = TabManager.tabTags[i]

        varLengthAdapter.apply {
            setListener { position ->
                if (position < data.size) {
                    val bean = data[position]
                    if (tabTag.type === SymbolKeyboardType.SYMBOL) {
                        service.inputSymbol(bean.text)
                        return@setListener
                    } else if (tabTag.type === SymbolKeyboardType.TABS) {
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

        if (shouldChangeAdapter(AdapterType.VAR_LENGTH)) {
            adapterType = AdapterType.VAR_LENGTH
            // 设置布局管理器
            keyboardView.apply {
                layoutManager = getFlexbox()
                adapter = varLengthAdapter
                setItemViewCacheSize(50)
                setHasFixedSize(false)
                keyboardView.isSelected = true
            }
        }

        val candidates =
            if (tabTag.type === SymbolKeyboardType.SYMBOL) {
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
                    (keyboardView.adapter as FlexibleAdapter).updateBeans(ClipboardHelper.getAll())
                }
            }
        }
    }

    private fun shouldChangeAdapter(type: AdapterType) =
        adapterType != type ||
            adapterType == AdapterType.INIT

    private enum class AdapterType {
        INIT,
        SIMPLE,
        DB,
        CANDIDATE,
        VAR_LENGTH,
    }
}
