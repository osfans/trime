/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import android.os.Build
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NativePluginIntegrationTest {
    @Test
    fun testDirectoryLoadingAndRedeploy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Use a dedicated emulator" }
        check(instrumentation.targetContext.packageName.endsWith(".debug"))
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val expected = args.getString("expectJoint") == "true"
        val filename = args.getString("filename") ?: "librime-joint.so"
        val directory = File(context.getExternalFilesDir(null), "rime-plugins").apply { mkdirs() }
        val source = File(directory, filename)
        val disabled = File(directory, "$filename.disabled")
        val authority = "${context.packageName}.provider"
        val parent = DocumentsContract.buildDocumentUri(authority, "files/rime-plugins")
        val document = DocumentsContract.buildDocumentUri(authority, "files/rime-plugins/$filename")
        val resolver = context.contentResolver
        when (args.getString("action")) {
            "install", "replace" -> {
                if (source.exists()) {
                    DocumentsContract.deleteDocument(resolver, document)
                }
                val uri = checkNotNull(DocumentsContract.createDocument(resolver, parent, "application/octet-stream", filename))
                checkNotNull(resolver.openOutputStream(uri)).use { output ->
                    instrumentation.context.assets.open("librime-joint.so").use { it.copyTo(output) }
                    if (args.getString("action") == "replace") output.write("\nrestart-replacement-test\n".toByteArray())
                }
                File(directory, "librime-broken.so").writeText("Deliberately invalid ELF")
                File(directory, "readme.txt").writeText("Not a plugin")
            }

            "remove" -> DocumentsContract.deleteDocument(resolver, document)
        }
        val modules = NativePlugins.modules()
        assertEquals(if (expected) listOf("joint") else emptyList<String>(), modules.toList())
        val cached = File(context.noBackupFilesDir, "rime-plugins/$filename")
        assertEquals("Deleted plugins must not survive in the private snapshot", expected, cached.exists())
        if (expected) {
            fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
            assertEquals("Restart must load the current file, including replacements", digest(source), digest(cached))
        }
        if (expected) assertTrue(source.renameTo(disabled))
        val root = File(context.cacheDir, "native-plugin-integration-${UUID.randomUUID()}").apply { mkdirs() }
        val schema = """
            schema: {schema_id: native_test, name: Native plugin test, version: '1'}
            engine:
              processors: [speller, selector, express_editor]
              segmentors: [abc_segmentor, fallback_segmentor]
              translators: [${if (expected) "joint_translator" else "script_translator"}@translator]
            speller:
              alphabet: abcdefghijklmnopqrstuvwxyz
              algebra:
                - xform/^xie$/xp/
                - xform/^shu$/uu/
                - xform/^ru$/ru/
                - xform/^zheng$/vg/
                - xform/^hao$/hc/
                - xlit/XUV/xuv/
            translator:
              dictionary: native_test
              enable_user_dict: false
              enable_word_completion: false
              enable_completion: true
              joint_completion: true
        """.trimIndent()
        File(root, "native_test.schema.yaml").writeText(schema)
        File(root, "native_test.dict.yaml").writeText(
            "---\nname: native_test\nversion: '1'\nsort: by_weight\n...\n" +
                "谢谢\txie xie\t100\n" +
                "输入\tshu ru\t100\n" +
                "正好\tzheng hao\t100\n" +
                "X\tX\t1\nU\tU\t1\nV\tV\t1\n",
        )
        File(root, "default.yaml").writeText("config_version: '1'\nschema_list:\n  - schema: native_test\n")
        try {
            repeat(2) { round ->
                val done = CountDownLatch(1)
                val handler: (RimeMessage<*>) -> Unit = { msg ->
                    if (msg is RimeMessage.DeployMessage && msg.data != RimeMessage.DeployMessage.State.Start) done.countDown()
                }
                Rime.registerRimeMessageHandler(handler)
                try {
                    assertEquals(modules.toList(), NativePlugins.modules().toList())
                    Rime.startupRime(root.path, root.path, "native-plugin-test-$round", true, NativePlugins.modules())
                    assertTrue("Maintenance timed out", done.await(60, TimeUnit.SECONDS))
                    // Deployment notification can precede maintenance completion.
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (!Rime.selectRimeSchema("native_test")) {
                        assertTrue("Schema unavailable after deployment", System.nanoTime() < deadline)
                        Thread.sleep(10)
                    }
                    val cases = if (expected) {
                        listOf("xpx" to "谢谢", "uur" to "输入", "vgh" to "正好")
                    } else {
                        listOf("xpxp" to "谢谢", "uuru" to "输入", "vghc" to "正好")
                    }
                    for ((input, target) in cases) {
                        Rime.clearRimeComposition()
                        Rime.simulateRimeKeySequence(input)
                        val candidates = Rime.getRimeCandidates(0, 30).map { it.text }
                        assertTrue("$input → $target missing: $candidates", target in candidates)
                    }
                } finally {
                    Rime.unregisterRimeMessageHandler(handler)
                    Rime.exitRime()
                }
            }
        } finally {
            if (expected) assertTrue(disabled.renameTo(source))
            root.deleteRecursively()
        }
    }
}
