// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import com.osfans.trime.data.prefs.AppPrefs

object UserDbMigration {
    private val prefs get() = AppPrefs.defaultInstance().profile

    fun shouldImportUserDb(): Boolean = !prefs.userDbMigrated.getValue()

    fun markImported() {
        prefs.userDbMigrated.setValue(true)
    }

    fun onStorageModeChanged(
        from: DataStorageMode,
        to: DataStorageMode,
    ) {
        if (from == DataStorageMode.EXTERNAL_SYNC &&
            to == DataStorageMode.APP_STORAGE
        ) {
            prefs.userDbMigrated.setValue(false)
        }
    }
}
