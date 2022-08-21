package com.osfans.trime.ui.main

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.blankj.utilcode.util.BarUtils
import com.osfans.trime.R
import com.osfans.trime.data.db.DbDao
import com.osfans.trime.databinding.LiquidKeyboardActivityBinding
import com.osfans.trime.ime.symbol.CheckableAdatper
import com.osfans.trime.ime.symbol.SimpleKeyBean
import timber.log.Timber

class LiquidKeyboardActivity : AppCompatActivity() {
    val CLIPBOARD = "clipboard"
    val COLLECTION = "collection"
    val DRAFT = "draft"
    var dbName = "clipboard"
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
                    DbDao(DbDao.COLLECTION).delete(r)
                else if (type.equals(DRAFT))
                    DbDao(DbDao.DRAFT).delete(r)
                else
                    DbDao(DbDao.CLIPBOARD).delete(r)

                Timber.d("delete " + r.size)
            }
        }

        binding.btnCollect.setOnClickListener {

            Timber.d("collect")
            mAdapter.collectSelected()
        }

        binding.btnEdit.setOnClickListener {

            val et = EditText(this)
            et.setText(mAdapter.checkedText)
            AlertDialog.Builder(this)
                .setTitle(R.string.edit)
                .setView(et)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    mAdapter.updateItem(et.text.toString())
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.btnMerge.setOnClickListener {
            Timber.d("merge")
            mAdapter.mergeItem()
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
        binding.progressBar.visibility = View.VISIBLE

        if (type.equals(COLLECTION)) {
            dbName = type + ".db"
            beans = DbDao(dbName).getAllSimpleBean(-1)
            binding.btnCollect.visibility = View.GONE
        } else if (type.equals(DRAFT)) {
            dbName = type + ".db"
            beans = DbDao(dbName).getAllSimpleBean(1000)
        } else {
            beans = DbDao(DbDao.CLIPBOARD).getAllSimpleBean(1000)
        }

        mAdapter = CheckableAdatper(
            this,
            R.layout.checkable_item,
            beans,
            dbName
        )
        binding.listWord.adapter = mAdapter

        binding.listWord.setOnItemClickListener({ parent: AdapterView<*>?, view: View?, i: Int, l: Long ->
            mAdapter.clickItem(
                i
            )
            val selected = mAdapter.checked.size
            if (selected> 1) {
                binding.btnEdit.visibility = View.GONE
                binding.btnMerge.visibility = View.VISIBLE
            } else {
                binding.btnEdit.visibility = View.VISIBLE
                binding.btnMerge.visibility = View.GONE
            }
        })
        binding.progressBar.visibility = View.GONE
    }
}
