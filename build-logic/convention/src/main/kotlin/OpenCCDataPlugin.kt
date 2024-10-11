/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.task

class OpenCCDataPlugin : Plugin<Project> {
    companion object {
        const val INSTALL_TASK = "installOpenCCData"
        const val CLEAN_TASK = "cleanOpenCCData"
    }

    private val Project.dataBaseDir
        get() = file("src/main/jni/OpenCC/data")

    private val Project.dataInstallDir
        get() = assetsDir.resolve("shared/opencc")

    override fun apply(target: Project) {
        registerInstallTask(target)
        registerCleanTask(target)
    }

    private fun registerInstallTask(project: Project) {
        val task =
            project.task<InstallOpenCCDataTask>(INSTALL_TASK) {
                inputDir.set(project.dataBaseDir)
                outputDir.set(project.dataInstallDir)
            }
        // make sure OpenCC data have been installed before generating data checksums
        project.tasks.getByName(DataChecksumsPlugin.TASK).dependsOn(task)
    }

    private fun registerCleanTask(project: Project) {
        project
            .task<Delete>(CLEAN_TASK) {
                delete(project.dataInstallDir)
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }

    abstract class InstallOpenCCDataTask : DefaultTask() {
        @get:PathSensitive(PathSensitivity.NAME_ONLY)
        @get:InputDirectory
        abstract val inputDir: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        private val input by lazy { inputDir.get().asFile }

        private val output by lazy { outputDir.get().asFile }

        companion object {
            private val DICTS_RAW =
                arrayOf(
                    "STCharacters",
                    "STPhrases",
                    "TSCharacters",
                    "TSPhrases",
                    "TWVariants",
                    "TWVariantsRevPhrases",
                    "HKVariants",
                    "HKVariantsRevPhrases",
                    "JPVariants",
                    "JPShinjitaiCharacters",
                    "JPShinjitaiPhrases",
                )

            private val DICTS_GENERATED = arrayOf("TWPhrases", "TWPhrasesRev", "TWVariantsRev", "HKVariantsRev", "JPVariantsRev")
        }

        @TaskAction
        fun execute() {
            input.run {
                resolve("config")
                    .listFiles { f -> f.extension == "json" }
                    ?.forEach { f -> f.copyTo(output.resolve(f.name)) }

                val dictionary = resolve("dictionary")
                for (raw in DICTS_RAW) {
                    val basename = "$raw.txt"
                    dictionary.resolve(basename).copyTo(output.resolve(basename))
                }

                val merge = resolve("scripts/merge.py").absolutePath
                val reverse = resolve("scripts/reverse.py").absolutePath

                fun merge(
                    sources: List<String>,
                    outputFilePath: String,
                ) {
                    project.exec {
                        workingDir = output
                        commandLine = listOf("python3", merge) + sources + outputFilePath
                    }
                }

                fun reverse(
                    source: String,
                    outputFilePath: String,
                ) {
                    project.exec {
                        workingDir = output
                        commandLine = listOf("python3", reverse, source, outputFilePath)
                    }
                }
                for (generated in DICTS_GENERATED) {
                    val outputFile = output.resolve("$generated.txt")
                    if (generated == "TWPhrases") {
                        val sources =
                            arrayOf("TWPhrasesIT", "TWPhrasesName", "TWPhrasesOther").map {
                                dictionary.resolve("$it.txt").absolutePath
                            }
                        merge(sources, outputFile.name)
                    } else {
                        val sourceName = generated.substringBefore("Rev")
                        val source =
                            if (sourceName == "TWPhrases") {
                                output
                            } else {
                                dictionary
                            }.resolve("$sourceName.txt")
                        reverse(source.absolutePath, outputFile.name)
                    }
                }
            }
        }
    }
}
