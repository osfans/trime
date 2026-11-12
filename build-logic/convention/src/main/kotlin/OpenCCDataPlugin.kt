/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

class OpenCCDataPlugin : Plugin<Project> {
    companion object {
        const val DOWNLOAD_TASK = "downloadOpenCCData"
        const val INSTALL_TASK = "installOpenCCData"
        const val CLEAN_TASK = "cleanOpenCCData"
    }

    private val Project.openccDir
        get() = file("src/main/jni/librime/deps/opencc")

    private val Project.dataConfigDir
        get() = openccDir.resolve("data/config")

    private val Project.dataInstallDir
        get() = assetsDir.resolve("shared/opencc")

    private val Project.openccResourcesZip
        get() = layout.buildDirectory.file("opencc/opencc-resources.zip").get().asFile

    private fun Project.openccGit(vararg args: String): Provider<String> = providers
        .exec {
            commandLine("git", "-C", openccDir.absolutePath, *args)
            setIgnoreExitValue(true)
        }.standardOutput.asText
        .map { it.trim() }

    private val Project.openccTag
        get() = openccGit("describe", "--tags", "--abbrev=0", "--match", "ver.*")

    /** The web URL of the repository the OpenCC submodule was cloned from. */
    private val Project.openccRepositoryUrl
        get() =
            openccGit("config", "--get", "remote.origin.url").map { remote ->
                when {
                    remote.isEmpty() -> ""

                    remote.startsWith("git@") ->
                        "https://" + remote.removePrefix("git@").replaceFirst(':', '/').removeSuffix(".git")

                    else -> remote.removeSuffix(".git")
                }
            }

    override fun apply(target: Project) {
        val download = registerDownloadTask(target)
        registerInstallTask(target, download)
        registerCleanTask(target)
    }

    private fun registerDownloadTask(project: Project): TaskProvider<DownloadOpenCCDataTask> {
        // Offline/F-Droid builds can use a local copy.
        val localZipOverride =
            project.providers.gradleProperty("openccDataZip")
                .orElse(project.providers.environmentVariable("OPENCC_DATA_ZIP"))
        return project.tasks.register<DownloadOpenCCDataTask>(DOWNLOAD_TASK) {
            releaseTag.set(project.openccTag)
            repositoryUrl.set(project.openccRepositoryUrl)
            outputZip.set(project.openccResourcesZip)
            localZipOverride.orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { localZip.set(project.rootProject.file(it)) }
        }
    }

    private fun registerInstallTask(
        project: Project,
        download: TaskProvider<DownloadOpenCCDataTask>,
    ) {
        val install =
            project.tasks.register<Sync>(INSTALL_TASK) {
                dependsOn(download)
                from(project.dataConfigDir) {
                    include("*.json")
                    exclude("*.schema.json")
                }
                from(project.zipTree(project.openccResourcesZip)) {
                    include("*.txt")
                }
                into(project.dataInstallDir)
                doLast {
                    val dir = project.dataInstallDir
                    val configs = dir.listFiles { f -> f.extension == "json" }.orEmpty()
                    val dictionaries = dir.listFiles { f -> f.extension == "txt" }.orEmpty()
                    logger.lifecycle(
                        "Installed ${configs.size} OpenCC configs and ${dictionaries.size} dictionaries to $dir",
                    )
                }
            }
        project.tasks.getByName(DataChecksumsPlugin.TASK).dependsOn(install)
    }

    private fun registerCleanTask(project: Project) {
        project
            .tasks.register<Delete>(CLEAN_TASK) {
                delete(project.dataInstallDir)
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }

    abstract class DownloadOpenCCDataTask : DefaultTask() {
        @get:Input
        abstract val releaseTag: Property<String>

        @get:Input
        abstract val repositoryUrl: Property<String>

        @get:Optional
        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val localZip: RegularFileProperty

        @get:OutputFile
        abstract val outputZip: RegularFileProperty

        @TaskAction
        fun execute() {
            val target = outputZip.get().asFile
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.part")
            temp.delete()
            try {
                val source = localZip.orNull?.asFile
                if (source != null) {
                    logger.lifecycle("Using local OpenCC resource archive: $source")
                    source.copyTo(temp, overwrite = true)
                } else {
                    val url = releaseArchiveUrl()
                    logger.lifecycle("Downloading OpenCC resource archive: $url")
                    download(url, temp)
                }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.lifecycle("OpenCC resource archive ready: $target")
            } finally {
                temp.delete()
            }
        }

        /** The release archive of the OpenCC version the submodule is tagged with. */
        private fun releaseArchiveUrl(): String {
            val tag = releaseTag.get()
            check(tag.isNotEmpty()) {
                "Unable to determine the OpenCC release tag from the submodule; " +
                    "provide the official resource archive with -PopenccDataZip."
            }
            val repository = repositoryUrl.get()
            check(repository.isNotEmpty()) {
                "Unable to determine the OpenCC repository URL from the submodule; " +
                    "provide the official resource archive with -PopenccDataZip."
            }
            val version = tag.removePrefix("ver.")
            return "$repository/releases/download/$tag/opencc-v$version-resources.zip"
        }

        private fun download(
            url: String,
            destination: File,
        ) {
            val client =
                HttpClient
                    .newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build()
            val request =
                HttpRequest
                    .newBuilder(URI(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build()
            val response =
                try {
                    client.send(request, HttpResponse.BodyHandlers.ofFile(destination.toPath()))
                } catch (e: IOException) {
                    throw GradleException("Failed to download OpenCC resources from $url: ${e.message}", e)
                }
            check(response.statusCode() == 200) {
                "Failed to download OpenCC resources from $url: HTTP ${response.statusCode()}"
            }
        }
    }
}
