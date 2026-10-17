// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import java.io.File
import java.io.IOException
import java.util.UUID

/** Files are staged before any live source is changed. Backups outlive native activation. */
internal class LocalImportTransaction(private val root: File) {
    companion object {
        const val MAX_STAGED_BYTES = 512L * 1024 * 1024
        private const val MAX_BACKUP_FILES = 4096
    }

    private val workspace = File(checkNotNull(root.parentFile), ".trime-import-${UUID.randomUUID()}.tmp")
    val recoveryDirectory: String get() = workspace.absolutePath
    private val stagedRoot = File(workspace, "incoming")
    private val backupRoot = File(workspace, "original")
    private val runtimeRoot = File(workspace, "runtime")
    private val changes = mutableListOf<Pair<String, Boolean>>()
    private val runtimeSnapshots = mutableListOf<Pair<String, Boolean>>()
    private val createdDirectories = mutableSetOf<File>()
    private var closed = false
    var isCommitted = false
        private set

    init {
        require(root.isDirectory && root.canonicalFile == root.absoluteFile) { "Import root is unavailable or indirect" }
        check(workspace.mkdir()) { "Cannot create an import workspace" }
        try {
            check(stagedRoot.mkdir() && backupRoot.mkdir() && runtimeRoot.mkdir()) { "Cannot initialize an import workspace" }
        } catch (failure: Exception) {
            workspace.deleteRecursively()
            throw failure
        }
    }

    fun stageFile(path: String): File = resolve(stagedRoot, path).also { check(it.parentFile!!.mkdirs() || it.parentFile!!.isDirectory) }

    fun publish(path: String) {
        check(!closed)
        require(changes.none { it.first == path }) { "Duplicate import path" }
        val staged = resolve(stagedRoot, path)
        require(staged.isFile) { "Staged import is missing: $path" }
        val destination = resolve(root, path)
        require(!destination.exists() || destination.isFile) { "Import destination is not a file: $path" }
        val original = destination.exists()
        if (original) {
            val backup = resolve(backupRoot, path)
            check(backup.parentFile!!.mkdirs() || backup.parentFile!!.isDirectory)
            check(destination.renameTo(backup)) { "Cannot retain the previous configuration: $path" }
        }
        changes.add(path to original)
        createParents(destination.parentFile!!)
        check(staged.renameTo(destination)) { "Cannot activate imported file: $path" }
    }

    fun delete(path: String) {
        check(!closed)
        require(changes.none { it.first == path }) { "Duplicate import path" }
        val destination = resolve(root, path)
        require(destination.isFile) { "Orphan is no longer a file: $path" }
        val backup = resolve(backupRoot, path)
        check(backup.parentFile!!.mkdirs() || backup.parentFile!!.isDirectory)
        check(destination.renameTo(backup)) { "Cannot retain removed configuration: $path" }
        changes.add(path to true)
    }

    /** The engine can update these files during activation even when they were not imported. */
    fun snapshotRuntime(additionalSources: Collection<String> = emptyList()) {
        check(runtimeSnapshots.isEmpty())
        var bytes = 0L
        var files = 0
        val snapshots = mutableListOf<Pair<String, Boolean>>()
        val runtimePaths = listOf("build", "opencc", "trash", "user.yaml", "installation.yaml")
        val sourcePaths = additionalSources.distinct().filter { source ->
            runtimePaths.none { source == it || source.startsWith("$it/") }
        }
        (runtimePaths + sourcePaths).forEach { path ->
            val source = resolve(root, path)
            val exists = source.exists()
            snapshots.add(path to exists)
            if (exists) {
                copyTree(source, resolve(runtimeRoot, path)) { size ->
                    bytes += size
                    files++
                    require(bytes <= MAX_STAGED_BYTES && files <= MAX_BACKUP_FILES) { "Deployment backup exceeds its disk budget" }
                }
            }
        }
        runtimeSnapshots.addAll(snapshots)
    }

    /** Native deployment may not silently discard or rewrite validated source configuration. */
    fun verifySources(expected: Map<String, String>) {
        check(!closed)
        expected.forEach { (path, expectedSha) ->
            val source = resolve(root, path)
            check(source.isFile && SyncFingerprint.of(source) == expectedSha) {
                "Native activation changed validated configuration source: $path"
            }
        }
    }

    fun commit() {
        check(!closed)
        closed = true
        isCommitted = true
        // A failed cleanup never authorizes destroying the newly activated configuration.
        workspace.deleteRecursively()
    }

    /** Do not replace files whose native handles could not be closed. */
    fun retainForRecovery(failure: Throwable) {
        closed = true
        failure.addSuppressed(IOException("Configuration recovery backup retained at ${workspace.absolutePath}"))
    }

    fun rollback(failure: Throwable): Boolean {
        if (closed) return true
        var recovered = true
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (error: Exception) {
                recovered = false
                failure.addSuppressed(error)
            }
        }
        changes.asReversed().forEach { (path, existed) ->
            attempt {
                val live = resolve(root, path)
                check(!live.exists() || live.delete()) { "Cannot remove failed import: $path" }
                if (existed) check(resolve(backupRoot, path).renameTo(live)) { "Cannot restore previous file: $path" }
            }
        }
        runtimeSnapshots.forEach { (path, existed) ->
            attempt {
                val live = resolve(root, path)
                check(removeTree(live)) { "Cannot remove failed deployment cache: $path" }
                if (existed) check(resolve(runtimeRoot, path).renameTo(live)) { "Cannot restore deployment cache: $path" }
            }
        }
        createdDirectories.sortedByDescending { it.path.length }.forEach { if (it.list()?.isEmpty() == true) it.delete() }
        closed = true
        if (recovered) {
            workspace.deleteRecursively()
        } else {
            failure.addSuppressed(IOException("Configuration recovery backup retained at ${workspace.absolutePath}"))
        }
        return recovered
    }

    private fun createParents(parent: File) {
        if (parent == root || parent.exists()) return
        createParents(checkNotNull(parent.parentFile))
        check(parent.mkdir()) { "Cannot create import directory" }
        createdDirectories.add(parent)
    }

    private fun resolve(base: File, path: String): File {
        require(SyncRelativePath.normalize(path) == path) { "Non-canonical import path" }
        return SyncRelativePath.resolveContained(base, path)
    }

    private fun copyTree(source: File, destination: File, account: (Long) -> Unit) {
        require(SyncRelativePath.isDirectPath(root, source)) { "Indirect deployment cache cannot be backed up" }
        if (source.isDirectory) {
            check(destination.mkdirs() || destination.isDirectory)
            (source.listFiles() ?: error("Cannot list deployment cache")).forEach { copyTree(it, File(destination, it.name), account) }
        } else {
            require(source.isFile) { "Deployment cache is not a regular file" }
            account(source.length())
            source.inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } }
        }
        destination.setLastModified(source.lastModified())
    }

    private fun removeTree(file: File): Boolean {
        if (!file.exists()) return true
        require(SyncRelativePath.isDirectPath(root, file)) { "Indirect deployment cache cannot be removed" }
        if (file.isDirectory) {
            (file.listFiles() ?: return false).forEach { if (!removeTree(it)) return false }
        }
        return file.delete()
    }
}
