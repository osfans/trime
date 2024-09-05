// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.opencc.dict

import com.osfans.trime.data.opencc.OpenCCDictManager
import java.io.File

class OpenCCDictionary(
    file: File,
) : Dictionary() {
    override var file: File = file
        private set

    override val type: Type =
        if (file.extension == NEW_FORMAT) {
            Type.OCD2
        } else {
            Type.OCD
        }

    init {
        ensureFileExists()
        if (file.extension != type.ext) {
            throw IllegalArgumentException("Not a OpenCC dict ${file.name}")
        }
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
        const val NEW_FORMAT = "ocd2"
        const val OLD_FORMAT = "ocd"
    }
}
