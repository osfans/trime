/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.prefs

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.osfans.trime.core.data.DataStorageMode

/** Run before recording this process's PID or creating the first Rime configuration. */
@SuppressLint("ApplySharedPref", "UseKtx")
internal fun initializeDefaultStoragePreferences(
    preferences: SharedPreferences,
    existingRuntimeData: Boolean,
): Boolean {
    if (preferences.contains(AppPrefs.Profile.DATA_STORAGE_MODE)) return true
    // An old installation could rely on the previous implicit EXTERNAL_SYNC default.
    // A restored data directory also counts, even if its preferences were not restored.
    val fresh = preferences.all.isEmpty() && !existingRuntimeData
    return preferences.edit()
        .putString(
            AppPrefs.Profile.DATA_STORAGE_MODE,
            (if (fresh) DataStorageMode.APP_STORAGE else DataStorageMode.EXTERNAL_SYNC).name,
        )
        .putBoolean(AppPrefs.Profile.FRESH_INPUT_SETUP_PENDING, fresh)
        .commit()
}
