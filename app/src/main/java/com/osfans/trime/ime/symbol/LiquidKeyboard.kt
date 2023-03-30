package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
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
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.ime.text.TextInputManager
import com.osfans.trime.util.dp2px
import kotlinx.coroutines.launch
import timber.log.Timber

class LiquidKeyboard(private val context: Context) {
    private val theme: Theme = Theme.get()
    private val tabManager: TabManager = TabManager.get()
    private val service: Trime = Trime.getService()
    private lateinit var keyboardView: RecyclerView
    private val symbolHistory = SymbolHistory(180)

    private val flexbox: FlexboxLayoutManager by lazy {
        return@lazy FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW // 主轴为水平方向，起点在左端。
            flexWrap = FlexWrap.WRAP // 按正常方向换行
            justifyContent = JustifyContent.FLEX_START // 交叉轴的起点对齐
        }
    }

    private val oneColumnStaggeredGrid: StaggeredGridLayoutManager by lazy {
        return@lazy StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
    }

    fun setKeyboardView(view: RecyclerView) {
        keyboardView = view
        keyboardView.apply {
            val space = dp2px(3)
            addItemDecoration(SpacesItemDecoration(space))
            setPadding(space)
        }
    }

    fun select(i: Int): SymbolKeyboardType {
        val tag = TabManager.getTag(i)
        symbolHistory.load()
        keyboardView.removeAllViews()
        when (tag.type) {
            SymbolKeyboardType.CLIPBOARD,
            SymbolKeyboardType.COLLECTION,
            SymbolKeyboardType.DRAFT,
            -> {
                tabManager.select(i)
                initDbData(tag.type)
            }
            SymbolKeyboardType.CANDIDATE -> {
                tabManager.select(i)
                initCandidates()
            }
            SymbolKeyboardType.VAR_LENGTH -> {
                initVarLengthKeys(tabManager.select(i))
            }
            SymbolKeyboardType.SYMBOL, SymbolKeyboardType.HISTORY, SymbolKeyboardType.TABS -> {
                tabManager.select(i)
                initFixData(i)
            }
            else -> initFixData(i)
        }
        return tag.type
    }

    private fun initFixData(i: Int) {
        val tabTag = TabManager.getTag(i)

        val simpleAdapter = SimpleAdapter(theme).apply {
            // 列表适配器的点击监听事件
            setListener { position ->
                val bean = beans[position]
                if (tabTag.type === SymbolKeyboardType.SYMBOL) {
                    service.inputSymbol(bean.text)
                } else if (tabTag.type !== SymbolKeyboardType.TABS) {
                    service.currentInputConnection?.run {
                        commitText(bean.text, 1)
                        if (tabTag.type !== SymbolKeyboardType.HISTORY) {
                            symbolHistory.insert(bean.text)
                            symbolHistory.save()
                        }
                    }
                } else {
                    val tag = TabManager.getTag(position)
                    if (tag.type === SymbolKeyboardType.NO_KEY) {
                        when (tag.command) {
                            KeyCommandType.EXIT -> service.selectLiquidKeyboard(-1)
                            KeyCommandType.DEL_LEFT, KeyCommandType.DEL_RIGHT, KeyCommandType.REDO, KeyCommandType.UNDO -> {}
                            else -> {}
                        }
                    } else if (tabManager.isAfterTabSwitch(position)) {
                        // tab的位置在“更多”的右侧，不滚动tab，焦点仍然在”更多“上
                        select(position)
                    } else {
                        service.selectLiquidKeyboard(position)
                    }
                }
            }
        }
        keyboardView.apply {
            layoutManager = flexbox
            /*
            Timber.d(
                "configStylet() single_width=%s, keyHeight=%s, margin_x=%s, margin_top=%s",
                singleWidth, keyHeight, marginLeft, marginTop
            ) **/
            // simpleAdapter!!.configStyle(singleWidth, keyHeight, marginLeft, marginTop)
            //            simpleAdapter.configKey(single_width,height,margin_x,margin_top);
            adapter = simpleAdapter
            // 添加分割线
            // 设置添加删除动画
            // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
            isSelected = true
        }

        when (tabTag.type) {
            SymbolKeyboardType.HISTORY ->
                simpleAdapter.updateBeans(symbolHistory.toOrderedList().map(::SimpleKeyBean))
            SymbolKeyboardType.TABS ->
                simpleAdapter.updateBeans(tabManager.tabSwitchData)
            else ->
                simpleAdapter.updateBeans(tabManager.select(i))
        }
        Timber.d("Tab #%s with bean size %s", i, simpleAdapter.itemCount)
    }

    private fun initDbData(type: SymbolKeyboardType) {
        val dbAdapter = FlexibleAdapter(theme).apply {
            setListener(object : FlexibleAdapter.Listener {
                override fun onPaste(bean: DatabaseBean) {
                    service.currentInputConnection?.commitText(bean.text, 1)
                }

                override suspend fun onPin(bean: DatabaseBean) {
                    when (type) {
                        SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.pin(bean.id)
                        SymbolKeyboardType.COLLECTION -> CollectionHelper.pin(bean.id)
                        SymbolKeyboardType.DRAFT -> DraftHelper.pin(bean.id)
                        else -> return
                    }
                }

                override suspend fun onUnpin(bean: DatabaseBean) {
                    when (type) {
                        SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.unpin(bean.id)
                        SymbolKeyboardType.COLLECTION -> CollectionHelper.unpin(bean.id)
                        SymbolKeyboardType.DRAFT -> DraftHelper.unpin(bean.id)
                        else -> return
                    }
                }

                override suspend fun onDelete(bean: DatabaseBean) {
                    when (type) {
                        SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.delete(bean.id)
                        SymbolKeyboardType.COLLECTION -> CollectionHelper.delete(bean.id)
                        SymbolKeyboardType.DRAFT -> DraftHelper.delete(bean.id)
                        else -> return
                    }
                }

                // FIXME: 这个方法可能实现得比较粗糙，需要日后改进
                @SuppressLint("NotifyDataSetChanged")
                override suspend fun onDeleteAll() {
                    if (beans.all { it.pinned }) {
                        // 如果没有未置顶的条目，则删除所有已置顶的条目
                        when (type) {
                            SymbolKeyboardType.CLIPBOARD -> ClipboardHelper.deleteAll(false)
                            SymbolKeyboardType.COLLECTION -> CollectionHelper.deleteAll(false)
                            SymbolKeyboardType.DRAFT -> DraftHelper.deleteAll(false)
                            else -> return
                        }
                        updateBeans(emptyList())
                    } else {
                        // 如果有已置顶的条目，则删除所有未置顶的条目
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
                            else -> return
                        }
                    }
                    notifyDataSetChanged()
                }

                override val showCollectButton: Boolean = type != SymbolKeyboardType.COLLECTION
            })
        }
        keyboardView.apply {
            layoutManager = oneColumnStaggeredGrid
            adapter = dbAdapter
            // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
            isSelected = true
        }

        when (type) {
            SymbolKeyboardType.CLIPBOARD -> {
                service.lifecycleScope.launch {
                    dbAdapter.updateBeans(ClipboardHelper.getAll())
                }
            }
            SymbolKeyboardType.COLLECTION -> {
                service.lifecycleScope.launch {
                    dbAdapter.updateBeans(CollectionHelper.getAll())
                }
            }
            SymbolKeyboardType.DRAFT -> {
                service.lifecycleScope.launch {
                    dbAdapter.updateBeans(DraftHelper.getAll())
                }
            }
            else -> return
        }
    }

    private fun initCandidates() {
        val candidateAdapter = CandidateAdapter(theme).apply {
            setListener { position ->
                TextInputManager.getInstance().onCandidatePressed(position)
                if (Rime.isComposing) {
                    updateCandidates(Rime.candidatesWithoutSwitch.toList())
                    keyboardView.scrollToPosition(0)
                } else {
                    service.selectLiquidKeyboard(-1)
                }
            }
        }
        // 设置布局管理器
        keyboardView.apply {
            layoutManager = flexbox
            adapter = candidateAdapter
            isSelected = true
        }

        candidateAdapter.updateCandidates(Rime.candidatesWithoutSwitch.toList())
        keyboardView.scrollToPosition(0)
    }

    private fun initVarLengthKeys(data: List<SimpleKeyBean>) {
        val candidateAdapter = CandidateAdapter(theme).apply {
            setListener { position ->
                service.currentInputConnection?.commitText(data[position].text, 1)
            }
        }
        // 设置布局管理器
        keyboardView.apply {
            layoutManager = flexbox
            adapter = candidateAdapter
            keyboardView.isSelected = true
        }
        candidateAdapter.updateCandidates(
            data.map { b -> CandidateListItem("", b.text) },
        )
    }
}
