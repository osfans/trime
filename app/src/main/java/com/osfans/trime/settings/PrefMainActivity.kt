package com.osfans.trime.settings

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.osfans.trime.Function
import com.osfans.trime.R
import com.osfans.trime.databinding.PrefActivityBinding
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

internal const val FRAGMENT_TAG = "FRAGMENT_TAG"

class PrefMainActivity: AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(this)

    lateinit var binding: PrefActivityBinding
    lateinit var imeManager: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (prefs.getString("pref__settings_theme", "auto")) {
            "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        super.onCreate(savedInstanceState)
        binding = PrefActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        imeManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        if (savedInstanceState == null) {
            loadFragment(PrefFragment())
        } else {
            title = savedInstanceState.getCharSequence(FRAGMENT_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.ime_name)
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

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.preference, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(binding.preference.id, fragment, FRAGMENT_TAG)
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.preference_main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.preference__menu_deploy -> {
                val progressDialog = ProgressDialog(this).apply {
                    setMessage(getString(R.string.deploy_progress))
                    show()
                }
                launch {
                    Runnable {
                        try {
                            Function.deploy(this@PrefMainActivity)
                        } catch (ex: Exception) {
                            Log.e(FRAGMENT_TAG, "Deploy Exception: $ex")
                        } finally {
                            progressDialog.dismiss()
                            exitProcess(0)
                        }
                    }.run()
                }
                true
            }
            R.id.preference__menu_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            R.id.preference__menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    class PrefFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
            if (checkIfImeIsEnabled(requireContext())) {
                findPreference<Preference>("pref_enable")?.isVisible = false
            }
            if (checkIfImeIsSelected(requireContext())) {
                findPreference<Preference>("pref_select")?.isVisible = false
            }
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "pref_enable" -> { //啓用
                    val intent = Intent()
                    intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    startActivity(intent)
                    true
                }
                "pref_select" -> { //切換
                    (activity as PrefMainActivity).imeManager.showInputMethodPicker()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }

        override fun onResume() { // 如果同文已被启用/选用，则隐藏设置项
            super.onResume()
            if (checkIfImeIsEnabled(requireContext())) {
                findPreference<Preference>("pref_enable")?.isVisible = false
            }
            if (checkIfImeIsSelected(requireContext())) {
                findPreference<Preference>("pref_select")?.isVisible = false
            }
        }
    }

    companion object {
        private const val IME_ID: String = "com.osfans.trime/.Trime"

        fun checkIfImeIsEnabled(context: Context): Boolean {
            val activeImeIds = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: "(none)"
            return activeImeIds.split(":").contains(IME_ID)
        }

        fun checkIfImeIsSelected(context: Context): Boolean {
            val selectedImeIds = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: "(none)"
            return selectedImeIds == IME_ID
        }
    }
}