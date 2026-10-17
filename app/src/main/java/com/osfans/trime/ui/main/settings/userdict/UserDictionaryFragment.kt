/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.userdict.CustomPhraseStore
import com.osfans.trime.data.userdict.UserDictManager
import com.osfans.trime.ime.core.EditorPolicy
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.importErrorDialog
import com.osfans.trime.util.item
import io.github.whiredplanck.storageaccess.StorageAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class UserDictionaryFragment : Fragment() {
    private val storageAccess = StorageAccess(this)
    private val viewModel: MainViewModel by activityViewModels()
    private var popupMenu: PopupMenu? = null
    private var editorDialog: AlertDialog? = null
    private val auxiliaryDialogs = mutableSetOf<AlertDialog>()
    private var operationInProgress = false

    private val ui: UserDictListUi by lazy {
        UserDictListUi(requireContext(), emptyArray()) { dictName ->
            setOnClickListener {
                if (operationInProgress) return@setOnClickListener
                val popup = PopupMenu(requireContext(), this)
                popup.menu.item(R.string.backup) {
                    operate {
                        viewModel.rime.runOnReady {
                            maintainUserDictionaries { UserDictManager.backupForRecovery(dictName).getOrThrow() }
                        }
                        ui.showSnackBar(getString(R.string.backed_up_x_to_sync_dir, dictName))
                    }
                }
                popup.menu.item(R.string.import_) {
                    operate {
                        val picked = storageAccess.pickFile(mimeTypes = arrayOf("text/plain")) ?: return@operate
                        val count = readDocument(picked.uri) { input ->
                            viewModel.rime.runOnReady {
                                maintainUserDictionaries {
                                    UserDictManager.importUserDict(input, dictName, picked.name).getOrThrow()
                                }
                            }
                        }
                        ui.showSnackBar(resources.getQuantityString(R.plurals.import_n_entries, count, count))
                    }
                }
                popup.menu.item(R.string.export) {
                    operate {
                        val doc = storageAccess.createFile(filename = "$dictName.txt", mimeType = "text/plain") ?: return@operate
                        val count = writeDocument(doc.uri) { output ->
                            viewModel.rime.runOnReady {
                                maintainUserDictionaries {
                                    UserDictManager.exportUserDict(output, dictName, doc.name).getOrThrow()
                                }
                            }
                        }
                        ui.showSnackBar(resources.getQuantityString(R.plurals.exported_n_entries, count, count))
                    }
                }
                popup.menu.item(R.string.userdict_recover_backup) {
                    confirmRecovery {
                        operate {
                            viewModel.rime.runOnReady {
                                maintainUserDictionaries { UserDictManager.recoverUserDict(dictName).getOrThrow() }
                            }
                            refreshDictionaries()
                            ui.showSnackBar(getString(R.string.userdict_recovery_complete))
                        }
                    }
                }
                popup.setOnDismissListener { if (it === popupMenu) popupMenu = null }
                popupMenu?.dismiss()
                popupMenu = popup
                popup.show()
            }
        }.apply {
            fab.contentDescription = getString(R.string.userdict_merge_snapshot)
            fab.setOnClickListener {
                operate {
                    val picked = storageAccess.pickFile(mimeTypes = arrayOf("text/plain")) ?: return@operate
                    readDocument(picked.uri) { input ->
                        viewModel.rime.runOnReady {
                            maintainUserDictionaries {
                                UserDictManager.restoreUserDict(input, picked.name).getOrThrow()
                            }
                        }
                    }
                    refreshDictionaries()
                    ui.showSnackBar(getString(R.string.restored_from_x, picked.name))
                }
            }
            phraseButton.setOnClickListener { showPhraseActions() }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = ui.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        operate { refreshDictionaries() }
    }

    override fun onDestroyView() {
        popupMenu?.dismiss()
        editorDialog?.dismiss()
        editorDialog = null
        auxiliaryDialogs.toList().forEach { it.dismiss() }
        super.onDestroyView()
    }

    private suspend fun refreshDictionaries() {
        val entries = viewModel.rime.runOnReady {
            maintainUserDictionaries { UserDictManager.getUserDictList().filterNot { it.startsWith('.') }.sorted() }
        }
        ui.adapter.submitList(entries)
    }

    private fun operate(block: suspend () -> Unit) {
        if (operationInProgress) return
        val ctx = requireContext()
        operationInProgress = true
        ui.fab.isEnabled = false
        ui.phraseButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                ctx.importErrorDialog(failure)
            } finally {
                operationInProgress = false
                ui.fab.isEnabled = true
                ui.phraseButton.isEnabled = true
            }
        }
    }

    private fun confirmRecovery(action: () -> Unit) {
        trackDialog(
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.userdict_recover_backup)
                .setMessage(R.string.userdict_recovery_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> action() }
                .show(),
        )
    }

    private fun trackDialog(dialog: AlertDialog) {
        auxiliaryDialogs.add(dialog)
        dialog.setOnDismissListener { auxiliaryDialogs.remove(dialog) }
    }

    private fun showPhraseActions() {
        if (operationInProgress) return
        val options = arrayOf(
            getString(R.string.userdict_edit_phrases),
            getString(R.string.import_),
            getString(R.string.export),
            getString(R.string.userdict_recover_backup),
        )
        trackDialog(
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.userdict_phrases)
                .setItems(options) { _, selected ->
                    when (selected) {
                        0 -> operate {
                            val text = viewModel.rime.runOnReady { maintainUserDictionaries { CustomPhraseStore.read() } }
                            showPhraseEditor(text)
                        }

                        1 -> operate {
                            val picked = storageAccess.pickFile(mimeTypes = arrayOf("text/plain")) ?: return@operate
                            val count = readDocument(picked.uri) { input ->
                                viewModel.rime.runOnReady {
                                    maintainUserDictionaries { CustomPhraseStore.importPhrases(input) }
                                }
                            }
                            ui.showSnackBar(resources.getQuantityString(R.plurals.import_n_entries, count, count))
                        }

                        2 -> operate {
                            val doc = storageAccess.createFile(filename = CustomPhraseStore.FILE_NAME, mimeType = "text/plain") ?: return@operate
                            writeDocument(doc.uri) { output ->
                                viewModel.rime.runOnReady {
                                    maintainUserDictionaries { CustomPhraseStore.export(output) }
                                }
                            }
                            ui.showSnackBar(getString(R.string.userdict_phrases_exported))
                        }

                        3 -> confirmRecovery {
                            operate {
                                viewModel.rime.runOnReady { maintainUserDictionaries { CustomPhraseStore.recover() } }
                                ui.showSnackBar(getString(R.string.userdict_recovery_complete))
                            }
                        }
                    }
                }
                .show(),
        )
    }

    private suspend fun <T> readDocument(uri: Uri, operation: suspend (InputStream) -> T): T {
        val resolver = requireContext().contentResolver
        return withContext(Dispatchers.IO) {
            (resolver.openInputStream(uri) ?: throw IOException("Cannot open dictionary document")).use { operation(it) }
        }
    }

    private suspend fun <T> writeDocument(uri: Uri, operation: suspend (OutputStream) -> T): T {
        val resolver = requireContext().contentResolver
        return withContext(Dispatchers.IO) {
            (resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot write dictionary document")).use { operation(it) }
        }
    }

    private fun showPhraseEditor(text: String) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = imeOptions or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG
            setText(text)
            minLines = 5
            setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.userdict_edit_phrases)
            .setMessage(R.string.userdict_phrase_help)
            .setView(ScrollView(ctx).apply { addView(input) })
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.userdict_add_phrase, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        editorDialog?.dismiss()
        editorDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { appendPhrase(input) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (operationInProgress) return@setOnClickListener
                val updated = input.text.toString()
                input.isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                operate {
                    try {
                        viewModel.rime.runOnReady { maintainUserDictionaries { CustomPhraseStore.save(updated) } }
                        dialog.dismiss()
                        ui.showSnackBar(getString(R.string.userdict_phrases_saved))
                    } finally {
                        input.isEnabled = true
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                    }
                }
            }
        }
        dialog.setOnDismissListener { if (editorDialog === dialog) editorDialog = null }
        dialog.show()
    }

    private fun appendPhrase(editor: EditText) {
        val ctx = requireContext()
        val phrase = EditText(ctx).apply {
            hint = getString(R.string.userdict_phrase)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = imeOptions or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG
        }
        val code = EditText(ctx).apply {
            hint = getString(R.string.userdict_phrase_code)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = imeOptions or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG
        }
        val fields = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(8))
            addView(phrase)
            addView(code)
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.userdict_add_phrase)
            .setView(fields)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = phrase.text.toString().trim()
                val spelling = code.text.toString().trim()
                if (value.isEmpty() || spelling.isEmpty() || value.any { it == '\t' || it == '\n' || it == '\r' } ||
                    spelling.any { it == '\t' || it == '\n' || it == '\r' } || value.startsWith('#')
                ) {
                    phrase.error = getString(R.string.userdict_phrase_invalid)
                } else {
                    if (editor.text.isNotEmpty() && !editor.text.endsWith('\n')) editor.append("\n")
                    editor.append("$value\t$spelling\t1\n")
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
        trackDialog(dialog)
    }
}
