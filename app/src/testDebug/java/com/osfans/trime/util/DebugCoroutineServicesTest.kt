/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.Enumeration
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class DebugMappedHandler :
    AbstractCoroutineContextElement(CoroutineExceptionHandler),
    CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) = Unit
}

class DebugExternalHandler :
    AbstractCoroutineContextElement(CoroutineExceptionHandler),
    CoroutineExceptionHandler {
    override fun handleException(context: CoroutineContext, exception: Throwable) = Unit
}

class DebugCoroutineServicesTest :
    StringSpec({
        val service = CoroutineExceptionHandler::class.java
        val original = "META-INF/services/" + service.name
        val relocated = "trime-services/" + service.name

        fun withLoader(resources: Map<String, String>, block: (ClassLoader) -> Unit) {
            val directory = Files.createTempDirectory("trime-debug-services").toFile()
            try {
                val urls = resources.mapValues { (name, text) ->
                    directory.resolve(name).apply {
                        parentFile.mkdirs()
                        writeText(text)
                    }.toURI().toURL()
                }
                val loader = object : ClassLoader(DebugMappedHandler::class.java.classLoader) {
                    override fun getResources(name: String): Enumeration<URL> = Collections.enumeration(listOfNotNull(urls[name]))
                }
                block(loader)
            } finally {
                directory.deleteRecursively()
            }
        }

        "mapped resources use original class loading and retain original-path external providers" {
            withLoader(
                mapOf(
                    original to DebugExternalHandler::class.java.name + "\n",
                    relocated to DebugMappedHandler::class.java.name + "\n",
                ),
            ) { loader ->
                // Ordering across the two resource locations is deliberately not asserted.
                DebugCoroutineServices.load(service, loader).map { it.javaClass }.toSet() shouldBe
                    setOf(DebugExternalHandler::class.java, DebugMappedHandler::class.java)
            }
        }

        "fast resource lookup sees relocated resources without replacing their bytes" {
            val text = DebugMappedHandler::class.java.name + "\n"
            withLoader(mapOf(relocated to text)) { loader ->
                val resources = Collections.list(DebugCoroutineServices.getResources(loader, original))
                resources.size shouldBe 1
                resources.single().openStream().bufferedReader().use { it.readText() } shouldBe text
            }
        }

        "unrelated resources are delegated unchanged" {
            withLoader(mapOf("example/settings.txt" to "retained")) { loader ->
                Collections.list(DebugCoroutineServices.getResources(loader, "example/settings.txt")) shouldBe
                    Collections.list(loader.getResources("example/settings.txt"))
            }
        }

        "invalid providers preserve ServiceLoader failure rather than disappearing" {
            withLoader(mapOf(relocated to "example.DoesNotExist\n")) { loader ->
                shouldThrow<ServiceConfigurationError> {
                    DebugCoroutineServices.load(service, loader).toList()
                }
            }
        }

        "null loader retains platform system-loader discovery" {
            DebugCoroutineServices.load(service, null).map { it.javaClass.name }.toList() shouldBe
                ServiceLoader.load(service, null).map { it.javaClass.name }.toList()
        }

        "null service retains the platform null rejection" {
            shouldThrow<NullPointerException> {
                DebugCoroutineServices.load<CoroutineExceptionHandler>(null, service.classLoader)
            }
        }

        "non-target services keep platform discovery" {
            withLoader(emptyMap()) { loader ->
                DebugCoroutineServices.load(Runnable::class.java, loader).toList() shouldBe
                    ServiceLoader.load(Runnable::class.java, loader).toList()
            }
        }

        "resource IO failures remain observable" {
            val failure = IOException("synthetic lookup failure")
            val loader = object : ClassLoader(service.classLoader) {
                override fun getResources(name: String): Enumeration<URL> = throw failure
            }
            shouldThrow<IOException> { DebugCoroutineServices.getResources(loader, original) } shouldBe failure
        }

        "JVM test dependencies retain original coroutine bytecode" {
            val expected = mapOf(
                "kotlinx/coroutines/internal/CoroutineExceptionHandlerImplKt.class" to "4101f2b692d8a39b789caa258189d1538e6b1e86a2609f18a15603fdc70404b7",
                "kotlinx/coroutines/internal/MainDispatcherLoader.class" to "74fcc2319ea2bf04271a2fcbf86a0436431862765b3add8290fa1292e4d5b18a",
                "kotlinx/coroutines/internal/FastServiceLoader.class" to "82dc841566a8c98e678f84af41d1744869e2bcdd8804e4094252325fcc12e28e",
            )
            expected.forEach { (name, digest) ->
                val bytes = checkNotNull(service.classLoader.getResourceAsStream(name)).use { it.readBytes() }
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } shouldBe digest
            }
        }

        "JVM tests keep their original dependency SPI resources" {
            service.classLoader.getResources(original).hasMoreElements() shouldBe true
        }
    })
