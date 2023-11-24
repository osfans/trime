package com.osfans.trime.ime.landscapeinput

import java.util.Locale
import kotlin.collections.HashMap

enum class LandscapeInputUIMode {
    AUTO_SHOW,
    ALWAYS_SHOW,
    NEVER_SHOW,
    ;

    companion object {
        private val convertMap: HashMap<String, LandscapeInputUIMode> =
            hashMapOf(
                Pair("AUTO_SHOW", AUTO_SHOW),
                Pair("ALWAYS_SHOW", ALWAYS_SHOW),
                Pair("NEVER_SHOW", NEVER_SHOW),
            )

        fun fromString(mode: String): LandscapeInputUIMode {
            val type = convertMap[mode.uppercase(Locale.getDefault())]
            return type ?: AUTO_SHOW
        }
    }
}
