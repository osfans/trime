/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
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
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

class OpenCCDataPlugin : Plugin<Project> {
    companion object {
        const val INSTALL_TASK = "installOpenCCData"
        const val CLEAN_TASK = "cleanOpenCCData"
    }

    private val Project.dataBaseDir
        get() = file("src/main/jni/librime/deps/opencc/data")

    private val Project.dataInstallDir
        get() = assetsDir.resolve("shared/opencc")

    override fun apply(target: Project) {
        registerInstallTask(target)
        registerCleanTask(target)
    }

    private fun registerInstallTask(project: Project) {
        val task =
            project.tasks.register<InstallOpenCCDataTask>(INSTALL_TASK) {
                inputDir.set(project.dataBaseDir)
                outputDir.set(project.dataInstallDir)
            }
        // make sure OpenCC data have been installed before generating data checksums
        project.tasks.getByName(DataChecksumsPlugin.TASK).dependsOn(task)
    }

    private fun registerCleanTask(project: Project) {
        project
            .tasks.register<Delete>(CLEAN_TASK) {
                delete(project.dataInstallDir)
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }

    abstract class InstallOpenCCDataTask : DefaultTask() {
        @get:Inject
        abstract val execOperations: ExecOperations

        // relative paths plus content hashes: updating OpenCC's configs or
        // dictionaries alone must re-run this task after a submodule bump
        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:InputDirectory
        abstract val inputDir: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        private val input by lazy { inputDir.get().asFile }
        private val output by lazy { outputDir.get().asFile }

        /** A dictionary derived from [source] by running [script] from OpenCC's data/scripts. */
        private data class GeneratedDict(val source: String, val script: String)

        companion object {
            // Dictionaries that cannot be copied verbatim but have to be generated;
            // keyed by the name OpenCC configs refer to them by
            private val GENERATED_DICTS = mapOf(
                "TWVariantsRev" to GeneratedDict("TWVariants", "reverse.py"),
                "HKVariantsRev" to GeneratedDict("HKVariants", "reverse.py"),
                "JPShinjitaiCharactersRev" to GeneratedDict("JPShinjitaiCharacters", "reverse.py"),
                "TSCharactersExt" to GeneratedDict("TSCharacters", "extract_tofu_risk.py"),
            )

            // Dicts referenced by OpenCC configs that cannot be generated at build time
            // (STPhrases_GeneratedFromRegionalPhrases needs a host opencc CLI).
            // Configs using them (s2t, s2tw, s2twp, s2hk, s2hkp) stay broken.
            private val UNAVAILABLE_DICTS = setOf("STPhrases_GeneratedFromRegionalPhrases")

            // Regex to find "file": "xxx.ocd2" in config JSON
            private val OCD2_FILE_PATTERN = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+\\.ocd2)\"")
        }

        @TaskAction
        fun execute() {
            // Gradle does no per-file cleanup for a whole-@OutputDirectory task, so
            // dictionaries dropped by an OpenCC bump (e.g. JPVariants*) would
            // otherwise linger in the assets forever.
            output.deleteRecursively()

            val dictionary = input.resolve("dictionary")
            val configs =
                input.resolve("config")
                    // the config schema is a test artifact; librime ships its own copy
                    .listFiles { f -> f.extension == "json" && !f.name.endsWith(".schema.json") }
                    .orEmpty()
                    .toList()
            check(configs.isNotEmpty()) { "No OpenCC configs found in ${input.resolve("config")}" }
            configs.forEach { it.copyTo(output.resolve(it.name), overwrite = true) }

            // Package exactly the dictionaries the installed configs ask for: the
            // app compiles them to .ocd2 at runtime, and a missing one makes opencc
            // fail to load the whole config, silently disabling conversion in
            // librime. Deriving the set from the configs keeps it from drifting
            // after an OpenCC bump.
            val unpackaged = mutableListOf<String>()
            referencedDicts(configs).forEach { name ->
                val generated = GENERATED_DICTS[name]
                val source = dictionary.resolve("$name.txt")
                when {
                    generated != null -> generate(name, generated, dictionary)
                    source.isFile -> source.copyTo(output.resolve("$name.txt"), overwrite = true)
                    name in UNAVAILABLE_DICTS -> Unit
                    else -> unpackaged += name
                }
            }
            check(unpackaged.isEmpty()) {
                "OpenCC configs refer to dictionaries which are not packaged:\n" +
                    unpackaged.joinToString("\n") { "  $it" } +
                    "\nAdd a generator to GENERATED_DICTS, drop the text dictionary into " +
                    "OpenCC's data/dictionary, or allow-list the name in UNAVAILABLE_DICTS " +
                    "when it cannot be produced at build time."
            }
        }

        /** Every dictionary referenced as `file` by the given configs. */
        private fun referencedDicts(configs: List<File>): Set<String> {
            val referenced = sortedSetOf<String>()
            configs.forEach { config ->
                val matcher = OCD2_FILE_PATTERN.matcher(config.readText())
                while (matcher.find()) {
                    referenced += matcher.group(1).removeSuffix(".ocd2")
                }
            }
            return referenced
        }

        private fun generate(name: String, generated: GeneratedDict, dictionary: File) {
            execOperations.exec {
                workingDir = output
                commandLine = listOf(
                    "python3",
                    input.resolve("scripts/${generated.script}").absolutePath,
                    dictionary.resolve("${generated.source}.txt").absolutePath,
                    "$name.txt",
                )
            }.assertNormalExitValue()
        }
    }
}
