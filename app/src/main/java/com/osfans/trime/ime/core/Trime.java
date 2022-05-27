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

package com.osfans.trime.ime.core;

import static android.graphics.Color.parseColor;

import android.app.Dialog;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.blankj.utilcode.util.BarUtils;
import com.osfans.trime.R;
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.data.Config;
import com.osfans.trime.data.db.clipboard.ClipboardDao;
import com.osfans.trime.data.db.draft.DraftDao;
import com.osfans.trime.databinding.CompositionRootBinding;
import com.osfans.trime.databinding.InputRootBinding;
import com.osfans.trime.ime.broadcast.IntentReceiver;
import com.osfans.trime.ime.enums.PositionType;
import com.osfans.trime.ime.keyboard.Event;
import com.osfans.trime.ime.keyboard.InputFeedbackManager;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.ime.keyboard.Keyboard;
import com.osfans.trime.ime.keyboard.KeyboardSwitcher;
import com.osfans.trime.ime.keyboard.KeyboardView;
import com.osfans.trime.ime.keyboard.Sound;
import com.osfans.trime.ime.lifecycle.LifecycleInputMethodService;
import com.osfans.trime.ime.symbol.LiquidKeyboard;
import com.osfans.trime.ime.symbol.TabManager;
import com.osfans.trime.ime.symbol.TabView;
import com.osfans.trime.ime.text.Candidate;
import com.osfans.trime.ime.text.Composition;
import com.osfans.trime.ime.text.ScrollView;
import com.osfans.trime.ime.text.TextInputManager;
import com.osfans.trime.settings.PrefMainActivity;
import com.osfans.trime.settings.components.ColorPickerDialog;
import com.osfans.trime.settings.components.SchemaPickerDialog;
import com.osfans.trime.settings.components.SoundPickerDialog;
import com.osfans.trime.settings.components.ThemePickerDialog;
import com.osfans.trime.util.ShortcutUtils;
import com.osfans.trime.util.StringUtils;
import com.osfans.trime.util.ViewUtils;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import kotlin.jvm.Synchronized;
import timber.log.Timber;

/** {@link InputMethodService 輸入法}主程序 */
public class Trime extends LifecycleInputMethodService {
  private static Trime self = null;
  private LiquidKeyboard liquidKeyboard;
  private boolean normalTextEditor;

  @NonNull
  private AppPrefs getPrefs() {
    return AppPrefs.Companion.defaultInstance();
  }

  /** 输入法配置 */
  @NonNull
  public Config getImeConfig() {
    return Config.get(this);
  }

  private boolean darkMode; // 当前键盘主题是否处于暗黑模式
  private KeyboardView mainKeyboardView; // 主軟鍵盤
  public KeyboardSwitcher keyboardSwitcher; // 键盘切换器

  private Candidate mCandidate; // 候選
  private Composition mComposition; // 編碼
  private CompositionRootBinding compositionRootBinding = null;
  private ScrollView mCandidateRoot, mTabRoot;
  private TabView tabView;
  public InputRootBinding inputRootBinding = null;
  public CopyOnWriteArrayList<EventListener> eventListeners = new CopyOnWriteArrayList<>();
  public InputMethodManager imeManager = null;
  public InputFeedbackManager inputFeedbackManager = null; // 效果管理器
  private IntentReceiver mIntentReceiver = null;

  private boolean isWindowShown = false; // 键盘窗口是否已显示

  private boolean isAutoCaps; // 句首自動大寫
  private final Locale[] locales = new Locale[2];

  private int oneHandMode = 0; // 单手键盘模式
  public EditorInstance activeEditorInstance;
  public TextInputManager textInputManager; // 文字输入管理器

  private final int dialogType =
      VERSION.SDK_INT >= VERSION_CODES.P
          ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
          : WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;

  private boolean isPopupWindowEnabled = true; // 顯示懸浮窗口
  private String isPopupWindowMovable; // 悬浮窗口是否可移動
  private int popupWindowX, popupWindowY; // 悬浮床移动座標
  private int popupMargin; // 候選窗與邊緣空隙
  private int popupMarginH; // 悬浮窗与屏幕两侧的间距
  private boolean isCursorUpdated = false; // 光標是否移動
  private int minPopupSize; // 上悬浮窗的候选词的最小词长
  private int minPopupCheckSize; // 第一屏候选词数量少于设定值，则候选词上悬浮窗。（也就是说，第一屏存在长词）此选项大于1时，min_length等参数失效
  private PositionType popupWindowPos; // 悬浮窗口彈出位置
  private PopupWindow mPopupWindow;
  private RectF mPopupRectF = new RectF();
  private final Handler mPopupHandler = new Handler(Looper.getMainLooper());
  private final Runnable mPopupTimer =
      new Runnable() {
        @Override
        public void run() {
          if (mCandidateRoot == null || mCandidateRoot.getWindowToken() == null) return;
          if (!isPopupWindowEnabled) return;
          int x = 0, y = 0;
          final int[] candidateLocation = new int[2];
          mCandidateRoot.getLocationOnScreen(candidateLocation);
          final int minX = popupMarginH;
          final int minY = popupMargin;
          final int maxX = mCandidateRoot.getWidth() - mPopupWindow.getWidth() - minX;
          final int maxY = candidateLocation[1] - mPopupWindow.getHeight() - minY;
          if (isWinFixed() || !isCursorUpdated) {
            // setCandidatesViewShown(true);
            switch (popupWindowPos) {
              case TOP_RIGHT:
                x = maxX;
                y = minY;
                break;
              case TOP_LEFT:
                x = minX;
                y = minY;
                break;
              case BOTTOM_RIGHT:
                x = maxX;
                y = maxY;
                break;
              case DRAG:
                x = popupWindowX;
                y = popupWindowY;
                break;
              case FIXED:
              case BOTTOM_LEFT:
              default:
                x = minX;
                y = maxY;
                break;
            }
          } else {
            // setCandidatesViewShown(false);
            switch (popupWindowPos) {
              case LEFT:
              case LEFT_UP:
                x = (int) mPopupRectF.left;
                break;
              case RIGHT:
              case RIGHT_UP:
                x = (int) mPopupRectF.right;
                break;
              default:
                Timber.wtf("UNREACHABLE BRANCH");
            }
            x = Math.min(maxX, x);
            x = Math.max(minX, x);
            switch (popupWindowPos) {
              case LEFT:
              case RIGHT:
                y = (int) mPopupRectF.bottom + popupMargin;
                break;
              case LEFT_UP:
              case RIGHT_UP:
                y = (int) mPopupRectF.top - mPopupWindow.getHeight() - popupMargin;
                break;
              default:
                Timber.wtf("UNREACHABLE BRANCH");
            }
            y = Math.min(maxY, y);
            y = Math.max(minY, y);
          }
          y -= BarUtils.getStatusBarHeight(); // 不包含狀態欄

          if (!mPopupWindow.isShowing()) {
            mPopupWindow.showAtLocation(mCandidateRoot, Gravity.START | Gravity.TOP, x, y);
          } else {
            mPopupWindow.update(x, y, mPopupWindow.getWidth(), mPopupWindow.getHeight());
          }
        }
      };

