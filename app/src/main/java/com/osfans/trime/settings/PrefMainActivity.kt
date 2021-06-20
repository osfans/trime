package com.osfans.trime.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.osfans.trime.R
import com.osfans.trime.databinding.PrefActivityBinding

internal const val FRAGMENT_TAG = "FRAGMENT_TAG"

class PrefMainActivity: AppCompatActivity() {

    lateinit var binding: PrefActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PrefActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(binding.preference.id, fragment, FRAGMENT_TAG)
            .commit()
    }
}