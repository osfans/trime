package com.osfans.trime.ime.keyboard

import com.blankj.utilcode.util.ScreenUtils
import com.osfans.trime.data.AppPrefs

class KeyboardPrefs {
    private val prefs = AppPrefs.defaultInstance()

    fun isLandscapeMode(): Boolean {
        return when (prefs.keyboard.splitOption) {
            SPLIT_OPTION_AUTO -> isWideScreen()
            SPLIT_OPTION_LANDSCAPE -> ScreenUtils.isLandscape()
            SPLIT_OPTION_ALWAYS -> true
            else -> false
        }
    }

    private fun isWideScreen(): Boolean {
        return ScreenUtils.getAppScreenWidth() / ScreenUtils.getScreenDensity() > WIDE_SCREEN_WIDTH_DP
    }

    companion object {
        const val SPLIT_OPTION_AUTO = "auto"
        const val SPLIT_OPTION_LANDSCAPE = "landscape"
        const val SPLIT_OPTION_NEVER = "never"
        const val SPLIT_OPTION_ALWAYS = "always"

        private const val WIDE_SCREEN_WIDTH_DP = 600
    }
}
