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

import android.app.AlertDialog;
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
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
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
import com.blankj.utilcode.util.BarUtils;
import com.osfans.trime.R;
import com.osfans.trime.Rime;
import com.osfans.trime.clipboard.ClipboardDao;
import com.osfans.trime.common.ViewUtils;
import com.osfans.trime.databinding.CompositionRootBinding;
import com.osfans.trime.databinding.InputRootBinding;
import com.osfans.trime.ime.enums.InlineModeType;
import com.osfans.trime.ime.enums.WindowsPositionType;
import com.osfans.trime.ime.keyboard.Event;
import com.osfans.trime.ime.keyboard.InputFeedbackManager;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.ime.keyboard.Keyboard;
import com.osfans.trime.ime.keyboard.KeyboardSwitcher;
import com.osfans.trime.ime.keyboard.KeyboardView;
import com.osfans.trime.ime.symbol.LiquidKeyboard;
import com.osfans.trime.ime.symbol.TabView;
import com.osfans.trime.ime.text.Candidate;
import com.osfans.trime.ime.text.Composition;
import com.osfans.trime.ime.text.ScrollView;
import com.osfans.trime.settings.PrefMainActivity;
import com.osfans.trime.settings.components.ColorPickerDialog;
import com.osfans.trime.settings.components.SchemaPickerDialog;
import com.osfans.trime.settings.components.ThemePickerDialog;
import com.osfans.trime.setup.Config;
import com.osfans.trime.setup.IntentReceiver;
import com.osfans.trime.util.ShortcutUtils;
import com.osfans.trime.util.StringUtils;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.jvm.Synchronized;
import timber.log.Timber;

