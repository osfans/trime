package com.osfans.trime.ime.landscapeinput

enum class LandscapeInputUIMode {
    AUTO_SHOW,
    ALWAYS_SHOW,
    NEVER_SHOW;

    companion object {
        fun fromString(string: String): LandscapeInputUIMode {
            return valueOf(string.uppercase())
        }
    }
}
