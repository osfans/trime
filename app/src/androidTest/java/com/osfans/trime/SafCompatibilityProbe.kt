// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.osfans.trime.core.RimeMaintenanceMutex
import com.osfans.trime.core.data.ConfigurationImportException
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.core.data.SafDirectoryListing
import com.osfans.trime.core.data.SyncEntry
import com.osfans.trime.core.data.SyncFingerprint
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.core.data.fixture.FixtureAtomicSafFileCopy
import com.osfans.trime.core.data.fixture.FixtureSafPathCache
import com.osfans.trime.core.data.fixture.FixtureSafTreeListing
import com.osfans.trime.core.data.fixture.FixtureSafTreeWalker
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.provider.RimeDataProvider
import io.github.whiredplanck.storageaccess.StorageAccess
import io.github.whiredplanck.storageaccess.StorageDocument
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Uses Android cursors, pipes and DocumentsContract, with only UUID-scoped fixtures.
 *
 * The isolated resolver cases below validate the hostile-provider fixture contract. Production
 * StorageAccess/UserDataManager is exercised by [verifyExternalTree] and [verifyImportHistory]
 * against a picker-granted system-provider tree.
 */
object SafCompatibilityProbe {
    fun run(instrumentation: Instrumentation, externalTree: String? = null, revoke: Boolean = false) {
        val result = Bundle()
        var passed = false
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            var checks = if (Build.VERSION.SDK_INT >= 29) {
                verify(instrumentation)
            } else {
                check(externalTree != null) { "API 21-28 requires a real dedicated SAF tree; virtual resolver fixtures need API 29" }
                0
            }
            check(!revoke || externalTree != null) { "Revocation requires the dedicated tree URI" }
            externalTree?.let {
                verifyExternalTree(instrumentation, it, revoke)
                checks++
            }
            val externalResult = when {
                revoke -> "; system provider permission revoked and access denied"
                externalTree != null -> "; system provider persisted grant and round trip"
                else -> "; isolated provider fixtures only"
            }
            result.putString("stream", "PASS: $checks SAF checks$externalResult\n")
            result.putInt("checks", checks)
            passed = true
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        }
        result.putBoolean("passed", passed)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    @RequiresApi(29)
    private object ResolverWrapper {
        fun wrap(provider: DocumentsProvider): ContentResolver = ContentResolver.wrap(provider)
    }

    /** Runs the production replacement algorithm against an isolated hostile provider. */
    private class ResolverExternalFileOperations(
        private val resolver: ContentResolver,
    ) : UserDataManager.ExternalFileOperations {
        private val projection =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
            )

        override suspend fun list(dirUri: Uri): List<StorageDocument> = SafDirectoryListing.list(resolver, dirUri)

