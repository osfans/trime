// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import splitties.dimensions.dp
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
class LiquidLayout(
    context: Context,
    theme: Theme,
    commonKeyboardActionListener: CommonKeyboardActionListener,
) : ConstraintLayout(context) {
    // TODO: 继承一个键盘视图嵌入到这里，而不是自定义一个视图
    private val fixedKeyBar =
        constraintLayout {
            val fixedKeys =
                theme.liquidKeyboard.fixedKeyBar.keys
            if (fixedKeys.isNotEmpty()) {
                val btns =
                    Array(fixedKeys.size) { index ->
                        val presetKeyName = fixedKeys[index]
                        val text =
                            textView {
                                text =
                                    theme.presetKeys[presetKeyName]?.label ?: ""
                                textSize = theme.generalStyle.labelTextSize
                                typeface = FontManager.getTypeface("key_font")
                                setTextColor(ColorManager.getColor("key_text_color"))
                            }
                        val root =
                            frameLayout {
                                background =
                                    ColorManager.getDrawable(
                                        "key_back_color",
                                        "key_border_color",
                                        dp(theme.generalStyle.keyBorder),
                                        dp(theme.generalStyle.roundCorner),
                                        cache = false,
                                    )
                                add(
                                    text,
                                    lParams(matchParent, wrapContent) {
                                        gravity = gravityCenter
                                        padding = dp(5)
                                    },
                                )
                                // todo 想办法实现退格键、空格键等 repeatable: true 长按连续触发
                                setOnClickListener {
                                    val event = KeyActionManager.getAction(presetKeyName)
                                    commonKeyboardActionListener.listener.run {
                                        onPress(event.code)
                                        onAction(event)
                                    }
                                }
                            }
                        return@Array root
                    }
                val marginX = theme.liquidKeyboard.marginX
                when (theme.liquidKeyboard.fixedKeyBar.position) {
                    LiquidKeyboard.KeyBar.Position.LEFT,
                    LiquidKeyboard.KeyBar.Position.RIGHT,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(wrapContent, matchConstraints) {
                                    if (i == 0) {
                                        topOfParent()
                                    } else {
                                        topMargin = dp(marginX).toInt()
                                        below(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        bottomOfParent()
                                    } else {
                                        bottomMargin = dp(marginX).toInt()
                                        above(btns[i + 1])
                                    }
                                },
                            )
                        }
                    }
                    LiquidKeyboard.KeyBar.Position.TOP,
                    LiquidKeyboard.KeyBar.Position.BOTTOM,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(wrapContent, matchConstraints) {
                                    if (i == 0) {
                                        startOfParent()
                                    } else {
                                        leftMargin = dp(marginX).toInt()
                                        after(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        endOfParent()
                                    } else {
                                        rightMargin = dp(marginX).toInt()
                                        before(btns[i + 1])
                                    }
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
        when (theme.liquidKeyboard.fixedKeyBar.position) {
            LiquidKeyboard.KeyBar.Position.TOP -> {
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
            LiquidKeyboard.KeyBar.Position.BOTTOM -> {
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
            LiquidKeyboard.KeyBar.Position.LEFT -> {
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
            LiquidKeyboard.KeyBar.Position.RIGHT -> {
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
        }
    }
}
