/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

fun @receiver:ColorInt Int.alpha(a: Float) = ColorUtils.setAlphaComponent(this, (a * 0xff).roundToInt())
