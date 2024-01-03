package com.osfans.trime.ime.landscapeinput

import java.util.Locale

enum class LandscapeInputUIMode {
    AUTO_SHOW,
    ALWAYS_SHOW,
    NEVER_SHOW,
    ;

    companion object {
        private val convertMap: Map<String, LandscapeInputUIMode> = LandscapeInputUIMode.values().associateBy { it.name }

        fun fromString(mode: String): LandscapeInputUIMode {
            val type = convertMap[mode.uppercase(Locale.getDefault())]
            return type ?: AUTO_SHOW
        }
    }
}
