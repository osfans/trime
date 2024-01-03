package com.osfans.trime.util

import android.graphics.Color
import timber.log.Timber

object ColorUtils {
    @JvmStatic
    fun parseColor(s: String?): Int? {
        if (s == null) return null
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
}
