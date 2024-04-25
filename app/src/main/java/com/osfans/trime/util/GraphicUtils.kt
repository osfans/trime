// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.util.GraphicUtils.measureText

object GraphicUtils {
    private fun determineTypeface(
        codePoint: Int,
        font: Typeface,
    ): Typeface {
        if (Character.isSupplementaryCodePoint(codePoint)) {
            FontManager.hanBFont.let { if (it != Typeface.DEFAULT) return@determineTypeface it }
        } else if (codePoint < 0x2E80) {
            FontManager.latinFont.let { if (it != Typeface.DEFAULT) return@determineTypeface it }
        }
        return font
    }

    @JvmStatic
    fun Paint.measureText(
        text: String,
        font: Typeface,
    ): Float {
        if (text.isEmpty()) return 0.0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && font != Typeface.DEFAULT) {
            this.typeface = font
            return this.measureText(text)
        }

        val codePoints = text.codePointCount(0, text.length)
        var x = 0.0f
        if (FontManager.latinFont != Typeface.DEFAULT ||
            (FontManager.hanBFont != Typeface.DEFAULT && text.length > codePoints)
        ) {
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                val charCount = Character.charCount(codePoint)
                this.typeface = determineTypeface(codePoint, font)
                x += this.measureText(text, offset, offset + charCount)
                offset += charCount
            }
            this.typeface = font
        } else {
            this.typeface = font
            x = this.measureText(text)
        }
        return x
    }

    @JvmStatic
    fun Canvas.drawText(
        text: String,
        centerX: Float,
        y: Float,
        paint: Paint,
        font: Typeface,
    ) {
        if (text.isEmpty()) return
        var x = centerX - paint.measureText(text, font) / 2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && font != Typeface.DEFAULT) {
            paint.typeface = font
            this.drawText(text, x, y, paint)
            return
        }

        val codePoints = text.codePointCount(0, text.length)
        if (FontManager.latinFont != Typeface.DEFAULT ||
            (FontManager.hanBFont != Typeface.DEFAULT && text.length > codePoints)
        ) {
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                val charCount = Character.charCount(codePoint)
                paint.typeface = determineTypeface(codePoint, font)
                this.drawText(text, offset, offset + charCount, x, y, paint)
                x += paint.measureText(text, offset, offset + charCount)
                offset += charCount
            }
        } else {
            paint.typeface = font
            this.drawText(text, x, y, paint)
        }
    }
}
