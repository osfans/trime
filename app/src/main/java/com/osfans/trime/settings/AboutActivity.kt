package com.osfans.trime.settings

import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.databinding.AboutActivityBinding
import com.osfans.trime.util.AppVersionUtils.writeLibraryVersionToSummary

class AboutActivity : AppCompatActivity() {
    lateinit var binding: AboutActivityBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AboutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (savedInstanceState == null) {
            loadFragment(AboutFragment())
        } else {
            title = savedInstanceState.getCharSequence(FRAGMENT_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.settings__about_title)
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(FRAGMENT_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(binding.about.id, fragment, FRAGMENT_TAG)
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class AboutFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.about_preference, rootKey)
            findPreference<Preference>("about__changelog")
                ?.writeLibraryVersionToSummary(BuildConfig.BUILD_VERSION)

            findPreference<Preference>("about__buildinfo")
                ?.writeLibraryVersionToSummary(BuildConfig.BUILD_INFO)

            findPreference<Preference>("about__librime_version")
                ?.writeLibraryVersionToSummary(Rime.get_librime_version())

            findPreference<Preference>("about__opencc_version")
                ?.writeLibraryVersionToSummary(Rime.get_opencc_version())
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "about__licensing" -> {
                    val webView = WebView(requireContext())
                    webView.loadUrl("file:///android_asset/license/open_source_license.html")
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.about__licensing_title)
                        .setView(webView)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    true
                }
                "about__used_libraries" -> {
                    val webView = WebView(requireContext())
                    webView.loadUrl("file:///android_asset/license/library_licenses.html")
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.about__used_library_dialog_title)
                        .setView(webView)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }
    }
}
