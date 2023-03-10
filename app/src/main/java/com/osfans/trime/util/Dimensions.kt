@file:Suppress("NOTHING_TO_INLINE")

package com.osfans.trime.util

import android.content.res.Resources

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
