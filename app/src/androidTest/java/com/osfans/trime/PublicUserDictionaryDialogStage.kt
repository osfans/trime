/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.ConfigurationCompat
import androidx.core.view.children
import androidx.navigation.fragment.NavHostFragment
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.userdict.CustomPhraseStore
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.ui.main.settings.userdict.UserDictionaryFragment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.IdentityHashMap

/** Actual U-screen cancellation/validation only. Caller owns the Activity and whole-phase budgets. */
internal object PublicUserDictionaryDialogStage {
    fun verify(
        instrumentation: Instrumentation,
        activity: MainActivity,
        token: String,
        workDeadline: Long,
        directory: File,
        progress: JSONObject,
        onPassed: (String) -> Unit,
    ): JSONObject = Runner(instrumentation, activity, token, workDeadline, directory, progress, onPassed).run()

    /** Separate real malformed-parent-save path; the caller observes/dismisses its actual error UI. */
    fun verifyImportFailure(
        instrumentation: Instrumentation,
        activity: MainActivity,
        token: String,
        workDeadline: Long,
        directory: File,
        progress: JSONObject,
        onPassed: (String) -> Unit,
        onActualImportError: (String) -> JSONObject,
    ): JSONObject = Runner(
        instrumentation,
        activity,
        token,
        workDeadline,
        directory,
        progress,
        onPassed,
        onActualImportError,
    ).runImportFailure()

