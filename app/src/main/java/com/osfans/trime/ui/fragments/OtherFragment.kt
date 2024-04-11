package com.osfans.trime.ui.fragments

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.formatDateTime
import com.osfans.trime.util.iso8601UTCDateTime
import com.osfans.trime.util.queryFileName
import com.osfans.trime.util.withLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OtherFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    private var exportTimestamp = System.currentTimeMillis()

    private lateinit var exportLauncher: ActivityResultLauncher<String>

    private lateinit var importLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri == null) return@registerForActivityResult
                val ctx = requireContext()
                val cr = ctx.contentResolver
                lifecycleScope.withLoadingDialog(ctx) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val name = cr.queryFileName(uri) ?: return@withContext
                        if (!name.endsWith(".zip")) {
                            ctx.importErrorDialog(getString(R.string.exception_app_data_filename, name))
                            return@withContext
                        }
                        try {
                            val inputStream = cr.openInputStream(uri)!!
                            val metadata = DataManager.import(inputStream).getOrThrow()
                            withContext(Dispatchers.Main) {
                                ToastUtils.showShort(getString(R.string.app_data_imported, formatDateTime(metadata.exportTime)))
                            }
                        } catch (e: Exception) {
                            ctx.importErrorDialog(e.localizedMessage ?: e.stackTraceToString())
                        }
                    }
                }
            }
        exportLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri == null) return@registerForActivityResult
                val ctx = requireContext()
                lifecycleScope.withLoadingDialog(requireContext()) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        try {
                            val outputStream = ctx.contentResolver.openOutputStream(uri)!!
                            DataManager.export(outputStream, exportTimestamp).getOrThrow()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            ToastUtils.showShort(e.localizedMessage ?: e.stackTraceToString())
                        }
                    }
                }
            }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.other_preference)
        findPreference<ListPreference>("other__ui_mode")?.setOnPreferenceChangeListener { _, newValue ->
            val uiMode =
                when (newValue) {
                    "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
                }
            AppCompatDelegate.setDefaultNightMode(uiMode)
            true
        }
        val screen = preferenceScreen
        screen.addPreference(
            Preference(requireContext()).apply {
                isIconSpaceReserved = false
                isSingleLineTitle = false
                title = getString(R.string.export_app_data)
                setOnPreferenceClickListener { _ ->
                    lifecycleScope.launch {
                        exportTimestamp = System.currentTimeMillis()
                        exportLauncher.launch("trime_${iso8601UTCDateTime(exportTimestamp)}.zip")
                    }
                    true
                }
            },
        )
        screen.addPreference(
            Preference(requireContext()).apply {
                isIconSpaceReserved = false
                isSingleLineTitle = false
                title = getString(R.string.import_app_data)
                setOnPreferenceClickListener { _ ->
                    AlertDialog.Builder(requireContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.import_app_data)
                        .setMessage(R.string.confirm_import_app_data)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            importLauncher.launch("application/zip")
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_other))
        viewModel.disableTopOptionsMenu()
    }

    override fun onPause() {
        updateLauncherIconStatus()
        super.onPause()
    }

    private fun updateLauncherIconStatus() {
        // Set LauncherAlias enabled/disabled state just before destroying/pausing this activity
        if (prefs.other.showAppIcon) {
            showAppIcon(requireContext())
        } else {
            hideAppIcon(requireContext())
        }
    }

    private suspend fun Context.importErrorDialog(message: String) {
        withContext(Dispatchers.Main.immediate) {
            AlertDialog.Builder(this@importErrorDialog)
                .setTitle(R.string.import_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .show()
        }
    }

    companion object {
        private const val SETTINGS_ACTIVITY_NAME = "com.osfans.trime.PrefLauncherAlias"

        fun hideAppIcon(context: Context) {
            val pkg: PackageManager = context.packageManager
            pkg.setComponentEnabledSetting(
                ComponentName(context, SETTINGS_ACTIVITY_NAME),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        fun showAppIcon(context: Context) {
            val pkg: PackageManager = context.packageManager
            pkg.setComponentEnabledSetting(
                ComponentName(context, SETTINGS_ACTIVITY_NAME),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
