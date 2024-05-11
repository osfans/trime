// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.EventManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.gravityCenter
import splitties.views.padding

@SuppressLint("ViewConstructor")
class LiquidLayout(context: Context, service: TrimeInputMethodService, theme: Theme) :
    ConstraintLayout(context) {
    // TODO: 继承一个键盘视图嵌入到这里，而不是自定义一个视图
    private val fixedKeyBar =
        constraintLayout {
            val operations = theme.liquid.getMap("fixed_key_bar")?.get("keys")?.configList
            operations?.let {
                val btns =
                    Array(it.size) {
                        val operation = operations.get(it)
                        val text =
                            textView {
                                text =
                                    theme.presetKeys?.get(operation.toString())?.configMap?.get("label")
                                        .toString()
                                textSize = theme.generalStyle.labelTextSize.toFloat()
                                typeface = FontManager.getTypeface("key_font")
                                ColorManager.getColor("key_text_color")
                                    ?.let { color -> setTextColor(color) }
                            }
                        val root =
                            frameLayout {
                                add(
                                    text,
                                    lParams(matchParent, wrapContent) {
                                        gravity = gravityCenter
                                        padding = dp(5)
                                    },
                                )
                                ColorManager.getColor("key_back_color")
                                    ?.let { bg -> backgroundColor = bg }
                                // todo 想办法实现退格键、空格键等 repeatable: true 长按连续触发
                                setOnClickListener {
                                    val event = EventManager.getEvent(operation.toString())
                                    service.textInputManager?.run {
                                        onPress(event.code)
                                        onEvent(event)
                                    }
                                }
                            }
                        return@Array root
                    }
                when (
                    theme.liquid.getMap("fixed_key_bar")
                        ?.get("position")?.configValue.toString()
                ) {
                    LEFT, RIGHT -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(wrapContent, matchConstraints) {
                                    if (i == 0) topOfParent() else below(btns[i - 1])
                                    if (i == btns.size - 1) bottomOfParent() else above(btns[i + 1])
                                },
                            )
                        }
                    }

                    TOP, BOTTOM -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(wrapContent, matchConstraints) {
                                    if (i == 0) startOfParent() else after(btns[i - 1])
                                    if (i == btns.size - 1) endOfParent() else before(btns[i + 1])
                                },
                            )
                        }
                    }
                }
            }
        }

    val boardView =
        recyclerView {
            val space = dp(3)
            addItemDecoration(SpacesItemDecoration(space))
            setPadding(space)
        }

    val tabsUi = LiquidTabsUi(context, theme)

    init {
        when (theme.liquid.getMap("fixed_key_bar")?.get("position")?.configValue.toString() ?: "") {
            TOP -> {
                add(
                    boardView,
                    lParams {
                        centerHorizontally()
                        topToBottomOf(fixedKeyBar)
                        bottomOfParent()
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, wrapContent) {
                        centerHorizontally()
                        topOfParent()
                        bottomToTopOf(boardView)
                    },
                )
            }

            BOTTOM -> {
                add(
                    boardView,
                    lParams {
                        centerHorizontally()
                        topOfParent()
                        bottomToTopOf(fixedKeyBar)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, wrapContent) {
                        centerHorizontally()
                        topToBottomOf(boardView)
                        bottomOfParent()
                    },
                )
            }

            LEFT -> {
                add(
                    boardView,
                    lParams {
                        centerVertically()
                        startToEndOf(fixedKeyBar)
                        endOfParent()
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        centerVertically()
                        startOfParent()
                        endToStartOf(boardView)
                    },
                )
            }

            RIGHT -> {
                add(
                    boardView,
                    lParams {
                        centerVertically()
                        startOfParent()
                        endToStartOf(fixedKeyBar)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        centerVertically()
                        startToEndOf(boardView)
                        endOfParent()
                    },
                )
            }

            else -> {
                add(
                    boardView,
                    lParams {
                        centerVertically()
                        startOfParent()
                        endOfParent()
                    },
                )
            }
        }
    }

    companion object {
        private const val TOP = "top"
        private const val BOTTOM = "bottom"
        private const val LEFT = "left"
        private const val RIGHT = "right"
    }
}
