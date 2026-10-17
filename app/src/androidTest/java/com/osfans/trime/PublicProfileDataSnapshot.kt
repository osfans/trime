/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Read-only byte observations for a disposable profile; not an atomic or restorable DB backup. */
internal object PublicProfileDataSnapshot {
    private const val MAX_BYTES = 512L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private const val MAX_DEPTH = 64

    fun capture(
        roots: List<Pair<String, File>>,
        destination: File,
        checkTime: () -> Unit,
    ): JSONObject {
        checkTime()
        check(roots.map { it.first }.distinct().size == roots.size)
        check(roots.all { (name, _) -> name.matches(Regex("[a-z-]+")) })
        check(!destination.exists() && destination.mkdir())
        // Android app-storage parent aliases are valid; no root leaf or child symlink is allowed.
        val sources = roots.map { (name, file) -> name to File(checkNotNull(file.parentFile).canonicalFile, file.name) }
        check(sources.all { (_, file) -> !destination.canonicalPath.startsWith(file.absolutePath + File.separator) && destination.canonicalPath != file.absolutePath })

        fun stat(file: File): StructStat? = try {
            Os.lstat(file.absolutePath)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) null else throw error
        }

        fun identity(value: StructStat): List<Long> = listOf(
            value.st_dev, value.st_ino, value.st_mode.toLong(), value.st_nlink,
            value.st_size, value.st_mtime, value.st_ctime,
        )

        fun scan(copy: Boolean, label: String): JSONArray {
            val rows = JSONArray()
            val metadata = JSONArray()
            var total = 0L
            var entries = 0
            fun visit(root: String, file: File, relative: String, depth: Int) {
                checkTime()
                check(depth <= MAX_DEPTH && relative.length <= 1024 && ++entries <= MAX_ENTRIES)
                val before = stat(file)
                val row = JSONObject().put("root", root).put("path", relative)
                if (before == null) {
                    check(relative.isEmpty()) { "A captured child disappeared" }
                    rows.put(row.put("type", "absent"))
                    return
                }
                check(!OsConstants.S_ISLNK(before.st_mode)) { "Symlink in protected profile data" }
                val copyFile = File(File(destination, root), relative)
                val observation = JSONObject().put("root", root).put("path", relative).put("stat_before", JSONArray(identity(before)))
                when {
                    OsConstants.S_ISDIR(before.st_mode) -> {
                        if (copy) check(copyFile.mkdir())
                        rows.put(row.put("type", "directory"))
                        val children = checkNotNull(file.listFiles()).sortedBy { it.name }
                        children.forEach { child ->
                            check(child.name.isNotEmpty() && child.name !in setOf(".", "..") && '/' !in child.name && '\\' !in child.name)
                            visit(root, child, if (relative.isEmpty()) child.name else "$relative/${child.name}", depth + 1)
                        }
                        check(children.map { it.name } == checkNotNull(file.listFiles()).map { it.name }.sorted()) { "Protected directory changed during capture" }
                    }

                    OsConstants.S_ISREG(before.st_mode) -> {
                        check(before.st_size >= 0 && before.st_size <= MAX_BYTES - total)
                        total += before.st_size
                        val digest = MessageDigest.getInstance("SHA-256")
                        var size = 0L
                        val fd = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW, 0)
                        try {
                            check(identity(Os.fstat(fd)) == identity(before)) { "Protected file changed before open" }
                            val output = if (copy) FileOutputStream(copyFile) else null
                            try {
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    checkTime()
                                    val count = Os.read(fd, buffer, 0, buffer.size)
                                    check(count >= 0)
                                    if (count == 0) break
                                    size += count
                                    check(size <= before.st_size) { "Protected file grew during capture" }
                                    digest.update(buffer, 0, count)
                                    output?.write(buffer, 0, count)
                                }
                                check(size == before.st_size && identity(Os.fstat(fd)) == identity(before)) { "Protected file changed during capture" }
                            } finally {
                                output?.close()
                            }
                        } finally {
                            // Os.open owns this descriptor; API21 FileInputStream(fd) would not close it.
                            Os.close(fd)
                        }
                        rows.put(row.put("type", "file").put("bytes", size).put("sha256", digest.digest().joinToString("") { "%02x".format(it) }))
                    }

                    else -> error("Unsupported protected profile entry")
                }
                val after = checkNotNull(stat(file))
                observation.put("stat_after", JSONArray(identity(after)))
                metadata.put(observation)
                check(identity(before) == identity(after)) { "Protected entry changed during capture" }
                checkTime()
            }
            try {
                sources.forEach { (name, file) -> visit(name, file, "", 0) }
                checkTime()
                return rows
            } finally {
                // Preserve observed/partial evidence even on a failure; never retry or replace a baseline.
                val inventory = rows.toString(2) + "\n"
                val observations = metadata.toString(2) + "\n"
                check(inventory.length <= 8 * 1024 * 1024 && observations.length <= 8 * 1024 * 1024)
                File(destination, "$label.json").writeText(inventory)
                File(destination, "$label-stat.json").writeText(observations)
            }
        }

        val first = scan(copy = true, label = "inventory")
        val second = scan(copy = false, label = "verification")
        check(first.toString() == second.toString()) { "Protected data changed between complete capture and verification" }
        checkTime()
        val manifest = File(destination, "inventory.json")
        return JSONObject().put("directory", destination.absolutePath)
            .put("roots", JSONArray(sources.map { (name, file) -> JSONObject().put("name", name).put("source", file.absolutePath) }))
            .put("inventory_bytes", manifest.length())
            .put("inventory_sha256", MessageDigest.getInstance("SHA-256").digest(manifest.readBytes()).joinToString("") { "%02x".format(it) })
            .put("entries", first.length()).put("complete_scans", 2)
            .put("maximum_bytes_per_scan", MAX_BYTES).put("maximum_entries", MAX_ENTRIES).put("maximum_depth", MAX_DEPTH)
            .put("source_files_modified", false).put("atomic_database_backup", false)
    }
}
