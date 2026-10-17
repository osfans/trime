/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Only applies to the reviewed coroutines 1.11.0 / R8 9.4.24 Android output. */
internal object OptimizedCoroutineServices {
    const val AGP_VERSION = "9.4.1"
    const val R8_VERSION = "9.4.24"
    const val R8_COMMIT = "61dc59bf271ddb314b5e0953231bd22df0ffb17e"
    const val COROUTINES_VERSION = "1.11.0"
    private const val PREFIX = "META-INF/services/"
    val providers = linkedMapOf(
        "kotlinx.coroutines.internal.MainDispatcherFactory" to
            "kotlinx.coroutines.android.AndroidDispatcherFactory",
        "kotlinx.coroutines.CoroutineExceptionHandler" to
            "kotlinx.coroutines.android.AndroidExceptionPreHandler",
    )
    private val requiredClasses = setOf(
        "Lkotlinx/coroutines/internal/MainDispatcherLoader;",
        "Lkotlinx/coroutines/internal/CoroutineExceptionHandlerImplKt;",
        "Lkotlinx/coroutines/android/AndroidDispatcherFactory;",
        "Lkotlinx/coroutines/android/AndroidExceptionPreHandler;",
        "Lkotlinx/coroutines/android/HandlerDispatcherKt;",
        "Lkotlinx/coroutines/android/HandlerContext;",
    )

    fun verifyOriginalServices(jar: File) = ZipFile(jar).use { archive ->
        val services = serviceEntries(archive)
        require(services.keys == providers.keys.map { PREFIX + it }.toSet()) {
            "Unexpected original coroutine service descriptors: ${services.keys}"
        }
        providers.forEach { (service, provider) ->
            require(services.getValue(PREFIX + service).contentEquals("$provider\n".toByteArray())) {
                "Unexpected original provider for $service"
            }
        }
    }

    fun verifyConsumerRules(androidJar: File, coreJar: File, configuration: String) {
        val androidRules = ZipFile(androidJar).use {
            it.getInputStream(requireNotNull(it.getEntry("META-INF/com.android.tools/r8-from-1.6.0/coroutines.pro"))).use { stream -> stream.readBytes() }
        }
        val coreRules = ZipFile(coreJar).use {
            it.getInputStream(requireNotNull(it.getEntry("META-INF/com.android.tools/r8/coroutines.pro"))).use { stream -> stream.readBytes() }
        }
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(
            sha256(androidRules) == "9e234a50d0a557c9dc6d299cd4d8843c62c083eb57e70cfb8260b1a826e164dc" &&
                sha256(coreRules) == "a3415c723861df894885f171ccba3f3c3741a42db0e7cc0c61fe4ca29fa38868",
        ) {
            "The reviewed R8-specific coroutine consumer rules changed"
        }
        fun normalize(text: String) = text.lineSequence().map { it.substringBefore('#') }.joinToString("").replace(Regex("\\s+"), "")
        val actual = normalize(configuration)
        require(listOf(androidRules, coreRules).all { normalize(it.decodeToString()) in actual }) {
            "The actual R8 configuration is missing bundled R8-specific coroutine consumer rules"
        }
        // Old ProGuard keep rules prevent the ServiceLoader rewrite: do not reintroduce them.
        providers.values.forEach { provider ->
            require("-keepclass$provider{" !in actual) {
                "A legacy coroutine provider keep rule prevents R8 optimization"
            }
        }
    }

