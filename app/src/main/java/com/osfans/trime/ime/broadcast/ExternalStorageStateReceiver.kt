// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import com.osfans.trime.data.AppPrefs

class ExternalStorageStateReceiver(
    private val context: Context,
    private val mountedRunFunc: () -> Unit,
) {
    private val externalStorageStateReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                updateExternalStorageState(intent)
            }
        }

    private fun updateExternalStorageState(intent: Intent) {
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED == state) {
            // SD card is mounted and ready for use
            intent.data?.path?.let { path ->
                if (AppPrefs.Profile.getAppPath().startsWith(path)) {
                    mountedRunFunc()
                    context.unregisterReceiver(externalStorageStateReceiver)
                }
            }
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY == state) {
            // SD card is mounted, but it is read only
        } else {
            // SD card is unavailable
        }
    }

    fun listenExternalStorageChangeState() {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            context.registerReceiver(
                externalStorageStateReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_MEDIA_MOUNTED)
                    addDataScheme("file")
                },
            )
        }
    }
}
