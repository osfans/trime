/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.ServiceLoader
import java.util.zip.ZipFile

/** Installed-APK packaging and dispatcher checks; never triggers an uncaught exception. */
object CoroutinePackagingProbe {
    fun verify(instrumentation: Instrumentation, evidence: JSONObject? = null): Int {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Packaging probe must run off the main thread" }
        var checks = 0
        fun passed(message: String) {
            checks++
            val text = "Coroutine packaging check $checks: $message"
            Log.i("TrimeRuntimeAudit", text)
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putInt("coroutine_packaging_checks", checks)
                    putString("stream", "$text\n")
                },
            )
        }

        val expectedProviders = mapOf(
            "kotlinx.coroutines.CoroutineExceptionHandler" to "kotlinx.coroutines.android.AndroidExceptionPreHandler",
            "kotlinx.coroutines.internal.MainDispatcherFactory" to "kotlinx.coroutines.android.AndroidDispatcherFactory",
        )
        val target = instrumentation.targetContext
        val loader = target.classLoader
        val helper = try {
            Class.forName("com.osfans.trime.util.DebugCoroutineServices", true, loader)
        } catch (_: ClassNotFoundException) {
            null
        }
        val resourcePrefix = if (helper == null) "META-INF/services/" else "trime-services/"
        evidence?.put("resource_layout", if (helper == null) "original" else "relocated")
        val resources = JSONArray()
        evidence?.put("service_resources", resources)
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        fun providerNames(service: Class<*>): List<String> {
            val providers = if (helper == null) {
                ServiceLoader.load(service, loader)
            } else {
                helper.getMethod("load", Class::class.java, ClassLoader::class.java).invoke(null, service, loader) as ServiceLoader<*>
            }
            return providers.map { it.javaClass.name }
        }
        ZipFile(target.applicationInfo.sourceDir).use { apk ->
            expectedProviders.forEach { (service, provider) ->
                val originalPath = "META-INF/services/$service"
                val path = resourcePrefix + service
                if (helper != null) check(apk.getEntry(originalPath) == null) { "Original signing-warning SPI entry remains: $originalPath" }
                val entry = checkNotNull(apk.getEntry(path)) { "Installed application APK is missing $path" }
                check(!entry.isDirectory) { "Service entry is a directory: $path" }
                val bytes = apk.getInputStream(entry).use { it.readBytes() }
                val expected = "$provider\n".toByteArray(Charsets.UTF_8)
                check(bytes.contentEquals(expected)) { "Service bytes differ from the pinned coroutines 1.11.0 descriptor: $path" }
                val declaredProviders = bytes.toString(Charsets.UTF_8).lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toList()
                val resource = JSONObject().put("original", originalPath).put("installed", path)
                    .put("bytes", bytes.size).put("sha256", digest(bytes))
                    .put("original_entry_absent", apk.getEntry(originalPath) == null)
                resources.put(resource)
                if (helper != null) {
                    val rawOriginal = loader.getResources(originalPath).toList()
                    check(rawOriginal.isEmpty()) { "Unexpected unredirected original descriptors: $rawOriginal" }
                    val redirected = helper.getMethod("getResources", ClassLoader::class.java, String::class.java)
                        .invoke(null, loader, originalPath) as? java.util.Enumeration<*> ?: error("Helper did not return resource enumeration")
                    val urls = redirected.toList().map { it as java.net.URL }
                    check(urls.size == 1) { "Expected exactly one relocated provider URL: $urls" }
                    urls.forEach { url -> check(url.openStream().use { it.readBytes() }.contentEquals(bytes)) { "ClassLoader descriptor bytes differ: $url" } }
                    resource.put("redirected_urls", JSONArray(urls.map { it.toExternalForm() }))
                }
                check(declaredProviders == listOf(provider)) { "Unexpected installed providers for $service: $declaredProviders" }
                passed("installed $path declares $provider")
            }
            val metadata = "META-INF/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/verification.properties"
            check(apk.getEntry(metadata) == null) { "Excluded ownership metadata remains in the installed application APK" }
            passed("serialization ownership metadata is absent")
        }

        val handlers = providerNames(CoroutineExceptionHandler::class.java)
        check(expectedProviders.getValue(CoroutineExceptionHandler::class.java.name) in handlers) {
            "Target classloader did not discover AndroidExceptionPreHandler: $handlers"
        }
        passed("target service lookup discovers AndroidExceptionPreHandler")

        // Keep the internal coroutine API behind reflection; only provider discovery is tested.
        val factoryType = Class.forName("kotlinx.coroutines.internal.MainDispatcherFactory", true, loader)
        val factories = providerNames(factoryType)
        check(expectedProviders.getValue(factoryType.name) in factories) {
            "Target classloader did not discover AndroidDispatcherFactory: $factories"
        }
        passed("target service lookup discovers AndroidDispatcherFactory")

        runBlocking {
            withTimeout(10_000) {
                withContext(Dispatchers.Main) {
                    check(Looper.myLooper() === Looper.getMainLooper()) { "Dispatchers.Main did not run on the main looper" }
                }
            }
        }
        passed("Dispatchers.Main executes on the main looper")
        runBlocking {
            withTimeout(10_000) {
                withContext(Dispatchers.Main) {
                    check(!Dispatchers.Main.immediate.isDispatchNeeded(coroutineContext)) {
                        "Dispatchers.Main.immediate still requires dispatch on the main looper"
                    }
                    val mainThread = Thread.currentThread()
                    withContext(Dispatchers.Main.immediate) {
                        check(Looper.myLooper() === Looper.getMainLooper() && Thread.currentThread() === mainThread) {
                            "Dispatchers.Main.immediate did not remain on the main thread"
                        }
                    }
                }
            }
        }
        passed("Dispatchers.Main.immediate stays on main and needs no dispatch")
        return checks
    }
}