    fun filter(input: File, output: File, dexFiles: List<File>): String {
        require(input.canonicalFile != output.canonicalFile) { "Never modify the R8 input in place" }
        require(dexFiles.isNotEmpty()) { "No actual R8 DEX output" }
        val inventories = dexFiles.map {
            require(it.length() in 112L..(64L * 1024 * 1024)) { "Unsupported DEX file size" }
            CoroutineDexInventory.read(it.readBytes())
        }
        val definitions = inventories.flatMap { it.classes }
        val classes = definitions.toSet()
        require(classes.size == definitions.size) { "Duplicate classes across DEX files" }
        require(classes.containsAll(requiredClasses)) { "Reviewed Android coroutine classes are missing: ${requiredClasses - classes}" }
        require("Lkotlinx/coroutines/internal/FastServiceLoader;" !in classes) { "FastServiceLoader still exists" }
        val removed = ZipFile(input).use { archive ->
            val services = serviceEntries(archive)
            require(services.size == providers.size) { "Unexpected post-R8 service descriptor count: ${services.keys}" }
            val known = providers.flatMap { (service, provider) -> listOf(PREFIX + service, PREFIX + provider) }.toSet()
            require(services.keys.all { it in known }) { "Unknown service descriptor: ${services.keys - known}" }
            providers.forEach { (service, provider) ->
                val matching = services.keys.filter { it == PREFIX + service || it == PREFIX + provider }
                require(matching.size == 1 && services.getValue(matching.single()).contentEquals("$provider\n".toByteArray())) {
                    "Unexpected post-R8 provider for $service"
                }
            }
            require(archive.entries().asSequence().none { it.name.matches(Regex("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) }) {
                "Refuse to transform an already signed archive"
            }
            output.parentFile.mkdirs()
            val temporary = Files.createTempFile(output.parentFile.toPath(), "coroutine-resources-", ".jar").toFile()
            try {
                ZipOutputStream(temporary.outputStream().buffered()).use { destination ->
                    archive.entries().asSequence().forEach { original ->
                        if (original.name !in services) {
                            val entry = ZipEntry(original.name)
                            entry.time = original.time
                            entry.comment = original.comment
                            entry.extra = original.extra
                            destination.putNextEntry(entry)
                            archive.getInputStream(original).use { it.copyTo(destination) }
                            destination.closeEntry()
                        }
                    }
                }
                verifyUnchanged(archive, temporary, services.keys)
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            services.keys
        }
        return "Removed ${removed.sorted()}; checked ${dexFiles.size} DEX files, ${inventories.sumOf { it.methodCount }} method IDs."
    }

    private fun serviceEntries(archive: ZipFile): Map<String, ByteArray> {
        val entries = archive.entries().asSequence().toList()
        require(entries.map { it.name }.toSet().size == entries.size) { "Duplicate Java resource entries" }
        return entries.filter { !it.isDirectory && it.name.startsWith(PREFIX) }.associate { entry ->
            require(entry.size in 1..4096) { "Unexpected SPI resource size" }
            entry.name to archive.getInputStream(entry).use { it.readBytes() }
        }
    }

    private fun verifyUnchanged(before: ZipFile, output: File, removed: Set<String>) = ZipFile(output).use { after ->
        val remaining = before.entries().asSequence().filter { it.name !in removed }.toList()
        require(after.entries().asSequence().map { it.name }.toSet() == remaining.map { it.name }.toSet()) {
            "Unexpected Java resource entry changes"
        }
        remaining.forEach { entry ->
            before.getInputStream(entry).use { original ->
                after.getInputStream(after.getEntry(entry.name)).use { copied ->
                    val a = ByteArray(8192)
                    val b = ByteArray(8192)
                    while (true) {
                        val count = original.readNBytes(a, 0, a.size)
                        require(copied.readNBytes(b, 0, b.size) == count && (0 until count).all { a[it] == b[it] }) {
                            "Changed Java resource bytes: ${entry.name}"
                        }
                        if (count == 0) break
                    }
                }
            }
        }
    }
}

/** Bounded standard DEX reader: inspects all method IDs, strings and class definitions. */
internal data class CoroutineDexInventory(val classes: Set<String>, val methodCount: Int) {
    companion object {
        fun read(data: ByteArray): CoroutineDexInventory {
            require(data.size in 112..(64 * 1024 * 1024)) { "Unsupported DEX size" }
            require(
                data.copyOfRange(0, 4).contentEquals("dex\n".toByteArray()) &&
                    data[7] == 0.toByte() && data.copyOfRange(4, 7).decodeToString() in setOf("035", "037", "038", "039", "040"),
            ) {
                "Unsupported DEX format"
            }
            fun uint(offset: Int): Int {
                require(offset >= 0 && offset <= data.size - 4) { "Out-of-bounds DEX uint" }
                val result = (0..3).fold(0L) { value, n -> value or ((data[offset + n].toLong() and 255) shl (8 * n)) }
                require(result <= Int.MAX_VALUE) { "DEX integer exceeds supported bounds" }
                return result.toInt()
            }
            fun ushort(offset: Int): Int = (data[offset].toInt() and 255) or ((data[offset + 1].toInt() and 255) shl 8)
            require(uint(32) == data.size && uint(36) == 112 && uint(40) == 0x12345678) { "Invalid DEX header" }
            fun table(countOffset: Int, width: Int): Pair<Int, Int> {
                val count = uint(countOffset)
                val offset = uint(countOffset + 4)
                require(
                    offset % 4 == 0 && (if (count == 0) offset == 0 else offset >= 112) &&
                        offset.toLong() + count.toLong() * width <= data.size,
                ) { "Invalid DEX index table" }
                return count to offset
            }
            val (stringsCount, stringsOffset) = table(56, 4)
            val (typesCount, typesOffset) = table(64, 4)
            val (protosCount, _) = table(72, 12)
            val (methodsCount, methodsOffset) = table(88, 8)
            val (classesCount, classesOffset) = table(96, 32)
            require(stringsCount <= 200000) { "Unreviewed DEX string table size" }
            val stringDataOffsets = HashSet<Int>()
            var decodedCharacters = 0L
            val strings = Array(stringsCount) { index ->
                var cursor = uint(stringsOffset + index * 4)
                require(cursor >= 112 && cursor < data.size) { "Invalid DEX string data offset" }
                require(stringDataOffsets.add(cursor)) { "Duplicate DEX string data offset" }
                var length = 0L
                var terminated = false
                for (n in 0..4) {
                    require(cursor < data.size) { "Truncated DEX string length" }
                    val value = data[cursor++].toInt() and 255
                    length = length or ((value.toLong() and 127) shl (7 * n))
                    if (value < 128) {
                        terminated = true
                        break
                    }
                }
                require(terminated && length <= data.size) { "Invalid DEX string length" }
                decodedCharacters += length
                require(decodedCharacters <= data.size) { "DEX decoded strings exceed bounded input size" }
                val text = StringBuilder()
                while (true) {
                    require(cursor < data.size) { "Unterminated DEX string" }
                    val first = data[cursor++].toInt() and 255
                    if (first == 0) break
                    fun continuation(): Int {
                        require(cursor < data.size) { "Truncated DEX MUTF-8" }
                        val value = data[cursor++].toInt() and 255
                        require(value and 192 == 128) { "Invalid DEX MUTF-8 continuation" }
                        return value and 63
                    }
                    val character = when {
                        first < 128 -> first

                        first in 192..223 -> {
                            val value = ((first and 31) shl 6) or continuation()
                            require(value >= 128 || (first == 192 && value == 0)) { "Overlong DEX MUTF-8" }
                            value
                        }

                        first in 224..239 -> {
                            val value = ((first and 15) shl 12) or (continuation() shl 6) or continuation()
                            require(value >= 2048) { "Overlong DEX MUTF-8" }
                            value
                        }

                        else -> throw IllegalArgumentException("Unsupported DEX MUTF-8 byte")
                    }
                    text.append(character.toChar())
                    require(text.length.toLong() <= length) { "DEX UTF-16 length mismatch" }
                }
                require(text.length.toLong() == length) { "DEX UTF-16 length mismatch" }
                text.toString()
            }
            val types = Array(typesCount) { index ->
                val stringIndex = uint(typesOffset + index * 4)
                require(stringIndex < stringsCount) { "Invalid DEX type string index" }
                strings[stringIndex]
            }
            val forbiddenNames = OptimizedCoroutineServices.providers.keys + OptimizedCoroutineServices.providers.values + "java.util.ServiceLoader"
            require(strings.none { it in forbiddenNames || "META-INF" in it }) { "DEX still names a dynamic SPI resource" }
            for (index in 0 until methodsCount) {
                val offset = methodsOffset + index * 8
                val owner = ushort(offset)
                val proto = ushort(offset + 2)
                val name = uint(offset + 4)
                require(owner < typesCount && proto < protosCount && name < stringsCount) { "Invalid DEX method index" }
                require(types[owner] != "Ljava/util/ServiceLoader;") { "DEX still references ServiceLoader" }
                require(
                    !(
                        types[owner] in setOf("Ljava/lang/Class;", "Ljava/lang/ClassLoader;") &&
                            strings[name] in setOf("getResource", "getResources", "getResourceAsStream")
                        ),
                ) {
                    "DEX still references Class/ClassLoader resource lookup"
                }
            }
            val definitions = (0 until classesCount).map { index ->
                val type = uint(classesOffset + index * 32)
                require(type < typesCount) { "Invalid DEX class index" }
                types[type].also { require(it.startsWith("L") && it.endsWith(";")) { "Invalid DEX class descriptor" } }
            }
            val classes = definitions.toSet()
            require(classes.size == classesCount) { "Duplicate DEX class definitions" }
            return CoroutineDexInventory(classes, methodsCount)
        }
    }
}
