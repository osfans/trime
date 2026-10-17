/* SPDX-License-Identifier: GPL-3.0-or-later */
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DebugCoroutineServicesTest {
    @Test
    fun acceptExactlyOneReviewedOriginalAndRelocatedProviderPerService() {
        DebugCoroutineResources.validateEntries(DebugCoroutineResources.expectedEntries().toList())
    }

    @Test
    fun rejectMissingDuplicateConflictingAndUnknownProviders() {
        val valid = DebugCoroutineResources.expectedEntries().toList()
        val invalid = listOf(
            valid.drop(1),
            valid + valid.first(),
            valid.drop(1) + (valid.first().first to "example.OtherProvider\n".toByteArray()),
            valid + ("META-INF/services/example.Other" to "example.Provider\n".toByteArray()),
            valid + ("trime-services/example.Other" to "example.Provider\n".toByteArray()),
        )
        invalid.forEach { entries ->
            assertFailsWith<IllegalArgumentException> { DebugCoroutineResources.validateEntries(entries) }
        }
    }

    @Test
    fun rewriteAllFourInvocationSitesWithoutTouchingUnrelatedInstructions() {
        val actual = mutableListOf<String>()
        DebugCoroutineVisitor.expectedMethods.forEach { (owner, methods) ->
            val writer = ClassWriter(0)
            ClassReader(fixture(owner, methods)).accept(DebugCoroutineVisitor(owner, writer), 0)
            ClassReader(writer.toByteArray()).accept(
                object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?,
                    ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            actual += owner + "." + name + descriptor
                        }
                    }
                },
                0,
            )
        }
        assertEquals(3, actual.count { it == DebugCoroutineVisitor.HELPER + ".load" + DebugCoroutineVisitor.LOAD_DESCRIPTOR })
        assertEquals(1, actual.count { it == DebugCoroutineVisitor.HELPER + ".getResources" + DebugCoroutineVisitor.HELPER_RESOURCE_DESCRIPTOR })
        assertEquals(3, actual.count { it == "java/lang/System.nanoTime()J" })
    }

    @Test
    fun rejectMissingDuplicateAndChangedInvocationSignatures() {
        DebugCoroutineVisitor.expectedMethods.forEach { (owner, methods) ->
            listOf(0 to false, 2 to false, 1 to true).forEach { (copies, changed) ->
                assertFailsWith<IllegalArgumentException> {
                    ClassReader(fixture(owner, methods, copies, changed)).accept(
                        DebugCoroutineVisitor(owner, ClassWriter(0)),
                        0,
                    )
                }
            }
        }
    }

    private fun fixture(owner: String, methods: Map<String, String>, copies: Int = 1, changed: Boolean = false): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null)
        methods.forEach { (key, call) ->
            val method = writer.visitMethod(Opcodes.ACC_STATIC, key.substringBefore('('), key.substring(key.indexOf('(')), null, null)
            method.visitCode()
            repeat(copies) {
                method.visitInsn(Opcodes.ACONST_NULL)
                method.visitInsn(Opcodes.ACONST_NULL)
                method.visitMethodInsn(
                    if (call == "load") Opcodes.INVOKESTATIC else Opcodes.INVOKEVIRTUAL,
                    if (call == "load") "java/util/ServiceLoader" else "java/lang/ClassLoader",
                    call,
                    if (changed) {
                        "()V"
                    } else if (call == "load") {
                        DebugCoroutineVisitor.LOAD_DESCRIPTOR
                    } else {
                        DebugCoroutineVisitor.RESOURCE_DESCRIPTOR
                    },
                    false,
                )
                method.visitInsn(Opcodes.POP)
            }
            if (key.endsWith("V")) {
                method.visitInsn(Opcodes.RETURN)
            } else {
                method.visitInsn(Opcodes.ACONST_NULL)
                method.visitInsn(Opcodes.ARETURN)
            }
            method.visitMaxs(2, 2)
            method.visitEnd()
        }
        writer.visitMethod(Opcodes.ACC_STATIC, "unrelated", "()V", null, null).apply {
            visitCode()
            visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false)
            visitInsn(Opcodes.POP2)
            visitInsn(Opcodes.RETURN)
            visitMaxs(2, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }
}
