/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatViewInflater
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.soundeffect.SoundEffectManager
import com.osfans.trime.databinding.ActivityMainBinding
import com.osfans.trime.ui.common.PublicWindowPilotActivity
import com.osfans.trime.ui.common.dialog.LegacyPublicPreferenceDialogFactory
import com.osfans.trime.ui.common.dialog.PublicAlertDialogs
import com.osfans.trime.ui.setup.SetupActivity
import com.osfans.trime.util.item
import com.osfans.trime.util.parcelable
import com.osfans.trime.util.startActivity
import com.osfans.trime.worker.BackgroundSyncWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import splitties.views.topPadding

open class MainActivity : PublicWindowPilotActivity() {
    protected open val shouldCheckSetup: Boolean get() = true

    private val viewModel: MainViewModel by viewModels()

    private val compatViewInflater by lazy(LazyThreadSafetyMode.NONE) { AppCompatViewInflater() }

    private lateinit var navController: NavController
    private var testInputPanel: TestInputPanel? = null
    private var notificationDialog: AlertDialog? = null
    private var notificationPromptCancelled = false
    private var deferredToolbarMenu: Menu? = null
    private var firstContentPreDraw = false
    private var firstWindowFocusReceived = false
    private var toolbarMenuPosted = false
    private var startupShellPreDraw = false
    private var contentRequested = false
    private var contentInflationPosted = false
    private var contentCreated = false
    private var startupShell: FrameLayout? = null
    private var contentInflationRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The shared base attaches the public UI-policy context; keep the Activity theme's framework flags explicit.
        setTheme(R.style.Theme_TrimePublicWindowPilot)
        val previousFactory = supportFragmentManager.fragmentFactory
        if (previousFactory !is LegacyPublicPreferenceDialogFactory) {
            supportFragmentManager.fragmentFactory = LegacyPublicPreferenceDialogFactory(previousFactory)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val restoreCreatedContent = savedInstanceState?.getBoolean(STATE_CONTENT_CREATED) == true
        if (restoreCreatedContent) {
            // Android restores the ordinary view hierarchy after onStart. Rebuild
            // an already-created content tree synchronously so its state has real
            // target views. A first launch still uses the lightweight shell below.
            contentRequested = true
            contentCreated = true
            createContent()
        } else {
            installStartupShell()
        }
        lifecycleScope.launch {
            if (shouldCheckSetup && SetupActivity.shouldShowUp()) {
                startActivity<SetupActivity>()
                finish()
            } else if (!contentCreated) {
                contentRequested = true
                postContentInflationIfReady()
            }
        }
    }

    private fun installStartupShell() {
        val shell = FrameLayout(this).also { startupShell = it }
        setContentView(shell)
        shell.doOnPreDraw {
            startupShellPreDraw = true
            postContentInflationIfReady()
        }
    }

    private fun postContentInflationIfReady() {
        val shell = startupShell ?: return
        if (!contentRequested || !startupShellPreDraw || !firstWindowFocusReceived ||
            !hasWindowFocus() || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            supportFragmentManager.isStateSaved || contentInflationPosted || contentCreated
        ) {
            return
        }
        contentInflationPosted = true
        val runnable = Runnable {
            contentInflationRunnable = null
            contentInflationPosted = false
            if (isFinishing || isDestroyed || startupShell !== shell || contentCreated ||
                !hasWindowFocus() || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                supportFragmentManager.isStateSaved
            ) {
                return@Runnable
            }
            // Let the lightweight window draw and receive focus before inflating
            // navigation, fragments, and the test-input panel. On a cold
            // software-rendered device, doing this work during the Setup -> Main
            // handoff can hold the outgoing window past Android's input timeout.
            contentCreated = true
            startupShell = null
            createContent()
        }
        contentInflationRunnable = runnable
        window.decorView.post(runnable)
    }

