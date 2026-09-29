/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import com.osfans.trime.util.appContext

class StorageResultContracts private constructor() {
    class OpenDirectory : ActivityResultContract<OpenDirectory.Args, Result<StorageDocument?>>() {
        private lateinit var args: Args

        override fun createIntent(
            context: Context,
            input: Args,
        ): Intent {
            args = input
            return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                val initialUri = input.initialUri
                if (initialUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                }
            }
        }

        override fun parseResult(
            resultCode: Int,
            intent: Intent?,
        ): Result<StorageDocument?> {
            if (resultCode != Activity.RESULT_OK || intent == null) {
                return Result.success(null)
            }
            return try {
                val tree = intent.data!!
                val write = args.writePermission
                val persistable = args.persistablePermission
                if (persistable) takePersistable(tree, write)
                val value = StorageDocs.stat(appContext, tree)
                Result.success(value)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

        data class Args(
            val initialUri: Uri?,
            val writePermission: Boolean,
            val persistablePermission: Boolean,
        )
    }

    companion object {
        private fun takePersistable(uri: Uri, write: Boolean) {
            var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            appContext.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }
}
