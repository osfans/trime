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
package com.osfans.trime.ime.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Message
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupWindow
import android.widget.TextView
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.AppPrefs.Companion.defaultInstance
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.databinding.KeyboardKeyPreviewBinding
import com.osfans.trime.ime.enums.KeyEventType
import com.osfans.trime.ime.keyboard.Key.Companion.isTrimeModifierKey
import com.osfans.trime.util.LeakGuardHandlerWrapper
import com.osfans.trime.util.dp2px
import com.osfans.trime.util.sp2px
import splitties.bitflags.hasFlag
import splitties.systemservices.layoutInflater
import timber.log.Timber
import java.lang.reflect.Method
import java.util.Arrays

/** 顯示[鍵盤][Keyboard]及[按鍵][Key]  */

@Suppress("ktlint:standard:property-naming")
class KeyboardView(context: Context?, attrs: AttributeSet?) : View(context, attrs), View.OnClickListener {
    /** 處理按鍵、觸摸等輸入事件  */
    interface OnKeyboardActionListener {
        /**
         * Called when the user presses a key. This is sent before the [.onKey] is called. For
         * keys that repeat, this is only called once.
         *
         * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid key,
         * the value will be zero.
         */
        fun onPress(primaryCode: Int)

        /**
         * Called when the user releases a key. This is sent after the [.onKey] is called. For
         * keys that repeat, this is only called once.
         *
         * @param primaryCode the code of the key that was released
         */
        fun onRelease(primaryCode: Int)

        fun onEvent(event: Event?)

        /**
         * Send a key press to the listener.
         *
         * @param primaryCode this is the key that was pressed
         * @param mask the codes for all the possible alternative keys with the primary code being the
         * first. If the primary key code is a single character such as an alphabet or number or
         * symbol, the alternatives will include other characters that may be on the same key or
         * adjacent keys. These codes are useful to correct for accidental presses of a key adjacent
         * to the intended key.
         */
        fun onKey(
            primaryCode: Int,
            mask: Int,
        )

        /**
         * Sends a sequence of characters to the listener.
         *
         * @param text the sequence of characters to be displayed.
         */
        fun onText(text: CharSequence?)
    }

    enum class EnterLabelMode {
        ACTION_LABEL_NEVER,
        ACTION_LABEL_ONLY,
        ACTION_LABEL_PREFERRED,
        CUSTOM_PREFERRED,
        ;

        companion object {
            fun fromOrdinal(ordinal: Int) =
                runCatching { entries[ordinal] }
                    .getOrDefault(ACTION_LABEL_NEVER)
        }
    }

    private var mKeyboard: Keyboard? = null
    private var mCurrentKeyIndex = NOT_A_KEY
    private var mLabelTextSize = 0
    private var mKeyTextSize = 0
    private var mKeyTextColor: ColorStateList? = null
    private var mKeyBackColor: StateListDrawable? = null

    private var key_symbol_color = 0
    private var hilited_key_symbol_color = 0
    private var mSymbolSize = 0
    private val mPaintSymbol: Paint
    private var mShadowRadius = 0f
    private var mShadowColor = 0
    private var mBackgroundDimAmount = 0f

    // private Drawable mBackground;
    private val mPreviewText: TextView
    private val mPreviewPopup: PopupWindow
    private var mPreviewOffset = 0
    private var mPreviewHeight = 0

    // Working variable
    private val mCoordinates = IntArray(2)
    private val mPopupKeyboard: PopupWindow
    private var mMiniKeyboardOnScreen = false
    private var mPopupParent: View
    private var mMiniKeyboardOffsetX = 0
    private var mMiniKeyboardOffsetY = 0
    private val mMiniKeyboardCache: MutableMap<Key, View?>
    private var mKeys: Array<Key>? = null

    /** Listener for [OnKeyboardActionListener].  */
    private var onKeyboardActionListener: OnKeyboardActionListener? = null
    private var mVerticalCorrection = 0
    private var mProximityThreshold = 0

    /**
     * Enables or disables the key feedback popup. This is a popup that shows a magnified version of
     * the depressed key. By default the preview is enabled.
     */
    private var showPreview = true
    private var mLastX = 0
    private var mLastY = 0
    private var mStartX = 0
    private var mStartY = 0
    private var touchX0 = 0
    private var touchY0 = 0
    private var touchOnePoint = false

    /**
     * 是否允許距離校正 When enabled, calls to [OnKeyboardActionListener.onKey] will include key codes for
     * adjacent keys. When disabled, only the primary key code will be reported.
     */
    private var enableProximityCorrection = false
    private val mPaint: Paint
    private val mPadding: Rect
    private var mDownTime: Long = 0
    private var mLastMoveTime: Long = 0
    private var mLastKey = 0
    private var mLastCodeX = 0
    private var mLastCodeY = 0
    private var mCurrentKey = NOT_A_KEY
    private var mDownKey = NOT_A_KEY
    private var mLastKeyTime: Long = 0
    private var mCurrentKeyTime: Long = 0
    private var mLastUpTime: Long = 0
    private var isFastInput = false
    private var isClickAtLast = false
    private val mKeyIndices = IntArray(12)
    private var mGestureDetector: GestureDetector? = null
    private var mRepeatKeyIndex = NOT_A_KEY
    private val mPopupLayout: Int
    private var mAbortKey = false
    private var mInvalidatedKey: Key? = null
    private val mClipRegion = Rect(0, 0, 0, 0)
    private var mPossiblePoly = false
    private val mSwipeTracker = SwipeTracker()
    private val mDisambiguateSwipe: Boolean

    // Variables for dealing with multiple pointers
    private var mOldPointerCount = 1
    private val mComboCodes = IntArray(10)
    private var mComboCount = 0
    private var mComboMode = false
    private val mDistances = IntArray(MAX_NEARBY_KEYS)

    // For multi-tap
    private var mLastSentIndex = 0
    private var mLastTapTime: Long = 0
    private val mPreviewLabel = StringBuilder(1)

    /** Whether the keyboard bitmap needs to be redrawn before it's blitted. *  */
    private var mDrawPending = false

    /** The dirty region in the keyboard bitmap  */
    private val mDirtyRect = Rect()

    /** The keyboard bitmap for faster updates  */
    private var mBuffer: Bitmap? = null

    /** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer.  */
    private var mKeyboardChanged = false

    /** The canvas for the above mutable keyboard bitmap  */
    private var mCanvas: Canvas? = null
    // The accessibility manager for accessibility support */
    // private AccessibilityManager mAccessibilityManager;
    // The audio manager for accessibility support */
    // private AudioManager mAudioManager;

    /**
     * Whether the requirement of a headset to hear passwords if accessibility is enabled is
     * announced.
     */
    private val mHeadsetRequiredToHearPasswordsAnnounced = false
    private var mShowHint = true
    private var mShowSymbol = true
    private var findStateDrawableIndex: Method? = null
    private var getStateDrawable: Method? = null
    private var labelEnter: String? = "⏎"
    private var mEnterLabels: MutableMap<String, String?>? = null
    private var enterLabelMode = EnterLabelMode.ACTION_LABEL_NEVER

    fun setShowHint(value: Boolean) {
        mShowHint = value
    }

