package com.osfans.trime.ime.enums

enum class FullscreenMode {
    AUTO_SHOW,
    ALWAYS_SHOW,
    NEVER_SHOW,
    ;

    companion object {
        fun fromString(mode: String): FullscreenMode {
            return runCatching {
                valueOf(mode.uppercase())
            }.getOrDefault(AUTO_SHOW)
        }
    }
}
