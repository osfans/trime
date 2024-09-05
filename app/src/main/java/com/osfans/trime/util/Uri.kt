// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import timber.log.Timber
import java.io.File
import java.lang.reflect.Array

fun Context.getUriForFile(file: File): Uri =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(this, "${applicationInfo.packageName}.fileprovider", file)
    } else {
        file.toUri()
    }

fun Context.getFileFromUri(uri: Uri): File? {
    Timber.d(uri.toString())
    val authority = uri.authority
    val scheme = uri.scheme
    val path = uri.path
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && path != null) {
        val externals = arrayOf("/external/", "/external_path/")
        for (external in externals) {
            if (path.startsWith(external)) {
                val f =
                    File(
                        Environment.getExternalStorageDirectory().absolutePath +
                            path.replace(external, "/"),
                    )
                if (f.exists()) {
                    Timber.d("$uri -> $external")
                    return f
                }
            }
        }
        val file =
            if (path.startsWith("/files_path/")) {
                File(filesDir.absolutePath + path.replace("/files_path/", "/"))
            } else if (path.startsWith("/cache_path/")) {
                File(cacheDir.absolutePath + path.replace("/cache_path/", "/"))
            } else if (path.startsWith("/external_files_path/")) {
                File(getExternalFilesDir(null)!!.absolutePath + path.replace("/external_files_path/", "/"))
            } else if (path.startsWith("/external_cache_path/")) {
                File(externalCacheDir!!.absolutePath + path.replace("/external_cache_path/", "/"))
            } else {
                null
            }
        if (file != null && file.exists()) {
            Timber.d("$uri -> $path")
            return file
        }
    }
    return if (ContentResolver.SCHEME_FILE == scheme) {
        if (path != null) return File(path)
        Timber.d("$uri parse failed. -> 0")
        null // end 0
    } else if (DocumentsContract.isDocumentUri(this, uri)) {
        if ("com.android.externalstorage.documents" == authority) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                return File(Environment.getExternalStorageDirectory().toString() + "/" + split[1])
            } else {
                // Below logic is how External Storage provider build URI for documents
                // http://stackoverflow.com/questions/28605278/android-5-sd-card-label
                val mStorageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                runCatching {
                    val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
                    val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
                    val getUuid = storageVolumeClazz.getMethod("getUuid")
                    val getState = storageVolumeClazz.getMethod("getState")
                    val getPath = storageVolumeClazz.getMethod("getPath")
                    val isPrimary = storageVolumeClazz.getMethod("isPrimary")
                    val isEmulated = storageVolumeClazz.getMethod("isEmulated")
                    val result = getVolumeList.invoke(mStorageManager)
                    val length = result?.let { Array.getLength(it) } ?: 0
                    for (i in 0 until length) {
                        val storageVolumeElement = Array.get(result, i)
                        val mounted =
                            Environment.MEDIA_MOUNTED ==
                                getState.invoke(
                                    storageVolumeElement,
                                ) ||
                                Environment.MEDIA_MOUNTED_READ_ONLY == getState.invoke(storageVolumeElement)

                        // if the media is not mounted, we need not get the volume details
                        if (!mounted) continue

                        // Primary storage is already handled.
                        if (isPrimary.invoke(storageVolumeElement) as Boolean &&
                            isEmulated.invoke(storageVolumeElement) as Boolean
                        ) {
                            continue
                        }
                        val uuid = getUuid.invoke(storageVolumeElement) as? String
                        if (uuid != null && uuid == type) {
                            return File(getPath.invoke(storageVolumeElement).toString() + "/" + split[1])
                        }
                    }
                }.getOrElse {
                    Timber.d("$uri parse failed. $it -> 1_0")
                }
            }
            Timber.d("$uri parse failed. -> 1_0")
            null // end 1_0
        } else if ("com.android.providers.downloads.documents" == authority) {
            var id = DocumentsContract.getDocumentId(uri)
            if (id.isEmpty()) {
                Timber.d("$uri parse failed(id is null). -> 1_1")
                return null
            }
            if (id.startsWith("raw:")) {
                return File(id.substring(4))
            } else if (id.startsWith("msf:")) {
                id = id.split(":")[1]
            }
            val availableId: Long =
                try {
                    id.toLong()
                } catch (e: Exception) {
                    return null
                }
            val contentUriPrefixesToTry =
                arrayOf(
                    "content://downloads/public_downloads",
                    "content://downloads/all_downloads",
                    "content://downloads/my_downloads",
                )
            for (contentUriPrefix in contentUriPrefixesToTry) {
                val contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), availableId)
                try {
                    val file = getFileFromUri(contentUri, "1_1")
                    if (file != null) {
                        return file
                    }
                } catch (ignore: Exception) {
                }
            }
            Timber.d("$uri parse failed. -> 1_1")
            null // end 1_1
        } else if ("com.android.providers.media.documents" == authority) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val contentUri: Uri =
                when (type) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> {
                        Timber.d("$uri parse failed. -> 1_2")
                        return null
                    }
                }
            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])
            getFileFromUri(contentUri, "1_2", selection, selectionArgs) // end 1_2
        } else if (ContentResolver.SCHEME_CONTENT == scheme) {
            getFileFromUri(uri, code = "1_3") // end 1_3
        } else {
            Timber.d("$uri parse failed. -> 1_4")
            null // end 1_4
        } // end 1
    } else if (ContentResolver.SCHEME_CONTENT == scheme) {
        getFileFromUri(uri, "2") // end 2
    } else {
        Timber.d("$uri parse failed. -> 3")
        null
    } // end 3
}

private fun Context.getFileFromUri(
    uri: Uri,
    code: String,
    selection: String? = null,
    selectionArgs: kotlin.Array<String>? = null,
): File? {
    when (uri.authority) {
        "com.google.android.apps.photos.content" -> {
            val path = uri.lastPathSegment
            if (!path.isNullOrEmpty()) {
                return File(path)
            }
        }
        "com.tencent.mtt.fileprovider" -> {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val fileDir = Environment.getExternalStorageDirectory()
                return File(fileDir, path.substring("/QQBrowser".length, path.length))
            }
        }
        "com.huawei.hidisk.fileprovider" -> {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                return File(path.replace("/root", ""))
            }
        }
    }

    contentResolver.query(uri, arrayOf("_data"), selection, selectionArgs, null).use {
        if (it == null) {
            Timber.d("$uri parse failed(cursor is null). -> $code")
            return null
        }
        return runCatching {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex("_data")
                if (columnIndex > -1) {
                    File(it.getString(columnIndex))
                } else {
                    Timber.d("$uri parse failed(columnIndex: $columnIndex is wrong). -> $code")
                    null
                }
            } else {
                Timber.d("$uri parse failed(moveToFirst return false). -> $code")
                null
            }
        }.getOrElse {
            Timber.d("$uri parse failed. -> $code")
            null
        }
    }
}
