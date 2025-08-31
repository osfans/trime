// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.NinePatchDrawable
import android.util.DisplayMetrics
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Read non-compiled Nine Patch image from file system.
 * https://stackoverflow.com/a/29824210
 */
object NinePatchBitmapFactory {
    private const val NO_COLOR = 0x00000001
    private const val TRANSPARENT_COLOR = 0x00000000

    fun createNinePatchDrawable(
        res: Resources?,
        bitmap: Bitmap,
    ): NinePatchDrawable {
        val rangeLists = checkBitmap(bitmap)
        val trimedBitmap = trimBitmap(bitmap)
        return createNinePatchWithCapInsets(
            res,
            trimedBitmap,
            rangeLists.rangeListX,
            rangeLists.rangeListY,
            null,
        )
    }

    private fun createNinePatchWithCapInsets(
        res: Resources?,
        bitmap: Bitmap,
        rangeListX: List<Range>,
        rangeListY: List<Range>,
        srcName: String?,
    ): NinePatchDrawable {
        val buffer =
            getByteBuffer(rangeListX, rangeListY)
        return NinePatchDrawable(res, bitmap, buffer.array(), Rect(), srcName)
    }

    private fun getByteBuffer(
        rangeListX: List<Range>,
        rangeListY: List<Range>,
    ): ByteBuffer {
        val buffer =
            ByteBuffer
                .allocate(4 + 4 * 7 + 4 * 2 * rangeListX.size + 4 * 2 * rangeListY.size + 4 * 9)
                .order(
                    ByteOrder.nativeOrder(),
                )
        buffer.put(0x01.toByte()) // was serialised
        buffer.put((rangeListX.size * 2).toByte()) // x div
        buffer.put((rangeListY.size * 2).toByte()) // y div
        buffer.put(0x09.toByte()) // color

        // skip
        buffer.putInt(0)
        buffer.putInt(0)

        // padding
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(0)

        // skip 4 bytes
        buffer.putInt(0)
        for (range in rangeListX) {
            buffer.putInt(range.start)
            buffer.putInt(range.end)
        }
        for (range in rangeListY) {
            buffer.putInt(range.start)
            buffer.putInt(range.end)
        }
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        buffer.putInt(NO_COLOR)
        return buffer
    }

    private fun checkBitmap(bitmap: Bitmap): RangeLists {
        val width = bitmap.width
        val height = bitmap.height
        val rangeListX = arrayListOf<Range>()
        var pos = -1
        for (i in 1 until width - 1) {
            val color = bitmap.getPixel(i, 0)
            val alpha = Color.alpha(color)
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            // 			System.out.println( String.valueOf(alpha) + "," + String.valueOf(red) + "," + String.valueOf(green) + "," + String.valueOf(blue) );
            if (alpha == 255 && red == 0 && green == 0 && blue == 0) {
                if (pos == -1) {
                    pos = i - 1
                }
            } else {
                if (pos != -1) {
                    rangeListX.add(Range(pos, i - 1))
                    pos = -1
                }
            }
        }
        if (pos != -1) {
            rangeListX.add(Range(pos, width - 2))
        }
        for (range in rangeListX) {
            Timber.v("(" + range.start + "," + range.end + ")")
        }
        val rangeListY: MutableList<Range> = ArrayList()
        pos = -1
        for (i in 1 until height - 1) {
            val color = bitmap.getPixel(0, i)
            val alpha = Color.alpha(color)
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            if (alpha == 255 && red == 0 && green == 0 && blue == 0) {
                if (pos == -1) {
                    pos = i - 1
                }
            } else {
                if (pos != -1) {
                    rangeListY.add(Range(pos, i - 1))
                    pos = -1
                }
            }
        }
        if (pos != -1) {
            rangeListY.add(Range(pos, height - 2))
        }
        for (range in rangeListY) {
            Timber.v("(" + range.start + "," + range.end + ")")
        }
        return RangeLists(rangeListX, rangeListY)
    }

    private fun trimBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        return Bitmap.createBitmap(bitmap, 1, 1, width - 2, height - 2)
    }

    fun loadBitmap(file: File): Bitmap? = runCatching {
        file.inputStream().buffered().use {
            BitmapFactory.decodeStream(it)
        }
    }.getOrNull()

    fun getDensityPostfix(res: Resources): String? = when (res.displayMetrics.densityDpi) {
        DisplayMetrics.DENSITY_LOW -> "ldpi"
        DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
        DisplayMetrics.DENSITY_HIGH -> "hdpi"
        DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
        DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
        DisplayMetrics.DENSITY_XXXHIGH -> "xxxhdpi"
        else -> null
    }

    class RangeLists(
        val rangeListX: List<Range>,
        val rangeListY: List<Range>,
    )

    data class Range(
        val start: Int,
        val end: Int,
    )
}
