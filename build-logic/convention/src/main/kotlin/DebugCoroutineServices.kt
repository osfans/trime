/* SPDX-License-Identifier: GPL-3.0-or-later */
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

internal object DebugCoroutineResources {
    const val ORIGINAL = "META-INF/services/"
    const val RELOCATED = "trime-services/"
    const val VERIFIED = "coroutines-1.11.0:two-original-and-two-relocated-providers\n"
    val providers = OptimizedCoroutineServices.providers
    val jarHashes = setOf(
        "c2cb206d27017c7d1bf5ff179787397543d13748dbabb0d7237e1585e0b29044",
        "d1d75aa01dffbb4d1c520e67e4c4e7f5f6174718e7cb4632412503f2f0e604fa",
    )

    fun sha256(file: File): String {
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

    fun expectedEntries(): Map<String, ByteArray> = providers.flatMap { (service, provider) ->
        listOf(ORIGINAL + service, RELOCATED + service).map { it to (provider + "\n").toByteArray() }
    }.toMap()

    fun validateEntries(entries: List<Pair<String, ByteArray>>) {
        val expected = expectedEntries()
        require(entries.size == expected.size && entries.map { it.first }.toSet() == expected.keys) {
            "Missing, duplicate or unknown coroutine service resource"
        }
        entries.forEach { (name, bytes) ->
            require(bytes.contentEquals(expected.getValue(name))) {
                "Unreviewed coroutine provider content: " + name
            }
        }
    }

    fun isServiceResource(name: String) = name.startsWith(ORIGINAL) || name.startsWith(RELOCATED)
}

/** Generates only the two byte-identical relocated resources, without consuming project resources. */
@DisableCachingByDefault(because = "Pinned dependency and provider checks run against actual inputs")
abstract class GenerateDebugCoroutineResourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val coroutineArtifacts: ConfigurableFileCollection

    @get:Input
    abstract val coroutineCoordinates: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        require(
            coroutineCoordinates.get().toSet() == setOf(
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
            ),
        ) { "Unreviewed coroutine runtime dependencies" }
        val jars = coroutineArtifacts.files
        require(jars.size == 2 && jars.map(DebugCoroutineResources::sha256).toSet() == DebugCoroutineResources.jarHashes) {
            "The reviewed raw coroutine runtime JARs changed"
        }
        val android = jars.single { file ->
            ZipFile(file).use {
                it.getEntry(DebugCoroutineResources.ORIGINAL + DebugCoroutineResources.providers.keys.first()) != null
            }
        }
        OptimizedCoroutineServices.verifyOriginalServices(android)
        ZipFile(android).use { archive ->
            DebugCoroutineResources.providers.forEach { (service, _) ->
                val entry = archive.getEntry(DebugCoroutineResources.ORIGINAL + service)
                val bytes = archive.getInputStream(entry).use { it.readBytes() }
                val output = outputDirectory.file(DebugCoroutineResources.RELOCATED + service).get().asFile
                output.parentFile.mkdirs()
                output.writeBytes(bytes)
            }
        }
    }
}

/** Read-only audit after resource generation; it is deliberately not the generator's dependency. */
@DisableCachingByDefault(because = "Detects conflicting service resources before packaging exclusions")
abstract class VerifyDebugCoroutineResourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputResources: ConfigurableFileCollection

    @get:OutputFile
    abstract val resultFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        // Optional source directories may not exist; required descriptors are still checked below.
        inputResources.files.filter { it.exists() }.forEach { input ->
            if (input.isDirectory) {
                input.walkTopDown().filter { it.isFile }.forEach { file ->
                    val name = file.relativeTo(input).invariantSeparatorsPath
                    if (DebugCoroutineResources.isServiceResource(name)) {
                        require(file.length() in 1..4096) { "Unreviewed service resource size" }
                        entries += name to file.readBytes()
                    }
                }
            } else {
                ZipFile(input).use { archive ->
                    archive.entries().asSequence().filter {
                        !it.isDirectory && DebugCoroutineResources.isServiceResource(it.name)
                    }.forEach { entry ->
                        require(entry.size in 1..4096) { "Unreviewed service resource size" }
                        entries += entry.name to archive.getInputStream(entry).use { it.readBytes() }
                    }
                }
            }
        }
        DebugCoroutineResources.validateEntries(entries)
        resultFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(DebugCoroutineResources.VERIFIED)
        }
    }
}

