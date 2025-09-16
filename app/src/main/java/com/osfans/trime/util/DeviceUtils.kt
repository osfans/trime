/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.os.Build

object DeviceUtils {
    /**
     * https://stackoverflow.com/questions/60122037/how-can-i-detect-samsung-one-ui
     */
    val isSamsungOneUI: Boolean by lazy {
        try {
            val semPlatformInt = Build.VERSION::class.java
                .getDeclaredField("SEM_PLATFORM_INT")
                .getInt(null)
            semPlatformInt > 90000
        } catch (e: Exception) {
            false
        }
    }
}
