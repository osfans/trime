package com.osfans.trime.ime.symbol

import android.graphics.Rect
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType

// Tab是滑动键盘顶部的标签按钮（包含返回键）。
// 为了公用候选栏的皮肤参数以及外观，保持了和普通键盘布局相似的代码。此类相当于原键盘布局的Rime.RimeCandidate
data class TabTag(
    /** display text of the tab */
    val text: String,
    /** additional comment of the tab */
    val comment: String,
    /** position and size info of the tab */
    val geometry: Rect,
    /** data type of the tab (unused) */
    val type: SymbolKeyboardType,
    /** action type of the tab */
    val command: KeyCommandType,
) {
    constructor(text: String, type: SymbolKeyboardType) :
        this(text, "", Rect(), type, KeyCommandType.NULL)
    constructor(text: String, comment: String, type: SymbolKeyboardType) :
        this(text, comment, Rect(), type, KeyCommandType.NULL)
    constructor(text: String, type: SymbolKeyboardType, command: KeyCommandType) :
        this(text, "", Rect(), type, command)
}