    private class Runner(
        private val instrumentation: Instrumentation,
        private val activity: MainActivity,
        private val token: String,
        private val workDeadline: Long,
        private val directory: File,
        private val progress: JSONObject,
        private val onPassed: (String) -> Unit,
        private val onActualImportError: ((String) -> JSONObject)? = null,
    ) {
        private var fragment: UserDictionaryFragment? = null
        private var parentDialog: AlertDialog? = null
        private var parentEditor: EditText? = null
        private val checks = JSONArray()
        private val windows = JSONArray()
        private val images = JSONArray()
        private val markers = JSONArray()
        private val phraseFile = File(DataManager.userDataDir, CustomPhraseStore.FILE_NAME)
        private val backupRoot = File(activity.filesDir, "userdict-backups")
        private lateinit var beforeTree: Map<String, String>

        private fun time() {
            check(SystemClock.uptimeMillis() <= workDeadline) { "U dialog stage exceeded caller work deadline" }
        }
        private fun <T> main(block: () -> T): T {
            time()
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(block) }
            val observed = checkNotNull(result)
            val businessFailure = observed.exceptionOrNull()
            try {
                time()
            } catch (deadlineFailure: Throwable) {
                if (businessFailure != null) {
                    if (businessFailure !== deadlineFailure) businessFailure.addSuppressed(deadlineFailure)
                    throw businessFailure
                }
                throw deadlineFailure
            }
            return observed.getOrThrow()
        }
        private fun await(label: String, test: () -> Boolean) {
            val limit = minOf(workDeadline, SystemClock.uptimeMillis() + 30_000L)
            while (!test()) {
                check(SystemClock.uptimeMillis() < limit) { label }
                SystemClock.sleep(50)
            }
            check(SystemClock.uptimeMillis() <= limit) { "$label (late)" }
        }
        private fun marker(label: String) {
            time()
            val value = "PUBLIC_USERDICT_DIALOG_${token}_${markers.length() + 1}_$label"
            markers.put(JSONObject().put("label", label).put("value", value).put("uptime_ms", SystemClock.uptimeMillis()))
            android.util.Log.i("TrimePublicHostAudit", value)
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("public_host_marker", value) })
        }
        private fun passed(label: String) {
            time()
            checks.put(label)
            onPassed(label)
        }
        private fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) view.children.forEach { yieldAll(descendants(it)) }
        }
        private fun field(name: String): Any? = UserDictionaryFragment::class.java.getDeclaredField(name).run {
            isAccessible = true
            get(checkNotNull(fragment))
        }
        private fun editorDialog(): AlertDialog? = field("editorDialog") as AlertDialog?
        private fun auxiliary(): List<AlertDialog> = (field("auxiliaryDialogs") as Set<*>).map { it as AlertDialog }
        private fun liveAuxiliary(): AlertDialog? = auxiliary().filter { it.isShowing }.singleOrNull()
        private fun laidOut(dialog: AlertDialog): Boolean = dialog.isShowing && dialog.window?.decorView?.let {
            it.isShown && it.hasWindowFocus() && it.width > 0 && it.height > 0 && !it.isLayoutRequested
        } == true
        private fun contextOwner(context: Context): Context? {
            val seen = IdentityHashMap<Context, Boolean>()
            var next: Context = context
            repeat(32) {
                check(seen.put(next, true) == null) { "Cyclic U context chain" }
                if (next === activity) return next
                next = (next as? ContextWrapper)?.baseContext ?: return null
            }
            error("U context chain too long")
        }
        private fun window(dialog: AlertDialog, label: String, title: Int) = main {
            check(dialog.javaClass == AlertDialog::class.java && laidOut(dialog))
            val root = checkNotNull(dialog.window).decorView
            check(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(title) })
            check(contextOwner(dialog.context) === activity)
            val config = dialog.context.resources.configuration
            check(config.fontScale == activity.resources.configuration.fontScale)
            check(
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK),
            )
            check(ConfigurationCompat.getLocales(config) == ConfigurationCompat.getLocales(activity.resources.configuration))
            windows.put(
                JSONObject().put("label", label).put("dialog_class", dialog.javaClass.name)
                    .put("window_identity", System.identityHashCode(dialog.window)).put("owner_identity", System.identityHashCode(activity))
                    .put("font_scale", config.fontScale.toDouble()).put("ui_mode", config.uiMode)
                    .put("locales", ConfigurationCompat.getLocales(config).toLanguageTags()),
            )
        }
        private fun touch(view: View) {
            val point = main {
                val visible = Rect()
                check(view.isShown && view.isEnabled && view.width > 0 && view.height > 0 && view.getLocalVisibleRect(visible))
                check(visible == Rect(0, 0, view.width, view.height))
                val xy = IntArray(2)
                view.getLocationOnScreen(xy)
                xy[0] + view.width / 2f to xy[1] + view.height / 2f
            }
            val down = SystemClock.uptimeMillis()
            for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.sendPointerSync(event)
                } finally {
                    event.recycle()
                }
                if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40)
            }
            time()
        }
        private fun button(dialog: AlertDialog, which: Int) = touch(main { checkNotNull(dialog.getButton(which)) })
        private fun item(dialog: AlertDialog, text: Int) {
            val row = main {
                val list = dialog.listView
                val expected = activity.getString(text)
                val index = (0 until list.count).single { list.adapter.getItem(it).toString() == expected }
                checkNotNull(list.getChildAt(index - list.firstVisiblePosition))
            }
            touch(row)
        }
        private fun capture(dialog: AlertDialog, label: String) {
            val root = main { checkNotNull(dialog.window).decorView }
            val rendered = RenderedScreenshotBarrier.capture(instrumentation, root) { check(dialog.isShowing && root.isShown && root.hasWindowFocus()) }
            try {
                val file = File(directory, "userdict-$label.png")
                file.outputStream().use { check(rendered.first.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                images.put(binding(file).put("barrier", rendered.second).put("human_reviewed", false))
            } finally {
                rendered.first.recycle()
            }
            time()
        }
        private fun digest(file: File): String {
            val sha = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            file.inputStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    sha.update(buffer, 0, n)
                    time()
                }
            }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }
        private fun binding(file: File): JSONObject = JSONObject().put("path", file.absolutePath).put("bytes", file.length()).put("sha256", digest(file))
        private fun tree(): Map<String, String> {
            var total = 0L
            val result = sortedMapOf<String, String>()
            fun add(file: File, key: String) {
                time()
                val stat = Os.lstat(file.absolutePath)
                check(!OsConstants.S_ISLNK(stat.st_mode)) { "Unexpected symbolic link in dedicated U fixture" }
                if (file.isDirectory) {
                    result[key] = "directory"
                } else {
                    check(file.isFile)
                    total += file.length()
                    check(total <= 16L * 1024 * 1024)
                    result[key] = "${file.length()}:${digest(file)}"
                }
                check(result.size <= 256)
            }
            fun existsNoFollow(file: File): Boolean = try {
                Os.lstat(file.absolutePath)
                true
            } catch (failure: ErrnoException) {
                if (failure.errno == OsConstants.ENOENT) false else throw failure
            }
            if (existsNoFollow(phraseFile)) add(phraseFile, "phrase") else result["phrase"] = "absent"
            if (!existsNoFollow(backupRoot)) {
                result["backups"] = "absent"
            } else {
                backupRoot.walkTopDown().onEnter { dir ->
                    val stat = Os.lstat(dir.absolutePath)
                    check(!OsConstants.S_ISLNK(stat.st_mode))
                    true
                }.forEach { add(it, "backups/${it.relativeTo(backupRoot).invariantSeparatorsPath}") }
            }
            return result
        }
        private fun treeArtifact(label: String, value: Map<String, String>): JSONObject {
            val file = File(directory, "userdict-$label-tree.json")
            file.writeText(JSONObject(value).toString(2) + "\n")
            return binding(file)
        }
        private fun phraseButton(): Button = main {
            descendants(checkNotNull(fragment).requireView()).filterIsInstance<Button>().single {
                it.text.toString() == activity.getString(R.string.userdict_phrases) && it.isShown && it.isEnabled
            }.also { check(it.javaClass == Button::class.java) }
        }
        private fun actions(): AlertDialog {
            marker("actions_show_begin")
            touch(phraseButton())
            var result: AlertDialog? = null
            await("Actual U actions window did not show") {
                main {
                    result = liveAuxiliary()
                    result?.let(::laidOut) == true
                }
            }
            return checkNotNull(result).also {
                window(it, "phrase_actions", R.string.userdict_phrases)
                marker("actions_show_ready")
            }
        }
        private fun openEditor() {
            val choices = actions()
            marker("editor_show_begin")
            item(choices, R.string.userdict_edit_phrases)
            await("Actual U editor read operation did not show a ready dialog") { main { editorDialog()?.let(::laidOut) == true } }
            main {
                parentDialog = editorDialog()
                parentEditor = descendants(checkNotNull(parentDialog?.window).decorView).filterIsInstance<EditText>().single()
                check(checkNotNull(parentEditor).javaClass == EditText::class.java)
                check(checkNotNull(checkNotNull(parentEditor).parent).javaClass == ScrollView::class.java)
                check(contextOwner(checkNotNull(parentEditor).context) === activity)
                check(checkNotNull(parentDialog).getButton(DialogInterface.BUTTON_NEUTRAL).isEnabled)
                check(checkNotNull(parentDialog).getButton(DialogInterface.BUTTON_POSITIVE).isEnabled)
            }
            window(checkNotNull(parentDialog), "phrase_editor", R.string.userdict_edit_phrases)
            marker("editor_show_ready")
        }
        private fun addPhrase(): AlertDialog {
            marker("add_phrase_show_begin")
            button(checkNotNull(parentDialog), DialogInterface.BUTTON_NEUTRAL)
            var child: AlertDialog? = null
            await("Actual Add phrase child did not show") {
                main {
                    child = liveAuxiliary()
                    child?.let(::laidOut) == true
                }
            }
            return checkNotNull(child).also {
                window(it, "add_phrase", R.string.userdict_add_phrase)
                marker("add_phrase_show_ready")
            }
        }
        private fun childFields(dialog: AlertDialog): Pair<EditText, EditText> = main {
            val inputs = descendants(checkNotNull(dialog.window).decorView).filterIsInstance<EditText>().toList()
            check(inputs.size == 2 && inputs.all { it.javaClass == EditText::class.java })
            val phrase = inputs.single { it.hint.toString() == activity.getString(R.string.userdict_phrase) }
            val code = inputs.single { it.hint.toString() == activity.getString(R.string.userdict_phrase_code) }
            check(phrase.parent === code.parent && checkNotNull(phrase.parent).javaClass == LinearLayout::class.java)
            check(contextOwner(phrase.context) === activity && contextOwner(code.context) === activity)
            phrase to code
        }
        private fun routeAndSnapshot() {
            beforeTree = tree()
            marker("route_begin")
            main {
                check(!activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus())
                activity.startActivity(
                    Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_RUN)
                        .putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, NavigationRoute.UserDict),
                )
            }
            await("Product UserDict route and phrase button were not ready") {
                main {
                    val nav = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    fragment = nav?.childFragmentManager?.primaryNavigationFragment as? UserDictionaryFragment
                    fragment?.view?.let { root ->
                        descendants(root).filterIsInstance<Button>().any {
                            it.text.toString() == activity.getString(R.string.userdict_phrases) && it.isShown && it.isEnabled
                        }
                    } == true
                }
            }
            main { check(editorDialog() == null && auxiliary().isEmpty()) }
        }
        private fun validateAndAppend() {
            openEditor()
            val parent = checkNotNull(parentDialog)
            val ownedDraft = main { checkNotNull(parentEditor).text.toString() + "\n# __public_u_${token}__" }
            main { checkNotNull(parentEditor).setText(ownedDraft) }
            val child = addPhrase()
            val fields = childFields(child)
            for (invalid in listOf("", "bad\tphrase", "bad\nphrase", "#invalid")) {
                main {
                    fields.first.setText(invalid)
                    fields.second.setText("virtual-code")
                }
                button(child, DialogInterface.BUTTON_POSITIVE)
                await("U invalid phrase did not keep its real child/draft and field error") {
                    main {
                        child.isShowing && fields.first.error?.toString() == activity.getString(R.string.userdict_phrase_invalid) &&
                            fields.first.text.toString() == invalid && fields.second.text.toString() == "virtual-code" &&
                            parent.isShowing && checkNotNull(parentEditor).text.toString() == ownedDraft
                    }
                }
            }
            capture(child, "invalid-child")
            val phrase = "虚构短语${token.take(8)}"
            val code = "probe${token.take(6)}"
            main {
                fields.first.setText(phrase)
                fields.second.setText(code)
            }
            button(child, DialogInterface.BUTTON_POSITIVE)
            val expected = ownedDraft + "\n$phrase\t$code\t1\n"
            await("Valid U child did not append exactly to the uncommitted parent draft") {
                main { !child.isShowing && parent.isShowing && checkNotNull(parentEditor).text.toString() == expected }
            }
            capture(parent, "parent-uncommitted")
            check(tree() == beforeTree)
            passed("real U invalid field error preserves draft and valid child appends exact virtual TSV without persistence")
        }
        private fun childAndParentCancel() {
            val beforeDraft = main { checkNotNull(parentEditor).text.toString() }
            val child = addPhrase()
            val fields = childFields(child)
            main {
                fields.first.setText("取消虚构词")
                fields.second.setText("cancel")
            }
            button(child, DialogInterface.BUTTON_NEGATIVE)
            await("U child Cancel changed draft or did not close") { main { !child.isShowing && checkNotNull(parentEditor).text.toString() == beforeDraft } }
            val parent = checkNotNull(parentDialog)
            button(parent, DialogInterface.BUTTON_NEGATIVE)
            await("U parent Cancel did not release its owned editor reference") { main { !parent.isShowing && editorDialog() == null && auxiliary().isEmpty() } }
            parentDialog = null
            parentEditor = null
            check(tree() == beforeTree)
            passed("real U child and parent negative callbacks preserve phrase and backup bytes")
        }
        private fun recoveryCancel() {
            marker("recovery_show_begin")
            item(actions(), R.string.userdict_recover_backup)
            var dialog: AlertDialog? = null
            await("Actual U recovery confirmation did not show") {
                main {
                    dialog = liveAuxiliary()
                    dialog?.let(::laidOut) == true
                }
            }
            val recovery = checkNotNull(dialog)
            window(recovery, "recovery_confirmation", R.string.userdict_recover_backup)
            marker("recovery_show_ready")
            button(recovery, DialogInterface.BUTTON_NEGATIVE)
            await("U recovery Cancel did not close its auxiliary reference") { main { !recovery.isShowing && auxiliary().isEmpty() } }
            check(tree() == beforeTree)
            passed("real U recovery confirmation Cancel does not invoke dictionary recovery or change backups")
        }
        private fun ownerTeardown() {
            openEditor()
            val parent = checkNotNull(parentDialog)
            val child = addPhrase()
            val fields = childFields(child)
            main {
                fields.first.setText("终止虚构草稿")
                fields.second.setText("teardown")
            }
            marker("owner_teardown_begin")
            // Caller owns this fresh Main; normal public Activity finish drives real onDestroyView.
            main { activity.finish() }
            await("Normal owner teardown did not dismiss both real U windows") {
                main { activity.isDestroyed && !parent.isShowing && !child.isShowing && editorDialog() == null && auxiliary().isEmpty() }
            }
            check(tree() == beforeTree)
            passed("normal owned Main teardown dismisses real U parent/child and clears references without persistence")
            marker("owner_teardown_end")
        }
        private fun phraseTemporaryFiles(): Map<String, String> {
            val root = DataManager.userDataDir
            val files = checkNotNull(root.listFiles()).filter { it.name.startsWith(".custom-phrase-") }
            check(files.size <= 32)
            return files.sortedBy { it.name }.associate { file ->
                time()
                val stat = Os.lstat(file.absolutePath)
                check(OsConstants.S_ISREG(stat.st_mode) && !OsConstants.S_ISLNK(stat.st_mode))
                check(file.length() <= 256L * 1024)
                file.name to "${file.length()}:${digest(file)}"
            }
        }

        private fun invalidParentSave() {
            openEditor()
            val parent = checkNotNull(parentDialog)
            val editor = checkNotNull(parentEditor)
            val invalid = "__public_invalid_phrase_${token}__" // Exactly one non-comment line, without TAB.
            main {
                editor.setText(invalid)
                editor.setSelection(4, 11)
            }
            val selection = main { editor.selectionStart to editor.selectionEnd }
            val beforeTemporary = phraseTemporaryFiles()
            marker("invalid_parent_save_begin")
            button(parent, DialogInterface.BUTTON_POSITIVE)
            // This callback is the existing host's real public title/message/OK UI path,
            // not a fabricated Dialog or direct call to importErrorDialog/CustomPhraseStore.
            val observed = checkNotNull(onActualImportError)("Invalid dictionary entry at line 1")
            progress.put("actual_import_error", observed)
            await("Shared import-error OK did not restore the same failed parent draft") {
                main {
                    parent.isShowing && parent.window?.decorView?.hasWindowFocus() == true &&
                        editorDialog() === parent && parentEditor === editor && editor.isEnabled &&
                        editor.text.toString() == invalid &&
                        editor.selectionStart == selection.first && editor.selectionEnd == selection.second &&
                        field("operationInProgress") == false &&
                        parent.getButton(DialogInterface.BUTTON_NEUTRAL).isEnabled &&
                        parent.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled
                }
            }
            check(tree() == beforeTree)
            check(phraseTemporaryFiles() == beforeTemporary)
            capture(parent, "invalid-parent-after-error-OK")
            progress.put("parent_invalid_save_tested", true).put("invalid_parent_draft_unchanged", true)
                .put("invalid_parent_selection_unchanged", true)
                .put("phrase_backups_unchanged_after_failure", true)
                .put("temporary_files_unchanged_after_failure", true)
            marker("invalid_parent_save_error_OK_terminal")
            passed("real U invalid parent Save reaches shared import-error UI and preserves draft selection phrase backups and temporary files")
            val readyPhraseButton = phraseButton()
            button(parent, DialogInterface.BUTTON_NEGATIVE)
            await("Failed U parent Cancel did not release its real editor or restore controls") {
                main { !parent.isShowing && editorDialog() == null && auxiliary().isEmpty() && readyPhraseButton.isEnabled }
            }
            parentDialog = null
            parentEditor = null
            check(tree() == beforeTree && phraseTemporaryFiles() == beforeTemporary)
            marker("invalid_parent_save_cancel_terminal")
            passed("real shared error OK then parent Cancel leaves phrase backup and temporary bytes unchanged")
        }

        fun runImportFailure(): JSONObject {
            progress.put("checks", checks).put("windows", windows).put("screenshots", images).put("markers", markers)
                .put("scope", "Real malformed U editor Save to shared importErrorDialog; no successful persistence or storage rollback claim")
                .put("parent_positive_save_tested", false).put("parent_invalid_save_tested", false)
                .put("transaction_failure_recovery_tested", false).put("error_dialog_class_observed", false)
                .put("progress_200ms_tested", false).put("human_png_reviewed", false)
            try {
                check(onActualImportError != null)
                routeAndSnapshot()
                progress.put("before_tree", treeArtifact("import-error-before", beforeTree))
                invalidParentSave()
                val after = tree()
                check(after == beforeTree && checks.length() == 2)
                progress.put("after_tree", treeArtifact("import-error-after", after)).put("stage_complete", true)
                return progress
            } catch (failure: Throwable) {
                progress.put("stage_complete", false).put("error", failure.stackTraceToString())
                throw failure
            } finally {
                // Existing host finally still owns normal Activity/session cleanup on failure.
                parentEditor = null
                parentDialog = null
                fragment = null
            }
        }

        fun run(): JSONObject {
            progress.put("checks", checks).put("windows", windows).put("screenshots", images).put("markers", markers)
                .put("scope", "Real U validation/cancel/owner teardown only")
                .put("parent_positive_save_tested", false).put("transaction_failure_recovery_tested", false)
                .put("action_mode_tested", false).put("human_png_reviewed", false)
            try {
                routeAndSnapshot()
                progress.put("before_tree", treeArtifact("before", beforeTree))
                validateAndAppend()
                childAndParentCancel()
                recoveryCancel()
                ownerTeardown()
                val after = tree()
                check(after == beforeTree && checks.length() == 4)
                progress.put("after_tree", treeArtifact("after", after)).put("stage_complete", true)
                return progress
            } catch (failure: Throwable) {
                progress.put("stage_complete", false).put("error", failure.stackTraceToString())
                throw failure
            } finally {
                // Caller finally owns normal Activity cleanup even when a stage fails. Release only
                // local audit references; never delete/restore production dictionary files on failure.
                parentEditor = null
                parentDialog = null
                fragment = null
            }
        }
    }
}
