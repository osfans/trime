/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

private val isWindows = System.getProperty("os.name")?.contains("win", ignoreCase = true) == true

/**
 * Installs OpenCC's dictionaries and configs into the app's assets.
 *
 * The data is produced by a host build of the bundled OpenCC source instead of
 * being reimplemented here: upstream's own CMake rules generate the dictionaries
 * (including the ones derived by Python scripts and the one that needs a runnable
 * `opencc`) and install them into a private prefix, which is then synced into the
 * assets. Bumping the submodule needs no change to this plugin as long as
 * upstream keeps installing its data.
 */
class OpenCCDataPlugin : Plugin<Project> {
    companion object {
        const val EXTENSION = "openccData"
        const val BUILD_TASK = "buildHostOpenCCData"
        const val INSTALL_TASK = "installOpenCCData"
        const val CLEAN_TASK = "cleanOpenCCData"
    }

    abstract class OpenCCDataExtension {
        /** Defaults to the SDK's copy, then PATH. */
        abstract val cmakeExecutable: Property<String>

        /** Defaults to the copy shipped with cmake, then PATH. */
        abstract val ninjaExecutable: Property<String>

        /** Defaults to `python3`, then `python` on PATH. */
        abstract val pythonExecutable: Property<String>

        /** `ocd2`, `ocd` or `text`. */
        abstract val dictFormat: Property<String>

        /** Appended to the host CMake configure command. */
        abstract val extraArgs: ListProperty<String>
    }

    private val Project.openccSourceDir
        get() = file("src/main/jni/librime/deps/opencc")

    private val Project.dataInstallDir
        get() = assetsDir.resolve("shared/opencc")

    private val Project.hostBuildRoot
        get() = layout.buildDirectory.dir("opencc")

    override fun apply(target: Project) {
        val extension = target.extensions.create<OpenCCDataExtension>(EXTENSION)
        extension.cmakeExecutable.convention(target.envOrPropOrNull("HOST_CMAKE", "hostCmake"))
        extension.ninjaExecutable.convention(target.envOrPropOrNull("HOST_NINJA", "hostNinja"))
        extension.pythonExecutable.convention(target.envOrPropOrNull("HOST_PYTHON", "hostPython"))
        extension.dictFormat.convention(
            target.envOrPropOrNull("OPENCC_DICT_FORMAT", "openccDictFormat") ?: "ocd2",
        )
        extension.extraArgs.convention(emptyList())

        val buildTask = registerBuildTask(target, extension)
        registerInstallTask(target, buildTask)
        registerCleanTask(target)
    }

    private fun registerBuildTask(
        project: Project,
        extension: OpenCCDataExtension,
    ): TaskProvider<BuildHostOpenCCDataTask> {
        val buildRoot = project.hostBuildRoot
        val sdkDirectory = project.androidSdkDirectory
        return project.tasks.register<BuildHostOpenCCDataTask>(BUILD_TASK) {
            sourceDir.set(project.openccSourceDir)
            // the files configure, the build and the generators read; the
            // bindings, docs and unused test frameworks are not among them
            sources.from(
                project.fileTree(project.openccSourceDir) {
                    exclude(
                        ".git/**",
                        "doc/**",
                        "examples/**",
                        "node/**",
                        "packaging/**",
                        "python*/**",
                        "test/**",
                        "deps/google-benchmark/**",
                        "deps/googletest*/**",
                        "deps/pybind11*/**",
                    )
                },
            )
            if (sdkDirectory != null) {
                this.sdkDirectory.set(sdkDirectory)
            }
            cmakeVersion.set(project.cmakeVersion)
            cmakeExecutable.set(extension.cmakeExecutable)
            ninjaExecutable.set(extension.ninjaExecutable)
            pythonExecutable.set(extension.pythonExecutable)
            dictFormat.set(extension.dictFormat)
            extraArgs.set(extension.extraArgs)
            hostBuildDir.set(buildRoot.map { it.dir("host") })
            installPrefix.set(buildRoot.map { it.dir("host-prefix") })
        }
    }

    private fun registerInstallTask(
        project: Project,
        buildTask: TaskProvider<BuildHostOpenCCDataTask>,
    ) {
        // syncing the whole prefix also drops dictionaries removed upstream
        val installTask =
            project.tasks.register<Sync>(INSTALL_TASK) {
                from(buildTask.flatMap { it.installPrefix.dir("share/opencc") })
                into(project.dataInstallDir)
            }
        project.tasks.getByName(DataChecksumsPlugin.TASK).dependsOn(installTask)
    }

