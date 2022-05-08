package com.osfans.trime.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.osfans.trime.data.Config

class GraphicUtils(
    context: Context
) {
    private var hanBFont: Typeface = Typeface.DEFAULT
    private var latinFont: Typeface = Typeface.DEFAULT

    companion object {
        const val HAN_B_FONT = "hanb_font"
        const val LATIN_FONT = "latin_font"
    }

    init {
        val imeConfig = Config.get(context)
        hanBFont = imeConfig.getFont(HAN_B_FONT)
        latinFont = imeConfig.getFont(LATIN_FONT)
    }

    private fun determineTypeface(codePoint: Int, font: Typeface): Typeface {
        return if (hanBFont != Typeface.DEFAULT && Character.isSupplementaryCodePoint(codePoint)) {
            hanBFont
        } else if (latinFont != Typeface.DEFAULT && codePoint < 0x2E80) {
            latinFont
        } else font
    }

    fun Paint.measureText(text: String, font: Typeface): Float {
        if (text.isEmpty()) return 0.0f
        val codePoints = text.codePointCount(0, text.length)
        var x = 0.0f
        if (latinFont != Typeface.DEFAULT ||
            (hanBFont != Typeface.DEFAULT && text.length > codePoints)
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

    fun Canvas.drawText(text: String, centerX: Float, y: Float, paint: Paint, font: Typeface) {
        if (text.isEmpty()) return
        val codePoints = text.codePointCount(0, text.length)
        var x = centerX - paint.measureText(text, font) / 2
        if (latinFont != Typeface.DEFAULT ||
            (hanBFont != Typeface.DEFAULT && text.length > codePoints)
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
