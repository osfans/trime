package com.osfans.trime.settings

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.R
import com.osfans.trime.Rime
import com.osfans.trime.databinding.AboutActivityBinding

class AboutActivity: AppCompatActivity() {
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

    class AboutFragment: PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.about_preference, rootKey)
            val changelogPreference: Preference? = findPreference("pref_changelog")
            if (changelogPreference != null) {
                setVersion(changelogPreference, Rime.get_trime_version())
            }
            val librimeVerPreference: Preference? = findPreference("pref_librime_ver")
            if (librimeVerPreference != null) {
                setVersion(librimeVerPreference, Rime.get_librime_version())
            }
            val openccVerPreference: Preference? = findPreference("pref_opencc_ver")
            if (openccVerPreference != null) {
                setVersion(openccVerPreference, Rime.get_opencc_version())
            }
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            return when(preference?.key) {
                "pref_licensing" -> {
                    val webView = WebView(requireContext())
                    webView.loadUrl("file:///android_asset/licensing.html")
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pref_licensing)
                        .setView(webView)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }

        private fun getCommit(versionCode: String): String {
            return if (versionCode.contains("-g")) {
                versionCode.replace("^(.*-g)([0-9a-f]+)(.*)$".toRegex(), "$2")
            } else {
                versionCode.replace("^([^-]*)(-.*)$".toRegex(), "$1")
            }
        }

        private fun setVersion(pref: Preference, versionCode: String) {
            val commit = getCommit(versionCode)
            pref.summary = versionCode
            val intent = pref.intent
            intent.data = Uri.withAppendedPath(intent.data, "commits/$commit")
            pref.intent = intent
        }
    }
}