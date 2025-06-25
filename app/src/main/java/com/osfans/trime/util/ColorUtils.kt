// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import androidx.annotation.ColorInt
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

object ColorUtils {
    /**
     * 计算颜色相对亮度，如果超出 0.73，认为是亮色，否则认为是暗色
     */
    fun isContrastedDark(
        @ColorInt color: Int,
    ): Boolean {
        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        return (r * 0.2126f + g * 0.7152f + b * 0.0722f) <= 0.73f
    }
}
