package com.osfans.trime.ime.symbol

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
import com.osfans.trime.data.db.clipboard.ClipboardHelper
import com.osfans.trime.data.db.draft.DraftHelper
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.ime.text.TextInputManager.Companion.getInstance
import com.osfans.trime.util.ConfigGetter.getPixel
import com.osfans.trime.util.appContext
import com.osfans.trime.util.dp2px
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.math.ceil

class LiquidKeyboard(private val context: Context) {
    private val theme: Config = Config.get()
    private val service: Trime = Trime.getService()
    private var rootView: View? = null
    private lateinit var keyboardView: RecyclerView
    private var flexibleAdapter: FlexibleAdapter? = null
    private var simpleAdapter: SimpleAdapter? = null
    private var candidateAdapter: CandidateAdapter? = null
    private val simpleKeyBeans: MutableList<SimpleKeyBean>
    private var historyBeans: MutableList<SimpleKeyBean>? = null
    private var marginLeft = 0
    private var marginTop = 0
    private var singleWidth = 0
    private var parentWidth = 0
    private var keyHeight = 0
    private var isLand = false
    private val historySavePath: String

    private val dbAdapter: FlexibleAdapter by lazy {
        object : FlexibleAdapter() {
            override val theme: Config
                get() = this@LiquidKeyboard.theme

            override fun onPaste(id: Int) {
                service.currentInputConnection?.commitText(getBeanById(id).text, 1)
            }
        }
    }

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

    fun setLand(land: Boolean) {
        isLand = land
    }

    init {
        simpleKeyBeans = ArrayList()
        historySavePath = (
            appContext.getExternalFilesDir(null)!!.absolutePath +
                File.separator +
                "key_history"
            )
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
                initCandidateAdapter()
                initCandidate()
            }
            SymbolKeyboardType.VAR_LENGTH -> {
                TabManager.get().select(i)
                initCandidateAdapter()
                initVarLengthKeys(i)
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
                horizontal_gap = horizontal_gap / 2
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

    fun initFixData(i: Int) {
        val tabTag = TabManager.getTag(i)
        keyboardView.removeAllViews()
        flexibleAdapter = null

        keyboardView.removeAllViews()
        keyboardView.layoutManager = flexbox

        // 设置适配器
        simpleKeyBeans.clear()
        when (tabTag.type) {
            SymbolKeyboardType.HISTORY -> simpleKeyBeans.addAll(historyBeans!!)
            SymbolKeyboardType.TABS -> simpleKeyBeans.addAll(TabManager.get().tabSwitchData)
            else -> simpleKeyBeans.addAll(TabManager.get().select(i))
        }
        Timber.d("Tab.select(%s) beans.size=%s", i, simpleKeyBeans.size)
        simpleAdapter = SimpleAdapter(simpleKeyBeans)
        Timber.d(
            "configStylet() single_width=%s, keyHeight=%s, margin_x=%s, margin_top=%s",
            singleWidth, keyHeight, marginLeft, marginTop
        )
        simpleAdapter!!.configStyle(singleWidth, keyHeight, marginLeft, marginTop)
        //            simpleAdapter.configKey(single_width,height,margin_x,margin_top);
        keyboardView.adapter = simpleAdapter
        // 添加分割线
        // 设置添加删除动画
        // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
        keyboardView.isSelected = true

        // 列表适配器的点击监听事件
        simpleAdapter!!.setOnItemClickListener { view: View?, position: Int ->
            if (tabTag.type === SymbolKeyboardType.SYMBOL) {
                Trime.getService().inputSymbol(simpleKeyBeans[position].text)
            } else if (tabTag.type !== SymbolKeyboardType.TABS) {
                val ic = Trime.getService().currentInputConnection
                if (ic != null) {
                    val bean = simpleKeyBeans[position]
                    ic.commitText(bean.text, 1)
                    if (tabTag.type !== SymbolKeyboardType.HISTORY) {
                        historyBeans?.add(0, bean)
                        SimpleKeyDao.saveSymbolKeyHistory(historySavePath, historyBeans!!)
                    }
                }
            } else {
                val tag = TabManager.getTag(position)
                if (tag.type === SymbolKeyboardType.NO_KEY) {
                    when (tag.command) {
                        KeyCommandType.EXIT -> Trime.getService().selectLiquidKeyboard(-1)
                        KeyCommandType.DEL_LEFT, KeyCommandType.DEL_RIGHT, KeyCommandType.REDO, KeyCommandType.UNDO -> {}
                        else -> {}
                    }
                } else if (TabManager.get().isAfterTabSwitch(position)) {
                    // tab的位置在“更多”的右侧，不滚动tab，焦点仍然在”更多“上
                    select(position)
                } else {
                    Trime.getService().selectLiquidKeyboard(position)
                }
            }
        }
    }

    fun initDbData(type: SymbolKeyboardType) {
        keyboardView.removeAllViews()
        simpleAdapter = null

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

    fun initCandidateAdapter() {
        keyboardView.removeAllViews()
        simpleAdapter = null
        flexibleAdapter = null
        if (candidateAdapter == null) candidateAdapter = CandidateAdapter()

        // 设置布局管理器
        keyboardView.layoutManager = flexbox
        candidateAdapter!!.configStyle(marginLeft, marginTop)
        keyboardView.adapter = candidateAdapter
        keyboardView.isSelected = true
    }

    fun initCandidate() {
        candidateAdapter!!.updateCandidates()
        candidateAdapter!!.setOnItemClickListener { view: View?, position: Int ->
            getInstance().onCandidatePressed(position)
            if (Rime.isComposing()) {
                updateCandidates()
            } else Trime.getService().selectLiquidKeyboard(-1)
        }
    }

    fun initVarLengthKeys(i: Int) {
        simpleKeyBeans.clear()
        simpleKeyBeans.addAll(TabManager.get().select(i))
        candidateAdapter!!.setCandidates(simpleKeyBeans)
        candidateAdapter!!.setOnItemClickListener { view: View?, position: Int ->
            val ic = Trime.getService().currentInputConnection
            if (ic != null) {
                val bean = simpleKeyBeans[position]
                ic.commitText(bean.text, 1)
            }
        }
    }

    fun updateCandidates() {
        candidateAdapter!!.updateCandidates()
        candidateAdapter!!.notifyDataSetChanged()
        keyboardView.scrollToPosition(0)
    }
}