interface DebugCoroutineInstrumentationParameters : InstrumentationParameters {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val verifiedResources: RegularFileProperty
}

abstract class DebugCoroutineVisitorFactory : AsmClassVisitorFactory<DebugCoroutineInstrumentationParameters> {
    override fun isInstrumentable(classData: ClassData) = classData.className.replace('.', '/') in DebugCoroutineVisitor.expectedMethods

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        require(parameters.get().verifiedResources.get().asFile.readText() == DebugCoroutineResources.VERIFIED)
        return DebugCoroutineVisitor(classContext.currentClassData.className.replace('.', '/'), nextClassVisitor)
    }
}

/** Changes four invocation instructions only; methods, frames and all coroutine flags stay intact. */
internal class DebugCoroutineVisitor(private val owner: String, downstream: ClassVisitor) : ClassVisitor(Opcodes.ASM9, downstream) {
    companion object {
        const val HELPER = "com/osfans/trime/util/DebugCoroutineServices"
        const val LOAD_DESCRIPTOR = "(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/ServiceLoader;"
        const val RESOURCE_DESCRIPTOR = "(Ljava/lang/String;)Ljava/util/Enumeration;"
        const val HELPER_RESOURCE_DESCRIPTOR = "(Ljava/lang/ClassLoader;Ljava/lang/String;)Ljava/util/Enumeration;"
        val expectedMethods = mapOf(
            "kotlinx/coroutines/internal/MainDispatcherLoader" to mapOf(
                "loadMainDispatcher()Lkotlinx/coroutines/MainCoroutineDispatcher;" to "load",
            ),
            "kotlinx/coroutines/internal/CoroutineExceptionHandlerImplKt" to mapOf(
                "<clinit>()V" to "load",
            ),
            "kotlinx/coroutines/internal/FastServiceLoader" to mapOf(
                "load(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/List;" to "load",
                "loadProviders\$kotlinx_coroutines_core(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/List;" to "getResources",
            ),
        )
    }

    private val expected = expectedMethods.getValue(owner)
    private val rewritten = mutableMapOf<String, Int>()

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val method = name + descriptor
        val downstream = super.visitMethod(access, name, descriptor, signature, exceptions)
        return object : MethodVisitor(Opcodes.ASM9, downstream) {
            override fun visitMethodInsn(opcode: Int, calledOwner: String, name: String, descriptor: String, isInterface: Boolean) {
                val load = calledOwner == "java/util/ServiceLoader" && name == "load"
                val resources = calledOwner == "java/lang/ClassLoader" && name == "getResources"
                if (load || resources) {
                    require(!isInterface && expected[method] == name) { "Unexpected coroutine service call site: " + method }
                    require(
                        (load && opcode == Opcodes.INVOKESTATIC && descriptor == LOAD_DESCRIPTOR) ||
                            (resources && opcode == Opcodes.INVOKEVIRTUAL && descriptor == RESOURCE_DESCRIPTOR),
                    ) { "Unexpected coroutine service invocation signature" }
                    rewritten[method] = (rewritten[method] ?: 0) + 1
                    require(rewritten.getValue(method) == 1) { "Duplicate coroutine service invocation" }
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        name,
                        if (resources) HELPER_RESOURCE_DESCRIPTOR else LOAD_DESCRIPTOR,
                        false,
                    )
                } else {
                    super.visitMethodInsn(opcode, calledOwner, name, descriptor, isInterface)
                }
            }
        }
    }

    override fun visitEnd() {
        require(rewritten == expected.mapValues { 1 }) { "Missing coroutine service invocation in " + owner }
        super.visitEnd()
    }
}
