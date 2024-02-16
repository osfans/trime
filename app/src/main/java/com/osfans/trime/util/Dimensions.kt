@file:Suppress("NOTHING_TO_INLINE")

package com.osfans.trime.util

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View

/**
 * Value of dp to value of px.
 *
 * @param value The value of dp.
 * @return value of px
 */
inline fun dp2px(value: Float): Float = value * Resources.getSystem().displayMetrics.density

/**
 * Value of dp to value of px.
 *
 * @param value The value of dp.
 * @return value of px
 */
inline fun dp2px(value: Int): Int = (value * Resources.getSystem().displayMetrics.density).toInt()

/**
 * Value of sp to value of px.
 *
 * @param value The value of sp.
 * @return value of px
 */
inline fun sp2px(value: Float): Float = value * Resources.getSystem().displayMetrics.scaledDensity

/**
 * Value of sp to value of px.
 *
 * @param value The value of sp.
 * @return value of px
 */
inline fun sp2px(value: Int): Int = (value * Resources.getSystem().displayMetrics.scaledDensity).toInt()

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun Context.sp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), resources.displayMetrics).toInt()

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun Context.sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun View.sp(value: Int) = context.sp(value)

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun View.sp(value: Float) = context.sp(value)
