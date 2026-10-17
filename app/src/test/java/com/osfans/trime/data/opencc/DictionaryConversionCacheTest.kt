// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.opencc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

private fun withDictionaries(block: (File, File) -> Unit) {
    val directory = createTempDirectory().toFile()
    try {
        val source = directory.resolve("words.txt").apply { writeText("old") }
        block(source, directory.resolve("words.ocd2"))
        directory.listFiles()!!.map { it.extension }.all { it in setOf("txt", "ocd2") } shouldBe true
    } finally {
        directory.deleteRecursively()
    }
}

private fun convertDictionary(source: File, output: File) {
    output.writeText("dictionary:${source.readText()}")
}

class DictionaryConversionCacheTest :
    StringSpec({
        "unchanged source and output reuse the conversion without rewriting output" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output, ::convertDictionary) shouldBe true
                output.setLastModified(1000L) shouldBe true
                cache.convert(source, output) { _, _ -> error("Unchanged dictionary was rebuilt") } shouldBe false
                output.readText() shouldBe "dictionary:old"
                output.lastModified() shouldBe 1000L
            }
        }

        "source changes invalidate even with identical size and timestamp" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output, ::convertDictionary)
                val timestamp = source.lastModified()
                source.writeText("new")
                source.setLastModified(timestamp) shouldBe true
                cache.convert(source, output, ::convertDictionary) shouldBe true
                output.readText() shouldBe "dictionary:new"
            }
        }

        "missing output is rebuilt" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output, ::convertDictionary)
                output.delete() shouldBe true
                cache.convert(source, output, ::convertDictionary) shouldBe true
                output.readText() shouldBe "dictionary:old"
            }
        }

        "corrupt output invalidates even with identical size and timestamp" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output, ::convertDictionary)
                val timestamp = output.lastModified()
                output.writeText("dictionary:BAD")
                output.setLastModified(timestamp) shouldBe true
                cache.convert(source, output, ::convertDictionary) shouldBe true
                output.readText() shouldBe "dictionary:old"
            }
        }

        "failed conversion preserves the previous dictionary and retry must convert" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output, ::convertDictionary)
                source.writeText("new")
                shouldThrow<IOException> {
                    cache.convert(source, output) { _, temporary ->
                        temporary.writeText("partial")
                        throw IOException("conversion failed")
                    }
                }
                output.readText() shouldBe "dictionary:old"
                cache.convert(source, output, ::convertDictionary) shouldBe true
                output.readText() shouldBe "dictionary:new"
                cache.convert(source, output) { _, _ -> error("Successful retry was not cached") } shouldBe false
            }
        }

        "source changed during conversion is rejected without replacing old output" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output, ::convertDictionary)
                source.writeText("new")
                shouldThrow<IOException> {
                    cache.convert(source, output) { snapshot, temporary ->
                        convertDictionary(snapshot, temporary)
                        source.writeText("latest")
                    }
                }
                output.readText() shouldBe "dictionary:old"
                cache.convert(source, output, ::convertDictionary) shouldBe true
                output.readText() shouldBe "dictionary:latest"
            }
        }

        "source snapshot prevents an intervening edit and restore from poisoning the cache" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output) { snapshot, temporary ->
                    snapshot.extension shouldBe "tmp"
                    temporary.extension shouldBe "tmp"
                    source.writeText("intervening")
                    convertDictionary(snapshot, temporary)
                    source.writeText("old")
                } shouldBe true
                output.readText() shouldBe "dictionary:old"
                cache.convert(source, output) { _, _ -> error("Snapshot fingerprint did not match") } shouldBe false
            }
        }

        "empty output cannot replace a usable dictionary or become a cache hit" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                output.writeText("previous")
                shouldThrow<IOException> { cache.convert(source, output) { _, _ -> } }
                output.readText() shouldBe "previous"
                cache.convert(source, output, ::convertDictionary) shouldBe true
            }
        }

        "a failed source read invalidates the previous evidence" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                cache.convert(source, output, ::convertDictionary)
                source.delete() shouldBe true
                shouldThrow<IOException> { cache.convert(source, output, ::convertDictionary) }
                output.readText() shouldBe "dictionary:old"
                source.writeText("old")
                cache.convert(source, output, ::convertDictionary) shouldBe true
            }
        }

        "failed atomic publication preserves the destination and retry converts" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                output.mkdir() shouldBe true
                val retained = output.resolve("retained").apply { writeText("keep") }
                shouldThrow<IOException> { cache.convert(source, output, ::convertDictionary) }
                retained.readText() shouldBe "keep"
                output.deleteRecursively() shouldBe true
                cache.convert(source, output, ::convertDictionary) shouldBe true
                output.readText() shouldBe "dictionary:old"
            }
        }

        "the least recently used entry is rebuilt after the capacity limit" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache(capacity = 2)
                val second = source.resolveSibling("second.txt").apply { writeText("two") }
                val secondOutput = output.resolveSibling("second.ocd2")
                val third = source.resolveSibling("third.txt").apply { writeText("three") }
                val thirdOutput = output.resolveSibling("third.ocd2")
                cache.convert(source, output, ::convertDictionary) shouldBe true
                cache.convert(second, secondOutput, ::convertDictionary) shouldBe true
                cache.convert(source, output, ::convertDictionary) shouldBe false
                cache.convert(third, thirdOutput, ::convertDictionary) shouldBe true
                cache.convert(source, output, ::convertDictionary) shouldBe false
                cache.convert(second, secondOutput, ::convertDictionary) shouldBe true
            }
        }

        "a new process cache does not trust previously produced output" {
            withDictionaries { source, output ->
                DictionaryConversionCache().convert(source, output, ::convertDictionary) shouldBe true
                DictionaryConversionCache().convert(source, output, ::convertDictionary) shouldBe true
            }
        }

        "concurrent conversions publish once and the waiting caller validates the result" {
            withDictionaries { source, output ->
                val cache = DictionaryConversionCache()
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val secondStarted = CountDownLatch(1)
                val calls = AtomicInteger()
                val executor = Executors.newFixedThreadPool(2)
                try {
                    val first = executor.submit<Boolean> {
                        cache.convert(source, output) { snapshot, temporary ->
                            calls.incrementAndGet()
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                            convertDictionary(snapshot, temporary)
                        }
                    }
                    entered.await(5, TimeUnit.SECONDS) shouldBe true
                    val second = executor.submit<Boolean> {
                        secondStarted.countDown()
                        cache.convert(source, output) { snapshot, temporary ->
                            calls.incrementAndGet()
                            convertDictionary(snapshot, temporary)
                        }
                    }
                    secondStarted.await(5, TimeUnit.SECONDS) shouldBe true
                    second.isDone shouldBe false
                    release.countDown()
                    first.get(5, TimeUnit.SECONDS) shouldBe true
                    second.get(5, TimeUnit.SECONDS) shouldBe false
                    calls.get() shouldBe 1
                    output.readText() shouldBe "dictionary:old"
                } finally {
                    release.countDown()
                    executor.shutdownNow()
                    executor.awaitTermination(5, TimeUnit.SECONDS)
                }
            }
        }
    })
