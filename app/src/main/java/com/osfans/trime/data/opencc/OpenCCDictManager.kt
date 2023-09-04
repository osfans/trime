package com.osfans.trime.data.opencc

import com.osfans.trime.data.DataDirectoryChangeListener
import com.osfans.trime.data.DataManager
import com.osfans.trime.data.opencc.dict.Dictionary
import com.osfans.trime.data.opencc.dict.OpenCCDictionary
import com.osfans.trime.data.opencc.dict.TextDictionary
import com.osfans.trime.util.appContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import kotlin.system.measureTimeMillis

object OpenCCDictManager : DataDirectoryChangeListener.Listener {
    init {
        System.loadLibrary("rime_jni")
        // register listener
        DataDirectoryChangeListener.addDirectoryChangeListener(this)
    }

    var sharedDir = File(DataManager.sharedDataDir, "opencc").also { it.mkdirs() }
    var userDir = File(DataManager.userDataDir, "opencc").also { it.mkdirs() }

    /**
     * Update sharedDir and userDir.
     */
    override fun onDataDirectoryChange() {
        sharedDir = File(DataManager.sharedDataDir, "opencc").also { it.mkdirs() }
        userDir = File(DataManager.userDataDir, "opencc").also { it.mkdirs() }
    }

    fun sharedDictionaries(): List<Dictionary> = sharedDir
        .listFiles()
        ?.mapNotNull { Dictionary.new(it) } ?: listOf()

    fun userDictionaries(): List<Dictionary> = userDir
        .listFiles()
        ?.mapNotNull { Dictionary.new(it) } ?: listOf()

    fun getAllDictionaries(): List<Dictionary> =
        if (sharedDir.path == userDir.path) {
            userDictionaries()
        } else {
            (sharedDictionaries() + userDictionaries())
        }

    fun openCCDictionaries(): List<OpenCCDictionary> =
        getAllDictionaries().mapNotNull { it as? OpenCCDictionary }

    fun importFromFile(file: File): OpenCCDictionary {
        val raw = Dictionary.new(file)
            ?: throw IllegalArgumentException("${file.path} is not a opencc/text dictionary")
        // convert to opencc format in dictionaries dir
        // preserve original file name
        val new = raw.toOpenCCDictionary(
            File(
                userDir,
                file.nameWithoutExtension + ".${Dictionary.Type.OPENCC.ext}",
            ),
        )
        Timber.d("Converted $raw to $new")
        return new
    }

    /**
     * Convert internal text dict to opencc format
     */
    @JvmStatic
    fun buildOpenCCDict() {
        for (d in getAllDictionaries()) {
            if (d is TextDictionary) {
                val result: OpenCCDictionary
                measureTimeMillis {
                    result = d.toOpenCCDictionary()
                }.also { Timber.d("Took $it to convert to $result") }
            }
        }
    }

    fun importFromInputStream(stream: InputStream, name: String): OpenCCDictionary {
        val tempFile = File(appContext.cacheDir, name)
        tempFile.outputStream().use {
            stream.copyTo(it)
        }
        val new = importFromFile(tempFile)
        tempFile.delete()
        return new
    }

    @JvmStatic
    fun convertLine(input: String, configFileName: String): String {
        if (configFileName.isEmpty()) return input
        with(File(userDir, configFileName)) {
            if (exists()) return openCCLineConv(input, path)
        }
        with(File(sharedDir, configFileName)) {
            if (exists()) return openCCLineConv(input, path)
        }
        Timber.w("Specified config $configFileName doesn't exist, returning raw input ...")
        return input
    }

    @JvmStatic
    external fun openCCDictConv(src: String, dest: String, mode: Boolean)

    @JvmStatic
    external fun openCCLineConv(input: String, configFileName: String): String

    @JvmStatic
    external fun getOpenCCVersion(): String

    const val MODE_BIN_TO_TXT = true // OCD2 to TXT
    const val MODE_TXT_TO_BIN = false // TXT to OCD2
}
