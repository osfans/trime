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

package com.osfans.trime.ime.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.osfans.trime.R;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.data.Config;
import com.osfans.trime.databinding.KeyboardKeyPreviewBinding;
import com.osfans.trime.ime.enums.KeyEventType;
import com.osfans.trime.ime.lifecycle.CoroutineScopeJava;
import com.osfans.trime.util.LeakGuardHandlerWrapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import timber.log.Timber;

/** 顯示{@link Keyboard 鍵盤}及{@link Key 按鍵} */
public class KeyboardView extends View implements View.OnClickListener, CoroutineScope {

  @NonNull
  @Override
  public CoroutineContext getCoroutineContext() {
    return CoroutineScopeJava.getMainScopeJava().getCoroutineContext();
  }

  /** 處理按鍵、觸摸等輸入事件 */
  public interface OnKeyboardActionListener {

    /**
     * Called when the user presses a key. This is sent before the {@link #onKey} is called. For
     * keys that repeat, this is only called once.
     *
     * @param primaryCode the unicode of the key being pressed. If the touch is not on a valid key,
     *     the value will be zero.
     */
    void onPress(final int primaryCode);

    /**
     * Called when the user releases a key. This is sent after the {@link #onKey} is called. For
     * keys that repeat, this is only called once.
     *
     * @param primaryCode the code of the key that was released
     */
    void onRelease(final int primaryCode);

    void onEvent(final Event event);

    /**
     * Send a key press to the listener.
     *
     * @param primaryCode this is the key that was pressed
     * @param mask the codes for all the possible alternative keys with the primary code being the
     *     first. If the primary key code is a single character such as an alphabet or number or
     *     symbol, the alternatives will include other characters that may be on the same key or
     *     adjacent keys. These codes are useful to correct for accidental presses of a key adjacent
     *     to the intended key.
     */
    void onKey(int primaryCode, int mask);

    /**
     * Sends a sequence of characters to the listener.
     *
     * @param text the sequence of characters to be displayed.
     */
    void onText(final CharSequence text);
  }

  private static final boolean DEBUG = false;
  private static final int NOT_A_KEY = -1;
  private static final int[] LONG_PRESSABLE_STATE_SET = {android.R.attr.state_long_pressable};

  private Keyboard mKeyboard;
  private int mCurrentKeyIndex = NOT_A_KEY;
  private int mLabelTextSize;
  private int mKeyTextSize;
  private ColorStateList mKeyTextColor;
  private StateListDrawable mKeyBackColor;
  private int key_symbol_color, hilited_key_symbol_color;
  private int mSymbolSize;
  private final Paint mPaintSymbol;
  private float mShadowRadius;
  private int mShadowColor;
  private float mBackgroundDimAmount;
  // private Drawable mBackground;

  private final TextView mPreviewText;
  private final PopupWindow mPreviewPopup;
  private int mPreviewOffset;
  private int mPreviewHeight;
  // Working variable
  private final int[] mCoordinates = new int[2];

  private final PopupWindow mPopupKeyboard;
  private boolean mMiniKeyboardOnScreen;
  private View mPopupParent;
  private int mMiniKeyboardOffsetX;
  private int mMiniKeyboardOffsetY;
  private final Map<Key, View> mMiniKeyboardCache;
  private Key[] mKeys;

  /** Listener for {@link OnKeyboardActionListener}. */
  private OnKeyboardActionListener mKeyboardActionListener;

  private static final int MSG_SHOW_PREVIEW = 1;
  private static final int MSG_REMOVE_PREVIEW = 2;
  private static final int MSG_REPEAT = 3;
  private static final int MSG_LONGPRESS = 4;

  private static final int DELAY_BEFORE_PREVIEW = 0;
  private static final int DELAY_AFTER_PREVIEW = 70;
  private static final int DEBOUNCE_TIME = 70;

  private int mVerticalCorrection;
  private int mProximityThreshold;

  private boolean mShowPreview = true;

  private int mLastX;
  private int mLastY;
  private int mStartX;
  private int mStartY;
  private int touchX0, touchY0;
  private boolean touchOnePoint;

  private boolean mProximityCorrectOn;

  private final Paint mPaint;
  private final Rect mPadding;

  private long mDownTime;
  private long mLastMoveTime;
  private int mLastKey;
  private int mLastCodeX;
  private int mLastCodeY;
  private int mCurrentKey = NOT_A_KEY;
  private int mDownKey = NOT_A_KEY;
  private long mLastKeyTime;
  private long mCurrentKeyTime;
  private long mLastUpTime;
  private boolean isFastInput;
  private boolean isClickAtLast;
  private final int[] mKeyIndices = new int[12];
  private GestureDetector mGestureDetector;
  private int mRepeatKeyIndex = NOT_A_KEY;
  private final int mPopupLayout;
  private boolean mAbortKey;
  private Key mInvalidatedKey;
  private final Rect mClipRegion = new Rect(0, 0, 0, 0);
  private boolean mPossiblePoly;
  private final SwipeTracker mSwipeTracker = new SwipeTracker();
  private final boolean mDisambiguateSwipe;

  // Variables for dealing with multiple pointers
  private int mOldPointerCount = 1;
  private final int[] mComboCodes = new int[10];
  private int mComboCount = 0;
  private boolean mComboMode = false;

  private static int REPEAT_INTERVAL = 50; // ~20 keys per second
  private static int REPEAT_START_DELAY = 400;
  private static int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

  private static final int MAX_NEARBY_KEYS = 12;
  private final int[] mDistances = new int[MAX_NEARBY_KEYS];

  // For multi-tap
  private int mLastSentIndex;
  private long mLastTapTime;
  private static int MULTI_TAP_INTERVAL = 800; // milliseconds
  private final StringBuilder mPreviewLabel = new StringBuilder(1);

  /** Whether the keyboard bitmap needs to be redrawn before it's blitted. * */
  private boolean mDrawPending;
  /** The dirty region in the keyboard bitmap */
  private final Rect mDirtyRect = new Rect();
  /** The keyboard bitmap for faster updates */
  private Bitmap mBuffer;
  /** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer. */
  private boolean mKeyboardChanged;
  /** The canvas for the above mutable keyboard bitmap */
  private Canvas mCanvas;
  // The accessibility manager for accessibility support */
  // private AccessibilityManager mAccessibilityManager;
  // The audio manager for accessibility support */
  // private AudioManager mAudioManager;
  /**
   * Whether the requirement of a headset to hear passwords if accessibility is enabled is
   * announced.
   */
  private boolean mHeadsetRequiredToHearPasswordsAnnounced;

  private boolean mShowHint = true, mShowSymbol = true;

  private Method findStateDrawableIndex;
  private Method getStateDrawable;

  private String labelEnter = "";
  private Map<String, String> mEnterLabels;
  private int enterLabelMode;

  public void resetEnterLabel() {
    labelEnter = mEnterLabels.get("default");
  }

  public void setEnterLabel(int action, CharSequence actionLabel) {
    // enter_label_mode 取值：
    // 0不使用，1只使用actionlabel，2优先使用，3当其他方式没有获得label时才读取actionlabel

    if (enterLabelMode == 1) {
      if (actionLabel != null && actionLabel.length() > 0) labelEnter = actionLabel.toString();
      else labelEnter = mEnterLabels.get("default");
      return;
    }

    if (enterLabelMode == 2) {
      if (actionLabel != null && actionLabel.length() > 0) {
        labelEnter = actionLabel.toString();
        return;
      }
    }

    switch (action) {
      case EditorInfo.IME_ACTION_DONE:
        labelEnter = mEnterLabels.get("done");
        break;
      case EditorInfo.IME_ACTION_GO:
        labelEnter = mEnterLabels.get("go");
        break;
      case EditorInfo.IME_ACTION_NEXT:
        labelEnter = mEnterLabels.get("next");
        break;
      case EditorInfo.IME_ACTION_PREVIOUS:
        labelEnter = mEnterLabels.get("pre");
        break;
      case EditorInfo.IME_ACTION_SEARCH:
        labelEnter = mEnterLabels.get("search");
        break;
      case EditorInfo.IME_ACTION_SEND:
        labelEnter = mEnterLabels.get("send");
        break;
      case EditorInfo.IME_ACTION_NONE:
        labelEnter = mEnterLabels.get("none");
      default:
        if (enterLabelMode == 3) {
          if (actionLabel != null && actionLabel.length() > 0) {
            labelEnter = actionLabel.toString();
            return;
          }
        }
        labelEnter = mEnterLabels.get("default");
    }
  }

