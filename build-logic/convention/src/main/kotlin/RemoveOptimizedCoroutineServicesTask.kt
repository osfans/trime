/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "Fail closed against actual post-R8 DEX and pinned internal AGP artifacts")
abstract class RemoveOptimizedCoroutineServicesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputResources: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val originalResources: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val r8Configuration: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexDirectories: ListProperty<Directory>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val coroutineArtifacts: ConfigurableFileCollection

    @get:Input abstract val coroutineCoordinates: ListProperty<String>

    @get:Input abstract val agpVersion: Property<String>

    @get:OutputFile abstract val outputResources: RegularFileProperty

    @TaskAction
    fun removeOptimizedDescriptors() {
        require(agpVersion.get() == OptimizedCoroutineServices.AGP_VERSION) { "Unreviewed AGP version" }
        val versions = javaClass.classLoader.getResources("r8-version.properties").toList().map { resource ->
            Properties().apply { resource.openStream().use { load(it) } }
        }
        require(
            versions.isNotEmpty() && versions.all {
                it.getProperty("version.version") == OptimizedCoroutineServices.R8_VERSION &&
                    it.getProperty("version.sha") == OptimizedCoroutineServices.R8_COMMIT
            },
        ) { "Unreviewed R8 compiler version" }
        require(
            coroutineCoordinates.get().toSet() == setOf(
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:${OptimizedCoroutineServices.COROUTINES_VERSION}",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${OptimizedCoroutineServices.COROUTINES_VERSION}",
            ),
        ) { "Unreviewed coroutine runtime dependencies" }
        val jars = coroutineArtifacts.files
        require(jars.size == 2) { "Expected the actual Android and core coroutine runtime JARs" }
        val android = jars.single { file ->
            ZipFile(file).use { it.getEntry("META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory") != null }
        }
        val core = (jars - android).single()
        fun sha256(file: java.io.File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        require(
            sha256(android) == "c2cb206d27017c7d1bf5ff179787397543d13748dbabb0d7237e1585e0b29044" &&
                sha256(core) == "d1d75aa01dffbb4d1c520e67e4c4e7f5f6174718e7cb4632412503f2f0e604fa",
        ) {
            "The reviewed raw coroutine runtime JARs changed"
        }
        OptimizedCoroutineServices.verifyOriginalServices(android)
        OptimizedCoroutineServices.verifyOriginalServices(originalResources.get().asFile)
        OptimizedCoroutineServices.verifyConsumerRules(android, core, r8Configuration.get().asFile.readText())
        val dex = dexDirectories.get().flatMap { directory ->
            directory.asFile.walkTopDown().filter { it.isFile && it.extension == "dex" }.toList()
        }.sortedBy { it.absolutePath }
        logger.lifecycle(OptimizedCoroutineServices.filter(inputResources.get().asFile, outputResources.get().asFile, dex))
    }
}
