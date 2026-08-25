// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import java.io.File

fun Context.launchBrowseAppRimeDataDir() {
    val externalFilesDir = getExternalFilesDir(null)
    if (externalFilesDir == null) {
        toast(R.string.browse_app_data_dir_failed)
        return
    }
    val authority = "${applicationInfo.packageName}.provider"
    val docIdPrefix = "${externalFilesDir.parent}${File.separator}"
    val rootId = externalFilesDir.absolutePath.removePrefix(docIdPrefix)
    val rimeDocumentId = DataManager.userDataDir.absolutePath.removePrefix(docIdPrefix)
    val rootUri = DocumentsContract.buildRootUri(authority, rootId)
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(rootUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri(authority, rimeDocumentId),
                )
            }
        }
    runCatching { startActivity(intent) }
        .onFailure { toast(R.string.browse_app_data_dir_failed) }
}
