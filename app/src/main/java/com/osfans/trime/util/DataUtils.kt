package com.osfans.trime.util

import com.osfans.trime.BuildConfig
import com.osfans.trime.Rime
import com.osfans.trime.ime.core.Preferences
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception

object DataUtils {
    private val prefs get() = Preferences.defaultInstance()

    @JvmStatic
    val sharedDataDir: String get() = prefs.conf.sharedDataDir

    @JvmStatic
    val userDataDir: String get() = prefs.conf.userDataDir

    @JvmStatic
    fun getAssetsDir(childdir: String = ""): String {
        val path = File(sharedDataDir, childdir).path
        return if (File(path).exists()) {
            path
        } else {
            File(userDataDir, childdir).path
        }
    }

    fun copyFile(file: File, outputPath: String): Boolean {
        if (file.exists()) {
            try {
                val input: InputStream = FileInputStream(file)
                val outputStream: OutputStream = FileOutputStream(File(outputPath))
                val buffer = ByteArray(1024)
                var len: Int
                while (input.read(buffer).also { len = it } > 0) {
                    outputStream.write(buffer, 0, len)
                }
                Timber.i("copyFile = ${file.absolutePath}, $outputPath")
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    fun backupPref(): Boolean {
        val file = File(
            "/data/data/APPLICATION_ID/shared_prefs/APPLICATION_ID_preferences.xml"
                .replace("APPLICATION_ID", BuildConfig.APPLICATION_ID)
        )

        return copyFile(
            file,
            Rime.get_sync_dir() + File.separator + Rime.get_user_id() + File.separator + file.name
        )
    }

    fun recoverPref(): Boolean {
        val file = File(Rime.get_sync_dir() + File.separator + Rime.get_user_id() + File.separator + "recover.xml")

        if (copyFile(
                file,
                "/data/data/APPLICATION_ID/shared_prefs/APPLICATION_ID_preferences.xml"
                    .replace("APPLICATION_ID", BuildConfig.APPLICATION_ID)
            )
        ) {
            return file.renameTo(File(file.parent, "recovered.xml"))
        }
        return false
    }
}
