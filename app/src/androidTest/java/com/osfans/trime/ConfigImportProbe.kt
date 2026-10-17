// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.data.ConfigurationImportException
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.core.data.DataStorageMode
import com.osfans.trime.core.data.SyncRelativePath
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Production import through a picker-granted system provider, on an isolated emulator only. */
object ConfigImportProbe {
    fun run(instrumentation: Instrumentation, value: String?) {
        val result = Bundle()
        var passed = false
        var session: RimeSession? = null
        val owner = "t12-import-${UUID.randomUUID()}"
        val cr = instrumentation.targetContext.contentResolver
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            val tree = Uri.parse(checkNotNull(value) { "A picker-granted dedicated tree is required" })
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            check(
                tree.authority == "com.android.externalstorage.documents" &&
                    rootId.substringAfter(':').substringAfterLast('/').startsWith("trime-saf-"),
            ) { "Dedicated system-provider tree required" }
            check(cr.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission })
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val prefs = AppPrefs.defaultInstance().profile
            val previousMode = prefs.dataStorageMode.getValue()
            val previousTree = prefs.externalRimeTreeUri.getValue()
            check(previousMode == DataStorageMode.EXTERNAL_SYNC && previousTree == tree.toString()) {
                "First select this dedicated empty tree through the real configuration picker"
            }
            val id = "t12_import_${UUID.randomUUID()}"
            val configName = "$id.yaml"
            val assetName = "$id.txt"
            val localConfig = File(DataManager.userDataDir, configName)
            val localAsset = File(DataManager.userDataDir, assetName)
            check(!localConfig.exists() && !localAsset.exists())
            val createdDocuments = mutableListOf<Uri>()
            fun write(uri: Uri, content: String) = checkNotNull(cr.openOutputStream(uri, "wt")).use {
                it.write(content.toByteArray(Charsets.UTF_8))
            }
            fun digest(file: File): String {
                val sha = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val bytes = ByteArray(8192)
                    while (true) {
                        val count = input.read(bytes)
                        if (count < 0) break
                        sha.update(bytes, 0, count)
                    }
                }
                return sha.digest().joinToString("") { "%02x".format(it) }
            }
            val custom = File(DataManager.userDataDir, "default.custom.yaml")
            val customBefore = custom.takeIf { it.isFile }?.let(::digest)
            val checks = mutableListOf<String>()
            val asciiTipChecks = mutableListOf<String>()
            try {
                val config = checkNotNull(DocumentsContract.createDocument(cr, root, "application/x-yaml", configName))
                    .also { createdDocuments.add(it) }
                val asset = checkNotNull(DocumentsContract.createDocument(cr, root, "text/plain", assetName))
                    .also { createdDocuments.add(it) }
                instrumentation.runOnMainSync { session = RimeDaemon.createSession(owner) }
                runBlocking {
                    withTimeout(300_000) {
                        checkNotNull(session).runOnReady {
                            val beforeSchema = selectedSchemaId()
                            check(AppPrefs.defaultInstance().general.asciiSwitchTips.getValue()) {
                                "Enable ASCII switch tips in this dedicated profile before running the inactive-editor regression"
                            }
                            val realRime = RimeDaemon::class.java.getDeclaredMethod("getRealRime").apply {
                                isAccessible = true
                            }.invoke(RimeDaemon) as Rime
                            val editorTokenField = Rime::class.java.getDeclaredField("editorToken").apply { isAccessible = true }
                            val tipJobField = Rime::class.java.getDeclaredField("asciiSwitchTipsJob").apply { isAccessible = true }
                            suspend fun assertNoInactiveEditorTip(label: String) {
                                check(editorTokenField.getLong(null) == 0L) { "$label unexpectedly has an active editor" }
                                // This real engine read also places the inspection after pending native work.
                                check(getRawInput().isEmpty()) { "$label retained raw input" }
                                check(tipJobField.get(realRime) == null) { "$label scheduled an ownerless ASCII-tip context read" }
                                checkTransientEditorStateCleared(label)
                                asciiTipChecks.add(label)
                            }
                            assertNoInactiveEditorTip("startup without an editor")
                            val beforeAscii = getRuntimeOption("ascii_mode")
                            try {
                                setRuntimeOption("ascii_mode", true)
                                setRuntimeOption("ascii_mode", false)
                                assertNoInactiveEditorTip("explicit ASCII option changes without an editor")
                            } finally {
                                setRuntimeOption("ascii_mode", beforeAscii)
                            }
                            val starts = AtomicInteger()
                            val observer = async(start = CoroutineStart.UNDISPATCHED) {
                                messageFlow.collect { message ->
                                    if (message is RimeMessage.DeployMessage && message.data == RimeMessage.DeployMessage.State.Start) starts.incrementAndGet()
                                }
                            }
                            try {
                                suspend fun verifyMalformedBatch() {
                                    write(config, "fixture: [unterminated\n")
                                    write(asset, "must not publish early")
                                    val firstFailure = runCatching { importConfiguration(tree.toString()) }.exceptionOrNull()
                                    check(firstFailure != null)
                                    check(!localConfig.exists() && !localAsset.exists())
                                    check(prefs.dataStorageMode.getValue() == previousMode && prefs.externalRimeTreeUri.getValue() == previousTree)
                                    check(starts.get() == 0 && selectedSchemaId() == beforeSchema)
                                    assertNoInactiveEditorTip("malformed configuration rejection")
                                    checks.add("malformed YAML rejects the entire batch before publishing files, changing preferences, or starting deployment")
                                }
                                verifyMalformedBatch()

                                suspend fun verifyValidActivation() {
                                    write(config, "fixture:\n  value: accepted\n")
                                    write(asset, "initial asset")
                                    val accepted = importConfiguration(tree.toString())
                                    yield()
                                    check(accepted.validatedConfigs >= 1 && accepted.configurationChanged)
                                    check(localConfig.readText() == "fixture:\n  value: accepted\n" && localAsset.readText() == "initial asset")
                                    check(prefs.externalRimeTreeUri.getValue() == tree.toString() && prefs.dataStorageMode.getValue() == DataStorageMode.EXTERNAL_SYNC)
                                    check(isReady && starts.get() > 0 && selectedSchemaId() == beforeSchema)
                                    assertNoInactiveEditorTip("valid native configuration activation")
                                    checks.add("valid configuration activates through native deployment before the selected folder is remembered")
                                }
                                verifyValidActivation()

                                val deploymentCount = starts.get()
                                suspend fun verifyUnchangedAndResourceOnlyImports() {
                                    val repeated = importConfiguration(tree.toString())
                                    check(!repeated.configurationChanged && repeated.copied == 0 && starts.get() == deploymentCount)
                                    assertNoInactiveEditorTip("identical configuration import")
                                    checks.add("re-importing identical validated configuration starts no deployment")

                                    write(asset, "changed asset")
                                    val resourceOnly = importConfiguration(tree.toString())
                                    check(!resourceOnly.configurationChanged && localAsset.readText() == "changed asset" && starts.get() == deploymentCount)
                                    assertNoInactiveEditorTip("resource-only configuration import")
                                    checks.add("a non-configuration resource change starts no unrelated deployment")
                                }
                                verifyUnchangedAndResourceOnlyImports()

                                val oldConfig = digest(localConfig)
                                suspend fun verifyReplacementRollback() {
                                    write(config, "fixture: [broken\n")
                                    write(asset, "must not replace the accepted asset")
                                    check(runCatching { importConfiguration(tree.toString()) }.isFailure)
                                    check(digest(localConfig) == oldConfig && localAsset.readText() == "changed asset")
                                    check(prefs.externalRimeTreeUri.getValue() == tree.toString() && starts.get() == deploymentCount && isReady)
                                    check(custom.takeIf { it.isFile }?.let(::digest) == customBefore)
                                    check(cr.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission })
                                    assertNoInactiveEditorTip("failed replacement rollback")
                                    checks.add("failed replacement preserves the accepted files, user template, native readiness, and previous folder grant")
                                }
                                verifyReplacementRollback()

                                suspend fun verifySemanticThemeRollback() {
                                    val themeId = ThemeManager.prefs.selectedTheme.getValue()
                                    SyncRelativePath.requireSafeName(themeId)
                                    val themeName = "$themeId.yaml"
                                    val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
                                    checkNotNull(cr.query(childUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)).use { cursor ->
                                        while (cursor.moveToNext()) check(cursor.getString(0) != themeName) { "The dedicated tree already contains the active theme; refusing to overwrite it" }
                                    }
                                    val themeSource = File(DataManager.userDataDir, themeName)
                                    check(!themeSource.exists() || themeSource.isFile)
                                    val sourceBefore = themeSource.takeIf { it.isFile }?.let(::digest)
                                    val sharedSource = File(DataManager.sharedDataDir, themeName)
                                    val sharedBefore = sharedSource.takeIf { it.isFile }?.let(::digest)
                                    val buildDir = File(DataManager.userDataDir, "build")
                                    fun buildDigests(): Map<String, String> = if (!buildDir.exists()) {
                                        emptyMap()
                                    } else {
                                        buildDir.walkTopDown()
                                            .filter { it.isFile }.associate { it.relativeTo(buildDir).path to digest(it) }
                                    }
                                    val buildsBefore = buildDigests()
                                    val artifactBefore = digest(File(DataManager.resolveDeployedResourcePath(themeId)))
                                    val indexFile = File(instrumentation.targetContext.filesDir, "rime_sync_index.json")
                                    val indexBefore = indexFile.takeIf { it.isFile }?.let(::digest)
                                    val activeBefore = withContext(Dispatchers.Main) { ThemeManager.activeTheme }
                                    val optionNames = (currentSchema().switches.flatMap { if (it.name.isEmpty()) it.options else listOf(it.name) } + "ascii_mode")
                                        .filter { it.isNotEmpty() }.distinct()
                                    val optionsBefore = optionNames.associateWith { getRuntimeOption(it) }
                                    val fixtureConfigBefore = checkNotNull(cr.openInputStream(config)).bufferedReader().use { it.readText() }
                                    val fixtureAssetBefore = checkNotNull(cr.openInputStream(asset)).bufferedReader().use { it.readText() }
                                    var badTheme: Uri? = null
                                    var semanticFailure: Throwable? = null
                                    fun cleanupSemanticFixtures() {
                                        // Only dedicated-tree fixtures are cleaned up; original local files are never rewritten by this probe.
                                        val cleanupErrors = mutableListOf<Throwable>()
                                        fun cleanSemanticFixture(block: () -> Unit) {
                                            runCatching(block).exceptionOrNull()?.let(cleanupErrors::add)
                                        }
                                        badTheme?.let { document ->
                                            cleanSemanticFixture {
                                                check(DocumentsContract.deleteDocument(cr, document))
                                                createdDocuments.remove(document)
                                            }
                                        }
                                        cleanSemanticFixture { write(config, fixtureConfigBefore) }
                                        cleanSemanticFixture { write(asset, fixtureAssetBefore) }
                                        if (cleanupErrors.isNotEmpty()) {
                                            val failure = semanticFailure ?: IllegalStateException("Semantic import fixture cleanup failed")
                                            cleanupErrors.forEach(failure::addSuppressed)
                                            if (semanticFailure == null) throw failure
                                        }
                                    }
                                    try {
                                        // Restore the earlier fixture so this rejection reaches real native activation.
                                        write(config, "fixture:\n  value: accepted\n")
                                        write(asset, "changed asset")
                                        delay(1_100)
                                        badTheme = checkNotNull(DocumentsContract.createDocument(cr, root, "application/x-yaml", themeName))
                                            .also { createdDocuments.add(it) }
                                        // Keep this deliberately invalid source newer than the shared theme:
                                        // native deployment must decode it instead of trashing an older copy.
                                        val invalidTheme = "config_version: '999.0'\nname: T12 invalid theme\nstyle: {candidate_text_size: not_a_number}\npreset_color_schemes: {t12_invalid: {name: Invalid}}\n"
                                        write(checkNotNull(badTheme), invalidTheme)
                                        suspend fun assertRestored(label: String, failure: Throwable?, deploymentBefore: Int) {
                                            check(failure is ConfigurationImportException && failure.previousConfigurationRestored) {
                                                "$label did not report a completed rollback: ${failure?.stackTraceToString()}"
                                            }
                                            withTimeout(5_000) { while (starts.get() <= deploymentBefore) yield() }
                                            check(if (sourceBefore == null) !themeSource.exists() else themeSource.isFile && digest(themeSource) == sourceBefore) {
                                                "$label changed the original theme source; expected SHA $sourceBefore"
                                            }
                                            check(sharedSource.takeIf { it.isFile }?.let(::digest) == sharedBefore) { "$label changed the shared theme source; expected SHA $sharedBefore" }
                                            val buildsAfter = buildDigests()
                                            check(buildsAfter == buildsBefore) {
                                                val changed = (buildsBefore.keys + buildsAfter.keys)
                                                    .filter { buildsBefore[it] != buildsAfter[it] }
                                                    .sorted().associateWith { "${buildsBefore[it]} -> ${buildsAfter[it]}" }
                                                "$label changed compiled cache; changed SHA evidence $changed; original SHA evidence $buildsBefore"
                                            }
                                            check(digest(File(DataManager.resolveDeployedResourcePath(themeId))) == artifactBefore) { "$label changed active artifact; expected SHA $artifactBefore" }
                                            check(indexFile.takeIf { it.isFile }?.let(::digest) == indexBefore)
                                            check(ThemeManager.prefs.selectedTheme.getValue() == themeId)
                                            check(withContext(Dispatchers.Main) { ThemeManager.activeTheme == activeBefore })
                                            check(isReady && selectedSchemaId() == beforeSchema && currentSchema().schemaId == beforeSchema)
                                            check(optionNames.associateWith { getRuntimeOption(it) } == optionsBefore)
                                            check(prefs.dataStorageMode.getValue() == previousMode && prefs.externalRimeTreeUri.getValue() == previousTree)
                                            check(custom.takeIf { it.isFile }?.let(::digest) == customBefore)
                                            check(digest(localConfig) == oldConfig && localAsset.readText() == "changed asset")
                                            check(cr.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission })
                                            assertNoInactiveEditorTip(label)
                                        }
                                        val directoryDeploymentBefore = starts.get()
                                        val directoryFailure = runCatching { importConfiguration(tree.toString()) }.exceptionOrNull()
                                        assertRestored("directory semantic-theme failure", directoryFailure, directoryDeploymentBefore)
                                        checks.add("real native directory activation rejects an invalid typed theme and restores source, compiled cache, active model, schema/options, index, preferences, and grant")

                                        // librime detects source modification at whole-second precision.
                                        delay(1_100)
                                        write(checkNotNull(badTheme), invalidTheme)
                                        val singleDeploymentBefore = starts.get()
                                        val singleFailure = runCatching { importThemeConfiguration(themeId) }.exceptionOrNull()
                                        assertRestored("single-theme semantic failure", singleFailure, singleDeploymentBefore)
                                        checks.add("same-ID single-theme activation fails without accepting an in-memory fallback and restores original files, compiled cache, selection, native readiness, and grant")
                                    } catch (failure: Throwable) {
                                        semanticFailure = failure
                                        throw failure
                                    } finally {
                                        cleanupSemanticFixtures()
                                    }
                                }
                                verifySemanticThemeRollback()
                            } finally {
                                observer.cancelAndJoin()
                            }
                        }
                    }
                }
            } finally {
                val cleanupFailures = mutableListOf<Throwable>()
                fun cleanup(block: () -> Unit) {
                    runCatching(block).exceptionOrNull()?.let(cleanupFailures::add)
                }
                cleanup { instrumentation.runOnMainSync { RimeDaemon.destroySession(owner) } }
                session = null
                createdDocuments.forEach { document -> cleanup { check(DocumentsContract.deleteDocument(cr, document)) } }
                cleanup { check(!localConfig.exists() || localConfig.delete()) }
                cleanup { check(!localAsset.exists() || localAsset.delete()) }
                cleanup { prefs.externalRimeTreeUri.setValue(previousTree) }
                cleanup { prefs.dataStorageMode.setValue(previousMode) }
                if (cleanupFailures.isNotEmpty()) {
                    val failure = IllegalStateException("Configuration-import fixture cleanup failed")
                    cleanupFailures.forEach(failure::addSuppressed)
                    throw failure
                }
            }
            result.putInt("checks", checks.size)
            result.putInt("asciiTipChecks", asciiTipChecks.size)
            result.putString("asciiTipChecksDetails", asciiTipChecks.joinToString("\n"))
            result.putString("stream", "PASS: ${checks.size} production configuration-import checks\n${checks.joinToString("\n")}\n")
            passed = true
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            session?.let { instrumentation.runOnMainSync { RimeDaemon.destroySession(owner) } }
        }
        result.putBoolean("passed", passed)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
