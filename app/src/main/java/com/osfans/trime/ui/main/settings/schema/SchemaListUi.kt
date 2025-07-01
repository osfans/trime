/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedDispatcher
import androidx.coordinatorlayout.widget.CoordinatorLayout
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
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.ui.components.OnItemChangedListener
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

class SchemaListUi(
    override val ctx: Context,
    private val initialEntries: List<SchemaItem>,
    private val contentSource: SchemaListUi.() -> Array<SchemaItem>,
) : Ui {
    private var shouldShowFab = false

    private val fab =
        view(::FloatingActionButton) {
            imageDrawable =
                drawable(R.drawable.ic_baseline_add_24)!!.apply {
                    setTint(styledColor(android.R.attr.colorForegroundInverse))
                }
        }

    private var suspendUndo = false

    private fun showUndoSnackBar(
        text: String,
        action: () -> Unit,
    ) {
        if (suspendUndo) return
        Snackbar
            .make(root, text, Snackbar.LENGTH_SHORT)
            .setAction(R.string.undo) {
                suspendUndo = true
                action.invoke()
                suspendUndo = false
            }.addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onShown(transientBottomBar: Snackbar) {
                        // snackbar is invisible when it attached to parent,
                        // but change visibility won't trigger `onDependentViewChanged`.
                        // so we need to update fab position when snackbar fully shown
                        // see [^1]
                        fab.translationY = -transientBottomBar.view.height.toFloat()
                    }
                },
            ).show()
    }

    fun updateFAB() {
        val source = contentSource(this)
        if (source.isEmpty()) {
            shouldShowFab = false
            fab.hide()
        } else {
            shouldShowFab = true
            fab.show()
            fab.setOnClickListener {
                val items = source.map { it.name }.toTypedArray()
                AlertDialog
                    .Builder(ctx)
                    .setTitle(R.string.enable_schemata)
                    .setItems(items) { _, which ->
                        adapter.add(source[which])
                    }.show()
            }
        }
    }

    val adapter =
        object : SchemaListAdapter(initialEntries) {
            init {
                addOnItemChangedListener(
                    object : OnItemChangedListener<SchemaItem> {
                        override fun onItemAdded(
                            idx: Int,
                            item: SchemaItem,
                        ) {
                            updateFAB()
                            showUndoSnackBar(ctx.getString(R.string.added_x, item.name)) {
                                remove(item)
                            }
                        }

                        override fun onItemRemoved(
                            idx: Int,
                            item: SchemaItem,
                        ) {
                            updateFAB()
                            showUndoSnackBar(ctx.getString(R.string.removed_x, item.name)) {
                                add(idx, item)
                            }
                        }

                        override fun onItemRemovedBatch(items: List<SchemaItem>) {
                            updateFAB()
                            showUndoSnackBar(ctx.getString(R.string.removed_n_items, items.size)) {
                                items.forEach { add(it) }
                            }
                        }
                    },
                )
            }

            override fun enterMultiSelect(onBackPressedDispatcher: OnBackPressedDispatcher) {
                if (shouldShowFab) {
                    fab.hide()
                }
                super.enterMultiSelect(onBackPressedDispatcher)
            }

            override fun exitMultiSelect() {
                if (shouldShowFab) {
                    fab.show()
                }
                super.exitMultiSelect()
            }
        }

    private val list =
        recyclerView {
            layoutManager = verticalLayoutManager()
            adapter = this@SchemaListUi.adapter
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

    override val root =
        coordinatorLayout {
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
            updateFAB()
        }
}
