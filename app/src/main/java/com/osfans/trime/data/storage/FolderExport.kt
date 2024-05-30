package com.osfans.trime.data.storage

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.util.appContext
import timber.log.Timber
import java.io.File

class FolderExport(private val context: Context, private val docUriStr: String) {
    suspend fun exportDir(dirPath: File) {
        DocumentFile.fromTreeUri(appContext, docUriStr.toUri())?.runCatching {
            val dirDoc = this.findFile(dirPath.name)?.takeIf { it.isDirectory && it.canWrite() } ?: this.createDirectory(dirPath.name)

            dirDoc?.let {
                recursiveExport(dirPath, dirDoc)
            } ?: run {
                Timber.e("Error, cannot create export folder, %s", dirPath.name)
            }
        }?.onFailure {
            Timber.e(it)
        }
    }

    suspend fun exportModifiedFiles(fileNames: Array<File>) {
        DocumentFile.fromTreeUri(appContext, docUriStr.toUri())?.runCatching {
            fileNames.forEach { fileName ->
                export(fileName, this)
            }
        }
    }

    private suspend fun recursiveExport(
        sourcePath: File,
        targetDir: DocumentFile,
    ) {
        sourcePath.listFiles()?.forEach { file ->
            if (file.isFile) {
                export(file, targetDir)
            } else if (file.isDirectory) {
                targetDir.runCatching {
                    val docFile =
                        this.findFile(file.name)?.takeIf { it.isDirectory && it.canWrite() }
                            ?: this.createDirectory(file.name)

                    docFile?.let {
                        recursiveExport(file, it)
                    }
                }.onFailure {
                    Timber.e(it)
                }
            }
        }
    }

    private suspend fun export(
        fileName: File,
        targetDir: DocumentFile,
    ) {
        runCatching {
            val docFile =
                targetDir.findFile(fileName.name)?.takeIf { it.isFile && it.canWrite() }
                    ?: targetDir.createFile("*/*", fileName.name)
            docFile?.let { doc ->
                copyToUri(fileName, doc)
            } ?: run {
                Timber.e("Cannot export file: %s", fileName.name)
            }
        }.onFailure {
            Timber.e(it, "Uri Error")
        }
    }

    private fun copyToUri(
        sourceFile: File,
        targetDoc: DocumentFile,
    ) {
        val oss = context.contentResolver.openOutputStream(targetDoc.uri, "wt")
        oss?.use {
            sourceFile.inputStream().apply {
                copyTo(oss)

                close()
            }
        }
    }

    companion object {
        suspend fun exportModifiedFiles() {
            val userDirUri = AppPrefs.defaultInstance().profile.userDataDir

            val f1 = File(AppPrefs.Profile.getAppUserDir(), "default.custom.yaml")
            val f2 = File(AppPrefs.Profile.getAppUserDir(), "user.yaml")

            FolderExport(appContext, userDirUri).exportModifiedFiles(arrayOf(f1, f2))
        }

        suspend fun exportSyncDir() {
            val userDirUri = AppPrefs.defaultInstance().profile.userDataDir

            val dir = "sync"
            val dirFile = File(AppPrefs.Profile.getAppUserDir(), dir)

            FolderExport(appContext, userDirUri).exportDir(dirFile)
        }
    }
}
