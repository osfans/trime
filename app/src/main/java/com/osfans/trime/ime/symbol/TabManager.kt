package com.osfans.trime.ime.symbol

import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.util.config.ConfigItem
import com.osfans.trime.util.config.ConfigList
import com.osfans.trime.util.config.ConfigMap
import com.osfans.trime.util.config.ConfigValue
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
        reset()

        val available = theme.liquid.getList("keyboards") ?: return
        for (item in available) {
            val id = item?.configValue?.getString() ?: ""
            Timber.d("preparing data for tab #$id")
            val keyboard = theme.liquid.getMap(id) ?: continue
            if (!keyboard.containsKey("type")) continue
            val name = keyboard.getValue("name")?.getString() ?: id
            val type = SymbolKeyboardType.fromString(keyboard.getValue("type")?.getString())
            val keys = keyboard["keys"]
            addTabHasKeys(name, type, keys)
        }
        tabSwitchData.addAll(
            tabTags.filter { SymbolKeyboardType.hasKeys(it.type) }
                .map { SimpleKeyBean(it.text) },
        )
    }

    private fun reset() {
        tabTags.clear()
        keyboards.clear()
        tabSwitchData.clear()
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
        keys: ConfigItem?,
    ) {
        // 处理single类型和no_key类型。前者把字符串切分为多个按键，后者把字符串转换为命令
        if (keys is ConfigValue) {
            val key = keys.getString()
            when (type) {
                SymbolKeyboardType.SINGLE -> addListTab(name, type, SimpleKeyDao.singleData(key))
                SymbolKeyboardType.NO_KEY -> {
                    val commandType = KeyCommandType.fromString(key)
                    tabTags.add(TabTag(name, type, commandType))
                    keyboards.add(notKeyboard)
                }
                else -> addListTab(name, type, SimpleKeyDao.simpleKeyboardData(key))
            }
        }

        if (keys !is ConfigList) return
        val keysList: MutableList<SimpleKeyBean> = ArrayList()
        for (k in keys) {
            if (k is ConfigValue) {
                keysList.add(SimpleKeyBean(k.getString()))
            }

            if (k !is ConfigMap) continue
            val p = k.entries.associate { (s, n) -> s to n!!.configValue.getString() }
            if (k.containsKey("click")) {
                if (p.containsKey("label")) {
                    keysList.add(SimpleKeyBean(p["click"]!!, p["label"]!!))
                } else {
                    keysList.add(SimpleKeyBean(p["click"]!!))
                }
                continue
            }

            val symbolMaps = SchemaManager.getActiveSchema().symbols
            for ((key, value) in p) {
                if (symbolMaps?.containsKey(value) == true) {
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
