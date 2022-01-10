package com.osfans.trime.setup

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.R
import com.osfans.trime.databinding.ActivitySetupBinding
import com.osfans.trime.setup.SetupPage.Companion.firstUndonePage
import com.osfans.trime.setup.SetupPage.Companion.isLastPage

class SetupActivity : FragmentActivity() {
    private lateinit var viewPager: ViewPager2
    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val prevButton = binding.prevButton.apply {
            text = getString(R.string.setup__prev)
            setOnClickListener { viewPager.currentItem = viewPager.currentItem - 1 }
        }
        val nextButton = binding.nextButton.apply {
            setOnClickListener {
                if (viewPager.currentItem != SetupPage.values().size - 1) {
                    viewPager.currentItem = viewPager.currentItem + 1
                }
                else finish()
            }
        }
        viewPager = binding.viewpager
        viewPager.adapter = Adapter()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Manually call following observer when page changed
                // intentionally before changing the text of nextButton
                viewModel.isAllDone.value = viewModel.isAllDone.value
                // Hide prev button for the first page
                prevButton.visibility = if (position != 0) View.VISIBLE else View.GONE
                nextButton.text =
                    getString(
                        if (position.isLastPage())
                            R.string.setup__done else R.string.setup__next
                    )
            }
        })
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
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val fragment = supportFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        (fragment as SetupFragment).sync()
    }

    private inner class Adapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = SetupPage.values().size

        override fun createFragment(position: Int): Fragment =
            SetupFragment(SetupPage.values()[position])
    }
}
