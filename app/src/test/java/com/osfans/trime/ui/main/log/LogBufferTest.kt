/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.main.log

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun LogBuffer.texts(): List<String> = List(size) { get(it).text }

class LogBufferTest :
    StringSpec({
        "the default window retains the newest ten thousand records in order" {
            val buffer = LogBuffer()
            repeat(10_001) { buffer.append("row$it") }
            buffer.size shouldBe 10_000
            buffer[0].text shouldBe "row1"
            buffer[9_999].text shouldBe "row10000"
            buffer.omittedLines shouldBe 1L
            buffer.omittedCharacters shouldBe 4L
        }

        "character pressure evicts whole oldest records and budgets joining newlines" {
            val buffer = LogBuffer(maxLines = 10, maxCharacters = 12)
            buffer.append("aaaa")
            buffer.append("bbbb")
            buffer.characterCount shouldBe 9
            buffer.append("ccc")
            buffer.texts() shouldBe listOf("bbbb", "ccc")
            buffer.characterCount shouldBe 8
            buffer.omittedLines shouldBe 1L
            buffer.omittedCharacters shouldBe 4L
        }

        "ring indexing remains ordered after repeated wraparound and mixed eviction" {
            val buffer = LogBuffer(maxLines = 3, maxCharacters = 20)
            repeat(100) { buffer.append("$it") }
            buffer.texts() shouldBe listOf("97", "98", "99")
            buffer.characterCount shouldBe 8
            buffer.omittedLines shouldBe 97L
            shouldThrow<IllegalArgumentException> { buffer[-1] }
            shouldThrow<IllegalArgumentException> { buffer[3] }
        }

        "empty records consume a row and their joining newlines consume characters" {
            val buffer = LogBuffer(maxLines = 10, maxCharacters = 2)
            repeat(4) { buffer.append("") }
            buffer.size shouldBe 3
            buffer.characterCount shouldBe 2
            buffer.omittedLines shouldBe 1L
            buffer.omittedCharacters shouldBe 0L
            buffer.snapshot("earlier omitted") shouldBe "earlier omitted\n\n\n"
        }

        "an oversized record keeps a bounded tail without splitting a surrogate pair" {
            val buffer = LogBuffer(maxLines = 5, maxCharacters = 2)
            buffer.append("before𠼞Z", isLogcat = true, priority = 'E')
            buffer.texts() shouldBe listOf("Z")
            buffer.characterCount shouldBe 1
            buffer.omittedCharacters shouldBe 8L
            buffer.omittedLines shouldBe 0L
            buffer[0].isLogcat shouldBe true
            buffer[0].priority shouldBe 'E'
        }

        "an entire supplementary character fits when its pair and suffix fit" {
            val buffer = LogBuffer(maxLines = 5, maxCharacters = 3)
            buffer.append("before𠼞Z")
            buffer.texts() shouldBe listOf("𠼞Z")
            buffer.characterCount shouldBe 3
            buffer.omittedCharacters shouldBe 6L
        }

        "oversized records cannot leave an unreported internal gap after older records" {
            val buffer = LogBuffer(maxLines = 5, maxCharacters = 8)
            buffer.append("old")
            buffer.append("prefix-abcdefghi")
            buffer.texts() shouldBe listOf("bcdefghi")
            buffer.omittedLines shouldBe 1L
            buffer.omittedCharacters shouldBe 11L
            buffer.snapshot("earlier omitted") shouldBe "earlier omitted\nbcdefghi"
        }

        "later eviction of a truncated record counts the original text only once" {
            val buffer = LogBuffer(maxLines = 3, maxCharacters = 4)
            buffer.append("123456789")
            buffer.append("z")
            buffer.texts() shouldBe listOf("z")
            buffer.omittedLines shouldBe 1L
            buffer.omittedCharacters shouldBe 9L
        }

        "clear removes the omission notice and does not reuse previously visible IDs" {
            val buffer = LogBuffer(maxLines = 1, maxCharacters = 20)
            buffer.append("first")
            val oldId = buffer[0].id
            buffer.append("second")
            buffer.hasOmissions shouldBe true
            buffer.clear()
            buffer.size shouldBe 0
            buffer.characterCount shouldBe 0
            buffer.omittedLines shouldBe 0L
            buffer.omittedCharacters shouldBe 0L
            buffer.snapshot("earlier omitted") shouldBe ""
            buffer.append("third")
            (buffer[0].id > oldId) shouldBe true
            buffer.snapshot("earlier omitted") shouldBe "third"
        }

        "a pending batch extends the retained records without losing order or metadata" {
            val displayed = LogBuffer(maxLines = 5, maxCharacters = 100)
            val pending = LogBuffer(maxLines = 5, maxCharacters = 100)
            displayed.append("a")
            displayed.append("b")
            pending.append("c", isLogcat = true, priority = 'W')
            pending.append("d")
            pending.drainTo(displayed)
            displayed.texts() shouldBe listOf("a", "b", "c", "d")
            displayed[2].priority shouldBe 'W'
            displayed[2].isLogcat shouldBe true
            displayed.hasOmissions shouldBe false
            pending.size shouldBe 0
            pending.characterCount shouldBe 0
            pending.hasOmissions shouldBe false
        }

        "a pending batch that lost its prefix also evicts all older displayed records" {
            val displayed = LogBuffer(maxLines = 3, maxCharacters = 100)
            val pending = LogBuffer(maxLines = 3, maxCharacters = 100)
            displayed.append("A")
            displayed.append("B")
            listOf("C", "D", "E", "F").forEach { pending.append(it) }
            pending.drainTo(displayed)
            displayed.texts() shouldBe listOf("D", "E", "F")
            displayed.omittedLines shouldBe 3L
            displayed.omittedCharacters shouldBe 3L
            pending.hasOmissions shouldBe false
        }

        "a partially clipped pending record cannot follow older displayed content" {
            val displayed = LogBuffer(maxLines = 5, maxCharacters = 8)
            val pending = LogBuffer(maxLines = 5, maxCharacters = 8)
            displayed.append("older")
            pending.append("prefix-abcdefghi")
            pending.drainTo(displayed)
            displayed.texts() shouldBe listOf("bcdefghi")
            displayed.omittedLines shouldBe 1L
            displayed.omittedCharacters shouldBe 13L
        }

        "existing omission history and a lost pending prefix are counted once" {
            val displayed = LogBuffer(maxLines = 2, maxCharacters = 100)
            val pending = LogBuffer(maxLines = 2, maxCharacters = 100)
            listOf("A", "B", "C").forEach { displayed.append(it) }
            listOf("D", "E", "F").forEach { pending.append(it) }
            pending.drainTo(displayed)
            displayed.texts() shouldBe listOf("E", "F")
            displayed.omittedLines shouldBe 4L
            displayed.omittedCharacters shouldBe 4L
        }

        "clearing both windows discards queued records and their omission history" {
            val displayed = LogBuffer(maxLines = 1, maxCharacters = 100)
            val pending = LogBuffer(maxLines = 1, maxCharacters = 100)
            displayed.append("displayed")
            pending.append("queued1")
            pending.append("queued2")
            pending.clear()
            displayed.clear()
            pending.append("fresh")
            pending.drainTo(displayed)
            displayed.snapshot("earlier omitted") shouldBe "fresh"
            displayed.hasOmissions shouldBe false
        }

        "exports contain the omission notice once and remain immutable after appends" {
            val buffer = LogBuffer(maxLines = 2, maxCharacters = 100)
            listOf("one", "two", "three").forEach { buffer.append(it) }
            val exported = buffer.snapshot("earlier omitted")
            buffer.append("four")
            exported shouldBe "earlier omitted\ntwo\nthree"
            buffer.snapshot("earlier omitted") shouldBe "earlier omitted\nthree\nfour"
        }

        "budgets must be positive and a buffer cannot drain into itself" {
            shouldThrow<IllegalArgumentException> { LogBuffer(maxLines = 0) }
            shouldThrow<IllegalArgumentException> { LogBuffer(maxCharacters = 0) }
            val buffer = LogBuffer()
            shouldThrow<IllegalArgumentException> { buffer.drainTo(buffer) }
        }
    })
