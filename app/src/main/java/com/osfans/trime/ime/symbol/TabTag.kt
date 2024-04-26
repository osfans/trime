// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import com.osfans.trime.ime.enums.KeyCommandType

// Tab是滑动键盘顶部的标签按钮（包含返回键）。
// 为了公用候选栏的皮肤参数以及外观，保持了和普通键盘布局相似的代码。此类相当于原键盘布局的Rime.RimeCandidate
data class TabTag(
    /** display text of the tab */
    val text: String,
    /** additional comment of the tab */
    val comment: String,
    /** data type of the tab (unused) */
    val type: SymbolBoardType,
    /** action type of the tab */
    val command: KeyCommandType,
) {
    constructor(text: String, type: SymbolBoardType) :
        this(text, "", type, KeyCommandType.NULL)
    constructor(text: String, comment: String, type: SymbolBoardType) :
        this(text, comment, type, KeyCommandType.NULL)
    constructor(text: String, type: SymbolBoardType, command: KeyCommandType) :
        this(text, "", type, command)
}
