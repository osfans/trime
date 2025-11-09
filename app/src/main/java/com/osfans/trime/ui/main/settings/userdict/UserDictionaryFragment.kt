/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.content.ContentResolver
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.userdict.UserDictManager
import com.osfans.trime.util.importErrorDialog
import com.osfans.trime.util.item
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserDictionaryFragment : Fragment() {
    private lateinit var restoreLauncher: ActivityResultLauncher<String>

    private lateinit var importLauncher: ActivityResultLauncher<String>

    private lateinit var exportLauncher: ActivityResultLauncher<String>

    private var popupMenu: PopupMenu? = null

    private var beingImported: String? = null

    private var beingExported: String? = null

    private val ui: UserDictListUi by lazy {
        UserDictListUi(
            requireContext(),
            UserDictManager.getUserDictList(),
        ) { dictName ->
            setOnClickListener {
                val popup = PopupMenu(requireContext(), this)
                val menu = popup.menu
                menu.item(R.string.backup) {
                    lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                        if (UserDictManager.backupUserDict(dictName)) {
                            ui.showSnackBar(
                                requireContext().getString(
                                    R.string.backed_up_x_to_sync_dir,
                                    dictName,
                                ),
                            )
                        }
                    }
                }
                menu.item(R.string.import_) {
                    beingImported = dictName
                    importLauncher.launch("text/plain")
                }
                menu.item(R.string.export) {
                    beingExported = dictName
                    exportLauncher.launch("$dictName.txt")
                }
                popup.setOnDismissListener {
                    if (it === popupMenu) popupMenu = null
                }
                popupMenu?.dismiss()
                popupMenu = popup
                popup.show()
            }
        }.apply {
            fab.setOnClickListener {
                restoreLauncher.launch("text/plain")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        registerLauncher()
        return ui.root
    }

    private fun registerLauncher() {
        restoreLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            importFromUri(uri, merge = true)
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            importFromUri(uri)
        }
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@registerForActivityResult
            val ctx = requireContext()
            val cr = ctx.contentResolver
            val dictName = beingExported ?: return@registerForActivityResult
            beingExported = null
            lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                val fileName = cr.queryFileName(uri) ?: return@launch
                try {
                    val outputStream = cr.openOutputStream(uri)!!
                    val count = UserDictManager.exportUserDict(
                        outputStream,
                        dictName,
                        fileName,
                    ).getOrThrow()
                    ui.showSnackBar(ctx.getString(R.string.exported_n_entries, count))
                } catch (e: Exception) {
                    withContext(Dispatchers.Main.immediate) {
                        ctx.toast(e)
                    }
                }
            }
        }
    }

    private fun importFromUri(uri: Uri, merge: Boolean = false) {
        val ctx = requireContext()
        val cr = ctx.contentResolver
        lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
            val fileName = cr.queryFileName(uri) ?: return@launch
            try {
                val inputStream = cr.openInputStream(uri)!!
                if (merge) {
                    val result = UserDictManager.restoreUserDict(inputStream, fileName)
                    if (result.isSuccess) {
                        ui.showSnackBar(ctx.getString(R.string.restored_from_x, fileName))
                        ContextCompat.getMainExecutor(requireContext()).execute {
                            ui.adapter.submitList(UserDictManager.getUserDictList().toList())
                        }
                    }
                } else {
                    val dictName = beingImported ?: return@launch
                    beingImported = null
                    val count = UserDictManager.importUserDict(
                        inputStream,
                        dictName,
                        fileName,
                    ).getOrThrow()
                    ui.showSnackBar(ctx.getString(R.string.import_n_entries, count))
                }
            } catch (e: Exception) {
                ctx.importErrorDialog(e)
            }
        }
    }

    fun ContentResolver.queryFileName(uri: Uri): String? = query(uri, null, null, null, null)?.use {
        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        it.getString(index)
    }
}