  @NonNull
  private AppPrefs getPrefs() {
    return AppPrefs.Companion.defaultInstance();
  }

  private final MyHandler mHandler = new MyHandler(this);

  private static class MyHandler extends LeakGuardHandlerWrapper<KeyboardView> {

    public MyHandler(@NonNull final KeyboardView view) {
      super(view);
    }

    @Override
    public void handleMessage(Message msg) {
      final KeyboardView mKeyboardView = getOwnerInstanceOrNull();
      if (mKeyboardView == null) return;
      switch (msg.what) {
        case MSG_SHOW_PREVIEW:
          mKeyboardView.showKey(msg.arg1, msg.arg2);
          break;
        case MSG_REMOVE_PREVIEW:
          mKeyboardView.mPreviewText.setVisibility(INVISIBLE);
          break;
        case MSG_REPEAT:
          if (mKeyboardView.repeatKey()) {
            Message repeat = Message.obtain(this, MSG_REPEAT);
            sendMessageDelayed(repeat, REPEAT_INTERVAL);
          }
          break;
        case MSG_LONGPRESS:
          mKeyboardView.openPopupIfRequired((MotionEvent) msg.obj);
          break;
      }
    }
  }

  public void setShowHint(final boolean value) {
    mShowHint = value;
  }

  public void setShowSymbol(final boolean value) {
    mShowSymbol = value;
  }

  public void reset(final Context context) {
    final Config config = Config.get(context);
    key_symbol_color = config.getColor("key_symbol_color");
    hilited_key_symbol_color = config.getColor("hilited_key_symbol_color");
    mShadowColor = config.getColor("shadow_color");

    mSymbolSize = config.getPixel("symbol_text_size", 10);
    mKeyTextSize = config.getPixel("key_text_size", 22);
    mVerticalCorrection = config.getPixel("vertical_correction");
    setProximityCorrectionEnabled(config.getBoolean("proximity_correction"));
    mPreviewOffset = config.getPixel("preview_offset");
    mPreviewHeight = config.getPixel("preview_height");
    mLabelTextSize = config.getPixel("key_long_text_size");
    if (mLabelTextSize == 0) mLabelTextSize = mKeyTextSize;

    mBackgroundDimAmount = config.getFloat("background_dim_amount");
    mShadowRadius = config.getFloat("shadow_radius");
    final float mRoundCorner = config.getFloat("round_corner");

    mKeyBackColor = new StateListDrawable();
    mKeyBackColor.addState(
        Key.KEY_STATE_PRESSED_ON, config.getColorDrawable("hilited_on_key_back_color"));
    mKeyBackColor.addState(
        Key.KEY_STATE_PRESSED_OFF, config.getColorDrawable("hilited_off_key_back_color"));
    mKeyBackColor.addState(Key.KEY_STATE_NORMAL_ON, config.getColorDrawable("on_key_back_color"));
    mKeyBackColor.addState(Key.KEY_STATE_NORMAL_OFF, config.getColorDrawable("off_key_back_color"));
    mKeyBackColor.addState(
        Key.KEY_STATE_PRESSED, config.getColorDrawable("hilited_key_back_color"));
    mKeyBackColor.addState(Key.KEY_STATE_NORMAL, config.getColorDrawable("key_back_color"));

    mKeyTextColor =
        new ColorStateList(
            Key.KEY_STATES,
            new int[] {
              config.getColor("hilited_on_key_text_color"),
              config.getColor("hilited_off_key_text_color"),
              config.getColor("on_key_text_color"),
              config.getColor("off_key_text_color"),
              config.getColor("hilited_key_text_color"),
              config.getColor("key_text_color")
            });

    final Integer color = config.getColor("preview_text_color");
    if (color != null) mPreviewText.setTextColor(color);
    final Integer previewBackColor = config.getColor("preview_back_color");
    if (previewBackColor != null) {
      final GradientDrawable background = new GradientDrawable();
      background.setColor(previewBackColor);
      background.setCornerRadius(mRoundCorner);
      mPreviewText.setBackground(background);
    }
    final int mPreviewTextSizeLarge = config.getInt("preview_text_size");
    mPreviewText.setTextSize(mPreviewTextSizeLarge);
    mShowPreview = getPrefs().getKeyboard().getPopupKeyPressEnabled();

    mPaint.setTypeface(config.getFont("key_font"));
    mPaintSymbol.setTypeface(config.getFont("symbol_font"));
    mPaintSymbol.setColor(key_symbol_color);
    mPaintSymbol.setTextSize(mSymbolSize);
    mPreviewText.setTypeface(config.getFont("preview_font"));

    REPEAT_INTERVAL = config.getRepeatInterval();
    REPEAT_START_DELAY = config.getLongTimeout() + 1;
    LONG_PRESS_TIMEOUT = config.getLongTimeout();
    MULTI_TAP_INTERVAL = config.getLongTimeout();

    mEnterLabels = config.getmEnterLabels();
    enterLabelMode = config.getInt("enter_label_mode");
    invalidateAllKeys();
  }

  public KeyboardView(final Context context, final AttributeSet attrs) {
    super(context, attrs);

    try {
      findStateDrawableIndex =
          StateListDrawable.class.getMethod(
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                  ? "findStateDrawableIndex"
                  : "getStateDrawableIndex",
              int[].class);
      getStateDrawable = StateListDrawable.class.getMethod("getStateDrawable", int.class);
    } catch (Exception ex) {
      Timber.e(ex, "Get Drawable Exception");
    }

    mPreviewText = KeyboardKeyPreviewBinding.inflate(LayoutInflater.from(context)).getRoot();
    mPaint = new Paint();
    mPaint.setAntiAlias(true);
    mPaint.setTextAlign(Align.CENTER);
    mPaintSymbol = new Paint();
    mPaintSymbol.setAntiAlias(true);
    mPaintSymbol.setTextAlign(Align.CENTER);
    // reset(context);

    mPreviewPopup = new PopupWindow(context);
    mPreviewPopup.setContentView(mPreviewText);
    mPreviewPopup.setBackgroundDrawable(null);
    mPreviewPopup.setTouchable(false);

    mPopupLayout = R.layout.keyboard_popup_keyboard;
    mPopupKeyboard = new PopupWindow(context);
    mPopupKeyboard.setBackgroundDrawable(null);

    mPopupParent = this;
    mPadding = new Rect(0, 0, 0, 0);
    mMiniKeyboardCache = new HashMap<>();
    mDisambiguateSwipe = true;

    resetMultiTap();
    initGestureDetector();
  }

