// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** A binary backport of removing Configuration.visible assignments; see BUILD-PLUGIN-COMPAT.md. */
internal object GradleVisibleBackport {
    private const val OWNER = "org/gradle/api/artifacts/Configuration"
    private const val SETTER = "(Z)Lorg/gradle/api/artifacts/Configuration;"

    private data class PluginJar(val sha256: String, val classes: Map<String, Int>)

    private val rawPlugins = mapOf(
        "gradle-9.4.1.jar" to PluginJar(
            "4021fa6ea76b6fac076dd0bdacbb3e1b76c31f33b6c392e1a49a2e42072196bf",
            mapOf(
                "com/android/build/gradle/internal/coverage/JacocoConfigurations.class" to 1,
                "com/android/build/gradle/internal/dependency/VariantDependenciesBuilder.class" to 8,
                "com/android/build/gradle/internal/TaskManager\$Companion\$createCoreLibraryDesugaringConfig\$1.class" to 1,
                "com/android/build/gradle/internal/dependency/SourceSetManager.class" to 1,
                "com/android/build/gradle/internal/dependency/VariantDependencies\$Companion.class" to 1,
                "com/android/build/gradle/internal/lint/AndroidLintInputs.class" to 1,
                "com/android/build/gradle/internal/plugins/BasePlugin\$Companion\$createAndroidJdkImageConfiguration\$1.class" to 1,
                "com/android/build/gradle/internal/plugins/BasePlugin\$Companion.class" to 3,
                "com/android/build/gradle/internal/plugins/FusedLibraryPlugin\$maybePublishToMaven\$runtimePublication\$1.class" to 1,
                "com/android/build/gradle/internal/plugins/FusedLibraryPlugin\$maybePublishToMaven\$runtimeSourcePublication\$1.class" to 1,
                "com/android/build/gradle/internal/tasks/creationconfig/BaseJavaPreCompileTaskCreationConfig.class" to 1,
                "com/android/build/gradle/internal/tasks/factory/KmpGlobalTaskCreationConfigImpl.class" to 2,
                "com/android/build/gradle/internal/testing/utp/UtpTestUtilsKt\$maybeCreateUtpConfigurations\$1\$1.class" to 1,
                "com/android/build/gradle/internal/testsuites/impl/TestSuiteDependenciesBuilder.class" to 3,
                "com/android/build/gradle/tasks/GetPrefabFromMavenKt\$getPrefabFromMaven\$config\$1.class" to 1,
                "com/android/build/gradle/tasks/KaptCreationAction.class" to 1,
            ),
        ),
        "symbol-processing-gradle-plugin-2.3.11.jar" to PluginJar(
            "8a00502695b7c44a190c12b9b54d6668724b7aa583f48f9657fab437fbcf8fb9",
            mapOf(
                "com/google/devtools/ksp/gradle/KspConfigurations.class" to 2,
                "com/google/devtools/ksp/gradle/KspSubpluginKt.class" to 1,
            ),
        ),
    )

    // Gradle's public classpath transform pipeline has both original-resource and
    // instrumented-class routes. These are exact outputs from Gradle 9.8.1/JDK 17,
    // independently inspected with javap; they contain the same setter sites.
    private val plugins = rawPlugins + mapOf(
        "instrumented-gradle-9.4.1.jar" to rawPlugins.getValue("gradle-9.4.1.jar").copy(
            sha256 = "35811e6a9e6c682680b3657f21706d2bcb717fe510352f8a601451cce224d5c0",
        ),
        "instrumented-symbol-processing-gradle-plugin-2.3.11.jar" to rawPlugins.getValue("symbol-processing-gradle-plugin-2.3.11.jar").copy(
            sha256 = "1410088bae2f9717d78414944950cb4f4a113c9f7540bc527227481bf176b901",
        ),
    )

    fun isTarget(name: String): Boolean {
        if (name in plugins) return true
        require(
            !name.matches(Regex("(?:instrumented-)?gradle-[0-9].*\\.jar")) &&
                !name.startsWith("symbol-processing-gradle-plugin-") &&
                !name.startsWith("instrumented-symbol-processing-gradle-plugin-"),
        ) { "Unexpected build-plugin version or unreviewed instrumentation input: $name" }
        return false
    }

