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
