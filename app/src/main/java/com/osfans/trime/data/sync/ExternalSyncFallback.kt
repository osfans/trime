// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import android.content.Context
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.DeployNotification
import timber.log.Timber

object ExternalSyncFallback {
    suspend fun fallbackToAppStorage(
        context: Context,
        reason: Throwable? = null,
    ) {
        if (!RimeDataSync.usesExternalSync(context)) return
        Timber.w(reason, "External sync unavailable; falling back to app-specific storage")
        RimeDataSync.clearExternalTree(context)
        UserDbMigration.onStorageModeChanged(
            DataStorageMode.EXTERNAL_SYNC,
            DataStorageMode.APP_STORAGE,
        )
        AppPrefs.defaultInstance().profile.dataStorageMode.setValue(
            DataStorageMode.APP_STORAGE,
        )
        DeployNotification.showExternalSyncFallback()
    }
}
