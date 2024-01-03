package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.databinding.InitialKeyboardBinding

/**
 * Keyboard to be displayed before Rime is deployed completely.
 *
 * It displays a loading screen (if deploying) or error message (if cannot deploy).
 *
 * TODO: Add a help or info button to show what's problem is behind the scene.
 */
class InitialKeyboard(context: Context) {
    private var binding = InitialKeyboardBinding.inflate(LayoutInflater.from(context))

    fun change(start: Boolean): View {
        if (start) {
            binding.progressBar.visibility = View.VISIBLE
            binding.deploying.setText(R.string.deploy_progress)
        } else {
            binding.progressBar.visibility = View.INVISIBLE
            binding.deploying.setText(R.string.external_storage_permission_not_available)
        }
        return binding.root
    }
}
