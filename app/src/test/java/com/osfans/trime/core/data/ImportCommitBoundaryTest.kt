// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory

private class ImportChoiceFixture(parent: File) {
    val root = File(parent, "rime").apply { mkdir() }
    val source = File(root, "selected.yaml").apply { writeText("value: old\n") }
    val compiled = File(root, "build/selected.yaml").apply {
        parentFile!!.mkdirs()
        writeText("old compiled")
    }
    val index = File(parent, "index.json").apply { writeText("old index") }
    var mode = "APP_STORAGE"
    var tree = "old-tree"
    val grants = mutableSetOf("old-tree", "picked-tree")

    fun publish(transaction: LocalImportTransaction) {
        transaction.stageFile("selected.yaml").writeText("value: new\n")
        transaction.snapshotRuntime()
        transaction.publish("selected.yaml")
        compiled.writeText("new compiled")
    }

    fun rememberChoice() {
        index.writeText("new index")
        mode = "EXTERNAL_SYNC"
        tree = "picked-tree"
    }

    fun restoreChoice() {
        mode = "APP_STORAGE"
        tree = "old-tree"
        grants.remove("picked-tree")
    }

    fun releasePreviousChoice() {
        grants.remove("old-tree")
    }
}

/** Both contexts are real dispatchers, so withContext must dispatch the result back. */
private suspend fun onImportDispatchers(block: suspend (CoroutineDispatcher) -> Unit) {
    val caller = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val engine = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    try {
        withContext(caller) { block(engine) }
    } finally {
        engine.close()
        caller.close()
    }
}

private suspend fun runImport(
    fixture: ImportChoiceFixture,
    boundary: ImportCommitBoundary,
    engine: CoroutineDispatcher,
    activate: (Job) -> Unit,
): Throwable? = coroutineScope {
    var observed: Throwable? = null
    launch {
        val callerJob = currentCoroutineContext().job
        try {
            boundary.preserveChoice(
                restorePreviousChoice = { fixture.restoreChoice() },
                releasePreviousChoice = fixture::releasePreviousChoice,
            ) {
                withContext(engine) { activate(callerJob) }
            }
        } catch (failure: Throwable) {
            observed = failure
        }
    }.join()
    observed
}

class ImportCommitBoundaryTest :
    StringSpec({
        "dispatcher return cancellation keeps committed files, index, directory and grant consistent" {
            val parent = createTempDirectory().toFile()
            try {
                val fixture = ImportChoiceFixture(parent)
                val boundary = ImportCommitBoundary()
                val transaction = LocalImportTransaction(fixture.root)
                onImportDispatchers { engine ->
                    val failure = runImport(fixture, boundary, engine) { callerJob ->
                        fixture.publish(transaction)
                        fixture.rememberChoice()
                        transaction.commit()
                        boundary.markCommitted()
                        // No throwing statement follows commit: cancellation happens on dispatch back.
                        callerJob.cancel(CancellationException("Import page was destroyed"))
                    }
                    (failure is CancellationException) shouldBe true
                }
                transaction.isCommitted shouldBe true
                boundary.isCommitted shouldBe true
                fixture.source.readText() shouldBe "value: new\n"
                fixture.compiled.readText() shouldBe "new compiled"
                fixture.index.readText() shouldBe "new index"
                fixture.mode shouldBe "EXTERNAL_SYNC"
                fixture.tree shouldBe "picked-tree"
                fixture.grants shouldBe setOf("picked-tree")
                File(transaction.recoveryDirectory).exists() shouldBe false
            } finally {
                parent.deleteRecursively()
            }
        }
        "pre-commit cancellation, activation and choice failures restore original files and storage choice" {
            for (failurePoint in listOf("cancellation", "activation", "choice")) {
                val parent = createTempDirectory().toFile()
                try {
                    val fixture = ImportChoiceFixture(parent)
                    val boundary = ImportCommitBoundary()
                    val transaction = LocalImportTransaction(fixture.root)
                    onImportDispatchers { engine ->
                        val failure = runImport(fixture, boundary, engine) { callerJob ->
                            try {
                                fixture.publish(transaction)
                                when (failurePoint) {
                                    "cancellation" -> {
                                        callerJob.cancel(CancellationException("Import timed out before commit"))
                                        callerJob.ensureActive()
                                    }

                                    "activation" -> throw IOException("Imported native configuration failed")

                                    "choice" -> {
                                        fixture.rememberChoice()
                                        // The new index and selection were written, but files are not committed yet.
                                        throw IOException("Storage choice failed before transaction commit")
                                    }
                                }
                                fixture.rememberChoice()
                                transaction.commit()
                                boundary.markCommitted()
                            } catch (failure: Throwable) {
                                transaction.rollback(failure) shouldBe true
                                fixture.index.writeText("old index")
                                throw failure
                            }
                        }
                        if (failurePoint == "cancellation") {
                            (failure is CancellationException) shouldBe true
                        } else {
                            (failure is IOException) shouldBe true
                        }
                    }
                    transaction.isCommitted shouldBe false
                    boundary.isCommitted shouldBe false
                    fixture.source.readText() shouldBe "value: old\n"
                    fixture.compiled.readText() shouldBe "old compiled"
                    fixture.index.readText() shouldBe "old index"
                    fixture.mode shouldBe "APP_STORAGE"
                    fixture.tree shouldBe "old-tree"
                    fixture.grants shouldBe setOf("old-tree")
                    File(transaction.recoveryDirectory).exists() shouldBe false
                } finally {
                    parent.deleteRecursively()
                }
            }
        }
        "a notification failure after commit cannot restore the previous storage choice" {
            val parent = createTempDirectory().toFile()
            try {
                val fixture = ImportChoiceFixture(parent)
                val boundary = ImportCommitBoundary()
                val transaction = LocalImportTransaction(fixture.root)
                onImportDispatchers { engine ->
                    val failure = runImport(fixture, boundary, engine) {
                        fixture.publish(transaction)
                        fixture.rememberChoice()
                        transaction.commit()
                        boundary.markCommitted()
                        throw IOException("Committed notification failed")
                    }
                    failure?.message shouldBe "Committed notification failed"
                }
                transaction.isCommitted shouldBe true
                fixture.source.readText() shouldBe "value: new\n"
                fixture.compiled.readText() shouldBe "new compiled"
                fixture.index.readText() shouldBe "new index"
                fixture.mode shouldBe "EXTERNAL_SYNC"
                fixture.tree shouldBe "picked-tree"
                fixture.grants shouldBe setOf("picked-tree")
            } finally {
                parent.deleteRecursively()
            }
        }
    })
