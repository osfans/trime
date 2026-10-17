/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.R
import com.osfans.trime.core.RimeMaintenanceMutex
import com.osfans.trime.core.data.ConfigurationImportException
import com.osfans.trime.core.data.DataStorageMode
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.ChineseVariant
import com.osfans.trime.data.prefs.InputLayout
import com.osfans.trime.data.prefs.InputSetupException
import com.osfans.trime.data.prefs.InputSetupFailure
import com.osfans.trime.data.prefs.InputSetupSelection
import com.osfans.trime.data.prefs.InputSetupState
import com.osfans.trime.databinding.ActivitySetupBinding
import com.osfans.trime.ui.common.dialog.PublicAlertDialogs
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.ui.setup.SetupPage.Companion.isLastPage
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.appContext
import com.osfans.trime.util.createNotificationChannel
import com.osfans.trime.util.startActivity
import com.osfans.trime.util.toast
import io.github.whiredplanck.storageaccess.StorageAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import splitties.systemservices.notificationManager
import timber.log.Timber
import java.util.UUID

class SetupActivity : FragmentActivity() {
    private lateinit var viewPager: ViewPager2

    private lateinit var skipButton: Button
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button

    private val storageAccess = StorageAccess(this)
    private var completedPages = emptySet<SetupPage>()
    private var refreshJob: Job? = null
    private var refreshPending = false
    private var storageRevision = 0
    private var selectInitialPage = false
    private var observingInputMethods = false
    private val inputMethodObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!observingInputMethods || isFinishing || isDestroyed) return
            storageRevision++
            refreshCurrentFragment()
        }
    }
    var changingStorageMode = false
        private set

    private val inputSessionName = "setup-input-${UUID.randomUUID()}"
    private var inputSession: RimeSession? = null
    private var inputJob: Job? = null
    private var inputLoaded = false
    private var draftEdited = false
    private var restoreTrialFocus = false
    private var trialFocusFrame: SetupTrialEditText? = null
    internal val isTrialFocusRestorationPending get() = restoreTrialFocus
    var keepCurrentInput = true
        private set
    var inputChoice = InputSetupSelection(InputLayout.FULL_PINYIN, ChineseVariant.SIMPLIFIED)
        private set
    var inputState: InputSetupState? = null
        private set
    var inputStatus: String? = null
        private set
    var changingInput = false
        private set
    val currentSetupPage get() = SetupPage.entries[viewPager.currentItem]

    fun isPageDone(page: SetupPage) = page in completedPages

    private fun rimeSession() = inputSession ?: RimeDaemon.createSession(inputSessionName).also {
        inputSession = it
    }

    fun loadInputState() {
        if (inputLoaded || changingInput || changingStorageMode || !isPageDone(SetupPage.Select)) return
        changingInput = true
        inputJob = lifecycleScope.launch {
            inputStatus = getString(R.string.setup__input_loading)
            renderStatus()
            try {
                val state = withTimeout(60_000) {
                    rimeSession().runOnReady { inspectInputSetup() }
                }
                inputState = state
                if (!draftEdited && keepCurrentInput) state.selection?.let { inputChoice = it }
                inputStatus = getString(R.string.setup__input_current, state.schemaName.ifEmpty { state.schemaId })
                inputLoaded = true
            } catch (e: TimeoutCancellationException) {
                inputStatus = getString(R.string.setup__input_failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to read the input setup")
                inputStatus = getString(R.string.setup__input_failed)
            } finally {
                inputJob = null
                changingInput = false
                renderStatus()
            }
        }
    }

    fun chooseInput(keepCurrent: Boolean, layout: InputLayout, variant: ChineseVariant) {
        val choice = InputSetupSelection(layout, variant)
        if (changingInput || changingStorageMode || (keepCurrentInput == keepCurrent && inputChoice == choice)) return
        keepCurrentInput = keepCurrent
        inputChoice = choice
        draftEdited = true
        SetupProgress.confirmInput(false)
        storageRevision++
        completedPages = completedPages - SetupPage.Input - SetupPage.Try
        renderStatus()
    }

    fun applyInputChoice() {
        if (changingInput || changingStorageMode || !isPageDone(SetupPage.Select)) return
        changingInput = true
        inputJob = lifecycleScope.launch {
            inputStatus = getString(R.string.setup__input_loading)
            renderStatus()
            try {
                val state = withTimeout(60_000) {
                    rimeSession().runOnReady {
                        if (keepCurrentInput) {
                            inspectInputSetup().also {
                                check(it.schemaId.isNotEmpty() && it.schemaId != ".default")
                            }
                        } else {
                            configureInput(inputChoice)
                        }
                    }
                }
                inputState = state
                inputLoaded = true
                SetupProgress.confirmInput(true)
                AppPrefs.defaultInstance().profile.freshInputSetupPending.setValue(false)
                inputStatus = getString(R.string.setup__input_applied, state.schemaName.ifEmpty { state.schemaId })
            } catch (e: TimeoutCancellationException) {
                inputStatus = getString(R.string.setup__input_failed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to apply the input setup")
                inputStatus = getString(
                    when {
                        (e as? InputSetupException)?.previousRestored == false -> R.string.setup__input_restore_failed
                        (e as? InputSetupException)?.reason == InputSetupFailure.PENDING_INPUT -> R.string.setup__input_pending
                        else -> R.string.setup__input_failed
                    },
                )
            } finally {
                inputJob = null
                changingInput = false
                storageRevision++
                refreshCurrentFragment()
            }
        }
    }

    fun openTrial() {
        if (!isPageDone(SetupPage.Input) || !isPageDone(SetupPage.Select)) return
        val input = supportFragmentManager.fragments.filterIsInstance<SetupFragment>()
            .firstOrNull { it.setupPage == SetupPage.Try }?.trialInput() ?: return
        input.requestFocus()
        WindowCompat.getInsetsController(window, input).show(WindowInsetsCompat.Type.ime())
    }

    /** Fragment pages attach after Window restores its hierarchy, so restore their focus here. */
    internal fun restoreTrialFocusIfReady() {
        if (!restoreTrialFocus || !::viewPager.isInitialized || currentSetupPage != SetupPage.Try ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !hasWindowFocus() || isFinishing || isDestroyed
        ) {
            return
        }
        val input = supportFragmentManager.fragments.filterIsInstance<SetupFragment>()
            .firstOrNull { it.setupPage == SetupPage.Try }?.trialInput() ?: return
        if (!input.isAttachedToWindow || !input.isShown || !input.isEnabled) return
        if (!input.isLaidOut || input.isLayoutRequested) {
            if (trialFocusFrame === input) return
            trialFocusFrame = input
            input.doOnPreDraw {
                if (trialFocusFrame === input) trialFocusFrame = null
                restoreTrialFocusIfReady()
            }
            return
        }
        // Requesting view focus does not explicitly show the IME or change trial checkpoints.
        if (input.requestFocus()) restoreTrialFocus = false
    }

    fun recordChineseCommit() {
        if (currentSetupPage != SetupPage.Try || !isPageDone(SetupPage.Input) ||
            !InputMethodUtils.checkIsTrimeSelected()
        ) {
            return
        }
        SetupProgress.completeTrial()
        storageRevision++
        refreshCurrentFragment()
    }

    fun deferTrial() {
        if (!isPageDone(SetupPage.Input)) return
        SetupProgress.defer()
        storageRevision++
        refreshCurrentFragment()
    }

    fun openAdvancedSettings() {
        startActivity(
            Intent(this, SetupAdvancedSettingsActivity::class.java).setAction(Intent.ACTION_RUN)
                .putExtra(MainActivity.EXTRA_SETTINGS_ROUTE, NavigationRoute.SchemaList),
        )
    }

    fun pickDataPath() {
        if (changingStorageMode || changingInput) return
        changingStorageMode = true
        storageRevision++
        renderStatus()
        lifecycleScope.launch {
            try {
                val picked = storageAccess.pickDirectory() ?: return@launch
                runCatching {
                    val stats = UserDataManager.importConfigurationWithSession(rimeSession(), picked.uri.toString())
                    storageRevision++
                    inputLoaded = false
                    keepCurrentInput = true
                    draftEdited = false
                    SetupProgress.confirmInput(false)
                    refreshCurrentFragment()
                    toast(getString(R.string.configuration_import_validated, stats.validatedConfigs, stats.copied + stats.deleted))
                }.onFailure {
                    if (it is CancellationException) throw it
                    Timber.w(it, "Unable to import selected data folder")
                    storageRevision++
                    refreshCurrentFragment()
                    val failure = it as? ConfigurationImportException
                    when {
                        failure == null -> toast(R.string.setup__data_path_import_failed)

                        failure.previousConfigurationRestored -> toast(R.string.configuration_import_rejected)

                        failure.recoveryDirectory != null -> toast(
                            getString(R.string.configuration_import_recovery_needed, failure.recoveryDirectory),
                        )

                        else -> toast(R.string.configuration_import_recovery_failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to pick data folder")
                toast(R.string.setup__data_path_import_failed)
            } finally {
                changingStorageMode = false
                storageRevision++
                refreshCurrentFragment()
            }
        }
    }

    fun refreshCurrentFragment() {
        refreshPending = true
        if (refreshJob?.isActive == true) return
        refreshJob = lifecycleScope.launch {
            while (refreshPending) {
                refreshPending = false
                val revision = storageRevision
                val pages = withContext(Dispatchers.IO) {
                    SetupPage.entries.filter {
                        runCatching { it.isDone() }
                            .onFailure { error -> Timber.w(error, "Unable to read setup status") }
                            .getOrDefault(false)
                    }.toSet()
                }
                if (revision != storageRevision) {
                    refreshPending = true
                    continue
                }
                completedPages = pages
                if (selectInitialPage) {
                    selectInitialPage = false
                    SetupPage.entries.firstOrNull { it !in pages }?.let {
                        viewPager.setCurrentItem(it.ordinal, false)
                    }
                }
                renderStatus()
            }
        }
    }

    private fun renderStatus() {
        supportFragmentManager.fragments.forEach { (it as? SetupFragment)?.sync() }
        updateButtons()
        // The async checkpoint read can enable this editor after the initial focus callbacks.
        restoreTrialFocusIfReady()
    }

    fun changeStorageMode(mode: DataStorageMode) {
        val prefs = AppPrefs.defaultInstance().profile
        if (changingStorageMode || changingInput || prefs.dataStorageMode.getValue() == mode) return
        changingStorageMode = true
        storageRevision++
        completedPages = completedPages - SetupPage.Mode
        renderStatus()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RimeMaintenanceMutex.withLock {
                        prefs.dataStorageMode.setValue(mode)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to change storage mode")
                toast(R.string.setup__data_path_import_failed)
            } finally {
                storageRevision++
                changingStorageMode = false
                refreshCurrentFragment()
            }
        }
    }

    private fun completeSetup(deferred: Boolean = false) {
        if (deferred) SetupProgress.defer()
        startActivity<MainActivity>()
        finish()
    }

    companion object {
        private var shown = false
        private const val CHANNEL_ID = "setup"
        private const val NOTIFY_ID = 87463

        suspend fun shouldShowUp(): Boolean {
            if (shown) return false
            val incomplete = withContext(Dispatchers.IO) {
                runCatching { SetupPage.hasUndonePage() }
                    .onFailure { Timber.w(it, "Unable to read initial setup status") }
                    .getOrDefault(true)
            }
            return !shown && incomplete
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivitySetupBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(
                sysBars.left,
                sysBars.top,
                sysBars.right,
                sysBars.bottom,
            )
            windowInsets
        }
        setContentView(binding.root)
        skipButton = binding.skipButton.apply {
            text = getString(R.string.setup__skip)
            setOnClickListener {
                PublicAlertDialogs
                    .builder(this@SetupActivity)
                    .setMessage(R.string.setup__skip_hint)
                    .setPositiveButton(R.string.setup__skip_hint_yes) { _, _ ->
                        completeSetup(deferred = true)
                    }.setNegativeButton(R.string.setup__skip_hint_no, null)
                    .show()
            }
        }
        prevButton =
            binding.prevButton.apply {
                text = getString(R.string.setup__prev)
                setOnClickListener { viewPager.currentItem -= 1 }
            }
        nextButton =
            binding.nextButton.apply {
                setOnClickListener {
                    if (!isPageDone(currentSetupPage)) return@setOnClickListener
                    if (viewPager.currentItem != SetupPage.entries.size - 1) {
                        viewPager.currentItem += 1
                    } else {
                        completeSetup()
                    }
                }
            }
        viewPager = binding.viewpager
        viewPager.adapter = Adapter()
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateButtons()
                    if (SetupPage.entries[position] == SetupPage.Input) loadInputState()
                    if (SetupPage.entries[position] == SetupPage.Try) {
                        restoreTrialFocusIfReady()
                    } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        restoreTrialFocus = false
                    }
                }
            },
        )
        selectInitialPage = savedInstanceState == null
        keepCurrentInput = savedInstanceState?.getBoolean("keep_current_input")
            ?: !AppPrefs.defaultInstance().profile.freshInputSetupPending.getValue()
        draftEdited = savedInstanceState?.getBoolean("input_draft_edited") ?: false
        restoreTrialFocus = savedInstanceState?.getBoolean("restore_trial_focus") ?: false
        inputChoice = InputSetupSelection(
            savedInstanceState?.getString("input_layout")?.let { InputLayout.valueOf(it) } ?: InputLayout.FULL_PINYIN,
            savedInstanceState?.getString("input_variant")?.let { ChineseVariant.valueOf(it) } ?: ChineseVariant.SIMPLIFIED,
        )
        SetupProgress.begin()
        updateButtons()
        refreshCurrentFragment()
        shown = true
        createNotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.setup_channel),
        )
    }

    fun updateButtons() {
        val allDone = completedPages.size == SetupPage.entries.size
        val modeSetupDone = !changingStorageMode && isPageDone(SetupPage.Mode)
        val isFirstPage = viewPager.currentItem == 0
        val isLastPage = viewPager.currentItem.isLastPage()

        // System steps and actual schema activation precede typing. Back remains available.
        viewPager.isUserInputEnabled = false

        prevButton.isGone = isFirstPage
        skipButton.isGone = !modeSetupDone || allDone
        nextButton.text = getString(if (isLastPage) R.string.done else R.string.setup__next)
        nextButton.isGone = isLastPage && !allDone
        nextButton.isEnabled = modeSetupDone && isPageDone(currentSetupPage) && !changingInput
    }

    override fun onStart() {
        super.onStart()
        // The system picker can restore focus before it saves the selected IME.
        // Keep observing while the dialog covers this Activity; resumed pages also refresh.
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            inputMethodObserver,
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
            false,
            inputMethodObserver,
        )
        observingInputMethods = true
    }

    override fun onStop() {
        observingInputMethods = false
        contentResolver.unregisterContentObserver(inputMethodObserver)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        refreshCurrentFragment()
        restoreTrialFocusIfReady()
    }

    override fun onPause() {
        if (completedPages.size != SetupPage.entries.size) {
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_trime_status)
                .setContentTitle(getText(R.string.trime_app_name))
                .setContentText(getText(R.string.setup__notify_hint))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, javaClass),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setAutoCancel(true)
                .build()
                .let { notificationManager.notify(NOTIFY_ID, it) }
        }
        super.onPause()
    }

    override fun onResume() {
        notificationManager.cancel(NOTIFY_ID)
        super.onResume()
        refreshCurrentFragment()
    }

    override fun onPostResume() {
        super.onPostResume()
        restoreTrialFocusIfReady()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("keep_current_input", keepCurrentInput)
        outState.putBoolean("input_draft_edited", draftEdited)
        outState.putString("input_layout", inputChoice.layout.name)
        outState.putString("input_variant", inputChoice.variant.name)
        saveWithTrialFocusOwner {
            outState.putBoolean("restore_trial_focus", restoreTrialFocus)
            super.onSaveInstanceState(outState)
        }
    }

    /** Keep the Window snapshot on a stable focus owner without exposing another editor. */
    internal fun <T> saveWithTrialFocusOwner(save: () -> T): T {
        val input = if (currentSetupPage == SetupPage.Try) {
            supportFragmentManager.fragments.filterIsInstance<SetupFragment>()
                .firstOrNull { it.setupPage == SetupPage.Try }?.trialInput()?.takeIf { it.hasFocus() }
        } else {
            null
        }
        restoreTrialFocus = restoreTrialFocus || input != null
        // Window's immediate focused-ID restoration cannot reach an asynchronously attached page.
        // Preserve that intent ourselves. Restore this same view in the same main-thread call so
        // an ordinary save does not expose another editor or an IME visibility change to a frame.
        val focusable = viewPager.isFocusable
        val focusableInTouchMode = viewPager.isFocusableInTouchMode
        val descendantFocusability = viewPager.descendantFocusability
        try {
            if (input != null) {
                // clearFocus alone can immediately refocus the only touch-focusable child.
                // The stable pager exists before its Fragment pages are attached on restore.
                viewPager.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                viewPager.isFocusableInTouchMode = true
                viewPager.requestFocus()
            }
            return save()
        } finally {
            viewPager.descendantFocusability = descendantFocusability
            viewPager.isFocusableInTouchMode = focusableInTouchMode
            viewPager.isFocusable = focusable
            if (input != null && hasWindowFocus() && !isFinishing && !isDestroyed &&
                currentSetupPage == SetupPage.Try && input.isAttachedToWindow && input.isShown && input.isEnabled
            ) {
                if (input.requestFocus()) restoreTrialFocus = false
            }
        }
    }

    override fun onDestroy() {
        trialFocusFrame = null
        inputJob?.cancel()
        if (inputSession != null) RimeDaemon.destroySession(inputSessionName)
        super.onDestroy()
    }

    private inner class Adapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = SetupPage.entries.size

        override fun createFragment(position: Int): Fragment = SetupFragment().apply {
            arguments = bundleOf("page" to SetupPage.entries[position])
        }
    }
}
