// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.artifacts.Configuration
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.lang.reflect.Proxy
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GradleVisibleBackportTest {
    @Test
    fun `removing obsolete assignments keeps real configuration flags and JVM stack valid`() {
        val original = fixture()
        val (patched, count) = GradleVisibleBackport.patchClass(original)
        assertEquals(1, count)
        assertEquals(listOf("setVisible:false", "setCanBeResolved:false", "setCanBeConsumed:false"), execute(original))
        assertEquals(listOf("setCanBeResolved:false", "setCanBeConsumed:false"), execute(patched))
        val (again, repeated) = GradleVisibleBackport.patchClass(patched)
        assertEquals(0, repeated)
        assertContentEquals(patched, again)
    }

    @Test
    fun `unrelated classes retain their exact bytes`() {
        val original = fixture(setter = false)
        val (patched, count) = GradleVisibleBackport.patchClass(original)
        assertEquals(0, count)
        assertContentEquals(original, patched)
    }

    @Test
    fun `unexpected values signatures getters and used results are rejected`() {
        listOf(
            fixture(value = true),
            fixture(discard = false),
            fixture(descriptor = "(Z)V"),
            fixture(getter = true),
        ).forEach { bytes ->
            assertFailsWith<IllegalArgumentException> { GradleVisibleBackport.patchClass(bytes) }
        }
    }

    @Test
    fun `new plugin versions and changed pinned bytes fail before writing output`() {
        val directory = createTempDirectory("gradle-visible-backport").toFile()
        try {
            val output = directory.resolve("result.jar")
            listOf(
                "gradle-9.4.1.jar",
                "gradle-9.4.2.jar",
                "symbol-processing-gradle-plugin-2.3.11.jar",
                "instrumented-gradle-9.4.1.jar",
                "instrumented-symbol-processing-gradle-plugin-2.3.11.jar",
            ).forEach { name ->
                val input = directory.resolve(name).apply { writeBytes(byteArrayOf(1, 2, 3)) }
                assertFailsWith<IllegalArgumentException> { GradleVisibleBackport.patchJar(input, output) }
                assertEquals(false, output.exists())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `only audited raw or instrumented build plugins are eligible and unrelated jars use identity transform`() {
        assertEquals(true, GradleVisibleBackport.isTarget("gradle-9.4.1.jar"))
        assertEquals(true, GradleVisibleBackport.isTarget("symbol-processing-gradle-plugin-2.3.11.jar"))
        assertEquals(true, GradleVisibleBackport.isTarget("instrumented-gradle-9.4.1.jar"))
        assertEquals(true, GradleVisibleBackport.isTarget("instrumented-symbol-processing-gradle-plugin-2.3.11.jar"))
        assertEquals(false, GradleVisibleBackport.isTarget("kotlin-gradle-plugin-2.4.21.jar"))
        assertEquals(false, GradleVisibleBackport.isTarget("gradle-tooling-api-9.8.1.jar"))
        listOf("gradle-9.4.2.jar", "symbol-processing-gradle-plugin-2.3.12.jar", "instrumented-gradle-9.4.2.jar").forEach {
            assertFailsWith<IllegalArgumentException> { GradleVisibleBackport.isTarget(it) }
        }
    }

    @Test
    fun `instrumented outputs keep the original Gradle directory and filename pairing`() {
        assertEquals("gradle-9.4.1.jar", GradleVisibleBackport.outputPath("gradle-9.4.1.jar"))
        assertEquals("instrumented/instrumented-gradle-9.4.1.jar", GradleVisibleBackport.outputPath("instrumented-gradle-9.4.1.jar"))
        assertEquals(
            "instrumented/instrumented-symbol-processing-gradle-plugin-2.3.11.jar",
            GradleVisibleBackport.outputPath("instrumented-symbol-processing-gradle-plugin-2.3.11.jar"),
        )
        assertFailsWith<IllegalArgumentException> { GradleVisibleBackport.outputPath("another.jar") }
    }

    private fun fixture(
        setter: Boolean = true,
        value: Boolean = false,
        discard: Boolean = true,
        descriptor: String = "(Z)Lorg/gradle/api/artifacts/Configuration;",
        getter: Boolean = false,
    ): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TrimePluginFixture", null, "java/lang/Object", null)
        val method = writer.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "configure", "(Lorg/gradle/api/artifacts/Configuration;)I", null, null)
        method.visitCode()
        if (setter) {
            method.visitVarInsn(Opcodes.ALOAD, 0)
            method.visitInsn(if (value) Opcodes.ICONST_1 else Opcodes.ICONST_0)
            method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/gradle/api/artifacts/Configuration", "setVisible", descriptor, true)
            if (discard) method.visitInsn(Opcodes.POP)
        }
        if (getter) {
            method.visitVarInsn(Opcodes.ALOAD, 0)
            method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/gradle/api/artifacts/Configuration", "isVisible", "()Z", true)
            method.visitInsn(Opcodes.POP)
        }
        listOf("setCanBeResolved", "setCanBeConsumed").forEach { name ->
            method.visitVarInsn(Opcodes.ALOAD, 0)
            method.visitInsn(Opcodes.ICONST_0)
            method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/gradle/api/artifacts/Configuration", name, "(Z)V", true)
        }
        method.visitIntInsn(Opcodes.BIPUSH, 17)
        method.visitInsn(Opcodes.IRETURN)
        method.visitMaxs(2, 1)
        method.visitEnd()
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun execute(bytes: ByteArray): List<String> {
        val events = mutableListOf<String>()
        val loader = object : ClassLoader(javaClass.classLoader) {
            fun define() = defineClass("TrimePluginFixture", bytes, 0, bytes.size)
        }
        val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Configuration::class.java)) { receiver, method, arguments ->
            events += "${method.name}:${arguments?.single()}"
            if (method.name == "setVisible") receiver else null
        }
        assertEquals(17, loader.define().getMethod("configure", Configuration::class.java).invoke(null, proxy))
        return events
    }
}
