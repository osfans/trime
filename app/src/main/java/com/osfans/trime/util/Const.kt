package com.osfans.trime.util

import com.osfans.trime.BuildConfig

object Const {
    val buildGitHash = BuildConfig.BUILD_GIT_HASH
    val displayVersionName = "${BuildConfig.BUILD_VERSION_NAME}-${BuildConfig.BUILD_TYPE}"
    val originalGitRepo = "https://github.com/osfans/trime"
    val currentGitRepo = BuildConfig.BUILD_GIT_REPO
}
