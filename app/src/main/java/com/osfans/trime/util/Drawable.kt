// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.NinePatch
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi

fun rippleDrawable(
    @ColorInt color: Int,
) = RippleDrawable(ColorStateList.valueOf(color), null, ColorDrawable(Color.WHITE))

@RequiresApi(Build.VERSION_CODES.M)
fun borderlessRippleDrawable(
    @ColorInt color: Int,
    r: Int = RippleDrawable.RADIUS_AUTO,
) = RippleDrawable(ColorStateList.valueOf(color), null, null).apply {
    radius = r
}

fun pressHighlightDrawable(
    @ColorInt color: Int,
) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(color))
}

fun circlePressHighlightDrawable(
    @ColorInt color: Int,
) = StateListDrawable().apply {
    addState(
        intArrayOf(android.R.attr.state_pressed),
        ShapeDrawable(OvalShape()).apply { paint.color = color },
    )
}

fun borderDrawable(
    width: Int,
    @ColorInt stroke: Int,
    @ColorInt background: Int = Color.TRANSPARENT,
) = GradientDrawable().apply {
    setStroke(width, stroke)
    setColor(background)
}

fun borderDrawable(
    width: Int,
    radius: Float,
    alpha: Int,
    @ColorInt stroke: Int,
    @ColorInt background: Int = Color.TRANSPARENT,
) = GradientDrawable().apply {
    setStroke(width, stroke)
    cornerRadius = radius
    setColor(background)
    setAlpha(alpha)
}

fun bitmapDrawable(path: String?): Drawable? {
    path ?: return null
    val bitmap = BitmapFactory.decodeFile(path) ?: return null
    if (path.endsWith(".9.png")) {
        val chunk = bitmap.ninePatchChunk
        return if (NinePatch.isNinePatchChunk(chunk)) {
            // for compiled nine patch image
            NinePatchDrawable(Resources.getSystem(), bitmap, chunk, Rect(), null)
        } else {
            // for source nine patch image
            NinePatchBitmapFactory.createNinePatchDrawable(Resources.getSystem(), bitmap)
        }
    }
    return BitmapDrawable(Resources.getSystem(), bitmap)
}

fun StateListDrawable.stateDrawableAt(index: Int): Drawable =
    javaClass
        .getMethod(
            "getStateDrawable",
            Int::class.javaPrimitiveType,
        ).invoke(this, index) as Drawable

fun StateListDrawable.indexOfStateSet(stateSet: IntArray): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        findStateDrawableIndex(stateSet)
    } else {
        javaClass.getMethod("getStateDrawableIndex", IntArray::class.java).invoke(this, stateSet) as Int
    }