  private void initGestureDetector() {
    mGestureDetector =
        new GestureDetector(
            getContext(),
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onFling(
                  MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                if (mPossiblePoly) return false;
                final float absX = Math.abs(velocityX);
                final float absY = Math.abs(velocityY);
                float deltaX = me2.getX() - me1.getX();
                float deltaY = me2.getY() - me1.getY();
                final int travel =
                    (isFastInput && isClickAtLast)
                        ? getPrefs().getKeyboard().getSwipeTravelHi()
                        : getPrefs().getKeyboard().getSwipeTravel();
                final int velocity =
                    (isFastInput && isClickAtLast)
                        ? getPrefs().getKeyboard().getSwipeVelocity()
                        : getPrefs().getKeyboard().getSwipeVelocityHi();
                mSwipeTracker.computeCurrentVelocity(10);
                final float endingVelocityX = mSwipeTracker.getXVelocity();
                final float endingVelocityY = mSwipeTracker.getYVelocity();
                boolean sendDownKey = false;
                KeyEventType type = KeyEventType.CLICK;
                if ((deltaX > travel || velocityX > velocity) && absY < absX) {
                  if (mDisambiguateSwipe && endingVelocityX < velocityX / 4) {
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_RIGHT;
                  } else {
                    return true;
                  }
                } else if ((deltaX < -travel || velocityX < -velocity) && absY < absX) {
                  if (mDisambiguateSwipe && endingVelocityX > velocityX / 4) {
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_LEFT;
                  } else {
                    return true;
                  }
                } else if ((deltaY < -travel || velocityY < -velocity) && absX < absY) {
                  if (mDisambiguateSwipe && endingVelocityY > velocityY / 4) {
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_UP;
                  } else {
                    return true;
                  }
                } else if ((deltaY > travel || velocityY > velocity) && absX < absY) {
                  if (mDisambiguateSwipe && endingVelocityY < velocityY / 4) {
                    Timber.d(
                        "swipeDebug.onFling sendDownKey, dY=%f, vY=%f, eVY=%f, travel=%d, mSwipeThreshold=%d",
                        deltaY, velocityY, endingVelocityY, travel, velocity);
                    sendDownKey = true;
                    type = KeyEventType.SWIPE_DOWN;
                  } else {
                    return true;
                  }
                } else {
                  Timber.d(
                      "swipeDebug.onFling fail , dY=%f, vY=%f, eVY=%f, travel=%d",
                      deltaY, velocityY, endingVelocityY, travel);
                }

                if (sendDownKey) {
                  Timber.d("\t<TrimeInput>\tinitGestureDetector()\tsendDownKey");
                  showPreview(NOT_A_KEY);
                  showPreview(mDownKey, type.ordinal());
                  detectAndSendKey(mDownKey, mStartX, mStartY, me1.getEventTime(), type);
                  isClickAtLast = false;
                  return true;
                }
                return false;
              }
            });

