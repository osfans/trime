/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.com.google.common.hash.Hashing
import org.jetbrains.kotlin.com.google.common.io.ByteSource
import java.io.File

@Suppress("unused")
class NativeCacheHashPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register<NativeCacheHashCalcTask>("calculateNativeCacheHash") {
            cacheCmakeVersion = target.cmakeVersion
            cacheNdkVersion = target.ndkVersion
            cacheAbis = target.buildAbis
            val output = target.providers.exec {
                commandLine = listOf("git", "submodule", "status")
            }
            cacheSubmoduleStatus = output.result.zip(output.standardOutput.asText) { result, text ->
                if (result.exitValue == 0) text.trim() else ""
            }
            cacheCmakeModules = target.fileTree("src/main/jni/cmake")
            cacheJniSources = target.fileTree("src/main/jni/librime_jni")
            cacheCmakeFile = target.file("src/main/jni/CMakeLists.txt")
        }
    }

    abstract class NativeCacheHashCalcTask : DefaultTask() {
        // Capture configuration without retaining Project in the task action.
        // FileTrees stay live and retain their existing iteration order.
        @get:Internal
        lateinit var cacheCmakeVersion: String

        @get:Internal
        lateinit var cacheNdkVersion: String

        @get:Internal
        lateinit var cacheAbis: List<String>

        @get:Internal
        lateinit var cacheSubmoduleStatus: Provider<String>

        @get:Internal
        lateinit var cacheCmakeModules: FileTree

        @get:Internal
        lateinit var cacheJniSources: FileTree

        @get:Internal
        lateinit var cacheCmakeFile: File

        companion object {
            fun sha256(file: File): String = ByteSource.wrap(file.readBytes()).hash(Hashing.sha256()).toString()

            fun sha256(string: String) = ByteSource.wrap(string.encodeToByteArray()).hash(Hashing.sha256()).toString()
        }

        @TaskAction
        fun execute() {
            val magic =
                buildString {
                    appendLine(cacheCmakeVersion)
                    appendLine(cacheNdkVersion)
                    appendLine(cacheAbis.joinToString(","))
                    appendLine(cacheSubmoduleStatus.get())
                    cacheCmakeModules.forEach { module ->
                        appendLine(sha256(module))
                    }
                    cacheJniSources.forEach { source ->
                        appendLine(sha256(source))
                    }
                    appendLine(sha256(cacheCmakeFile))
                }
            val hash = sha256(magic)
            logger.log(LogLevel.DEBUG, "Native Cache Hash: $hash")
            System.getenv("GITHUB_OUTPUT")?.takeIf { it.isNotBlank() }?.let {
                File(it).appendText("native-cache-hash=$hash")
            }
        }
    }
}
