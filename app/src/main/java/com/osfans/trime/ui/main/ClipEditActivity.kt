/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.TextUpdateResult
import com.osfans.trime.databinding.ActivityClipEditBinding
import com.osfans.trime.util.appContext
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class ClipEditActivity : Activity() {
    private val scope: CoroutineScope = MainScope()
    private var beanId: Int = -1
    private lateinit var editText: EditText
    private var clipType: String? = null
    private lateinit var binding: ActivityClipEditBinding
    private var pendingUi: Job? = null
    private var requestVersion = 0
    private var loading = false
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        binding =
            ActivityClipEditBinding.inflate(layoutInflater).apply {
                editText = clipEditText
                clipEditCancel.setOnClickListener { finish() }
                clipEditOk.setOnClickListener { finishEditing() }
            }
        setContentView(binding.root)
        editText.requestFocus()
        editText.post {
            WindowCompat.getInsetsController(window, editText).show(WindowInsetsCompat.Type.ime())
        }
        processIntent(intent)
    }

    private fun finishEditing() {
        if (loading || saving) return
        val id = beanId
        val type = clipType
        if (id < 0 || type == null) {
            finish()
            return
        }
        val str = editText.editableText.toString()
        val version = requestVersion
        saving = true
        updateControls()
        val write = persistEdit(type, id, str)
        pendingUi = scope.launch {
            val result = write.await()
            if (version != requestVersion) return@launch
            saving = false
            when (result.getOrNull()) {
                TextUpdateResult.SAVED -> finish()

                TextUpdateResult.NOT_FOUND -> {
                    updateControls()
                    editText.error = getString(R.string.clipboard_entry_removed)
                }

                null -> updateControls()
            }
        }
    }

    private fun updateControls() {
        editText.isEnabled = !loading && !saving
        binding.clipEditOk.isEnabled = !loading && !saving
        binding.clipEditCancel.isEnabled = !saving
    }

    private fun setBean(bean: DatabaseBean) {
        beanId = bean.id
        editText.error = null
        editText.setText(bean.text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        val version = ++requestVersion
        pendingUi?.cancel()
        beanId = -1
        clipType = null
        saving = false
        val type = intent.getStringExtra(CLIP_TYPE)
        val id = intent.getIntExtra(BEAN_ID, -1)
        loading = id >= 0 && type in setOf(FROM_CLIPBOARD, FROM_COLLECTION)
        updateControls()
        if (!loading) return
        pendingUi = scope.launch {
            try {
                val bean = when (type) {
                    FROM_CLIPBOARD -> ClipboardHelper.get(id)
                    else -> CollectionHelper.get(id)
                }
                if (version != requestVersion) return@launch
                if (bean == null) {
                    toast(R.string.clipboard_entry_removed)
                    finish()
                    return@launch
                }
                clipType = type
                setBean(bean)
                loading = false
                updateControls()
                editText.requestFocus()
                editText.post {
                    if (!isFinishing) WindowCompat.getInsetsController(window, editText).show(WindowInsetsCompat.Type.ime())
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Timber.e(failure, "Unable to open clipboard entry")
                if (version != requestVersion) return@launch
                toast(R.string.clipboard_load_failed)
                finish()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val BEAN_ID = "id"
        const val CLIP_TYPE = "clip_type"
        const val FROM_CLIPBOARD = "from_clipboard"
        const val FROM_COLLECTION = "from_collection"

        // The confirmed write belongs to the database scope, not to the dialog.
        // Capture only immutable values so a destroyed Activity is not retained.
        private fun persistEdit(type: String, id: Int, text: String) = (if (type == FROM_CLIPBOARD) ClipboardHelper else CollectionHelper).async(Dispatchers.Main.immediate) {
            try {
                val outcome = when (type) {
                    FROM_CLIPBOARD -> ClipboardHelper.updateText(id, text)
                    else -> CollectionHelper.updateText(id, text)
                }
                Result.success(outcome)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                Timber.e(failure, "Unable to save clipboard entry")
                appContext.toast(R.string.clipboard_save_failed)
                Result.failure(failure)
            }
        }
    }
}
