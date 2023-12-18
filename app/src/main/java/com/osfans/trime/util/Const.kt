package com.osfans.trime.util

import com.osfans.trime.BuildConfig

object Const {
    val builder = BuildConfig.BUILDER
    val buildTimestamp = BuildConfig.BUILD_TIMESTAMP
    val buildCommitHash = BuildConfig.BUILD_COMMIT_HASH
    val displayVersionName = "${BuildConfig.BUILD_VERSION_NAME}-${BuildConfig.BUILD_TYPE}"
    val originalGitRepo = "https://github.com/osfans/trime"
    val currentGitRepo = BuildConfig.BUILD_GIT_REPO
}
