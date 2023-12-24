/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.ime.core

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import android.inputmethodservice.InputMethodService
import android.os.*
import android.os.Build.VERSION_CODES
import android.text.InputType
import android.text.TextUtils
import android.util.Size
import android.view.*
import android.view.inputmethod.*
import android.widget.ImageView.ScaleType
import android.widget.PopupWindow
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.blankj.utilcode.util.BarUtils
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.core.Rime.Companion.compositionText
import com.osfans.trime.core.Rime.Companion.getOption
import com.osfans.trime.core.Rime.Companion.isAsciiMode
import com.osfans.trime.core.Rime.Companion.isAsciiPunch
import com.osfans.trime.core.Rime.Companion.isEmpty
import com.osfans.trime.core.Rime.Companion.isVoidKeycode
import com.osfans.trime.core.Rime.Companion.processKey
import com.osfans.trime.core.Rime.Companion.setCaretPos
import com.osfans.trime.core.Rime.Companion.setOption
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.AppPrefs.Companion.defaultInstance
import com.osfans.trime.data.db.DraftHelper.onInputEventChanged
import com.osfans.trime.data.sound.SoundThemeManager.switchSound
import com.osfans.trime.data.theme.Theme.Companion.get
import com.osfans.trime.databinding.CompositionRootBinding
import com.osfans.trime.databinding.InputRootBinding
import com.osfans.trime.ime.broadcast.IntentReceiver
import com.osfans.trime.ime.enums.Keycode.Companion.isStdKey
import com.osfans.trime.ime.enums.PopupPosition
import com.osfans.trime.ime.enums.PopupPosition.Companion.fromString
import com.osfans.trime.ime.enums.SymbolKeyboardType
import com.osfans.trime.ime.keyboard.*
import com.osfans.trime.ime.keyboard.KeyboardSwitcher.currentKeyboard
import com.osfans.trime.ime.keyboard.KeyboardSwitcher.newOrReset
import com.osfans.trime.ime.landscapeinput.LandscapeInputUIMode
import com.osfans.trime.ime.lifecycle.LifecycleInputMethodService
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.ime.symbol.TabManager
import com.osfans.trime.ime.symbol.TabView
import com.osfans.trime.ime.text.Candidate
import com.osfans.trime.ime.text.Composition
import com.osfans.trime.ime.text.ScrollView
import com.osfans.trime.ime.text.TextInputManager
import com.osfans.trime.ime.util.UiUtil.isDarkMode
import com.osfans.trime.util.ShortcutUtils.openCategory
import com.osfans.trime.util.ShortcutUtils.pasteFromClipboard
import com.osfans.trime.util.ShortcutUtils.syncInBackground
import com.osfans.trime.util.StringUtils.findSectionAfter
import com.osfans.trime.util.StringUtils.findSectionBefore
import com.osfans.trime.util.ViewUtils.updateLayoutGravityOf
import com.osfans.trime.util.ViewUtils.updateLayoutHeightOf
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.dp2px
import splitties.bitflags.hasFlag
import splitties.systemservices.inputMethodManager
import timber.log.Timber
import java.util.*
import kotlin.math.max
import kotlin.math.min


/** [輸入法][InputMethodService]主程序  */
open class Trime : LifecycleInputMethodService() {
    private var liquidKeyboard: LiquidKeyboard? = null
    private var normalTextEditor = false

    private val prefs: AppPrefs
        get() = defaultInstance()

    private var darkMode = false // 当前键盘主题是否处于暗黑模式
    private var mainKeyboardView: KeyboardView? = null // 主軟鍵盤

    private var mCandidate: Candidate? = null // 候選
    private var mComposition: Composition? = null // 編碼
    private var compositionRootBinding: CompositionRootBinding? = null
    private var mCandidateRoot: ScrollView? = null
    private var mTabRoot: ScrollView? = null
    private var tabView: TabView? = null
    private var inputRootBinding: InputRootBinding? = null
    private var eventListeners: WeakHashSet<EventListener> = WeakHashSet()
    var inputFeedbackManager: InputFeedbackManager? = null // 效果管理器
    private var mIntentReceiver: IntentReceiver? = null

    private var editorInfo: EditorInfo? = null

    private var isWindowShown = false // 键盘窗口是否已显示

    private var isAutoCaps = false // 句首自動大寫

    var activeEditorInstance: EditorInstance? = null
    var textInputManager: TextInputManager? = null // 文字输入管理器

