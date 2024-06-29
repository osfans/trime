// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.content.res.Configuration

fun Configuration.isNightMode() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun Configuration.isLandscape() = orientation == Configuration.ORIENTATION_LANDSCAPE
