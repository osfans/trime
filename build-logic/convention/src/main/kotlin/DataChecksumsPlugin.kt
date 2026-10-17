// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.ApplicationExtension
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.task
import java.io.File
import java.security.MessageDigest
import kotlin.collections.set

/**
 * Add task generateDataChecksums
 */
class DataChecksumsPlugin : Plugin<Project> {
    companion object {
        const val TASK = "generateDataChecksums"
        const val CLEAN_TASK = "cleanDatacheksums"
        const val FILE_NAME = "checksums.json"
    }

    override fun apply(target: Project) {
        val prepared = target.layout.buildDirectory.dir("generated/rimeAssets")
        val checksums = target.layout.buildDirectory.dir("generated/rimeChecksums")
        val prepare = target.tasks.register<PrepareRimeAssetsTask>("prepareRimeAssets") {
            inputDir.set(target.assetsDir)
            outputDir.set(prepared)
            reportDir.set(target.layout.buildDirectory.dir("reports/rime-assets"))
            dependsOn(OpenCCDataPlugin.INSTALL_TASK)
        }
        target.extensions.getByType<ApplicationExtension>().sourceSets.getByName("main").assets.directories.apply {
            clear()
            add(prepared.get().asFile.absolutePath)
            add(checksums.get().asFile.absolutePath)
        }
        target.tasks.register<DataChecksumsTask>(TASK) {
            dependsOn(prepare)
            inputDir.set(prepared)
            outputFile.set(checksums.map { it.file(FILE_NAME) })
        }
        target.tasks.register<Delete>(CLEAN_TASK) {
            delete(target.assetsDir.resolve(FILE_NAME))
        }.also {
            target.tasks.findByName("clean")?.dependsOn(it)
        }
    }

    abstract class DataChecksumsTask : DefaultTask() {
        @Serializable
        data class DataChecksums(
            val sha256: String,
            val files: Map<String, String>,
        )

        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:InputDirectory
        abstract val inputDir: DirectoryProperty

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

        private val file by lazy { outputFile.get().asFile }

        private fun serialize(files: Map<String, String>) {
            val checksums =
                DataChecksums(
                    digest(files.entries.joinToString { it.key + it.value }.toByteArray(Charsets.UTF_8)),
                    files,
                )
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(checksums))
        }

        companion object {
            private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

            private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

            fun sha256(file: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                return digest.digest().hex()
            }
        }

        @TaskAction
        fun execute() {
            val root = inputDir.get().asFile
            // Rebuild from current assets so removals/renames cannot leave stale
            // entries in a checksum map restored from a previous build.
            val map = sortedMapOf<String, String>()
            root.walkTopDown().filter { it != root }.forEach { entry ->
                val path = entry.relativeTo(root).invariantSeparatorsPath
                map[path] = if (entry.isDirectory) "" else sha256(entry)
            }
            serialize(map)
        }
    }
}
