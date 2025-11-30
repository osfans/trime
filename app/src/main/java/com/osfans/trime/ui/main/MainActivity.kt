/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.fragment.NavHostFragment
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.databinding.ActivityMainBinding
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.util.parcelable
import com.osfans.trime.util.startActivity
import com.osfans.trime.worker.BackgroundSyncWork
import splitties.views.topPadding

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val uiMode by AppPrefs.defaultInstance().other.uiMode

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        val uiMode =
            when (uiMode) {
                AppPrefs.Other.UiMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppPrefs.Other.UiMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppPrefs.Other.UiMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        AppCompatDelegate.setDefaultNightMode(uiMode)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBars.left
                rightMargin = systemBars.right
            }
            binding.mainToolbar.root.topPadding = systemBars.top
            windowInsets
        }
        WindowCompat
            .getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setContentView(binding.root)
        // always show toolbar back arrow icon
        binding.mainToolbar.toolbar.navigationIcon =
            DrawerArrowDrawable(this).apply {
                progress = 1f
                color = ContextCompat.getColor(this@MainActivity, R.color.toolbarForegroundColor)
            }
        // show menu icon and other action icons on toolbar
        // don't use `setSupportActionBar(binding.toolbar)` here,
        // because navController would change toolbar title, we need to control it by ourselves
        setupToolbarMenu(binding.mainToolbar.toolbar.menu)
        navController = binding.navHostFragment.getFragment<NavHostFragment>().navController
        navController.graph = NavigationRoute.createGraph(navController)
        binding.mainToolbar.toolbar.setNavigationOnClickListener {
            // prevent navigate up when child fragment has enabled `OnBackPressedCallback`
            if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                onBackPressedDispatcher.onBackPressed()
                return@setNavigationOnClickListener
            }
            // "minimize" the activity if we can't go back
            navController.navigateUp() || onSupportNavigateUp() || moveTaskToBack(false)
        }
        viewModel.toolbarTitle.observe(this) {
            binding.mainToolbar.toolbar.title = it
        }
        navController.addOnDestinationChangedListener { _, dest, _ ->
            dest.label?.let { viewModel.setToolbarTitle(it.toString()) }
            binding.mainToolbar.toolbar.subtitle =
                if (dest.hasRoute<NavigationRoute.Main>()) {
                    getString(R.string.trime_app_slogan)
                } else {
                    ""
                }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        processIntent(intent)
        checkNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_MAIN -> if (SetupActivity.shouldSetup()) {
                startActivity<SetupActivity>()
            }
            Intent.ACTION_RUN -> {
                val route = intent.parcelable<NavigationRoute>(EXTRA_SETTINGS_ROUTE) ?: return
                navController.popBackStack(NavigationRoute.Main, false)
                navController.navigate(route)
            }
        }
    }

    private fun setupToolbarMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.forEach { item ->
            // show menu item on demand
            item.isVisible = false
            when (item.itemId) {
                R.id.deploy, R.id.developer, R.id.about -> {
                    viewModel.topOptionsMenu.observe(this) {
                        item.isVisible = it
                    }
                    when (item.itemId) {
                        R.id.deploy -> {
                            item.setOnMenuItemClickListener {
                                viewModel.rime.launchOnReady {
                                    it.deploy()
                                }
                                true
                            }
                        }
                        R.id.developer -> {
                            item.setOnMenuItemClickListener {
                                navController.navigate(NavigationRoute.Developer)
                                true
                            }
                        }
                        R.id.about -> {
                            item.setOnMenuItemClickListener {
                                navController.navigate(NavigationRoute.About)
                                true
                            }
                        }
                    }
                }
                R.id.edit -> {
                    viewModel.toolbarEditButtonVisible.observe(this) {
                        item.isVisible = it
                    }
                    item.setOnMenuItemClickListener {
                        viewModel.toolbarEditButtonOnClickListener.value?.invoke()
                        true
                    }
                }
                R.id.delete -> {
                    viewModel.toolbarDeleteButtonOnClickListener.observe(this) {
                        item.isVisible = it != null
                    }
                    item.setOnMenuItemClickListener {
                        viewModel.toolbarDeleteButtonOnClickListener.value?.invoke()
                        true
                    }
                }
                else -> {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.restartBackgroundSyncWork.value == true) {
            viewModel.restartBackgroundSyncWork.value = false
            BackgroundSyncWork.forceStart(this)
        }
    }

    private fun checkNotificationPermission() {
        if (XXPermissions.isGranted(this, Permission.POST_NOTIFICATIONS)) {
            return
        } else {
            AlertDialog
                .Builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.grant_permission) { _, _ ->
                    XXPermissions
                        .with(this)
                        .permission(Permission.POST_NOTIFICATIONS)
                        .request(null)
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        const val EXTRA_SETTINGS_ROUTE = "${BuildConfig.APPLICATION_ID}.EXTRA_SETTINGS_ROUTE"
    }
}
