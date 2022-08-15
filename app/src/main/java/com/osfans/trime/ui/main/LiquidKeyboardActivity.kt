package com.osfans.trime.ui.main

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.blankj.utilcode.util.BarUtils
import com.osfans.trime.R
import com.osfans.trime.data.db.CollectionDao
import com.osfans.trime.data.db.clipboard.ClipboardDao
import com.osfans.trime.data.db.draft.DraftDao
import com.osfans.trime.databinding.LiquidKeyboardActivityBinding
import com.osfans.trime.ime.symbol.CheckableAdatper
import com.osfans.trime.ime.symbol.SimpleKeyBean
import timber.log.Timber

class LiquidKeyboardActivity : AppCompatActivity() {
    val CLIPBOARD = "clipboard"
    val COLLECTION = "collection"
    val DRAFT = "draft"
    lateinit var binding: LiquidKeyboardActivityBinding
    var type: String = CLIPBOARD
    var beans: List<SimpleKeyBean> = ArrayList()
    lateinit var mAdapter: CheckableAdatper
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = LiquidKeyboardActivityBinding.inflate(layoutInflater)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BarUtils.setNavBarColor(
                this,
                getColor(R.color.windowBackground)
            )
        } else
            BarUtils.setNavBarColor(
                this,
                @Suppress("DEPRECATION")
                resources.getColor(R.color.windowBackground)
            )
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        type = intent.getStringExtra("type").toString()
        if (type.equals(COLLECTION)) {
            setTitle(R.string.other__list_collection_title)
        } else if (type.equals(DRAFT)) {
            setTitle(R.string.other__list_draft_title)
        } else {
            type = CLIPBOARD
            setTitle(R.string.other__list_clipboard_title)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        getDbData()

        binding.btnDel.setOnClickListener {

            var position: Int =
                binding.listWord.getPositionForView(binding.listWord.getChildAt(0))
            if (position > 0) position++
            val r: List<SimpleKeyBean> = mAdapter.remove(position)
            if (r.isNotEmpty()) {
                if (type.equals(COLLECTION))
                    CollectionDao.get().delete(r)
                else if (type.equals(DRAFT))
                    DraftDao.get().delete(r)
                else
                    ClipboardDao.get().delete(r)

                Timber.d("delete " + r.size)
            }
        }

        binding.btnCollect.setOnClickListener {

            Timber.d("collect")
            mAdapter.collectSelected()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    fun getDbData() {
        binding.progressBar.setVisibility(View.VISIBLE)

        if (type.equals(COLLECTION)) {
            beans = CollectionDao.get().allSimpleBean
            binding.btnCollect.setVisibility(View.GONE)
        } else if (type.equals(DRAFT)) {
            beans = DraftDao.get().getAllSimpleBean(1000)
        } else {
            beans = ClipboardDao.get().getAllSimpleBean(1000)
        }

        mAdapter = CheckableAdatper(
            this,
            R.layout.checkable_item,
            beans
        )
        binding.listWord.setAdapter(mAdapter)

        binding.listWord.setOnItemClickListener({ parent: AdapterView<*>?, view: View?, i: Int, l: Long ->
            mAdapter.clickItem(
                i
            )
        })
        binding.progressBar.setVisibility(View.GONE)
    }
}
