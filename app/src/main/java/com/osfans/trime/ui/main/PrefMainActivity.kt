package com.osfans.trime.ui.main

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
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.databinding.ActivityPrefBinding
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.util.RimeUtils
import com.osfans.trime.util.withLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrefMainActivity :
    AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback {
    private val viewModel: MainViewModel by viewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    private lateinit var navHostFragment: NavHostFragment

    private fun onNavigateUpListener(): Boolean {
        val navController = navHostFragment.navController
        return when (navController.currentDestination?.id) {
            R.id.prefFragment -> {
                // "minimize" the app, don't exit activity
                moveTaskToBack(false)
                true
            }
            else -> onSupportNavigateUp()
        }
    }

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
        val binding = ActivityPrefBinding.inflate(layoutInflater)
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
        setSupportActionBar(binding.toolbar.toolbar)
        val appBarConfiguration = AppBarConfiguration(
            topLevelDestinationIds = setOf(),
            fallbackOnNavigateUpListener = ::onNavigateUpListener
        )
        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.toolbar.toolbar.setupWithNavController(navHostFragment.navController, appBarConfiguration)
        viewModel.toolbarTitle.observe(this) {
            binding.toolbar.toolbar.title = it
        }
        viewModel.topOptionsMenu.observe(this) {
            binding.toolbar.toolbar.menu.forEach { m ->
                m.isVisible = it
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (SetupActivity.shouldSetup()) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        requestExternalStoragePermission()
        requestAlertWindowPermission()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_EXTERNAL_STORAGE) {
            // Request for external storage permission
            if (grantResults.size == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lifecycleScope.launch {
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
        menu?.forEach {
            it.isVisible = viewModel.topOptionsMenu.value ?: true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.preference__menu_deploy -> {
                lifecycleScope.withLoadingDialog(
                    this, 200L, R.string.deploy_progress
                ) {
                    withContext(Dispatchers.IO) {
                        Rime.destroy()
                        Rime.get(this@PrefMainActivity, true)
                    }
                    ToastUtils.showLong(R.string.deploy_finish)
                }
                true
            }
            R.id.preference__menu_about -> {
                navHostFragment.navController.navigate(R.id.action_prefFragment_to_aboutFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestExternalStoragePermission() {
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
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
        if (VERSION.SDK_INT >= VERSION_CODES.P) { // 僅Android P需要此權限在最上層顯示懸浮窗、對話框
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

    companion object {
        private const val PERMISSION_REQUEST_EXTERNAL_STORAGE = 0
    }
}