    private var isPopupWindowEnabled = true // 顯示懸浮窗口
    private var isPopupWindowMovable: String? = null // 悬浮窗口是否可移動
    private var popupWindowX = 0
    private var popupWindowY = 0 // 悬浮床移动座標
    private var popupMargin = 0 // 候選窗與邊緣空隙
    private var popupMarginH = 0 // 悬浮窗与屏幕两侧的间距
    private var isCursorUpdated = false // 光標是否移動
    private var minPopupSize = 0 // 上悬浮窗的候选词的最小词长
    private var minPopupCheckSize = 0 // 第一屏候选词数量少于设定值，则候选词上悬浮窗。（也就是说，第一屏存在长词）此选项大于1时，min_length等参数失效
    private var popupWindowPos: PopupPosition? = null // 悬浮窗口彈出位置
    private var mPopupWindow: PopupWindow? = null
    private val mPopupRectF = RectF()
    private val mPopupHandler = Handler(Looper.getMainLooper())
    private val mPopupTimer: Runnable = object : Runnable {
        override fun run() {
            if (mCandidateRoot == null || mCandidateRoot!!.windowToken == null) return
            if (!isPopupWindowEnabled) return
            var x = 0
            var y = 0
            val candidateLocation = IntArray(2)
            mCandidateRoot!!.getLocationOnScreen(candidateLocation)
            val minX = popupMarginH
            val minY = popupMargin
            val maxX = mCandidateRoot!!.width - mPopupWindow!!.width - minX
            val maxY = candidateLocation[1] - mPopupWindow!!.height - minY
            if (this@Trime.isWinFixed || !isCursorUpdated) {
                // setCandidatesViewShown(true);
                when (popupWindowPos) {
                    PopupPosition.TOP_RIGHT -> {
                        x = maxX
                        y = minY
                    }

                    PopupPosition.TOP_LEFT -> {
                        x = minX
                        y = minY
                    }

                    PopupPosition.BOTTOM_RIGHT -> {
                        x = maxX
                        y = maxY
                    }

                    PopupPosition.DRAG -> {
                        x = popupWindowX
                        y = popupWindowY
                    }

                    PopupPosition.FIXED, PopupPosition.BOTTOM_LEFT -> {
                        x = minX
                        y = maxY
                    }

                    else -> {
                        x = minX
                        y = maxY
                    }
                }
            } else {
                // setCandidatesViewShown(false);
                when (popupWindowPos) {
                    PopupPosition.LEFT, PopupPosition.LEFT_UP -> x = mPopupRectF.left.toInt()
                    PopupPosition.RIGHT, PopupPosition.RIGHT_UP -> x = mPopupRectF.right.toInt()
                    else -> Timber.wtf("UNREACHABLE BRANCH")
                }
                x = min(maxX.toDouble(), x.toDouble()).toInt()
                x = max(minX.toDouble(), x.toDouble()).toInt()
                when (popupWindowPos) {
                    PopupPosition.LEFT, PopupPosition.RIGHT -> y = mPopupRectF.bottom.toInt() + popupMargin
                    PopupPosition.LEFT_UP, PopupPosition.RIGHT_UP -> y =
                        mPopupRectF.top.toInt() - mPopupWindow!!.height - popupMargin

                    else -> Timber.wtf("UNREACHABLE BRANCH")
                }
                y = min(maxY.toDouble(), y.toDouble()).toInt()
                y = max(minY.toDouble(), y.toDouble()).toInt()
            }
            y -= BarUtils.getStatusBarHeight() // 不包含狀態欄

            if (!mPopupWindow!!.isShowing) {
                mPopupWindow!!.showAtLocation(mCandidateRoot, Gravity.START or Gravity.TOP, x, y)
            } else {
                mPopupWindow!!.update(x, y, mPopupWindow!!.width, mPopupWindow!!.height)
            }
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (isWindowShown) {
            Timber.i("Ignoring (is already shown)")
            return
        } else {
            Timber.i("onWindowShown...")
        }
        isWindowShown = true

        updateComposing()

        for (listener in eventListeners) {
            listener.onWindowShown()
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (!isWindowShown) {
            Timber.d("Ignoring (window is already hidden)")
            return
        } else {
            Timber.d("onWindowHidden")
        }
        isWindowShown = false

        if (prefs.profile.syncBackgroundEnabled) {
            val msg = Message()
            msg.obj = this
            syncBackgroundHandler.sendMessageDelayed(msg, 5000) // 输入面板隐藏5秒后，开始后台同步
        }

        for (listener in eventListeners) {
            listener.onWindowHidden()
        }
    }

    private val isWinFixed: Boolean
        get() = (Build.VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP
                || (popupWindowPos != PopupPosition.LEFT && popupWindowPos != PopupPosition.RIGHT && popupWindowPos != PopupPosition.LEFT_UP && popupWindowPos != PopupPosition.RIGHT_UP))

    fun updatePopupWindow(offsetX: Int, offsetY: Int) {
        Timber.d("updatePopupWindow: winX = %s, winY = %s", offsetX, offsetY)
        popupWindowPos = PopupPosition.DRAG
        popupWindowX = offsetX
        popupWindowY = offsetY
        mPopupWindow!!.update(popupWindowX, popupWindowY, -1, -1, true)
    }

    fun loadConfig() {
        val theme = get()
        popupWindowPos = fromString(theme.style.getString("layout/position"))
        isPopupWindowMovable = theme.style.getString("layout/movable")
        popupMargin = dp2px(theme.style.getFloat("layout/spacing")).toInt()
        minPopupSize = theme.style.getInt("layout/min_length")
        minPopupCheckSize = theme.style.getInt("layout/min_check")
        popupMarginH = dp2px(theme.style.getFloat("layout/real_margin")).toInt()
        textInputManager!!.shouldResetAsciiMode = theme.style.getBoolean("reset_ascii_mode")
        isAutoCaps = theme.style.getBoolean("auto_caps")
        isPopupWindowEnabled =
            prefs.keyboard.popupWindowEnabled && theme.style.getObject("window") != null
        textInputManager!!.shouldUpdateRimeOption = true
    }

    private fun updateRimeOption(): Boolean {
        try {
            if (textInputManager!!.shouldUpdateRimeOption) {
                setOption("soft_cursor", prefs.keyboard.softCursorEnabled) // 軟光標
                setOption("_horizontal", get().style.getBoolean("horizontal")) // 水平模式
                textInputManager!!.shouldUpdateRimeOption = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun restartSystemStartTimingSync() { // 防止重启系统 强行停止应用时alarm任务丢失
        if (prefs.profile.timingSyncEnabled) {
            val triggerTime = prefs.profile.timingSyncTriggerTime
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val pendingIntent =
                PendingIntent.getBroadcast( // 设置待发送的同步事件
                    this,
                    0,
                    Intent("com.osfans.trime.timing.sync"),
                    if (Build.VERSION.SDK_INT >= VERSION_CODES.M
                    ) (PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    else PendingIntent.FLAG_UPDATE_CURRENT
                )
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) { // 根据SDK设置alarm任务
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

    override fun onCreate() {
        // MUST WRAP all code within Service onCreate() in try..catch to prevent any crash loops
        try {
            // Additional try..catch wrapper as the event listeners chain or the super.onCreate() method
            // could crash
            //  and lead to a crash loop
            try {
                Timber.d("onCreate")
                textInputManager = TextInputManager.getInstance(isDarkMode(this))
                activeEditorInstance = EditorInstance(this)
                inputFeedbackManager = InputFeedbackManager(this)
                liquidKeyboard = LiquidKeyboard(this)
                restartSystemStartTimingSync()
            } catch (e: Exception) {
                Timber.e(e)
                super.onCreate()
                return
            }
            super.onCreate()
            for (listener in eventListeners) {
                listener.onCreate()
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    /**
     * 变更配置的暗黑模式开关
     *
     * @param darkMode 设置为暗黑模式
     * @return 模式实际上是否有发生变更
     */
    fun setDarkMode(darkMode: Boolean): Boolean {
        if (darkMode != this.darkMode) {
            Timber.d("Dark mode changed: %s", darkMode)
            this.darkMode = darkMode
            return true
        }
        Timber.d("Dark mode not changed: %s", darkMode)
        return false
    }

    private var symbolKeyboardType = SymbolKeyboardType.NO_KEY

    fun inputSymbol(text: String) {
        textInputManager!!.onPress(KeyEvent.KEYCODE_UNKNOWN)
        if (isAsciiMode) setOption("ascii_mode", false)
        val asciiPunch = isAsciiPunch
        if (asciiPunch) setOption("ascii_punct", false)
        textInputManager!!.onText("{Escape}$text")
        if (asciiPunch) setOption("ascii_punct", true)
        selectLiquidKeyboard(-1)
    }

    fun selectLiquidKeyboard(tabIndex: Int) {
        if (inputRootBinding == null) return
        val symbolInput: View = inputRootBinding!!.symbol.symbolInput
        val mainInput: View = inputRootBinding!!.main.mainInput
        if (tabIndex >= 0) {
            symbolInput.layoutParams.height = mainInput.height
            symbolInput.visibility = View.VISIBLE

            symbolKeyboardType = liquidKeyboard!!.select(tabIndex)
            tabView!!.updateTabWidth()
            if (inputRootBinding != null) {
                mTabRoot!!.background = mCandidateRoot!!.background
                mTabRoot!!.move(tabView!!.hightlightLeft, tabView!!.hightlightRight)
            }
        } else {
            symbolKeyboardType = SymbolKeyboardType.NO_KEY
            // 设置液体键盘处于隐藏状态
            TabManager.get().setTabExited()
            symbolInput.visibility = View.GONE
        }
        updateComposing()
        mainInput.visibility = if (tabIndex >= 0) View.GONE else View.VISIBLE
    }

    // 按键需要通过tab name来打开liquidKeyboard的指定tab
    fun selectLiquidKeyboard(name: String) {
        if (name.matches("-?\\d+".toRegex())) selectLiquidKeyboard(name.toInt())
        else if (name.matches("[A-Z]+".toRegex())) selectLiquidKeyboard(SymbolKeyboardType.valueOf(name))
        else selectLiquidKeyboard(TabManager.getTagIndex(name))
    }

    fun selectLiquidKeyboard(type: SymbolKeyboardType?) {
        selectLiquidKeyboard(TabManager.getTagIndex(type))
    }

    fun pasteByChar() {
        commitTextByChar(Objects.requireNonNull(pasteFromClipboard(this)).toString())
    }

    fun invalidate() {
        Rime.getInstance(false)
        get().destroy()
        reset()
        textInputManager!!.shouldUpdateRimeOption = true
    }

    private fun hideCompositionView() {
        if (isPopupWindowMovable != null && isPopupWindowMovable == "once") {
            popupWindowPos = fromString(get().style.getString("layout/position"))
        }

        if (mPopupWindow != null && mPopupWindow!!.isShowing) {
            mPopupWindow!!.dismiss()
            mPopupHandler.removeCallbacks(mPopupTimer)
        }
    }

    private fun showCompositionView(isCandidate: Boolean) {
        if (TextUtils.isEmpty(compositionText) && isCandidate) {
            hideCompositionView()
            return
        }
        compositionRootBinding!!.compositionRoot.measure(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mPopupWindow!!.width = compositionRootBinding!!.compositionRoot.measuredWidth
        mPopupWindow!!.height = compositionRootBinding!!.compositionRoot.measuredHeight
        mPopupHandler.post(mPopupTimer)
    }

    fun loadBackground() {
        val theme = get()
        val orientation = resources.configuration.orientation

        if (mPopupWindow != null) {
            val textBackground =
                theme.colors.getDrawable(
                    "text_back_color",
                    "layout/border",
                    "border_color",
                    "layout/round_corner",
                    "layout/alpha"
                )
            if (textBackground != null) mPopupWindow!!.setBackgroundDrawable(textBackground)
            mPopupWindow!!.elevation = dp2px(theme.style.getFloat("layout/elevation")).toInt().toFloat()
        }

        if (mCandidateRoot != null) {
            val candidateBackground =
                theme.colors.getDrawable(
                    "candidate_background",
                    "candidate_border",
                    "candidate_border_color",
                    "candidate_border_round",
                    null
                )
            if (candidateBackground != null) mCandidateRoot!!.background = candidateBackground
        }

        if (inputRootBinding == null) return

        // 单手键盘模式
        val oneHandMode = 0
        val padding =
            theme.getKeyboardPadding(oneHandMode, orientation == Configuration.ORIENTATION_LANDSCAPE)
        Timber.i(
            "update KeyboardPadding: Trime.loadBackground, padding= %s %s %s, orientation=%s",
            padding[0], padding[1], padding[2], orientation
        )
        mainKeyboardView!!.setPadding(padding[0], 0, padding[1], padding[2])

        val inputRootBackground = theme.colors.getDrawable("root_background")
        if (inputRootBackground != null) {
            inputRootBinding!!.inputRoot.background = inputRootBackground
        } else {
            // 避免因为键盘整体透明而造成的异常
            inputRootBinding!!.inputRoot.setBackgroundColor(Color.BLACK)
        }

        tabView!!.reset()
    }

    fun resetKeyboard() {
        if (mainKeyboardView != null) {
            mainKeyboardView!!.setShowHint(!getOption("_hide_key_hint"))
            mainKeyboardView!!.setShowSymbol(!getOption("_hide_key_symbol"))
            mainKeyboardView!!.reset() // 實體鍵盤無軟鍵盤
        }
    }

    fun resetCandidate() {
        if (mCandidateRoot != null) {
            loadBackground()
            setShowComment(!getOption("_hide_comment"))
            mCandidateRoot!!.visibility =
                if (!getOption("_hide_candidate")) View.VISIBLE else View.GONE
            mCandidate!!.reset()
            isPopupWindowEnabled = (
                    prefs.keyboard.popupWindowEnabled
                            && get().style.getObject("window") != null)
            mComposition!!.visibility = if (isPopupWindowEnabled) View.VISIBLE else View.GONE
            mComposition!!.reset()
        }
    }

    /** 重置鍵盤、候選條、狀態欄等 !!注意，如果其中調用Rime.setOption，切換方案會卡住  */
    private fun reset() {
        if (inputRootBinding == null) return
        inputRootBinding!!.symbol.symbolInput.visibility = View.GONE
        inputRootBinding!!.main.mainInput.visibility = View.VISIBLE
        loadConfig()
        updateDarkMode()
        val theme = get(darkMode)
        theme.initCurrentColors(darkMode)
        switchSound(theme.colors.getString("sound"))
        newOrReset()
        resetCandidate()
        hideCompositionView()
        resetKeyboard()
    }

    /** Must be called on the UI thread  */
    fun initKeyboard() {
        reset()
        // setNavBarColor();
        textInputManager!!.shouldUpdateRimeOption = true // 不能在Rime.onMessage中調用set_option，會卡死
        bindKeyboardToInputView()
        // loadBackground(); // reset()调用过resetCandidate()，resetCandidate()一键调用过loadBackground();
        updateComposing() // 切換主題時刷新候選
    }

    fun initKeyboardDarkMode(darkMode: Boolean) {
        val theme = get()
        if (theme.hasDarkLight) {
            loadConfig()
            theme.initCurrentColors(darkMode)
            switchSound(theme.colors.getString("sound"))
            newOrReset()
            resetCandidate()
            hideCompositionView()
            resetKeyboard()

            // setNavBarColor();
            textInputManager!!.shouldUpdateRimeOption = true // 不能在Rime.onMessage中調用set_option，會卡死
            bindKeyboardToInputView()
            // loadBackground(); // reset()调用过resetCandidate()，resetCandidate()一键调用过loadBackground();
            updateComposing() // 切換主題時刷新候選
        }
    }

    override fun onDestroy() {
        if (mIntentReceiver != null) mIntentReceiver!!.unregisterReceiver(this)
        mIntentReceiver = null
        if (inputFeedbackManager != null) inputFeedbackManager!!.destroy()
        inputFeedbackManager = null
        inputRootBinding = null

        for (listener in eventListeners) {
            listener.onDestroy()
        }
        eventListeners.clear()
        super.onDestroy()

        check(instance !== null) { "instance must be null" }
        check(instance === this) { "instance must be this" }
        instance = null
    }

    private fun handleReturnKey() {
        if (editorInfo == null) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            return
        }
        if ((editorInfo!!.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            return
        }
        if (editorInfo!!.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            val ic = currentInputConnection
            ic?.commitText("\n", 1)
            return
        }
        if (!TextUtils.isEmpty(editorInfo!!.actionLabel)
            && editorInfo!!.actionId != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            val ic = currentInputConnection
            ic?.performEditorAction(editorInfo!!.actionId)
            return
        }
        val action = editorInfo!!.imeOptions and EditorInfo.IME_MASK_ACTION
        val ic = currentInputConnection
        when (action) {
            EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE -> ic?.commitText("\n", 1)
            else -> ic?.performEditorAction(action)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val config = resources.configuration
        if (config != null) {
            if (config.orientation != newConfig.orientation) {
                // Clear composing text and candidates for orientation change.
                performEscape()
                config.orientation = newConfig.orientation
            }
        }
        super.onConfigurationChanged(newConfig)
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        if (!isWinFixed) {
            val composingText = cursorAnchorInfo.composingText
            // update mPopupRectF
            if (composingText == null) {
                // composing is disabled in target app or trime settings
                // use the position of the insertion marker instead
                mPopupRectF.top = cursorAnchorInfo.insertionMarkerTop
                mPopupRectF.left = cursorAnchorInfo.insertionMarkerHorizontal
                mPopupRectF.bottom = cursorAnchorInfo.insertionMarkerBottom
                mPopupRectF.right = mPopupRectF.left
            } else {
                val startPos = cursorAnchorInfo.composingTextStart
                val endPos = startPos + composingText.length - 1
                val startCharRectF = cursorAnchorInfo.getCharacterBounds(startPos)
                val endCharRectF = cursorAnchorInfo.getCharacterBounds(endPos)
                if (startCharRectF == null || endCharRectF == null) {
                    // composing text has been changed, the next onUpdateCursorAnchorInfo is on the road
                    // ignore this outdated update
                    return
                }
                // for different writing system (e.g. right to left languages),
                // we have to calculate the correct RectF
                mPopupRectF.top = min(startCharRectF.top.toDouble(), endCharRectF.top.toDouble()).toFloat()
                mPopupRectF.left = min(startCharRectF.left.toDouble(), endCharRectF.left.toDouble()).toFloat()
                mPopupRectF.bottom = max(startCharRectF.bottom.toDouble(), endCharRectF.bottom.toDouble()).toFloat()
                mPopupRectF.right = max(startCharRectF.right.toDouble(), endCharRectF.right.toDouble()).toFloat()
            }
            cursorAnchorInfo.matrix.mapRect(mPopupRectF)
        }
        if (mCandidateRoot != null) {
            showCompositionView(true)
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if ((candidatesEnd != -1) && ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd))) {
            // 移動光標時，更新候選區
            if ((newSelEnd < candidatesEnd) && (newSelEnd >= candidatesStart)) {
                val n = newSelEnd - candidatesStart
                setCaretPos(n)
                updateComposing()
            }
        }
        if ((candidatesStart == -1 && candidatesEnd == -1) && (newSelStart == 0 && newSelEnd == 0)) {
            // 上屏後，清除候選區
            performEscape()
        }
        // Update the caps-lock status for the current cursor position.
        dispatchCapsStateToInputView()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.contentTopInsets = outInsets.visibleTopInsets
    }

    override fun onCreateInputView(): View {
        Timber.e("onCreateInputView()")
        // 初始化键盘布局
        super.onCreateInputView()
        updateDarkMode()
        get(darkMode).initCurrentColors(darkMode)

        inputRootBinding = InputRootBinding.inflate(LayoutInflater.from(this))
        mainKeyboardView = inputRootBinding!!.main.mainKeyboardView

        // 初始化候选栏
        mCandidateRoot = inputRootBinding!!.main.candidateView.candidateRoot
        mCandidate = inputRootBinding!!.main.candidateView.candidates

        // 候选词悬浮窗的容器
        compositionRootBinding = CompositionRootBinding.inflate(LayoutInflater.from(this))
        mComposition = compositionRootBinding!!.compositions
        mPopupWindow = PopupWindow(compositionRootBinding!!.compositionRoot)
        mPopupWindow!!.isClippingEnabled = false
        mPopupWindow!!.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            mPopupWindow!!.windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        }
        hideCompositionView()
        mTabRoot = inputRootBinding!!.symbol.tabView.tabRoot

        liquidKeyboard!!.setKeyboardView(inputRootBinding!!.symbol.liquidKeyboardView)
        tabView = inputRootBinding!!.symbol.tabView.tabs

        for (listener in eventListeners) {
            assert(inputRootBinding != null)
            listener.onInitializeInputUi(inputRootBinding!!)
        }
        loadBackground()

        newOrReset()
        Timber.i("onCreateInputView() finish")

        return inputRootBinding!!.inputRoot
    }

    fun setShowComment(show_comment: Boolean) {
        if (mCandidateRoot != null) mCandidate!!.setShowComment(show_comment)
        mComposition!!.setShowComment(show_comment)
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        editorInfo = attribute
        Timber.d("onStartInput: restarting=%s", restarting)
    }

    private fun updateDarkMode(): Boolean {
        val isDarkMode = isDarkMode(this)

        return setDarkMode(isDarkMode)
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        Timber.d("onStartInputView: restarting=%s", restarting)
        editorInfo = attribute

        if (updateDarkMode()) {
            initKeyboardDarkMode(darkMode)
        }

        inputFeedbackManager!!.resumeSoundPool()
        inputFeedbackManager!!.resetPlayProgress()
        for (listener in eventListeners) {
            listener.onStartInputView(activeEditorInstance!!, restarting)
        }
        if (prefs.other.showStatusBarIcon) {
            showStatusIcon(R.drawable.ic_trime_status) // 狀態欄圖標
        }
        bindKeyboardToInputView()
        // if (!restarting) setNavBarColor();
        setCandidatesViewShown(!isEmpty) // 軟鍵盤出現時顯示候選欄

        if ((attribute.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION)
            == EditorInfo.IME_FLAG_NO_ENTER_ACTION
        ) {
            mainKeyboardView!!.resetEnterLabel()
        } else {
            mainKeyboardView!!.setEnterLabel(
                attribute.imeOptions and EditorInfo.IME_MASK_ACTION, attribute.actionLabel
            )
        }

        when (attribute.inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> {
                Timber.i(
                    "EditorInfo: private;"
                            + " packageName="
                            + attribute.packageName
                            + "; fieldName="
                            + attribute.fieldName
                            + "; actionLabel="
                            + attribute.actionLabel
                            + "; inputType="
                            + attribute.inputType
                            + "; VARIATION="
                            + (attribute.inputType and InputType.TYPE_MASK_VARIATION)
                            + "; CLASS="
                            + (attribute.inputType and InputType.TYPE_MASK_CLASS)
                            + "; ACTION="
                            + (attribute.imeOptions and EditorInfo.IME_MASK_ACTION)
                )
                normalTextEditor = false
            }

            else -> {
                Timber.i(
                    "EditorInfo: normal;"
                            + " packageName="
                            + attribute.packageName
                            + "; fieldName="
                            + attribute.fieldName
                            + "; actionLabel="
                            + attribute.actionLabel
                            + "; inputType="
                            + attribute.inputType
                            + "; VARIATION="
                            + (attribute.inputType and InputType.TYPE_MASK_VARIATION)
                            + "; CLASS="
                            + (attribute.inputType and InputType.TYPE_MASK_CLASS)
                            + "; ACTION="
                            + (attribute.imeOptions and EditorInfo.IME_MASK_ACTION)
                )

                if ((attribute.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
                    == EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                ) {
                    //  应用程求以隐身模式打开键盘应用程序
                    normalTextEditor = false
                    Timber.d("EditorInfo: normal -> private, IME_FLAG_NO_PERSONALIZED_LEARNING")
                } else if (attribute.packageName == BuildConfig.APPLICATION_ID || prefs.clipboard.draftExcludeApp.contains(
                        attribute.packageName
                    )
                ) {
                    normalTextEditor = false
                    Timber.d("EditorInfo: normal -> exclude, packageName=%s", attribute.packageName)
                } else {
                    normalTextEditor = true
                    onInputEventChanged()
                }
            }
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (normalTextEditor) {
            onInputEventChanged()
        }
        super.onFinishInputView(finishingInput)
        // Dismiss any pop-ups when the input-view is being finished and hidden.
        mainKeyboardView!!.closing()
        performEscape()
        if (inputFeedbackManager != null) {
            inputFeedbackManager!!.releaseSoundPool()
        }
        try {
            hideCompositionView()
        } catch (e: Exception) {
            Timber.e(e, "Failed to show the PopupWindow.")
        }
    }

    override fun onFinishInput() {
        editorInfo = null
        super.onFinishInput()
    }

    fun bindKeyboardToInputView() {
        if (mainKeyboardView != null) {
            // Bind the selected keyboard to the input view.
            val sk = currentKeyboard
            mainKeyboardView!!.keyboard = sk
            dispatchCapsStateToInputView()
        }
    }

    /**
     * Dispatches cursor caps info to input view in order to implement auto caps lock at the start of
     * a sentence.
     */
    private fun dispatchCapsStateToInputView() {
        if ((isAutoCaps && isAsciiMode)
            && (mainKeyboardView != null && !mainKeyboardView!!.isCapsOn)
        ) {
            mainKeyboardView!!.setShifted(false, activeEditorInstance!!.cursorCapsMode != 0)
        }
    }

    private val isComposing: Boolean
        get() = Rime.isComposing

    /**
     * Commit the current composing text together with the new text
     *
     * @param text the new text to be committed
     */
    fun commitText(text: String?) {
        currentInputConnection.finishComposingText()
        activeEditorInstance!!.commitText(text!!, true)
    }

    fun commitTextByChar(text: String) {
        for (i in 0 until text.length) {
            if (!activeEditorInstance!!.commitText(text.substring(i, i + 1), false)) break
        }
    }

    /**
     * 如果爲[Back鍵][KeyEvent.KEYCODE_BACK]，則隱藏鍵盤
     *
     * @param keyCode [鍵碼][KeyEvent.getKeyCode]
     * @return 是否處理了Back鍵事件
     */
    private fun handleBack(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            requestHideSelf(0)
            return true
        }
        return false
    }

    private fun onRimeKey(event: IntArray): Boolean {
        updateRimeOption()
        // todo 改为异步处理按键事件、刷新UI
        val ret = processKey(event[0], event[1])
        activeEditorInstance!!.commitRimeText()
        return ret
    }

    private fun composeEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_MENU) return false // 不處理 Menu 鍵

        if (!isStdKey(keyCode)) return false // 只處理安卓標準按鍵

        if (event.repeatCount == 0 && Key.isTrimeModifierKey(keyCode)) {
            val ret =
                onRimeKey(
                    Event.getRimeEvent(
                        keyCode,
                        if (event.action == KeyEvent.ACTION_DOWN
                        ) event.modifiers
                        else Rime.META_RELEASE_ON
                    )
                )
            if (this.isComposing) setCandidatesViewShown(textInputManager!!.isComposable) // 藍牙鍵盤打字時顯示候選欄

            return ret
        }
        return textInputManager!!.isComposable && !isVoidKeycode(keyCode)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Timber.i("\t<TrimeInput>\tonKeyDown()\tkeycode=%d, event=%s", keyCode, event.toString())
        if (composeEvent(event) && onKeyEvent(event) && isWindowShown) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Timber.i("\t<TrimeInput>\tonKeyUp()\tkeycode=%d, event=%s", keyCode, event.toString())
        if (composeEvent(event) && textInputManager!!.needSendUpRimeKey) {
            textInputManager!!.onRelease(keyCode)
            if (isWindowShown) return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * 處理實體鍵盤事件
     *
     * @param event [按鍵事件][KeyEvent]
     * @return 是否成功處理
     */
    // KeyEvent 处理实体键盘事件
    private fun onKeyEvent(event: KeyEvent): Boolean {
        Timber.i("\t<TrimeInput>\tonKeyEvent()\tRealKeyboard event=%s", event.toString())
        var keyCode = event.keyCode
        textInputManager!!.needSendUpRimeKey = Rime.isComposing
        if (!this.isComposing) {
            if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK) {
                return false
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            keyCode = KeyEvent.KEYCODE_ESCAPE // 返回鍵清屏
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && event.repeatCount == 0 && !KeyEvent.isModifierKey(
                keyCode
            )
        ) {
            if (hookKeyboard(keyCode, event.metaState)) return true
        }

        val unicodeChar = event.unicodeChar
        val s = unicodeChar.toChar().toString()
        val i = Event.getClickCode(s)
        var mask = 0
        if (i > 0) {
            keyCode = i
        } else { // 空格、回車等
            mask = event.metaState
        }
        val ret = handleKey(keyCode, mask)
        if (this.isComposing) setCandidatesViewShown(textInputManager!!.isComposable) // 藍牙鍵盤打字時顯示候選欄

        return ret
    }

    fun switchToPrevIme() {
        try {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                val window = window.window
                if (window != null) {
                    @Suppress("DEPRECATION")
                    inputMethodManager
                        .switchToLastInputMethod(window.attributes.token)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to switch to the previous IME.")
            inputMethodManager.showInputMethodPicker()
        }
    }

    fun switchToNextIme() {
        try {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                val window = window.window
                if (window != null) {
                    @Suppress("DEPRECATION")
                    inputMethodManager
                        .switchToNextInputMethod(window.attributes.token, false)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to switch to the next IME.")
            inputMethodManager.showInputMethodPicker()
        }
    }

    // 处理键盘事件(Android keycode)
    fun handleKey(keyEventCode: Int, metaState: Int): Boolean { // 軟鍵盤
        textInputManager!!.needSendUpRimeKey = false
        if (onRimeKey(Event.getRimeEvent(keyEventCode, metaState))) {
            // 如果输入法消费了按键事件，则需要释放按键
            textInputManager!!.needSendUpRimeKey = true
            Timber.d(
                "\t<TrimeInput>\thandleKey()\trimeProcess, keycode=%d, metaState=%d",
                keyEventCode, metaState
            )
        } else if (hookKeyboard(keyEventCode, metaState)) {
            Timber.d("\t<TrimeInput>\thandleKey()\thookKeyboard, keycode=%d", keyEventCode)
        } else if (performEnter(keyEventCode) || handleBack(keyEventCode)) {
            // 处理返回键（隐藏软键盘）和回车键（换行）
            // todo 确认是否有必要单独处理回车键？是否需要把back和escape全部占用？
            Timber.d("\t<TrimeInput>\thandleKey()\tEnterOrHide, keycode=%d", keyEventCode)
        } else if (openCategory(keyEventCode)) {
            // 打开系统默认应用
            Timber.d("\t<TrimeInput>\thandleKey()\topenCategory keycode=%d", keyEventCode)
        } else {
            textInputManager!!.needSendUpRimeKey = true
            Timber.d(
                "\t<TrimeInput>\thandleKey()\treturn FALSE, keycode=%d, metaState=%d",
                keyEventCode, metaState
            )
            return false
        }
        return true
    }

    fun shareText(): Boolean {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            val ic = currentInputConnection ?: return false
            val cs = ic.getSelectedText(0)
            if (cs == null) ic.performContextMenuAction(android.R.id.selectAll)
            return ic.performContextMenuAction(android.R.id.shareText)
        }
        return false
    }

    private fun hookKeyboard(code: Int, mask: Int): Boolean { // 編輯操作
        val ic = currentInputConnection ?: return false
        if (mask == KeyEvent.META_CTRL_ON) {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
                if (prefs.keyboard.hookCtrlZY) {
                    when (code) {
                        KeyEvent.KEYCODE_Y -> return ic.performContextMenuAction(android.R.id.redo)
                        KeyEvent.KEYCODE_Z -> return ic.performContextMenuAction(android.R.id.undo)
                    }
                }
            }
            when (code) {
                KeyEvent.KEYCODE_A ->
                    if (prefs.keyboard.hookCtrlA)
                        return ic.performContextMenuAction(android.R.id.selectAll)

                KeyEvent.KEYCODE_X ->
                    if (prefs.keyboard.hookCtrlCV) {
                        val etr = ExtractedTextRequest()
                        etr.token = 0
                        val et = ic.getExtractedText(etr, 0)
                        if (et != null) {
                            if (et.selectionEnd - et.selectionStart > 0) return ic.performContextMenuAction(android.R.id.cut)
                        }
                    } else {
                        Timber.i("hookKeyboard cut fail")
                    }

                KeyEvent.KEYCODE_C ->
                    if (prefs.keyboard.hookCtrlCV) {
                        val etr = ExtractedTextRequest()
                        etr.token = 0
                        val et = ic.getExtractedText(etr, 0)
                        if (et != null) {
                            if (et.selectionEnd - et.selectionStart > 0) return ic.performContextMenuAction(android.R.id.copy)
                        }
                    } else {
                        Timber.i("hookKeyboard copy fail")
                    }


                KeyEvent.KEYCODE_V ->
                    if (prefs.keyboard.hookCtrlCV) {
                        val etr = ExtractedTextRequest()
                        etr.token = 0
                        val et = ic.getExtractedText(etr, 0)
                        if (et == null) {
                            Timber.d("hookKeyboard paste, et == null, try commitText")
                            if (ic.commitText(pasteFromClipboard(this), 1)) {
                                return true
                            }
                        } else if (ic.performContextMenuAction(android.R.id.paste)) {
                            return true
                        }
                        Timber.w("hookKeyboard paste fail")
                    }

                KeyEvent.KEYCODE_DPAD_RIGHT ->
                    if (prefs.keyboard.hookCtrlLR) {
                        val etr = ExtractedTextRequest()
                        etr.token = 0
                        val et = ic.getExtractedText(etr, 0)
                        if (et != null) {
                            val move_to = findSectionAfter(et.text, et.startOffset + et.selectionEnd)
                            ic.setSelection(move_to, move_to)
                            return true
                        }
                    }

                KeyEvent.KEYCODE_DPAD_LEFT ->
                    if (prefs.keyboard.hookCtrlLR) {
                        val etr = ExtractedTextRequest()
                        etr.token = 0
                        val et = ic.getExtractedText(etr, 0)
                        if (et != null) {
                            val move_to =
                                findSectionBefore(et.text, et.startOffset + et.selectionStart)
                            ic.setSelection(move_to, move_to)
                            return true
                        }
                    }
            }
        }
        return false
    }

    /** 更新Rime的中西文狀態、編輯區文本  */
    fun updateComposing(): Int {
        val ic = currentInputConnection
        activeEditorInstance!!.updateComposingText()
        if (ic != null && !isWinFixed) isCursorUpdated = ic.requestCursorUpdates(1)
        var startNum = 0
        if (mCandidateRoot != null) {
            if (isPopupWindowEnabled) {
                Timber.d("updateComposing() SymbolKeyboardType=%s", symbolKeyboardType.toString())
                if (symbolKeyboardType != SymbolKeyboardType.NO_KEY
                    && symbolKeyboardType != SymbolKeyboardType.CANDIDATE
                ) {
                    mComposition!!.setWindow()
                    showCompositionView(false)
                } else {
                    mComposition!!.visibility = View.VISIBLE
                    startNum = mComposition!!.setWindow(minPopupSize, minPopupCheckSize, Int.MAX_VALUE)
                    mCandidate!!.setText(startNum)
                    // if isCursorUpdated, showCompositionView will be called in onUpdateCursorAnchorInfo
                    // otherwise we need to call it here
                    if (!isCursorUpdated) showCompositionView(true)
                }
            } else {
                mCandidate!!.setText(0)
            }
            mCandidate!!.setExpectWidth(mainKeyboardView!!.width)
            // 刷新候选词后，如果候选词超出屏幕宽度，滚动候选栏
            mTabRoot!!.move(mCandidate!!.highlightLeft, mCandidate!!.highlightRight)
        }
        if (mainKeyboardView != null) mainKeyboardView!!.invalidateComposingKeys()
        if (!onEvaluateInputViewShown()) setCandidatesViewShown(textInputManager!!.isComposable) // 實體鍵盤打字時顯示候選欄


        return startNum
    }

    fun showDialogAboveInputView(dialog: Dialog) {
        val token = inputRootBinding!!.inputRoot.windowToken
        val window = dialog.window
        val lp = window!!.attributes
        lp.token = Objects.requireNonNull(token, "InputRoot token is null.")
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        window.attributes = lp
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog.show()
    }

    /**
     * 如果爲[回車鍵][KeyEvent.KEYCODE_ENTER]，則換行
     *
     * @param keyCode [鍵碼][KeyEvent.getKeyCode]
     * @return 是否處理了回車事件
     */
    private fun performEnter(keyCode: Int): Boolean { // 回車
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            onInputEventChanged()
            handleReturnKey()
            return true
        }
        return false
    }

    /** 模擬PC鍵盤中Esc鍵的功能：清除輸入的編碼和候選項  */
    fun performEscape() {
        if (this.isComposing) textInputManager!!.onKey(KeyEvent.KEYCODE_ESCAPE, 0)
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val config = resources.configuration
        if (config != null) {
            if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                return false
            } else {
                when (prefs.keyboard.fullscreenMode) {
                    LandscapeInputUIMode.AUTO_SHOW -> {
                        val ei = currentInputEditorInfo
                        if (ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0) {
                            return false
                        }
                        return true
                    }

                    LandscapeInputUIMode.ALWAYS_SHOW -> return true
                    LandscapeInputUIMode.NEVER_SHOW -> return false
                }
            }
        }
        return false
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        updateSoftInputWindowLayoutParameters()
    }

    /** Updates the layout params of the window and input view.  */
    private fun updateSoftInputWindowLayoutParameters() {
        val w = window.window ?: return
        val inputRoot = if (inputRootBinding != null) inputRootBinding!!.inputRoot else null
        if (inputRoot != null) {
            val layoutHeight =
                if (isFullscreenMode
                ) WindowManager.LayoutParams.WRAP_CONTENT
                else WindowManager.LayoutParams.MATCH_PARENT
            val inputArea = w.findViewById<View>(android.R.id.inputArea)
            // TODO: 需要获取到文本编辑框、完成按钮，设置其色彩和尺寸。
            if (isFullscreenMode) {
                Timber.i("isFullscreenMode")
                /* In Fullscreen mode, when layout contains transparent color,
         * the background under input area will disturb users' typing,
         * so set the input area as light pink */
                inputArea.setBackgroundColor(Color.parseColor("#ff660000"))
            } else {
                Timber.i("NotFullscreenMode")
                /* Otherwise, set it as light gray to avoid potential issue */
                inputArea.setBackgroundColor(Color.parseColor("#dddddddd"))
            }

            updateLayoutHeightOf(inputArea, layoutHeight)
            updateLayoutGravityOf(inputArea, Gravity.BOTTOM)
            updateLayoutHeightOf(inputRoot, layoutHeight)
        }
    }

    fun addEventListener(listener: EventListener): Boolean =
        eventListeners.add(listener)

    fun removeEventListener(listener: EventListener): Boolean =
        eventListeners.remove(listener)

    interface EventListener {
        fun onCreate() {}

        fun onInitializeInputUi(uiBinding: InputRootBinding) {}

        fun onDestroy() {}

        fun onStartInputView(instance: EditorInstance, restarting: Boolean) {}

        fun osFinishInputView(finishingInput: Boolean) {}

        fun onWindowShown() {}

        fun onWindowHidden() {}

        fun onUpdateSelection() {}
    }

    private var candidateExPage = false

    init {
        try {
            check(instance == null) { "Trime already initialized" }
            instance = this
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun hasCandidateExPage(): Boolean {
        return candidateExPage
    }

    fun setCandidateExPage(candidateExPage: Boolean) {
        this.candidateExPage = candidateExPage
    }

    /**
     * Android R introduced Inline Suggestions, which is using to show autofill suggestions in the IME.
     *
     * [Integrate autofill with IMEs and autofill services](https://developer.android.com/guide/topics/text/ime-autofill)
     */
    @RequiresApi(VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        // uiExtras: "androidx.autofill.inline.ui.version:key": "androidx.autofill.inline.ui.version:v1"
        @RequiresApi(VERSION_CODES.R)
        fun createStylesBundle(): Bundle? {
            val actionIconSize = 128
            val pinnedActionMarginEnd = 8

            // We have styles builder, because it's possible that the IME can support multiple UI
            // templates in the future.
            val stylesBuilder = UiVersions.newStylesBuilder()

            // Assuming we only want to support v1 UI template. If the provided uiExtras doesn't contain
            // v1, then return null.
            if (!UiVersions.getVersions(uiExtras).contains(UiVersions.INLINE_UI_VERSION_1)) {
                return null
            }

            // Create the style for v1 template.
            val style: InlineSuggestionUi.Style = InlineSuggestionUi.newStyleBuilder()
                .setSingleIconChipStyle(
                    ViewStyle.Builder()
                        .setBackgroundColor(Color.TRANSPARENT)
                        .setPadding(0, 0, 0, 0)
                        .setLayoutMargin(0, 0, 0, 0)
                        .build()
                )
                .setSingleIconChipIconStyle(
                    ImageViewStyle.Builder()
                        .setMaxWidth(actionIconSize)
                        .setMaxHeight(actionIconSize)
                        .setScaleType(ScaleType.FIT_CENTER)
                        .setLayoutMargin(0, 0, pinnedActionMarginEnd, 0)
//                    .setTintList(actionIconColor)
                        .build()
                )
                .setChipStyle(
                    ViewStyle.Builder()
//                    .setBackground(
//                        Icon.createWithResource(this, R.drawable.chip_background)
//                    )
//                    .setPadding(toPixel(13), 0, toPixel(13), 0)
                        .setPadding(0, 0, 0, 0)
                        .build()
                )
                .setStartIconStyle(
                    ImageViewStyle.Builder()
                        .setLayoutMargin(0, 0, 0, 0)
//                    .setTintList(chipIconColor)
                        .build()
                )
                .setTitleStyle(
                    TextViewStyle.Builder()
//                    .setLayoutMargin(toPixel(4), 0, toPixel(4), 0)
                        .setLayoutMargin(16, 0, 16, 0)
                        .setTextColor(Color.parseColor("#FF202124"))
                        .setTextSize(16f)
                        .build()
                )
                .setSubtitleStyle(
                    TextViewStyle.Builder()
//                    .setLayoutMargin(0, 0, toPixel(4), 0)
                        .setLayoutMargin(0, 0, 16, 0)
                        .setTextColor(Color.parseColor("#99202124")) // 60% opacity
                        .setTextSize(14f)
                        .build()
                )
                .setEndIconStyle(
                    ImageViewStyle.Builder()
                        .setLayoutMargin(0, 0, 0, 0)
//                    .setTintList(chipIconColor)
                        .build()
                )
                .build()

            // Add v1 UI style to the supported styles and return.
            stylesBuilder.addStyle(style)
            val stylesBundle = stylesBuilder.build()
            return stylesBundle
        }

        Timber.i("onCreateInlineSuggestionsRequest")
        return InlineSuggestionsRequest.Builder(List(9) {
            InlinePresentationSpec.Builder(
                // TODO: Replace with real-world sizes
                // In Gboard：
                // min: w ~32dp h ~40dp
                // max: w 240dp h ~40dp
                Size(100, 100),
                Size(100, 100)
            ).apply {
                createStylesBundle()?.let(::setStyle)
            }.build()
        }).apply {
            setMaxSuggestionCount(9)
//            setSupportedLocales()
        }.build()
    }

    @RequiresApi(VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        Timber.i("onInlineSuggestionsResponse")
        response.inlineSuggestions.forEach {
            Timber.i("onInlineSuggestionsResponse: %s", it.toString())
        }
        return true
    }

    companion object {
        private var instance: Trime? = null
        fun getServiceOrNull(): Trime? =
            instance
        fun getService(): Trime =
            instance ?: throw IllegalStateException("Trime not initialized")

        private val syncBackgroundHandler = Handler(
            Looper.getMainLooper()
        ) { msg: Message ->
            if (!(msg.obj as Trime).isShowInputRequested) { // 若当前没有输入面板，则后台同步。防止面板关闭后5秒内再次打开
                syncInBackground()
                (msg.obj as Trime).loadConfig()
            }
            false
        }
    }
}
