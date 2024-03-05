package com.osfans.trime.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

fun ContentResolver.queryFileName(uri: Uri): String? =
    query(uri, null, null, null, null)?.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        it.getString(index)
    }
