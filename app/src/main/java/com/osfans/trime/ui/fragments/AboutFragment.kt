package com.osfans.trime.ui.fragments

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.get
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.opencc.OpenCCDictManager
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.Const
import splitties.systemservices.clipboardManager

class AboutFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about_preference, rootKey)
        with(preferenceScreen) {
            get<Preference>("about__changelog")?.apply {
                summary = Const.displayVersionName
                isCopyingEnabled = true
                intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("${Const.currentGitRepo}/commits/${Const.buildGitHash}"),
                )
            }
            get<Preference>("about__buildinfo")?.apply {
                summary = BuildConfig.BUILD_INFO
                setOnPreferenceClickListener {
                    val info = ClipData.newPlainText("BuildInfo", summary)
                    clipboardManager.setPrimaryClip(info)
                    ToastUtils.showLong(R.string.copy_done)
                    true
                }
            }
            get<Preference>("about__librime_version")?.apply {
                val version = Rime.getLibrimeVersion()
                summary = version
                intent = intent?.let {
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.withAppendedPath(it.data, "commits/$version"),
                    )
                }
            }
            get<Preference>("about__opencc_version").apply {
                val version = OpenCCDictManager.getOpenCCVersion()
                summary = version
                intent = intent?.let {
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.withAppendedPath(it.data, "commits/$version"),
                    )
                }
            }
            get<Preference>("pref_trime_custom_qq")
                ?.hidden()
            get<Preference>("about__open_source_licenses")?.apply {
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_aboutFragment_to_licenseFragment)
                    true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_about))
    }

    private fun Preference.hidden() {
        if (this.summary?.isBlank() == true || this.intent?.data == null) {
            this.isVisible = false
        }
    }
}
