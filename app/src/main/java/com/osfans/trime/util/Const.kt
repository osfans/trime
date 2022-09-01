package com.osfans.trime.util

import com.osfans.trime.BuildConfig

object Const {
    const val buildGitHash = BuildConfig.BUILD_GIT_HASH
    const val displayVersionName = "${BuildConfig.BUILD_VERSION_NAME}-${BuildConfig.BUILD_TYPE}"
    const val originalGitRepo = "https://github.com/osfans/trime"
    const val currentGitRepo = BuildConfig.BUILD_GIT_REPO
}
