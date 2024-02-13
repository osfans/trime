package com.osfans.trime.ime.symbol

import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.util.CollectionUtils.getOrDefault
import timber.log.Timber

// 使用TabManager时，不应该使用变量保存TabManager实例，应该使用TabManager.get()方法获取
class TabManager private constructor() {
    var selected = 0
        private set
    private val keyboardData: List<SimpleKeyBean> = ArrayList()
    private val tabSwitchData: MutableList<SimpleKeyBean> = ArrayList()
    private val tabTags = ArrayList<TabTag>()
    private var tabSwitchPosition = 0
    private val keyboards: MutableList<List<SimpleKeyBean>> = ArrayList()
    private val notKeyboard: List<SimpleKeyBean> = ArrayList()
    private val tagExit = TabTag("返回", SymbolKeyboardType.NO_KEY, KeyCommandType.EXIT)

    fun getTabSwitchData(): List<SimpleKeyBean> {
        if (tabSwitchData.size > 0) return tabSwitchData
        for (tag in tabTags) {
            if (SymbolKeyboardType.hasKey(tag.type)) tabSwitchData.add(SimpleKeyBean(tag.text))
        }
        return tabSwitchData
    }

    /**
     * 得到TABS中对应的TabTag 去除不显示的tagTab(没有keys列表的tagTab)之后按顺序排列tagTab,再从中获取TabTag
     *
     * @param position 位置（索引）
     * @return TabTag
     */
    fun getTabSwitchTabTag(position: Int): TabTag? {
        var i = 0
        for (tag in tabTags) {
            if (SymbolKeyboardType.hasKey(tag.type)) {
                if (i++ == position) return tag
            }
        }
        return null
    }

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

    init {
        initLiquidKboards()
    }

    private fun initLiquidKboards() {
        val theme = ThemeManager.activeTheme
        Timber.i("TabManager init")
        // 获取 tab 列表
        val availables =
            theme.liquid.getObject("keyboards") as List<String>?
                ?: return
        for (id in availables) {
            Timber.i("add liquidKboard tab id=$id")
            val keyboard: Map<String, Any> = theme.liquid.getObject(id) as Map<String, Any>? ?: continue
            val name = getOrDefault(keyboard, "name", id) as String
            if (!keyboard.containsKey("type")) continue

            val type = SymbolKeyboardType.fromString(keyboard["type"] as String?)
            val keys = keyboard["keys"] ?: "1"
            addTabHasKeys(name, type, keys)
        }
    }

    private fun addListTab(
        name: String,
        type: SymbolKeyboardType,
        keyBeans: List<SimpleKeyBean>,
    ) {
        if (name.trim { it <= ' ' }.isEmpty()) return
        if (SymbolKeyboardType.hasKeys(type)) {
            for (i in tabTags.indices) {
                val tag = tabTags[i]
                if (tag.text == name) {
                    keyboards[i] = keyBeans
                    return
                }
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
                SymbolKeyboardType.SINGLE -> addListTab(name, type, SimpleKeyDao.Single(keys))
                SymbolKeyboardType.NO_KEY -> {
                    val commandType = KeyCommandType.fromString(keys)
                    tabTags.add(TabTag(name, type, commandType))
                    keyboards.add(notKeyboard)
                }
                else -> addListTab(name, type, SimpleKeyDao.SimpleKeyboard(keys))
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

    fun select(tabIndex: Int): List<SimpleKeyBean> {
        selected = tabIndex
        if (tabIndex >= tabTags.size) return keyboardData
        val tag = tabTags[tabIndex]
        if (tag.type == SymbolKeyboardType.TABS) tabSwitchPosition = selected
        return keyboards[tabIndex]
    }

    val selectedOrZero: Int
        get() = if (selected == -1) 0 else selected

    fun setTabExited() {
        selected = -1
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

    companion object {
        private var self: TabManager? = null
            get() {
                if (field == null) {
                    field = TabManager()
                }
                return field
            }

        fun get(): TabManager {
            return self!!
        }

        fun updateSelf() {
            self = TabManager()
        }

        fun getTag(i: Int): TabTag {
            return self!!.tabTags[i]
        }

        fun getTagIndex(name: String?): Int {
            if (name.isNullOrEmpty()) return 0
            for (i in self!!.tabTags.indices) {
                val tag = self!!.tabTags[i]
                if (tag.text == name) {
                    return i
                }
            }
            return 0
        }

        fun getTagIndex(type: SymbolKeyboardType?): Int {
            if (type == null) return 0
            for (i in self!!.tabTags.indices) {
                val tag = self!!.tabTags[i]
                if (tag.type == type) {
                    return i
                }
            }
            return 0
        }
    }
}
