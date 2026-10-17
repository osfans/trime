/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.osfans.trime.core.T9Action
import com.osfans.trime.core.T9StateProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.composition.T9DisambiguationView

/** Actual Android view regression, called on the main thread by the startup probe. */
object ThemeMergeRegression {
    fun verify(context: Context, state: T9StateProto) {
        val scope = checkNotNull(ColorManager.currentScope())
        val original = ColorManager.activeColorScheme
        val selection = com.osfans.trime.data.theme.ThemeManager.prefs.normalModeColor.getValue()
        val alternate = original +
            mapOf(
                "candidate_text_color" to "#123456",
                "hilited_candidate_text_color" to "#fefdfc",
                "hilited_candidate_back_color" to "#543210",
                "candidate_background" to "#ffffff",
            )
        try {
            for (orientation in listOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE)) {
                ColorManager.setColorSchemeForTesting(original)
                val configuration = Configuration(context.resources.configuration).apply { this.orientation = orientation }
                val themedContext = context.createConfigurationContext(configuration)
                val actions = mutableListOf<Pair<Int, T9Action>>()
                val view = T9DisambiguationView(themedContext, scope) { revision, action, _, _, _ ->
                    actions += revision to action
                }
                view.update(state)
                layout(view)
                val height = view.measuredHeight
                val before = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }
                check("ni" in before && "426" in before) { "Missing locked syllable or suffix" }
                ColorManager.setColorSchemeForTesting(alternate)
                check(ColorManager.currentScope() === scope) { "Scheme switch replaced the injected scope" }
                val borderless = checkNotNull(
                    scope.decorDrawable(
                        colorKey = "candidate_background",
                        borderColorKey = "candidate_border_color",
                        borderPx = 8,
                    ),
                )
                val borderBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                try {
                    borderless.setBounds(0, 0, borderBitmap.width, borderBitmap.height)
                    borderless.draw(Canvas(borderBitmap))
                    check(borderBitmap.getPixel(1, 16) == Color.WHITE) {
                        "An omitted candidate border rendered as a transparent stroke"
                    }
                } finally {
                    borderBitmap.recycle()
                }
                view.refreshColors()
                layout(view)
                val labels = descendants(view).filterIsInstance<TextView>()
                check(labels.map { it.text.toString() } == before) { "Recolor changed composition or choices" }
                check(view.measuredHeight == height) { "Recolor changed keyboard geometry" }
                check(labels.first { it.text.toString() == "ni" }.currentTextColor == Color.rgb(0x12, 0x34, 0x56))
                check(labels.first { it.text.toString() == "426" }.currentTextColor == Color.rgb(0xfe, 0xfd, 0xfc))
                check(labels.all { it.currentTextColor == scope.colors.candidateTextColor || it.currentTextColor == scope.colors.hilitedCandidateTextColor })
                check(descendants(view).filterIsInstance<ImageButton>().all { it.imageTintList?.defaultColor == scope.colors.candidateTextColor })
                check(actions.isEmpty()) { "Scheme refresh sent an input action" }
                labels.first { it.text.toString() == "ni" }.performClick()
                check(actions == listOf(state.revision to T9Action.Focus)) { "Recolor lost the composition revision" }
            }
        } finally {
            ColorManager.setColorSchemeForTesting(original)
            com.osfans.trime.data.theme.ThemeManager.prefs.normalModeColor.setValue(selection)
        }
    }

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
        }
    }
}
