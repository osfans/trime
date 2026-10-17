/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/** Reject an asset set that would make a bundled conversion config fail at runtime. */
internal fun requireOpenCCDictionaryAssets(names: Set<String>, directory: File) {
    val missing = names.filterNot { name -> directory.resolve("$name.txt").let { it.isFile && it.length() > 0L } }
    check(missing.isEmpty()) { "OpenCC packaged dictionaries are missing or empty: ${missing.joinToString()}" }
}

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
        val regionalPhrases = project.tasks.register<GenerateRegionalPhrasesTask>("generateOpenCCRegionalPhrases") {
            sourceDir.set(project.layout.projectDirectory.dir("src/main/jni/OpenCC"))
            adaptationDir.set(project.layout.projectDirectory.dir("src/main/jni/cmake"))
            outputDir.set(project.layout.buildDirectory.dir("opencc-host"))
            cmakeExecutable.set(
                project.providers.gradleProperty("openccCmakeExecutable")
                    .orElse(project.providers.environmentVariable("CMAKE_EXECUTABLE"))
                    .orElse("cmake"),
            )
        }
        val task =
            project.tasks.register<InstallOpenCCDataTask>(INSTALL_TASK) {
                inputDir.set(project.dataBaseDir)
                outputDir.set(project.dataInstallDir)
                regionalPhrasesFile.set(regionalPhrases.flatMap { it.outputDir.file("OpenCC/data/STPhrases_GeneratedFromRegionalPhrases.txt") })
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

    /** Build the matching host converter instead of shipping configs with missing dictionaries. */
    abstract class GenerateRegionalPhrasesTask : DefaultTask() {
        @get:Inject
        abstract val execOperations: ExecOperations

        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:InputDirectory
        abstract val sourceDir: DirectoryProperty

        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:InputDirectory
        abstract val adaptationDir: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @get:Input
        abstract val cmakeExecutable: Property<String>

        @TaskAction
        fun execute() {
            val output = outputDir.get().asFile
            // OpenCC's generated-dictionary rules do not depend on their Python scripts.
            // Gradle tracks those inputs, so a changed input must get a fresh host tree.
            check(!output.exists() || output.deleteRecursively()) { "Cannot clean stale OpenCC host data: $output" }
            val cmake = cmakeExecutable.get()
            execOperations.exec {
                commandLine(
                    cmake, "-S", adaptationDir.get().asFile.resolve("opencc-host").absolutePath,
                    "-B", output.absolutePath, "-DCMAKE_BUILD_TYPE=Release",
                    "-DTRIME_OPENCC_SOURCE_DIR=${sourceDir.get().asFile.absolutePath}",
                )
            }.assertNormalExitValue()
            execOperations.exec {
                commandLine(cmake, "--build", output.absolutePath, "--target", "Dictionaries", "--parallel", "1")
            }.assertNormalExitValue()
            check(output.resolve("OpenCC/data/STPhrases_GeneratedFromRegionalPhrases.txt").isFile) {
                "OpenCC's matching host converter did not generate regional phrases"
            }
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

        @get:PathSensitive(PathSensitivity.NONE)
        @get:InputFile
        abstract val regionalPhrasesFile: RegularFileProperty

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

            // Regex to find "file": "xxx.ocd2" in config JSON
            private val OCD2_FILE_PATTERN = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+\\.ocd2)\"")
        }

        @TaskAction
        fun execute() {
            // Gradle does no per-file cleanup for a whole-@OutputDirectory task, so
            // dictionaries dropped by an OpenCC bump (e.g. JPVariants*) would
            // otherwise linger in the assets forever.
            check(!output.exists() || output.deleteRecursively()) { "Cannot clean stale OpenCC assets: $output" }

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
            val references = referencedDicts(configs)
            references.forEach { name ->
                val generated = GENERATED_DICTS[name]
                val source = dictionary.resolve("$name.txt")
                when {
                    generated != null -> generate(name, generated, dictionary)

                    source.isFile -> source.copyTo(output.resolve("$name.txt"), overwrite = true)

                    name == "STPhrases_GeneratedFromRegionalPhrases" ->
                        regionalPhrasesFile.get().asFile.copyTo(output.resolve("$name.txt"), overwrite = true)

                    else -> unpackaged += name
                }
            }
            check(unpackaged.isEmpty()) {
                "OpenCC configs refer to dictionaries which are not packaged:\n" +
                    unpackaged.joinToString("\n") { "  $it" } +
                    "\nAdd a generator to GENERATED_DICTS, drop the text dictionary into " +
                    "OpenCC's data/dictionary. Every shipped config must be usable."
            }
            requireOpenCCDictionaryAssets(references, output)
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
