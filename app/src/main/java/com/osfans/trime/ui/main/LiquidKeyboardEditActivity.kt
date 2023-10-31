package com.osfans.trime.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.DraftHelper
import com.osfans.trime.databinding.ActivityLiquidKeyboardEditBinding
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ime.enums.SymbolKeyboardType
import kotlinx.coroutines.launch
import timber.log.Timber

class LiquidKeyboardEditActivity : AppCompatActivity() {
    private val service: Trime = Trime.getService()
    private var id: Int? = null
    private lateinit var editText: EditText
    private var type: SymbolKeyboardType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        val binding =
            ActivityLiquidKeyboardEditBinding.inflate(layoutInflater).apply {
                editText = liquidKeyboardEditText
                liquidKeyboardEditCancel.setOnClickListener { finish() }
                liquidKeyboardEditOk.setOnClickListener {
                    editHandler()
                    finish()
                }
            }
        setContentView(binding.root)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        // Extract necessary values.
        if (intent.extras != null) {
            val strType = intent.getStringExtra(LIQUID_KEYBOARD_TYPE)
            type = SymbolKeyboardType.fromString(strType)
            id = intent.getIntExtra(DB_BEAN_ID, -1)
            val text = intent.getStringExtra(DB_BEAN_TEXT)
            editText.setText(text)
            Timber.d(
                "LiquidKeyboardEditActivity:processIntent (type=$type, id=$id, text=$text)",
            )
        }
    }

    private fun editHandler() {
        // Submit modifications.
        if (id == null || id == -1) return
        val newText = editText.text.toString()
        when (type) {
            SymbolKeyboardType.CLIPBOARD -> {
                service.lifecycleScope.launch {
                    ClipboardHelper.updateText(id!!, newText)
                }
            }

            SymbolKeyboardType.COLLECTION -> {
                service.lifecycleScope.launch {
                    CollectionHelper.updateText(id!!, newText)
                }
            }

            SymbolKeyboardType.DRAFT -> {
                service.lifecycleScope.launch {
                    DraftHelper.updateText(id!!, newText)
                }
            }

            else -> return
        }
    }

    override fun onNewIntent(intent: Intent) {
        processIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    companion object {
        const val DB_BEAN_ID = "db_bean_id"
        const val DB_BEAN_TEXT = "db_bean_text"
        const val LIQUID_KEYBOARD_TYPE = "liquid_keyboard_type"
    }
}