    private fun registerCleanTask(project: Project) {
        project
            .tasks.register<Delete>(CLEAN_TASK) {
                // the host build tree sits in the build directory and goes with it
                delete(project.dataInstallDir)
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }

    abstract class BuildHostOpenCCDataTask : DefaultTask() {
        @get:Inject
        abstract val execOperations: ExecOperations

        @get:Internal
        abstract val sourceDir: DirectoryProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sources: ConfigurableFileCollection

        @get:Input
        @get:Optional
        abstract val sdkDirectory: Property<String>

        @get:Input
        abstract val cmakeVersion: Property<String>

        @get:Input
        @get:Optional
        abstract val cmakeExecutable: Property<String>

        @get:Input
        @get:Optional
        abstract val ninjaExecutable: Property<String>

        @get:Input
        @get:Optional
        abstract val pythonExecutable: Property<String>

        @get:Input
        abstract val dictFormat: Property<String>

        @get:Input
        abstract val extraArgs: ListProperty<String>

        /** Incremental build tree, maintained by CMake itself. */
        @get:Internal
        abstract val hostBuildDir: DirectoryProperty

        @get:OutputDirectory
        abstract val installPrefix: DirectoryProperty

        companion object {
            private val DICTIONARY_REFERENCE = Regex("\"file\"\\s*:\\s*\"([^\"]+)\"")
            private val DICTIONARY_EXTENSIONS = listOf(".ocd2", ".ocd", ".txt")
        }

        @TaskAction
        fun execute() {
            val source = sourceDir.get().asFile
            val build = hostBuildDir.get().asFile
            val prefix = installPrefix.get().asFile
            val cmake = resolveCmake()
            val python = resolvePython()

            configure(cmake, source, build, prefix, python)
            buildDictionaries(cmake, build)
            installData(cmake, build, prefix)
            verifyInstalled(prefix.resolve("share/opencc"))
        }

        private fun configure(
            cmake: String,
            source: File,
            build: File,
            prefix: File,
            python: String?,
        ) {
            val ninja = resolveNinja(cmake)
            logger.info("OpenCC host build: cmake=$cmake ninja=$ninja python=$python")

            val buildType = if (isWindows) emptyList() else listOf("-DCMAKE_BUILD_TYPE=Release")
            // Ninja is missing on some build hosts, the fallback is whatever
            // generator CMake picks by default
            val generator =
                ninja?.let { listOf("-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=$it") }.orEmpty()
            val pythonArg = python?.let { listOf("-DPython3_EXECUTABLE=$it") }.orEmpty()
            run(
                cmake,
                listOf("-S", source.absolutePath, "-B", build.absolutePath) +
                    buildType +
                    generator +
                    listOf(
                        "-DCMAKE_INSTALL_PREFIX=${prefix.absolutePath}",
                        // macOS enables the first two for a top level project, and
                        // only the data is wanted here
                        "-DBUILD_SHARED_LIBS=OFF",
                        "-DBUILD_OPENCC_JIEBA_PLUGIN=OFF",
                        "-DBUILD_DOCUMENTATION=OFF",
                        "-DBUILD_PYTHON=OFF",
                        "-DENABLE_GTEST=OFF",
                        "-DENABLE_BENCHMARK=OFF",
                        "-DOPENCC_ENABLE_INSTALL=ON",
                        "-DOPENCC_DICT_FORMAT=${dictFormat.get()}",
                    ) +
                    pythonArg +
                    extraArgs.get(),
            )
        }

        private fun buildDictionaries(
            cmake: String,
            build: File,
        ) {
            // opencc_dict compiles the dictionaries, opencc itself converts the
            // phrases they are derived from
            val targets =
                listOf("opencc_dict", "Dictionaries") +
                    // another format needs its configs rewritten to reference it
                    if (dictFormat.get() == "ocd2") emptyList() else listOf("ConvertedConfigs")
            val release = if (isWindows) listOf("--config", "Release") else emptyList()
            run(
                cmake,
                listOf("--build", build.absolutePath, "--target") +
                    targets +
                    listOf("--parallel", Runtime.getRuntime().availableProcessors().toString()) +
                    release,
            )
        }

        private fun installData(
            cmake: String,
            build: File,
            prefix: File,
        ) {
            // this script installs the dictionaries and configs only, while
            // `--target install` would install the command line tools too
            val script = build.resolve("data/cmake_install.cmake")
            check(script.isFile) {
                "OpenCC no longer generates data/cmake_install.cmake in ${build.path}. " +
                    "Its install rules changed, update OpenCCDataPlugin to match."
            }

            // Gradle does not clean an @OutputDirectory per file, so dictionaries
            // dropped by an OpenCC bump would otherwise reach the assets
            prefix.deleteRecursively()
            val release = if (isWindows) listOf("-DCMAKE_INSTALL_CONFIG_NAME=Release") else emptyList()
            run(
                cmake,
                listOf("-DCMAKE_INSTALL_PREFIX=${prefix.absolutePath}") +
                    release +
                    listOf("-P", script.absolutePath),
            )
        }

        /**
         * A config naming a missing dictionary makes OpenCC fail to load it and
         * silently disables the conversion in librime.
         */
        private fun verifyInstalled(output: File) {
            val configs = output.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty()
            check(configs.isNotEmpty()) { "OpenCC installed no configs into ${output.path}" }

            val missing =
                configs.flatMap { config ->
                    DICTIONARY_REFERENCE
                        .findAll(config.readText())
                        .map { it.groupValues[1] }
                        .filter { name -> DICTIONARY_EXTENSIONS.any { name.endsWith(it) } }
                        .filterNot { name -> output.resolve(name).isFile }
                        .map { name -> "${config.name} -> $name" }
                        .toList()
                }
            check(missing.isEmpty()) {
                "OpenCC configs reference dictionaries which were not installed:\n" +
                    missing.joinToString("\n") { "  $it" } +
                    "\nCheck whether the install rules of the bundled OpenCC changed."
            }
        }

        private fun resolveCmake(): String {
            cmakeExecutable.orNotBlank()?.let { return it }
            sdkExecutable("cmake")?.let { return it }
            findOnPath("cmake")?.let { return it }

            throw GradleException(
                "OpenCC's dictionaries are generated by a host build, which needs CMake " +
                    "and a C++ toolchain.\n" +
                    "Install them with `sdkmanager \"cmake;${cmakeVersion.get()}\"`, put cmake " +
                    "on PATH, or set HOST_CMAKE (or -PhostCmake=<path>).",
            )
        }

        private fun resolveNinja(cmake: String): String? = ninjaExecutable.orNotBlank()
            // the SDK, and CMake's own builds, ship ninja next to cmake
            ?: File(cmake).resolveSibling(executableName("ninja")).takeIf { it.isFile }?.absolutePath
            ?: findOnPath("ninja")

        private fun resolvePython(): String? = pythonExecutable.orNotBlank() ?: findOnPath("python3") ?: findOnPath("python")

        private fun sdkExecutable(name: String): String? {
            val sdk = sdkDirectory.orNull ?: return null
            val root = File(sdk, "cmake")
            val preferred = cmakeVersion.get()
            // the version the Android plugin asks for may not be the one that was
            // installed, e.g. the F-Droid build server pins its own
            val versions =
                listOf(preferred) +
                    root.listFiles { file -> file.isDirectory }
                        ?.map { it.name }
                        ?.filterNot { it == preferred }
                        ?.sorted()
                        .orEmpty()
            return versions
                .asSequence()
                .map { File(File(root, it), "bin/${executableName(name)}") }
                .firstOrNull { it.isFile }
                ?.absolutePath
        }

        private fun findOnPath(name: String): String? {
            val executable = executableName(name)
            return System.getenv("PATH")
                ?.split(File.pathSeparatorChar)
                ?.asSequence()
                ?.filter { it.isNotBlank() }
                ?.map { File(it, executable) }
                ?.firstOrNull { it.isFile }
                ?.absolutePath
        }

        private fun executableName(name: String) = if (isWindows) "$name.exe" else name

        private fun Property<String>.orNotBlank() = orNull?.takeIf { it.isNotBlank() }

        private fun run(
            cmake: String,
            args: List<String>,
        ) {
            execOperations
                .exec {
                    commandLine = listOf(cmake) + args
                }.assertNormalExitValue()
        }
    }
}
