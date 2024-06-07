// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.storage

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract

object StorageUtils {
    private fun getDocumentsUiPackage(context: Context): String? {
        // See android.permission.cts.ProviderPermissionTest.testManageDocuments()
        val packageInfos =
            context.packageManager.getPackagesHoldingPermissions(
                arrayOf(android.Manifest.permission.MANAGE_DOCUMENTS),
                0,
            )
        val packageInfo =
            packageInfos.firstOrNull { it.packageName.endsWith(".documentsui") }
                ?: packageInfos.firstOrNull()
        return packageInfo?.packageName
    }

    fun getViewDirIntent(context: Context): Intent? {
        val viewIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    DocumentsContract.buildRootsUri("${context.packageName}.provider"),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                    addCategory(Intent.CATEGORY_OPENABLE)
                getDocumentsUiPackage(context)?.let { setPackage(it) }
            }
        return if (viewIntent.resolveActivity(context.packageManager) != null) {
            viewIntent
        } else {
            null
        }
    }
}