        override suspend fun writeFile(
            dirUri: Uri,
            name: String,
            mime: String,
            write: suspend (OutputStream) -> Unit,
        ): StorageDocument {
            val uri =
                DocumentsContract.createDocument(resolver, dirUri, mime, name)
                    ?: error("Cannot create document: $name")
            val descriptor =
                resolver.openFileDescriptor(uri, "wt")
                    ?: error("Cannot write document: $uri")
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                write(output)
            }
            return stat(uri)
        }

        override suspend fun rename(
            uri: Uri,
            name: String,
        ): StorageDocument {
            val renamed =
                DocumentsContract.renameDocument(resolver, uri, name)
                    ?: error("Cannot rename $uri to $name")
            return stat(renamed)
        }

        override suspend fun delete(uri: Uri) {
            check(DocumentsContract.deleteDocument(resolver, uri)) {
                "Cannot delete document: $uri"
            }
        }

        override suspend fun supportsRename(uri: Uri): Boolean = resolver.query(uri, arrayOf(Document.COLUMN_FLAGS), null, null, null)?.use {
            check(it.moveToFirst()) { "Missing document: $uri" }
            it.getInt(0) and Document.FLAG_SUPPORTS_RENAME != 0
        } ?: error("Cannot query document: $uri")

        override suspend fun digest(uri: Uri): ByteArray {
            val descriptor =
                resolver.openFileDescriptor(uri, "r")
                    ?: error("Cannot read document: $uri")
            return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                SyncFingerprint.digest(input).also {
                    if (descriptor.canDetectErrors()) descriptor.checkError()
                }
            }
        }

        private fun stat(uri: Uri): StorageDocument = resolver.query(uri, projection, null, null, null)?.use {
            check(it.moveToFirst()) { "Missing document: $uri" }
            documentFromCursor(uri, it)
        } ?: error("Cannot query document: $uri")

        private fun documentFromCursor(
            treeUri: Uri,
            cursor: Cursor,
        ): StorageDocument {
            val id = cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME))
            val mime = cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE))
            val sizeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
            check(!id.isNullOrEmpty() && !name.isNullOrEmpty() && !mime.isNullOrEmpty()) {
                "Invalid document metadata"
            }
            return StorageDocument(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                name,
                mime == Document.MIME_TYPE_DIR,
                if (cursor.isNull(sizeColumn)) 0 else cursor.getLong(sizeColumn),
                if (cursor.isNull(modifiedColumn)) 0 else cursor.getLong(modifiedColumn),
                mime,
            )
        }
    }

    private fun verifyExternalTree(instrumentation: Instrumentation, value: String, revoke: Boolean) {
        val tree = value.toUri()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        check(tree.authority == "com.android.externalstorage.documents" && rootId.substringAfterLast('/').startsWith("trime-saf-")) {
            "System provider test requires a dedicated trime-saf-UUID directory"
        }
        val cr = instrumentation.targetContext.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        cr.takePersistableUriPermission(tree, flags)
        check(cr.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission })
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        if (revoke) {
            cr.releasePersistableUriPermission(tree, flags)
            check(cr.persistedUriPermissions.none { it.uri == tree })
            val failure = runCatching { cr.query(root, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.close() }.exceptionOrNull()
            check(failure is SecurityException) { "Access still allowed; stop the picker/app to release transient grants before this test" }
            return
        }
        val directory = checkNotNull(DocumentsContract.createDocument(cr, root, Document.MIME_TYPE_DIR, "probe-${UUID.randomUUID()}"))
        val source = File.createTempFile("saf-source", ".yaml", instrumentation.targetContext.cacheDir)
        try {
            runBlocking {
                val unrelated = checkNotNull(DocumentsContract.createDocument(cr, directory, "text/plain", "aaa-unrelated.txt"))
                checkNotNull(cr.openOutputStream(unrelated, "wt")).use { it.write("must survive".toByteArray()) }
                for (payload in listOf("initial data", "replacement\n".repeat(1024))) {
                    source.writeText(payload)
                    RimeMaintenanceMutex.withLock {
                        UserDataManager.replaceExternalFileForTesting(source, directory, "config.yaml")
                    }
                    val document = checkNotNull(StorageAccess.child(directory, "config.yaml"))
                    val actual = StorageAccess.readFile(document.uri) { it.bufferedReader().readText() }
                    check(actual == payload)
                    val listing = SafDirectoryListing.list(cr, directory)
                    check(listing.map { it.name }.toSet() == setOf("aaa-unrelated.txt", "config.yaml"))
                    check(StorageAccess.readFile(unrelated) { it.bufferedReader().readText() } == "must survive")
                }
            }
        } finally {
            source.delete()
            check(DocumentsContract.deleteDocument(cr, directory))
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: production StorageAccess replacement via system provider, persisted grant, read-back and cleanup\n") })
        verifyImportHistory(instrumentation, tree, rootId)
    }

    private fun verifyImportHistory(instrumentation: Instrumentation, tree: android.net.Uri, rootId: String) = runBlocking {
        RimeMaintenanceMutex.withLock {
            val context = instrumentation.targetContext
            check(UserDataManager.usesExternalSync() && UserDataManager.treeUri() == tree)
            val cr = context.contentResolver
            val id = "__saf_history_${UUID.randomUUID()}"
            val names = listOf("$id-edited.yaml", "$id-unchanged.yaml")
            val binaryNames = listOf("$id-existing.userdb", "$id-fresh.userdb")
            val portableName = "$id.userdb.txt"
            val migration = AppPrefs.defaultInstance().profile.userDbMigrated
            val previousMigration = migration.getValue()
            val untracked = File(DataManager.userDataDir, "$id-untracked.yaml")
            val source = File.createTempFile("saf-history", ".yaml", context.cacheDir)
            val cache = FixtureSafPathCache(FixtureSafTreeListing(emptyList(), mapOf("" to rootId)), rootId)
            try {
                untracked.writeText("personal")
                val importedYaml = "value: old\n"
                source.writeText(importedYaml)
                migration.setValue(false)
                val personalDb = File(DataManager.userDataDir, binaryNames.first())
                check(personalDb.mkdir())
                File(personalDb, "LOG").writeText("personal-learning")
                binaryNames.forEach { name -> FixtureAtomicSafFileCopy.copyFromFile(cr, tree, cache, source, "$name/LOG", name, "LOG") }
                FixtureAtomicSafFileCopy.copyFromFile(cr, tree, cache, source, portableName, "", portableName)
                names.forEach { name -> FixtureAtomicSafFileCopy.copyFromFile(cr, tree, cache, source, name, "", name) }
                UserDataManager.importToLocal(showProgress = false).getOrThrow()
                check(untracked.readText() == "personal")
                check(File(personalDb, "LOG").readText() == "personal-learning")
                check(!File(DataManager.userDataDir, binaryNames.last()).exists())
                check(File(DataManager.userDataDir, portableName).readText() == importedYaml)
                val history = UserDataManager.indexSnapshotForTesting().entries
                check(names.all { history[it]?.localSha256?.length == 64 })
                val edited = File(DataManager.userDataDir, names.first())
                val timestamp = edited.lastModified()
                edited.writeText("new")
                check(edited.setLastModified(timestamp))
                FixtureSafTreeWalker.listFiles(cr, tree, rootId).filter { it.relativePath in names }.forEach { entry ->
                    check(DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)))
                }
                UserDataManager.importToLocal(showProgress = false).getOrThrow()
                check(edited.readText() == "new")
                check(!File(DataManager.userDataDir, names.last()).exists())
                check(untracked.readText() == "personal")
                check(names.none { it in UserDataManager.indexSnapshotForTesting().entries })
                val blockedName = "$id-blocked.yaml"
                val blocked = File(DataManager.userDataDir, blockedName)
                try {
                    FixtureAtomicSafFileCopy.copyFromFile(cr, tree, cache, source, blockedName, "", blockedName)
                    check(blocked.mkdir())
                    check(UserDataManager.importToLocal(showProgress = false).isFailure)
                    var activationAttempted = false
                    fun unexpectedActivation(): Nothing {
                        activationAttempted = true
                        error("Blocked import reached activation")
                    }
                    val themeFailure = runCatching {
                        UserDataManager.importThemeToLocal(
                            blockedName.removeSuffix(".yaml"),
                            beforePublish = { unexpectedActivation() },
                            beforeRollback = { unexpectedActivation() },
                            afterRollback = { unexpectedActivation() },
                            activationContext = { unexpectedActivation() },
                            rememberSelection = { unexpectedActivation() },
                            restoreSelection = { unexpectedActivation() },
                            activate = { unexpectedActivation() },
                        )
                    }.exceptionOrNull()
                    check(themeFailure is ConfigurationImportException)
                    check(themeFailure.previousConfigurationRestored && !activationAttempted)
                    check(themeFailure.cause?.message == "Import destination is not a file: $blockedName")
                    check(blocked.isDirectory && untracked.readText() == "personal")
                    check(blockedName !in UserDataManager.indexSnapshotForTesting().entries)
                } finally {
                    FixtureSafTreeWalker.listFiles(cr, tree, rootId).filter { it.relativePath == blockedName }.forEach { entry ->
                        check(DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)))
                    }
                    check(!blocked.exists() || blocked.delete())
                }
            } finally {
                migration.setValue(previousMigration)
                (binaryNames + portableName).forEach { name ->
                    FixtureSafTreeWalker.findFileEntry(cr, tree, rootId, name)?.let { entry ->
                        check(DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)))
                    }
                    val local = File(DataManager.userDataDir, name)
                    check(!local.exists() || local.deleteRecursively())
                }
                FixtureSafTreeWalker.listFiles(cr, tree, rootId).filter { it.relativePath in names }.forEach { entry ->
                    check(DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)))
                }
                names.forEach { File(DataManager.userDataDir, it).delete() }
                untracked.delete()
                source.delete()
            }
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("stream", "PASS: real SAF import preserves untracked files and same-metadata local edits; only hash-verified deletions propagate\n")
                },
            )
        }
    }

    /** Provider-fault fixtures; these do not substitute for the real-tree production checks. */
    @RequiresApi(29)
    private fun verify(instrumentation: Instrumentation): Int {
        val context = instrumentation.targetContext
        val sandbox = File(context.cacheDir, "saf-probe-${UUID.randomUUID()}").apply { mkdirs() }
        val authority = "${context.packageName}.saf.test"
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root")
        val info = ProviderInfo().apply {
            this.authority = authority
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = readPermission
        }
        var checks = 0
        fun test(name: String, block: (FixtureProvider, ContentResolver, File) -> Unit) {
            val directory = File(sandbox, "case-${checks + 1}").apply { mkdir() }
            val provider = FixtureProvider(directory)
            provider.attachInfo(context, info)
            val resolver = ResolverWrapper.wrap(provider)
            val source = File(sandbox, "source").apply { writeText("new dictionary\n".repeat(1024)) }
            block(provider, resolver, source)
            checks++
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS $checks: $name\n") })
        }
        fun copy(cr: ContentResolver, source: File, path: String = "sample.yaml") {
            val cache = FixtureSafPathCache(FixtureSafTreeListing(emptyList(), mapOf("" to "root")), "root")
            FixtureAtomicSafFileCopy.copyFromFile(cr, tree, cache, source, path, path.substringBeforeLast('/', ""), path.substringAfterLast('/'))
        }
        fun productionCopy(cr: ContentResolver, source: File, name: String = "sample.yaml") {
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, "root")
            runBlocking {
                UserDataManager.replaceExternalFileForTesting(
                    source,
                    root,
                    name,
                    ResolverExternalFileOperations(cr),
                )
            }
        }
        fun expectFailure(block: () -> Unit) {
            check(runCatching(block).isFailure) { "Failure was not reported" }
        }
        fun productionListing(cr: ContentResolver) = runBlocking { UserDataManager.listExternalEntriesForTesting(cr, tree) }
        fun expectRejectedListingPreservesTrackedLocal(cr: ContentResolver): Throwable {
            val localRoot = File(sandbox, "listing-orphan-${UUID.randomUUID()}").apply { mkdir() }
            val sentinel = File(localRoot, "tracked.yaml").apply { writeText("must survive") }
            val known =
                mapOf(
                    sentinel.name to
                        SyncEntry(
                            sentinel.length(),
                            sentinel.lastModified(),
                            SyncFingerprint.of(sentinel),
                        ),
                )
            try {
                val result =
                    runCatching {
                        val externalPaths = productionListing(cr).map { it.relativePath }.toSet()
                        UserDataManager.removeLocalOrphans(localRoot, externalPaths, knownEntries = known)
                    }
                check(result.isFailure) { "Unsafe provider listing was accepted" }
                check(sentinel.readText() == "must survive") {
                    "Rejected provider listing triggered orphan deletion"
                }
                return checkNotNull(result.exceptionOrNull())
            } finally {
                localRoot.deleteRecursively()
            }
        }
        try {
            test("new file, nested Unicode/spaces and changed rename ID") { p, cr, source ->
                val name = "\u8bcd\u5178 folder/sample.yaml"
                copy(cr, source, name)
                val listing = FixtureSafTreeWalker.listTree(cr, tree, "root")
                check(listing.files.single().relativePath == name)
                check(productionListing(cr).single { !it.file.isDir }.relativePath == name)
                check(p.file("sample.yaml").readBytes().contentEquals(source.readBytes()))
            }
            test("replace retains unrelated .bak file") { p, cr, source ->
                p.seed("sample.yaml", "old")
                p.seed("sample.yaml.bak", "personal backup")
                copy(cr, source)
                check(p.file("sample.yaml").readBytes().contentEquals(source.readBytes()))
                check(p.file("sample.yaml.bak").readText() == "personal backup")
                check(p.names().none { it.startsWith(".trime-") })
            }
            test("production replacement finds an existing child after the first row") { p, cr, source ->
                p.seed("first.yaml", "must survive")
                p.seed("sample.yaml", "old")
                check(SafDirectoryListing.list(cr, tree).map { it.name } == listOf("first.yaml", "sample.yaml"))
                productionCopy(cr, source)
                check(p.file("first.yaml").readText() == "must survive")
                check(p.file("sample.yaml").readBytes().contentEquals(source.readBytes()))
                check(p.names().toSet() == setOf("first.yaml", "sample.yaml"))
            }
            test("production listing rejects a document outside a tree URI") { _, cr, _ ->
                expectFailure { SafDirectoryListing.list(cr, DocumentsContract.buildDocumentUri(authority, "root")) }
            }
            test("unknown metadata and nonseekable reliable pipe") { p, cr, source ->
                p.pipeReads = true
                copy(cr, source)
                val entry = FixtureSafTreeWalker.listFiles(cr, tree, "root").single()
                check(entry.size == -1L && entry.lastModified == 0L)
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
                val local = File(sandbox, "round-trip")
                val descriptor = checkNotNull(cr.openFileDescriptor(uri, "r"))
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    check(descriptor.canDetectErrors())
                    UserDataManager.writeFromStream(local) { output ->
                        input.copyTo(output)
                        descriptor.checkError()
                    }
                }
                check(local.readBytes().contentEquals(source.readBytes()))
            }
            test("write failure cleans temporary document") { p, cr, source ->
                p.seed("sample.yaml", "old")
                p.failWrite = true
                expectFailure { copy(cr, source) }
                check(p.file("sample.yaml").readText() == "old")
                check(p.names() == listOf("sample.yaml"))
            }
            test("reliable pipe error preserves original") { p, cr, source ->
                p.seed("sample.yaml", "old")
                p.pipeReads = true
                p.failPipeRead = true
                expectFailure { copy(cr, source) }
                check(p.file("sample.yaml").readText() == "old")
                check(p.names() == listOf("sample.yaml"))
            }
            test("read-back detects truncated provider output") { p, cr, source ->
                p.seed("sample.yaml", "old")
                p.corruptRead = true
                expectFailure { copy(cr, source) }
                check(p.file("sample.yaml").readText() == "old")
                check(p.names() == listOf("sample.yaml"))
            }
            test("publish failure restores backup with changed ID") { p, cr, source ->
                p.seed("sample.yaml", "old")
                p.failPublish = true
                expectFailure { copy(cr, source) }
                check(p.file("sample.yaml").readText() == "old")
                check(p.names() == listOf("sample.yaml"))
            }
            test("rollback failure retains recovery backup") { p, cr, source ->
                p.seed("sample.yaml", "old")
                p.failPublish = true
                p.failRestore = true
                expectFailure { copy(cr, source) }
                check(p.names().single().endsWith(".bak"))
                check(p.file(p.names().single()).readText() == "old")
            }
            test("provider without rename supports new files") { p, cr, source ->
                p.canRename = false
                productionCopy(cr, source)
                check(p.file("sample.yaml").readBytes().contentEquals(source.readBytes()))
                check(p.names() == listOf("sample.yaml"))
            }
            test("provider without rename cleans failed new files") { p, cr, source ->
                p.canRename = false
                p.corruptPublishedRead = true
                expectFailure { productionCopy(cr, source) }
                check(p.names().isEmpty())
            }
            test("provider without rename retains existing files") { p, cr, source ->
                p.canRename = false
                p.seed("sample.yaml", "old")
                expectFailure { productionCopy(cr, source) }
                check(p.file("sample.yaml").readText() == "old")
                check(p.names() == listOf("sample.yaml"))
            }
            test("loading listing fails closed") { p, cr, source ->
                p.loading = true
                expectRejectedListingPreservesTrackedLocal(cr)
                expectFailure { FixtureSafTreeWalker.listTree(cr, tree, "root") }
                expectFailure { copy(cr, source) }
                expectFailure { productionCopy(cr, source) }
                check(p.names().isEmpty())
            }
            test("null listing fails closed") { p, cr, source ->
                p.nullListing = true
                expectRejectedListingPreservesTrackedLocal(cr)
                expectFailure { FixtureSafTreeWalker.listTree(cr, tree, "root") }
                expectFailure { copy(cr, source) }
                expectFailure { productionCopy(cr, source) }
                check(p.names().isEmpty())
            }
            test("provider listing error fails closed") { p, cr, source ->
                p.listingError = "fixture unavailable"
                expectRejectedListingPreservesTrackedLocal(cr)
                expectFailure { productionCopy(cr, source) }
            }
            test("production walker rejects late loading before deleting tracked local data") { p, cr, _ ->
                p.seed("first.yaml", "remote data must survive")
                p.loadingAfterTraversal = true
                val failure = expectRejectedListingPreservesTrackedLocal(cr)
                check(failure is IOException && failure.message?.startsWith("Document listing is still loading:") == true)
                check(p.exhaustedListingExtrasReads > 0) { "Loading must be observed after traversing the valid provider row" }
                check(p.file("first.yaml").readText() == "remote data must survive")
            }
            test("production walker rejects late provider error before deleting tracked local data") { p, cr, _ ->
                p.seed("first.yaml", "remote data must survive")
                p.errorAfterTraversal = "fixture failed after its last row"
                val failure = expectRejectedListingPreservesTrackedLocal(cr)
                check(failure is IOException && failure.message?.contains("fixture failed after its last row") == true)
                check(p.exhaustedListingExtrasReads > 0) { "Error must be observed after traversing the valid provider row" }
                check(p.file("first.yaml").readText() == "remote data must survive")
            }
            test("unsafe provider display name rejected") { p, cr, source ->
                p.seed("../escape", "old")
                expectRejectedListingPreservesTrackedLocal(cr)
                expectFailure { FixtureSafTreeWalker.listTree(cr, tree, "root") }
                expectFailure { productionCopy(cr, source) }
            }
            test("duplicate display names rejected") { p, cr, source ->
                p.seed("same", "a")
                p.seed("same", "b")
                expectRejectedListingPreservesTrackedLocal(cr)
                expectFailure { FixtureSafTreeWalker.listTree(cr, tree, "root") }
                expectFailure { FixtureSafTreeWalker.findFileEntry(cr, tree, "root", "same") }
                expectFailure { productionCopy(cr, source) }
            }
            test("repeated provider row rejected") { p, cr, _ ->
                p.seed("same", "a")
                p.repeatFirstListingRow = true
                expectRejectedListingPreservesTrackedLocal(cr)
            }
            test("listing beyond the traversal limit fails closed") { p, cr, _ ->
                var parent = "root"
                repeat(65) { depth ->
                    parent = p.createDocument(parent, Document.MIME_TYPE_DIR, "depth-$depth")
                }
                expectRejectedListingPreservesTrackedLocal(cr)
            }
            test("revoked permission does not alter destination") { p, cr, source ->
                p.seed("sample.yaml", "old")
                p.denied = true
                expectFailure { copy(cr, source) }
                check(p.file("sample.yaml").readText() == "old")
            }
            test("duplicate directory names rejected before index overwrite") { p, cr, _ ->
                repeat(2) { p.createDocument("root", Document.MIME_TYPE_DIR, "same") }
                expectFailure { FixtureSafTreeWalker.listTree(cr, tree, "root") }
            }
            val own = RimeDataProvider().apply { attachInfo(context, info) }
            val rootId = checkNotNull(context.getExternalFilesDir(null)).name
            check(!own.isChildDocument(rootId, "$rootId-sibling"))
            expectFailure { own.queryDocument("../escape", null) }
            expectFailure { own.createDocument(rootId, "text/plain", "../escape") }
            own.queryDocument(rootId, arrayOf(Document.COLUMN_FLAGS)).use {
                check(it.moveToFirst())
                check(it.getInt(0) and (Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_MOVE) == 0)
            }
            checks += 4
            val ownTree = DocumentsContract.buildTreeDocumentUri(authority, rootId)
            val ownResolver = ResolverWrapper.wrap(own)
            val probeId = own.createDocument(rootId, Document.MIME_TYPE_DIR, ".saf-probe-${UUID.randomUUID()}")
            try {
                val cache = FixtureSafPathCache(FixtureSafTreeListing(emptyList(), mapOf("" to probeId)), probeId)
                val source = File(sandbox, "own-source")
                for (payload in listOf("initial", "updated")) {
                    source.writeText(payload)
                    FixtureAtomicSafFileCopy.copyFromFile(ownResolver, ownTree, cache, source, "config.yaml", "", "config.yaml")
                    val entry = FixtureSafTreeWalker.listFiles(ownResolver, ownTree, probeId).single()
                    val uri = DocumentsContract.buildDocumentUriUsingTree(ownTree, entry.documentId)
                    check(ownResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } == payload)
                }
                checks++
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: Trime provider create, replace and read-back\n") })
            } finally {
                own.deleteDocument(probeId)
            }
            return checks
        } finally {
            sandbox.deleteRecursively()
        }
    }

    private class FixtureProvider(private val directory: File) : DocumentsProvider() {
        private data class Node(val id: String, val parent: String, val name: String, val file: File, val directory: Boolean = false)
        private val nodes = linkedMapOf("root" to Node("root", "", "root", directory, true))
        var canRename = true
        var pipeReads = false
        var failPipeRead = false
        var failWrite = false
        var corruptRead = false
        var corruptPublishedRead = false
        var failPublish = false
        var failRestore = false
        var loading = false
        var listingError: String? = null
        var loadingAfterTraversal = false
        var errorAfterTraversal: String? = null
        var exhaustedListingExtrasReads = 0
            private set
        var nullListing = false
        var repeatFirstListingRow = false
        var denied = false
        fun names() = nodes.values.filter { !it.directory }.map { it.name }
        fun file(name: String) = nodes.values.first { it.name == name }.file
        fun seed(name: String, value: String) {
            createDocument("root", "text/plain", name).let { nodes.getValue(it).file.writeText(value) }
        }
        override fun onCreate() = true
        override fun queryRoots(projection: Array<String>?) = MatrixCursor(projection ?: emptyArray())
        override fun isChildDocument(parentDocumentId: String, documentId: String) = nodes.containsKey(documentId)
        private fun cursor(projection: Array<out String>?, rows: List<Node>, listing: Boolean = false): Cursor {
            if (denied) throw SecurityException("Fixture permission revoked")
            val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
            return object : MatrixCursor(columns) {
                override fun getExtras(): Bundle {
                    val current = super.getExtras()
                    if (!listing || !isAfterLast) return current
                    if (!loadingAfterTraversal && errorAfterTraversal == null) return current
                    exhaustedListingExtrasReads++
                    return Bundle(current).apply {
                        if (loadingAfterTraversal) putBoolean(DocumentsContract.EXTRA_LOADING, true)
                        errorAfterTraversal?.let { putString(DocumentsContract.EXTRA_ERROR, it) }
                    }
                }
            }.apply {
                extras =
                    Bundle().apply {
                        putBoolean(DocumentsContract.EXTRA_LOADING, loading)
                        listingError?.let { putString(DocumentsContract.EXTRA_ERROR, it) }
                    }
                rows.forEach { node ->
                    addRow(
                        columns.map { column ->
                            when (column) {
                                Document.COLUMN_DOCUMENT_ID -> node.id

                                Document.COLUMN_DISPLAY_NAME -> node.name

                                Document.COLUMN_MIME_TYPE -> if (node.directory) Document.MIME_TYPE_DIR else "text/plain"

                                Document.COLUMN_SIZE -> if (pipeReads) null else node.file.length()

                                Document.COLUMN_LAST_MODIFIED -> if (pipeReads) null else node.file.lastModified()

                                Document.COLUMN_FLAGS ->
                                    Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or
                                        Document.FLAG_DIR_SUPPORTS_CREATE or (if (canRename) Document.FLAG_SUPPORTS_RENAME else 0)

                                else -> null
                            }
                        }.toTypedArray<Any?>(),
                    )
                }
            }
        }
        override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(projection, listOf(nodes.getValue(documentId)))
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, sortOrder: String?) = if (nullListing) {
            null
        } else {
            val rows = nodes.values.filter { it.parent == parentDocumentId }
            cursor(projection, if (repeatFirstListingRow && rows.isNotEmpty()) rows + rows.first() else rows, listing = true)
        }
        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            val id = UUID.randomUUID().toString()
            val dir = mimeType == Document.MIME_TYPE_DIR
            val file = File(directory, id)
            if (dir) file.mkdir() else file.createNewFile()
            nodes[id] = Node(id, parentDocumentId, displayName, file, dir)
            return id
        }
        override fun renameDocument(documentId: String, displayName: String): String {
            val node = nodes.getValue(documentId)
            if (!canRename || (
                    displayName == "sample.yaml" &&
                        ((failPublish && node.name.endsWith(".tmp")) || (failRestore && node.name.endsWith(".bak")))
                    )
            ) {
                throw FileNotFoundException("Injected rename failure")
            }
            val id = UUID.randomUUID().toString()
            nodes.remove(documentId)
            nodes[id] = node.copy(id = id, name = displayName)
            return id
        }
        override fun deleteDocument(documentId: String) {
            nodes.remove(documentId)?.file?.delete()
        }
        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
            val node = nodes.getValue(documentId)
            if (mode.contains('w') && failWrite) throw FileNotFoundException("Injected write failure")
            if (
                mode == "r" &&
                (
                    (corruptRead && node.name.endsWith(".tmp")) ||
                        (corruptPublishedRead && node.name == "sample.yaml")
                    )
            ) {
                node.file.writeText("truncated")
            }
            if (mode == "r" && pipeReads) {
                val pipe = ParcelFileDescriptor.createReliablePipe()
                thread(name = "saf-fixture-pipe") {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                        node.file.inputStream().use { it.copyTo(output) }
                        if (failPipeRead) pipe[1].closeWithError("Injected provider read failure")
                    }
                }
                return pipe[0]
            }
            return ParcelFileDescriptor.open(node.file, ParcelFileDescriptor.parseMode(mode))
        }
    }
}
