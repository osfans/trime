/**
 * Adapted from [fcitx5-android/LogActivity.kt](https://github.com/fcitx5-android/fcitx5-android/blob/e44c1c7/app/src/main/java/org/fcitx/fcitx5/android/ui/main/LogActivity.kt)
 */
package com.osfans.trime.settings

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cat.ereza.customactivityoncrash.CustomActivityOnCrash
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.databinding.ActivityLogBinding
import com.osfans.trime.settings.components.LogView
import com.osfans.trime.util.DeviceInfo
import com.osfans.trime.util.Logcat
import com.osfans.trime.util.bindOnNotNull
import com.osfans.trime.util.iso8601UTCDateTime
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

class LogActivity : AppCompatActivity() {

    private lateinit var launcher: ActivityResultLauncher<String>
    private lateinit var logView: LogView

    private fun registerLauncher() {
        launcher = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
            lifecycleScope.launch(NonCancellable + Dispatchers.IO) {
                runCatching {
                    if (uri != null)
                        contentResolver.openOutputStream(uri)?.let { OutputStreamWriter(it) }
                    else null
                }.bindOnNotNull { x ->
                    x.use {
                        logView
                            .currentLog
                            .let { log ->
                                runCatching {
                                    it.write("--------- Build Info\n")
                                    it.write("${BuildConfig.BUILD_INFO}\n")
                                    it.write("--------- Device Info\n")
                                    it.write(DeviceInfo.get(this@LogActivity))
                                    it.write(log.toString())
                                }
                            }
                    }
                }?.toast()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        with(binding) {
            setSupportActionBar(toolbar.toolbar)
            this@LogActivity.logView = logView
            logView.setLogcat(
                if (CustomActivityOnCrash.getConfigFromIntent(intent) == null) {
                    supportActionBar!!.apply {
                        setDisplayHomeAsUpEnabled(true)
                        setTitle(R.string.real_time_logs)
                    }
                    Logcat()
                } else {
                    supportActionBar!!.setTitle(R.string.crash_logs)
                    clearButton.visibility = View.GONE
                    AlertDialog.Builder(this@LogActivity)
                        .setTitle(R.string.app_crash)
                        .setMessage(R.string.app_crash_message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
                    Logcat(TrimeApplication.getLastPid())
                }
            )
            clearButton.setOnClickListener {
                logView.clear()
            }
            exportButton.setOnClickListener {
                launcher.launch("$packageName-${iso8601UTCDateTime()}.txt")
            }
        }
        registerLauncher()
    }
}
