// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeUnavailableException
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.core.data.DataStorageMode
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

/** Real failed asset installation, restricted to the dedicated audit emulator. */
internal object StartupFailureProbe {
    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        var success = false
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            val context = instrumentation.targetContext
            val marker = File(context.getExternalFilesDir(null), "runtime-audit-dedicated")
            check(marker.isFile) { "Requires an explicitly marked disposable audit installation" }
            runBlocking {
                withTimeout(300_000) {
                    check(RimeDaemon.getFirstSessionOrNull() == null)
                    check(RimeDaemon.engineState.value == RimeLifecycle.State.STOPPED)
                    val shared = DataManager.sharedDataDir
                    val suffix = UUID.randomUUID().toString()
                    val savedShared = File(shared.parentFile, "shared-$suffix")
                    val dataDir = if (Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext().dataDir else File(context.applicationInfo.dataDir)
                    val checksum = File(dataDir, "checksums.json")
                    val savedChecksum = File(checksum.parentFile, "checksums-$suffix.json")
                    val hadChecksum = checksum.exists()
                    check(hadChecksum) { "Missing installed resource descriptor" }
                    val checksumBeforeRepair = checksum.readBytes()
                    val missingAsset = File(shared, "default.yaml")
                    check(missingAsset.isFile)
                    val missingAssetContents = missingAsset.readBytes()
                    check(missingAsset.delete())
                    try {
                        val session = RimeDaemon.createSession("missing-asset-repair-probe")
                        try {
                            session.runOnReady {
                                check(isReady && selectSchema("luna_pinyin_t9"))
                            }
                            check(missingAsset.isFile)
                            check(missingAsset.readBytes().contentEquals(missingAssetContents))
                            check(checksum.readBytes().contentEquals(checksumBeforeRepair))
                        } finally {
                            RimeDaemon.destroySession("missing-asset-repair-probe")
                        }
                    } finally {
                        if (!missingAsset.isFile) missingAsset.writeBytes(missingAssetContents)
                    }
                    RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                    check(shared.renameTo(savedShared))
                    try {
                        if (hadChecksum) check(checksum.renameTo(savedChecksum))
                        check(shared.mkdir())
                        check(File(shared, "default.yaml").mkdir())
                        val session = RimeDaemon.createSession("startup-failure-probe")
                        try {
                            val failure = runCatching { session.runOnReady { error("Unexpected engine ready") } }.exceptionOrNull()
                            check(failure is RimeUnavailableException && failure.cause != null)
                            check(failure.cause?.message?.contains("Destination is not a file") == true)
                            check(RimeDaemon.engineState.value == RimeLifecycle.State.FAILED)
                            check(!checksum.exists()) { "Failed install published checksums" }
                            check(shared.deleteRecursively())
                            check(savedShared.renameTo(shared))
                            if (hadChecksum) check(savedChecksum.renameTo(checksum))
                            RimeDaemon.retryFailedStartup()
                            session.runOnReady {
                                check(isReady && selectSchema("luna_pinyin_t9"))
                                clearComposition()
                                "64426".forEach { check(processKey(it.code)) }
                                check(t9Cached.choices.any { it.spelling == "ni" })
                                clearComposition()
                            }
                        } finally {
                            RimeDaemon.destroySession("startup-failure-probe")
                        }
                    } finally {
                        if (savedShared.exists()) {
                            if (shared.exists()) check(shared.isDirectory && shared.deleteRecursively())
                            check(savedShared.renameTo(shared))
                        }
                        if (savedChecksum.exists()) check(savedChecksum.renameTo(checksum))
                    }
                    RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                    val profile = AppPrefs.defaultInstance().profile
                    val previousMode = profile.dataStorageMode.getValue()
                    val previousTree = profile.externalRimeTreeUri.getValue()
                    try {
                        profile.externalRimeTreeUri.setValue("")
                        profile.dataStorageMode.setValue(DataStorageMode.EXTERNAL_SYNC)
                        val session = RimeDaemon.createSession("external-permission-failure-probe")
                        try {
                            session.runOnReady {
                                check(isReady && usingExistingResources)
                                check(selectSchema("luna_pinyin_t9"))
                                setRuntimeOption("ascii_mode", false)
                                "64".forEach { check(processKey(it.code)) }
                                check(t9Cached.choices.any { it.spelling == "ni" })
                                clearComposition()
                            }
                        } finally {
                            RimeDaemon.destroySession("external-permission-failure-probe")
                        }
                    } finally {
                        profile.dataStorageMode.setValue(previousMode)
                        profile.externalRimeTreeUri.setValue(previousTree)
                        check(profile.externalRimeTreeUri.sharedPreferences.edit().commit())
                    }
                    RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                    val custom = File(DataManager.userDataDir, "default.custom.yaml")
                    val savedCustom = File(custom.parentFile, "default-$suffix.saved")
                    val hadCustom = custom.exists()
                    if (hadCustom) check(custom.renameTo(savedCustom))
                    try {
                        custom.writeText("patch:\n  schema_list:\n    - schema: __missing_$suffix\n")
                        // Startup modification detection uses seconds; FAT can also round
                        // writes to the same tick as the preceding successful deployment.
                        check(custom.setLastModified(System.currentTimeMillis() + 2000))
                        val session = RimeDaemon.createSession("config-failure-probe")
                        try {
                            val failure = runCatching { session.runOnReady { error("Unexpected engine ready") } }.exceptionOrNull()
                            check(failure is RimeUnavailableException) { "Expected configuration startup failure, got $failure" }
                            check(failure.cause?.message?.startsWith("Rime configuration deployment failed") == true)
                            check(RimeDaemon.engineState.value == RimeLifecycle.State.FAILED)
                            check(custom.delete())
                            if (hadCustom) check(savedCustom.renameTo(custom))
                            RimeDaemon.retryFailedStartup()
                            session.runOnReady {
                                check(isReady)
                                val schemas = selectedSchemata().map { it.id }
                                check("luna_pinyin_t9" in schemas && schemas.none { it.startsWith("__missing_") }) {
                                    "Retry retained the failed deployment's schema list: $schemas"
                                }
                                check(selectSchema("luna_pinyin_t9"))
                            }

                            val themeId = "__config_failure_$suffix"
                            val theme = File(DataManager.userDataDir, "$themeId.yaml")
                            val compiledTheme = File(DataManager.stagingDir, "$themeId.yaml")
                            try {
                                File(DataManager.sharedDataDir, "trime.yaml").copyTo(theme)
                                check(!compiledTheme.exists())
                                check(ThemeLoader.loadTheme(themeId) is ThemeLoader.ThemeLoadResult.Success)
                                check(!compiledTheme.exists()) { "Supported source theme unexpectedly required deployment" }
                                session.runOnReady { check(deployConfigFile(themeId, ThemeLoader.CONFIG_VERSION_KEY)) }
                                check(compiledTheme.isFile)
                                val nextTimestamp = theme.lastModified() + 2000
                                theme.writeText("config_version: 'broken'\n__include: __missing_$suffix:/\n")
                                check(theme.setLastModified(nextTimestamp))
                                val failedTheme = ThemeLoader.loadTheme(themeId)
                                check(failedTheme is ThemeLoader.ThemeLoadResult.Failure && failedTheme.error is ThemeLoader.ThemeLoadError.DeploymentFailure)
                            } finally {
                                theme.delete()
                                compiledTheme.delete()
                            }
                            val invalidPath = "__jni_${suffix}_" + "x".repeat(300)
                            val nativeFailure = ThemeLoader.loadTheme(invalidPath)
                            check(
                                nativeFailure is ThemeLoader.ThemeLoadResult.Failure &&
                                    nativeFailure.error is ThemeLoader.ThemeLoadError.DeploymentFailure,
                            ) { "Overlong path did not return a managed deployment failure: $nativeFailure" }
                            session.runOnReady { check(isReady) }
                        } finally {
                            RimeDaemon.destroySession("config-failure-probe")
                        }
                    } finally {
                        if (savedCustom.exists()) {
                            custom.delete()
                            check(savedCustom.renameTo(custom))
                        } else if (!hadCustom) {
                            custom.delete()
                        }
                    }
                    RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                    if (hadCustom) check(custom.renameTo(savedCustom))
                    try {
                        custom.writeText("patch: [unterminated\n")
                        check(custom.setLastModified(System.currentTimeMillis() + 2000))
                        val session = RimeDaemon.createSession("malformed-optional-probe")
                        try {
                            session.runOnReady {
                                check(isReady && usingExistingResources) { "Existing valid dictionary was not reused after failed deployment" }
                                clearComposition()
                                setRuntimeOption("ascii_mode", false)
                                "64".forEach { check(processKey(it.code)) }
                                check(t9Cached.choices.any { it.spelling == "ni" })
                                clearComposition()
                            }
                            check(custom.delete())
                            if (hadCustom) check(savedCustom.renameTo(custom))
                            session.runOnReady {
                                updateConfig()
                                check(isReady && !usingExistingResources && selectSchema("luna_pinyin_t9"))
                            }
                        } finally {
                            RimeDaemon.destroySession("malformed-optional-probe")
                        }
                    } finally {
                        if (savedCustom.exists()) {
                            custom.delete()
                            check(savedCustom.renameTo(custom))
                        } else if (!hadCustom) {
                            custom.delete()
                        }
                    }
                    RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                    if (hadCustom) check(custom.renameTo(savedCustom))
                    val privateEditor = 0x7052_4956_4154L
                    try {
                        val session = RimeDaemon.createSession("private-restart-failure-probe")
                        try {
                            session.runOnReady {
                                check(isReady && selectSchema("luna_pinyin_t9"))
                                setRuntimeOption("ascii_mode", false)
                                beginEditor(privateEditor, nullInputType = false, privacyMode = true)
                                clearComposition()
                                "64".forEach { check(processKey(it.code)) }
                                check(t9Cached.input == "64") {
                                    "Private restart fixture did not populate transient snapshots"
                                }
                            }
                            custom.writeText("patch:\n  schema_list:\n    - schema: __missing_private_$suffix\n")
                            check(custom.setLastModified(System.currentTimeMillis() + 2000))
                            val failure =
                                runCatching {
                                    session.runOnReady { updateConfig() }
                                }.exceptionOrNull()
                            check(failure is RimeUnavailableException) {
                                "Expected private in-session restart failure, got $failure"
                            }
                            RimeDaemon.engineState.first { it == RimeLifecycle.State.FAILED }
                            session.run {
                                checkTransientEditorStateCleared("failed private native restart")
                            }

                            check(custom.delete())
                            if (hadCustom) check(savedCustom.renameTo(custom))
                            RimeDaemon.retryFailedStartup()
                            session.runOnReady {
                                check(isReady && selectSchema("luna_pinyin_t9"))
                                finishEditor(privateEditor)
                                checkTransientEditorStateCleared("private retry finish")
                                beginEditor(privateEditor + 1, nullInputType = false, privacyMode = false)
                                setRuntimeOption("ascii_mode", false)
                                "64".forEach { check(processKey(it.code)) }
                                check(t9Cached.choices.any { it.spelling == "ni" }) {
                                    "Ordinary input did not recover after private restart failure"
                                }
                                finishEditor(privateEditor + 1)
                                checkTransientEditorStateCleared("ordinary retry finish")
                            }
                        } finally {
                            RimeDaemon.destroySession("private-restart-failure-probe")
                        }
                    } finally {
                        if (savedCustom.exists()) {
                            custom.delete()
                            check(savedCustom.renameTo(custom))
                        } else if (!hadCustom) {
                            custom.delete()
                        }
                    }
                    RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED }
                }
            }
            success = true
            result.putString("stream", "PASS: missing asset repair, asset-copy and unusable-configuration failure, usable existing dictionary after malformed optional patch, private transient-state clearing on failed restart, bounded wait, unchanged checksums, retry, typing, stale-theme rejection and shutdown\n")
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n")
        } finally {
            result.putBoolean("passed", success)
            instrumentation.finish(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }
}