    mGestureDetector.setIsLongpressEnabled(false);
  }

  public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
    mKeyboardActionListener = listener;
  }

  /**
   * Returns the {@link OnKeyboardActionListener} object.
   *
   * @return the listener attached to this keyboard
   */
  protected OnKeyboardActionListener getOnKeyboardActionListener() {
    return mKeyboardActionListener;
  }

  private void setKeyboardBackground() {
    if (mKeyboard == null) return;
    Drawable d = mPreviewText.getBackground();
    if (d instanceof GradientDrawable) {
      ((GradientDrawable) d).setCornerRadius(mKeyboard.getRoundCorner());
      mPreviewText.setBackground(d);
    }
    d = mKeyboard.getBackground();
    setBackground(d);
  }

  /**
   * Attaches a keyboard to this view. The keyboard can be switched at any time and the view will
   * re-layout itself to accommodate the keyboard.
   *
   * @see Keyboard
   * @see #getKeyboard()
   * @param keyboard the keyboard to display in this view
   */
  public void setKeyboard(Keyboard keyboard) {
    if (mKeyboard != null) {
      showPreview(NOT_A_KEY);
    }
    // Remove any pending messages
    removeMessages();
    mRepeatKeyIndex = NOT_A_KEY;
    mKeyboard = keyboard;
    List<Key> keys = mKeyboard.getKeys();
    mKeys = keys.toArray(new Key[keys.size()]);
    setKeyboardBackground();
    requestLayout();
    // Hint to reallocate the buffer if the size changed
    mKeyboardChanged = true;
    invalidateAllKeys();
    computeProximityThreshold(keyboard);
    mMiniKeyboardCache.clear(); // Not really necessary to do every time, but will free up views
    // Switching to a different keyboard should abort any pending keys so that the key up
    // doesn't get delivered to the old or new keyboard
    mAbortKey = true; // Until the next ACTION_DOWN
  }

  /**
   * Returns the current keyboard being displayed by this view.
   *
   * @return the currently attached keyboard
   * @see #setKeyboard(Keyboard)
   */
  public Keyboard getKeyboard() {
    return mKeyboard;
  }

  /**
   * 设置键盘修饰键的状态
   *
   * @param key 按下的修饰键
   * @return
   */
  public boolean setModifier(Key key) {
    if (mKeyboard != null) {
      if (mKeyboard.clikModifierKey(key.isShiftLock(), key.getModifierKeyOnMask())) {
        invalidateAllKeys();
        return true;
      }
    }
    return false;
  }

  /**
   * 設定鍵盤的Shift鍵狀態
   *
   * @param on 是否保持Shift按下狀態
   * @param shifted 是否按下Shift
   * @return Shift鍵狀態是否改變
   * @see Keyboard#setShifted(boolean, boolean) KeyboardView#isShifted()
   */
  public boolean setShifted(boolean on, boolean shifted) {
    if (mKeyboard != null) {
      // todo 扩展为设置全部修饰键的状态
      if (mKeyboard.setShifted(on, shifted)) {
        // The whole keyboard probably needs to be redrawn
        invalidateAllKeys();
        return true;
      }
    }
    return false;
  }

  private boolean resetShifted() {
    if (mKeyboard != null) {
      if (mKeyboard.resetShifted()) {
        // The whole keyboard probably needs to be redrawn
        invalidateAllKeys();
        return true;
      }
    }
    return false;
  }

  // 重置全部修饰键的状态
  private boolean resetModifer() {
    if (mKeyboard != null) {
      if (mKeyboard.resetModifer()) {
        // The whole keyboard probably needs to be redrawn
        invalidateAllKeys();
        return true;
      }
    }
    return false;
  }

  // 重置全部修饰键的状态(如果有锁定则不重置）
  private void refreshModifier() {
    if (mKeyboard != null) {
      if (mKeyboard.refreshModifier()) {
        invalidateAllKeys();
      }
    }
  }

  public boolean hasModifier() {
    if (mKeyboard != null) {
      return mKeyboard.hasModifier();
    }
    return false;
  }

  /**
   * Returns the state of the shift key of the keyboard, if any.
   *
   * @return true if the shift is in a pressed state, false otherwise. If there is no shift key on
   *     the keyboard or there is no keyboard attached, it returns false.
   * @see KeyboardView#setShifted(boolean, boolean)
   */
  public boolean isShifted() {
    if (mKeyboard != null) {
      return mKeyboard.isShifted();
    }
    return false;
  }

  /**
   * 返回鍵盤是否爲大寫狀態
   *
   * @return true 如果大寫
   */
  public boolean isCapsOn() {
    if (mKeyboard != null && mKeyboard.getmShiftKey() != null)
      return mKeyboard.getmShiftKey().isOn();
    return false;
  }

  public boolean isShiftOn() {
    if (mKeyboard != null && mKeyboard.getmShiftKey() != null)
      return mKeyboard.getmShiftKey().isOn();
    return false;
  }

  public boolean isAltOn() {
    if (mKeyboard != null && mKeyboard.getmAltKey() != null) return mKeyboard.getmAltKey().isOn();
    return false;
  }

  public boolean isSysOn() {
    if (mKeyboard != null && mKeyboard.getmSymKey() != null) return mKeyboard.getmSymKey().isOn();
    return false;
  }

  public boolean isCtrlOn() {
    if (mKeyboard != null && mKeyboard.getmCtrlKey() != null) return mKeyboard.getmCtrlKey().isOn();
    return false;
  }

  public boolean isMetaOn() {
    if (mKeyboard != null && mKeyboard.getmMetaKey() != null) return mKeyboard.getmMetaKey().isOn();
    return false;
  }

  /**
   * Enables or disables the key feedback popup. This is a popup that shows a magnified version of
   * the depressed key. By default the preview is enabled.
   *
   * @param previewEnabled whether or not to enable the key feedback popup
   * @see #isPreviewEnabled()
   */
  public void setPreviewEnabled(final boolean previewEnabled) {
    mShowPreview = previewEnabled;
  }

  /**
   * Returns the enabled state of the key feedback popup.
   *
   * @return whether or not the key feedback popup is enabled
   * @see #setPreviewEnabled(boolean)
   */
  public boolean isPreviewEnabled() {
    return mShowPreview;
  }

  // public void setVerticalCorrection(int verticalOffset) {}

  private void setPopupParent(final View v) {
    mPopupParent = v;
  }

  private void setPopupOffset(final int x, final int y) {
    mMiniKeyboardOffsetX = x;
    mMiniKeyboardOffsetY = y;
    if (mPreviewPopup.isShowing()) {
      mPreviewPopup.dismiss();
    }
  }

  /**
   * When enabled, calls to {@link OnKeyboardActionListener#onKey} will include key codes for
   * adjacent keys. When disabled, only the primary key code will be reported.
   *
   * @param enabled whether or not the proximity correction is enabled
   */
  private void setProximityCorrectionEnabled(boolean enabled) {
    mProximityCorrectOn = enabled;
  }

  /**
   * 檢查是否允許距離校正
   *
   * @return 是否允許距離校正
   */
  public boolean isProximityCorrectionEnabled() {
    return mProximityCorrectOn;
  }

  /**
   * 關閉彈出鍵盤
   *
   * @param v 鍵盤視圖
   */
  @Override
  public void onClick(final View v) {
    dismissPopupKeyboard();
  }

  @Override
  public void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
    // Round up a little
    if (mKeyboard == null) {
      setMeasuredDimension(
          getPaddingLeft() + getPaddingRight(), getPaddingTop() + getPaddingBottom());
    } else {
      int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
      if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
        width = MeasureSpec.getSize(widthMeasureSpec);
      }
      setMeasuredDimension(width, mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
    }
  }

  /**
   * 計算水平和豎直方向的相鄰按鍵中心的平均距離的平方，這樣不需要做開方運算
   *
   * @param keyboard 鍵盤
   */
  private void computeProximityThreshold(Keyboard keyboard) {
    if (keyboard == null) return;
    final Key[] keys = mKeys;
    if (keys == null) return;
    int length = keys.length;
    int dimensionSum = 0;
    for (Key key : keys) {
      dimensionSum += Math.min(key.getWidth(), key.getHeight()) + key.getGap();
    }
    if (dimensionSum < 0 || length == 0) return;
    mProximityThreshold = (int) (dimensionSum * Keyboard.SEARCH_DISTANCE / length);
    mProximityThreshold *= mProximityThreshold; // Square it
  }

  @Override
  public void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // if (mKeyboard != null) {
    // mKeyboard.resize(w, h);
    // }
    // Release the buffer, if any and it will be reallocated on the next draw
    mBuffer = null;
  }

  @Override
  public void onDraw(final Canvas canvas) {
    super.onDraw(canvas);
    if (mDrawPending || mBuffer == null || mKeyboardChanged) {
      onBufferDraw();
    }
    canvas.drawBitmap(mBuffer, 0, 0, null);
  }

  private void onBufferDraw() {
    if (mBuffer == null || mKeyboardChanged) {

      if (mBuffer == null
          || mKeyboardChanged
              && (mBuffer.getWidth() != getWidth() || mBuffer.getHeight() != getHeight())) {
        // Make sure our bitmap is at least 1x1
        final int width = Math.max(1, getWidth());
        final int height = Math.max(1, getHeight());
        mBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBuffer);
      }
      invalidateAllKeys();
      mKeyboardChanged = false;
    }

    if (mKeyboard == null) return;

    mCanvas.save();
    final Canvas canvas = mCanvas;
    canvas.clipRect(mDirtyRect);

    final Paint paint = mPaint;
    Drawable keyBackground;
    final Rect clipRegion = mClipRegion;
    final Rect padding = mPadding;
    final int kbdPaddingLeft = getPaddingLeft();
    final int kbdPaddingTop = getPaddingTop();
    final Key[] keys = mKeys;
    final Key invalidKey = mInvalidatedKey;

    boolean drawSingleKey = false;
    if (invalidKey != null && canvas.getClipBounds(clipRegion)) {
      // Is clipRegion completely contained within the invalidated key?
      if (invalidKey.getX() + kbdPaddingLeft - 1 <= clipRegion.left
          && invalidKey.getY() + kbdPaddingTop - 1 <= clipRegion.top
          && invalidKey.getX() + invalidKey.getWidth() + kbdPaddingLeft + 1 >= clipRegion.right
          && invalidKey.getY() + invalidKey.getHeight() + kbdPaddingTop + 1 >= clipRegion.bottom) {
        drawSingleKey = true;
      }
    }
    canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
    final int keyCount = keys.length;
    final float symbolBase = padding.top - mPaintSymbol.getFontMetrics().top;
    final float hintBase = -padding.bottom - mPaintSymbol.getFontMetrics().bottom;

    Timber.i(
        "onBufferDraw() keyCount=%d, drawSingleKey=%s, invalidKeyIsNull=%s",
        keyCount, drawSingleKey, invalidKey == null);
    mKeyboard.printModifierKeyState("onBufferDraw, drawSingleKey=" + drawSingleKey);
    for (final Key key : keys) {
      if (drawSingleKey && invalidKey != key) {
        continue;
      }
      int[] drawableState = key.getCurrentDrawableState();
      keyBackground = key.getBackColorForState(drawableState);
      if (keyBackground == null) {
        try {
          final int index = (int) findStateDrawableIndex.invoke(mKeyBackColor, drawableState);
          keyBackground = (Drawable) getStateDrawable.invoke(mKeyBackColor, index);
        } catch (Exception ex) {
          Timber.e(ex, "Get Drawable Exception");
        }
      }
      if (keyBackground instanceof GradientDrawable) {
        ((GradientDrawable) keyBackground)
            .setCornerRadius(
                key.getRound_corner() != null && key.getRound_corner() > 0
                    ? key.getRound_corner()
                    : mKeyboard.getRoundCorner());
      }
      Integer color = key.getTextColorForState(drawableState);
      mPaint.setColor(color != null ? color : mKeyTextColor.getColorForState(drawableState, 0));
      color = key.getSymbolColorForState(drawableState);
      mPaintSymbol.setColor(
          color != null ? color : (key.isPressed() ? hilited_key_symbol_color : key_symbol_color));

      // Switch the character to uppercase if shift is pressed
      String label = key.getLabel();
      if (label.equals("enter_labels")) label = labelEnter;
      final String hint = key.getHint();
      int left = (key.getWidth() - padding.left - padding.right) / 2 + padding.left;
      int top = padding.top;

      final Rect bounds = keyBackground.getBounds();
      if (key.getWidth() != bounds.right || key.getHeight() != bounds.bottom) {
        keyBackground.setBounds(0, 0, key.getWidth(), key.getHeight());
      }
      canvas.translate(key.getX() + kbdPaddingLeft, key.getY() + kbdPaddingTop);
      keyBackground.draw(canvas);

      if (!TextUtils.isEmpty(label)) {
        // For characters, use large font. For labels like "Done", use small font.
        if (key.getKey_text_size() != null && key.getKey_text_size() > 0) {
          paint.setTextSize(key.getKey_text_size());
        } else {
          paint.setTextSize(label.length() > 1 ? mLabelTextSize : mKeyTextSize);
        }
        // Draw a drop shadow for the text
        paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
        // Draw the text
        canvas.drawText(
            label,
            left + key.getKey_text_offset_x(),
            (key.getHeight() - padding.top - padding.bottom) / 2f
                + (paint.getTextSize() - paint.descent()) / 2f
                + top
                + key.getKey_text_offset_y(),
            paint);
        if (mShowSymbol) {
          String labelSymbol = key.getSymbolLabel();
          if (!TextUtils.isEmpty(labelSymbol)) {
            mPaintSymbol.setTextSize(
                key.getSymbol_text_size() != null && key.getSymbol_text_size() > 0
                    ? key.getSymbol_text_size()
                    : mSymbolSize);
            mPaintSymbol.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
            canvas.drawText(
                labelSymbol,
                left + key.getKey_symbol_offset_x(),
                symbolBase + key.getKey_symbol_offset_y(),
                mPaintSymbol);
          }
        }
        if (mShowHint) {
          if (!TextUtils.isEmpty(hint)) {
            mPaintSymbol.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
            canvas.drawText(
                hint,
                left + key.getKey_hint_offset_x(),
                key.getHeight() + hintBase + key.getKey_hint_offset_y(),
                mPaintSymbol);
          }
        }

        // Turn off drop shadow
        paint.setShadowLayer(0, 0, 0, 0);
      }
      canvas.translate(-key.getX() - kbdPaddingLeft, -key.getY() - kbdPaddingTop);
    }
    mInvalidatedKey = null;
    // Overlay a dark rectangle to dim the keyboard
    if (mMiniKeyboardOnScreen) {
      paint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
      canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    boolean mShowTouchPoints = true;
    if (DEBUG && mShowTouchPoints) {
      paint.setAlpha(128);
      paint.setColor(0xFFFF0000);
      canvas.drawCircle(mStartX, mStartY, 3, paint);
      canvas.drawLine(mStartX, mStartY, mLastX, mLastY, paint);
      paint.setColor(0xFF0000FF);
      canvas.drawCircle(mLastX, mLastY, 3, paint);
      paint.setColor(0xFF00FF00);
      canvas.drawCircle((mStartX + mLastX) / 2f, (mStartY + mLastY) / 2f, 2f, paint);
    }
    mCanvas.restore();
    mDrawPending = false;
    mDirtyRect.setEmpty();
  }

  private int getKeyIndices(final int x, final int y, final int[] allKeys) {
    final Key[] keys = mKeys;
    int primaryIndex = NOT_A_KEY;
    int closestKey = NOT_A_KEY;
    int closestKeyDist = mProximityThreshold + 1;
    java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
    final int[] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
    for (int nearestKeyIndex : nearestKeyIndices) {
      final Key key = keys[nearestKeyIndex];
      int dist = 0;
      boolean isInside = key.isInside(x, y);
      if (isInside) {
        primaryIndex = nearestKeyIndex;
      }

      if ((mProximityCorrectOn && (dist = key.squaredDistanceFrom(x, y)) < mProximityThreshold)
          || isInside) {
        // Find insertion point
        final int nCodes = 1;
        if (dist < closestKeyDist) {
          closestKeyDist = dist;
          closestKey = nearestKeyIndex;
        }

        if (allKeys == null) continue;

        for (int j = 0; j < mDistances.length; j++) {
          if (mDistances[j] > dist) {
            // Make space for nCodes codes
            System.arraycopy(mDistances, j, mDistances, j + nCodes, mDistances.length - j - nCodes);
            System.arraycopy(allKeys, j, allKeys, j + nCodes, allKeys.length - j - nCodes);
            allKeys[j] = key.getCode();
            mDistances[j] = dist;
            break;
          }
        }
      }
    }
    if (primaryIndex == NOT_A_KEY) {
      primaryIndex = closestKey;
    }
    return primaryIndex;
  }

  private void releaseKey(int code) {
    Timber.d(
        "\t<TrimeInput>\treleaseKey() key=%d, mComboCount=%d, mComboMode=%s",
        code, mComboCount, mComboMode);
    if (mComboMode) {
      if (mComboCount > 9) mComboCount = 9;
      mComboCodes[mComboCount++] = code;
    } else {
      mKeyboardActionListener.onRelease(code);
      if (mComboCount > 0) {
        for (int i = 0; i < mComboCount; i++) {
          mKeyboardActionListener.onRelease(mComboCodes[i]);
        }
        mComboCount = 0;
      }
    }
    Timber.d("\t<TrimeInput>\treleaseKey() finish");
  }

  private void detectAndSendKey(int index, int x, int y, long eventTime, KeyEventType type) {
    Timber.d(
        "\t<TrimeInput>\tdetectAndSendKey()\tindex=%d, x=%d, y=%d, type=%d, mKeys.length=%d",
        index, x, y, type.ordinal(), mKeys.length);

    if (index != NOT_A_KEY && index < mKeys.length) {
      final Key key = mKeys[index];

      if (Key.isTrimeModifierKey(key.getCode()) && !key.sendBindings(type.ordinal())) {
        Timber.d(
            "\t<TrimeInput>\tdetectAndSendKey()\tModifierKey, key.getEvent, KeyLabel=%s",
            key.getLabel());
        setModifier(key);
      } else {
        if (key.getClick().isRepeatable()) {
          if (type.ordinal() > KeyEventType.CLICK.ordinal()) mAbortKey = true;
          if (!key.hasEvent(type.ordinal())) return;
        }
        final int code = key.getCode(type.ordinal());
        // TextEntryState.keyPressedAt(key, x, y);
        final int[] codes = new int[MAX_NEARBY_KEYS];
        Arrays.fill(codes, NOT_A_KEY);
        getKeyIndices(x, y, codes);
        Timber.d("\t<TrimeInput>\tdetectAndSendKey()\tonEvent, code=%d, key.getEvent", code);
        // 可以在这里把 mKeyboard.getModifer() 获取的修饰键状态写入event里
        mKeyboardActionListener.onEvent(key.getEvent(type.ordinal()));
        releaseKey(code);
        Timber.d("\t<TrimeInput>\tdetectAndSendKey()\trefreshModifier");
        refreshModifier();
      }
      mLastSentIndex = index;
      mLastTapTime = eventTime;
      Timber.d("\t<TrimeInput>\tdetectAndSendKey()\tfinish");
    }
  }

  private void detectAndSendKey(final int index, final int x, final int y, final long eventTime) {
    detectAndSendKey(index, x, y, eventTime, KeyEventType.CLICK);
  }

  private void showPreview(final int keyIndex, final int type) {
    final int oldKeyIndex = mCurrentKeyIndex;
    final PopupWindow previewPopup = mPreviewPopup;

    mCurrentKeyIndex = keyIndex;
    // Release the old key and press the new key
    final Key[] keys = mKeys;
    if (oldKeyIndex != mCurrentKeyIndex) {
      if (oldKeyIndex != NOT_A_KEY && keys.length > oldKeyIndex) {
        final Key oldKey = keys[oldKeyIndex];
        oldKey.onReleased(mCurrentKeyIndex == NOT_A_KEY);
        invalidateKey(oldKeyIndex);
      }
      if (mCurrentKeyIndex != NOT_A_KEY && keys.length > mCurrentKeyIndex) {
        final Key newKey = keys[mCurrentKeyIndex];
        newKey.onPressed();
        invalidateKey(mCurrentKeyIndex);
      }
    }
    // If key changed and preview is on ...
    if (oldKeyIndex != mCurrentKeyIndex && mShowPreview) {
      mHandler.removeMessages(MSG_SHOW_PREVIEW);
      if (previewPopup.isShowing()) {
        if (keyIndex == NOT_A_KEY) {
          mHandler.sendMessageDelayed(
              mHandler.obtainMessage(MSG_REMOVE_PREVIEW), DELAY_AFTER_PREVIEW);
        }
      }
      if (keyIndex != NOT_A_KEY) {
        if (previewPopup.isShowing() && mPreviewText.getVisibility() == VISIBLE) {
          // Show right away, if it's already visible and finger is moving around
          showKey(keyIndex, type);
        } else {
          mHandler.sendMessageDelayed(
              mHandler.obtainMessage(MSG_SHOW_PREVIEW, keyIndex, type), DELAY_BEFORE_PREVIEW);
        }
      }
    }
  }

  private void showPreview(final int keyIndex) {
    showPreview(keyIndex, 0);
  }

  private void showKey(final int keyIndex, final int type) {
    final PopupWindow previewPopup = mPreviewPopup;
    final Key[] keys = mKeys;
    if (keyIndex < 0 || keyIndex >= mKeys.length) return;
    final Key key = keys[keyIndex];
    mPreviewText.setCompoundDrawables(null, null, null, null);
    mPreviewText.setText(key.getPreviewText(type));
    mPreviewText.measure(
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    final int popupWidth =
        Math.max(
            mPreviewText.getMeasuredWidth(),
            key.getWidth() + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight());
    final int popupHeight = mPreviewHeight;
    final ViewGroup.LayoutParams lp = mPreviewText.getLayoutParams();
    if (lp != null) {
      lp.width = popupWidth;
      lp.height = popupHeight;
    }
    int mPopupPreviewY;
    int mPopupPreviewX;
    boolean mPreviewCentered = false;
    if (!mPreviewCentered) {
      mPopupPreviewX = key.getX() - mPreviewText.getPaddingLeft() + getPaddingLeft();
      mPopupPreviewY = key.getY() - popupHeight + mPreviewOffset;
    } else {
      // TODO: Fix this if centering is brought back
      mPopupPreviewX = 160 - mPreviewText.getMeasuredWidth() / 2;
      mPopupPreviewY = -mPreviewText.getMeasuredHeight();
    }
    mHandler.removeMessages(MSG_REMOVE_PREVIEW);
    getLocationInWindow(mCoordinates);
    mCoordinates[0] += mMiniKeyboardOffsetX; // Offset may be zero
    mCoordinates[1] += mMiniKeyboardOffsetY; // Offset may be zero

    // Set the preview background state
    mPreviewText
        .getBackground()
        .setState(key.getPopupResId() != 0 ? LONG_PRESSABLE_STATE_SET : EMPTY_STATE_SET);
    mPopupPreviewX += mCoordinates[0];
    mPopupPreviewY += mCoordinates[1];

    // If the popup cannot be shown above the key, put it on the side
    getLocationOnScreen(mCoordinates);
    if (mPopupPreviewY + mCoordinates[1] < 0) {
      // If the key you're pressing is on the left side of the keyboard, show the popup on
      // the right, offset by enough to see at least one key to the left/right.
      if (key.getX() + key.getWidth() <= getWidth() / 2) {
        mPopupPreviewX += (int) (key.getWidth() * 2.5);
      } else {
        mPopupPreviewX -= (int) (key.getWidth() * 2.5);
      }
      mPopupPreviewY += popupHeight;
    }

    if (previewPopup.isShowing()) {
      // previewPopup.update(mPopupPreviewX, mPopupPreviewY, popupWidth, popupHeight);
      previewPopup.dismiss(); // 禁止窗口動畫
    }
    previewPopup.setWidth(popupWidth);
    previewPopup.setHeight(popupHeight);
    previewPopup.showAtLocation(mPopupParent, Gravity.NO_GRAVITY, mPopupPreviewX, mPopupPreviewY);
    mPreviewText.setVisibility(VISIBLE);
  }

  /**
   * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not sufficient because
   * the keyboard renders the keys to an off-screen buffer and an invalidate() only draws the cached
   * buffer.
   *
   * @see #invalidateKey(int)
   */
  public void invalidateAllKeys() {
    Timber.d("\t<TrimeInput>\tinvalidateAllKeys()");
    mDirtyRect.union(0, 0, getWidth(), getHeight());
    mDrawPending = true;
    invalidate();
  }

  /**
   * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only one
   * key is changing it's content. Any changes that affect the position or size of the key may not
   * be honored.
   *
   * @param keyIndex the index of the key in the attached {@link Keyboard}.
   * @see #invalidateAllKeys
   */
  private void invalidateKey(int keyIndex) {
    Timber.d(
        "\t<TrimeInput>\tinvalidateKey()\tkeyIndex=%d, mKeysExist=%s", keyIndex, mKeys != null);
    if (mKeys == null) return;
    if (keyIndex < 0 || keyIndex >= mKeys.length) {
      return;
    }
    final Key key = mKeys[keyIndex];
    mInvalidatedKey = key;
    mDirtyRect.union(
        key.getX() + getPaddingLeft(),
        key.getY() + getPaddingTop(),
        key.getX() + key.getWidth() + getPaddingLeft(),
        key.getY() + key.getHeight() + getPaddingTop());
    onBufferDraw();
    Timber.d("\t<TrimeInput>\tinvalidateKey()\tinvalidate");
    invalidate(
        key.getX() + getPaddingLeft(),
        key.getY() + getPaddingTop(),
        key.getX() + key.getWidth() + getPaddingLeft(),
        key.getY() + key.getHeight() + getPaddingTop());
    Timber.d("\t<TrimeInput>\tinvalidateKey()\tfinish");
  }

  private void invalidateKeys(final List<Key> keys) {
    if (keys == null || keys.size() == 0) return;
    for (Key key : keys) {
      mDirtyRect.union(
          key.getX() + getPaddingLeft(),
          key.getY() + getPaddingTop(),
          key.getX() + key.getWidth() + getPaddingLeft(),
          key.getY() + key.getHeight() + getPaddingTop());
    }
    onBufferDraw();
    invalidate();
  }

  public void invalidateComposingKeys() {
    if (mKeyboard != null) {
      final List<Key> keys = mKeyboard.getComposingKeys();
      if (keys != null && keys.size() > 5) invalidateAllKeys();
      else invalidateKeys(keys);
    } else {
      Timber.e("invalidateComposingKeys() mKeyboard==null");
    }
  }

  private boolean openPopupIfRequired(final MotionEvent me) {
    // Check if we have a popup layout specified first.
    if (mPopupLayout == 0) {
      return false;
    }
    if (mCurrentKey < 0 || mCurrentKey >= mKeys.length) {
      return false;
    }
    showPreview(NOT_A_KEY);
    showPreview(mCurrentKey, KeyEventType.LONG_CLICK.ordinal());
    Key popupKey = mKeys[mCurrentKey];
    boolean result = onLongPress(popupKey);
    if (result) {
      mAbortKey = true;
      showPreview(NOT_A_KEY);
    }
    return result;
  }

  /**
   * Called when a key is long pressed. By default this will open any popup keyboard associated with
   * this key through the attributes popupLayout and popupCharacters.
   *
   * @param popupKey the key that was long pressed
   * @return true if the long press is handled, false otherwise. Subclasses should call the method
   *     on the base class if the subclass doesn't wish to handle the call.
   */
  private boolean onLongPress(@NonNull final Key popupKey) {
    final int popupKeyboardId = popupKey.getPopupResId();

    if (popupKeyboardId != 0) {
      View mMiniKeyboardContainer = mMiniKeyboardCache.get(popupKey);
      final KeyboardView mMiniKeyboard;
      if (mMiniKeyboardContainer == null) {
        final LayoutInflater inflater =
            (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mMiniKeyboardContainer = inflater.inflate(mPopupLayout, null);
        mMiniKeyboard = mMiniKeyboardContainer.findViewById(android.R.id.keyboardView);
        final View closeButton = mMiniKeyboardContainer.findViewById(android.R.id.closeButton);
        if (closeButton != null) closeButton.setOnClickListener(this);
        mMiniKeyboard.setOnKeyboardActionListener(
            new OnKeyboardActionListener() {
              @Override
              public void onEvent(final Event event) {
                mKeyboardActionListener.onEvent(event);
                dismissPopupKeyboard();
              }

              @Override
              public void onKey(final int primaryCode, final int mask) {
                mKeyboardActionListener.onKey(primaryCode, mask);
                dismissPopupKeyboard();
              }

              @Override
              public void onText(final CharSequence text) {
                mKeyboardActionListener.onText(text);
                dismissPopupKeyboard();
              }

              @Override
              public void onPress(final int primaryCode) {
                Timber.d("\t<TrimeInput>\tonLongPress() onPress key=" + primaryCode);
                mKeyboardActionListener.onPress(primaryCode);
              }

              @Override
              public void onRelease(final int primaryCode) {
                mKeyboardActionListener.onRelease(primaryCode);
              }
            });
        // mInputView.setSuggest(mSuggest);
        final Keyboard keyboard;
        if (popupKey.getPopupCharacters() != null) {
          keyboard =
              new Keyboard(
                  getContext(),
                  popupKey.getPopupCharacters(),
                  -1,
                  getPaddingLeft() + getPaddingRight());
        } else {
          keyboard = new Keyboard(getContext());
        }
        mMiniKeyboard.setKeyboard(keyboard);
        mMiniKeyboard.setPopupParent(this);
        mMiniKeyboardContainer.measure(
            MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

        mMiniKeyboardCache.put(popupKey, mMiniKeyboardContainer);
      } else {
        mMiniKeyboard = mMiniKeyboardContainer.findViewById(android.R.id.keyboardView);
      }
      getLocationInWindow(mCoordinates);
      int mPopupX = popupKey.getX() + getPaddingLeft();
      int mPopupY = popupKey.getY() + getPaddingTop();
      mPopupX = mPopupX + popupKey.getWidth() - mMiniKeyboardContainer.getMeasuredWidth();
      mPopupY = mPopupY - mMiniKeyboardContainer.getMeasuredHeight();
      final int x = mPopupX + mMiniKeyboardContainer.getPaddingRight() + mCoordinates[0];
      final int y = mPopupY + mMiniKeyboardContainer.getPaddingBottom() + mCoordinates[1];
      mMiniKeyboard.setPopupOffset(Math.max(x, 0), y);

      // todo 只处理了shift
      Timber.w("only set isShifted, no others modifierkey");
      mMiniKeyboard.setShifted(false, isShifted());
      mPopupKeyboard.setContentView(mMiniKeyboardContainer);
      mPopupKeyboard.setWidth(mMiniKeyboardContainer.getMeasuredWidth());
      mPopupKeyboard.setHeight(mMiniKeyboardContainer.getMeasuredHeight());
      mPopupKeyboard.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
      mMiniKeyboardOnScreen = true;
      // mMiniKeyboard.onTouchEvent(getTranslatedEvent(me));
      invalidateAllKeys();
      return true;
    } else {
      if (popupKey.getLongClick() != null) {
        removeMessages();
        mAbortKey = true;
        final Event e = popupKey.getLongClick();
        mKeyboardActionListener.onEvent(e);
        releaseKey(e.getCode());
        resetModifer();
        return true;
      }

      Timber.w("only set isShifted, no others modifierkey");
      if (popupKey.isShift() && !popupKey.sendBindings(KeyEventType.LONG_CLICK.ordinal())) {
        // todo 其他修饰键
        setShifted(!popupKey.isOn(), !popupKey.isOn());
        return true;
      }
    }
    return false;
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

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  public boolean onTouchEvent(@NonNull final MotionEvent me) {
    // Convert multi-pointer up/down events to single up/down events to
    // deal with the typical multi-pointer behavior of two-thumb typing
    final int index = me.getActionIndex();
    final int pointerCount = me.getPointerCount();
    final int action = me.getActionMasked();
    boolean result = false;
    final long now = me.getEventTime();

    mComboMode = false;
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_CANCEL) {
      mComboCount = 0;
      if (getPrefs().getKeyboard().getHookFastInput())
        isFastInput = getPrefs().getKeyboard().getSwipeTimeHi() > me.getEventTime() - mLastUpTime;
      else isFastInput = false;
    } else if (pointerCount > 1
        || action == MotionEvent.ACTION_POINTER_DOWN
        || action == MotionEvent.ACTION_POINTER_UP) {
      mComboMode = true;
    }

    if (action == MotionEvent.ACTION_UP) {
      Timber.d("swipeDebug.onTouchEvent ?, action = ACTION_UP");
    }

    if (action == MotionEvent.ACTION_POINTER_UP
        || (mOldPointerCount > 1 && action == MotionEvent.ACTION_UP)) {
      // 並擊鬆開前的虛擬按鍵事件
      final MotionEvent ev =
          MotionEvent.obtain(
              now,
              now,
              MotionEvent.ACTION_POINTER_DOWN,
              me.getX(index),
              me.getY(index),
              me.getMetaState());
      result = onModifiedTouchEvent(ev, false);
      ev.recycle();
      Timber.d("\t<TrimeInput>\tonTouchEvent()\tactionUp done");
    }

    if (action == MotionEvent.ACTION_POINTER_DOWN) {
      // 並擊中的按鍵事件，需要按鍵提示
      final MotionEvent ev =
          MotionEvent.obtain(
              now, now, MotionEvent.ACTION_DOWN, me.getX(index), me.getY(index), me.getMetaState());
      result = onModifiedTouchEvent(ev, false);
      ev.recycle();
      Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tactionDown done");
    } else {
      Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tonModifiedTouchEvent");
      result = onModifiedTouchEvent(me, false);
      Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tnot actionDown done");
    }

    if (action != MotionEvent.ACTION_MOVE) mOldPointerCount = pointerCount;
    performClick();
    return result;
  }

  private boolean onModifiedTouchEvent(@NonNull final MotionEvent me, final boolean possiblePoly) {
    // final int pointerCount = me.getPointerCount();
    final int index = me.getActionIndex();
    int touchX = (int) me.getX(index) - getPaddingLeft();
    int touchY = (int) me.getY(index) - getPaddingTop();
    if (touchY >= -mVerticalCorrection) touchY += mVerticalCorrection;
    final int action = me.getActionMasked();
    final long eventTime = me.getEventTime();
    final int keyIndex = getKeyIndices(touchX, touchY, null);
    mPossiblePoly = possiblePoly;

    // Track the last few movements to look for spurious swipes.
    if (action == MotionEvent.ACTION_DOWN) mSwipeTracker.clear();
    mSwipeTracker.addMovement(me);

    if (action == MotionEvent.ACTION_CANCEL)
      Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action = cancel");
    else if (action == MotionEvent.ACTION_UP)
      Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action = UP");
    else Timber.d("swipeDebug.onModifiedTouchEvent before gesture, action != UP");

    // Ignore all motion events until a DOWN.
    if (mAbortKey && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
      return true;
    }

    // 优先判定是否触发了滑动手势
    if (getPrefs().getKeyboard().getSwipeEnabled()) {
      if (mGestureDetector.onTouchEvent(me)) {
        showPreview(NOT_A_KEY);
        mHandler.removeMessages(MSG_REPEAT);
        mHandler.removeMessages(MSG_LONGPRESS);
        return true;
      }
    }

    // Needs to be called after the gesture detector gets a turn, as it may have
    // displayed the mini keyboard
    if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
      return true;
    }

    switch (action) {
      case MotionEvent.ACTION_DOWN:
        touchX0 = touchX;
        touchY0 = touchY;
        touchOnePoint = true;
      case MotionEvent.ACTION_POINTER_DOWN:
        mAbortKey = false;
        mStartX = touchX;
        mStartY = touchY;
        mLastCodeX = touchX;
        mLastCodeY = touchY;
        mLastKeyTime = 0;
        mCurrentKeyTime = 0;
        mLastKey = NOT_A_KEY;
        mCurrentKey = keyIndex;
        mDownKey = keyIndex;
        mDownTime = me.getEventTime();
        mLastMoveTime = mDownTime;
        touchOnePoint = false;
        if (action == MotionEvent.ACTION_POINTER_DOWN) break; // 並擊鬆開前的虛擬按鍵事件
        checkMultiTap(eventTime, keyIndex);
        mKeyboardActionListener.onPress(keyIndex != NOT_A_KEY ? mKeys[keyIndex].getCode() : 0);
        if (mCurrentKey >= 0 && mKeys[mCurrentKey].getClick().isRepeatable()) {
          mRepeatKeyIndex = mCurrentKey;
          final Message msg = mHandler.obtainMessage(MSG_REPEAT);
          mHandler.sendMessageDelayed(msg, REPEAT_START_DELAY);
          // Delivering the key could have caused an abort
          if (mAbortKey) {
            mRepeatKeyIndex = NOT_A_KEY;
            break;
          }
        }
        if (mCurrentKey != NOT_A_KEY) {
          final Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
          mHandler.sendMessageDelayed(msg, LONG_PRESS_TIMEOUT);
        }
        showPreview(keyIndex, 0);
        break;

      case MotionEvent.ACTION_MOVE:
        boolean continueLongPress = false;
        if (keyIndex != NOT_A_KEY) {
          if (mCurrentKey == NOT_A_KEY) {
            mCurrentKey = keyIndex;
            mCurrentKeyTime = eventTime - mDownTime;
          } else {
            if (keyIndex == mCurrentKey) {
              mCurrentKeyTime += eventTime - mLastMoveTime;
              continueLongPress = true;
            } else if (mRepeatKeyIndex == NOT_A_KEY) {
              resetMultiTap();
              mLastKey = mCurrentKey;
              mLastCodeX = mLastX;
              mLastCodeY = mLastY;
              mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
              mCurrentKey = keyIndex;
              mCurrentKeyTime = 0;
            }
          }
        }
        if (!mComboMode && !continueLongPress) {
          // Cancel old long press
          mHandler.removeMessages(MSG_LONGPRESS);
          // Start new long press if key has changed
          if (keyIndex != NOT_A_KEY) {
            final Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
            mHandler.sendMessageDelayed(msg, LONG_PRESS_TIMEOUT);
          }
        }
        showPreview(mCurrentKey);
        mLastMoveTime = eventTime;
        break;

      case MotionEvent.ACTION_UP:
        Timber.d(
            "swipeDebug.onModifiedTouchEvent mGestureDetector.onTouchEvent(me) = fall & action_up");
      case MotionEvent.ACTION_POINTER_UP:
        removeMessages();
        mLastUpTime = eventTime;
        if (keyIndex == mCurrentKey) {
          mCurrentKeyTime += eventTime - mLastMoveTime;
        } else {
          resetMultiTap();
          mLastKey = mCurrentKey;
          mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
          mCurrentKey = keyIndex;
          mCurrentKeyTime = 0;
        }

        // 滑动兜底，不判定速度，只判定距离
        if (getPrefs().getKeyboard().getSwipeEnabled()) {
          int dx = touchX - touchX0;
          int dy = touchY - touchY0;
          int absX = Math.abs(dx);
          int absY = Math.abs(dy);
          final int travel =
              (isFastInput && isClickAtLast)
                  ? getPrefs().getKeyboard().getSwipeTravelHi()
                  : getPrefs().getKeyboard().getSwipeTravel();

          if (Math.max(absY, absX) > travel && touchOnePoint) {
            Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\ttouch");
            KeyEventType type = KeyEventType.CLICK;
            if (absX < absY) {
              Timber.d("swipeDebug.ext y, dX=%d, dY=%d", dx, dy);
              if (dy > travel) type = KeyEventType.SWIPE_DOWN;
              else type = KeyEventType.SWIPE_UP;
            } else {
              Timber.d("swipeDebug.ext x, dX=%d, dY=%d", dx, dy);
              if (dx > travel) type = KeyEventType.SWIPE_RIGHT;
              else type = KeyEventType.SWIPE_LEFT;
            }

            showPreview(NOT_A_KEY);
            mHandler.removeMessages(MSG_REPEAT);
            mHandler.removeMessages(MSG_LONGPRESS);
            detectAndSendKey(mDownKey, mStartX, mStartY, me.getEventTime(), type);
            isClickAtLast = false;
            return true;
          } else Timber.d("swipeDebug.ext fail, dX=%d, dY=%d", dx, dy);
        }

        if (mCurrentKeyTime < mLastKeyTime
            && mCurrentKeyTime < DEBOUNCE_TIME
            && mLastKey != NOT_A_KEY) {
          mCurrentKey = mLastKey;
          touchX = mLastCodeX;
          touchY = mLastCodeY;
        }
        showPreview(NOT_A_KEY);
        Arrays.fill(mKeyIndices, NOT_A_KEY);
        // If we're not on a repeating key (which sends on a DOWN event)
        if (mRepeatKeyIndex != NOT_A_KEY && !mAbortKey) repeatKey();
        if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
          Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tdetectAndSendKey");
          detectAndSendKey(
              mCurrentKey,
              touchX,
              touchY,
              eventTime,
              (mOldPointerCount > 1 || mComboMode) ? KeyEventType.COMBO : KeyEventType.CLICK);
          isClickAtLast = true;
        }
        Timber.d("\t<TrimeInput>\tonModifiedTouchEvent()\tdetectAndSendKey finish");
        invalidateKey(keyIndex);
        mRepeatKeyIndex = NOT_A_KEY;
        break;
      case MotionEvent.ACTION_CANCEL:
        removeMessages();
        dismissPopupKeyboard();
        mAbortKey = true;
        showPreview(NOT_A_KEY);
        invalidateKey(mCurrentKey);
        break;
    }
    mLastX = touchX;
    mLastY = touchY;
    return true;
  }

  private boolean repeatKey() {
    Timber.d("\t<TrimeInput>\trepeatKey()");
    final Key key = mKeys[mRepeatKeyIndex];
    detectAndSendKey(mCurrentKey, key.getX(), key.getY(), mLastTapTime);
    return true;
  }

  public void closing() {
    if (mPreviewPopup.isShowing()) {
      mPreviewPopup.dismiss();
    }
    removeMessages();

    dismissPopupKeyboard();
    mBuffer = null;
    mCanvas = null;
    mMiniKeyboardCache.clear();
  }

  private void removeMessages() {
    mHandler.removeMessages(MSG_REPEAT);
    mHandler.removeMessages(MSG_LONGPRESS);
    mHandler.removeMessages(MSG_SHOW_PREVIEW);
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    closing();
  }

  private void dismissPopupKeyboard() {
    if (mPopupKeyboard.isShowing()) {
      mPopupKeyboard.dismiss();
      mMiniKeyboardOnScreen = false;
      invalidateAllKeys();
    }
  }

  public boolean handleBack() {
    if (mPopupKeyboard.isShowing()) {
      dismissPopupKeyboard();
      return true;
    }
    return false;
  }

  private void resetMultiTap() {
    mLastSentIndex = NOT_A_KEY;
    // final int mTapCount = 0;
    mLastTapTime = -1;
    // final boolean mInMultiTap = false;
  }

  private void checkMultiTap(long eventTime, int keyIndex) {
    if (keyIndex == NOT_A_KEY) return;
    // final Key key = mKeys[keyIndex];
    if (eventTime > mLastTapTime + MULTI_TAP_INTERVAL || keyIndex != mLastSentIndex) {
      resetMultiTap();
    }
  }

  /** 識別滑動手勢 */
  private static class SwipeTracker {

    static final int NUM_PAST = 4;
    static final int LONGEST_PAST_TIME = 200;

    final float[] mPastX = new float[NUM_PAST];
    final float[] mPastY = new float[NUM_PAST];
    final long[] mPastTime = new long[NUM_PAST];

    float mYVelocity;
    float mXVelocity;

    public void clear() {
      mPastTime[0] = 0;
    }

    public void addMovement(@NonNull final MotionEvent ev) {
      long time = ev.getEventTime();
      final int N = ev.getHistorySize();
      for (int i = 0; i < N; i++) {
        addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalEventTime(i));
      }
      addPoint(ev.getX(), ev.getY(), time);
    }

    private void addPoint(final float x, final float y, final long time) {
      int drop = -1;
      int i;
      final long[] pastTime = mPastTime;
      for (i = 0; i < NUM_PAST; i++) {
        if (pastTime[i] == 0) {
          break;
        } else if (pastTime[i] < time - LONGEST_PAST_TIME) {
          drop = i;
        }
      }
      if (i == NUM_PAST && drop < 0) {
        drop = 0;
      }
      if (drop == i) drop--;
      final float[] pastX = mPastX;
      final float[] pastY = mPastY;
      if (drop >= 0) {
        final int start = drop + 1;
        final int count = NUM_PAST - drop - 1;
        System.arraycopy(pastX, start, pastX, 0, count);
        System.arraycopy(pastY, start, pastY, 0, count);
        System.arraycopy(pastTime, start, pastTime, 0, count);
        i -= (drop + 1);
      }
      pastX[i] = x;
      pastY[i] = y;
      pastTime[i] = time;
      i++;
      if (i < NUM_PAST) {
        pastTime[i] = 0;
      }
    }

    public void computeCurrentVelocity(int units) {
      computeCurrentVelocity(units, Float.MAX_VALUE);
    }

    public void computeCurrentVelocity(int units, float maxVelocity) {
      final float[] pastX = mPastX;
      final float[] pastY = mPastY;
      final long[] pastTime = mPastTime;

      final float oldestX = pastX[0];
      final float oldestY = pastY[0];
      final long oldestTime = pastTime[0];
      float accumX = 0;
      float accumY = 0;
      int N = 0;
      while (N < NUM_PAST) {
        if (pastTime[N] == 0) {
          break;
        }
        N++;
      }

      for (int i = 1; i < N; i++) {
        final int dur = (int) (pastTime[i] - oldestTime);
        if (dur == 0) continue;
        float dist = pastX[i] - oldestX;
        float vel = (dist / dur) * units; // pixels/frame.
        if (accumX == 0) accumX = vel;
        else accumX = (accumX + vel) * .5f;

        dist = pastY[i] - oldestY;
        vel = (dist / dur) * units; // pixels/frame.
        if (accumY == 0) accumY = vel;
        else accumY = (accumY + vel) * .5f;
      }
      mXVelocity = accumX < 0.0f ? Math.max(accumX, -maxVelocity) : Math.min(accumX, maxVelocity);
      mYVelocity = accumY < 0.0f ? Math.max(accumY, -maxVelocity) : Math.min(accumY, maxVelocity);
    }

    public float getXVelocity() {
      return mXVelocity;
    }

    public float getYVelocity() {
      return mYVelocity;
    }
  }
}
