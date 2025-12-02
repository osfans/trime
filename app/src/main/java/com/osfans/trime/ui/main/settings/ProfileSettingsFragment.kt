/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.customFormatTimeInDefault
import com.osfans.trime.util.getFileFromUri
import com.osfans.trime.util.getUriForFile
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.styledColor
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.editText
import splitties.views.dsl.core.imageButton
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.topPadding
import java.io.File

class ProfileSettingsFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.Companion.defaultInstance().profile
    private val backgroundSyncEnable = prefs.periodicBackgroundSync
    private val lastSyncTime by prefs.lastBackgroundSyncTime
    private val lastSyncStatus by prefs.lastBackgroundSyncStatus

    private val onBackgroundSyncEnable = PreferenceDelegate.OnChangeListener<Boolean> { _, v ->
        editSyncIntervalPreference.isEnabled = v
    }

    private val onSyncIntervalChange =
        PreferenceDelegate.OnChangeListener<Int> { _, _ ->
            if (backgroundSyncEnable.getValue()) {
                viewModel.restartBackgroundSyncWork.value = true
            }
        }

    private lateinit var browseLauncher: ActivityResultLauncher<Uri?>
    private var launcherResultCallback: ((path: String) -> Unit)? = null

    private lateinit var editSyncIntervalPreference: EditTextIntPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.periodicBackgroundSync.registerOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncInterval.registerOnChangeListener(onSyncIntervalChange)
        browseLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it ?: return@registerForActivityResult
            val uri =
                DocumentsContract.buildDocumentUriUsingTree(
                    it,
                    DocumentsContract.getTreeDocumentId(it),
                ) ?: return@registerForActivityResult
            val path = requireContext().getFileFromUri(uri)?.absolutePath ?: return@registerForActivityResult
            launcherResultCallback?.invoke(path)
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addCategory(R.string.storage) {
                isIconSpaceReserved = false
                addPreference(
                    Preference(requireContext()).apply {
                        key = AppPrefs.Profile.USER_DATA_DIR
                        isIconSpaceReserved = false
                        setTitle(R.string.user_data_dir)
                        setDefaultValue(DataManager.defaultDataDir.absolutePath)
                        summaryProvider = Preference.SummaryProvider<Preference> {
                            prefs.userDataDir.getValue()
                        }
                        setOnPreferenceClickListener {
                            val dirNameText = ctx.editText {
                                setText(prefs.userDataDir.getValue())
                            }
                            launcherResultCallback = { path ->
                                dirNameText.setText(path)
                            }
                            val browseButton = ctx.imageButton {
                                imageDrawable = ctx.drawable(R.drawable.ic_baseline_more_horiz_24)!!.apply {
                                    setTint(styledColor(android.R.attr.colorControlNormal))
                                }
                                setOnClickListener {
                                    val currentValue = prefs.userDataDir.getValue()
                                    browseLauncher.launch(ctx.getUriForFile(File(currentValue)))
                                }
                            }
                            val dialogContent = ctx.constraintLayout {
                                layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
                                topPadding = dp(8)
                                add(
                                    dirNameText,
                                    lParams(matchConstraints, wrapContent) {
                                        centerVertically()
                                        startOfParent(dp(20))
                                        endToStartOf(browseButton, dp(2))
                                    },
                                )
                                val size = dp(48)
                                add(
                                    browseButton,
                                    lParams(size, size) {
                                        centerVertically()
                                        startToEndOf(dirNameText, dp(2))
                                        endOfParent(dp(20))
                                    },
                                )
                            }
                            AlertDialog.Builder(ctx)
                                .setTitle(R.string.user_data_dir)
                                .setView(dialogContent)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    val value = dirNameText.text.toString()
                                    prefs.userDataDir.setValue(value)
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .setNeutralButton(R.string.default_) { _, _ ->
                                    prefs.userDataDir.setValue(DataManager.defaultDataDir.absolutePath)
                                }
                                .setOnDismissListener {
                                    // avoid memory leak
                                    launcherResultCallback = null
                                }
                                .show()
                            true
                        }
                    },
                )
            }
            addCategory(R.string.synchronization) {
                isIconSpaceReserved = false
                addPreference(R.string.sync_user_data_immediately) {
                    viewModel.rime.launchOnReady { it.syncUserData() }
                }
                addPreference(
                    SwitchPreferenceCompat(ctx).apply {
                        key = AppPrefs.Profile.PERIODIC_BACKGROUND_SYNC
                        isIconSpaceReserved = false
                        setTitle(R.string.periodic_background_sync)
                        setDefaultValue(false)
                        summaryProvider = Preference.SummaryProvider<SwitchPreferenceCompat> {
                            if (backgroundSyncEnable.getValue()) {
                                val lastTime: String
                                val lastStatus: String
                                if (lastSyncTime != 0L) {
                                    lastTime = customFormatTimeInDefault("yyyy-MM-dd HH:mm", lastSyncTime)
                                    lastStatus = getString(if (lastSyncStatus) R.string.success else R.string.failure)
                                } else {
                                    lastTime = "N/A"
                                    lastStatus = "N/A"
                                }
                                getString(
                                    R.string.periodic_background_sync_status,
                                    lastTime,
                                    lastStatus,
                                )
                            } else {
                                ""
                            }
                        }
                    },
                )
                addPreference(
                    EditTextIntPreference(ctx).apply {
                        editSyncIntervalPreference = this
                        key = AppPrefs.Profile.PERIODIC_BACKGROUND_SYNC_INTERVAL
                        isIconSpaceReserved = false
                        setTitle(R.string.periodic_background_sync_interval)
                        min = 15
                        setDefaultValue(30)
                        summaryProvider = EditTextIntPreference.SimpleSummaryProvider
                        isEnabled = backgroundSyncEnable.getValue()
                    },
                )
            }
            addCategory(R.string.maintenance) {
                isIconSpaceReserved = false
                addPreference(R.string.reset, R.string.reset_hint) {
                    val items = ctx.assets.list("shared") ?: return@addPreference
                    val checked = BooleanArray(items.size) { false }
                    AlertDialog
                        .Builder(context)
                        .setTitle(R.string.reset)
                        .setMultiChoiceItems(items, checked) { _, id, isChecked ->
                            checked[id] = isChecked
                        }.setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            var res = true
                            lifecycleScope.withLoadingDialog(context) {
                                withContext(Dispatchers.IO) {
                                    res =
                                        items
                                            .filterIndexed { index, _ -> checked[index] }
                                            .fold(true) { acc, asset ->
                                                val destPath =
                                                    DataManager.sharedDataDir.resolve(asset).absolutePath
                                                ResourceUtils
                                                    .copyFile("shared/$asset", destPath)
                                                    .fold({ acc and true }, { acc and false })
                                            }
                                }
                                ctx.toast((if (res) R.string.reset_success else R.string.reset_failure))
                            }
                        }.show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        prefs.periodicBackgroundSync.unregisterOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncInterval.unregisterOnChangeListener(onSyncIntervalChange)
    }
}
