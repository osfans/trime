package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.View.OnClickListener
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeNotification
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.styledFloat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    val service: Trime,
    val rime: Rime,
    val theme: Theme,
) : ConstraintLayout(service) {
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            setOnClickListener { placeholderListener }
        }

    private val rightPaddingSpace =
        view(::View) {
            setOnClickListener { placeholderListener }
        }

    private val bottomPaddingSpace =
        view(::View) {
            setOnClickListener { placeholderListener }
        }

    private val notificationHandlerJob: Job

    private val themedContext = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)
    val quickBar = QuickBar()
    val keyboardWindow = KeyboardWindow()
    val liquidKeyboard = LiquidKeyboard()

    private val module =
        module {
            single { this@InputView }
            single { theme }
            single { themedContext }
            single { service }
            single { keyboardWindow }
            single { liquidKeyboard }
            single { quickBar }
        }

    private val keyboardSidePadding = theme.style.getInt("keyboard_padding")
    private val keyboardSidePaddingLandscape = theme.style.getInt("keyboard_padding_land")
    private val keyboardBottomPadding = theme.style.getInt("keyboard_padding_bottom")
    private val keyboardBottomPaddingLandscape = theme.style.getInt("keyboard_padding_land_bottom")

    private val keyboardSidePaddingPx: Int
        get() {
            val value =
                when (resources.configuration.orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
                    else -> keyboardSidePadding
                }
            return dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value =
                when (resources.configuration.orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                    else -> keyboardBottomPadding
                }
            return dp(value)
        }

    val keyboardView: View

    init {
        // MUST call before any other operations
        loadKoinModules(module)

        notificationHandlerJob =
            service.lifecycleScope.launch {
                rime.notificationFlow.collect {
                    handleRimeNotification(it)
                }
            }

        service.window.window!!.also { it ->
            // allow draw behind navigation bar
            WindowCompat.setDecorFitsSystemWindows(it, false)
            it.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // don't apply scrim to transparent navigation bar
                it.isNavigationBarContrastEnforced = false
            }
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                    bottomPaddingSpace.updateLayoutParams<LayoutParams> {
                        bottomMargin = it.bottom
                    }
                }
                WindowInsetsCompat.CONSUMED
            }
        }

        liquidKeyboard.setKeyboardView(keyboardWindow.oldSymbolInputView.liquidKeyboardView)

        keyboardView =
            constraintLayout {
                isMotionEventSplittingEnabled = true
                background = theme.colors.getDrawable("root_background")
                add(
                    quickBar.view,
                    lParams(matchParent, wrapContent) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(quickBar.view)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(quickBar.view)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    keyboardWindow.view,
                    lParams(matchParent, wrapContent) {
                        below(quickBar.view)
                        above(bottomPaddingSpace)
                    },
                )
                add(
                    bottomPaddingSpace,
                    lParams {
                        startToEndOf(leftPaddingSpace)
                        endToStartOf(rightPaddingSpace)
                        bottomOfParent()
                    },
                )
            }

        updateKeyboardSize()

        add(
            keyboardView,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            },
        )
    }

    private fun updateKeyboardSize() {
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
        val unset = LayoutParams.UNSET
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = View.GONE
            rightPaddingSpace.visibility = View.GONE
            keyboardWindow.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            keyboardWindow.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        quickBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        if (!restarting) {
            service.window.window!!.also {
                WindowCompat.getInsetsController(it, it.decorView)
                    .isAppearanceLightNavigationBars =
                    ColorUtils.isDark(theme.colors.getColor("key_text_color")!!)
            }
        }
        keyboardWindow.oldMainInputView.mainKeyboardView.updateEnterLabelOnEditorInfo(info)
    }

    private fun handleRimeNotification(it: RimeNotification) {
        when (it) {
            is RimeNotification.OptionNotification -> {
                when (it.option) {
                    "_hide_comment" -> {
                        quickBar.oldCandidateBar.candidates.setShowComment(!it.value)
                    }
                    "_hide_bar",
                    "_hide_candidate",
                    -> {
                        quickBar.view.visibility =
                            if (it.value) View.GONE else View.VISIBLE
                    }
                    "_hide_key_hint" -> keyboardWindow.oldMainInputView.mainKeyboardView.setShowHint(!it.value)
                    "_hide_key_symbol" -> keyboardWindow.oldMainInputView.mainKeyboardView.setShowSymbol(!it.value)
                }
                keyboardWindow.oldMainInputView.mainKeyboardView.invalidateAllKeys()
            }
            else -> {}
        }
    }

    fun switchUiByState(state: KeyboardWindow.State) {
        keyboardWindow.switchUiByState(state)
        quickBar.switchUiByState(QuickBar.State.entries[state.ordinal])
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
        keyboardWindow.oldMainInputView.mainKeyboardView.finishInput()
    }

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        showingDialog?.dismiss()
        // unload the Koin module,
        // implies that InputView should not be attached again after detached.
        unloadKoinModules(module)
        super.onDetachedFromWindow()
    }
}
