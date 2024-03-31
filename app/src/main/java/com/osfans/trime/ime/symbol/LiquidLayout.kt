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
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
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
class LiquidLayout(context: Context, service: TrimeInputMethodService, theme: Theme) : ConstraintLayout(context) {
    // TODO: 继承一个键盘视图嵌入到这里，而不是自定义一个视图
    val operations =
        constraintLayout {
            val btns =
                Array(SimpleKeyDao.operations.size) {
                    val operation = SimpleKeyDao.operations[it]
                    val text =
                        textView {
                            text = operation.second
                            textSize = theme.style.getFloat("label_text_size")
                            typeface = FontManager.getTypeface("key_font")
                            ColorManager.getColor("key_text_color")?.let { color -> setTextColor(color) }
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
                            ColorManager.getColor("key_back_color")?.let { bg -> backgroundColor = bg }
                            setOnClickListener {
                                // TODO: 这个方式不太优雅，还需打磨
                                if (operation.first == "liquid_keyboard_exit") {
                                    service.selectLiquidKeyboard(-1)
                                } else {
                                    val event = EventManager.getEvent(operation.first)
                                    service.textInputManager?.run {
                                        onPress(event.code)
                                        onEvent(event)
                                    }
                                }
                            }
                        }
                    return@Array root
                }
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

    val boardView =
        recyclerView {
            val space = dp(3)
            addItemDecoration(SpacesItemDecoration(space))
            setPadding(space)
        }

    val tabsUi = LiquidTabsUi(context, theme)

    init {
        add(
            boardView,
            lParams {
                centerVertically()
                startOfParent()
                endToStartOf(operations)
            },
        )
        add(
            operations,
            lParams(wrapContent, matchConstraints) {
                centerVertically()
                startToEndOf(boardView)
                endOfParent()
            },
        )
    }
}
