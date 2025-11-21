/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.databinding.ActivityClipEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import splitties.systemservices.inputMethodManager
import timber.log.Timber

class ClipEditActivity : Activity() {
    private val scope: CoroutineScope = MainScope()
    private var beanId: Int = -1
    private lateinit var editText: EditText
    private var clipType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        val binding =
            ActivityClipEditBinding.inflate(layoutInflater).apply {
                editText = clipEditText
                clipEditCancel.setOnClickListener { finish() }
                clipEditOk.setOnClickListener { finishEditing() }
            }
        setContentView(binding.root)
        inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        processIntent(intent)
    }

    private fun finishEditing() {
        val str = editText.editableText.toString()
        scope.launch(NonCancellable) {
            when (clipType) {
                FROM_CLIPBOARD -> ClipboardHelper.updateText(beanId, str)
                FROM_COLLECTION -> CollectionHelper.updateText(beanId, str)
                else -> {}
            }
        }
        finish()
    }

    private fun setBean(bean: DatabaseBean) {
        beanId = bean.id
        editText.setText(bean.text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        scope.launch {
            intent.run {
                val clipType = intent.getStringExtra(CLIP_TYPE) ?: return@launch
                val beanId = getIntExtra(BEAN_ID, -1)
                Timber.d("processIntent: id=$beanId, type=$clipType")
                when (clipType) {
                    FROM_CLIPBOARD -> ClipboardHelper.get(beanId)
                    FROM_COLLECTION -> CollectionHelper.get(beanId)
                    else -> null
                }?.also {
                    this@ClipEditActivity.clipType = clipType
                    setBean(it)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
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
    }
}
