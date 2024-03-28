package com.osfans.trime.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class LiquidLayout(context: Context, theme: Theme) : ConstraintLayout(context) {
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
            lParams(matchConstraints, KeyboardSwitcher.currentKeyboard.keyboardHeight) {
                topOfParent()
                centerHorizontally()
                bottomOfParent()
            },
        )
    }
}
