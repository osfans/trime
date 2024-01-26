package com.osfans.trime.ime.symbol

@Suppress("ktlint:standard:function-naming")
object SimpleKeyDao {
    fun SimpleKeyboard(string: String): List<SimpleKeyBean> {
        val strings = string.split("\n+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val list: MutableList<SimpleKeyBean> = ArrayList()
        for (str in strings) {
            if (str.isEmpty()) continue
            val keyBean = SimpleKeyBean(str)
            list.add(keyBean)
        }
        return list
    }

    fun Single(string: String): List<SimpleKeyBean> {
        val list: MutableList<SimpleKeyBean> = ArrayList()
        var h = 0.toChar()
        for (element in string) {
            if (element in '\uD800'..'\udbff') {
                h = element
            } else if (element in '\udc00'..'\udfff') {
                list.add(SimpleKeyBean(java.lang.String.valueOf(charArrayOf(h, element))))
            } else {
                list.add(SimpleKeyBean(element.toString()))
            }
        }
        return list
    }
}