/** {@link InputMethodService 輸入法}主程序 */
public class Trime extends InputMethodService
    implements KeyboardView.OnKeyboardActionListener, Candidate.EventListener {
  private static Trime self = null;
  private LiquidKeyboard liquidKeyboard;

  @NonNull
  private Preferences getPrefs() {
    return Preferences.Companion.defaultInstance();
  }

  /** 输入法配置 */
  @NonNull
  private Config getImeConfig() {
    return Config.get(this);
  }

  private KeyboardView mainKeyboardView; // 主軟鍵盤
  private KeyboardSwitcher keyboardSwitcher; // 键盘切换器

  private Candidate mCandidate; // 候選
  private Composition mComposition; // 編碼
  private CompositionRootBinding compositionRootBinding = null;
  private ScrollView mCandidateRoot;
  private TabView tabView;
  private InputRootBinding inputRootBinding = null;
  private AlertDialog mOptionsDialog; // 對話框
  public InputMethodManager imeManager = null;
  private InputFeedbackManager inputFeedbackManager = null; // 效果管理器
  private IntentReceiver mIntentReceiver = null;

  private boolean canCompose;
  private boolean enterAsLineBreak;
  private boolean mShowWindow = true; // 顯示懸浮窗口
  private String movable; // 候選窗口是否可移動
  private int winX, winY; // 候選窗座標
  private int candSpacing; // 候選窗與邊緣間距
  private boolean cursorUpdated = false; // 光標是否移動
  private int minPopupSize; // 上悬浮窗的候选词的最小词长
  private int minPopupCheckSize; // 第一屏候选词数量少于设定值，则候选词上悬浮窗。（也就是说，第一屏存在长词）此选项大于1时，min_length等参数失效
  private int realPopupMargin; // 悬浮窗与屏幕两侧的间距
  private boolean mTempAsciiMode; // 臨時中英文狀態
  private boolean mAsciiMode; // 默認中英文狀態
  private boolean resetAsciiMode; // 重置中英文狀態
  private String autoCaps; // 句首自動大寫
  private final Locale[] locales = new Locale[2];
  private boolean keyUpNeeded; // RIME是否需要處理keyUp事件
  private boolean mNeedUpdateRimeOption = true;
  private String lastCommittedText;

  private WindowsPositionType winPos; // 候選窗口彈出位置
  private InlineModeType inlinePreedit; // 嵌入模式
  private int oneHandMode = 0; // 单手键盘模式

  // compile regex once
  private static final Pattern pattern = Pattern.compile("^(\\{[^{}]+\\}).*$");
  private static final Pattern patternText = Pattern.compile("^((\\{Escape\\})?[^{}]+).*$");

  private final int dialogType =
      VERSION.SDK_INT >= VERSION_CODES.P
          ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
          : WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;

  private PopupWindow mPopupWindow;
  private RectF mPopupRectF = new RectF();
  private final Handler mPopupHandler = new Handler(Looper.getMainLooper());
  private final Runnable mPopupTimer =
      new Runnable() {
        @Override
        public void run() {
          if (mCandidateRoot == null || mCandidateRoot.getWindowToken() == null) return;
          if (!mShowWindow) return;
          int x, y;
          final int[] mParentLocation = ViewUtils.getLocationOnScreen(mCandidateRoot);
          final int xRight = mCandidateRoot.getWidth() - mPopupWindow.getWidth();
          final int yRight = mParentLocation[1] - mPopupWindow.getHeight() - candSpacing;
          if (isWinFixed() || !cursorUpdated) {
            // setCandidatesViewShown(true);
            switch (winPos) {
              case TOP_RIGHT:
                x = xRight;
                y = candSpacing;
                break;
              case TOP_LEFT:
                x = 0;
                y = candSpacing;
                break;
              case BOTTOM_RIGHT:
                x = xRight;
                y = yRight;
                break;
              case DRAG:
                x = winX;
                y = winY;
                break;
              case FIXED:
              case BOTTOM_LEFT:
              default:
                x = 0;
                y = yRight;
                break;
            }
          } else {
            // setCandidatesViewShown(false);
            if (winPos == WindowsPositionType.RIGHT || winPos == WindowsPositionType.RIGHT_UP) {
              // 此处存在bug，暂未梳理原有算法的问题，单纯根据真机横屏显示长候选词超出屏幕进行了修复
              // log： mCandidateContainer.getWidth()=1328  mFloatingWindow.getWidth()= 1874
              // 导致x结果为负，超出屏幕。
              x = Math.max(0, Math.min(xRight, (int) mPopupRectF.right));
            } else {
              x = Math.max(0, Math.min(xRight, (int) mPopupRectF.left));
            }

            if (winPos == WindowsPositionType.LEFT_UP || winPos == WindowsPositionType.RIGHT_UP) {
              y =
                  Math.max(
                      0,
                      Math.min(
                          yRight, (int) mPopupRectF.top - mPopupWindow.getHeight() - candSpacing));
            } else {
              // candSpacing 爲負時，可覆蓋部分鍵盤
              y = Math.max(0, Math.min(yRight, (int) mPopupRectF.bottom + candSpacing));
            }
          }
          y -= BarUtils.getStatusBarHeight(); // 不包含狀態欄

          if (x < realPopupMargin) x = realPopupMargin;

          if (!mPopupWindow.isShowing()) {
            mPopupWindow.showAtLocation(mCandidateRoot, Gravity.START | Gravity.TOP, x, y);
          } else {
            mPopupWindow.update(x, y, mPopupWindow.getWidth(), mPopupWindow.getHeight());
          }
        }
      };

  public Trime() {
    try {
      self = this;
    } catch (Exception e) {
      e.fillInStackTrace();
    }
  }

  @Synchronized
  @NonNull
  public static Trime getService() {
    assert self != null;
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

  @Override
  public void onWindowHidden() {
    if (getPrefs().getConf().getSyncBackgroundEnabled()) {
      final Message msg = new Message();
      msg.obj = this;
      syncBackgroundHandler.sendMessageDelayed(msg, 5000); // 输入面板隐藏5秒后，开始后台同步
    }
  }

  private boolean isWinFixed() {
    return VERSION.SDK_INT < VERSION_CODES.LOLLIPOP
        || (winPos != WindowsPositionType.LEFT
            && winPos != WindowsPositionType.RIGHT
            && winPos != WindowsPositionType.LEFT_UP
            && winPos != WindowsPositionType.RIGHT_UP);
  }

  public void updateWindow(final int offsetX, final int offsetY) {
    winPos = WindowsPositionType.DRAG;
    winX = offsetX;
    winY = offsetY;
    Timber.i("updateWindow: winX = %s, winY = %s", winX, winY);
    mPopupWindow.update(winX, winY, -1, -1, true);
  }

  public void loadConfig() {
    final Config imeConfig = getImeConfig();
    inlinePreedit = getPrefs().getKeyboard().getInlinePreedit();
    winPos = imeConfig.getWinPos();
    movable = imeConfig.getString("layout/movable");
    candSpacing = imeConfig.getPixel("layout/spacing");
    minPopupSize = imeConfig.getInt("layout/min_length");
    minPopupCheckSize = imeConfig.getInt("layout/min_check");
    realPopupMargin = imeConfig.getPixel("layout/real_margin");
    resetAsciiMode = imeConfig.getBoolean("reset_ascii_mode");
    autoCaps = imeConfig.getString("auto_caps");
    mShowWindow = getPrefs().getKeyboard().getFloatingWindowEnabled() && imeConfig.hasKey("window");
    mNeedUpdateRimeOption = true;
  }

  @SuppressWarnings("UnusedReturnValue")
  private boolean updateRimeOption() {
    try {
      if (mNeedUpdateRimeOption) {
        Rime.setOption("soft_cursor", getPrefs().getKeyboard().getSoftCursorEnabled()); // 軟光標
        Rime.setOption("_horizontal", getImeConfig().getBoolean("horizontal")); // 水平模式
        mNeedUpdateRimeOption = false;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  @Override
  public void onCreate() {
    // MUST WRAP all code within Service onCreate() in try..catch to prevent any crash loops
    try {
      // Additional try..catch wrapper as the event listeners chain or the super.onCreate() method
      // could crash
      //  and lead to a crash loop
      try {
        Timber.i("onCreate");
        imeManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputFeedbackManager = new InputFeedbackManager(this);
        mIntentReceiver = new IntentReceiver();
        mIntentReceiver.registerReceiver(this);

        final Config imeConfig = getImeConfig();
        mNeedUpdateRimeOption = true;
        loadConfig();
        keyboardSwitcher = new KeyboardSwitcher();

        String s =
            TextUtils.isEmpty(imeConfig.getString("locale")) ? imeConfig.getString("locale") : "";
        final String DELIMITER = "[-_]";
        if (s.contains(DELIMITER)) {
          final String[] lc = s.split(DELIMITER);
          if (lc.length == 3) {
            locales[0] = new Locale(lc[0], lc[1], lc[2]);
          } else {
            locales[0] = new Locale(lc[0], lc[1]);
          }
        } else {
          locales[0] = Locale.getDefault();
        }

        s =
            TextUtils.isEmpty(imeConfig.getString("latin_locale"))
                ? imeConfig.getString("latin_locale")
                : "en_US";
        if (s.contains(DELIMITER)) {
          final String[] lc = s.split(DELIMITER);
          if (lc.length == 3) {
            locales[1] = new Locale(lc[0], lc[1], lc[2]);
          } else {
            locales[1] = new Locale(lc[0], lc[1]);
          }
        } else {
          locales[0] = Locale.ENGLISH;
          locales[1] = new Locale(s);
        }

        liquidKeyboard = new LiquidKeyboard(this, imeConfig.getClipboardMaxSize());
        clipBoardMonitor();
      } catch (Exception e) {
        super.onCreate();
        e.fillInStackTrace();
        return;
      }
      super.onCreate();
    } catch (Exception e) {
      e.fillInStackTrace();
    }
  }

  public void onOptionChanged(@NonNull String option, boolean value) {
    switch (option) {
      case "ascii_mode":
        if (!mTempAsciiMode) mAsciiMode = value; // 切換中西文時保存狀態
        if (inputFeedbackManager != null)
          inputFeedbackManager.setTtsLanguage(locales[value ? 1 : 0]);
        break;
      case "_hide_comment":
        setShowComment(!value);
        break;
      case "_hide_candidate":
        if (mCandidateRoot != null) mCandidateRoot.setVisibility(!value ? View.VISIBLE : View.GONE);
        setCandidatesViewShown(canCompose && !value);
        break;
      case "_liquid_keyboard":
        selectLiquidKeyboard(value ? 0 : -1);
        break;
      case "_hide_key_hint":
        if (mainKeyboardView != null) mainKeyboardView.setShowHint(!value);
        break;
      default:
        if (option.startsWith("_keyboard_")
            && option.length() > 10
            && value
            && (keyboardSwitcher != null)) {
          final String keyboard = option.substring(10);
          keyboardSwitcher.switchToKeyboard(keyboard);
          mTempAsciiMode = keyboardSwitcher.getAsciiMode();
          bindKeyboardToInputView();
        } else if (option.startsWith("_key_") && option.length() > 5 && value) {
          boolean bNeedUpdate = mNeedUpdateRimeOption;
          if (bNeedUpdate) mNeedUpdateRimeOption = false; // 防止在 onMessage 中 setOption
          final String key = option.substring(5);
          onEvent(new Event(key));
          if (bNeedUpdate) mNeedUpdateRimeOption = true;
        } else if (option.matches("_liquid_keyboard_\\d+")) {
          selectLiquidKeyboard(Integer.parseInt(option.replace("_liquid_keyboard_", "")));
        } else if (option.startsWith("_one_hand_mode")) {
          char c = option.charAt(option.length() - 1);
          if (c == '1' && value) oneHandMode = 1;
          else if (c == '2' && value) oneHandMode = 2;
          else if (c == '3') oneHandMode = value ? 1 : 2;
          else oneHandMode = 0;
          loadBackground();
          initKeyboard();
        }
    }
    if (mainKeyboardView != null) mainKeyboardView.invalidateAllKeys();
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

        tabView.updateCandidateWidth();
        if (inputRootBinding != null) {
          inputRootBinding.symbol.symbolInput.setBackground(mCandidateRoot.getBackground());
        }
      } else symbolInputView.setVisibility(View.GONE);
    }
    if (mainInputView != null)
      mainInputView.setVisibility(tabIndex >= 0 ? View.GONE : View.VISIBLE);
  }

  public void invalidate() {
    Rime.get(this);
    getImeConfig().destroy();
    reset();
    mNeedUpdateRimeOption = true;
  }

  private void hideCompositionView() {
    if (movable.contentEquals("once")) {
      winPos = getImeConfig().getWinPos();
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
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    mPopupWindow.setWidth(compositionRootBinding.compositionRoot.getMeasuredWidth());
    mPopupWindow.setHeight(compositionRootBinding.compositionRoot.getMeasuredHeight());
    mPopupHandler.post(mPopupTimer);
  }

  private void loadBackground() {
    final Config imeConfig = getImeConfig();
    final int orientation = getResources().getConfiguration().orientation;
    final int[] padding =
        imeConfig.getKeyboardPadding(
            oneHandMode, orientation == Configuration.ORIENTATION_LANDSCAPE);
    Timber.i("padding= %s %s %s", padding[0], padding[1], padding[2]);
    mainKeyboardView.setPadding(padding[0], 0, padding[1], padding[2]);

    final Drawable d =
        imeConfig.getDrawable(
            "text_back_color",
            "layout/border",
            "border_color",
            "layout/round_corner",
            "layout/alpha");
    if (d != null) mPopupWindow.setBackgroundDrawable(d);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
      mPopupWindow.setElevation(imeConfig.getPixel("layout/elevation"));

    final Drawable d2 =
        imeConfig.getDrawable(
            "candidate_background",
            "candidate_border",
            "candidate_border_color",
            "candidate_border_round",
            null);

    if (d2 != null) mCandidateRoot.setBackground(d2);

    final Drawable d3 = imeConfig.getDrawable_("root_background");
    if (d3 != null) {
      inputRootBinding.inputRoot.setBackground(d3);
    } else {
      // 避免因为键盘整体透明而造成的异常
      inputRootBinding.inputRoot.setBackgroundColor(Color.WHITE);
    }

    tabView.reset(this);
  }

  public void resetKeyboard() {
    if (mainKeyboardView != null) {
      mainKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));
      mainKeyboardView.reset(this); // 實體鍵盤無軟鍵盤
    }
  }

  public void resetCandidate() {
    if (mCandidateRoot != null) {
      loadBackground();
      setShowComment(!Rime.getOption("_hide_comment"));
      mCandidateRoot.setVisibility(!Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);
      mCandidate.reset(this);
      mShowWindow =
          getPrefs().getKeyboard().getFloatingWindowEnabled() && getImeConfig().hasKey("window");
      mComposition.setVisibility(mShowWindow ? View.VISIBLE : View.GONE);
      mComposition.reset(this);
    }
  }

  /** 重置鍵盤、候選條、狀態欄等 !!注意，如果其中調用Rime.setOption，切換方案會卡住 */
  private void reset() {
    getImeConfig().reset();
    loadConfig();
    getImeConfig().initCurrentColors();
    if (keyboardSwitcher != null) keyboardSwitcher.newOrReset();
    resetCandidate();
    hideCompositionView();
    resetKeyboard();
  }

  public void initKeyboard() {
    reset();
    setNavBarColor();
    mNeedUpdateRimeOption = true; // 不能在Rime.onMessage中調用set_option，會卡死
    bindKeyboardToInputView();
    loadBackground();
    updateComposing(); // 切換主題時刷新候選
  }

  @Override
  public void onDestroy() {
    if (mIntentReceiver != null) mIntentReceiver.unregisterReceiver(this);
    mIntentReceiver = null;
    if (inputFeedbackManager != null) inputFeedbackManager.destroy();
    inputFeedbackManager = null;
    inputRootBinding = null;
    imeManager = null;
    self = null;
    if (getPrefs().getOther().getDestroyOnQuit()) {
      Rime.destroy();
      getImeConfig().destroy();
      System.exit(0); // 清理內存
    }
    super.onDestroy();
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
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      final int i = cursorAnchorInfo.getComposingTextStart();
      if ((winPos == WindowsPositionType.LEFT || winPos == WindowsPositionType.LEFT_UP) && i >= 0) {
        mPopupRectF = cursorAnchorInfo.getCharacterBounds(i);
      } else {
        mPopupRectF.left = cursorAnchorInfo.getInsertionMarkerHorizontal();
        mPopupRectF.top = cursorAnchorInfo.getInsertionMarkerTop();
        mPopupRectF.right = mPopupRectF.left;
        mPopupRectF.bottom = cursorAnchorInfo.getInsertionMarkerBottom();
      }
      cursorAnchorInfo.getMatrix().mapRect(mPopupRectF);
      if (mCandidateRoot != null) {
        showCompositionView();
      }
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
    updateCursorCapsToInputView();
  }

  @Override
  public void onComputeInsets(InputMethodService.Insets outInsets) {
    super.onComputeInsets(outInsets);
    outInsets.contentTopInsets = outInsets.visibleTopInsets;
  }

  @Override
  public View onCreateInputView() {
    // 初始化键盘布局
    inputRootBinding = InputRootBinding.inflate(LayoutInflater.from(this));
    mainKeyboardView = inputRootBinding.main.mainKeyboardView;
    mainKeyboardView.setOnKeyboardActionListener(this);
    mainKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));

    // 初始化候选栏
    mCandidateRoot = inputRootBinding.main.candidateView.getRoot();
    mCandidate = inputRootBinding.main.candidateView.candidates;
    mCandidate.setCandidateListener(this);
    mCandidateRoot.setPageStr(
        () -> handleKey(KeyEvent.KEYCODE_PAGE_DOWN, 0),
        () -> handleKey(KeyEvent.KEYCODE_PAGE_UP, 0));

    // 候选词悬浮窗的容器
    compositionRootBinding = CompositionRootBinding.inflate(LayoutInflater.from(this));
    hideCompositionView();
    mPopupWindow = new PopupWindow(compositionRootBinding.compositionRoot);
    mPopupWindow.setClippingEnabled(false);
    mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

    mComposition = (Composition) compositionRootBinding.compositionRoot.getChildAt(0);

    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      mPopupWindow.setWindowLayoutType(dialogType);
    }

    setShowComment(!Rime.getOption("_hide_comment"));
    mCandidateRoot.setVisibility(!Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);

    liquidKeyboard.setView(inputRootBinding.symbol.liquidKeyboardView);
    tabView = inputRootBinding.symbol.tabView.tab;
    loadBackground();

    return inputRootBinding.inputRoot;
  }

  void setShowComment(boolean show_comment) {
    if (mCandidateRoot != null) mCandidate.setShowComment(show_comment);
    mComposition.setShowComment(show_comment);
  }

  /**
   * 重置鍵盤、候選條、狀態欄等，進入文本框時通常會調用。
   *
   * @param attribute 文本框的{@link EditorInfo 屬性}
   * @param restarting 是否重啓
   */
  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    super.onStartInput(attribute, restarting);
    canCompose = false;
    enterAsLineBreak = false;
    mTempAsciiMode = false;
    final int inputType = attribute.inputType;
    final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
    final int variation = inputType & InputType.TYPE_MASK_VARIATION;
    String keyboard = null;
    switch (inputClass) {
      case InputType.TYPE_CLASS_NUMBER:
      case InputType.TYPE_CLASS_PHONE:
      case InputType.TYPE_CLASS_DATETIME:
        mTempAsciiMode = true;
        keyboard = "number";
        break;
      case InputType.TYPE_CLASS_TEXT:
        if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
          // Make enter-key as line-breaks for messaging.
          enterAsLineBreak = true;
        }
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
          mTempAsciiMode = true;
          keyboard = ".ascii";
        } else {
          canCompose = true;
        }
        break;
      default: // 0
        canCompose = (inputType > 0); // 0x80000 FX重命名文本框
        if (canCompose) break;
        return;
    }
    Rime.get(this);
    if (resetAsciiMode) mAsciiMode = false;

    keyboardSwitcher.resize(getMaxWidth()); // 橫豎屏切換時重置鍵盤

    // Select a keyboard based on the input type of the editing field.
    keyboardSwitcher.switchToKeyboard(keyboard);
    updateAsciiMode();
    canCompose = canCompose && !Rime.isEmpty();
    if (!onEvaluateInputViewShown()) setCandidatesViewShown(canCompose); // 實體鍵盤進入文本框時顯示候選欄
    if (getPrefs().getOther().getShowStatusBarIcon()) showStatusIcon(R.drawable.ic_status); // 狀態欄圖標
  }

  @Override
  public void showWindow(boolean showInput) {
    super.showWindow(showInput);
    updateComposing();
  }

  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
    super.onStartInputView(attribute, restarting);
    bindKeyboardToInputView();
    if (!restarting) setNavBarColor();
    setCandidatesViewShown(!Rime.isEmpty()); // 軟鍵盤出現時顯示候選欄
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
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

  private void bindKeyboardToInputView() {
    if (mainKeyboardView != null) {
      // Bind the selected keyboard to the input view.
      Keyboard sk = keyboardSwitcher.getCurrentKeyboard();
      mainKeyboardView.setKeyboard(sk);
      updateCursorCapsToInputView();
    }
  }

  // 句首自動大小寫
  private void updateCursorCapsToInputView() {
    if (autoCaps.contentEquals("false") || TextUtils.isEmpty(autoCaps)) return;
    if ((autoCaps.contentEquals("true") || Rime.isAsciiMode())
        && (mainKeyboardView != null && !mainKeyboardView.isCapsOn())) {
      @Nullable final InputConnection ic = getCurrentInputConnection();
      if (ic != null) {
        int caps = 0;
        @Nullable final EditorInfo ei = getCurrentInputEditorInfo();
        if ((ei != null) && (ei.inputType != EditorInfo.TYPE_NULL)) {
          caps = ic.getCursorCapsMode(ei.inputType);
        }
        mainKeyboardView.setShifted(false, caps != 0);
      }
    }
  }

  private boolean isComposing() {
    return Rime.isComposing();
  }

  /**
   * 指定字符串上屏
   *
   * @param text 要上屏的字符串
   */
  public void commitText(CharSequence text, boolean isRime) {
    if (text == null) return;
    if (inputFeedbackManager != null) inputFeedbackManager.textCommitSpeak(text);
    final @Nullable InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      ic.commitText(text, 1);
      lastCommittedText = text.toString();
    }
    if (isRime && !isComposing()) Rime.commitComposition(); // 自動上屏
    if (ic != null) ic.clearMetaKeyStates(KeyEvent.getModifierMetaStateMask()); // 黑莓刪除鍵清空文本框問題
  }

  public void commitText(CharSequence text) {
    commitText(text, true);
  }

  /**
   * 從 Rime 獲得字符串並上屏
   *
   * @return 是否成功上屏
   */
  private boolean commitText() {
    boolean r = Rime.getCommit();
    if (r) commitText(Rime.getCommitText());
    updateComposing();
    return r;
  }

  public void keyPressVibrate() {
    if (inputFeedbackManager != null) inputFeedbackManager.keyPressVibrate();
  }

  public void keyPressSound() {
    if (inputFeedbackManager != null) inputFeedbackManager.keyPressSound(0);
  }

  private boolean handleAction(int keyEventCode, int metaState) { // 編輯操作
    final InputConnection ic = getCurrentInputConnection();
    if (ic == null) return false;
    if ((metaState & KeyEvent.META_CTRL_ON) != 0) {
      // android.R.id. + selectAll, startSelectingText, stopSelectingText, cut, copy, paste,
      // copyUrl, or switchInputMethod
      if (VERSION.SDK_INT >= VERSION_CODES.M) {
        if (keyEventCode == KeyEvent.KEYCODE_V
            && (metaState & KeyEvent.META_ALT_ON) != 0
            && (metaState & KeyEvent.META_SHIFT_ON) != 0) {
          return ic.performContextMenuAction(android.R.id.pasteAsPlainText);
        }
        if (keyEventCode == KeyEvent.KEYCODE_S && (metaState & KeyEvent.META_ALT_ON) != 0) {
          CharSequence cs = ic.getSelectedText(0);
          if (cs == null) ic.performContextMenuAction(android.R.id.selectAll);
          return ic.performContextMenuAction(android.R.id.shareText);
        }
        switch (keyEventCode) {
          case KeyEvent.KEYCODE_Y:
            return ic.performContextMenuAction(android.R.id.redo);
          case KeyEvent.KEYCODE_Z:
            return ic.performContextMenuAction(android.R.id.undo);
        }
      }
      switch (keyEventCode) {
        case KeyEvent.KEYCODE_A:
          return ic.performContextMenuAction(android.R.id.selectAll);
        case KeyEvent.KEYCODE_X:
          return ic.performContextMenuAction(android.R.id.cut);
        case KeyEvent.KEYCODE_C:
          return ic.performContextMenuAction(android.R.id.copy);
        case KeyEvent.KEYCODE_V:
          return ic.performContextMenuAction(android.R.id.paste);
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          if (getPrefs().getOther().getSelectionSense()) {
            final ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            final ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              int nextPosition =
                  StringUtils.findNextSection(et.text, et.startOffset + et.selectionEnd);
              ic.setSelection(nextPosition, nextPosition);
              return true;
            }
            break;
          }
        case KeyEvent.KEYCODE_DPAD_LEFT:
          if (getPrefs().getOther().getSelectionSense()) {
            final ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              int prevSection =
                  StringUtils.findPrevSection(et.text, et.startOffset + et.selectionStart);
              ic.setSelection(prevSection, prevSection);
              return true;
            }
            break;
          }
      }
    }
    return false;
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

  private boolean onRimeKey(int[] event) {
    updateRimeOption();
    final boolean ret = Rime.onKey(event);
    commitText();
    return ret;
  }

  private boolean composeEvent(@NonNull KeyEvent event) {
    final int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_MENU) return false; // 不處理 Menu 鍵
    if (keyCode >= Key.getSymbolStart()) return false; // 只處理安卓標準按鍵
    if (event.getRepeatCount() == 0 && KeyEvent.isModifierKey(keyCode)) {
      boolean ret =
          onRimeKey(
              Event.getRimeEvent(
                  keyCode, event.getAction() == KeyEvent.ACTION_DOWN ? 0 : Rime.META_RELEASE_ON));
      if (isComposing()) setCandidatesViewShown(canCompose); // 藍牙鍵盤打字時顯示候選欄
      return ret;
    }
    return canCompose && !Rime.isVoidKeycode(keyCode);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Timber.i("onKeyDown = %s", event);
    if (composeEvent(event) && onKeyEvent(event)) return true;
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    Timber.i("onKeyUp = %s", event);
    if (composeEvent(event) && keyUpNeeded) {
      onRelease(keyCode);
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
  private boolean onKeyEvent(@NonNull KeyEvent event) {
    Timber.i("onKeyEvent = %s", event);
    int keyCode = event.getKeyCode();
    keyUpNeeded = isComposing();
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
      if (handleAction(keyCode, event.getMetaState())) return true;
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
    if (isComposing()) setCandidatesViewShown(canCompose); // 藍牙鍵盤打字時顯示候選欄
    return ret;
  }

  private @Nullable IBinder getToken() {
    final Window window = getWindow().getWindow();
    if (window == null) {
      return null;
    }
    return window.getAttributes().token;
  }

  @Override
  public void onEvent(@NonNull Event event) {
    final String commit = event.getCommit();
    if (!TextUtils.isEmpty(commit)) {
      commitText(commit, false); // 直接上屏，不發送給Rime
      return;
    }
    String s = event.getText();
    if (!TextUtils.isEmpty(s)) {
      onText(s);
      return;
    }
    if (event.getCode() > 0) {
      final int code = event.getCode();
      if (code == KeyEvent.KEYCODE_SWITCH_CHARSET) { // 切換狀態
        Rime.toggleOption(event.getToggle());
        commitText();
      } else if (code == KeyEvent.KEYCODE_EISU) { // 切換鍵盤
        keyboardSwitcher.switchToKeyboard(event.getSelect());
        // 根據鍵盤設定中英文狀態，不能放在 Rime.onMessage 中做
        mTempAsciiMode = keyboardSwitcher.getAsciiMode(); // 切換到西文鍵盤時不保存狀態
        updateAsciiMode();
        bindKeyboardToInputView();
        updateComposing();
      } else if (code == KeyEvent.KEYCODE_LANGUAGE_SWITCH) { // 切換輸入法
        final IBinder imeToken = getToken();
        if (imeManager != null) {
          if (event.getSelect().contentEquals(".next")) {
            imeManager.switchToNextInputMethod(imeToken, false);
          } else if (!TextUtils.isEmpty(event.getSelect())) {
            imeManager.switchToLastInputMethod(imeToken);
          } else {
            imeManager.showInputMethodPicker();
          }
        }
      } else if (code == KeyEvent.KEYCODE_FUNCTION) { // 命令直通車
        final String arg =
            String.format(
                event.getOption(),
                getActiveText(1),
                getActiveText(2),
                getActiveText(3),
                getActiveText(4));
        s = (String) ShortcutUtils.INSTANCE.call(this, event.getCommand(), arg);
        if (s != null) {
          commitText(s);
          updateComposing();
        }
      } else if (code == KeyEvent.KEYCODE_VOICE_ASSIST) { // 語音輸入
        new Speech(this).startListening();
      } else if (code == KeyEvent.KEYCODE_SETTINGS) { // 設定
        switch (event.getOption()) {
          case "theme":
            showThemeDialog();
            break;
          case "color":
            showColorDialog();
            break;
          case "schema":
            showSchemaDialog();
            break;
          default:
            launchSettings();
            break;
        }
      } else if (code == KeyEvent.KEYCODE_PROG_RED) { // 配色方案
        showColorDialog();
      } else {
        onKey(event.getCode(), event.getMask());
      }
    }
  }

  private boolean handleKey(int keyEventCode, int metaState) { // 軟鍵盤
    keyUpNeeded = false;
    if (onRimeKey(Event.getRimeEvent(keyEventCode, metaState))) {
      keyUpNeeded = true;
      Timber.i("Rime onKey");
    } else if (handleAction(keyEventCode, metaState)
        || handleOption(keyEventCode)
        || performEnter(keyEventCode)
        || handleBack(keyEventCode)) {
      Timber.i("Trime onKey");
    } else if (ShortcutUtils.INSTANCE.openCategory(keyEventCode)) {
      Timber.i("Open category");
    } else {
      keyUpNeeded = true;
      return false;
    }
    return true;
  }

  private boolean sendDownKeyEvent(long eventTime, int keyEventCode, int metaState) {
    final InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      return ic.sendKeyEvent(
          new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyEventCode, 0, metaState));
    }
    return false;
  }

  private boolean sendUpKeyEvent(long eventTime, int keyEventCode, int metaState) {
    final InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      return ic.sendKeyEvent(
          new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyEventCode, 0, metaState));
    }
    return false;
  }

  private boolean sendDownUpKeyEvent(int keyEventCode, int metaState) {
    final InputConnection ic = getCurrentInputConnection();
    if (ic == null) return false;
    final int states =
        KeyEvent.META_FUNCTION_ON
            | KeyEvent.META_SHIFT_MASK
            | KeyEvent.META_ALT_MASK
            | KeyEvent.META_CTRL_MASK
            | KeyEvent.META_META_MASK
            | KeyEvent.META_SYM_ON;
    ic.clearMetaKeyStates(states);
    int newMetaState = metaState;
    if (mainKeyboardView != null && mainKeyboardView.isShifted()) {
      if (keyEventCode == KeyEvent.KEYCODE_MOVE_HOME
          || keyEventCode == KeyEvent.KEYCODE_MOVE_END
          || keyEventCode == KeyEvent.KEYCODE_PAGE_UP
          || keyEventCode == KeyEvent.KEYCODE_PAGE_DOWN
          || (keyEventCode >= KeyEvent.KEYCODE_DPAD_UP
              && keyEventCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
        newMetaState |= KeyEvent.META_SHIFT_ON;
      }
    }
    ic.beginBatchEdit();
    final long eventTime = System.currentTimeMillis();
    if ((newMetaState & KeyEvent.META_SHIFT_ON) > 0) {
      sendDownKeyEvent(
          eventTime,
          KeyEvent.KEYCODE_SHIFT_LEFT,
          KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }
    if ((newMetaState & KeyEvent.META_CTRL_ON) > 0) {
      sendDownKeyEvent(
          eventTime,
          KeyEvent.KEYCODE_CTRL_LEFT,
          KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
    }
    if ((newMetaState & KeyEvent.META_ALT_ON) > 0) {
      sendDownKeyEvent(
          eventTime, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
    }

    boolean sendKeyDownUp = true;
    if (newMetaState == 0 && mAsciiMode) {
      // 使用ASCII键盘输入英文字符时，直接上屏，跳过复杂的调用，从表面上解决issue #301 知乎输入英语后输入法失去焦点的问题
      final String keyText = StringUtils.toCharString(keyEventCode);
      if (keyText.length() > 0) {
        ic.commitText(keyText, 1);
        sendKeyDownUp = false;
      }
    }

    if (sendKeyDownUp) {
      sendDownKeyEvent(eventTime, keyEventCode, newMetaState);
      sendUpKeyEvent(eventTime, keyEventCode, newMetaState);
    }

    if ((newMetaState & KeyEvent.META_ALT_ON) > 0) {
      sendUpKeyEvent(
          eventTime, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
    }
    if ((newMetaState & KeyEvent.META_CTRL_ON) > 0) {
      sendUpKeyEvent(
          eventTime,
          KeyEvent.KEYCODE_CTRL_LEFT,
          KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
    }
    if ((newMetaState & KeyEvent.META_SHIFT_ON) > 0) {
      sendUpKeyEvent(
          eventTime,
          KeyEvent.KEYCODE_SHIFT_LEFT,
          KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }
    ic.endBatchEdit();
    return true;
  }

  @Override
  public void onKey(int keyCode, int mask) { // 軟鍵盤
    if (handleKey(keyCode, mask)) return;
    if (keyCode >= Key.getSymbolStart()) { // 符號
      keyUpNeeded = false;
      commitText(Event.getDisplayLabel(keyCode));
      return;
    }
    keyUpNeeded = false;
    sendDownUpKeyEvent(keyCode, mask);
  }

  @Override
  public void onText(CharSequence text) { // 軟鍵盤
    Timber.i("onText = %s", text);
    if (inputFeedbackManager != null) inputFeedbackManager.keyPressSpeak(text);

    // Commit current composition before simulate key sequence
    if (!Rime.isValidText(text) && isComposing()) {
      Rime.commitComposition();
      commitText();
    }
    String s = text.toString();
    String t;
    while (s.length() > 0) {
      Matcher m = patternText.matcher(s);
      if (m.matches()) {
        t = m.group(1);
        Rime.onText(t);
        if (!commitText() && !isComposing()) commitText(t);
        updateComposing();
      } else {
        m = pattern.matcher(s);
        t = m.matches() ? m.group(1) : s.substring(0, 1);
        onEvent(new Event(t));
      }
      assert t != null;
      s = s.substring(t.length());
    }
    keyUpNeeded = false;
  }

  @Override
  public void onPress(int keyCode) {
    if (inputFeedbackManager != null) {
      inputFeedbackManager.keyPressVibrate();
      inputFeedbackManager.keyPressSound(keyCode);
      inputFeedbackManager.keyPressSpeak(keyCode);
    }
  }

  @Override
  public void onRelease(int keyCode) {
    if (keyUpNeeded) {
      onRimeKey(Event.getRimeEvent(keyCode, Rime.META_RELEASE_ON));
    }
  }

  @Override
  public void onCandidatePressed(int index) {
    // Commit the picked candidate and suggest its following words.
    onPress(0);
    if (!isComposing()) {
      if (index >= 0) {
        Rime.toggleOption(index);
        updateComposing();
      }
    } else if (index == -4) onKey(KeyEvent.KEYCODE_PAGE_UP, 0);
    else if (index == -5) onKey(KeyEvent.KEYCODE_PAGE_DOWN, 0);
    else // if (Rime.selectCandidate(index))
    {
      handleKey(KeyEvent.KEYCODE_1 + index, 0);
    }
  }

  /** 獲得當前漢字：候選字、選中字、剛上屏字/光標前字/光標前所有字、光標後所有字 */
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
  }

  /** 更新Rime的中西文狀態、編輯區文本 */
  public void updateComposing() {
    final @Nullable InputConnection ic = getCurrentInputConnection();
    if (inlinePreedit != InlineModeType.INLINE_NONE) { // 嵌入模式
      String s = "";
      switch (inlinePreedit) {
        case INLINE_PREVIEW:
          s = Rime.getComposingText();
          break;
        case INLINE_COMPOSITION:
          s = Rime.getCompositionText();
          break;
        case INLINE_INPUT:
          s = Rime.RimeGetInput();
          break;
      }
      if (ic != null) {
        @Nullable final CharSequence cs = ic.getSelectedText(0);
        if (cs == null || !TextUtils.isEmpty(s)) {
          // 無選中文本或編碼不爲空時更新編輯區
          ic.setComposingText(s, 1);
        }
      }
    }
    if (ic != null && !isWinFixed()) cursorUpdated = ic.requestCursorUpdates(1);
    if (mCandidateRoot != null) {
      if (mShowWindow) {
        final int startNum = mComposition.setWindow(minPopupSize, minPopupCheckSize);
        mCandidate.setText(startNum);
        if (isWinFixed() || !cursorUpdated) showCompositionView();
      } else {
        mCandidate.setText(0);
      }
    }
    if (mainKeyboardView != null) mainKeyboardView.invalidateComposingKeys();
    if (!onEvaluateInputViewShown()) setCandidatesViewShown(canCompose); // 實體鍵盤打字時顯示候選欄
  }

  private void showDialog(@NonNull AlertDialog dialog) {
    final Window window = dialog.getWindow();
    final WindowManager.LayoutParams lp = window.getAttributes();
    if (mCandidateRoot != null) lp.token = getToken();
    lp.type = dialogType;
    window.setAttributes(lp);
    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    dialog.show();
  }

  /** 彈出{@link ColorPickerDialog 配色對話框} */
  private void showColorDialog() {
    AlertDialog dialog = new ColorPickerDialog(this).getPickerDialog();
    showDialog(dialog);
  }

  /** 彈出{@link SchemaPickerDialog 輸入法方案對話框} */
  private void showSchemaDialog() {
    new SchemaPickerDialog(this, mCandidateRoot.getWindowToken()).show();
  }

  /** 彈出{@link ThemePickerDialog 主題對話框} */
  private void showThemeDialog() {
    new ThemePickerDialog(this, mCandidateRoot.getWindowToken()).show();
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

  private boolean handleOption(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_MENU) {
      if (mOptionsDialog != null && mOptionsDialog.isShowing()) return true; // 對話框單例
      AlertDialog.Builder builder =
          new AlertDialog.Builder(this)
              .setTitle(R.string.trime_app_name)
              .setIcon(R.mipmap.ic_app_icon_round)
              .setCancelable(true)
              .setNegativeButton(
                  R.string.other_ime,
                  (di, id) -> {
                    di.dismiss();
                    if (imeManager != null) imeManager.showInputMethodPicker();
                  })
              .setPositiveButton(
                  R.string.set_ime,
                  (di, id) -> {
                    launchSettings(); // 全局設置
                    di.dismiss();
                  });
      if (Rime.isEmpty()) builder.setMessage(R.string.no_schemas); // 提示安裝碼表
      else {
        builder.setNeutralButton(
            R.string.pref_schemas,
            (di, id) -> {
              showSchemaDialog(); // 部署方案
              di.dismiss();
            });
        builder.setSingleChoiceItems(
            Rime.getSchemaNames(),
            Rime.getSchemaIndex(),
            (di, id) -> {
              di.dismiss();
              Rime.selectSchema(id); // 切換方案
              mNeedUpdateRimeOption = true;
            });
      }
      mOptionsDialog = builder.create();
      showDialog(mOptionsDialog);
      return true;
    }
    return false;
  }

  /**
   * 如果爲{@link KeyEvent#KEYCODE_ENTER 回車鍵}，則換行
   *
   * @param keyCode {@link KeyEvent#getKeyCode() 鍵碼}
   * @return 是否處理了回車事件
   */
  private boolean performEnter(int keyCode) { // 回車
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      if (enterAsLineBreak) {
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
    if (isComposing()) onKey(KeyEvent.KEYCODE_ESCAPE, 0);
  }

  /** 更新Rime的中西文狀態 */
  private void updateAsciiMode() {
    Rime.setOption("ascii_mode", mTempAsciiMode || mAsciiMode);
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
          if (imeConfig.getClipboardMaxSize() != 0) {
            final ClipData clipData = clipBoard.getPrimaryClip();
            final ClipData.Item item = clipData.getItemAt(0);
            if (item == null) return;
            final String text = item.coerceToText(self).toString();

            final String text2 = StringUtils.replace(text, imeConfig.getClipBoardCompare());
            if (text2.length() < 1 || text2.equals(ClipBoardString)) return;

            if (StringUtils.mismatch(text, imeConfig.getClipBoardOutput())) {
              ClipBoardString = text2;
              liquidKeyboard.addClipboardData(text);
            }
          }
        });
  }
}