  @Synchronized
  @NonNull
  public static Trime getService() {
    assert self != null;
    return self;
  }

  @Synchronized
  @Nullable
  public static Trime getServiceOrNull() {
    return self;
  }

  private static final Handler syncBackgroundHandler =
      new Handler(
          msg -> {
            if (!((Trime) msg.obj).isShowInputRequested()) { // 若当前没有输入面板，则后台同步。防止面板关闭后5秒内再次打开
              ShortcutUtils.INSTANCE.syncInBackground((Trime) msg.obj);
              ((Trime) msg.obj).loadConfig();
            }
            return false;
          });

  public Trime() {
    try {
      self = this;
      textInputManager = TextInputManager.Companion.getInstance();
    } catch (Exception e) {
      e.fillInStackTrace();
    }
  }

  @Override
  public void onWindowShown() {
    super.onWindowShown();
    if (isWindowShown) {
      Timber.i("Ignoring (is already shown)");
      return;
    } else {
      Timber.i("onWindowShown...");
    }
    isWindowShown = true;

    updateComposing();

    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onWindowShown();
    }
  }

  @Override
  public void onWindowHidden() {
    String methodName =
        "\t<TrimeInit>\t" + Thread.currentThread().getStackTrace()[2].getMethodName() + "\t";
    Timber.d(methodName);
    super.onWindowHidden();
    Timber.d(methodName + "super finish");
    if (!isWindowShown) {
      Timber.i("Ignoring (is already hidden)");
      return;
    } else {
      Timber.i("onWindowHidden...");
    }
    isWindowShown = false;

    if (getPrefs().getConf().getSyncBackgroundEnabled()) {
      final Message msg = new Message();
      msg.obj = this;
      syncBackgroundHandler.sendMessageDelayed(msg, 5000); // 输入面板隐藏5秒后，开始后台同步
    }

    Timber.d(methodName + "eventListeners");
    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onWindowHidden();
    }
  }

  private boolean isWinFixed() {
    return VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP
        || (popupWindowPos != PositionType.LEFT
            && popupWindowPos != PositionType.RIGHT
            && popupWindowPos != PositionType.LEFT_UP
            && popupWindowPos != PositionType.RIGHT_UP);
  }

  public void updatePopupWindow(final int offsetX, final int offsetY) {
    popupWindowPos = PositionType.DRAG;
    popupWindowX = offsetX;
    popupWindowY = offsetY;
    Timber.i("updatePopupWindow: winX = %s, winY = %s", popupWindowX, popupWindowY);
    mPopupWindow.update(popupWindowX, popupWindowY, -1, -1, true);
  }

  public void loadConfig() {
    final Config imeConfig = getImeConfig();
    popupWindowPos = imeConfig.getWinPos();
    isPopupWindowMovable = imeConfig.getString("layout/movable");
    popupMargin = imeConfig.getPixel("layout/spacing");
    minPopupSize = imeConfig.getInt("layout/min_length");
    minPopupCheckSize = imeConfig.getInt("layout/min_check");
    popupMarginH = imeConfig.getPixel("layout/real_margin");
    textInputManager.setShouldResetAsciiMode(imeConfig.getBoolean("reset_ascii_mode"));
    isAutoCaps = imeConfig.getBoolean("auto_caps");
    isPopupWindowEnabled =
        getPrefs().getKeyboard().getPopupWindowEnabled() && imeConfig.hasKey("window");
    textInputManager.setShouldUpdateRimeOption(true);
  }

  @SuppressWarnings("UnusedReturnValue")
  private boolean updateRimeOption() {
    try {
      if (textInputManager.getShouldUpdateRimeOption()) {
        Rime.setOption("soft_cursor", getPrefs().getKeyboard().getSoftCursorEnabled()); // 軟光標
        Rime.setOption("_horizontal", getImeConfig().getBoolean("horizontal")); // 水平模式
        textInputManager.setShouldUpdateRimeOption(false);
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  @Override
  public void onCreate() {

    StrictMode.setVmPolicy(
        new StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
            .detectLeakedClosableObjects()
            .build());
    String methodName =
        "\t<TrimeInit>\t" + Thread.currentThread().getStackTrace()[2].getMethodName() + "\t";
    Timber.d(methodName);
    // MUST WRAP all code within Service onCreate() in try..catch to prevent any crash loops
    try {
      // Additional try..catch wrapper as the event listeners chain or the super.onCreate() method
      // could crash
      //  and lead to a crash loop
      try {
        Timber.i("onCreate...");

        activeEditorInstance = new EditorInstance(this);
        imeManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        Timber.d(methodName + "InputFeedbackManager");
        inputFeedbackManager = new InputFeedbackManager(this);

        Timber.d(methodName + "keyboardSwitcher");

        keyboardSwitcher = new KeyboardSwitcher();

        Timber.d(methodName + "liquidKeyboard");
        liquidKeyboard =
            new LiquidKeyboard(
                this, getImeConfig().getClipboardLimit(), getImeConfig().getDraftLimit());
        clipBoardMonitor();
        DraftDao.get();
      } catch (Exception e) {
        e.printStackTrace();
        super.onCreate();
        return;
      }
      Timber.d(methodName + "super.onCreate()");
      super.onCreate();
      Timber.d(methodName + "create listener");
      for (EventListener listener : eventListeners) {
        if (listener != null) listener.onCreate();
      }
    } catch (Exception e) {
      e.fillInStackTrace();
    }
    Timber.d(methodName + "finish");
  }

  /**
   * 变更配置的暗黑模式开关
   *
   * @param darkMode 设置为暗黑模式
   * @return 模式实际上是否有发生变更
   */
  public boolean setDarkMode(boolean darkMode) {
    if (darkMode != this.darkMode) {
      Timber.i("setDarkMode " + darkMode);
      this.darkMode = darkMode;
      return true;
    }
    return false;
  }

  public void selectLiquidKeyboard(final int tabIndex) {
    final LinearLayout symbolInputView =
        inputRootBinding != null ? inputRootBinding.symbol.symbolInput : null;
    final LinearLayout mainInputView =
        inputRootBinding != null ? inputRootBinding.main.mainInput : null;
    if (symbolInputView != null) {
      if (tabIndex >= 0) {
        final LinearLayout.LayoutParams param =
            (LinearLayout.LayoutParams) symbolInputView.getLayoutParams();
        param.height = mainInputView.getHeight();
        symbolInputView.setVisibility(View.VISIBLE);

        final int orientation = getResources().getConfiguration().orientation;
        liquidKeyboard.setLand(orientation == Configuration.ORIENTATION_LANDSCAPE);
        liquidKeyboard.calcPadding(mainInputView.getWidth());
        liquidKeyboard.select(tabIndex);

        tabView.updateTabWidth();
        if (inputRootBinding != null) {
          mTabRoot.setBackground(mCandidateRoot.getBackground());
          mTabRoot.move(tabView.getHightlightLeft(), tabView.getHightlightRight());
        }
      } else symbolInputView.setVisibility(View.GONE);
    }
    if (mainInputView != null)
      mainInputView.setVisibility(tabIndex >= 0 ? View.GONE : View.VISIBLE);
  }

  // 按键需要通过tab name来打开liquidKeyboard的指定tab
  public void selectLiquidKeyboard(@NonNull String name) {
    if (name.matches("\\d+")) selectLiquidKeyboard(Integer.parseInt(name));
    else selectLiquidKeyboard(TabManager.getTagIndex(name));
  }

  public void pasteByChar() {
    final ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    final ClipData clipData = clipBoard.getPrimaryClip();

    final ClipData.Item item = clipData.getItemAt(0);
    if (item == null) return;

    final String text = item.coerceToText(self).toString();
    commitTextByChar(text);
  }

  public void invalidate() {
    Rime.get(this);
    getImeConfig().destroy();
    reset();
    textInputManager.setShouldUpdateRimeOption(true);
  }

  private void hideCompositionView() {
    if (isPopupWindowMovable != null && isPopupWindowMovable.equals("once")) {
      popupWindowPos = getImeConfig().getWinPos();
    }

    if (mPopupWindow != null && mPopupWindow.isShowing()) {
      mPopupWindow.dismiss();
      mPopupHandler.removeCallbacks(mPopupTimer);
    }
  }

  private void showCompositionView() {
    if (TextUtils.isEmpty(Rime.getCompositionText())) {
      hideCompositionView();
      return;
    }
    compositionRootBinding.compositionRoot.measure(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    mPopupWindow.setWidth(compositionRootBinding.compositionRoot.getMeasuredWidth());
    mPopupWindow.setHeight(compositionRootBinding.compositionRoot.getMeasuredHeight());
    mPopupHandler.post(mPopupTimer);
  }

  public void loadBackground() {
    final Config mConfig = getImeConfig();
    final int orientation = getResources().getConfiguration().orientation;

    if (mPopupWindow != null) {
      final Drawable textBackground =
          mConfig.getDrawable(
              "text_back_color",
              "layout/border",
              "border_color",
              "layout/round_corner",
              "layout/alpha");
      if (textBackground != null) mPopupWindow.setBackgroundDrawable(textBackground);
      if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
        mPopupWindow.setElevation(mConfig.getPixel("layout/elevation"));
    }

    if (mCandidateRoot != null) {
      final Drawable candidateBackground =
          mConfig.getDrawable(
              "candidate_background",
              "candidate_border",
              "candidate_border_color",
              "candidate_border_round",
              null);
      if (candidateBackground != null) mCandidateRoot.setBackground(candidateBackground);
    }

    if (inputRootBinding == null) return;

    int[] padding =
        mConfig.getKeyboardPadding(oneHandMode, orientation == Configuration.ORIENTATION_LANDSCAPE);
    Timber.i(
        "update KeyboardPadding: Trime.loadBackground, padding= %s %s %s, orientation=%s",
        padding[0], padding[1], padding[2], orientation);
    mainKeyboardView.setPadding(padding[0], 0, padding[1], padding[2]);

    final Drawable inputRootBackground = mConfig.getDrawable_("root_background");
    if (inputRootBackground != null) {
      inputRootBinding.inputRoot.setBackground(inputRootBackground);
    } else {
      // 避免因为键盘整体透明而造成的异常
      inputRootBinding.inputRoot.setBackgroundColor(Color.BLACK);
    }

    tabView.reset(self);
  }

  public void resetKeyboard() {
    if (mainKeyboardView != null) {
      mainKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));
      mainKeyboardView.setShowSymbol(!Rime.getOption("_hide_key_symbol"));
      mainKeyboardView.reset(this); // 實體鍵盤無軟鍵盤
    }
  }

  public void resetCandidate() {
    if (mCandidateRoot != null) {
      loadBackground();
      setShowComment(!Rime.getOption("_hide_comment"));
      mCandidateRoot.setVisibility(!Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);
      mCandidate.reset(this);
      isPopupWindowEnabled =
          getPrefs().getKeyboard().getPopupWindowEnabled() && getImeConfig().hasKey("window");
      mComposition.setVisibility(isPopupWindowEnabled ? View.VISIBLE : View.GONE);
      mComposition.reset(this);
    }
  }

  /** 重置鍵盤、候選條、狀態欄等 !!注意，如果其中調用Rime.setOption，切換方案會卡住 */
  private void reset() {
    if (inputRootBinding == null) return;
    final LinearLayout symbolInputView = inputRootBinding.symbol.symbolInput;
    final LinearLayout mainInputView = inputRootBinding.main.mainInput;
    if (symbolInputView != null) symbolInputView.setVisibility(View.GONE);
    if (mainInputView != null) mainInputView.setVisibility(View.VISIBLE);
    getImeConfig().reset();
    loadConfig();
    getImeConfig().initCurrentColors();
    getImeConfig().setSoundFromColor();
    if (keyboardSwitcher != null) keyboardSwitcher.newOrReset();
    resetCandidate();
    hideCompositionView();
    resetKeyboard();
  }

  /** Must be called on the UI thread */
  public void initKeyboard() {
    reset();
    setNavBarColor();
    textInputManager.setShouldUpdateRimeOption(true); // 不能在Rime.onMessage中調用set_option，會卡死
    bindKeyboardToInputView();
    // loadBackground(); // reset()调用过resetCandidate()，resetCandidate()一键调用过loadBackground();
    updateComposing(); // 切換主題時刷新候選
  }

  public void initKeyboardDarkMode(boolean darkMode) {
    if (getImeConfig().hasDarkLight()) {
      getImeConfig().reset();
      loadConfig();
      getImeConfig().initCurrentColors(darkMode);
      getImeConfig().setSoundFromColor();
      if (keyboardSwitcher != null) keyboardSwitcher.newOrReset();
      resetCandidate();
      hideCompositionView();
      resetKeyboard();

      setNavBarColor();
      textInputManager.setShouldUpdateRimeOption(true); // 不能在Rime.onMessage中調用set_option，會卡死
      bindKeyboardToInputView();
      // loadBackground(); // reset()调用过resetCandidate()，resetCandidate()一键调用过loadBackground();
      updateComposing(); // 切換主題時刷新候選
    }
  }

  @Override
  public void onDestroy() {
    if (mIntentReceiver != null) mIntentReceiver.unregisterReceiver(this);
    mIntentReceiver = null;
    if (inputFeedbackManager != null) inputFeedbackManager.destroy();
    inputFeedbackManager = null;
    inputRootBinding = null;
    imeManager = null;

    if (getPrefs().getOther().getDestroyOnQuit()) {
      Rime.destroy();
      getImeConfig().destroy();
      System.exit(0); // 清理內存
    }
    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onDestroy();
    }
    eventListeners.clear();
    super.onDestroy();

    self = null;
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    final Configuration config = getResources().getConfiguration();
    if (config != null) {
      if (config.orientation != newConfig.orientation) {
        // Clear composing text and candidates for orientation change.
        performEscape();
        config.orientation = newConfig.orientation;
      }
    }
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
    if (!isWinFixed()) {
      final CharSequence composingText = cursorAnchorInfo.getComposingText();
      // update mPopupRectF
      if (composingText == null) {
        // composing is disabled in target app or trime settings
        // use the position of the insertion marker instead
        mPopupRectF.top = cursorAnchorInfo.getInsertionMarkerTop();
        mPopupRectF.left = cursorAnchorInfo.getInsertionMarkerHorizontal();
        mPopupRectF.bottom = cursorAnchorInfo.getInsertionMarkerBottom();
        mPopupRectF.right = mPopupRectF.left;
      } else {
        final int startPos = cursorAnchorInfo.getComposingTextStart();
        final int endPos = startPos + composingText.length() - 1;
        final RectF startCharRectF = cursorAnchorInfo.getCharacterBounds(startPos);
        final RectF endCharRectF = cursorAnchorInfo.getCharacterBounds(endPos);
        if (startCharRectF == null || endCharRectF == null) {
          // composing text has been changed, the next onUpdateCursorAnchorInfo is on the road
          // ignore this outdated update
          return;
        }
        // for different writing system (e.g. right to left languages),
        // we have to calculate the correct RectF
        mPopupRectF.top = Math.min(startCharRectF.top, endCharRectF.top);
        mPopupRectF.left = Math.min(startCharRectF.left, endCharRectF.left);
        mPopupRectF.bottom = Math.max(startCharRectF.bottom, endCharRectF.bottom);
        mPopupRectF.right = Math.max(startCharRectF.right, endCharRectF.right);
      }
      cursorAnchorInfo.getMatrix().mapRect(mPopupRectF);
    }
    if (mCandidateRoot != null) {
      showCompositionView();
    }
  }

  @Override
  public void onUpdateSelection(
      int oldSelStart,
      int oldSelEnd,
      int newSelStart,
      int newSelEnd,
      int candidatesStart,
      int candidatesEnd) {
    super.onUpdateSelection(
        oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    if ((candidatesEnd != -1) && ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd))) {
      // 移動光標時，更新候選區
      if ((newSelEnd < candidatesEnd) && (newSelEnd >= candidatesStart)) {
        final int n = newSelEnd - candidatesStart;
        Rime.RimeSetCaretPos(n);
        updateComposing();
      }
    }
    if ((candidatesStart == -1 && candidatesEnd == -1) && (newSelStart == 0 && newSelEnd == 0)) {
      // 上屏後，清除候選區
      performEscape();
    }
    // Update the caps-lock status for the current cursor position.
    dispatchCapsStateToInputView();
  }

  @Override
  public void onComputeInsets(InputMethodService.Insets outInsets) {
    super.onComputeInsets(outInsets);
    outInsets.contentTopInsets = outInsets.visibleTopInsets;
  }

  @Override
  public View onCreateInputView() {
    Timber.e("onCreateInputView()");
    // 初始化键盘布局
    super.onCreateInputView();
    inputRootBinding = InputRootBinding.inflate(LayoutInflater.from(this));
    mainKeyboardView = inputRootBinding.main.mainKeyboardView;

    // 初始化候选栏
    mCandidateRoot = inputRootBinding.main.candidateView.candidateRoot;
    mCandidate = inputRootBinding.main.candidateView.candidates;

    // 候选词悬浮窗的容器
    compositionRootBinding = CompositionRootBinding.inflate(LayoutInflater.from(this));
    mComposition = compositionRootBinding.compositions;
    mPopupWindow = new PopupWindow(compositionRootBinding.compositionRoot);
    mPopupWindow.setClippingEnabled(false);
    mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      mPopupWindow.setWindowLayoutType(dialogType);
    }
    hideCompositionView();
    mTabRoot = inputRootBinding.symbol.tabView.tabRoot;

    liquidKeyboard.setView(inputRootBinding.symbol.liquidKeyboardView);
    tabView = inputRootBinding.symbol.tabView.tabs;

    for (EventListener listener : eventListeners) {
      assert inputRootBinding != null;
      if (listener != null) listener.onInitializeInputUi(inputRootBinding);
    }
    getImeConfig().initCurrentColors();
    loadBackground();

    if (keyboardSwitcher != null) keyboardSwitcher.newOrReset();
    Timber.i("onCreateInputView() finish");

    return inputRootBinding.inputRoot;
  }

  public void setShowComment(boolean show_comment) {
    // if (mCandidateRoot != null) mCandidate.setShowComment(show_comment);
    mComposition.setShowComment(show_comment);
  }

  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
    super.onStartInputView(attribute, restarting);
    if (getPrefs().getLooks().getAutoDark()) {
      UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
      if (setDarkMode(uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES)) {
        Timber.i("dark mode changed");
        initKeyboardDarkMode(darkMode);
      } else Timber.i("dark mode not changed");
    } else {
      Timber.i("auto dark off");
    }

    Sound.resetProgress();
    for (EventListener listener : eventListeners) {
      if (listener != null) listener.onStartInputView(activeEditorInstance, restarting);
    }
    if (getPrefs().getOther().getShowStatusBarIcon()) {
      showStatusIcon(R.drawable.ic_status); // 狀態欄圖標
    }
    bindKeyboardToInputView();
    if (!restarting) setNavBarColor();
    setCandidatesViewShown(!Rime.isEmpty()); // 軟鍵盤出現時顯示候選欄

    if ((attribute.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        == EditorInfo.IME_FLAG_NO_ENTER_ACTION) {
      mainKeyboardView.resetEnterLabel();
    } else {
      mainKeyboardView.setEnterLabel(
          attribute.imeOptions & EditorInfo.IME_MASK_ACTION, attribute.actionLabel);
    }

    switch (attribute.inputType & InputType.TYPE_MASK_VARIATION) {
      case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_PASSWORD:
      case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
      case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
      case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
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
                + (attribute.inputType & InputType.TYPE_MASK_VARIATION)
                + "; CLASS="
                + (attribute.inputType & InputType.TYPE_MASK_CLASS)
                + "; ACTION="
                + (attribute.imeOptions & EditorInfo.IME_MASK_ACTION));
        normalTextEditor = false;
        break;

      default:
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
                + (attribute.inputType & InputType.TYPE_MASK_VARIATION)
                + "; CLASS="
                + (attribute.inputType & InputType.TYPE_MASK_CLASS)
                + "; ACTION="
                + (attribute.imeOptions & EditorInfo.IME_MASK_ACTION));

        if ((attribute.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
            == EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) {
          //  应用程求以隐身模式打开键盘应用程序
          Timber.i("EditorInfo: normal -> private, IME_FLAG_NO_PERSONALIZED_LEARNING");
        } else {
          normalTextEditor = true;
          activeEditorInstance.cacheDraft();
          addDraft();
        }
    }
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    if (normalTextEditor) addDraft();
    super.onFinishInputView(finishingInput);
    // Dismiss any pop-ups when the input-view is being finished and hidden.
    mainKeyboardView.closing();
    performEscape();
    try {
      hideCompositionView();
    } catch (Exception e) {
      Timber.e(e, "Failed to show the PopupWindow.");
    }
  }

  public void bindKeyboardToInputView() {
    if (mainKeyboardView != null) {
      // Bind the selected keyboard to the input view.
      Keyboard sk = keyboardSwitcher.getCurrentKeyboard();
      mainKeyboardView.setKeyboard(sk);
      dispatchCapsStateToInputView();
    }
  }

  /**
   * Dispatches cursor caps info to input view in order to implement auto caps lock at the start of
   * a sentence.
   */
  private void dispatchCapsStateToInputView() {
    if ((isAutoCaps && Rime.isAsciiMode())
        && (mainKeyboardView != null && !mainKeyboardView.isCapsOn())) {
      mainKeyboardView.setShifted(false, activeEditorInstance.getCursorCapsMode() != 0);
    }
  }

  private boolean isComposing() {
    return Rime.isComposing();
  }

  public void commitText(String text) {
    activeEditorInstance.commitText(text, true);
  }

  public void commitTextByChar(String text) {
    for (int i = 1; i < text.length(); i++) {
      if (!activeEditorInstance.commitText(text.substring(i - 1, i))) break;
    }
  }

  /**
   * 如果爲{@link KeyEvent#KEYCODE_BACK Back鍵}，則隱藏鍵盤
   *
   * @param keyCode {@link KeyEvent#getKeyCode() 鍵碼}
   * @return 是否處理了Back鍵事件
   */
  private boolean handleBack(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
      requestHideSelf(0);
      return true;
    }
    return false;
  }

  public boolean onRimeKey(int[] event) {
    updateRimeOption();
    final boolean ret = Rime.onKey(event);
    activeEditorInstance.commitRimeText();
    return ret;
  }

  private boolean composeEvent(@NonNull KeyEvent event) {
    final int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_MENU) return false; // 不處理 Menu 鍵
    if (keyCode >= Key.getSymbolStart()) return false; // 只處理安卓標準按鍵
    if (event.getRepeatCount() == 0 && Key.isTrimeModifierKey(keyCode)) {
      boolean ret =
          onRimeKey(
              Event.getRimeEvent(
                  keyCode, event.getAction() == KeyEvent.ACTION_DOWN ? 0 : Rime.META_RELEASE_ON));
      if (isComposing()) setCandidatesViewShown(textInputManager.isComposable()); // 藍牙鍵盤打字時顯示候選欄
      return ret;
    }
    return textInputManager.isComposable() && !Rime.isVoidKeycode(keyCode);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Timber.i("\t<TrimeInput>\tonKeyDown()\tkeycode=%d, event=%s", keyCode, event.toString());
    if (composeEvent(event) && onKeyEvent(event)) return true;
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    Timber.i("\t<TrimeInput>\tonKeyUp()\tkeycode=%d, event=%s", keyCode, event.toString());
    if (composeEvent(event) && textInputManager.getNeedSendUpRimeKey()) {
      textInputManager.onRelease(keyCode);
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  /**
   * 處理實體鍵盤事件
   *
   * @param event {@link KeyEvent 按鍵事件}
   * @return 是否成功處理
   */
  // KeyEvent 处理实体键盘事件
  private boolean onKeyEvent(@NonNull KeyEvent event) {
    Timber.i("\t<TrimeInput>\tonKeyEvent()\tRealKeyboard event=%s", event.toString());
    int keyCode = event.getKeyCode();
    textInputManager.setNeedSendUpRimeKey(Rime.isComposing());
    if (!isComposing()) {
      if (keyCode == KeyEvent.KEYCODE_DEL
          || keyCode == KeyEvent.KEYCODE_ENTER
          || keyCode == KeyEvent.KEYCODE_ESCAPE
          || keyCode == KeyEvent.KEYCODE_BACK) {
        return false;
      }
    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
      keyCode = KeyEvent.KEYCODE_ESCAPE; // 返回鍵清屏
    }
    if (event.getAction() == KeyEvent.ACTION_DOWN
        && event.isCtrlPressed()
        && event.getRepeatCount() == 0
        && !KeyEvent.isModifierKey(keyCode)) {
      if (hookKeyboard(keyCode, event.getMetaState())) return true;
    }

    final int unicodeChar = event.getUnicodeChar();
    final String s = String.valueOf((char) unicodeChar);
    final int i = Event.getClickCode(s);
    int mask = 0;
    if (i > 0) {
      keyCode = i;
    } else { // 空格、回車等
      mask = event.getMetaState();
    }
    final boolean ret = handleKey(keyCode, mask);
    if (isComposing()) setCandidatesViewShown(textInputManager.isComposable()); // 藍牙鍵盤打字時顯示候選欄
    return ret;
  }

  public void switchToPrevIme() {
    try {
      if (VERSION.SDK_INT >= VERSION_CODES.P) {
        switchToPreviousInputMethod();
      } else {
        Window window = getWindow().getWindow();
        if (window != null) {
          if (imeManager != null) {
            imeManager.switchToLastInputMethod(window.getAttributes().token);
          }
        }
      }
    } catch (Exception e) {
      Timber.e(e, "Unable to switch to the previous IME.");
      if (imeManager != null) {
        imeManager.showInputMethodPicker();
      }
    }
  }

  public void switchToNextIme() {
    try {
      if (VERSION.SDK_INT >= VERSION_CODES.P) {
        switchToNextInputMethod(false);
      } else {
        Window window = getWindow().getWindow();
        if (window != null) {
          if (imeManager != null) {
            imeManager.switchToNextInputMethod(window.getAttributes().token, false);
          }
        }
      }
    } catch (Exception e) {
      Timber.e(e, "Unable to switch to the next IME.");
      if (imeManager != null) {
        imeManager.showInputMethodPicker();
      }
    }
  }

  // 处理键盘事件(Android keycode)
  public boolean handleKey(int keyEventCode, int metaState) { // 軟鍵盤
    textInputManager.setNeedSendUpRimeKey(false);
    if (onRimeKey(Event.getRimeEvent(keyEventCode, metaState))) {
      // 如果输入法消费了按键事件，则需要释放按键
      textInputManager.setNeedSendUpRimeKey(true);
      Timber.d(
          "\t<TrimeInput>\thandleKey()\trimeProcess, keycode=%d, metaState=%d",
          keyEventCode, metaState);
    } else if (hookKeyboard(keyEventCode, metaState)) {
      Timber.d("\t<TrimeInput>\thandleKey()\thookKeyboard, keycode=%d", keyEventCode);
    } else if (performEnter(keyEventCode) || handleBack(keyEventCode)) {
      // 处理返回键（隐藏软键盘）和回车键（换行）
      // todo 确认是否有必要单独处理回车键？是否需要把back和escape全部占用？
      Timber.d("\t<TrimeInput>\thandleKey()\tEnterOrHide, keycode=%d", keyEventCode);
    } else if (ShortcutUtils.INSTANCE.openCategory(keyEventCode)) {
      // 打开系统默认应用
      Timber.d("\t<TrimeInput>\thandleKey()\topenCategory keycode=%d", keyEventCode);
    } else {
      textInputManager.setNeedSendUpRimeKey(true);
      Timber.d(
          "\t<TrimeInput>\thandleKey()\treturn FALSE, keycode=%d, metaState=%d",
          keyEventCode, metaState);
      return false;
    }
    return true;
  }

  public boolean shareText() {
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      final @Nullable InputConnection ic = getCurrentInputConnection();
      if (ic == null) return false;
      CharSequence cs = ic.getSelectedText(0);
      if (cs == null) ic.performContextMenuAction(android.R.id.selectAll);
      return ic.performContextMenuAction(android.R.id.shareText);
    }
    return false;
  }

  private boolean hookKeyboard(int code, int mask) { // 編輯操作
    final @Nullable InputConnection ic = getCurrentInputConnection();
    if (ic == null) return false;
    if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {

      if (VERSION.SDK_INT >= VERSION_CODES.M) {
        if (getPrefs().getKeyboard().getHookCtrlZY()) {
          switch (code) {
            case KeyEvent.KEYCODE_Y:
              return ic.performContextMenuAction(android.R.id.redo);
            case KeyEvent.KEYCODE_Z:
              return ic.performContextMenuAction(android.R.id.undo);
          }
        }
      }
      switch (code) {
        case KeyEvent.KEYCODE_A:
          if (getPrefs().getKeyboard().getHookCtrlA())
            return ic.performContextMenuAction(android.R.id.selectAll);
          return false;
        case KeyEvent.KEYCODE_X:
          if (getPrefs().getKeyboard().getHookCtrlCV()) {
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              if (et.selectionEnd - et.selectionStart > 0)
                return ic.performContextMenuAction(android.R.id.cut);
            }
          }
          Timber.i("hookKeyboard cut fail");
          return false;
        case KeyEvent.KEYCODE_C:
          if (getPrefs().getKeyboard().getHookCtrlCV()) {
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              if (et.selectionEnd - et.selectionStart > 0)
                return ic.performContextMenuAction(android.R.id.copy);
            }
          }
          Timber.i("hookKeyboard copy fail");
          return false;
        case KeyEvent.KEYCODE_V:
          if (getPrefs().getKeyboard().getHookCtrlCV()) {
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et == null) {
              Timber.d("hookKeyboard paste, et == null, try commitText");
              if (ic.commitText(ShortcutUtils.INSTANCE.pasteFromClipboard(self), 1)) {
                return true;
              }
            } else if (ic.performContextMenuAction(android.R.id.paste)) {
              return true;
            }
            Timber.w("hookKeyboard paste fail");
          }
          return false;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          if (getPrefs().getKeyboard().getHookCtrlLR()) {
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              int move_to =
                  StringUtils.INSTANCE.findNextSection(et.text, et.startOffset + et.selectionEnd);
              ic.setSelection(move_to, move_to);
              return true;
            }
            break;
          }
        case KeyEvent.KEYCODE_DPAD_LEFT:
          if (getPrefs().getKeyboard().getHookCtrlLR()) {
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              int move_to =
                  StringUtils.INSTANCE.findPrevSection(et.text, et.startOffset + et.selectionStart);
              ic.setSelection(move_to, move_to);
              return true;
            }
            break;
          }
      }
    }
    return false;
  }

  /** 獲得當前漢字：候選字、選中字、剛上屏字/光標前字/光標前所有字、光標後所有字 */
  /*
  private String getActiveText(int type) {
    if (type == 2) return Rime.RimeGetInput(); // 當前編碼
    String s = Rime.getComposingText(); // 當前候選
    if (TextUtils.isEmpty(s)) {
      final InputConnection ic = getCurrentInputConnection();
      CharSequence cs = ic != null ? ic.getSelectedText(0) : null; // 選中字
      if (type == 1 && TextUtils.isEmpty(cs)) cs = lastCommittedText; // 剛上屏字
      if (TextUtils.isEmpty(cs) && ic != null) {
        cs = ic.getTextBeforeCursor(type == 4 ? 1024 : 1, 0); // 光標前字
      }
      if (TextUtils.isEmpty(cs) && ic != null) cs = ic.getTextAfterCursor(1024, 0); // 光標後面所有字
      if (cs != null) s = cs.toString();
    }
    return s;
  } */

  /** 更新Rime的中西文狀態、編輯區文本 */
  public void updateComposing() {
    final @Nullable InputConnection ic = getCurrentInputConnection();
    activeEditorInstance.updateComposingText();
    if (ic != null && !isWinFixed()) isCursorUpdated = ic.requestCursorUpdates(1);
    if (mCandidateRoot != null) {
      if (isPopupWindowEnabled) {
        final int startNum = mComposition.setWindow(minPopupSize, minPopupCheckSize);
        mCandidate.setText(startNum);
        // if isCursorUpdated, showCompositionView will be called in onUpdateCursorAnchorInfo
        // otherwise we need to call it here
        if (!isCursorUpdated) showCompositionView();
      } else {
        mCandidate.setText(0);
      }
      // 刷新候选词后，如果候选词超出屏幕宽度，滚动候选栏
      mTabRoot.move(mCandidate.getHighlightLeft(), mCandidate.getHighlightRight());
    }
    if (mainKeyboardView != null) mainKeyboardView.invalidateComposingKeys();
    if (!onEvaluateInputViewShown())
      setCandidatesViewShown(textInputManager.isComposable()); // 實體鍵盤打字時顯示候選欄
  }

  private void showDialog(@NonNull Dialog dialog) {
    final Window window = dialog.getWindow();
    final WindowManager.LayoutParams lp = window.getAttributes();
    lp.token = getWindow().getWindow().getDecorView().getWindowToken();
    lp.type = dialogType;
    window.setAttributes(lp);
    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    dialog.show();
  }

  /** 彈出{@link ColorPickerDialog 配色對話框} */
  public void showColorDialog() {
    new ColorPickerDialog(this).show();
  }

  /** 彈出{@link SchemaPickerDialog 輸入法方案對話框} */
  public void showSchemaDialog() {
    new SchemaPickerDialog(this).show();
  }

  /** 彈出{@link ThemePickerDialog 主題對話框} */
  public void showThemeDialog() {
    new ThemePickerDialog(this).show();
  }

  /** 彈出{@link SoundPickerDialog 音效包对话框} */
  public void showSoundDialog() {
    new SoundPickerDialog(this).show();
  }

  /** Hides the IME and launches {@link PrefMainActivity}. */
  public void launchSettings() {
    requestHideSelf(0);
    final Intent i = new Intent(this, PrefMainActivity.class);
    i.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    getApplicationContext().startActivity(i);
  }

  public void showOptionsDialog() {
    final AlertDialog.Builder dialogBuilder =
        new AlertDialog.Builder(this, R.style.AlertDialogTheme);
    dialogBuilder
        .setTitle(R.string.trime_app_name)
        .setIcon(R.mipmap.ic_app_icon)
        .setCancelable(true)
        .setNegativeButton(
            R.string.other_ime,
            (dialog, which) -> {
              dialog.dismiss();
              if (imeManager != null) {
                imeManager.showInputMethodPicker();
              }
            })
        .setPositiveButton(
            R.string.set_ime,
            (dialog, which) -> {
              launchSettings();
              dialog.dismiss();
            });
    if (Rime.get_current_schema().contentEquals(".default")) {
      dialogBuilder.setMessage(R.string.no_schemas);
    } else {
      dialogBuilder
          .setNegativeButton(
              R.string.pref_schemas,
              (dialog, which) -> {
                showSchemaDialog();
                dialog.dismiss();
              })
          .setSingleChoiceItems(
              Rime.getSchemaNames(),
              Rime.getSchemaIndex(),
              (dialog, id) -> {
                dialog.dismiss();
                Rime.selectSchema(id);
                textInputManager.setShouldUpdateRimeOption(true);
              });
    }
    showDialog(dialogBuilder.create());
  }

  /**
   * 如果爲{@link KeyEvent#KEYCODE_ENTER 回車鍵}，則換行
   *
   * @param keyCode {@link KeyEvent#getKeyCode() 鍵碼}
   * @return 是否處理了回車事件
   */
  private boolean performEnter(int keyCode) { // 回車
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      activeEditorInstance.cacheDraft();
      if (textInputManager.getPerformEnterAsLineBreak()) {
        commitText("\n");
      } else {
        sendKeyChar('\n');
      }
      return true;
    }
    return false;
  }

  /** 模擬PC鍵盤中Esc鍵的功能：清除輸入的編碼和候選項 */
  private void performEscape() {
    if (isComposing()) textInputManager.onKey(KeyEvent.KEYCODE_ESCAPE, 0);
  }

  private void setNavBarColor() {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      try {
        final Window window = getWindow().getWindow();
        @ColorInt final Integer keyboardBackColor = getImeConfig().getCurrentColor_("back_color");
        if (keyboardBackColor != null) {
          BarUtils.setNavBarColor(window, keyboardBackColor);
        }
      } catch (Exception e) {
        Timber.e(e);
      }
    }
  }

  @Override
  public boolean onEvaluateFullscreenMode() {
    final Configuration config = getResources().getConfiguration();
    if (config != null) {
      if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
        return false;
      } else {
        switch (getPrefs().getKeyboard().getFullscreenMode()) {
          case AUTO_SHOW:
            final EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0) {
              return false;
            }
          case ALWAYS_SHOW:
            return true;
          case NEVER_SHOW:
            return false;
        }
      }
    }
    return false;
  }

  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    updateSoftInputWindowLayoutParameters();
  }

  /** Updates the layout params of the window and input view. */
  private void updateSoftInputWindowLayoutParameters() {
    final Window w = getWindow().getWindow();
    if (w == null) return;
    final LinearLayout inputRoot = inputRootBinding != null ? inputRootBinding.inputRoot : null;
    if (inputRoot != null) {
      final int layoutHeight =
          isFullscreenMode()
              ? WindowManager.LayoutParams.WRAP_CONTENT
              : WindowManager.LayoutParams.MATCH_PARENT;
      final View inputArea = w.findViewById(android.R.id.inputArea);
      // TODO: 需要获取到文本编辑框、完成按钮，设置其色彩和尺寸。
      if (isFullscreenMode()) {
        Timber.i("isFullscreenMode");
        /* In Fullscreen mode, when layout contains transparent color,
         * the background under input area will disturb users' typing,
         * so set the input area as light pink */
        inputArea.setBackgroundColor(parseColor("#ff660000"));
      } else {
        Timber.i("NotFullscreenMode");
        /* Otherwise, set it as light gray to avoid potential issue */
        inputArea.setBackgroundColor(parseColor("#dddddddd"));
      }

      ViewUtils.updateLayoutHeightOf(inputArea, layoutHeight);
      ViewUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM);
      ViewUtils.updateLayoutHeightOf(inputRoot, layoutHeight);
    }
  }

  public boolean addEventListener(@NonNull EventListener listener) {
    return eventListeners.add(listener);
  }

  public boolean removeEventListener(@NonNull EventListener listener) {
    return eventListeners.remove(listener);
  }

  public interface EventListener {
    default void onCreate() {}

    default void onInitializeInputUi(@NonNull InputRootBinding uiBinding) {}

    default void onDestroy() {}

    default void onStartInputView(@NonNull EditorInstance instance, boolean restarting) {}

    default void osFinishInputView(boolean finishingInput) {}

    default void onWindowShown() {}

    default void onWindowHidden() {}

    default void onUpdateSelection() {}
  }

  private String ClipBoardString = "";

  /**
   * 此方法设置监听剪贴板变化，如有新的剪贴内容，就启动选定的剪贴板管理器
   *
   * <p>ClipBoardCompare 比较规则。每次通知剪贴板管理器，都会保存 ClipBoardCompare 处理过的 string。如果两次处理过的内容不变，则不通知。
   * ClipBoardOut 输出规则。如果剪贴板内容与规则匹配，则不通知剪贴板管理器。
   */
  private void clipBoardMonitor() {
    ClipboardDao.get();
    final ClipboardManager clipBoard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    final Config imeConfig = getImeConfig();
    clipBoard.addPrimaryClipChangedListener(
        () -> {
          if (imeConfig.getClipboardLimit() != 0) {
            final ClipData clipData = clipBoard.getPrimaryClip();
            if (clipData == null) return;
            final ClipData.Item item = clipData.getItemAt(0);
            if (item == null) return;

            final String rawText = item.coerceToText(self).toString();
            final String filteredText =
                StringUtils.replace(rawText, imeConfig.getClipBoardCompare());
            if (filteredText.length() < 1 || filteredText.equals(ClipBoardString)) return;

            if (StringUtils.mismatch(rawText, imeConfig.getClipBoardOutput())) {
              ClipBoardString = filteredText;
              liquidKeyboard.addClipboardData(rawText);
            }
          }
        });
  }

  private String draftString = "", draftCache = "";

  private void addDraft() {
    draftCache = activeEditorInstance.getDraftCache();
    if (draftCache.isEmpty() || draftCache.trim().equals(draftString)) return;

    if (getImeConfig().getDraftLimit() != 0) {
      Timber.i("addDraft() cache=%s, string=%s", draftString, draftCache);
      if (StringUtils.mismatch(draftCache, getImeConfig().getDraftOutput())) {
        draftString = draftCache.trim();
        liquidKeyboard.addDraftData(draftCache);
      }
    }
  }
}