    private fun createContent() {
        val binding = ActivityMainBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBars.left
                rightMargin = systemBars.right
                bottomMargin = maxOf(systemBars.bottom, ime.bottom)
            }
            binding.mainToolbar.root.topPadding = systemBars.top
            windowInsets
        }
        ViewCompat.setWindowInsetsAnimationCallback(
            binding.root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat?>,
                ): WindowInsetsCompat {
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = maxOf(systemBars.bottom, ime.bottom)
                    }
                    return insets
                }
            },
        )
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
        // Building action-item views can be expensive on a cold, software-rendered
        // device. Let the first frame and the Setup -> Main focus handoff finish
        // before adding them so the outgoing Setup window can acknowledge focus
        // loss within Android's input-dispatch timeout.
        deferredToolbarMenu = binding.mainToolbar.toolbar.menu
        binding.root.doOnPreDraw {
            firstContentPreDraw = true
            // A recreated Activity can inherit an already-focused window without
            // receiving a new onWindowFocusChanged(true) callback. Its first
            // pre-draw still proves that this window owns focus.
            if (hasWindowFocus()) firstWindowFocusReceived = true
            postToolbarMenuIfReady()
        }
        navController = binding.navHostFragment.getFragment<NavHostFragment>().navController
        navController.graph = NavigationRoute.createGraph(navController)
        binding.mainToolbar.toolbar.setNavigationOnClickListener {
            // prevent navigate up when child fragment has enabled `OnBackPressedCallback`
            if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                onBackPressedDispatcher.onBackPressed()
                return@setNavigationOnClickListener
            }
            // "minimize" the activity if we can't go back
            navController.navigateUp() || navigateToActivityParent() || moveTaskToBack(false)
        }
        val panelBackCallback = onBackPressedDispatcher.addCallback(this, enabled = binding.testInputPanel.isVisible) {
            binding.testInputPanel.dismiss()
        }
        binding.testInputPanel.onVisibilityChange = { panelBackCallback.isEnabled = it }
        testInputPanel = binding.testInputPanel
        viewModel.toolbarTitle.observe(this) {
            binding.mainToolbar.toolbar.title = it
        }
        navController.addOnDestinationChangedListener { _, dest, _ ->
            dest.label?.let { viewModel.setToolbarTitle(it.toString()) }
            binding.mainToolbar.toolbar.subtitle =
                if (dest.route == NavigationRoute.Main.path) {
                    getString(R.string.trime_app_slogan)
                } else {
                    ""
                }
        }

        binding.mainToolbar.toolbar.setNavigationContentDescription(R.string.public_window_navigate_up)
        processIntent(intent)
    }

    // API21+ already applies android:theme. These are the exact AppCompat1.7.1 widget-only
    // inflater switches for our minSdk; no AppCompatDelegate or Window factory is installed.
    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? = compatViewInflater.createView(parent, name, context, attrs, false, false, true, false)
        ?: super.onCreateView(parent, name, context, attrs)

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? = compatViewInflater.createView(null, name, context, attrs, false, false, true, false)
        ?: super.onCreateView(name, context, attrs)

    private fun navigateToActivityParent(): Boolean {
        if (NavUtils.getParentActivityIntent(this) == null) return false
        navigateToManifestParent()
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!firstWindowFocusReceived) firstWindowFocusReceived = true
            postContentInflationIfReady()
            postToolbarMenuIfReady()
            if (toolbarMenuPosted && deferredToolbarMenu == null) {
                checkNotificationPermission()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CONTENT_CREATED, contentCreated)
        super.onSaveInstanceState(outState)
    }

    private fun postToolbarMenuIfReady() {
        val menu = deferredToolbarMenu ?: return
        if (!firstContentPreDraw || !firstWindowFocusReceived || !hasWindowFocus() || toolbarMenuPosted) return
        toolbarMenuPosted = true
        window.decorView.post {
            if (isFinishing || isDestroyed || deferredToolbarMenu !== menu) return@post
            if (!hasWindowFocus()) {
                toolbarMenuPosted = false
                return@post
            }
            // Show menu icons and actions after the first pre-draw and focus
            // handoff. Don't use setSupportActionBar(): NavController would then
            // own the custom title.
            deferredToolbarMenu = null
            setupToolbarMenu(menu)
            // A permission dialog shown before this window receives focus can
            // hold up the Setup -> Main focus handoff. Show it only after the
            // deferred menu is installed and the focus callback has returned.
            checkNotificationPermission()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_MAIN -> lifecycleScope.launch {
                if (shouldCheckSetup && SetupActivity.shouldShowUp()) startActivity<SetupActivity>()
            }

            Intent.ACTION_RUN -> {
                val route = intent.parcelable<NavigationRoute>(EXTRA_SETTINGS_ROUTE) ?: return
                navController.popBackStack(NavigationRoute.Main.path, false)
                navController.navigate(route.path)
                // This command describes one navigation event, not persistent
                // Activity state. Consuming it prevents recreation from
                // overriding NavController's restored destination.
                intent.action = null
                intent.removeExtra(EXTRA_SETTINGS_ROUTE)
            }
        }
    }

    private fun setupToolbarMenu(menu: Menu) {
        val optionMenuItems = listOf(
            menu.item(R.string.deploy, R.drawable.ic_baseline_refresh_reversed_24, showAsAction = true, id = R.id.action_deploy) {
                lifecycleScope.launch {
                    val session = viewModel.rime
                    RimeDaemon.retryFailedStartup()
                    session.runOnReady { deploy() }
                }
            },
            menu.item(R.string.test_input, R.drawable.ic_baseline_keyboard_24, showAsAction = true, id = R.id.action_test_input) {
                testInputPanel?.show(window)
            },
            menu.item(R.string.developer, id = R.id.action_developer) {
                navController.navigate(NavigationRoute.Developer.path)
            },
            menu.item(R.string.about, id = R.id.action_about) {
                navController.navigate(NavigationRoute.About.path)
            },
        )
        val editItem = menu.item(R.string.edit, R.drawable.ic_baseline_edit_24, showAsAction = true, id = R.id.action_edit) {
            viewModel.toolbarEditButtonOnClickListener.value?.invoke()
        }
        val deleteItem = menu.item(R.string.delete, R.drawable.ic_baseline_delete_24, showAsAction = true, id = R.id.action_delete) {
            viewModel.toolbarDeleteButtonOnClickListener.value?.invoke()
        }
        menu.forEach { item ->
            // show menu item on demand
            item.isVisible = false
        }
        // Establish defaults before observing. LiveData immediately replays the
        // current destination state, which must be the final visibility value.
        optionMenuItems.forEach { item ->
            viewModel.topOptionsMenu.observe(this) { enabled ->
                item.isVisible = enabled
            }
        }
        viewModel.toolbarEditButtonVisible.observe(this) {
            editItem.isVisible = it
        }
        viewModel.toolbarDeleteButtonOnClickListener.observe(this) {
            deleteItem.isVisible = it != null
        }
    }

    override fun onPause() {
        super.onPause()
        if (::navController.isInitialized && viewModel.restartBackgroundSyncWork.value == true) {
            viewModel.restartBackgroundSyncWork.value = false
            TrimeApplication.getInstance().coroutineScope.launch(Dispatchers.IO) {
                BackgroundSyncWork.forceStart(applicationContext)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        postContentInflationIfReady()
        postToolbarMenuIfReady()
        if (toolbarMenuPosted && deferredToolbarMenu == null) {
            checkNotificationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        postContentInflationIfReady()
        postToolbarMenuIfReady()
        if (toolbarMenuPosted && deferredToolbarMenu == null) {
            checkNotificationPermission()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            if (UserDataManager.isStorageAvailable()) SoundEffectManager.init()
        }
    }

    override fun onStop() {
        super.onStop()
        testInputPanel?.dismiss()
        dismissNotificationDialog()
    }

    override fun onDestroy() {
        contentInflationRunnable?.let(window.decorView::removeCallbacks)
        contentInflationRunnable = null
        startupShell = null
        dismissNotificationDialog()
        deferredToolbarMenu = null
        testInputPanel = null
        super.onDestroy()
    }

    private fun dismissNotificationDialog() {
        val dialog = notificationDialog
        notificationDialog = null
        dialog?.dismiss()
    }

    private fun checkNotificationPermission() {
        if (isFinishing || isDestroyed || !hasWindowFocus() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            notificationPromptCancelled || notificationDialog?.isShowing == true ||
            XXPermissions.isGranted(this, Permission.POST_NOTIFICATIONS)
        ) {
            return
        }
        val dialog =
            PublicAlertDialogs
                .builder(this)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.grant_permission) { source, _ ->
                    if (notificationDialog === source && !isFinishing && !isDestroyed &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    ) {
                        XXPermissions
                            .with(this)
                            .permission(Permission.POST_NOTIFICATIONS)
                            .request(null)
                    }
                }.setNegativeButton(android.R.string.cancel) { _, _ ->
                    notificationPromptCancelled = true
                }.setOnCancelListener {
                    notificationPromptCancelled = true
                }
                .create()
        notificationDialog = dialog
        dialog.setOnDismissListener {
            if (notificationDialog === dialog) notificationDialog = null
        }
        dialog.show()
    }

    companion object {
        const val EXTRA_SETTINGS_ROUTE = "${BuildConfig.APPLICATION_ID}.EXTRA_SETTINGS_ROUTE"
        private const val STATE_CONTENT_CREATED = "main_content_created"
    }
}
