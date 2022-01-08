package com.osfans.trime.ime.enums

import java.util.HashMap
import java.util.Locale

enum class SymbolKeyboardType {
    // 只占据tab位，不含keys（如返回键
    NO_KEY,

    //  剪贴板（大段文本自动缩略，按键长度自适应。）
    CLIPBOARD,

    //  文本框编辑历史，即“草稿箱”
    DRAFT,

    //  近期上屏符号历史（需要区分来源并提示？）
    HISTORY,

    //  以下类型的键盘，都必须包含keys列表

    //  按键使用固定宽度。单个字符即按键。SINGLE是默认类型的按键
    SINGLE,

    //  按键使用固定宽度。如不设置宽度，则自动换行
    SHORT,

    //  按键长度不固定
    LONG,

    //  需要展开显示的多行内容（不省略内容）
    MULTI_LINE,

    //  成对显示的符号。当光标选中一段文字时，点击此分类的符号，用成对符号包裹选中内容。当光标未选择文本时，插入一对符号并移动光标到符号内侧。
    PAIR;

    companion object {
        private val convertMap: HashMap<String, SymbolKeyboardType> = hashMapOf()

        init {
            for (type in values()) {
                convertMap[type.toString()] = type
            }
        }

        fun fromString(code: String): SymbolKeyboardType {
            val type = convertMap[code.uppercase(Locale.getDefault())]
            return type ?: SINGLE
        }

        fun fromObject(code: Any?): SymbolKeyboardType {
            if (code == null)
                return SINGLE
            val type = convertMap[code.toString().uppercase(Locale.getDefault())]
            return type ?: SINGLE
        }

        fun needKeys(type: SymbolKeyboardType): Boolean {
            return type > HISTORY
        }
    }
}
