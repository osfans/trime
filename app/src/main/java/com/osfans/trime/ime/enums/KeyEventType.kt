package com.osfans.trime.ime.enums

/** 按键事件枚举  */
enum class KeyEventType {

    // 长按按键展开列表时，正上方为长按对应按键，排序如上，不展示combo及之前的按键，展示extra
    COMPOSING, HAS_MENU, PAGING, COMBO, ASCII, CLICK, SWIPE_UP, LONG_CLICK, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, EXTRA;

    companion object {
        @JvmStatic
        fun valueOf(ordinal: Int): KeyEventType {
            if (ordinal < 0 || ordinal >= values().size) {
                return EXTRA
            }
            return values()[ordinal]
        }
    }
}
