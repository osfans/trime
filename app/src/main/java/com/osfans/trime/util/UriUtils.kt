package com.osfans.trime.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import timber.log.Timber
import java.io.File
import java.lang.reflect.Array

/**
 * Utils for dealing with Storage Access Framework URIs.
 */
object UriUtils {

    private const val HOME_VOLUME_NAME = "home"
    private const val DOWNLOADS_VOLUME_NAME = "downloads"
    private const val PRIMARY_VOLUME_NAME = "primary"

    @JvmStatic
    fun Uri?.toFile(): File? {
        this ?: return null

        // Determine volume id, e.g. "home", "downloads", ...
        val docId = DocumentsContract.getDocumentId(this)
        val docIdSplit = docId.split(':')
        val volumeId = if (docIdSplit.isNotEmpty()) docIdSplit[0] else return null
        Timber.d("docId: $docId, volumeId: $volumeId")

        // Handle Uri referring to internal or external storage
        val volumePath = runCatching {
            when (volumeId) {
                HOME_VOLUME_NAME -> {
                    Timber.v("Volume path: isHomeVolume")
                    @Suppress("DEPRECATION")
                    // Reading the environment var avoids hard coding the case of the "documents" folder.
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                }
                DOWNLOADS_VOLUME_NAME -> {
                    Timber.v("Volume path: isDownloadsVolume")
                    @Suppress("DEPRECATION")
                    // Reading the environment var avoids hard coding the case of the "downloads" folder.
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                }
                else -> {
                    val sm = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                    val storageVolume = Class.forName("android.os.storage.StorageVolume")
                    val getVolumeList = sm::class.java.getMethod("getVolumeList")
                    val getUuid = storageVolume.getMethod("getUuid")
                    val getPath = storageVolume.getMethod("getPath")
                    val isPrimary = storageVolume.getMethod("isPrimary")

                    val result = getVolumeList.invoke(sm)
                    val resultSize = Array.getLength(result!!)

                    var final: String? = null
                    for (i in 0 until resultSize) {
                        val e = Array.get(result, i)
                        val uuid = getUuid.invoke(e) as? String
                        val primary = isPrimary.invoke(e) as Boolean
                        val isPrimaryVolume = primary && PRIMARY_VOLUME_NAME == volumeId
                        val isExternalVolume = uuid == volumeId

                        Timber.d(
                            "Found volume: UUID = $uuid, " +
                                "volumeId = $volumeId, " +
                                "primary = $primary, " +
                                "isPrimaryVolume = $isPrimaryVolume, " +
                                "isExternalVolume = $isExternalVolume"
                        )

                        if (isPrimaryVolume || isExternalVolume) {
                            Timber.v("Volume path: isPrimaryVolume OR isExternalVolume")
                            // Return path if the correct volume corresponding to volumeId was found
                            final = getPath.invoke(e) as String
                            break
                        }
                    }
                    final ?: return File(File.separator)
                }
            }
        }.mapCatching {
            if (it.endsWith(File.separator))
                it.substringBeforeLast(File.separator)
            else it
        }.getOrElse {
            Timber.w(it, "volumePath: EXCEPTION on parsing!")
            Timber.e("volumePath: parse failed, volumeId = $volumeId")
            return File(File.separator)
        }

        val docTreeId = DocumentsContract.getTreeDocumentId(this)
        val docTreeIdSplit = docTreeId.split(':')
        val documentPath = if (docTreeIdSplit.size >= 2) {
            docTreeIdSplit[1].let {
                if (it.endsWith(File.separator))
                    it.substringBeforeLast(File.separator)
                else it
            }
        } else {
            ""
        }
        Timber.d("docTreeId: $docTreeId, documentPath: $documentPath")

        return if (documentPath.isNotEmpty()) {
            if (documentPath.startsWith(File.separator)) {
                File("$volumePath$documentPath")
            } else {
                File("$volumePath${File.separator}$documentPath")
            }
        } else {
            File(volumePath)
        }
    }

    @JvmStatic
    fun File?.toUri(): Uri? {
        return runCatching {
            val externalFileDirs = appContext.getExternalFilesDirs(null)
                .filter { it != appContext.getExternalFilesDir(null) }
            if (externalFileDirs.isEmpty()) {
                Timber.w("Could not determine app's private files directory on external storage")
                return null
            }

            val absPath = externalFileDirs[0].absolutePath
            val segments = absPath.split('/')
            if (segments.size < 2) {
                Timber.w("Could not extract volumeId from app's private files path '$absPath'")
                return null
            }

            val volumeId = segments[2]
            return Uri.parse("content://com.android.externalstorage.documents/document/$volumeId%3A/")
        }.getOrDefault(null)
    }
}
