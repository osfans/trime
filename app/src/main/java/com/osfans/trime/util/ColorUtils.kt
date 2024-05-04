// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import timber.log.Timber

object ColorUtils {
    @JvmStatic
    fun parseColor(s: String): Int? {
        val hex: String = if (s.startsWith("#")) s.replace("#", "0x") else s
        return try {
            val completed =
                if (hex.startsWith("0x") || hex.startsWith("0X")) {
                    when {
                        hex.length == 3 || hex.length == 4 -> {
                            String.format("#%02x000000", java.lang.Long.decode(hex)) // 0xA -> #AA000000
                        }
                        hex.length < 8 -> { // 0xGBB -> #RRGGBB
                            String.format("#%06x", java.lang.Long.decode(hex))
                        }
                        hex.length == 9 -> { // 0xARRGGBB -> #AARRGGBB
                            "#0" + hex.substring(2)
                        }
                        else -> {
                            "#" + hex.substring(2) // 0xAARRGGBB -> #AARRGGBB, 0xRRGGBB -> #RRGGBB
                        }
                    }
                } else {
                    hex // red, green, blue ...
                }
            Color.parseColor(completed)
        } catch (e: IllegalArgumentException) {
            Timber.w("Invalid or unknown color value: %s", s)
            null
        }
    }

    /**
     * 计算颜色相对亮度，如果超出 0.73，认为是亮色，否则认为是暗色
     */
    fun isContrastedDark(
        @ColorInt color: Int,
    ): Boolean {
        val r = color.red / 255f
        val g = color.green / 255f
        val b = color.blue / 255f
        return (r * 0.2126f + g * 0.7152f + b * 0.0722f) > 0.73f
    }
}
