/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.userdict

import com.osfans.trime.util.appContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object UserDictManager {
    fun restoreUserDict(stream: InputStream, snapshotFile: String): Result<Unit> {
        val tempFile = File(appContext.cacheDir, snapshotFile)
        try {
            tempFile.outputStream().use {
                stream.copyTo(it)
            }
            val success = restoreUserDict(tempFile.absolutePath)
            return if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to restore"))
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun importUserDict(stream: InputStream, dictName: String, textFile: String): Result<Int> {
        val tempFile = File(appContext.cacheDir, textFile)
        try {
            tempFile.outputStream().use {
                stream.copyTo(it)
            }
            val count = importUserDict(dictName, tempFile.absolutePath)
            return if (count >= 0) {
                Result.success(count)
            } else {
                Result.failure(
                    Exception("Failed to import from '$textFile' to '$dictName'"),
                )
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun exportUserDict(dest: OutputStream, dictName: String, textFile: String): Result<Int> {
        val tempFile = File(appContext.cacheDir, textFile)
        try {
            val count = exportUserDict(dictName, tempFile.absolutePath)
            if (count >= 0 && tempFile.exists()) {
                tempFile.inputStream().use {
                    it.copyTo(dest)
                }
                return Result.success(count)
            } else {
                return Result.failure(
                    Exception("Failed to export '$dictName' to '$textFile'"),
                )
            }
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    @JvmStatic
    external fun getUserDictList(): Array<String>

    @JvmStatic
    external fun backupUserDict(dictName: String): Boolean

    @JvmStatic
    external fun restoreUserDict(snapshotFile: String): Boolean

    @JvmStatic
    external fun exportUserDict(dictName: String, textFile: String): Int

    @JvmStatic
    external fun importUserDict(dictName: String, textFile: String): Int
}
