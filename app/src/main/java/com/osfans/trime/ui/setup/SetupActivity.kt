// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.R
import com.osfans.trime.databinding.ActivitySetupBinding
import com.osfans.trime.ui.setup.SetupPage.Companion.firstUndonePage
import com.osfans.trime.ui.setup.SetupPage.Companion.isLastPage
import com.osfans.trime.util.appContext
import com.osfans.trime.util.createNotificationChannel
import splitties.systemservices.notificationManager

class SetupActivity : FragmentActivity() {
    private lateinit var viewPager: ViewPager2
    private val viewModel: SetupViewModel by viewModels()

    companion object {
        private var binaryCount = false
        private const val CHANNEL_ID = "setup"
        private const val NOTIFY_ID = 87463

        fun shouldSetup() = !binaryCount && SetupPage.hasUndonePage()
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
        val prevButton =
            binding.prevButton.apply {
                text = getString(R.string.setup__prev)
                setOnClickListener { viewPager.currentItem -= 1 }
            }
        binding.skipButton.apply {
            text = getString(R.string.setup__skip)
            setOnClickListener {
                AlertDialog
                    .Builder(this@SetupActivity)
                    .setMessage(R.string.setup__skip_hint)
                    .setPositiveButton(R.string.setup__skip_hint_yes) { _, _ ->
                        finish()
                    }.setNegativeButton(R.string.setup__skip_hint_no, null)
                    .show()
            }
        }
        val nextButton =
            binding.nextButton.apply {
                setOnClickListener {
                    if (viewPager.currentItem != SetupPage.entries.size - 1) {
                        viewPager.currentItem += 1
                    } else {
                        finish()
                    }
                }
            }
        viewPager = binding.viewpager
        viewPager.adapter = Adapter()
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    // Manually call following observer when page changed
                    // intentionally before changing the text of nextButton
                    viewModel.isAllDone.value = viewModel.isAllDone.value
                    // Hide prev button for the first page
                    prevButton.visibility = if (position != 0) View.VISIBLE else View.GONE
                    nextButton.text =
                        getString(
                            if (position.isLastPage()) {
                                R.string.setup__done
                            } else {
                                R.string.setup__next
                            },
                        )
                }
            },
        )
        viewModel.isAllDone.observe(this) { allDone ->
            nextButton.apply {
                // Hide next button for the last page when allDone == false
                (allDone || !viewPager.currentItem.isLastPage()).let {
                    visibility = if (it) View.VISIBLE else View.GONE
                }
            }
        }
        // Skip to undone page
        firstUndonePage()?.let { viewPager.currentItem = it.ordinal }
        binaryCount = true
        createNotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.setup_channel),
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val fragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        (fragment as SetupFragment).sync()
    }

    override fun onPause() {
        if (SetupPage.hasUndonePage()) {
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
    }

    private inner class Adapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = SetupPage.entries.size

        override fun createFragment(position: Int): Fragment =
            SetupFragment().apply {
                arguments = bundleOf("page" to SetupPage.entries[position])
            }
    }
}
