package com.osfans.trime.ime.keyboard

data class KeyboardSize(
    val rowWidthTotalWeight: Map<Int, Float>,
    val defaultWidth: Float,
    val multiplier: Float,
    val height: List<Int>,
)
