package com.osfans.trime.data.opencc.dict

import com.osfans.trime.data.opencc.OpenCCDictManager
import java.io.File

class OpenCCDictionary(file: File) : Dictionary() {
    override var file: File = file
        private set

    var isOCD2: Boolean = true
        private set

    override val type: Type = Type.OPENCC

    override val name: String
        get() =
            if (isOCD2) {
                super.name
            } else {
                file.name.substringBefore("")
            }

    init {
        ensureFileExists()
        isOCD2 =
            when {
                file.extension == type.ext -> {
                    true
                }
                file.name.endsWith(".$OLD_FORMAT") -> {
                    false
                }
                else -> throw IllegalArgumentException("Not a libime dict ${file.name}")
            }
    }

    fun useOCD2() {
        if (isOCD2) {
            return
        }
        val newFile = file.resolveSibling(name + ".${type.ext}")
        file.renameTo(newFile)
        file = newFile
        isOCD2 = true
    }

    fun useOCD() {
        if (!isOCD2) {
            return
        }
        val newFile = file.resolveSibling(name + ".$OLD_FORMAT")
        file.renameTo(newFile)
        file = newFile
        isOCD2 = false
    }

    override fun toTextDictionary(dest: File): TextDictionary {
        ensureTxt(dest)
        OpenCCDictManager.openCCDictConv(
            file.absolutePath,
            dest.absolutePath,
            OpenCCDictManager.MODE_BIN_TO_TXT,
        )
        return TextDictionary(dest)
    }

    override fun toOpenCCDictionary(dest: File): OpenCCDictionary {
        ensureBin(dest)
        file.copyTo(dest)
        return OpenCCDictionary(dest)
    }

    companion object {
        const val OLD_FORMAT = "ocd"
    }
}
