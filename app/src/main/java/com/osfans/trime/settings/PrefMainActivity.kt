package com.osfans.trime.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.osfans.trime.databinding.PrefActivityBinding

class PrefMainActivity: AppCompatActivity() {

    lateinit var binding: PrefActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PrefActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}