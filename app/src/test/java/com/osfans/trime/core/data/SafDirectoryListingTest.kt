/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.IOException

private class TestDocumentRows(
    private val records: List<SafDirectoryListing.Row>,
    override var loading: Boolean = false,
    override var error: String? = null,
    private val onExhausted: () -> Unit = {},
) : SafDirectoryListing.Rows {
    private var index = -1
    override fun moveToNext(): Boolean {
        index++
        if (index == records.size) onExhausted()
        return index < records.size
    }
    override fun current(): SafDirectoryListing.Row = records[index]
}

private fun documentRow(id: String, name: String = "$id.yaml") = SafDirectoryListing.Row(id, name, "application/octet-stream", 3L, 42L)

class SafDirectoryListingTest :
    StringSpec({
        "listing includes files and directories after the first child" {
            val rows = listOf(
                documentRow("unrelated"),
                SafDirectoryListing.Row("directory", "folder", "vnd.android.document/directory"),
                documentRow("target"),
            )
            val listed = SafDirectoryListing.readRows(TestDocumentRows(rows))
            listed shouldBe rows
            listed.single { it.name == "target.yaml" }.documentId shouldBe "target"
        }

        "empty complete directory is a valid listing" {
            SafDirectoryListing.readRows(TestDocumentRows(emptyList())) shouldBe emptyList()
        }

        "duplicate name after an unrelated first child is rejected" {
            shouldThrow<IllegalStateException> {
                SafDirectoryListing.readRows(
                    TestDocumentRows(listOf(documentRow("first"), documentRow("a", "target.yaml"), documentRow("b", "target.yaml"))),
                )
            }
        }

        "repeated document identity with different names is rejected" {
            shouldThrow<IllegalStateException> {
                SafDirectoryListing.readRows(TestDocumentRows(listOf(documentRow("same", "a.yaml"), documentRow("same", "b.yaml"))))
            }
        }

        "tree root and parent directory cannot appear as children" {
            for (id in listOf("root", "parent")) {
                for (rows in listOf(listOf(documentRow(id)), listOf(documentRow("first"), documentRow(id)))) {
                    shouldThrow<IllegalStateException> {
                        SafDirectoryListing.readRows(TestDocumentRows(rows), excludedDocumentIds = setOf("root", "parent"))
                    }
                }
            }
        }

        "null and loading listings cannot prove that a child is absent" {
            shouldThrow<IOException> { SafDirectoryListing.readRows(null) }
            shouldThrow<IOException> {
                SafDirectoryListing.readRows(TestDocumentRows(emptyList(), loading = true))
            }
        }

        "provider error rejects otherwise valid rows" {
            shouldThrow<IOException> {
                SafDirectoryListing.readRows(TestDocumentRows(listOf(documentRow("first")), error = "provider unavailable"))
            }
        }

        "invalid identity name MIME or size rejects the whole listing" {
            val valid = documentRow("valid")
            val invalid = listOf(
                valid.copy(documentId = null),
                valid.copy(documentId = ""),
                valid.copy(name = null),
                valid.copy(name = "../outside.yaml"),
                valid.copy(name = "nested\\outside.yaml"),
                valid.copy(name = "."),
                valid.copy(mimeType = null),
                valid.copy(mimeType = ""),
                valid.copy(size = -1),
                valid.copy(lastModified = -1),
            )
            invalid.forEach { row ->
                shouldThrow<IllegalArgumentException> {
                    SafDirectoryListing.readRows(TestDocumentRows(listOf(documentRow("first"), row)))
                }
            }
        }

        "unknown metadata remains distinguishable from a known empty file" {
            val rows = listOf(documentRow("unknown").copy(size = null, lastModified = null), documentRow("empty").copy(size = 0L))
            SafDirectoryListing.readRows(TestDocumentRows(rows)) shouldBe rows
        }

        "cursor failure after a valid row does not return a partial listing" {
            shouldThrow<IOException> {
                SafDirectoryListing.readRows(TestDocumentRows(listOf(documentRow("first")), onExhausted = { throw IOException("cursor failed") }))
            }
        }

        "incomplete state observed at the end rejects already read rows" {
            lateinit var rows: TestDocumentRows
            rows = TestDocumentRows(listOf(documentRow("first")), onExhausted = { rows.loading = true })
            shouldThrow<IOException> { SafDirectoryListing.readRows(rows) }
            rows = TestDocumentRows(listOf(documentRow("first")), onExhausted = { rows.error = "late failure" })
            shouldThrow<IOException> { SafDirectoryListing.readRows(rows) }
        }
    })
