package com.osfans.trime.data.theme

import android.graphics.Typeface
import com.osfans.trime.data.DataManager
import java.io.File

object FontManager {
    val fontDir = File(DataManager.userDataDir, "fonts")

    @JvmStatic
    fun getTypeface(fontFileName: String): Typeface {
        val f = File(fontDir, fontFileName)
        return if (f.exists()) {
            Typeface.createFromFile(f)
        } else {
            Typeface.DEFAULT
        }
    }
}
