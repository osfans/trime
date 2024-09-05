// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.opencc.dict

import com.osfans.trime.data.opencc.OpenCCDictManager
import java.io.File

class TextDictionary(
    file: File,
) : Dictionary() {
    override var file: File = file
        private set

    override val type: Type = Type.Text

    init {
        ensureFileExists()
        if (file.extension != type.ext) {
            throw IllegalArgumentException("Not a text dict ${file.name}")
        }
    }

    override fun toTextDictionary(dest: File): TextDictionary {
        ensureTxt(dest)
        file.copyTo(dest)
        return TextDictionary(dest)
    }

    override fun toOpenCCDictionary(dest: File): OpenCCDictionary {
        ensureBin(dest)
        OpenCCDictManager.openCCDictConv(
            file.absolutePath,
            dest.absolutePath,
            OpenCCDictManager.MODE_TXT_TO_BIN,
        )
        return OpenCCDictionary(dest)
    }
}
