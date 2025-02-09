/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.graphics.RectF

inline fun RectF.any(predicate: (Float) -> Boolean): Boolean = predicate(left) || predicate(top) || predicate(right) || predicate(bottom)
