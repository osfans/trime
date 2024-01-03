package com.osfans.trime.util

import android.content.Context
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

object PermissionUtils {
    @JvmStatic
    fun isAllGranted(context: Context): Boolean {
        return XXPermissions.isGranted(context, Permission.MANAGE_EXTERNAL_STORAGE)
    }
}
