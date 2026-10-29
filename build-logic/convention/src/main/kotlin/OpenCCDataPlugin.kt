/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject

/** Select a runnable host architecture, independently of Android's target ABI. */
private fun openccHostCmakeArguments(osName: String, osArch: String, generator: String): List<String> {
    val args = mutableListOf("-DCMAKE_BUILD_TYPE=Release")
    if (osName.startsWith("Windows", ignoreCase = true)) {
        check(generator.isEmpty() || generator.startsWith("Visual Studio")) {
            "OpenCC host data generation requires a native Visual Studio CMake generator on Windows"
        }
        val platform =
            when (osArch.lowercase()) {
                "amd64", "x86_64" -> "x64"
                "aarch64", "arm64" -> "ARM64"
                "x86", "i386", "i486", "i586", "i686" -> "Win32"
                else -> error("Unsupported Windows OpenCC host architecture: $osArch")
            }
        args += listOf("-A", platform)
    } else if (osName.startsWith("Mac", ignoreCase = true) || osName.equals("Darwin", ignoreCase = true)) {
        val architecture =
            when (osArch.lowercase()) {
                "amd64", "x86_64" -> "x86_64"
                "aarch64", "arm64" -> "arm64"
                else -> error("Unsupported macOS OpenCC host architecture: $osArch")
            }
        args += "-DCMAKE_OSX_ARCHITECTURES=$architecture"
        args += "-DCMAKE_OSX_SYSROOT=macosx"
    }
    return args
}

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
        val regionalPhrases =
            project.tasks.register<GenerateRegionalPhrasesTask>("generateOpenCCRegionalPhrases") {
                sourceDir.set(project.layout.projectDirectory.dir("src/main/jni/librime/deps/opencc"))
                cmakeSourceDir.set(project.layout.projectDirectory.dir("src/main/jni/cmake/opencc-host"))
                hostBuildDir.set(project.layout.buildDirectory.dir("opencc-host"))
                outputFile.set(project.layout.buildDirectory.file("generated/opencc/STPhrases_GeneratedFromRegionalPhrases.txt"))
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
                regionalPhrasesFile.set(regionalPhrases.flatMap { it.outputFile })
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

    /** Generate the missing resource with the converter from the same OpenCC source. */
    @DisableCachingByDefault(because = "Host toolchains are external; only local up-to-date checks are supported")
    abstract class GenerateRegionalPhrasesTask : DefaultTask() {
        @get:Inject
        abstract val execOperations: ExecOperations

        @get:Internal
        abstract val sourceDir: DirectoryProperty

        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:InputFiles
        val sourceFiles: FileTree
            get() =
                sourceDir.get().asFileTree.matching {
                    exclude(".git", ".git/**", "**/.git", "**/.git/**")
                }

        @get:PathSensitive(PathSensitivity.RELATIVE)
        @get:InputDirectory
        abstract val cmakeSourceDir: DirectoryProperty

        @get:LocalState
        abstract val hostBuildDir: DirectoryProperty

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

        @get:Input
        abstract val cmakeExecutable: Property<String>

        @get:Input
        val hostOperatingSystem: String
            get() = System.getProperty("os.name")

        @get:Input
        val hostArchitecture: String
            get() = System.getProperty("os.arch")

        @get:Input
        val hostToolchainEnvironment: Map<String, String>
            get() =
                listOf("CC", "CXX", "CXXFLAGS", "CMAKE_GENERATOR", "CMAKE_GENERATOR_PLATFORM", "CMAKE_GENERATOR_TOOLSET", "CMAKE_TOOLCHAIN_FILE")
                    .associateWith { System.getenv(it).orEmpty() }

        @get:Input
        val hostCmakeArguments: List<String>
            get() =
                openccHostCmakeArguments(hostOperatingSystem, hostArchitecture, hostToolchainEnvironment.getValue("CMAKE_GENERATOR"))

        @TaskAction
        fun execute() {
            check(hostToolchainEnvironment.getValue("CMAKE_TOOLCHAIN_FILE").isEmpty()) {
                "Unset CMAKE_TOOLCHAIN_FILE: OpenCC dictionaries need a native host converter, not an Android cross build"
            }
            val source = sourceDir.get().asFile
            check(source.resolve("data/scripts/generate_st_phrases_from_regional_phrases.py").isFile) {
                "Initialize the pinned librime/deps/opencc submodule before generating dictionaries"
            }
            val build = hostBuildDir.get().asFile
            val output = outputFile.get().asFile
            check(!output.exists() || output.delete()) { "Cannot remove stale OpenCC regional dictionary: $output" }
            // Upstream's generated-dictionary rules omit Python script dependencies.
            // A changed Gradle input must therefore start from a fresh host tree.
            check(!build.exists() || build.deleteRecursively()) { "Cannot clean OpenCC host build: $build" }
            val cmake = cmakeExecutable.get()
            execOperations.exec {
                commandLine(
                    listOf(
                        cmake,
                        "-S",
                        cmakeSourceDir.get().asFile.absolutePath,
                        "-B",
                        build.absolutePath,
                        "-DTRIME_OPENCC_SOURCE_DIR=${source.absolutePath}",
                    ) + hostCmakeArguments,
                )
            }.assertNormalExitValue()
            execOperations.exec {
                commandLine(cmake, "--build", build.absolutePath, "--config", "Release", "--target", "Dictionaries", "--parallel", "1")
            }.assertNormalExitValue()
            val generated = build.resolve("opencc/data/STPhrases_GeneratedFromRegionalPhrases.txt")
            val hasEntries =
                generated.isFile && generated.useLines { lines ->
                    lines.any { it.isNotBlank() && !it.startsWith("#") && '\t' in it }
                }
            check(hasEntries) {
                "OpenCC's matching host converter did not generate a nonempty regional dictionary"
            }
            output.parentFile.mkdirs()
            generated.copyTo(output, overwrite = true)
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
                    "OpenCC's data/dictionary. Every shipped config must have its dictionaries."
            }
            check(references.all { name -> output.resolve("$name.txt").let { it.isFile && it.length() > 0 } }) {
                "OpenCC packaged dictionaries are missing or empty"
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
