/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.userdict

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.styledColor
import splitties.views.backgroundColor
import splitties.views.bottomPadding
import splitties.views.dsl.coordinatorlayout.coordinatorLayout
import splitties.views.dsl.coordinatorlayout.defaultLParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.margin
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityEndBottom
import splitties.views.imageDrawable
import splitties.views.recyclerview.verticalLayoutManager
import kotlin.math.min

class UserDictListUi(
    override val ctx: Context,
    entries: Array<String>,
    initMoreButton: (ImageButton.(String) -> Unit) = {},
) : Ui {
    val fab =
        view(::FloatingActionButton) {
            imageDrawable =
                drawable(R.drawable.ic_baseline_add_24)!!.apply {
                    setTint(styledColor(android.R.attr.colorForegroundInverse))
                }
        }

    fun showSnackBar(text: String) {
        Snackbar.make(root, text, Snackbar.LENGTH_SHORT)
            .addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onShown(transientBottomBar: Snackbar) {
                        // snackbar is invisible when it attached to parent,
                        // but change visibility won't trigger `onDependentViewChanged`.
                        // so we need to update fab position when snackbar fully shown
                        // see [^1]
                        fab.translationY = -transientBottomBar.view.height.toFloat()
                    }
                },
            )
            .show()
    }

    val adapter by lazy { UserDictListAdapter(entries.toList(), initMoreButton) }

    private val list = recyclerView {
        layoutManager = verticalLayoutManager()
        adapter = this@UserDictListUi.adapter
        clipToPadding = false
    }

    private fun updateViewMargin(insets: WindowInsetsCompat? = null) {
        val windowInsets = (insets ?: ViewCompat.getRootWindowInsets(root)) ?: return
        val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
        fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBars.bottom + ctx.dp(16)
        }
        list.bottomPadding = navBars.bottom
    }

    override val root = coordinatorLayout {
        backgroundColor = styledColor(android.R.attr.colorBackground)
        add(
            list,
            defaultLParams {
                height = matchParent
                width = matchParent
            },
        )
        add(
            fab,
            defaultLParams {
                gravity = gravityEndBottom
                margin = dp(16)
                behavior =
                    object : HideBottomViewOnScrollBehavior<FloatingActionButton>() {
                        @SuppressLint("RestrictedApi")
                        override fun layoutDependsOn(
                            parent: CoordinatorLayout,
                            child: FloatingActionButton,
                            dependency: View,
                        ): Boolean = dependency is Snackbar.SnackbarLayout

                        override fun onDependentViewChanged(
                            parent: CoordinatorLayout,
                            child: FloatingActionButton,
                            dependency: View,
                        ): Boolean {
                            // [^1]: snackbar is invisible when it attached to parent
                            // update fab position only when snackbar is visible
                            if (dependency.isVisible) {
                                child.translationY = min(0f, dependency.translationY - dependency.height)
                                return true
                            }
                            return false
                        }

                        override fun onDependentViewRemoved(
                            parent: CoordinatorLayout,
                            child: FloatingActionButton,
                            dependency: View,
                        ) {
                            child.translationY = 0f
                        }
                    }
            },
        )
        doOnAttach { updateViewMargin() }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            updateViewMargin(windowInsets)
            windowInsets
        }
    }
}
