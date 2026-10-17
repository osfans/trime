// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.room.withTransaction
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.Database
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DatabaseDao
import com.osfans.trime.ui.main.ClipEditActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real Room writes and dialog lifecycle; only newly inserted UUID-tagged rows are removed. */
internal object ClipSaveProbe {
    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        var passed = false
        var checks = 0
        val editErrors = AtomicInteger()
        val errorObserver = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority >= Log.ERROR &&
                    (message.startsWith("Unable to save clipboard entry") || message.startsWith("Unable to open clipboard entry"))
                ) {
                    editErrors.incrementAndGet()
                }
            }
        }
        Timber.plant(errorObserver)
        fun <T> main(block: () -> T): T {
            var outcome: Result<T>? = null
            instrumentation.runOnMainSync { outcome = runCatching(block) }
            return checkNotNull(outcome).getOrThrow()
        }
        fun editor(type: String, id: Int) = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ClipEditActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ClipEditActivity.CLIP_TYPE, type)
                .putExtra(ClipEditActivity.BEAN_ID, id),
        ) as ClipEditActivity
        suspend fun eventually(block: suspend () -> Boolean) = withTimeout(15_000) {
            while (!block()) delay(50)
        }
        fun checkPassed(name: String) {
            checks++
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS $checks: $name\n") })
        }
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            instrumentation.sendStatus(
                0,
                Bundle().apply {
                    putString("selected_input_method", Settings.Secure.getString(instrumentation.targetContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
                },
            )
            runBlocking {
                withTimeout(180_000) {
                    for ((type, helper, prefix) in listOf(
                        Triple(ClipEditActivity.FROM_CLIPBOARD, ClipboardHelper, "clb"),
                        Triple(ClipEditActivity.FROM_COLLECTION, CollectionHelper, "clt"),
                    )) {
                        val db = helper.javaClass.getDeclaredField("${prefix}Db").apply { isAccessible = true }.get(helper) as Database
                        val daoField = helper.javaClass.getDeclaredField("${prefix}Dao").apply { isAccessible = true }
                        val dao = daoField.get(helper) as DatabaseDao
                        val cached = helper.javaClass.getDeclaredField("lastBean").apply { isAccessible = true }
                        val previousCache = main { cached.get(helper) }
                        val owned = mutableListOf<Int>()
                        var active: ClipEditActivity? = null
                        suspend fun insert(text: String): DatabaseBean {
                            val row = dao.insert(DatabaseBean(text = text, pinned = true))
                            return checkNotNull(dao.get(row)).also { owned += it.id }
                        }
                        try {
                            suspend fun verifySuccessfulSaves() {
                                for (background in listOf(false, true)) {
                                    val token = UUID.randomUUID().toString()
                                    val bean = insert("initial-$token")
                                    val expected = "saved-$token-\u4f60\u597d-\uD842\uDFB7"
                                    main { cached.set(helper, bean) }
                                    val activity = editor(type, bean.id).also { active = it }
                                    val field = main { activity.findViewById<EditText>(R.id.clip_edit_text) }
                                    val ok = main { activity.findViewById<View>(R.id.clip_edit_ok) }
                                    eventually { main { field.text.toString() == bean.text && ok.isEnabled } }
                                    val entered = CompletableDeferred<Unit>()
                                    val release = CompletableDeferred<Unit>()
                                    val blocked = launch(Dispatchers.Default) {
                                        db.withTransaction {
                                            entered.complete(Unit)
                                            release.await()
                                        }
                                    }
                                    try {
                                        entered.await()
                                        main {
                                            field.setText(expected)
                                            ok.performClick()
                                            field.setText("duplicate-$token")
                                            ok.performClick()
                                        }
                                        delay(300)
                                        check(main { !activity.isFinishing }) { "Editor closed before its database write completed" }
                                        check(main { !ok.isEnabled }) { "Duplicate save was not disabled" }
                                        check(main { (cached.get(helper) as DatabaseBean).text == bean.text }) { "Uncommitted text reached the cache" }
                                        if (background) {
                                            main { activity.finish() }
                                            eventually { main { activity.isDestroyed } }
                                        }
                                    } finally {
                                        withContext(NonCancellable) {
                                            release.complete(Unit)
                                            blocked.join()
                                            db.withTransaction { }
                                        }
                                    }
                                    eventually { dao.get(bean.id)?.text == expected }
                                    eventually { main { (cached.get(helper) as DatabaseBean).text == expected } }
                                    eventually { main { activity.isDestroyed } }
                                    checkPassed("$type: ${if (background) "save survives dialog destruction" else "save awaits commit and ignores duplicate click"}")
                                    active = null
                                }
                            }
                            verifySuccessfulSaves()

                            suspend fun verifyRemovedRowFailure() {
                                val missing = insert("missing-${UUID.randomUUID()}")
                                main { cached.set(helper, missing) }
                                val failed = editor(type, missing.id).also { active = it }
                                val field = main { failed.findViewById<EditText>(R.id.clip_edit_text) }
                                val ok = main { failed.findViewById<View>(R.id.clip_edit_ok) }
                                eventually { main { field.text.toString() == missing.text && ok.isEnabled } }
                                dao.delete(missing.id)
                                main {
                                    field.setText("unsaved edit")
                                    ok.performClick()
                                }
                                eventually { main { ok.isEnabled && !failed.isFinishing } }
                                check(main { field.text.toString() == "unsaved edit" })
                                check(main { field.error?.toString() == failed.getString(R.string.clipboard_entry_removed) })
                                check(main { cached.get(helper) == missing }) { "Failed write changed the cache" }
                                check(dao.get(missing.id) == null)
                                main { failed.findViewById<View>(R.id.clip_edit_cancel).performClick() }
                                eventually { main { failed.isDestroyed } }
                                active = null
                                checkPassed("$type: removed-row failure retains editor text and cache")
                            }
                            verifyRemovedRowFailure()

                            suspend fun verifyRemovedBetweenReadAndUpdate() {
                                val missing = insert("update-race-${UUID.randomUUID()}")
                                main { cached.set(helper, missing) }
                                val activity = editor(type, missing.id).also { active = it }
                                val field = main { activity.findViewById<EditText>(R.id.clip_edit_text) }
                                val ok = main { activity.findViewById<View>(R.id.clip_edit_ok) }
                                eventually { main { field.text.toString() == missing.text && ok.isEnabled } }
                                val readExisting = AtomicBoolean()
                                val affectedRows = AtomicInteger(-1)
                                daoField.set(
                                    helper,
                                    object : DatabaseDao by dao {
                                        override suspend fun get(id: Int): DatabaseBean? = dao.get(id).also {
                                            if (id == missing.id && it != null) readExisting.set(true)
                                        }

                                        override suspend fun updateText(id: Int, newText: String?): Int {
                                            if (id == missing.id) {
                                                check(readExisting.get()) { "The save did not read the existing row before the deletion race" }
                                                dao.delete(id)
                                            }
                                            return dao.updateText(id, newText).also {
                                                if (id == missing.id) affectedRows.set(it)
                                            }
                                        }
                                    },
                                )
                                try {
                                    main {
                                        field.setText("unsaved raced edit")
                                        ok.performClick()
                                    }
                                    eventually { main { ok.isEnabled && !activity.isFinishing && field.error != null } }
                                    check(readExisting.get() && affectedRows.get() == 0) { "The real DAO did not return zero rows after a successful read" }
                                    check(main { field.text.toString() == "unsaved raced edit" })
                                    check(main { field.error?.toString() == activity.getString(R.string.clipboard_entry_removed) })
                                    check(main { cached.get(helper) == missing }) { "A zero-row update published edited text into the cache" }
                                    check(dao.get(missing.id) == null)
                                    main { activity.findViewById<View>(R.id.clip_edit_cancel).performClick() }
                                    eventually { main { activity.isDestroyed } }
                                    active = null
                                    checkPassed("$type: deletion after get returns zero-row NotFound without losing draft or cache")
                                } finally {
                                    daoField.set(helper, dao)
                                }
                            }
                            verifyRemovedBetweenReadAndUpdate()

                            suspend fun verifySupersededLoad() {
                                val first = insert("first-${UUID.randomUUID()}")
                                val second = insert("second-${UUID.randomUUID()}")
                                val reading = CompletableDeferred<Unit>()
                                val releaseRead = CompletableDeferred<Unit>()
                                daoField.set(
                                    helper,
                                    object : DatabaseDao by dao {
                                        override suspend fun get(id: Int): DatabaseBean? {
                                            if (id == first.id) {
                                                reading.complete(Unit)
                                                releaseRead.await()
                                            }
                                            return dao.get(id)
                                        }
                                    },
                                )
                                try {
                                    val loading = editor(type, first.id).also { active = it }
                                    reading.await()
                                    check(main { !loading.findViewById<View>(R.id.clip_edit_ok).isEnabled })
                                    check(main { !loading.findViewById<View>(R.id.clip_edit_text).isEnabled })
                                    main {
                                        instrumentation.callActivityOnNewIntent(
                                            loading,
                                            Intent().putExtra(ClipEditActivity.CLIP_TYPE, type)
                                                .putExtra(ClipEditActivity.BEAN_ID, second.id),
                                        )
                                    }
                                    eventually { main { loading.findViewById<EditText>(R.id.clip_edit_text).text.toString() == second.text } }
                                    releaseRead.complete(Unit)
                                    delay(200)
                                    check(main { loading.findViewById<EditText>(R.id.clip_edit_text).text.toString() == second.text })
                                    main {
                                        loading.findViewById<EditText>(R.id.clip_edit_text).setText("cancelled edit")
                                        loading.findViewById<View>(R.id.clip_edit_cancel).performClick()
                                    }
                                    eventually { main { loading.isDestroyed } }
                                    active = null
                                    check(dao.get(first.id)?.text == first.text && dao.get(second.id)?.text == second.text)
                                    checkPassed("$type: loading disables edits, new intent supersedes old read, cancel preserves both rows")
                                } finally {
                                    releaseRead.complete(Unit)
                                    daoField.set(helper, dao)
                                }
                            }
                            verifySupersededLoad()

                            suspend fun verifySupersededMissingLoad() {
                                val first = insert("stale-missing-${UUID.randomUUID()}")
                                val second = insert("current-${UUID.randomUUID()}")
                                main { cached.set(helper, second) }
                                val reading = CompletableDeferred<Unit>()
                                val releaseRead = CompletableDeferred<Unit>()
                                val readReturned = CompletableDeferred<Unit>()
                                val delayedFirst = AtomicBoolean()
                                daoField.set(
                                    helper,
                                    object : DatabaseDao by dao {
                                        override suspend fun get(id: Int): DatabaseBean? {
                                            if (id == first.id && delayedFirst.compareAndSet(false, true)) {
                                                reading.complete(Unit)
                                                val value = withContext(NonCancellable) {
                                                    releaseRead.await()
                                                    dao.delete(id)
                                                    dao.get(id)
                                                }
                                                readReturned.complete(Unit)
                                                return value
                                            }
                                            return dao.get(id)
                                        }
                                    },
                                )
                                try {
                                    val activity = editor(type, first.id).also { active = it }
                                    reading.await()
                                    main {
                                        instrumentation.callActivityOnNewIntent(
                                            activity,
                                            Intent().putExtra(ClipEditActivity.CLIP_TYPE, type)
                                                .putExtra(ClipEditActivity.BEAN_ID, second.id),
                                        )
                                    }
                                    val field = main { activity.findViewById<EditText>(R.id.clip_edit_text) }
                                    val ok = main { activity.findViewById<View>(R.id.clip_edit_ok) }
                                    eventually { main { field.text.toString() == second.text && ok.isEnabled } }
                                    main { field.setText("current draft must survive stale NotFound") }
                                    releaseRead.complete(Unit)
                                    readReturned.await()
                                    delay(200)
                                    check(main { !activity.isFinishing && ok.isEnabled && field.text.toString() == "current draft must survive stale NotFound" })
                                    check(main { field.error == null && cached.get(helper) == second })
                                    check(dao.get(first.id) == null && dao.get(second.id)?.text == second.text)
                                    main {
                                        instrumentation.callActivityOnNewIntent(
                                            activity,
                                            Intent().putExtra(ClipEditActivity.CLIP_TYPE, type)
                                                .putExtra(ClipEditActivity.BEAN_ID, first.id),
                                        )
                                    }
                                    eventually { main { activity.isDestroyed } }
                                    active = null
                                    check(main { cached.get(helper) == second })
                                    checkPassed("$type: stale missing read cannot close the new draft, while a current missing read exits normally")
                                } finally {
                                    releaseRead.complete(Unit)
                                    daoField.set(helper, dao)
                                }
                            }
                            verifySupersededMissingLoad()
                        } finally {
                            withContext(NonCancellable) {
                                active?.let { activity ->
                                    main { if (!activity.isFinishing) activity.finish() }
                                    eventually { main { activity.isDestroyed } }
                                }
                                daoField.set(helper, dao)
                                owned.forEach { dao.delete(it) }
                                main { cached.set(helper, previousCache) }
                            }
                        }
                    }
                }
            }
            check(editErrors.get() == 0) { "A normal clipboard edit outcome emitted an error log" }
            passed = true
            result.putString("stream", "PASS: $checks real clipboard/collection save and lifecycle checks; only owned rows removed\n")
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            Timber.uproot(errorObserver)
        }
        result.putInt("clip_edit_error_count", editErrors.get())
        result.putBoolean("passed", passed)
        result.putInt("checks", checks)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
