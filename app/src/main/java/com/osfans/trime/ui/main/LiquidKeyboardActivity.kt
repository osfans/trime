package com.osfans.trime.ui.main

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.db.CollectionDao
import com.osfans.trime.data.db.clipboard.ClipboardDao
import com.osfans.trime.data.db.draft.DraftDao
import com.osfans.trime.databinding.LiquidKeyboardActivityBinding
import com.osfans.trime.ime.symbol.CheckableAdapter
import com.osfans.trime.util.applyTranslucentSystemBars
import com.osfans.trime.util.withLoadingDialog
import timber.log.Timber

class LiquidKeyboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTranslucentSystemBars()
        val binding = LiquidKeyboardActivityBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = navBars.left
                rightMargin = navBars.right
                bottomMargin = navBars.bottom
            }
            binding.toolbar.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBars.top
            }
            windowInsets
        }

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar.toolbar)

        val type = Type.valueOf(intent.getStringExtra("type")!!.uppercase())
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            when (type) {
                Type.CLIPBOARD -> setTitle(R.string.other_managed_clipboard)
                Type.DRAFT -> setTitle(R.string.other_managed_draft)
                Type.COLLECTION -> setTitle(R.string.other_managed_collection)
            }
        }

        // getDbData
        with(binding) {
            lifecycleScope.withLoadingDialog(this@LiquidKeyboardActivity) {
                val entries = when (type) {
                    Type.CLIPBOARD -> ClipboardDao.get().getAllSimpleBean(1000)
                    Type.DRAFT -> DraftDao.get().getAllSimpleBean(1000)
                    Type.COLLECTION -> CollectionDao.get().allSimpleBean.also {
                        collectButton.visibility = View.GONE
                    }
                }

                val adapter = CheckableAdapter(this@LiquidKeyboardActivity, entries)
                beanList.adapter = adapter

                beanList.setOnItemClickListener { _, _, position, _ ->
                    adapter.clickItem(position)
                    beanButtons.visibility =
                        if (adapter.checked.isEmpty()) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }
                }

                deleteButton.setOnClickListener {
                    val position =
                        with(beanList.getPositionForView(beanList.getChildAt(0))) {
                            if (this > 0) this + 1 else this
                        }
                    val r = adapter.remove(position)
                    if (r.isNotEmpty()) {
                        when (type) {
                            Type.CLIPBOARD -> ClipboardDao.get().delete(r)
                            Type.DRAFT -> DraftDao.get().delete(r)
                            Type.COLLECTION -> CollectionDao.get().delete(r)
                        }
                        Timber.d("deleted %s beans", r.size)
                    }
                }

                collectButton.apply {
                    setOnClickListener {
                        adapter.collectSelected()
                        Timber.d("collected selected beans")
                    }
                }
            }
        }
    }

    enum class Type {
        CLIPBOARD, DRAFT, COLLECTION
    }
}
