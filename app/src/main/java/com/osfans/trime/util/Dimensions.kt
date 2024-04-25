// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("NOTHING_TO_INLINE")

package com.osfans.trime.util

import android.content.Context
import android.util.TypedValue
import android.view.View

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun Context.sp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), resources.displayMetrics).toInt()

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun Context.sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun View.sp(value: Int) = context.sp(value)

/** Converts Scaled Pixels to pixels. Returns an `Int` or a `Float` based on [value]'s type. */
inline fun View.sp(value: Float) = context.sp(value)
