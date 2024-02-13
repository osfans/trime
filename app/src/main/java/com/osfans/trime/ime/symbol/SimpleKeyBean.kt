package com.osfans.trime.ime.symbol

class SimpleKeyBean(
    val text: String = "",
    private val _label: String = "",
) {
    constructor(text: String) : this(text, "")

    val label: String
        get() = _label.ifEmpty { text }
}
