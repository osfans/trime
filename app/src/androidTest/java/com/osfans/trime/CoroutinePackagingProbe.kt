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
import java.util.ServiceLoader
import java.util.zip.ZipFile

/** Installed-APK packaging and dispatcher checks; never triggers an uncaught exception. */
object CoroutinePackagingProbe {
    fun verify(instrumentation: Instrumentation): Int {
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
        ZipFile(target.applicationInfo.sourceDir).use { apk ->
            expectedProviders.forEach { (service, provider) ->
                val path = "META-INF/services/$service"
                val entry = checkNotNull(apk.getEntry(path)) { "Installed application APK is missing $path" }
                check(!entry.isDirectory) { "Service entry is a directory: $path" }
                val declaredProviders = apk.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
                }
                check(declaredProviders == listOf(provider)) { "Unexpected installed providers for $service: $declaredProviders" }
                passed("installed $path declares $provider")
            }
            val metadata = "META-INF/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/verification.properties"
            check(apk.getEntry(metadata) == null) { "Excluded ownership metadata remains in the installed application APK" }
            passed("serialization ownership metadata is absent")
        }

        val loader = target.classLoader
        val handlers = ServiceLoader.load(CoroutineExceptionHandler::class.java, loader).map { it.javaClass.name }
        check(expectedProviders.getValue(CoroutineExceptionHandler::class.java.name) in handlers) {
            "Target classloader did not discover AndroidExceptionPreHandler: $handlers"
        }
        passed("target ServiceLoader discovers AndroidExceptionPreHandler")

        // Keep the internal coroutine API behind reflection; only provider discovery is tested.
        val factoryType = Class.forName("kotlinx.coroutines.internal.MainDispatcherFactory", true, loader)
        val factories = ServiceLoader.load(factoryType, loader).map { it.javaClass.name }
        check(expectedProviders.getValue(factoryType.name) in factories) {
            "Target classloader did not discover AndroidDispatcherFactory: $factories"
        }
        passed("target ServiceLoader discovers AndroidDispatcherFactory")

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
