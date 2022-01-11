package com.osfans.trime.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.databinding.PrefActivityBinding
import com.osfans.trime.ime.core.Preferences
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.settings.components.SchemaPickerDialog
import com.osfans.trime.setup.SetupActivity
import com.osfans.trime.util.AndroidVersion
import com.osfans.trime.util.RimeUtils
import com.osfans.trime.util.createLoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

internal const val FRAGMENT_TAG = "FRAGMENT_TAG"
const val PERMISSION_REQUEST_EXTERNAL_STORAGE = 0

class PrefMainActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    ActivityCompat.OnRequestPermissionsResultCallback,
    CoroutineScope by MainScope() {
    private val prefs get() = Preferences.defaultInstance()

    lateinit var binding: PrefActivityBinding
    lateinit var imeManager: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs.sync()
        val uiMode = when (prefs.other.uiMode) {
            "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
        }
        AppCompatDelegate.setDefaultNightMode(uiMode)

        super.onCreate(savedInstanceState)
        binding = PrefActivityBinding.inflate(layoutInflater)
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            BarUtils.setNavBarColor(
                this,
                getColor(R.color.windowBackground)
            )
        } else
            BarUtils.setNavBarColor(
                this,
                @Suppress("DEPRECATION")
                resources.getColor(R.color.windowBackground)
            )
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
                setTitle(R.string.trime_app_name)
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (SetupActivity.shouldSetup()) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        requestExternalStoragePermission()
        requestAlertWindowPermission()
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
            @Suppress("DEPRECATION")
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_EXTERNAL_STORAGE) {
            // Request for external storage permission
            if (grantResults.size == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launch {
                    ToastUtils.showShort(R.string.external_storage_permission_granted)
                    delay(500)
                    RimeUtils.deploy(this@PrefMainActivity)
                }
            } else {
                // Permission request was denied
                ToastUtils.showShort(R.string.external_storage_permission_denied)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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
                val progressDialog = createLoadingDialog(this, R.string.deploy_progress)
                progressDialog.show()
                Trime.getServiceOrNull()?.initKeyboard()
                launch {
                    try {
                        RimeUtils.deploy(this@PrefMainActivity)
                    } catch (ex: Exception) {
                        Timber.e(ex, "Deploy Exception")
                    } finally {
                        progressDialog.dismiss()
                    }
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

    private fun requestExternalStoragePermission() {
        if (AndroidVersion.ATLEAST_M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is already available, return
                return
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.external_storage_access_required)
                        .setCancelable(true)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                ),
                                PERMISSION_REQUEST_EXTERNAL_STORAGE
                            )
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    ToastUtils.showShort(R.string.external_storage_permission_not_available)

                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        PERMISSION_REQUEST_EXTERNAL_STORAGE
                    )
                }
            }
        }
    }

    private fun requestAlertWindowPermission() {
        if (AndroidVersion.ATLEAST_P) { // 僅Android P需要此權限在最上層顯示懸浮窗、對話框
            if (!Settings.canDrawOverlays(this)) { // 事先说明需要权限的理由
                AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setMessage(R.string.alert_window_access_required)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    class PrefFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
        }

        override fun onPreferenceTreeClick(preference: Preference?): Boolean {
            return when (preference?.key) {
                "pref_schemas" -> {
                    SchemaPickerDialog(requireContext()).show()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }
        }
    }
}