    fun outputPath(name: String): String {
        require(isTarget(name)) { "Only audited build plugins have backport outputs" }
        // Preserve the producer's relative directory and filename pairing. Gradle
        // validates this shape before pairing the resource and instrumented JARs.
        return if (name.startsWith("instrumented-")) "instrumented/$name" else name
    }

    fun patchJar(input: File, output: File): Int {
        val plugin = requireNotNull(plugins[input.name]) { "Unexpected build-plugin artifact: ${input.name}" }
        require(input.isFile && input.canonicalFile != output.canonicalFile) { "Invalid backport input/output" }
        val sha256 = MessageDigest.getInstance("SHA-256")
        input.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                sha256.update(buffer, 0, read)
            }
        }
        val digest = sha256.digest().joinToString("") { "%02x".format(it) }
        require(digest == plugin.sha256) { "Unexpected bytes for ${input.name}; audit the new upstream plugin before backporting" }
        val patched = linkedMapOf<String, ByteArray>()
        val actual = linkedMapOf<String, Int>()
        ZipFile(input).use { archive ->
            val entries = archive.entries().asSequence().toList()
            require(entries.map { it.name }.distinct().size == entries.size) { "Duplicate entries in build-plugin JAR" }
            entries.forEach { entry ->
                require(!entry.name.startsWith("META-INF/") || !entry.name.endsWith(".SF")) { "Cannot modify a signed build-plugin JAR" }
                val original = archive.getInputStream(entry).use { it.readBytes() }
                if (entry.name.endsWith(".class")) {
                    val (bytes, count) = patchClass(original)
                    if (count > 0) {
                        actual[entry.name] = count
                        patched[entry.name] = bytes
                    }
                }
            }
            require(actual == plugin.classes) { "Unexpected obsolete method-call inventory: $actual" }
            check(output.parentFile.isDirectory || output.parentFile.mkdirs()) { "Cannot create backport output directory" }
            ZipOutputStream(output.outputStream().buffered()).use { result ->
                entries.forEach { entry ->
                    result.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    if (!entry.isDirectory) {
                        result.write(patched[entry.name] ?: archive.getInputStream(entry).use { it.readBytes() })
                    }
                    result.closeEntry()
                }
            }
        }
        return actual.values.sum()
    }

    internal fun patchClass(original: ByteArray): Pair<ByteArray, Int> {
        val reader = ClassReader(original)
        val node = ClassNode()
        reader.accept(node, 0)
        var count = 0
        node.methods.forEach { method ->
            method.instructions.toArray().filterIsInstance<MethodInsnNode>().forEach calls@{ call ->
                if (call.owner != OWNER) return@calls
                require(call.name != "isVisible") { "Unexpected read of legacy Configuration.visible" }
                if (call.name == "setVisible") {
                    require(call.opcode == Opcodes.INVOKEINTERFACE && call.desc == SETTER && call.itf) { "Unexpected legacy setter signature" }
                    require(realInstruction(call.previous, false)?.opcode == Opcodes.ICONST_0) { "Only the pinned false assignments can be removed" }
                    require(realInstruction(call.next, true)?.opcode == Opcodes.POP) { "Legacy setter result must be discarded" }
                    // Discard the boolean and leave the original Configuration receiver as the
                    // result. The existing POP then discards it. Stack frames remain identical.
                    method.instructions.set(call, InsnNode(Opcodes.POP))
                    count++
                }
            }
        }
        if (count == 0) return original to 0
        val writer = ClassWriter(reader, 0)
        node.accept(writer)
        return writer.toByteArray() to count
    }

    private fun realInstruction(start: AbstractInsnNode?, forward: Boolean): AbstractInsnNode? {
        var current = start
        while (current != null && current.opcode < 0) {
            current = if (forward) current.next else current.previous
        }
        return current
    }
}
