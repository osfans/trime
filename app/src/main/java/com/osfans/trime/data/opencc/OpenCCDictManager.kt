package com.osfans.trime.data.opencc

import com.osfans.trime.data.DataManager
import com.osfans.trime.data.opencc.dict.Dictionary
import com.osfans.trime.data.opencc.dict.OpenCCDictionary
import com.osfans.trime.data.opencc.dict.TextDictionary
import com.osfans.trime.util.appContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import kotlin.system.measureTimeMillis

object OpenCCDictManager {
    init {
        System.loadLibrary("rime_jni")
    }

    private val openccDictDir = File(
        DataManager.getDataDir("opencc")
    ).also { it.mkdirs() }

    fun dictionaries(): List<Dictionary> = openccDictDir
        .listFiles()
        ?.mapNotNull { Dictionary.new(it) }
        ?.toList() ?: listOf()

    fun openccDictionaries(): List<OpenCCDictionary> =
        dictionaries().mapNotNull { it as? OpenCCDictionary }

    fun importFromFile(file: File): OpenCCDictionary {
        val raw = Dictionary.new(file)
            ?: throw IllegalArgumentException("${file.path} is not a opencc/text dictionary")
        // convert to opencc format in dictionaries dir
        // preserve original file name
        val new = raw.toOpenCCDictionary(
            File(
                openccDictDir,
                file.nameWithoutExtension + ".${Dictionary.Type.OPENCC.ext}"
            )
        )
        Timber.d("Converted $raw to $new")
        return new
    }

    /**
     * Convert internal text dict to opencc format
     */
    @JvmStatic
    fun internalDeploy() {
        for (d in dictionaries()) {
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
    external fun openccDictConv(src: String, dest: String, mode: Boolean)

    const val MODE_BIN_TO_TXT = true // OCD2 to TXT
    const val MODE_TXT_TO_BIN = false // TXT to OCD2
}
