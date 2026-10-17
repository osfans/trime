// SPDX-FileCopyrightText: 2015 - 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.userdict

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

internal fun requireDictionaryName(name: String): String {
    require(name.length in 1..128 && name.matches(Regex("[\\p{L}\\p{N}_-]+(?:\\.[\\p{L}\\p{N}_-]+)*"))) {
        "Invalid user dictionary name"
    }
    return name
}

/** Exact files, not Rime's merging snapshots. Call only while native database handles are closed. */
internal class DictionaryFileStore(
    dataRoot: File,
    backupRoot: File,
) {
    data class Backup(val id: String, val target: String, val created: Long)

    // Android storage parents can be aliases. Resolve the parent, while still
    // refusing a symbolic link in the leaf directory or any dictionary child.
    private fun storageRoot(root: File): File {
        val parent = root.absoluteFile.parentFile?.canonicalFile ?: throw IOException("Invalid dictionary storage directory")
        val resolved = File(parent, root.name)
        if (resolved.canonicalFile != resolved.absoluteFile) throw IOException("Dictionary storage directory cannot be a symbolic link")
        return resolved
    }

    private val dataRoot = storageRoot(dataRoot)
    private val backupRoot = storageRoot(backupRoot)

    private fun contained(root: File, name: String): File {
        require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name)
        val parent = root.canonicalFile
        val file = File(parent, name)
        if (file.canonicalFile != file.absoluteFile || file.parentFile != parent) {
            throw IOException("Dictionary path is outside its storage directory")
        }
        return file
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create dictionary backup directory")
        if (directory.canonicalFile != directory.absoluteFile) throw IOException("Dictionary backup directory cannot be a symbolic link")
    }

    private fun treeFiles(root: File): Map<String, String> {
        val files = linkedMapOf<String, String>()
        fun visit(file: File, relative: String, depth: Int) {
            if (depth > 32 || file.canonicalFile != file.absoluteFile) throw IOException("Unsafe dictionary backup path")
            if (file.isDirectory) {
                files["$relative/"] = "directory"
                val children = file.listFiles() ?: throw IOException("Cannot list dictionary files")
                children.sortedBy { it.name }.forEach { child ->
                    visit(child, if (relative.isEmpty()) child.name else "$relative/${child.name}", depth + 1)
                }
            } else if (file.isFile) {
                files[relative] = digest(file)
            } else {
                throw IOException("Unsupported dictionary file")
            }
        }
        if (root.exists()) visit(root, "", 0)
        return files
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyTree(source: File, destination: File, depth: Int = 0) {
        if (depth > 32 || source.canonicalFile != source.absoluteFile) throw IOException("Unsafe dictionary source")
        if (source.isDirectory) {
            if (!destination.mkdir()) throw IOException("Cannot create dictionary copy")
            (source.listFiles() ?: throw IOException("Cannot list dictionary source")).forEach { child ->
                copyTree(child, File(destination, child.name), depth + 1)
            }
        } else if (source.isFile) {
            source.inputStream().use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        } else {
            throw IOException("Unsupported dictionary source")
        }
    }

    private fun requireSafeTree(file: File, depth: Int = 0) {
        if (depth > 32 || file.canonicalFile != file.absoluteFile) throw IOException("Unsafe dictionary cleanup path")
        if (file.isDirectory) {
            (file.listFiles() ?: throw IOException("Cannot list dictionary cleanup files")).forEach { child ->
                requireSafeTree(child, depth + 1)
            }
        }
    }

    private fun properties(file: File): Properties {
        if (file.canonicalFile != file.absoluteFile || !file.isFile || file.length() > 1024 * 1024) {
            throw IOException("Invalid dictionary backup metadata")
        }
        return Properties().apply { file.inputStream().use(::load) }
    }

    private fun writeProperties(file: File, value: Properties) {
        FileOutputStream(file).use { output ->
            value.store(output, null)
            output.fd.sync()
        }
    }

    private fun metadata(backup: File): Properties {
        val properties = properties(File(backup, "metadata"))
        if (properties.getProperty("version") != "1") throw IOException("Unsupported dictionary backup")
        return properties
    }

    fun capture(targetName: String): Backup {
        ensureDirectory(backupRoot)
        val target = contained(dataRoot, targetName)
        val before = treeFiles(target)
        val id = UUID.randomUUID().toString()
        val staging = contained(backupRoot, ".$id.tmp")
        val destination = contained(backupRoot, id)
        if (!staging.mkdir()) throw IOException("Cannot stage dictionary backup")
        try {
            if (target.exists()) copyTree(target, File(staging, "data"))
            val copied = treeFiles(File(staging, "data"))
            if (copied != before || treeFiles(target) != before) throw IOException("Dictionary changed during backup")
            val created = maxOf(System.currentTimeMillis(), (latest(targetName)?.created ?: 0L) + 1)
            writeProperties(
                File(staging, "metadata"),
                Properties().apply {
                    setProperty("version", "1")
                    setProperty("target", targetName)
                    setProperty("created", created.toString())
                    setProperty("present", target.exists().toString())
                    before.forEach { (path, hash) -> setProperty("file:$path", hash) }
                },
            )
            if (!staging.renameTo(destination)) throw IOException("Cannot publish dictionary backup")
            prune(targetName)
            return Backup(id, targetName, created)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun latest(targetName: String): Backup? = backups(targetName).maxByOrNull { it.created }

    private fun prune(targetName: String) {
        val journal = contained(backupRoot, "pending")
        val pending = if (journal.exists()) properties(journal).getProperty("backup") else null
        backups(targetName).sortedByDescending { it.created }.drop(3).forEach { backup ->
            if (backup.id != pending) {
                val obsolete = contained(backupRoot, backup.id)
                requireSafeTree(obsolete)
                if (!obsolete.deleteRecursively()) throw IOException("Cannot remove an old dictionary backup")
            }
        }
    }

    fun backups(targetName: String): List<Backup> {
        contained(dataRoot, targetName)
        if (!backupRoot.exists()) return emptyList()
        ensureDirectory(backupRoot)
        return (backupRoot.listFiles() ?: throw IOException("Cannot list dictionary backups"))
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .mapNotNull { directory ->
                val meta = metadata(contained(backupRoot, directory.name))
                if (meta.getProperty("target") == targetName) {
                    Backup(directory.name, targetName, meta.getProperty("created").toLong())
                } else {
                    null
                }
            }
    }

    fun restore(backup: Backup) {
        val directory = contained(backupRoot, backup.id)
        val meta = metadata(directory)
        if (meta.getProperty("target") != backup.target) throw IOException("Dictionary backup identity mismatch")
        val expected = meta.stringPropertyNames().filter { it.startsWith("file:") }
            .associate { it.removePrefix("file:") to meta.getProperty(it) }
        val source = File(directory, "data")
        val present = meta.getProperty("present") == "true"
        if (source.exists() != present || treeFiles(source) != expected) throw IOException("Dictionary backup verification failed")
        val target = contained(dataRoot, backup.target)
        val token = UUID.randomUUID().toString()
        val staging = contained(dataRoot, ".dictionary-recovery-$token")
        val previous = contained(dataRoot, ".dictionary-previous-$token")
        val journal = contained(backupRoot, "pending")
        val cleanup = mutableSetOf<String>()
        if (journal.exists()) {
            val state = properties(journal)
            state.getProperty("cleanup", "").split('|').filter { it.isNotEmpty() }.forEach { name ->
                if (!name.matches(Regex("\\.dictionary-(?:recovery|previous)-[0-9a-f-]{36}"))) {
                    throw IOException("Invalid dictionary recovery cleanup path")
                }
                cleanup.add(name)
            }
            cleanup.add(staging.name)
            cleanup.add(previous.name)
            state.setProperty("cleanup", cleanup.joinToString("|"))
            val journalStaging = contained(backupRoot, "pending.tmp")
            writeProperties(journalStaging, state)
            if (!journalStaging.renameTo(journal)) throw IOException("Cannot update dictionary recovery journal")
        }
        try {
            if (present) {
                copyTree(source, staging)
                if (treeFiles(staging) != expected) throw IOException("Dictionary recovery copy is incomplete")
            }
            if (target.exists()) {
                requireSafeTree(target)
                if (!target.renameTo(previous)) throw IOException("Cannot replace current dictionary")
            }
            try {
                if (present && !staging.renameTo(target)) throw IOException("Cannot publish recovered dictionary")
            } catch (failure: Exception) {
                if (previous.exists() && !previous.renameTo(target)) {
                    failure.addSuppressed(IOException("Cannot restore the previous dictionary"))
                }
                throw failure
            }
            if (previous.exists() && !previous.deleteRecursively()) throw IOException("Cannot remove replaced dictionary")
            cleanup.forEach { name ->
                val obsolete = contained(dataRoot, name)
                if (obsolete.exists()) {
                    requireSafeTree(obsolete)
                    if (!obsolete.deleteRecursively()) throw IOException("Cannot remove interrupted dictionary recovery files")
                }
            }
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun beginMutation(backup: Backup) {
        ensureDirectory(backupRoot)
        val journal = contained(backupRoot, "pending")
        if (journal.exists()) throw IOException("An earlier dictionary change needs recovery")
        val staging = contained(backupRoot, "pending.tmp")
        writeProperties(
            staging,
            Properties().apply {
                setProperty("backup", backup.id)
                setProperty("target", backup.target)
                setProperty("created", backup.created.toString())
            },
        )
        if (!staging.renameTo(journal)) throw IOException("Cannot publish dictionary recovery journal")
    }

    fun finishMutation() {
        val journal = contained(backupRoot, "pending")
        if (journal.exists() && !journal.delete()) throw IOException("Cannot finish dictionary recovery journal")
    }

    fun recoverPendingMutation(): Boolean {
        val journal = contained(backupRoot, "pending")
        if (!journal.exists()) {
            cleanInterruptedBackups()
            return false
        }
        val meta = properties(journal)
        restore(Backup(meta.getProperty("backup"), meta.getProperty("target"), meta.getProperty("created").toLong()))
        finishMutation()
        cleanInterruptedBackups()
        return true
    }

    private fun cleanInterruptedBackups() {
        if (!backupRoot.exists()) return
        (backupRoot.listFiles() ?: throw IOException("Cannot list dictionary backups"))
            .filter { it.name.matches(Regex("\\.[0-9a-f-]{36}\\.tmp")) || it.name == "pending.tmp" }
            .forEach { file ->
                val owned = contained(backupRoot, file.name)
                requireSafeTree(owned)
                if (!owned.deleteRecursively()) throw IOException("Cannot remove an interrupted dictionary backup")
            }
    }

    fun <T> mutate(targetName: String, block: () -> T): T {
        recoverPendingMutation()
        val backup = capture(targetName)
        beginMutation(backup)
        return try {
            val result = block()
            finishMutation()
            result
        } catch (failure: Exception) {
            try {
                restore(backup)
                finishMutation()
            } catch (recoveryFailure: Exception) {
                failure.addSuppressed(recoveryFailure)
            }
            throw failure
        }
    }
}
