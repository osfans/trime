package com.osfans.trime.ime.symbol

import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.util.CollectionUtils.getOrDefault
import timber.log.Timber

object TabManager {
    var currentTabIndex = 0
        private set
    private var tabSwitchPosition = 0

    val theme get() = ThemeManager.activeTheme
    val tabTags = arrayListOf<TabTag>()
    val tabSwitchData = mutableListOf<SimpleKeyBean>()
    private val keyboards = mutableListOf<List<SimpleKeyBean>>()
    private val notKeyboard = mutableListOf<SimpleKeyBean>()

    private val tagExit = TabTag("返回", SymbolKeyboardType.NO_KEY, KeyCommandType.EXIT)

    /**
     * 得到TABS中对应的真实索引 真实的索引是去除 没有keys列表的tagTab 之后按顺序排列的tagTab索引
     *
     * @param position 位置（索引）
     * @return int TABS中显示的真实索引
     */
    fun getTabSwitchPosition(position: Int): Int {
        var p = position
        var i = 0
        for (tag in tabTags) {
            if (SymbolKeyboardType.hasKey(tag.type)) {
                p--
                if (p <= 0) break
            }
            i++
        }
        return i
    }

    fun refresh() {
        val available = theme.liquid.getObject("keyboards") as List<String>? ?: return
        for (id in available) {
            Timber.d("preparing data for tab #$id")
            val keyboard = theme.liquid.getObject(id) as Map<String, Any>? ?: continue
            if (!keyboard.containsKey("type")) continue
            val name = getOrDefault(keyboard, "name", id) as String
            val type = SymbolKeyboardType.fromString(keyboard["type"] as String?)
            val keys = keyboard["keys"] ?: "1"
            addTabHasKeys(name, type, keys)
        }
        tabSwitchData.clear()
        tabSwitchData.addAll(
            tabTags.filter { SymbolKeyboardType.hasKeys(it.type) }
                .map { SimpleKeyBean(it.text) },
        )
    }

    private fun addListTab(
        name: String,
        type: SymbolKeyboardType,
        keyBeans: List<SimpleKeyBean>,
    ) {
        if (name.isBlank()) return
        if (SymbolKeyboardType.hasKeys(type)) {
            val index = tabTags.indexOfFirst { it.text == name }
            if (index >= 0) {
                keyboards[index] = keyBeans
            }
        }
        tabTags.add(TabTag(name, type))
        keyboards.add(keyBeans)
    }

    private fun addTabHasKeys(
        name: String,
        type: SymbolKeyboardType,
        keys: Any?,
    ) {
        // 处理single类型和no_key类型。前者把字符串切分为多个按键，后者把字符串转换为命令
        if (keys is String) {
            when (type) {
                SymbolKeyboardType.SINGLE -> addListTab(name, type, SimpleKeyDao.singleData(keys))
                SymbolKeyboardType.NO_KEY -> {
                    val commandType = KeyCommandType.fromString(keys)
                    tabTags.add(TabTag(name, type, commandType))
                    keyboards.add(notKeyboard)
                }
                else -> addListTab(name, type, SimpleKeyDao.simpleKeyboardData(keys))
            }
        }

        if (keys !is List<*>) return
        val keysList: MutableList<SimpleKeyBean> = ArrayList()
        for (o in keys) {
            if (o is String) {
                keysList.add(SimpleKeyBean(o))
            }

            if (o !is Map<*, *>) continue
            val p = o as Map<String, String>
            if (p.containsKey("click")) {
                if (p.containsKey("label")) {
                    keysList.add(SimpleKeyBean(p["click"]!!, p["label"]!!))
                } else {
                    keysList.add(SimpleKeyBean(p["click"]!!))
                }
                continue
            }

            val symbolMaps = SchemaManager.getActiveSchema().symbols
            for ((key, value) in p) {
                if (symbolMaps != null && symbolMaps.containsKey(value)) {
                    keysList.add(SimpleKeyBean(value, key))
                }
            }
        }
        addListTab(name, type, keysList)
    }

    fun selectTabByIndex(index: Int): List<SimpleKeyBean> {
        if (index !in tabTags.indices) return listOf()
        currentTabIndex = index
        val tag = tabTags[index]
        if (tag.type == SymbolKeyboardType.TABS) tabSwitchPosition = currentTabIndex
        return keyboards[index]
    }

    val selectedOrZero: Int
        get() = if (currentTabIndex == -1) 0 else currentTabIndex

    fun setTabExited() {
        currentTabIndex = -1
    }

    fun isAfterTabSwitch(position: Int): Boolean {
        return tabSwitchPosition <= position
    }

    val tabCandidates: ArrayList<TabTag>
        get() {
            var addExit = true
            for (tag in tabTags) {
                if (tag.command == KeyCommandType.EXIT) {
                    addExit = false
                    break
                }
            }
            if (addExit) {
                tabTags.add(tagExit)
                keyboards.add(notKeyboard)
            }
            return tabTags
        }
}
