package com.osfans.trime.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.blankj.utilcode.util.ToastUtils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.sound.SoundThemeManager
import com.osfans.trime.databinding.ActivityPrefBinding
import com.osfans.trime.ime.core.RimeWrapper
import com.osfans.trime.ime.core.Status
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.util.applyTranslucentSystemBars
import com.osfans.trime.util.progressBarDialogIndeterminate
import com.osfans.trime.util.rimeActionWithResultDialog
import kotlinx.coroutines.launch

class PrefMainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    private lateinit var navHostFragment: NavHostFragment
    private var loadingDialog: AlertDialog? = null

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
        val uiMode =
            when (prefs.other.uiMode) {
                "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
            }
        AppCompatDelegate.setDefaultNightMode(uiMode)

        super.onCreate(savedInstanceState)
        applyTranslucentSystemBars()
        val binding = ActivityPrefBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.prefToolbar.appBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = navBars.left
                rightMargin = navBars.right
            }
            binding.prefToolbar.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBars.top
            }
            binding.navHostFragment.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = navBars.left
                rightMargin = navBars.right
            }
            windowInsets
        }

        setContentView(binding.root)
        setSupportActionBar(binding.prefToolbar.toolbar)
        val appBarConfiguration =
            AppBarConfiguration(
                topLevelDestinationIds = setOf(),
                fallbackOnNavigateUpListener = ::onNavigateUpListener,
            )
        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.prefToolbar.toolbar.setupWithNavController(navHostFragment.navController, appBarConfiguration)
        viewModel.toolbarTitle.observe(this) {
            binding.prefToolbar.toolbar.title = it
        }
        viewModel.topOptionsMenu.observe(this) {
            binding.prefToolbar.toolbar.menu.forEach { m ->
                m.isVisible = it
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (SetupActivity.shouldSetup()) {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RimeWrapper.statusStateFlow.collect { state ->
                    when (state) {
                        Status.IN_PROGRESS -> {
                            loadingDialog?.dismiss()
                            loadingDialog =
                                progressBarDialogIndeterminate(R.string.deploy_progress).create().apply {
                                    show()
                                }
                        }
                        Status.UN_INIT -> {
                            RimeWrapper.startup()
                        }
                        else -> loadingDialog?.dismiss()
                    }
                }
            }
        }
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
                deploy()
                true
            }
            R.id.preference__menu_about -> {
                navHostFragment.navController.navigate(R.id.action_prefFragment_to_aboutFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deploy() {
        lifecycleScope.launch {
            rimeActionWithResultDialog("rime.trime", "W", 1) {
                RimeWrapper.deploy()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestExternalStoragePermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun requestExternalStoragePermission() {
        XXPermissions.with(this)
            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
            .request(
                object : OnPermissionCallback {
                    override fun onGranted(
                        permissions: List<String>,
                        all: Boolean,
                    ) {
                        if (all) {
                            ToastUtils.showShort(R.string.external_storage_permission_granted)
                            RimeWrapper.canStart = true
                            SoundThemeManager.init()
                        }
                    }

                    override fun onDenied(
                        permissions: List<String>,
                        never: Boolean,
                    ) {
                        RimeWrapper.canStart = false
                        if (never) {
                            ToastUtils.showShort(R.string.external_storage_permission_denied)
                            XXPermissions.startPermissionActivity(
                                this@PrefMainActivity,
                                permissions,
                            )
                        } else {
                            ToastUtils.showShort(R.string.external_storage_permission_denied)
                        }
                    }
                },
            )
    }
}
