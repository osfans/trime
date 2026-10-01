/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.userdict.UserDictManager
import com.osfans.trime.util.importErrorDialog
import com.osfans.trime.util.item
import com.osfans.trime.util.toast
import io.planck.storageaccess.StorageAccess
import io.planck.storageaccess.StorageDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserDictionaryFragment : Fragment() {
    private val storageAccess = StorageAccess(this)

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
                    lifecycleScope.launch {
                        val success = withContext(Dispatchers.IO) {
                            UserDictManager.backupUserDict(dictName)
                        }
                        if (success) {
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
                    lifecycleScope.launch {
                        val picked = storageAccess.pickFile(mimeTypes = arrayOf("text/palin"))
                            ?: return@launch
                        importFromDoc(picked)
                    }
                }
                menu.item(R.string.export) {
                    beingExported = dictName
                    val ctx = requireContext()
                    lifecycleScope.launch {
                        try {
                            val doc = storageAccess.createFile(
                                filename = "$dictName.txt",
                                mimeType = "text/plain",
                            ) ?: return@launch
                            val count = withContext(Dispatchers.IO) {
                                ctx.contentResolver.openOutputStream(doc.uri)!!.buffered().use { outs ->
                                    UserDictManager.exportUserDict(outs, dictName, doc.name)
                                }
                            }.getOrThrow()
                            ui.showSnackBar(ctx.getString(R.string.exported_n_entries, count))
                        } catch (e: Throwable) {
                            ctx.toast(e)
                        }
                    }
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
                lifecycleScope.launch {
                    val picked = storageAccess.pickFile(mimeTypes = arrayOf("text/plain"))
                        ?: return@launch
                    importFromDoc(picked, merge = true)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ui.root

    private fun importFromDoc(doc: StorageDocument, merge: Boolean = false) {
        val ctx = requireContext()
        lifecycleScope.launch {
            try {
                if (merge) {
                    val result = StorageAccess.readFile(doc.uri) { ins ->
                        UserDictManager.restoreUserDict(ins, doc.name)
                    }
                    if (result.isSuccess) {
                        ui.showSnackBar(ctx.getString(R.string.restored_from_x, doc.name))
                        ui.adapter.submitList(UserDictManager.getUserDictList().toList())
                    }
                } else {
                    val dictName = beingImported ?: return@launch
                    beingImported = null
                    val count = StorageAccess.readFile(doc.uri) { ins ->
                        UserDictManager.importUserDict(ins, dictName, doc.name)
                            .getOrThrow()
                    }
                    ui.showSnackBar(ctx.getString(R.string.import_n_entries, count))
                }
            } catch (e: Exception) {
                ctx.importErrorDialog(e)
            }
        }
    }
}
