package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.View
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.util.styledFloat
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    val service: Trime,
) : ConstraintLayout(service) {
    private val themedContext = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)
    val quickBar = QuickBar()
    val keyboardWindow = KeyboardWindow()

    private val module =
        module {
            single { this@InputView }
            single { themedContext }
            single { service }
            single { keyboardWindow }
            single { quickBar }
        }

    val keyboardView: View

    init {
        startKoin {
            androidLogger()
            androidContext(context)
            modules(module)
        }

        keyboardView =
            constraintLayout {
                isMotionEventSplittingEnabled = true
                add(
                    quickBar.view,
                    lParams(matchParent, wrapContent) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    keyboardWindow.view,
                    lParams(matchParent, wrapContent) {
                        below(quickBar.view)
                        centerHorizontally()
                        bottomOfParent()
                    },
                )
            }

        add(
            keyboardView,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            },
        )
    }

    fun switchUiByIndex(index: Int) {
        keyboardWindow.switchUiByIndex(index)
        quickBar.switchUiByIndex(index)
    }

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        showingDialog?.dismiss()
        val windowToken = windowToken
        check(windowToken != null) { "InputView Token is null." }
        val window = dialog.window!!
        window.attributes.apply {
            token = windowToken
            type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        )
        window.setDimAmount(themedContext.styledFloat(android.R.attr.backgroundDimAmount))
        showingDialog =
            dialog.apply {
                setOnDismissListener { this@InputView.showingDialog = null }
                show()
            }
    }

    fun finishInput() {
        showingDialog?.dismiss()
    }
}
