/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package com.osfans.trime.ui.main.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.ui.main.MainViewModel
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.styles.AndroidStyles
import splitties.views.dsl.core.verticalMargin

abstract class ProgressFragment : Fragment() {
    private lateinit var root: FrameLayout

    @Volatile
    protected var isInitialized = false
        private set

    abstract suspend fun initialize(): View

    protected val viewModel: MainViewModel by activityViewModels()

    protected val rime
        get() = viewModel.rime

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        requireContext()
            .frameLayout {
                val androidStyles = AndroidStyles(requireContext())
                add(
                    androidStyles.progressBar.default { isIndeterminate = true },
                    lParams {
                        width = matchParent
                        verticalMargin = dp(20)
                        horizontalMargin = dp(26)
                    },
                )
            }.also { root = it }

    final override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        lifecycleScope.launch {
            val newView = initialize().apply { alpha = 0f }
            isInitialized = true
            root.removeAllViews()
            root.addView(newView)
            newView.animate().setDuration(150L).alphaBy(1f)
        }
    }
}
