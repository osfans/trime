package com.osfans.trime.ime.symbol

import android.graphics.Rect
import com.osfans.trime.ime.enums.KeyCommandType
import com.osfans.trime.ime.enums.SymbolKeyboardType

// Tab是滑动键盘顶部的标签按钮（包含返回键）。
// 为了公用候选栏的皮肤参数以及外观，保持了和普通键盘布局相似的代码。此类相当于原键盘布局的Rime.RimeCandidate
class TabTag {
    // text for tab
    @JvmField
    var text: String

    @JvmField
    var geometry: Rect? = null // position and size info of tab
    var comment: String? = null

    // not used
    @JvmField
    var type: SymbolKeyboardType

    @JvmField
    var command: KeyCommandType? = null // command for tag without key

    constructor(text: String, type: SymbolKeyboardType, comment: String?) {
        this.text = text
        this.comment = comment
        this.type = type
    }

    constructor(text: String, type: SymbolKeyboardType, command: KeyCommandType?) {
        this.text = text
        this.command = command
        this.type = type
    }
}