    fun setShowSymbol(value: Boolean) {
        mShowSymbol = value
    }

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener?) {
        onKeyboardActionListener = listener
    }

    private fun handleEnterLabel(theme: Theme) {
        mEnterLabels = theme.style.getObject("enter_labels") as MutableMap<String, String?>?
            ?: hashMapOf()
        labelEnter = mEnterLabels!!["default"] ?: "⏎".also { mEnterLabels!!["default"] = it }
        for (label in arrayOf("done", "go", "next", "none", "pre", "search", "send")) {
            if (!mEnterLabels!!.containsKey(label)) {
                mEnterLabels!![label] = labelEnter
            }
        }
    }

    fun updateEnterLabelOnEditorInfo(info: EditorInfo) {
        if (info.imeOptions.hasFlag(EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            labelEnter = mEnterLabels!!["default"]
        } else {
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val actionLabel = info.actionLabel
            when (enterLabelMode) {
                EnterLabelMode.ACTION_LABEL_ONLY -> {
                    labelEnter = actionLabel.toString()
                }
                EnterLabelMode.ACTION_LABEL_PREFERRED -> {
                    labelEnter =
                        if (!actionLabel.isNullOrEmpty()) {
                            actionLabel.toString()
                        } else {
                            mEnterLabels!!["default"]
                        }
                }
                EnterLabelMode.CUSTOM_PREFERRED,
                EnterLabelMode.ACTION_LABEL_NEVER,
                -> {
                    labelEnter =
                        when (action) {
                            EditorInfo.IME_ACTION_DONE -> mEnterLabels!!["done"]
                            EditorInfo.IME_ACTION_GO -> mEnterLabels!!["go"]
                            EditorInfo.IME_ACTION_NEXT -> mEnterLabels!!["next"]
                            EditorInfo.IME_ACTION_PREVIOUS -> mEnterLabels!!["pre"]
                            EditorInfo.IME_ACTION_SEARCH -> mEnterLabels!!["search"]
                            EditorInfo.IME_ACTION_SEND -> mEnterLabels!!["send"]
                            else -> {
                                if (enterLabelMode == EnterLabelMode.ACTION_LABEL_NEVER) {
                                    mEnterLabels!!["default"]
                                } else {
                                    if (!actionLabel.isNullOrEmpty()) {
                                        actionLabel.toString()
                                    } else {
                                        mEnterLabels!!["default"]
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    private val mHandler = MyHandler(this)

    private class MyHandler(view: KeyboardView) : LeakGuardHandlerWrapper<KeyboardView?>(view) {
        override fun handleMessage(msg: Message) {
            val mKeyboardView = getOwnerInstanceOrNull() ?: return
            when (msg.what) {
                MSG_SHOW_PREVIEW -> mKeyboardView.showKey(msg.arg1, msg.arg2)
                MSG_REMOVE_PREVIEW -> mKeyboardView.mPreviewText.visibility = INVISIBLE
                MSG_REPEAT ->
                    if (mKeyboardView.repeatKey()) {
                        val repeat = Message.obtain(this, MSG_REPEAT)
                        sendMessageDelayed(repeat, prefs.keyboard.repeatInterval.toLong())
                    }

                MSG_LONGPRESS -> {
                    InputFeedbackManager.keyPressVibrate(mKeyboardView, true)
                    mKeyboardView.openPopupIfRequired(msg.obj as MotionEvent)
                }
            }
        }
    }

    fun reset() {
        val theme = ThemeManager.activeTheme
        key_symbol_color = theme.colors.getColor("key_symbol_color")!!
        hilited_key_symbol_color = theme.colors.getColor("hilited_key_symbol_color")!!
        mShadowColor = theme.colors.getColor("shadow_color")!!
        mSymbolSize = sp2px(theme.style.getFloat("symbol_text_size")).toInt()
        mKeyTextSize = sp2px(theme.style.getFloat("key_text_size")).toInt()
        mVerticalCorrection = dp2px(theme.style.getFloat("vertical_correction")).toInt()
        enableProximityCorrection = theme.style.getBoolean("proximity_correction")
        mPreviewOffset = dp2px(theme.style.getFloat("preview_offset")).toInt()
        mPreviewHeight = dp2px(theme.style.getFloat("preview_height")).toInt()
        mLabelTextSize = sp2px(theme.style.getFloat("key_long_text_size")).toInt()
        if (mLabelTextSize == 0) mLabelTextSize = mKeyTextSize
        mBackgroundDimAmount = theme.style.getFloat("background_dim_amount")
        mShadowRadius = theme.style.getFloat("shadow_radius")
        val mRoundCorner = theme.style.getFloat("round_corner")
        mKeyBackColor = StateListDrawable()
        mKeyBackColor!!.addState(Key.KEY_STATE_ON_PRESSED, theme.colors.getDrawable("hilited_on_key_back_color"))
        mKeyBackColor!!.addState(Key.KEY_STATE_ON_NORMAL, theme.colors.getDrawable("on_key_back_color"))
        mKeyBackColor!!.addState(Key.KEY_STATE_OFF_PRESSED, theme.colors.getDrawable("hilited_off_key_back_color"))
        mKeyBackColor!!.addState(Key.KEY_STATE_OFF_NORMAL, theme.colors.getDrawable("off_key_back_color"))
        mKeyBackColor!!.addState(Key.KEY_STATE_PRESSED, theme.colors.getDrawable("hilited_key_back_color"))
        mKeyBackColor!!.addState(Key.KEY_STATE_NORMAL, theme.colors.getDrawable("key_back_color"))
        mKeyTextColor =
            ColorStateList(
                Key.KEY_STATES,
                intArrayOf(
                    theme.colors.getColor("hilited_on_key_text_color")!!,
                    theme.colors.getColor("on_key_text_color")!!,
                    theme.colors.getColor("hilited_off_key_text_color")!!,
                    theme.colors.getColor("off_key_text_color")!!,
                    theme.colors.getColor("hilited_key_text_color")!!,
                    theme.colors.getColor("key_text_color")!!,
                ),
            )
        val color = theme.colors.getColor("preview_text_color")
        if (color != null) mPreviewText.setTextColor(color)
        val previewBackColor = theme.colors.getColor("preview_back_color")
        if (previewBackColor != null) {
            val background = GradientDrawable()
            background.setColor(previewBackColor)
            background.cornerRadius = mRoundCorner
            mPreviewText.background = background
        }
        val mPreviewTextSizeLarge = theme.style.getInt("preview_text_size")
        mPreviewText.textSize = mPreviewTextSizeLarge.toFloat()
        showPreview = prefs.keyboard.popupKeyPressEnabled
        mPaint.setTypeface(FontManager.getTypeface("key_font"))
        mPaintSymbol.setTypeface(FontManager.getTypeface("symbol_font"))
        mPaintSymbol.color = key_symbol_color
        mPaintSymbol.textSize = mSymbolSize.toFloat()
        mPreviewText.typeface = FontManager.getTypeface("preview_font")
        handleEnterLabel(theme)
        enterLabelMode = EnterLabelMode.fromOrdinal(theme.style.getInt("enter_label_mode"))
        invalidateAllKeys()
    }

    init {
        try {
            findStateDrawableIndex =
                StateListDrawable::class.java.getMethod(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "findStateDrawableIndex" else "getStateDrawableIndex",
                    IntArray::class.java,
                )
            getStateDrawable = StateListDrawable::class.java.getMethod("getStateDrawable", Int::class.javaPrimitiveType)
        } catch (ex: Exception) {
            Timber.e(ex, "Get Drawable Exception")
        }
        mPreviewText = KeyboardKeyPreviewBinding.inflate(LayoutInflater.from(context)).root
        mPaint = Paint()
        mPaint.isAntiAlias = true
        mPaint.textAlign = Paint.Align.CENTER
        mPaintSymbol = Paint()
        mPaintSymbol.isAntiAlias = true
        mPaintSymbol.textAlign = Paint.Align.CENTER
        // reset(context);
        mPreviewPopup = PopupWindow(context)
        mPreviewPopup.contentView = mPreviewText
        mPreviewPopup.setBackgroundDrawable(null)
        mPreviewPopup.isTouchable = false
        mPopupLayout = R.layout.keyboard_popup_keyboard
        mPopupKeyboard = PopupWindow(context)
        mPopupKeyboard.setBackgroundDrawable(null)
        mPopupParent = this
        mPadding = Rect(0, 0, 0, 0)
        mMiniKeyboardCache = HashMap()
        mDisambiguateSwipe = false
        resetMultiTap()
        initGestureDetector()
    }

    private fun initGestureDetector() {
        mGestureDetector =
            GestureDetector(
                null,
                object : SimpleOnGestureListener() {
                    override fun onFling(
                        me1: MotionEvent?,
                        me2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        /*
                    Judgment basis: the sliding distance exceeds the threshold value,
                    and the sliding distance on the corresponding axis is less than
                    the sliding distance on the other coordinate axis.
                         */
                        if (mPossiblePoly) return false
                        val deltaX = me2.x - me1!!.x // distance X
                        val deltaY = me2.y - me1.y // distance Y
                        val absX = Math.abs(deltaX) // absolute value of distance X
                        val absY = Math.abs(deltaY) // absolute value of distance Y
                        val travel = // threshold distance
                            // I don't really know what getSwipeTravelHi is.
                            // For any one see this plz change the method name to something
                            // more understandable.
                            if (isFastInput && isClickAtLast) prefs.keyboard.swipeTravelHi else prefs.keyboard.swipeTravel
                        val velocity = // threshold velocity.
                            // Same here for getSwipeVelocityHi
                            if (isFastInput && isClickAtLast) prefs.keyboard.swipeVelocity else prefs.keyboard.swipeVelocityHi
                        mSwipeTracker.computeCurrentVelocity(10)
                        val endingVelocityX: Float = mSwipeTracker.xVelocity
                        val endingVelocityY: Float = mSwipeTracker.yVelocity
                        var sendDownKey = false
                        var type = KeyEventType.CLICK
                        //  In my tests velocity always smaller than 400
                        //  so I don't really why we need to compare velocity here,
                        //  as default value of getSwipeVelocity() is 800
                        //  and default value of getSwipeVelocityHi() is 25000,
                        //  so for most of the users that judgment is always true
                        if ((deltaX > travel || velocityX > velocity) &&
                            (
                                absY < absX || (
                                    deltaY > 0 &&
                                        mKeys!![mDownKey].events[KeyEventType.SWIPE_UP.ordinal] == null
                                ) ||
                                    (
                                        deltaY < 0 &&
                                            mKeys!![mDownKey].events[KeyEventType.SWIPE_DOWN.ordinal] == null
                                    )
                            ) && mKeys!![mDownKey].events[KeyEventType.SWIPE_RIGHT.ordinal] != null
                        ) {
                            // I should have implement mDisambiguateSwipe as a config option, but the logic
                            // here is really weird, and I don't really know
                            // when it is enabled what should be the behavior, so I just left it always false.
                            // endingVelocityX and endingVelocityY seems always > 0 but velocityX and
                            // velocityY can be negative.
                            if (mDisambiguateSwipe && endingVelocityX > velocityX / 4) {
                                return true
                            } else {
                                sendDownKey = true
                                type = KeyEventType.SWIPE_RIGHT
                            }
                        } else if ((deltaX < -travel || velocityX < -velocity) &&
                            (
                                absY < absX || (
                                    deltaY > 0 &&
                                        mKeys!![mDownKey].events[KeyEventType.SWIPE_UP.ordinal] == null
                                ) ||
                                    (
                                        deltaY < 0 &&
                                            mKeys!![mDownKey].events[KeyEventType.SWIPE_DOWN.ordinal] == null
                                    )
                            ) && mKeys!![mDownKey].events[KeyEventType.SWIPE_LEFT.ordinal] != null
                        ) {
                            if (mDisambiguateSwipe && endingVelocityX < velocityX / 4) {
                                return true
                            } else {
                                sendDownKey = true
                                type = KeyEventType.SWIPE_LEFT
                            }
                        } else if ((deltaY < -travel || velocityY < -velocity) &&
                            (
                                absX < absY || (
                                    deltaX > 0 &&
                                        mKeys!![mDownKey].events[KeyEventType.SWIPE_RIGHT.ordinal] == null
                                ) ||
                                    (
                                        deltaX < 0 &&
                                            mKeys!![mDownKey].events[KeyEventType.SWIPE_LEFT.ordinal] == null
                                    )
                            ) && mKeys!![mDownKey].events[KeyEventType.SWIPE_UP.ordinal] != null
                        ) {
                            if (mDisambiguateSwipe && endingVelocityY < velocityY / 4) {
                                return true
                            } else {
                                sendDownKey = true
                                type = KeyEventType.SWIPE_UP
                            }
                        } else if ((deltaY > travel || velocityY > velocity) &&
                            (
                                absX < absY || (
                                    deltaX > 0 &&
                                        mKeys!![mDownKey].events[KeyEventType.SWIPE_RIGHT.ordinal] == null
                                ) ||
                                    (
                                        deltaX < 0 &&
                                            mKeys!![mDownKey].events[KeyEventType.SWIPE_LEFT.ordinal] == null
                                    )
                            ) && mKeys!![mDownKey].events[KeyEventType.SWIPE_DOWN.ordinal] != null
                        ) {
                            if (mDisambiguateSwipe && endingVelocityY > velocityY / 4) {
                                return true
                            } else {
                                sendDownKey = true
                                type = KeyEventType.SWIPE_DOWN
                            }
                        } else {
                            Timber.d(
                                "swipeDebug.onFling fail , dY=%f, vY=%f, eVY=%f, travel=%d",
                                deltaY,
                                velocityY,
                                endingVelocityY,
                                travel,
                            )
                        }
                        if (sendDownKey) {
                            Timber.d("\t<TrimeInput>\tinitGestureDetector()\tsendDownKey")
                            showPreview(NOT_A_KEY)
                            showPreview(mDownKey, type.ordinal)
                            detectAndSendKey(mDownKey, mStartX, mStartY, me1.eventTime, type)
                            isClickAtLast = false
                            return true
                        }
                        return false
                    }
                },
            )
        mGestureDetector!!.setIsLongpressEnabled(false)
    }

    private fun setKeyboardBackground() {
        if (mKeyboard == null) return
        val d = mPreviewText.background
        if (d is GradientDrawable) {
            d.cornerRadius = mKeyboard!!.roundCorner
            mPreviewText.background = d
        }
    }

    var keyboard: Keyboard?
        /**
         * Returns the current keyboard being displayed by this view.
         *
         * @return the currently attached keyboard
         * @see .setKeyboard
         */
        get() = mKeyboard

        /**
         * Attaches a keyboard to this view. The keyboard can be switched at any time and the view will
         * re-layout itself to accommodate the keyboard.
         *
         * @see Keyboard
         *
         * @see .getKeyboard
         * @param keyboard the keyboard to display in this view
         */
        set(keyboard) {
            if (mKeyboard != null) {
                showPreview(NOT_A_KEY)
            }
            // Remove any pending messages
            removeMessages()
            mRepeatKeyIndex = NOT_A_KEY
            mKeyboard = keyboard
            val keys = mKeyboard!!.keys
            mKeys = keys.toTypedArray<Key>()
            setKeyboardBackground()
            requestLayout()
            // Hint to reallocate the buffer if the size changed
            mKeyboardChanged = true
            invalidateAllKeys()
            computeProximityThreshold(keyboard)
            mMiniKeyboardCache.clear() // Not really necessary to do every time, but will free up views
            // Switching to a different keyboard should abort any pending keys so that the key up
            // doesn't get delivered to the old or new keyboard
            mAbortKey = true // Until the next ACTION_DOWN
        }

    /**
     * 设置键盘修饰键的状态
     *
     * @param key 按下的修饰键(非组合键）
     * @return
     */
    fun setModifier(key: Key): Boolean {
        if (mKeyboard != null) {
            if (mKeyboard!!.clikModifierKey(key.isShiftLock, key.modifierKeyOnMask)) {
                invalidateAllKeys()
                return true
            }
        }
        return false
    }

    /**
     * 設定鍵盤的Shift鍵狀態
     *
     * @param on 是否保持Shift按下狀態
     * @param shifted 是否按下Shift
     * @return Shift鍵狀態是否改變
     * @see Keyboard.setShifted
     */
    fun setShifted(
        on: Boolean,
        shifted: Boolean,
    ): Boolean {
        if (mKeyboard != null) {
            // todo 扩展为设置全部修饰键的状态
            if (mKeyboard!!.setShifted(on, shifted)) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys()
                return true
            }
        }
        return false
    }

    private fun resetShifted(): Boolean {
        if (mKeyboard != null) {
            if (mKeyboard!!.resetShifted()) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys()
                return true
            }
        }
        return false
    }

    // 重置全部修饰键的状态
    private fun resetModifer(): Boolean {
        if (mKeyboard != null) {
            if (mKeyboard!!.resetModifer()) {
                // The whole keyboard probably needs to be redrawn
                invalidateAllKeys()
                return true
            }
        }
        return false
    }

    // 重置全部修饰键的状态(如果有锁定则不重置）
    private fun refreshModifier() {
        if (mKeyboard != null) {
            if (mKeyboard!!.refreshModifier()) {
                invalidateAllKeys()
            }
        }
    }

    fun hasModifier(): Boolean {
        return if (mKeyboard != null) {
            mKeyboard!!.hasModifier()
        } else {
            false
        }
    }

    val isShifted: Boolean
        /**
         * Returns the state of the shift key of the keyboard, if any.
         *
         * @return true if the shift is in a pressed state, false otherwise. If there is no shift key on
         * the keyboard or there is no keyboard attached, it returns false.
         * @see KeyboardView.setShifted
         */
        get() =
            if (mKeyboard != null) {
                mKeyboard!!.isShifted
            } else {
                false
            }

    /**
     * 返回鍵盤是否爲大寫狀態
     */
    val isCapsOn: Boolean
        get() = if (mKeyboard != null && mKeyboard!!.mShiftKey != null) mKeyboard!!.mShiftKey!!.isOn else false
    val isShiftOn: Boolean
        get() = if (mKeyboard != null && mKeyboard!!.mShiftKey != null) mKeyboard!!.mShiftKey!!.isOn else false
    val isAltOn: Boolean
        get() = if (mKeyboard != null && mKeyboard!!.mAltKey != null) mKeyboard!!.mAltKey!!.isOn else false
    val isSysOn: Boolean
        get() = if (mKeyboard != null && mKeyboard!!.mSymKey != null) mKeyboard!!.mSymKey!!.isOn else false
    val isCtrlOn: Boolean
        get() = if (mKeyboard != null && mKeyboard!!.mCtrlKey != null) mKeyboard!!.mCtrlKey!!.isOn else false
    val isMetaOn: Boolean
        get() = if (mKeyboard != null && mKeyboard!!.mMetaKey != null) mKeyboard!!.mMetaKey!!.isOn else false

    // public void setVerticalCorrection(int verticalOffset) {}

    private fun setPopupOffset(
        x: Int,
        y: Int,
    ) {
        mMiniKeyboardOffsetX = x
        mMiniKeyboardOffsetY = y
        if (mPreviewPopup.isShowing) {
            mPreviewPopup.dismiss()
        }
    }

    /**
     * 關閉彈出鍵盤
     *
     * @param v 鍵盤視圖
     */
    override fun onClick(v: View) {
        dismissPopupKeyboard()
    }

    public override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // Round up a little
        if (mKeyboard == null) {
            setMeasuredDimension(
                paddingLeft + paddingRight,
                paddingTop + paddingBottom,
            )
        } else {
            var width = mKeyboard!!.minWidth + paddingLeft + paddingRight
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec)
            }
            setMeasuredDimension(width, mKeyboard!!.height + paddingTop + paddingBottom)
        }
    }

    /**
     * 計算水平和豎直方向的相鄰按鍵中心的平均距離的平方，這樣不需要做開方運算
     *
     * @param keyboard 鍵盤
     */
    private fun computeProximityThreshold(keyboard: Keyboard?) {
        if (keyboard == null) return
        val keys = mKeys ?: return
        val length = keys.size
        var dimensionSum = 0
        for (key in keys) {
            dimensionSum += Math.min(key.width, key.height) + key.gap
        }
        if (dimensionSum < 0 || length == 0) return
        mProximityThreshold = (dimensionSum * Keyboard.SEARCH_DISTANCE / length).toInt()
        mProximityThreshold *= mProximityThreshold // Square it
    }

    public override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        // if (mKeyboard != null) {
        // mKeyboard.resize(w, h);
        // }
        // Release the buffer, if any and it will be reallocated on the next draw
        mBuffer = null
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mDrawPending || mBuffer == null || mKeyboardChanged) {
            onBufferDraw()
        }
        canvas.drawBitmap(mBuffer!!, 0f, 0f, null)
    }

    private fun onBufferDraw() {
        if (mBuffer == null || mKeyboardChanged) {
            if (mBuffer == null ||
                mKeyboardChanged &&
                (mBuffer!!.width != width || mBuffer!!.height != height)
            ) {
                // Make sure our bitmap is at least 1x1
                val width = Math.max(1, width)
                val height = Math.max(1, height)
                mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                mCanvas = Canvas(mBuffer!!)
            }
            invalidateAllKeys()
            mKeyboardChanged = false
        }
        if (mKeyboard == null) return
        mCanvas!!.save()
        val canvas = mCanvas
        canvas!!.clipRect(mDirtyRect)
        val paint = mPaint
        var keyBackground: Drawable?
        val clipRegion = mClipRegion
        val padding = mPadding
        val kbdPaddingLeft = paddingLeft
        val kbdPaddingTop = paddingTop
        val keys = mKeys
        val invalidKey = mInvalidatedKey
        var drawSingleKey = false
        if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
            // Is clipRegion completely contained within the invalidated key?
            if (invalidKey.x + kbdPaddingLeft - 1 <= clipRegion.left &&
                invalidKey.y + kbdPaddingTop - 1 <= clipRegion.top &&
                invalidKey.x + invalidKey.width + kbdPaddingLeft + 1 >= clipRegion.right &&
                invalidKey.y + invalidKey.height + kbdPaddingTop + 1 >= clipRegion.bottom
            ) {
                drawSingleKey = true
            }
        }
        canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR)
        val keyCount = keys!!.size
        val symbolBase = padding.top - mPaintSymbol.fontMetrics.top
        val hintBase = -padding.bottom - mPaintSymbol.fontMetrics.bottom
        Timber.i(
            "onBufferDraw() keyCount=%d, drawSingleKey=%s, invalidKeyIsNull=%s",
            keyCount,
            drawSingleKey,
            invalidKey == null,
        )
        mKeyboard!!.printModifierKeyState("onBufferDraw, drawSingleKey=$drawSingleKey")
        for (key in keys) {
            if (drawSingleKey && invalidKey != key) {
                continue
            }
            val drawableState = key.currentDrawableState
            keyBackground = key.getBackColorForState(drawableState)
            if (keyBackground == null) {
                Timber.d("onBufferDraw() keyBackground==null, key=%s", key.getLabel())
                try {
                    val index = findStateDrawableIndex!!.invoke(mKeyBackColor, *arrayOf(drawableState)) as Int
                    keyBackground = getStateDrawable!!.invoke(mKeyBackColor, index) as Drawable
                } catch (ex: Exception) {
                    Timber.e(ex, "Get Drawable Exception")
                }
            }
            if (keyBackground is GradientDrawable) {
                keyBackground.cornerRadius =
                    (
                        if (key.round_corner != null && key.round_corner!! > 0) {
                            key.round_corner
                        } else {
                            mKeyboard!!.roundCorner
                        }
                    )!!
            }
            var color = key.getTextColorForState(drawableState)
            mPaint.color = color
                ?: mKeyTextColor!!.getColorForState(drawableState, 0)
            color = key.getSymbolColorForState(drawableState)
            mPaintSymbol.color = color
                ?: if (key.isPressed) hilited_key_symbol_color else key_symbol_color

            // Switch the character to uppercase if shift is pressed
            var label = key.getLabel()
            if (label == "enter_labels") label = labelEnter
            val hint = key.hint
            val left = (key.width - padding.left - padding.right) / 2 + padding.left
            val top = padding.top
            val bounds = keyBackground?.bounds
            if (key.width != bounds?.right || key.height != bounds.bottom) {
                keyBackground?.setBounds(0, 0, key.width, key.height)
            }
            canvas.translate((key.x + kbdPaddingLeft).toFloat(), (key.y + kbdPaddingTop).toFloat())
            keyBackground?.draw(canvas)
            if (!TextUtils.isEmpty(label)) {
                // For characters, use large font. For labels like "Done", use small font.
                if (key.key_text_size != null && key.key_text_size!! > 0) {
                    paint.textSize = key.key_text_size!!.toFloat()
                } else {
                    paint.textSize = (if (label!!.length > 1) mLabelTextSize else mKeyTextSize).toFloat()
                }
                // Draw a drop shadow for the text
                paint.setShadowLayer(mShadowRadius, 0f, 0f, mShadowColor)
                // Draw the text
                canvas.drawText(
                    label!!,
                    (
                        left + key.key_text_offset_x
                    ).toFloat(),
                    (key.height - padding.top - padding.bottom) / 2f + (paint.textSize - paint.descent()) / 2f + top +
                        key.key_text_offset_y,
                    paint,
                )
                if (mShowSymbol) {
                    val labelSymbol = key.symbolLabel
                    if (!TextUtils.isEmpty(labelSymbol)) {
                        mPaintSymbol.textSize =
                            (
                                if (key.symbol_text_size != null && key.symbol_text_size!! > 0) key.symbol_text_size else mSymbolSize
                            )!!.toFloat()
                        mPaintSymbol.setShadowLayer(mShadowRadius, 0f, 0f, mShadowColor)
                        canvas.drawText(
                            labelSymbol!!,
                            (
                                left + key.key_symbol_offset_x
                            ).toFloat(),
                            symbolBase + key.key_symbol_offset_y,
                            mPaintSymbol,
                        )
                    }
                }
                if (mShowHint) {
                    if (!TextUtils.isEmpty(hint)) {
                        mPaintSymbol.setShadowLayer(mShadowRadius, 0f, 0f, mShadowColor)
                        canvas.drawText(
                            hint!!,
                            (
                                left + key.key_hint_offset_x
                            ).toFloat(),
                            key.height + hintBase + key.key_hint_offset_y,
                            mPaintSymbol,
                        )
                    }
                }

                // Turn off drop shadow
                paint.setShadowLayer(0f, 0f, 0f, 0)
            }
            canvas.translate((-key.x - kbdPaddingLeft).toFloat(), (-key.y - kbdPaddingTop).toFloat())
            //      break;
        }
        mInvalidatedKey = null
        // Overlay a dark rectangle to dim the keyboard
        if (mMiniKeyboardOnScreen) {
            paint.color = (mBackgroundDimAmount * 0xFF).toInt() shl 24
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        val mShowTouchPoints = true
        if (DEBUG && mShowTouchPoints) {
            paint.alpha = 128
            paint.color = -0x10000
            canvas.drawCircle(mStartX.toFloat(), mStartY.toFloat(), 3f, paint)
            canvas.drawLine(mStartX.toFloat(), mStartY.toFloat(), mLastX.toFloat(), mLastY.toFloat(), paint)
            paint.color = -0xffff01
            canvas.drawCircle(mLastX.toFloat(), mLastY.toFloat(), 3f, paint)
            paint.color = -0xff0100
            canvas.drawCircle((mStartX + mLastX) / 2f, (mStartY + mLastY) / 2f, 2f, paint)
        }
        mCanvas!!.restore()
        mDrawPending = false
        mDirtyRect.setEmpty()
    }

    private fun getKeyIndices(
        x: Int,
        y: Int,
        allKeys: IntArray?,
    ): Int {
        val keys = mKeys
        var primaryIndex = NOT_A_KEY
        var closestKey = NOT_A_KEY
        var closestKeyDist = mProximityThreshold + 1
        Arrays.fill(mDistances, Int.MAX_VALUE)
        val nearestKeyIndices = mKeyboard!!.getNearestKeys(x, y)
        for (nearestKeyIndex in nearestKeyIndices!!) {
            val key = keys!![nearestKeyIndex]
            var dist = 0
            val isInside = key.isInside(x, y)
            if (isInside) {
                primaryIndex = nearestKeyIndex
            }
            if (enableProximityCorrection && key.squaredDistanceFrom(x, y).also { dist = it } < mProximityThreshold ||
                isInside
            ) {
                // Find insertion point
                val nCodes = 1
                if (dist < closestKeyDist) {
                    closestKeyDist = dist
                    closestKey = nearestKeyIndex
                }
                if (allKeys == null) continue
                for (j in mDistances.indices) {
                    if (mDistances[j] > dist) {
                        // Make space for nCodes codes
                        System.arraycopy(mDistances, j, mDistances, j + nCodes, mDistances.size - j - nCodes)
                        System.arraycopy(allKeys, j, allKeys, j + nCodes, allKeys.size - j - nCodes)
                        allKeys[j] = key.code
                        mDistances[j] = dist
                        break
                    }
                }
            }
        }
        if (primaryIndex == NOT_A_KEY) {
            primaryIndex = closestKey
        }
        return primaryIndex
    }

    private fun releaseKey(code: Int) {
        Timber.d(
            "\t<TrimeInput>\treleaseKey() key=%d, mComboCount=%d, mComboMode=%s",
            code,
            mComboCount,
            mComboMode,
        )
        if (mComboMode) {
            if (mComboCount > 9) mComboCount = 9
            mComboCodes[mComboCount++] = code
        } else {
            onKeyboardActionListener!!.onRelease(code)
            if (mComboCount > 0) {
                for (i in 0 until mComboCount) {
                    onKeyboardActionListener!!.onRelease(mComboCodes[i])
                }
                mComboCount = 0
            }
        }
        Timber.d("\t<TrimeInput>\treleaseKey() finish")
    }

    private fun detectAndSendKey(
        index: Int,
        x: Int,
        y: Int,
        eventTime: Long,
        type: KeyEventType = KeyEventType.CLICK,
    ) {
        Timber.d(
            "\t<TrimeInput>\tdetectAndSendKey()\tindex=%d, x=%d, y=%d, type=%d, mKeys.length=%d",
            index,
            x,
            y,
            type.ordinal,
            mKeys!!.size,
        )
        if (index != NOT_A_KEY && index < mKeys!!.size) {
            val key = mKeys!![index]
            if (isTrimeModifierKey(key.code) && !key.sendBindings(type.ordinal)) {
                Timber.d(
                    "\t<TrimeInput>\tdetectAndSendKey()\tModifierKey, key.getEvent, KeyLabel=%s",
                    key.getLabel(),
                )
                setModifier(key)
            } else {
                if (key.click!!.isRepeatable) {
                    if (type.ordinal > KeyEventType.CLICK.ordinal) mAbortKey = true
                    if (!key.hasEvent(type.ordinal)) return
                }
                val code = key.getCode(type.ordinal)
                // TextEntryState.keyPressedAt(key, x, y);
                val codes = IntArray(MAX_NEARBY_KEYS)
                Arrays.fill(codes, NOT_A_KEY)
                // getKeyIndices(x, y, codes); // 这里实际上并没有生效
                Timber.d("\t<TrimeInput>\tdetectAndSendKey()\tonEvent, code=%d, key.getEvent", code)
                // 可以在这里把 mKeyboard.getModifer() 获取的修饰键状态写入event里
                onKeyboardActionListener!!.onEvent(key.getEvent(type.ordinal))
                releaseKey(code)
                Timber.d("\t<TrimeInput>\tdetectAndSendKey()\trefreshModifier")
                refreshModifier()
            }
            mLastSentIndex = index
            mLastTapTime = eventTime
            Timber.d("\t<TrimeInput>\tdetectAndSendKey()\tfinish")
        }
    }

    private fun showPreview(
        keyIndex: Int,
        type: Int = 0,
    ) {
        val oldKeyIndex = mCurrentKeyIndex
        val previewPopup = mPreviewPopup
        mCurrentKeyIndex = keyIndex
        // Release the old key and press the new key
        val keys = mKeys
        if (oldKeyIndex != mCurrentKeyIndex) {
            if (oldKeyIndex != NOT_A_KEY && keys!!.size > oldKeyIndex) {
                val oldKey = keys[oldKeyIndex]
                oldKey.onReleased(mCurrentKeyIndex == NOT_A_KEY)
                invalidateKey(oldKeyIndex)
            }
            if (mCurrentKeyIndex != NOT_A_KEY && keys!!.size > mCurrentKeyIndex) {
                val newKey = keys[mCurrentKeyIndex]
                newKey.onPressed()
                invalidateKey(mCurrentKeyIndex)
            }
        }
        // If key changed and preview is on ...
        if (oldKeyIndex != mCurrentKeyIndex && showPreview) {
            mHandler.removeMessages(MSG_SHOW_PREVIEW)
            if (previewPopup.isShowing) {
                if (keyIndex == NOT_A_KEY) {
                    mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(MSG_REMOVE_PREVIEW),
                        DELAY_AFTER_PREVIEW.toLong(),
                    )
                }
            }
            if (keyIndex != NOT_A_KEY) {
                if (previewPopup.isShowing && mPreviewText.visibility == VISIBLE) {
                    // Show right away, if it's already visible and finger is moving around
                    showKey(keyIndex, type)
                } else {
                    mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(MSG_SHOW_PREVIEW, keyIndex, type),
                        DELAY_BEFORE_PREVIEW.toLong(),
                    )
                }
            }
        }
    }

    private fun showKey(
        keyIndex: Int,
        type: Int,
    ) {
        val previewPopup = mPreviewPopup
        val keys = mKeys
        if (keyIndex < 0 || keyIndex >= mKeys!!.size) return
        val key = keys!![keyIndex]
        mPreviewText.setCompoundDrawables(null, null, null, null)
        mPreviewText.text = key.getPreviewText(type)
        mPreviewText.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val popupWidth =
            Math.max(
                mPreviewText.measuredWidth,
                key.width + mPreviewText.paddingLeft + mPreviewText.paddingRight,
            )
        val popupHeight = mPreviewHeight
        val lp = mPreviewText.layoutParams
        if (lp != null) {
            lp.width = popupWidth
            lp.height = popupHeight
        }
        var mPopupPreviewY: Int
        var mPopupPreviewX: Int
        val mPreviewCentered = false
        if (!mPreviewCentered) {
            mPopupPreviewX = key.x - mPreviewText.paddingLeft + paddingLeft
            mPopupPreviewY = key.y - popupHeight + mPreviewOffset
        } else {
            // TODO: Fix this if centering is brought back
            mPopupPreviewX = 160 - mPreviewText.measuredWidth / 2
            mPopupPreviewY = -mPreviewText.measuredHeight
        }
        mHandler.removeMessages(MSG_REMOVE_PREVIEW)
        getLocationInWindow(mCoordinates)
        mCoordinates[0] += mMiniKeyboardOffsetX // Offset may be zero
        mCoordinates[1] += mMiniKeyboardOffsetY // Offset may be zero

        // Set the preview background state
        mPreviewText
            .background
            .setState(if (key.popupResId != 0) LONG_PRESSABLE_STATE_SET else EMPTY_STATE_SET)
        mPopupPreviewX += mCoordinates[0]
        mPopupPreviewY += mCoordinates[1]

        // If the popup cannot be shown above the key, put it on the side
        getLocationOnScreen(mCoordinates)
        if (mPopupPreviewY + mCoordinates[1] < 0) {
            // If the key you're pressing is on the left side of the keyboard, show the popup on
            // the right, offset by enough to see at least one key to the left/right.
            if (key.x + key.width <= width / 2) {
                mPopupPreviewX += (key.width * 2.5).toInt()
            } else {
                mPopupPreviewX -= (key.width * 2.5).toInt()
            }
            mPopupPreviewY += popupHeight
        }
        if (previewPopup.isShowing) {
            // previewPopup.update(mPopupPreviewX, mPopupPreviewY, popupWidth, popupHeight);
            previewPopup.dismiss() // 禁止窗口動畫
        }
        previewPopup.width = popupWidth
        previewPopup.height = popupHeight
        previewPopup.showAtLocation(mPopupParent, Gravity.NO_GRAVITY, mPopupPreviewX, mPopupPreviewY)
        mPreviewText.visibility = VISIBLE
    }

    /**
     * Requests a redraw of the entire keyboard. Calling [.invalidate] is not sufficient because
     * the keyboard renders the keys to an off-screen buffer and an invalidate() only draws the cached
     * buffer.
     *
     * @see .invalidateKey
     */
    fun invalidateAllKeys() {
        Timber.d("\t<TrimeInput>\tinvalidateAllKeys()")
        mDirtyRect.union(0, 0, width, height)
        mDrawPending = true
        invalidate()
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only one
     * key is changing it's content. Any changes that affect the position or size of the key may not
     * be honored.
     *
     * @param keyIndex the index of the key in the attached [Keyboard].
     * @see .invalidateAllKeys
     */
    private fun invalidateKey(keyIndex: Int) {
        Timber.d(
            "\t<TrimeInput>\tinvalidateKey()\tkeyIndex=%d, mKeysExist=%s",
            keyIndex,
            mKeys != null,
        )
        if (mKeys == null) return
        if (keyIndex < 0 || keyIndex >= mKeys!!.size) {
            return
        }
        val key = mKeys!![keyIndex]
        mInvalidatedKey = key
        mDirtyRect.union(
            key.x + paddingLeft,
            key.y + paddingTop,
            key.x + key.width + paddingLeft,
            key.y + key.height + paddingTop,
        )
        onBufferDraw()
        Timber.d("\t<TrimeInput>\tinvalidateKey()\tinvalidate")
        invalidate(
            key.x + paddingLeft,
            key.y + paddingTop,
            key.x + key.width + paddingLeft,
            key.y + key.height + paddingTop,
        )
        Timber.d("\t<TrimeInput>\tinvalidateKey()\tfinish")
    }

    private fun invalidateKeys(keys: List<Key>?) {
        if (keys.isNullOrEmpty()) return
        for (key in keys) {
            mDirtyRect.union(
                key.x + paddingLeft,
                key.y + paddingTop,
                key.x + key.width + paddingLeft,
                key.y + key.height + paddingTop,
            )
        }
        onBufferDraw()
        invalidate()
    }

    fun invalidateComposingKeys() {
        if (mKeyboard != null) {
            val keys: List<Key> = mKeyboard!!.composingKeys
            if (keys != null && keys.size > 5) invalidateAllKeys() else invalidateKeys(keys)
        } else {
            Timber.e("invalidateComposingKeys() mKeyboard==null")
        }
    }

    private fun openPopupIfRequired(me: MotionEvent): Boolean {
        // Check if we have a popup layout specified first.
        if (mPopupLayout == 0) {
            return false
        }
        if (mCurrentKey < 0 || mCurrentKey >= mKeys!!.size) {
            return false
        }
        showPreview(NOT_A_KEY)
        showPreview(mCurrentKey, KeyEventType.LONG_CLICK.ordinal)
        val popupKey = mKeys!![mCurrentKey]
        val result = onLongPress(popupKey)
        if (result) {
            mAbortKey = true
            showPreview(NOT_A_KEY)
        }
        return result
    }

    /**
     * Called when a key is long pressed. By default this will open any popup keyboard associated with
     * this key through the attributes popupLayout and popupCharacters.
     *
     * @param popupKey the key that was long pressed
     * @return true if the long press is handled, false otherwise. Subclasses should call the method
     * on the base class if the subclass doesn't wish to handle the call.
     */
    private fun onLongPress(popupKey: Key): Boolean {
        val popupKeyboardId = popupKey.popupResId
        if (popupKeyboardId == 0) {
            if (popupKey.longClick != null) {
                removeMessages()
                mAbortKey = true
                val e = popupKey.longClick
                onKeyboardActionListener!!.onEvent(e)
                releaseKey(e!!.code)
                resetModifer()
                return true
            }
            Timber.w("only set isShifted, no others modifierkey")
            if (popupKey.isShift && !popupKey.sendBindings(KeyEventType.LONG_CLICK.ordinal)) {
                // todo 其他修饰键
                setShifted(!popupKey.isOn, !popupKey.isOn)
                return true
            }
            return false
        }

        var mMiniKeyboardContainer = mMiniKeyboardCache[popupKey]
        val mMiniKeyboard: KeyboardView
        if (mMiniKeyboardContainer == null) {
            mMiniKeyboardContainer = this.layoutInflater.inflate(mPopupLayout, null)
            mMiniKeyboard = mMiniKeyboardContainer.findViewById(android.R.id.keyboardView)
            val closeButton = mMiniKeyboardContainer.findViewById<View>(android.R.id.closeButton)
            closeButton?.setOnClickListener(this)
            mMiniKeyboard.onKeyboardActionListener =
                object : OnKeyboardActionListener {
                    override fun onEvent(event: Event?) {
                        onKeyboardActionListener!!.onEvent(event)
                        dismissPopupKeyboard()
                    }

                    override fun onKey(
                        primaryCode: Int,
                        mask: Int,
                    ) {
                        onKeyboardActionListener!!.onKey(primaryCode, mask)
                        dismissPopupKeyboard()
                    }

                    override fun onText(text: CharSequence?) {
                        onKeyboardActionListener!!.onText(text)
                        dismissPopupKeyboard()
                    }

                    override fun onPress(primaryCode: Int) {
                        Timber.d("\t<TrimeInput>\tonLongPress() onPress key=$primaryCode")
                        onKeyboardActionListener!!.onPress(primaryCode)
                    }

                    override fun onRelease(primaryCode: Int) {
                        onKeyboardActionListener!!.onRelease(primaryCode)
                    }
                }
            // mInputView.setSuggest(mSuggest);
            val keyboard =
                if (popupKey.popupCharacters != null) {
                    Keyboard(popupKey.popupCharacters, -1, paddingLeft + paddingRight)
                } else {
                    Keyboard()
                }
            mMiniKeyboard.keyboard = keyboard
            mMiniKeyboard.mPopupParent = this
            mMiniKeyboardContainer.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST),
            )
            mMiniKeyboardCache[popupKey] = mMiniKeyboardContainer
        } else {
            mMiniKeyboard = mMiniKeyboardContainer.findViewById(android.R.id.keyboardView)
        }
        getLocationInWindow(mCoordinates)
        var mPopupX = popupKey.x + paddingLeft
        var mPopupY = popupKey.y + paddingTop
        mPopupX = mPopupX + popupKey.width - mMiniKeyboardContainer!!.measuredWidth
        mPopupY = mPopupY - mMiniKeyboardContainer.measuredHeight
        val x = mPopupX + mMiniKeyboardContainer.paddingRight + mCoordinates[0]
        val y = mPopupY + mMiniKeyboardContainer.paddingBottom + mCoordinates[1]
        mMiniKeyboard.setPopupOffset(Math.max(x, 0), y)

        // todo 只处理了shift
        Timber.w("only set isShifted, no others modifierkey")
        mMiniKeyboard.setShifted(false, isShifted)
        mPopupKeyboard.contentView = mMiniKeyboardContainer
        mPopupKeyboard.width = mMiniKeyboardContainer.measuredWidth
        mPopupKeyboard.height = mMiniKeyboardContainer.measuredHeight
        mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        mMiniKeyboardOnScreen = true
        // mMiniKeyboard.onTouchEvent(getTranslatedEvent(me));
        invalidateAllKeys()
        return true
    }

    /*
  @Override
  public boolean onHoverEvent(MotionEvent event) {
      if (mAccessibilityManager.isTouchExplorationEnabled() && event.getPointerCount() == 1) {
          final int action = event.getAction();
          switch (action) {
              case MotionEvent.ACTION_HOVER_ENTER: {
                  event.setAction(MotionEvent.ACTION_DOWN);
              } break;
              case MotionEvent.ACTION_HOVER_MOVE: {
                  event.setAction(MotionEvent.ACTION_MOVE);
              } break;
              case MotionEvent.ACTION_HOVER_EXIT: {
                  event.setAction(MotionEvent.ACTION_UP);
              } break;
          }
          return onTouchEvent(event);
      }
      return true;
  }
     */
    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        // Convert multi-pointer up/down events to single up/down events to
        // deal with the typical multi-pointer behavior of two-thumb typing
        val index = me.actionIndex
        val pointerCount = me.pointerCount
        val action = me.actionMasked
        var result = false
        val now = me.eventTime
        mComboMode = false
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_CANCEL) {
            mComboCount = 0
            isFastInput = if (prefs.keyboard.hookFastInput) prefs.keyboard.swipeTimeHi > me.eventTime - mLastUpTime else false
        } else if (pointerCount > 1 || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            mComboMode = true
        }
        if (action == MotionEvent.ACTION_UP) {
            Timber.d("swipeDebug.onTouchEvent ?, action = ACTION_UP")
        }
        if (action == MotionEvent.ACTION_POINTER_UP || mOldPointerCount > 1 && action == MotionEvent.ACTION_UP) {
            // 並擊鬆開前的虛擬按鍵事件
            val ev =
                MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_POINTER_DOWN,
                    me.getX(index),
                    me.getY(index),
                    me.metaState,
                )
            result = onModifiedTouchEvent(ev, false)
            ev.recycle()
            Timber.d("\t<TrimeInput>\tonTouchEvent()\tactionUp done")
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // 並擊中的按鍵事件，需要按鍵提示
            val ev =
                MotionEvent.obtain(
                    now,
                    now,
                    MotionEvent.ACTION_DOWN,
                    me.getX(index),
                    me.getY(index),
                    me.metaState,
                )
            result = onModifiedTouchEvent(ev, false)
            ev.recycle()
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tactionDown done")
        } else {
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tonModifiedTouchEvent")
            result = onModifiedTouchEvent(me, false)
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tnot actionDown done")
        }
        if (action != MotionEvent.ACTION_MOVE) mOldPointerCount = pointerCount
        performClick()
        return result
    }

    private fun onModifiedTouchEvent(
        me: MotionEvent,
        possiblePoly: Boolean,
    ): Boolean {
        // final int pointerCount = me.getPointerCount();
        val index = me.actionIndex
        var touchX = me.getX(index).toInt() - paddingLeft
        var touchY = me.getY(index).toInt() - paddingTop
        if (touchY >= -mVerticalCorrection) touchY += mVerticalCorrection
        val action = me.actionMasked
        val eventTime = me.eventTime
        val keyIndex = getKeyIndices(touchX, touchY, null)
        mPossiblePoly = possiblePoly

        // Track the last few movements to look for spurious swipes.
        if (action == MotionEvent.ACTION_DOWN) mSwipeTracker.clear()
        mSwipeTracker.addMovement(me)
        if (action == MotionEvent.ACTION_CANCEL) {
            Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action = cancel")
        } else if (action == MotionEvent.ACTION_UP) {
            Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action = UP")
        } else {
            Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action != UP")
        }

        // Ignore all motion events until a DOWN.
        if (mAbortKey && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
            return true
        }

        // 优先判定是否触发了滑动手势
        if (prefs.keyboard.swipeEnabled) {
            if (mGestureDetector!!.onTouchEvent(me)) {
                showPreview(NOT_A_KEY)
                mHandler.removeMessages(MSG_REPEAT)
                mHandler.removeMessages(MSG_LONGPRESS)
                return true
            }
        }

        // Needs to be called after the gesture detector gets a turn, as it may have
        // displayed the mini keyboard
        if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
            return true
        }

        fun modifiedPointerDown() {
            mAbortKey = false
            mStartX = touchX
            mStartY = touchY
            mLastCodeX = touchX
            mLastCodeY = touchY
            mLastKeyTime = 0
            mCurrentKeyTime = 0
            mLastKey = NOT_A_KEY
            mCurrentKey = keyIndex
            mDownKey = keyIndex
            mDownTime = me.eventTime
            mLastMoveTime = mDownTime
            touchOnePoint = false
            if (action == MotionEvent.ACTION_POINTER_DOWN) return // 並擊鬆開前的虛擬按鍵事件
            checkMultiTap(eventTime, keyIndex)
            onKeyboardActionListener!!.onPress(if (keyIndex != NOT_A_KEY) mKeys!![keyIndex].code else 0)
            if (mCurrentKey >= 0 && mKeys!![mCurrentKey].click!!.isRepeatable) {
                mRepeatKeyIndex = mCurrentKey
                val msg = mHandler.obtainMessage(MSG_REPEAT)
                val repeatStartDelay = prefs.keyboard.longPressTimeout + 1
                mHandler.sendMessageDelayed(msg, repeatStartDelay.toLong())
                // Delivering the key could have caused an abort
                if (mAbortKey) {
                    mRepeatKeyIndex = NOT_A_KEY
                    return
                }
            }
            if (mCurrentKey != NOT_A_KEY) {
                val msg = mHandler.obtainMessage(MSG_LONGPRESS, me)
                mHandler.sendMessageDelayed(msg, prefs.keyboard.longPressTimeout.toLong())
            }
            showPreview(keyIndex, 0)
        }

        /**
         * @return 跳出外层函数
         */
        fun modifiedPointerUp(): Boolean {
            removeMessages()
            mLastUpTime = eventTime
            if (keyIndex == mCurrentKey) {
                mCurrentKeyTime += eventTime - mLastMoveTime
            } else {
                resetMultiTap()
                mLastKey = mCurrentKey
                mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime
                mCurrentKey = keyIndex
                mCurrentKeyTime = 0
            }
            if (prefs.keyboard.swipeEnabled) {
                val dx = touchX - touchX0
                val dy = touchY - touchY0
                val absX = Math.abs(dx)
                val absY = Math.abs(dy)
                val travel = if (isFastInput && isClickAtLast) prefs.keyboard.swipeTravelHi else prefs.keyboard.swipeTravel
                if (Math.max(absY, absX) > travel && touchOnePoint) {
                    Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\ttouch")
                    var type = KeyEventType.CLICK
                    type =
                        if (absX < absY) {
                            Timber.d("swipeDebug.ext y, dX=%d, dY=%d", dx, dy)
                            if (dy > travel) KeyEventType.SWIPE_DOWN else KeyEventType.SWIPE_UP
                        } else {
                            Timber.d("swipeDebug.ext x, dX=%d, dY=%d", dx, dy)
                            if (dx > travel) KeyEventType.SWIPE_RIGHT else KeyEventType.SWIPE_LEFT
                        }
                    showPreview(NOT_A_KEY)
                    mHandler.removeMessages(MSG_REPEAT)
                    mHandler.removeMessages(MSG_LONGPRESS)
                    detectAndSendKey(mDownKey, mStartX, mStartY, me.eventTime, type)
                    isClickAtLast = false
                    return true
                } else {
                    Timber.d("swipeDebug.ext fail, dX=%d, dY=%d", dx, dy)
                }
            }
            if (mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < DEBOUNCE_TIME && mLastKey != NOT_A_KEY) {
                mCurrentKey = mLastKey
                touchX = mLastCodeX
                touchY = mLastCodeY
            }
            showPreview(NOT_A_KEY)
            Arrays.fill(mKeyIndices, NOT_A_KEY)
            if (mRepeatKeyIndex != NOT_A_KEY && !mAbortKey) repeatKey()
            if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
                Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tdetectAndSendKey")
                detectAndSendKey(
                    mCurrentKey,
                    touchX,
                    touchY,
                    eventTime,
                    if (mOldPointerCount > 1 || mComboMode) KeyEventType.COMBO else KeyEventType.CLICK,
                )
                isClickAtLast = true
            }
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tdetectAndSendKey finish")
            invalidateKey(keyIndex)
            mRepeatKeyIndex = NOT_A_KEY
            return false
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchX0 = touchX
                touchY0 = touchY
                touchOnePoint = true
                modifiedPointerDown()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                modifiedPointerDown()
            }

            MotionEvent.ACTION_MOVE -> {
                var continueLongPress = false
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex
                        mCurrentKeyTime = eventTime - mDownTime
                    } else {
                        if (keyIndex == mCurrentKey) {
                            mCurrentKeyTime += eventTime - mLastMoveTime
                            continueLongPress = true
                        } else if (mRepeatKeyIndex == NOT_A_KEY) {
                            resetMultiTap()
                            mLastKey = mCurrentKey
                            mLastCodeX = mLastX
                            mLastCodeY = mLastY
                            mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime
                            mCurrentKey = keyIndex
                            mCurrentKeyTime = 0
                        }
                    }
                }
                if (!mComboMode && !continueLongPress) {
                    // Cancel old long press
                    mHandler.removeMessages(MSG_LONGPRESS)
                    // Start new long press if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        val msg = mHandler.obtainMessage(MSG_LONGPRESS, me)
                        mHandler.sendMessageDelayed(msg, prefs.keyboard.longPressTimeout.toLong())
                    }
                }
                showPreview(mCurrentKey)
                mLastMoveTime = eventTime
            }

            MotionEvent.ACTION_UP -> {
                Timber.d(
                    "swipeDebug.onModifiedTouchEvent mGestureDetector.onTouchEvent(me) = fall & action_up",
                )
                val breakout = modifiedPointerUp()
                if (breakout) return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val breakout = modifiedPointerUp()
                if (breakout) return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeMessages()
                dismissPopupKeyboard()
                mAbortKey = true
                showPreview(NOT_A_KEY)
                invalidateKey(mCurrentKey)
            }
        }
        mLastX = touchX
        mLastY = touchY
        return true
    }

    private fun repeatKey(): Boolean {
        Timber.d("\t<TrimeInput>\trepeatKey()")
        val key = mKeys!![mRepeatKeyIndex]
        detectAndSendKey(mCurrentKey, key.x, key.y, mLastTapTime)
        return true
    }

    fun finishInput() {
        if (mPreviewPopup.isShowing) {
            mPreviewPopup.dismiss()
        }
        removeMessages()
        dismissPopupKeyboard()
        mBuffer = null
        mCanvas = null
        mMiniKeyboardCache.clear()
    }

    private fun removeMessages() {
        mHandler.removeMessages(MSG_REPEAT)
        mHandler.removeMessages(MSG_LONGPRESS)
        mHandler.removeMessages(MSG_SHOW_PREVIEW)
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        finishInput()
    }

    private fun dismissPopupKeyboard() {
        if (mPopupKeyboard.isShowing) {
            mPopupKeyboard.dismiss()
            mMiniKeyboardOnScreen = false
            invalidateAllKeys()
        }
    }

    fun handleBack(): Boolean {
        if (mPopupKeyboard.isShowing) {
            dismissPopupKeyboard()
            return true
        }
        return false
    }

    private fun resetMultiTap() {
        mLastSentIndex = NOT_A_KEY
        // final int mTapCount = 0;
        mLastTapTime = -1
        // final boolean mInMultiTap = false;
    }

    private fun checkMultiTap(
        eventTime: Long,
        keyIndex: Int,
    ) {
        if (keyIndex == NOT_A_KEY) return
        // final Key key = mKeys[keyIndex];
        val multiTabInterval = prefs.keyboard.longPressTimeout
        if (eventTime > mLastTapTime + multiTabInterval || keyIndex != mLastSentIndex) {
            resetMultiTap()
        }
    }

    /** 識別滑動手勢  */
    private class SwipeTracker {
        val mPastX = FloatArray(NUM_PAST)
        val mPastY = FloatArray(NUM_PAST)
        val mPastTime = LongArray(NUM_PAST)
        var yVelocity = 0f
        var xVelocity = 0f

        fun clear() {
            mPastTime[0] = 0
        }

        fun addMovement(ev: MotionEvent) {
            val time = ev.eventTime
            val N = ev.historySize
            for (i in 0 until N) {
                addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalEventTime(i))
            }
            addPoint(ev.x, ev.y, time)
        }

        private fun addPoint(
            x: Float,
            y: Float,
            time: Long,
        ) {
            var drop = -1
            val pastTime = mPastTime
            var i = 0
            while (i < NUM_PAST) {
                if (pastTime[i] == 0L) {
                    break
                } else if (pastTime[i] < time - LONGEST_PAST_TIME) {
                    drop = i
                }
                i++
            }
            if (i == NUM_PAST && drop < 0) {
                drop = 0
            }
            if (drop == i) drop--
            val pastX = mPastX
            val pastY = mPastY
            if (drop >= 0) {
                val start = drop + 1
                val count = NUM_PAST - drop - 1
                System.arraycopy(pastX, start, pastX, 0, count)
                System.arraycopy(pastY, start, pastY, 0, count)
                System.arraycopy(pastTime, start, pastTime, 0, count)
                i -= drop + 1
            }
            pastX[i] = x
            pastY[i] = y
            pastTime[i] = time
            i++
            if (i < NUM_PAST) {
                pastTime[i] = 0
            }
        }

        @JvmOverloads
        fun computeCurrentVelocity(
            units: Int,
            maxVelocity: Float = Float.MAX_VALUE,
        ) {
            val pastX = mPastX
            val pastY = mPastY
            val pastTime = mPastTime
            val oldestX = pastX[0]
            val oldestY = pastY[0]
            val oldestTime = pastTime[0]
            var accumX = 0f
            var accumY = 0f
            var N = 0
            while (N < NUM_PAST) {
                if (pastTime[N] == 0L) {
                    break
                }
                N++
            }
            for (i in 1 until N) {
                val dur = (pastTime[i] - oldestTime).toInt()
                if (dur == 0) continue
                var dist = pastX[i] - oldestX
                var vel = dist / dur * units // pixels/frame.
                accumX = if (accumX == 0f) vel else (accumX + vel) * .5f
                dist = pastY[i] - oldestY
                vel = dist / dur * units // pixels/frame.
                accumY = if (accumY == 0f) vel else (accumY + vel) * .5f
            }
            xVelocity = if (accumX < 0.0f) Math.max(accumX, -maxVelocity) else Math.min(accumX, maxVelocity)
            yVelocity = if (accumY < 0.0f) Math.max(accumY, -maxVelocity) else Math.min(accumY, maxVelocity)
        }

        companion object {
            const val NUM_PAST = 4
            const val LONGEST_PAST_TIME = 200
        }
    }

    companion object {
        private const val DEBUG = false
        private const val NOT_A_KEY = -1
        private val LONG_PRESSABLE_STATE_SET = intArrayOf(android.R.attr.state_long_pressable)
        private const val MSG_SHOW_PREVIEW = 1
        private const val MSG_REMOVE_PREVIEW = 2
        private const val MSG_REPEAT = 3
        private const val MSG_LONGPRESS = 4
        private const val DELAY_BEFORE_PREVIEW = 0
        private const val DELAY_AFTER_PREVIEW = 70
        private const val DEBOUNCE_TIME = 70
        private const val MAX_NEARBY_KEYS = 12
        private val prefs: AppPrefs
            get() = defaultInstance()
    }
}
