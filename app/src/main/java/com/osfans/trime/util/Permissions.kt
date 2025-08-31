/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.content.Context
import android.os.Environment
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.R

@Suppress("NOTHING_TO_INLINE")
inline fun Context.isStorageAvailable(): Boolean = XXPermissions.isGranted(this, Permission.MANAGE_EXTERNAL_STORAGE) &&
    Environment.getExternalStorageDirectory().absolutePath.isNotEmpty()

fun Context.requestExternalStoragePermission() {
    XXPermissions
        .with(this)
        .permission(Permission.MANAGE_EXTERNAL_STORAGE)
        .request(
            object : OnPermissionCallback {
                override fun onGranted(
                    permissions: List<String>,
                    all: Boolean,
                ) {
                    if (all) {
                        toast(R.string.external_storage_permission_granted)
                    }
                }

                override fun onDenied(
                    permissions: List<String>,
                    never: Boolean,
                ) {
                    if (never) {
                        toast(R.string.external_storage_permission_denied)
                        XXPermissions.startPermissionActivity(
                            this@requestExternalStoragePermission,
                            permissions,
                        )
                    } else {
                        toast(R.string.external_storage_permission_denied)
                    }
                }
            },
        )
}
