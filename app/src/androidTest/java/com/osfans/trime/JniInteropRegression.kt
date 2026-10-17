// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.osfans.trime.core.RimeConfig
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.opencc.OpenCCDictManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal object JniInteropRegression {
    fun verify() {
        verifyDictionaryConversions()
        verifyConfigFailureResources()
        val supplementary = "\uD842\uDFB7\uD83C\uDF0F"
        val config = File(DataManager.sharedDataDir, "opencc/t2s.json")
        check(config.isFile)
        repeat(20) {
            check(OpenCCDictManager.openCCLineConv("漢語$supplementary", config.path) == "汉语$supplementary")
            for (text in listOf("a\u0000b", "\u0000", "\u0000a\u0000", "a\u0000\u0000b")) {
                check(OpenCCDictManager.openCCLineConv(text, config.path) == text)
            }
        }
        check(OpenCCDictManager.openCCLineConv("a\uD800x\uDC00", config.path) == "a\uFFFDx\uFFFD")
        val id = "__jni_${UUID.randomUUID()}_$supplementary"
        val source = File(DataManager.userDataDir, "$id.yaml")
        val content = File.createTempFile("__jni", ".yaml", DataManager.userDataDir)
        fun quote(value: String) = "'${value.replace("'", "'\\''")}'"
        fun nativeShell(script: String) {
            val process = ProcessBuilder("/system/bin/sh").redirectErrorStream(true).start()
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(script) }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) { "Native UTF-8 fixture failed: $output" }
        }
        try {
            // Android 5's Java file API encodes supplementary filenames differently
            // from native UTF-8. Feed bytes through stdin so this still tests a real
            // Unicode config path on every API, rather than skipping the JNI check.
            content.writeText("value: '$supplementary'\n")
            nativeShell("cat ${quote(content.path)} > ${quote(source.path)}\n")
            RimeConfig.openUserConfig(id).use { check(it.getString("value") == supplementary) }
            // OpenCC must return with the Java exception pending, without making
            // another JNI allocation (CheckJNI on API 21 catches that misuse).
            val missing = File(DataManager.userDataDir, "$id-missing.json")
            val failure = runCatching { OpenCCDictManager.openCCLineConv("test", missing.path) }.exceptionOrNull()
            check(failure is Exception && failure.message?.contains(supplementary) == true)
            check(OpenCCDictManager.openCCLineConv("漢語", config.path) == "汉语")
        } finally {
            try {
                nativeShell("rm -f ${quote(source.path)}\n")
            } finally {
                check(content.delete())
            }
        }
    }

    private fun verifyConfigFailureResources() {
        val directory = File(DataManager.userDataDir, "__jni_opencc_resources_${UUID.randomUUID()}")
        check(directory.mkdir())
        // Match the unique directory component: Android storage aliases can give
        // a different absolute prefix in /proc/self/fd than the Java file path.
        val ownedPath = "/${directory.name}/"
        fun descriptors(): Map<String, String> = buildMap {
            for (fd in checkNotNull(File("/proc/self/fd").listFiles())) {
                val target = try {
                    Os.readlink(fd.path)
                } catch (failure: ErrnoException) {
                    // Other threads may close an unrelated descriptor after listFiles.
                    if (failure.errno != OsConstants.ENOENT) throw failure
                    continue
                }
                if (ownedPath in target) put(fd.name, target)
            }
        }
        fun noRetainedDescriptors(stage: String) {
            val retained = descriptors()
            check(retained.isEmpty()) { "OpenCC retained fixture descriptors at $stage: $retained" }
        }
        try {
            val text = directory.resolve("source.txt")
            val dictionary = directory.resolve("dictionary.ocd2")
            val config = directory.resolve("config.json")
            text.writeText("漢語\t汉语\n測試\t测试\n")
            OpenCCDictManager.openCCDictConv(text.path, dictionary.path, OpenCCDictManager.MODE_TXT_TO_BIN)
            val complete = dictionary.readBytes()
            val header = "OPENCC_MARISA_0.2.5".toByteArray(Charsets.US_ASCII)
            check(complete.size > header.size && complete.copyOf(header.size).contentEquals(header))
            val dict = JSONObject().put("type", "ocd2").put("file", dictionary.path)
            config.writeText(
                JSONObject().put("name", "JNI resource regression")
                    .put("segmentation", JSONObject().put("type", "mmseg").put("dict", dict))
                    .put("conversion_chain", JSONArray().put(JSONObject().put("dict", dict)))
                    .toString(),
            )
            noRetainedDescriptors("before calibration")
            dictionary.inputStream().use {
                check(descriptors().values.any { it.endsWith("/dictionary.ocd2") }) { "Fixture descriptor scan cannot detect an open dictionary" }
            }
            noRetainedDescriptors("after calibration")
            repeat(32) { index ->
                noRetainedDescriptors("before attempt $index")
                val malformed = if (index % 2 == 0) {
                    complete.copyOf(header.size - 1)
                } else {
                    complete.copyOf().apply { this[0] = 'X'.code.toByte() }
                }
                dictionary.writeBytes(malformed)
                val failure = runCatching { OpenCCDictManager.openCCLineConv("漢語測試", config.path) }.exceptionOrNull()
                check(failure is Exception && failure.message?.contains("Invalid OpenCC dictionary header") == true) {
                    "OpenCC config did not reject malformed header at attempt $index: $failure"
                }
                noRetainedDescriptors("after rejected attempt $index")
                dictionary.writeBytes(complete)
                check(OpenCCDictManager.openCCLineConv("漢語測試", config.path) == "汉语测试") { "Valid OpenCC config retry failed at attempt $index" }
                noRetainedDescriptors("after valid retry $index")
            }
            Log.i("JniInteropRegression", "PASS: OpenCC config failures close fixture descriptors across 32 short/bad-header retries")
        } finally {
            check(directory.deleteRecursively()) { "OpenCC resource regression fixtures were not removed" }
        }
    }

    private fun verifyDictionaryConversions() {
        val directory = File(DataManager.userDataDir, "__jni_opencc_${UUID.randomUUID()}")
        check(directory.mkdir())
        try {
            val source = directory.resolve("source.tmp")
            val binary = directory.resolve("dictionary.tmp")
            val roundTrip = directory.resolve("roundtrip.tmp")
            val truncated = directory.resolve("truncated.tmp")
            val entries = setOf("漢語\t汉语", "測試\t测试")
            fun verifyRoundTrip() {
                source.writeText(entries.joinToString("\n", postfix = "\n"))
                OpenCCDictManager.openCCDictConv(source.path, binary.path, OpenCCDictManager.MODE_TXT_TO_BIN)
                check(binary.length() > 0) { "OpenCC .tmp conversion produced no dictionary" }
                OpenCCDictManager.openCCDictConv(binary.path, roundTrip.path, OpenCCDictManager.MODE_BIN_TO_TXT)
                check(roundTrip.readLines().toSet() == entries) { "OpenCC .tmp roundtrip changed dictionary entries" }
            }
            verifyRoundTrip()
            Log.i("JniInteropRegression", "PASS: OpenCC explicit-format .tmp roundtrip")
            val complete = binary.readBytes()
            val headerSize = "OPENCC_MARISA_0.2.5".length
            // This fixture uses the bundled OCD2/Marisa format. The second cut
            // retains the 16-byte Marisa header but truncates its first data block.
            val cuts = listOf("header" to headerSize - 1, "trie" to headerSize + 16 + 1, "values-tail" to complete.size - 1)
            for ((label, size) in cuts) {
                check(size in 1 until complete.size)
                truncated.writeBytes(complete.copyOf(size))
                roundTrip.writeText("previous output")
                val failure = runCatching {
                    OpenCCDictManager.openCCDictConv(truncated.path, roundTrip.path, OpenCCDictManager.MODE_BIN_TO_TXT)
                }.exceptionOrNull()
                check(failure is Exception) { "OpenCC accepted nonempty $label truncation" }
                check(roundTrip.readText() == "previous output") { "Invalid OpenCC $label input replaced the output" }
                verifyRoundTrip()
                Log.i("JniInteropRegression", "PASS: OpenCC nonempty $label truncation rejected and conversion recovered")
            }
            source.writeText("missing tab and value\n")
            val retained = binary.readBytes()
            val malformed = runCatching {
                OpenCCDictManager.openCCDictConv(source.path, binary.path, OpenCCDictManager.MODE_TXT_TO_BIN)
            }.exceptionOrNull()
            check(malformed is Exception && malformed.message?.contains("Tabular not found") == true) {
                "Malformed text dictionary did not return the OpenCC parse exception"
            }
            check(binary.readBytes().contentEquals(retained)) { "Invalid text dictionary replaced the output" }
            verifyRoundTrip()
            Log.i("JniInteropRegression", "PASS: OpenCC malformed text rejected and conversion recovered")
        } finally {
            check(directory.deleteRecursively()) { "OpenCC JNI regression files were not removed" }
        }
    }
}
