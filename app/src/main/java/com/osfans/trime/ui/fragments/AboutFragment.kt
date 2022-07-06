package com.osfans.trime.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.get
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.AppVersionUtils.writeLibraryVersionToSummary

class AboutFragment : PreferenceFragmentCompat() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about_preference, rootKey)
        with(preferenceScreen) {
            get<Preference>("about__changelog")
                ?.writeLibraryVersionToSummary(BuildConfig.BUILD_VERSION)
            get<Preference>("about__buildinfo")?.apply {
                writeLibraryVersionToSummary(BuildConfig.BUILD_INFO)
                setOnPreferenceClickListener {
                    val cbm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val info = ClipData.newPlainText("BuildInfo", BuildConfig.BUILD_INFO)
                    cbm.setPrimaryClip(info)
                    ToastUtils.showLong(R.string.copy_done)
                    true
                }
            }
            get<Preference>("about__librime_version")
                ?.writeLibraryVersionToSummary(Rime.get_librime_version())
            get<Preference>("about__opencc_version")
                ?.writeLibraryVersionToSummary(Rime.get_opencc_version())
            get<Preference>("pref_trime_custom_qq")
                ?.hidden()
            get<Preference>("about__licensing")?.setOnPreferenceClickListener {
                val webView = WebView(requireContext())
                webView.loadUrl("file:///android_asset/license/open_source_license.html")
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.about__licensing_title)
                    .setView(webView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
            get<Preference>("about__used_libraries")?.setOnPreferenceClickListener {
                val webView = WebView(requireContext())
                webView.loadUrl("file:///android_asset/license/library_licenses.html")
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.about__used_library_dialog_title)
                    .setView(webView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_about))
    }

    private fun Preference.hidden() {
        if (this.summary.isBlank() || this.intent.data.toString().isBlank()) {
            this.isVisible = false
        }
    }
}
