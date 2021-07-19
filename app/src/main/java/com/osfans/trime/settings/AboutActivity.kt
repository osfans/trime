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
import com.osfans.trime.util.AppVersionUtils.libVersion

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
                setTitle(R.string.pref_about)
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
            findPreference<Preference>("pref_changelog")?.libVersion = Rime.get_trime_version()
            findPreference<Preference>("pref_librime_ver")?.libVersion = Rime.get_librime_version()
            findPreference<Preference>("pref_opencc_ver")?.libVersion = Rime.get_opencc_version()
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
    }
}