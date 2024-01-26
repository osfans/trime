package com.osfans.trime.ime.symbol

import java.io.Serializable

class SimpleKeyBean : Serializable {
    var text: String = ""
    private var label: String? = null

    constructor(text: String?) {
        this.text = text ?: ""
    }

    constructor(text: String?, label: String?) {
        this.text = text ?: ""
        this.label = label
    }

    fun getLabel(): String {
        return if (label.isNullOrEmpty()) text else label!!
    }

    override fun toString(): String {
        return "SimpleKeyBean {text='$text', label='$label'}"
    }
}
