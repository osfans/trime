package com.osfans.trime.data.storage

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.osfans.trime.data.AppPrefs
import timber.log.Timber
import java.io.File

class FolderSync(private val context: Context, private val docUriStr: String) {
    private val sourceFiles = mutableSetOf<String>()

    suspend fun copyFiles(
        fileNames: Array<String>,
        appSpecificPath: String,
    ) {
        DocumentFile.fromTreeUri(context, docUriStr.toUri())?.runCatching {
            fileNames.forEach { name ->
                val docFile = this.findFile(name)?.takeIf { it.isFile }
                docFile?.let {
                    val file = File(appSpecificPath, name)
                    copyToFile(it, file)
                }
            }
        }?.onFailure {
            Timber.e(it, "Uri Error")
        }
    }

    suspend fun copyAll(appSpecificPath: String) {
        runCatching {
            DocumentFile.fromTreeUri(context, docUriStr.toUri())?.let { tree ->
                recursivelyCopy(tree, File(appSpecificPath))
                recursiveDeleteFiles(File(appSpecificPath))
            }
        }.onFailure {
            Timber.e(it, "Uri (%s) Error", docUriStr)
        }
    }

    // IO Operation, should call in background threads
    private fun recursivelyCopy(
        documentTree: DocumentFile,
        appSpecificPath: File,
    ) {
        documentTree.let { tree ->
            if (!appSpecificPath.exists()) {
                appSpecificPath.mkdir()
            }
            tree.listFiles().forEach { doc ->
                if (doc.isFile) {
                    doc.name?.let { name ->
                        val file = File(appSpecificPath, name)
                        sourceFiles.add(file.absolutePath)

                        if (shouldCopyToFile(doc, file)) {
                            copyToFile(doc, file)
                        }
                    }
                } else if (doc.isDirectory) {
                    doc.name?.takeIf { it != "build" && !it.contains("userdb") }?.let {
                        val file = File(appSpecificPath, it)
                        sourceFiles.add(file.absolutePath)
                        recursivelyCopy(doc, file)
                    }
                }
            }
        }
    }

    private fun shouldCopyToFile(
        sourceDoc: DocumentFile,
        targetFile: File,
    ): Boolean {
        return !targetFile.exists() || sourceDoc.length() != targetFile.length()
    }

    private fun copyToFile(
        sourceDoc: DocumentFile,
        targetFile: File,
    ) {
        val iss = context.contentResolver.openInputStream(sourceDoc.uri)
        iss?.use {
            targetFile.outputStream().apply {
                it.copyTo(this)
                close()
            }
//            Timber.d("Copied : ${file.absolutePath}")
        }
    }

    private fun recursiveDeleteFiles(path: File) {
        path.listFiles()?.forEachIndexed { _, file ->
            if (file.isFile) {
                if (!sourceFiles.contains(file.absolutePath)) {
                    file.delete()
                }
            } else if (file.isDirectory) {
                if (!file.name.contains("userdb") && file.name != "build") {
                    recursiveDeleteFiles(file)
                    if (!sourceFiles.contains(file.absolutePath)) {
                        file.delete()
                    }
                }
            }
        }
    }

    companion object {
        suspend fun copyDir(context: Context) {
            val userDirUri = AppPrefs.defaultInstance().profile.userDataDir
            val shareDirUri = AppPrefs.defaultInstance().profile.sharedDataDir

            FolderSync(context, userDirUri).copyAll((AppPrefs.Profile.getAppUserDir()))

            if (shareDirUri != userDirUri && shareDirUri.isNotBlank()) {
                FolderSync(context, shareDirUri).copyAll((AppPrefs.Profile.getAppShareDir()))
            }
        }
    }
}
