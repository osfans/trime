package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.Config
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.clipboard.ClipboardHelper
import com.osfans.trime.data.db.draft.DraftHelper
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.ime.text.TextInputManager
import com.osfans.trime.util.ConfigGetter.getPixel
import com.osfans.trime.util.appContext
import com.osfans.trime.util.dp2px
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.ceil

class LiquidKeyboard(private val context: Context) {
    private val theme: Config = Config.get()
    private val service: Trime = Trime.getService()
    private var rootView: View? = null
    private lateinit var keyboardView: RecyclerView
    private var historyBeans: MutableList<SimpleKeyBean>? = null
    private var marginLeft = 0
    private var marginTop = 0
    private var singleWidth = 0
    private var parentWidth = 0
    private var keyHeight = 0
    var isLand = false
    private val historySavePath = "${appContext.getExternalFilesDir(null)!!.absolutePath}/key_history"

    private val flexbox: FlexboxLayoutManager by lazy {
        return@lazy FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW // 主轴为水平方向，起点在左端。
            flexWrap = FlexWrap.WRAP // 按正常方向换行
            justifyContent = JustifyContent.FLEX_START // 交叉轴的起点对齐
            // alignItems = AlignItems.BASELINE
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
        calcPadding(tag.type)
        when (tag.type) {
            SymbolKeyboardType.CLIPBOARD,
            SymbolKeyboardType.COLLECTION,
            SymbolKeyboardType.DRAFT -> {
                TabManager.get().select(i)
                initDbData(tag.type)
            }
            SymbolKeyboardType.CANDIDATE -> {
                TabManager.get().select(i)
                initCandidates()
            }
            SymbolKeyboardType.VAR_LENGTH -> {
                initVarLengthKeys(TabManager.get().select(i))
            }
            SymbolKeyboardType.SYMBOL, SymbolKeyboardType.HISTORY, SymbolKeyboardType.TABS -> {
                TabManager.get().select(i)
                initFixData(i)
            }
            else -> initFixData(i)
        }
        return tag.type
    }

    // 设置liquidKeyboard共用的布局参数
    fun calcPadding(width: Int) {
        parentWidth = width
        val liquid_config = theme.liquidKeyboard

        // liquid_keyboard/margin_x定义了每个键左右两边的间隙，也就是说相邻两个键间隙是x2，而horizontal_gap定义的是spacer，使用时需要/2
        if (liquid_config != null) {
            if (liquid_config.containsKey("margin_x")) {
                val o: Any = liquid_config.getPixel("margin_x", 0f)
                marginLeft = o as Int
            }
        }
        if (marginLeft == 0) {
            var horizontal_gap = theme.getPixel("horizontal_gap")
            if (horizontal_gap > 1) {
                horizontal_gap /= 2
            }
            marginLeft = horizontal_gap
        }

        // 初次显示布局，需要刷新背景
        rootView = keyboardView.parent as View
        val keyboardBackground = theme.getDrawable("liquid_keyboard_background", null, null, null, null)
        if (keyboardBackground != null) rootView!!.background = keyboardBackground
        var keyboardHeight = theme.getPixel("keyboard_height")
        if (isLand) {
            val keyBoardHeightLand = theme.getPixel("keyboard_height_land")
            if (keyBoardHeightLand > 0) keyboardHeight = keyBoardHeightLand
        }
        var row = theme.getLiquidFloat("row").toInt()
        if (row > 0) {
            if (isLand) {
                val r = theme.getLiquidFloat("row_land")
                if (r > 0) row = r.toInt()
            }
            val rawHeight = theme.getLiquidFloat("key_height")
            val rawVGap = theme.getLiquidFloat("vertical_gap")
            val scale = keyboardHeight.toFloat() / ((rawHeight + rawVGap) * row)
            marginTop = ceil((rawVGap * scale).toDouble()).toInt()
            keyHeight = keyboardHeight / row - marginTop
        } else {
            keyHeight = theme.getLiquidPixel("key_height_land")
            if (!isLand || keyHeight <= 0) keyHeight = theme.getLiquidPixel("key_height")
            marginTop = theme.getLiquidPixel("vertical_gap")
        }
        Timber.i("config keyHeight=$keyHeight marginTop=$marginTop")
        if (isLand) {
            singleWidth = theme.getLiquidPixel("single_width_land")
            if (singleWidth <= 0) singleWidth = theme.getLiquidPixel("single_width")
        } else singleWidth = theme.getLiquidPixel("single_width")
        if (singleWidth <= 0) singleWidth = context.resources.getDimensionPixelSize(R.dimen.simple_key_single_width)
    }

    // 每次点击tab都需要刷新的参数
    private fun calcPadding(type: SymbolKeyboardType) {
        val padding = theme.keyboardPadding
        if (type === SymbolKeyboardType.SINGLE) {
            padding[0] = (
                (if (rootView!!.width > 0) rootView!!.width else parentWidth) %
                    (singleWidth + marginLeft * 2) /
                    2
                )
            padding[1] = padding[0]
        }
        rootView!!.setPadding(padding[0], 0, padding[1], 0)
        historyBeans = SimpleKeyDao.getSymbolKeyHistory(historySavePath)
    }

    private fun initFixData(i: Int) {
        val tabTag = TabManager.getTag(i)
        keyboardView.removeAllViews()

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
                            historyBeans?.add(0, bean)
                            SimpleKeyDao.saveSymbolKeyHistory(historySavePath, historyBeans!!)
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
                    } else if (TabManager.get().isAfterTabSwitch(position)) {
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
                simpleAdapter.updateBeans(historyBeans!!)
            SymbolKeyboardType.TABS ->
                simpleAdapter.updateBeans(TabManager.get().tabSwitchData)
            else ->
                simpleAdapter.updateBeans(TabManager.get().select(i))
        }
        Timber.d("Tab #%s with bean size %s", i, simpleAdapter.itemCount)
    }

    private fun initDbData(type: SymbolKeyboardType) {
        keyboardView.removeAllViews()

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
        keyboardView.removeAllViews()

        val candidateAdapter = CandidateAdapter(theme).apply {
            setListener { position ->
                TextInputManager.getInstance().onCandidatePressed(position)
                if (Rime.isComposing()) {
                    updateCandidates(Rime.getCandidatesWithoutSwitch().asList())
                    keyboardView.scrollToPosition(0)
                } else service.selectLiquidKeyboard(-1)
            }
        }
        // 设置布局管理器
        keyboardView.apply {
            layoutManager = flexbox
            adapter = candidateAdapter
            isSelected = true
        }

        candidateAdapter.updateCandidates(Rime.getCandidatesWithoutSwitch().asList())
        keyboardView.scrollToPosition(0)
    }

    private fun initVarLengthKeys(data: List<SimpleKeyBean>) {
        keyboardView.removeAllViews()

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
            data.map { b -> Rime.RimeCandidate(b.text, "") }
        )
    }
}
