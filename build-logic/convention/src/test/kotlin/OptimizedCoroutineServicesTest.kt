/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OptimizedCoroutineServicesTest {
    private val classes = listOf(
        "Lkotlinx/coroutines/internal/MainDispatcherLoader;",
        "Lkotlinx/coroutines/internal/CoroutineExceptionHandlerImplKt;",
        "Lkotlinx/coroutines/android/AndroidDispatcherFactory;",
        "Lkotlinx/coroutines/android/AndroidExceptionPreHandler;",
        "Lkotlinx/coroutines/android/HandlerDispatcherKt;",
        "Lkotlinx/coroutines/android/HandlerContext;",
    )

    @Test
    fun `post R8 mapped descriptors disappear while every other resource byte remains`() = temporary { dir ->
        val services = mappedServices()
        val other = linkedMapOf(
            "assets/example.bin" to ByteArray(24017) { (it * 31).toByte() },
            "META-INF/example.properties" to "retained=true\n".toByteArray(),
            "empty/" to byteArrayOf(),
        )
        val input = jar(dir, "r8.jar", services + other)
        val originalBytes = input.readBytes()
        val output = File(dir, "filtered.jar")
        val dex = File(dir, "classes.dex").apply { writeBytes(dex()) }
        OptimizedCoroutineServices.filter(input, output, listOf(dex))
        assertContentEquals(originalBytes, input.readBytes())
        ZipFile(output).use { archive ->
            assertEquals(other.keys, archive.entries().asSequence().map { it.name }.toSet())
            other.forEach { (name, bytes) -> assertContentEquals(bytes, archive.getInputStream(archive.getEntry(name)).readBytes()) }
        }
    }

    @Test
    fun `original SPI reaches R8 intact and alternative unmapped output is accepted`() = temporary { dir ->
        val original = OptimizedCoroutineServices.providers.mapKeys { "META-INF/services/${it.key}" }.mapValues { "${it.value}\n".toByteArray() }
        val input = jar(dir, "original.jar", original)
        OptimizedCoroutineServices.verifyOriginalServices(input)
        val dex = File(dir, "classes.dex").apply { writeBytes(dex()) }
        OptimizedCoroutineServices.filter(input, File(dir, "result.jar"), listOf(dex))
        val changed = original.toMutableMap().apply { put(keys.first(), "extra.Provider\n".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { OptimizedCoroutineServices.verifyOriginalServices(jar(dir, "wrong-original.jar", changed)) }
    }

    @Test
    fun `unknown services multiple providers and signed inputs fail without an output`() = temporary { dir ->
        val cases = listOf(
            mappedServices() + ("META-INF/services/example.Unknown" to "example.Provider\n".toByteArray()),
            mappedServices().toMutableMap().apply { put(keys.first(), "extra.Provider\n".toByteArray()) },
            mappedServices().toMutableMap().apply { put(keys.first(), "kotlinx.coroutines.android.AndroidDispatcherFactory\nextra.Provider\n".toByteArray()) },
            mappedServices() + ("META-INF/CERT.SF" to "signed\n".toByteArray()),
        )
        val dex = File(dir, "classes.dex").apply { writeBytes(dex()) }
        cases.forEachIndexed { index, contents ->
            val output = File(dir, "output-$index.jar")
            assertFailsWith<IllegalArgumentException> {
                OptimizedCoroutineServices.filter(jar(dir, "bad-$index.jar", contents), output, listOf(dex))
            }
            assertTrue(!output.exists())
        }
    }

    @Test
    fun `all ServiceLoader methods and ClassLoader resource lookup block removal`() = temporary { dir ->
        val input = jar(dir, "r8.jar", mappedServices())
        listOf(
            "Ljava/util/ServiceLoader;" to "reload",
            "Ljava/util/ServiceLoader;" to "load",
            "Ljava/lang/ClassLoader;" to "getResources",
            "Ljava/lang/Class;" to "getResourceAsStream",
        ).forEachIndexed { index, method ->
            val file = File(dir, "classes-$index.dex").apply { writeBytes(dex(method = method)) }
            assertFailsWith<IllegalArgumentException> {
                OptimizedCoroutineServices.filter(input, File(dir, "output-$index.jar"), listOf(file))
            }
        }
        // Ordinary Android asset access is present in the real application and stays valid.
        assertEquals(1, CoroutineDexInventory.read(dex(method = "Landroid/content/res/AssetManager;" to "open")).methodCount)
    }

    @Test
    fun `missing Android classes FastServiceLoader and dynamic SPI literals block removal`() = temporary { dir ->
        val input = jar(dir, "r8.jar", mappedServices())
        listOf(
            dex(classNames = classes.dropLast(1)),
            dex(classNames = classes + "Lkotlinx/coroutines/internal/FastServiceLoader;"),
            dex(extraStrings = listOf("META-INF/services/")),
            dex(extraStrings = listOf("kotlinx.coroutines.android.AndroidDispatcherFactory")),
        ).forEachIndexed { index, bytes ->
            val file = File(dir, "classes-$index.dex").apply { writeBytes(bytes) }
            assertFailsWith<IllegalArgumentException> {
                OptimizedCoroutineServices.filter(input, File(dir, "output-$index.jar"), listOf(file))
            }
        }
    }

    @Test
    fun `DEX reader accepts zero method tables and MUTF8 null and surrogate pairs`() {
        assertEquals(classes.toSet(), CoroutineDexInventory.read(dex(extraStrings = listOf("a\u0000b", "\uD83D\uDE00"))).classes)
    }

    @Test
    fun `DEX reader rejects corrupt offsets indexes strings and unsupported containers`() {
        val good = dex(method = "Landroid/content/res/AssetManager;" to "open")
        fun patched(offset: Int, value: Int) = good.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value) }
        val stringOffset = ByteBuffer.wrap(good).order(ByteOrder.LITTLE_ENDIAN).getInt(60)
        val methodOffset = ByteBuffer.wrap(good).order(ByteOrder.LITTLE_ENDIAN).getInt(92)
        val stringDataOffset = ByteBuffer.wrap(good).order(ByteOrder.LITTLE_ENDIAN).getInt(stringOffset)
        listOf(
            patched(32, good.size - 1),
            patched(60, Int.MAX_VALUE - 3),
            patched(56, Int.MAX_VALUE),
            patched(stringOffset, good.size),
            patched(stringOffset + 4, stringDataOffset),
            patched(methodOffset + 4, Int.MAX_VALUE),
            good.copyOf().apply { this[stringDataOffset] = 1 },
            good.copyOf().apply { this[6] = '1'.code.toByte() },
        ).forEach { assertFailsWith<IllegalArgumentException> { CoroutineDexInventory.read(it) } }
        assertFailsWith<IllegalArgumentException> { CoroutineDexInventory.read(ByteArray(8)) }
    }

    @Test
    fun `duplicate definitions within or across DEX files cannot satisfy required classes`() = temporary { dir ->
        assertFailsWith<IllegalArgumentException> { CoroutineDexInventory.read(dex(classNames = classes + classes.first())) }
        val first = File(dir, "classes.dex").apply { writeBytes(dex()) }
        val second = File(dir, "classes2.dex").apply { writeBytes(dex()) }
        assertFailsWith<IllegalArgumentException> {
            OptimizedCoroutineServices.filter(jar(dir, "r8.jar", mappedServices()), File(dir, "output.jar"), listOf(first, second))
        }
    }

    private fun mappedServices() = OptimizedCoroutineServices.providers.values.associate {
        "META-INF/services/$it" to "$it\n".toByteArray()
    }

    private fun jar(dir: File, name: String, entries: Map<String, ByteArray>) = File(dir, name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (path, contents) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(contents)
                zip.closeEntry()
            }
        }
    }

    private fun temporary(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("coroutine-services-test-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    // Synthetic standard DEX tables, including actual modified UTF-8 string_data_item encoding.
    // No executable code is required to exercise global method-id/string/class safety decisions.
    private fun dex(
        classNames: List<String> = classes,
        extraStrings: List<String> = emptyList(),
        method: Pair<String, String>? = null,
    ): ByteArray {
        val types = (classNames + listOfNotNull(method?.first) + "V").distinct()
        val strings = (types + listOfNotNull(method?.second) + extraStrings).distinct()
        val stringOffset = 112
        val typeOffset = stringOffset + strings.size * 4
        val protoOffset = if (method == null) 0 else typeOffset + types.size * 4
        val methodOffset = if (method == null) 0 else protoOffset + 12
        val classOffset = if (classNames.isEmpty()) 0 else typeOffset + types.size * 4 + (if (method == null) 0 else 20)
        val dataOffset = typeOffset + types.size * 4 + (if (method == null) 0 else 20) + classNames.size * 32
        val stringData = ByteArrayOutputStream()
        val offsets = strings.map { text ->
            val offset = dataOffset + stringData.size()
            var length = text.length
            do {
                val value = length and 127
                length = length ushr 7
                stringData.write(value or if (length == 0) 0 else 128)
            } while (length != 0)
            val encoded = ByteArrayOutputStream()
            DataOutputStream(encoded).use { it.writeUTF(text) }
            val bytes = encoded.toByteArray()
            stringData.write(bytes, 2, bytes.size - 2)
            stringData.write(0)
            offset
        }
        val buffer = ByteBuffer.allocate(dataOffset + stringData.size()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("dex\n035\u0000".toByteArray())
        buffer.putInt(32, buffer.capacity()).putInt(36, 112).putInt(40, 0x12345678)
        buffer.putInt(56, strings.size).putInt(60, stringOffset)
        buffer.putInt(64, types.size).putInt(68, typeOffset)
        buffer.putInt(72, if (method == null) 0 else 1).putInt(76, protoOffset)
        buffer.putInt(88, if (method == null) 0 else 1).putInt(92, methodOffset)
        buffer.putInt(96, classNames.size).putInt(100, classOffset)
        offsets.forEachIndexed { index, value -> buffer.putInt(stringOffset + index * 4, value) }
        types.forEachIndexed { index, value -> buffer.putInt(typeOffset + index * 4, strings.indexOf(value)) }
        method?.let { (owner, name) ->
            buffer.putInt(protoOffset, strings.indexOf("V")).putInt(protoOffset + 4, types.indexOf("V"))
            buffer.putShort(methodOffset, types.indexOf(owner).toShort()).putShort(methodOffset + 2, 0)
            buffer.putInt(methodOffset + 4, strings.indexOf(name))
        }
        classNames.forEachIndexed { index, name -> buffer.putInt(classOffset + index * 32, types.indexOf(name)) }
        buffer.position(dataOffset)
        buffer.put(stringData.toByteArray())
        return buffer.array()
    }
}
