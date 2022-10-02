package com.osfans.trime.ui.fragments

import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.get
import com.blankj.utilcode.util.ResourceUtils
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.ui.components.FolderPickerPreference
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.UriUtils.toFile
import com.osfans.trime.util.appContext
import com.osfans.trime.util.formatDateTime
import com.osfans.trime.util.withLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileFragment : PaddingPreferenceFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    private fun FolderPickerPreference.registerDocumentTreeLauncher() {
        documentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it ?: return@registerForActivityResult
            val uri = DocumentsContract.buildDocumentUriUsingTree(
                it,
                DocumentsContract.getTreeDocumentId(it)
            )
            val file = uri.toFile()
            dialogView.editText.setText(file?.toURI()?.normalize()?.path)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.profile_preference)
        with(preferenceScreen) {
            get<FolderPickerPreference>("profile_shared_data_dir")?.apply {
                setDefaultValue(DataManager.defaultDataDirectory.path)
                registerDocumentTreeLauncher()
            }
            get<FolderPickerPreference>("profile_user_data_dir")?.apply {
                setDefaultValue(DataManager.defaultDataDirectory.path)
                registerDocumentTreeLauncher()
            }
            get<Preference>("profile_sync_user_data")?.setOnPreferenceClickListener {
                lifecycleScope.withLoadingDialog(context, 200L, R.string.sync_progress) {
                    withContext(Dispatchers.IO) {
                        Rime.sync_user_data()
                        Rime.destroy()
                        Rime.get(true)
                    }
                }
                true
            }
            get<SwitchPreferenceCompat>("profile_sync_in_background")?.apply {
                val lastBackgroundSync = prefs.profile.lastBackgroundSync
                summaryOn =
                    if (lastBackgroundSync.isBlank()) {
                        context.getString(R.string.profile_never_sync_in_background)
                    } else {
                        context.getString(
                            R.string.profile_last_sync_in_background,
                            formatDateTime(lastBackgroundSync.toLong()),
                            context.getString(
                                if (prefs.profile.lastSyncStatus) {
                                    R.string.success
                                } else {
                                    R.string.failure
                                }
                            )
                        )
                    }
                summaryOff = context.getString(R.string.profile_enable_syncing_in_background)
            }
            get<Preference>("profile_reset")?.setOnPreferenceClickListener {
                val items = appContext.assets.list("rime")!!
                val checkedItems = items.map { false }.toBooleanArray()
                AlertDialog.Builder(context)
                    .setTitle(R.string.profile_reset)
                    .setMultiChoiceItems(items, checkedItems) { _, id, isChecked ->
                        checkedItems[id] = isChecked
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        var res = true
                        lifecycleScope.withLoadingDialog(context) {
                            withContext(Dispatchers.IO) {
                                for ((i, a) in items.withIndex()) {
                                    if (checkedItems[i]) {
                                        res = res and (
                                            runCatching {
                                                ResourceUtils.copyFileFromAssets(
                                                    "rime/$a",
                                                    "${DataManager.sharedDataDir.absolutePath}/$a"
                                                )
                                            }.getOrNull() ?: false
                                            )
                                    }
                                }
                            }
                        }
                        ToastUtils.showShort(
                            if (res) R.string.reset_success else R.string.reset_failure
                        )
                    }.show()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_profile))
        viewModel.disableTopOptionsMenu()
    }
}
