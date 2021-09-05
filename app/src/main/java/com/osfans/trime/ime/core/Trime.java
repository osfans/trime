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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.blankj.utilcode.util.BarUtils;
import com.osfans.trime.R;
import com.osfans.trime.Rime;
import com.osfans.trime.databinding.CompositionContainerBinding;
import com.osfans.trime.databinding.InputRootBinding;
import com.osfans.trime.ime.SymbolKeyboard.ClipboardDao;
import com.osfans.trime.ime.SymbolKeyboard.LiquidKeyboard;
import com.osfans.trime.ime.SymbolKeyboard.TabView;
import com.osfans.trime.ime.enums.InlineModeType;
import com.osfans.trime.ime.enums.WindowsPositionType;
import com.osfans.trime.ime.keyboard.Event;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.ime.keyboard.Keyboard;
import com.osfans.trime.ime.keyboard.KeyboardSwitch;
import com.osfans.trime.ime.keyboard.KeyboardView;
import com.osfans.trime.ime.text.Candidate;
import com.osfans.trime.ime.text.Composition;
import com.osfans.trime.ime.text.ScrollView;
import com.osfans.trime.settings.PrefMainActivity;
import com.osfans.trime.settings.components.ColorPickerDialog;
import com.osfans.trime.settings.components.SchemaPickerDialog;
import com.osfans.trime.settings.components.ThemePickerDialog;
import com.osfans.trime.setup.Config;
import com.osfans.trime.setup.IntentReceiver;
import com.osfans.trime.util.Function;
import com.osfans.trime.util.LocaleUtils;
import com.osfans.trime.util.StringUitls;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import timber.log.Timber;

/** {@link InputMethodService 輸入法}主程序 */
public class Trime extends InputMethodService
    implements KeyboardView.OnKeyboardActionListener, Candidate.CandidateListener {
  private static Trime self;
  private LiquidKeyboard liquidKeyboard;

  @NonNull
  private Preferences getPrefs() {
    return Preferences.Companion.defaultInstance();
  }

  private KeyboardView mKeyboardView; // 軟鍵盤
  private KeyboardSwitch mKeyboardSwitch;
  private Config mConfig; // 配置
  @Nullable private TrimeKeyEffects effectManager = null; // 效果管理器
  private Candidate mCandidate; // 候選
  private Composition mComposition; // 編碼
  private CompositionContainerBinding compositionContainerBinding;
  private ScrollView mCandidateContainer;
  private View mainKeyboard, symbleKeyboard;
  private TabView tabView;
  private InputRootBinding inputRootBinding;
  private PopupWindow mFloatingWindow;
  private final PopupTimer mFloatingWindowTimer = new PopupTimer();
  private RectF mPopupRectF = new RectF();
  private AlertDialog mOptionsDialog; // 對話框
  @Nullable public InputMethodManager imeManager = null;

  private int orientation;
  private boolean canCompose;
  private boolean enterAsLineBreak;
  private boolean mShowWindow = true; // 顯示懸浮窗口
  private String movable; // 候選窗口是否可移動
  private int winX, winY; // 候選窗座標
  private int candSpacing; // 候選窗與邊緣間距
  private boolean cursorUpdated = false; // 光標是否移動
  private int min_length; // 上悬浮窗的候选词的最小词长
  private int min_check; // 第一屏候选词数量少于设定值，则候选词上悬浮窗。（也就是说，第一屏存在长词）此选项大于1时，min_length等参数失效
  private int real_margin; // 悬浮窗与屏幕两侧的间距
  private boolean mTempAsciiMode; // 臨時中英文狀態
  private boolean mAsciiMode; // 默認中英文狀態
  private boolean reset_ascii_mode; // 重置中英文狀態
  private String auto_caps; // 句首自動大寫
  private final Locale[] locales = new Locale[2];
  private boolean keyUpNeeded; // RIME是否需要處理keyUp事件
  private boolean mNeedUpdateRimeOption = true;
  private String lastCommittedText;

  private WindowsPositionType winPos; // 候選窗口彈出位置
  private InlineModeType inlinePreedit; // 嵌入模式
  private int one_hand_mode = 0; // 单手键盘模式

  // compile regex once
  private static final Pattern pattern = Pattern.compile("^(\\{[^{}]+\\}).*$");
  private static final Pattern patternText = Pattern.compile("^((\\{Escape\\})?[^{}]+).*$");

  @Nullable private IntentReceiver mIntentReceiver = null;
  private static final Handler syncBackgroundHandler =
      new Handler(
          msg -> {
            if (!((Trime) msg.obj).isShowInputRequested()) { // 若当前没有输入面板，则后台同步。防止面板关闭后5秒内再次打开
              Function.syncBackground((Trime) msg.obj);
              ((Trime) msg.obj).loadConfig();
            }
            return false;
          });

  @Override
  public void onWindowHidden() {
    if (getPrefs().getConf().getSyncBackgroundEnabled()) {
      Message msg = new Message();
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

  public void updateWindow(int offsetX, int offsetY) {
    winPos = WindowsPositionType.DRAG;
    winX = offsetX;
    winY = offsetY;
    Timber.i("updateWindow: winX = %s, winY = %s", winX, winY);
    mFloatingWindow.update(winX, winY, -1, -1, true);
  }

  @NonNull
  public static int[] getLocationOnScreen(@NonNull View v) {
    final int[] position = new int[2];
    v.getLocationOnScreen(position);
    return position;
  }

  @SuppressLint("HandlerLeak")
  private class PopupTimer extends Handler implements Runnable {
    public PopupTimer() {
      super(Looper.getMainLooper());
    }

    void postShowFloatingWindow() {
      if (TextUtils.isEmpty(Rime.getCompositionText())) {
        hideComposition();
        return;
      }
      compositionContainerBinding.compositionContainer.measure(
          LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      mFloatingWindow.setWidth(compositionContainerBinding.compositionContainer.getMeasuredWidth());
      mFloatingWindow.setHeight(
          compositionContainerBinding.compositionContainer.getMeasuredHeight());
      post(this);
    }

    void cancelShowing() {
      if (null != mFloatingWindow && mFloatingWindow.isShowing()) mFloatingWindow.dismiss();
      removeCallbacks(this);
    }

    @Override
    public void run() {
      if (mCandidateContainer == null || mCandidateContainer.getWindowToken() == null) return;
      if (!mShowWindow) return;
      int x, y;
      final int[] mParentLocation = getLocationOnScreen(mCandidateContainer);
      if (isWinFixed() || !cursorUpdated) {
        // setCandidatesViewShown(true);
        switch (winPos) {
          case TOP_RIGHT:
            x = mCandidateContainer.getWidth() - mFloatingWindow.getWidth();
            y = candSpacing;
            break;
          case TOP_LEFT:
            x = 0;
            y = candSpacing;
            break;
          case BOTTOM_RIGHT:
            x = mCandidateContainer.getWidth() - mFloatingWindow.getWidth();
            y = mParentLocation[1] - mFloatingWindow.getHeight() - candSpacing;
            break;
          case DRAG:
            x = winX;
            y = winY;
            break;
          case FIXED:
          case BOTTOM_LEFT:
          default:
            x = 0;
            y = mParentLocation[1] - mFloatingWindow.getHeight() - candSpacing;
            break;
        }
      } else {
        // setCandidatesViewShown(false);
        x = (int) mPopupRectF.left;
        if (winPos == WindowsPositionType.RIGHT || winPos == WindowsPositionType.RIGHT_UP) {
          x = (int) mPopupRectF.right;
        }
        y = (int) mPopupRectF.bottom + candSpacing;
        if (winPos == WindowsPositionType.LEFT_UP || winPos == WindowsPositionType.RIGHT_UP) {
          y = (int) mPopupRectF.top - mFloatingWindow.getHeight() - candSpacing;
        }
      }
      if (x < 0) x = 0;
      if (x > mCandidateContainer.getWidth() - mFloatingWindow.getWidth()) {
        //        此处存在bug，暂未梳理原有算法的问题，单纯根据真机横屏显示长候选词超出屏幕进行了修复
        //        log： mCandidateContainer.getWidth()=1328  mFloatingWindow.getWidth()= 1874
        // 导致x结果为负，超出屏幕。
        x = mCandidateContainer.getWidth() - mFloatingWindow.getWidth();
        if (x < 0) x = 0;
      }
      if (y < 0) y = 0;
      if (y
          > mParentLocation[1]
              - mFloatingWindow.getHeight()
              - candSpacing) { // candSpacing爲負時，可覆蓋部分鍵盤
        y = mParentLocation[1] - mFloatingWindow.getHeight() - candSpacing;
      }
      y -= BarUtils.getStatusBarHeight(); // 不包含狀態欄

      if (x < real_margin) x = real_margin;

      if (!mFloatingWindow.isShowing()) {
        mFloatingWindow.showAtLocation(mCandidateContainer, Gravity.START | Gravity.TOP, x, y);
      } else {
        mFloatingWindow.update(x, y, mFloatingWindow.getWidth(), mFloatingWindow.getHeight());
      }
    }
  }

  public void loadConfig() {
    inlinePreedit = getPrefs().getKeyboard().getInlinePreedit();
    winPos = mConfig.getWinPos();
    movable = mConfig.getString("layout/movable");
    candSpacing = mConfig.getPixel("layout/spacing");
    min_length = mConfig.getInt("layout/min_length");
    min_check = mConfig.getInt("layout/min_check");
    real_margin = mConfig.getPixel("layout/real_margin");
    reset_ascii_mode = mConfig.getBoolean("reset_ascii_mode");
    auto_caps = mConfig.getString("auto_caps");
    mShowWindow = getPrefs().getKeyboard().getFloatingWindowEnabled() && mConfig.hasKey("window");
    mNeedUpdateRimeOption = true;
  }

  @SuppressWarnings("UnusedReturnValue")
  private boolean updateRimeOption() {
    try {
      if (mNeedUpdateRimeOption) {
        Rime.setOption("soft_cursor", getPrefs().getKeyboard().getSoftCursorEnabled()); // 軟光標
        Rime.setOption("_horizontal", mConfig.getBoolean("horizontal")); // 水平模式
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
    super.onCreate();
    self = this;
    imeManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    effectManager = new TrimeKeyEffects(this);
    mIntentReceiver = new IntentReceiver();
    mIntentReceiver.registerReceiver(this);

    mConfig = Config.get(this);
    mNeedUpdateRimeOption = true;
    loadConfig();
    mKeyboardSwitch = new KeyboardSwitch(this);

    @Nullable String s;
    s = mConfig.getString("locale");
    if (TextUtils.isEmpty(s)) s = "";
    locales[0] = LocaleUtils.INSTANCE.stringToLocale(s);
    if (locales[0].equals(new Locale(s))) locales[0] = Locale.getDefault();

    s = mConfig.getString("latin_locale");
    if (TextUtils.isEmpty(s)) s = "en_US";
    locales[1] = LocaleUtils.INSTANCE.stringToLocale(s);
    if (locales[1].equals(new Locale(s))) locales[0] = Locale.ENGLISH;
    /*
    String[] ss;
    s = mConfig.getString("locale");
    if (Function.isEmpty(s)) s = "";
    ss = s.split("[-_]");
    if (ss.length == 2) locales[0] = new Locale(ss[0], ss[1]);
    else if (ss.length == 3) locales[0] = new Locale(ss[0], ss[1], ss[2]);
    else locales[0] = Locale.getDefault();
    s = mConfig.getString("latin_locale");
    if (Function.isEmpty(s)) s = "en_US";
    ss = s.split("[-_]");
    if (ss.length == 1) locales[1] = new Locale(ss[0]);
    else if (ss.length == 2) locales[1] = new Locale(ss[0], ss[1]);
    else if (ss.length == 3) locales[1] = new Locale(ss[0], ss[1], ss[2]);
    else locales[0] = Locale.ENGLISH; **/

    orientation = getResources().getConfiguration().orientation;
    // Use the following line to debug IME service.
    // android.os.Debug.waitForDebugger();

    liquidKeyboard = new LiquidKeyboard(this, mConfig.getClipboardMaxSize());
    clipBoardMonitor();
  }

  public void onOptionChanged(@NonNull String option, boolean value) {
    switch (option) {
      case "ascii_mode":
        if (!mTempAsciiMode) mAsciiMode = value; // 切換中西文時保存狀態
        if (effectManager != null) effectManager.setTtsLanguage(locales[value ? 1 : 0]);
        break;
      case "_hide_comment":
        setShowComment(!value);
        break;
      case "_hide_candidate":
        if (mCandidateContainer != null)
          mCandidateContainer.setVisibility(!value ? View.VISIBLE : View.GONE);
        setCandidatesViewShown(canCompose && !value);
        break;
      case "_liquid_keyboard":
        selectLiquidKeyboard(value ? 0 : -1);
        break;
      case "_hide_key_hint":
        if (mKeyboardView != null) mKeyboardView.setShowHint(!value);
        break;
      default:
        if (option.startsWith("_keyboard_")
            && option.length() > 10
            && value
            && (mKeyboardSwitch != null)) {
          final String keyboard = option.substring(10);
          mKeyboardSwitch.setKeyboard(keyboard);
          mTempAsciiMode = mKeyboardSwitch.getAsciiMode();
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
          if (c == '1' && value) one_hand_mode = 1;
          else if (c == '2' && value) one_hand_mode = 2;
          else if (c == '3') one_hand_mode = value ? 1 : 2;
          else one_hand_mode = 0;
          loadBackground();
          initKeyboard();
        }
    }
    if (mKeyboardView != null) mKeyboardView.invalidateAllKeys();
  }

  public void selectLiquidKeyboard(int tabIndex) {
    if (symbleKeyboard != null) {
      if (tabIndex >= 0) {
        LinearLayout.LayoutParams param =
            (LinearLayout.LayoutParams) symbleKeyboard.getLayoutParams();
        param.height = mainKeyboard.getHeight();
        symbleKeyboard.setVisibility(View.VISIBLE);

        liquidKeyboard.setLand(orientation == Configuration.ORIENTATION_LANDSCAPE);
        liquidKeyboard.calcPadding(mainKeyboard.getWidth());
        liquidKeyboard.select(tabIndex);

        tabView.updateCandidateWidth();
        inputRootBinding.scroll2.setBackground(mCandidateContainer.getBackground());
      } else symbleKeyboard.setVisibility(View.GONE);
    }
    if (mainKeyboard != null) mainKeyboard.setVisibility(tabIndex >= 0 ? View.GONE : View.VISIBLE);
  }

  public void invalidate() {
    Rime.get(this);
    if (mConfig != null) mConfig.destroy();
    mConfig = Config.get(this);
    reset();
    mNeedUpdateRimeOption = true;
  }

  private void hideComposition() {
    if (movable.contentEquals("once")) winPos = mConfig.getWinPos();
    mFloatingWindowTimer.cancelShowing();
  }

  private void loadBackground() {
    int[] padding =
        mConfig.getKeyboardPadding(
            one_hand_mode, orientation == Configuration.ORIENTATION_LANDSCAPE);
    Timber.i("padding= %s %s %s", padding[0], padding[1], padding[2]);
    mKeyboardView.setPadding(padding[0], 0, padding[1], padding[2]);

    final Drawable d =
        mConfig.getDrawable(
            "text_back_color",
            "layout/border",
            "border_color",
            "layout/round_corner",
            "layout/alpha");
    if (d != null) mFloatingWindow.setBackgroundDrawable(d);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
      mFloatingWindow.setElevation(mConfig.getPixel("layout/elevation"));

    final Drawable d2 =
        mConfig.getDrawable(
            "candidate_background",
            "candidate_border",
            "candidate_border_color",
            "candidate_border_round",
            null);

    if (d2 != null) mCandidateContainer.setBackground(d2);

    final Drawable d3 = mConfig.getDrawable_("root_background");
    if (d3 != null) {
      inputRootBinding.inputRoot.setBackground(d3);
    } else {
      // 避免因为键盘整体透明而造成的异常
      inputRootBinding.inputRoot.setBackgroundColor(Color.WHITE);
    }

    tabView.reset(self);
  }

  public void resetKeyboard() {
    if (mKeyboardView != null) {
      mKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));
      mKeyboardView.reset(this); // 實體鍵盤無軟鍵盤
    }
  }

  public void resetCandidate() {
    if (mCandidateContainer != null) {
      loadBackground();
      setShowComment(!Rime.getOption("_hide_comment"));
      mCandidateContainer.setVisibility(
          !Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);
      mCandidate.reset(this);
      mShowWindow = getPrefs().getKeyboard().getFloatingWindowEnabled() && mConfig.hasKey("window");
      mComposition.setVisibility(mShowWindow ? View.VISIBLE : View.GONE);
      mComposition.reset(this);
    }
  }

  /** 重置鍵盤、候選條、狀態欄等 !!注意，如果其中調用Rime.setOption，切換方案會卡住 */
  private void reset() {
    mConfig.reset();
    loadConfig();
    if (mKeyboardSwitch != null) mKeyboardSwitch.reset(this);
    resetCandidate();
    hideComposition();
    resetKeyboard();
  }

  public void initKeyboard() {
    reset();
    mConfig.initCurrentColors();
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
    if (effectManager != null) effectManager.destroy();
    effectManager = null;
    inputRootBinding = null;
    imeManager = null;
    self = null;
    if (getPrefs().getOther().getDestroyOnQuit()) {
      Rime.destroy();
      mConfig.destroy();
      mConfig = null;
      System.exit(0); // 清理內存
    }
    super.onDestroy();
  }

  public static Trime getService() {
    if (self == null) self = new Trime();
    return self;
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    if (orientation != newConfig.orientation) {
      // Clear composing text and candidates for orientation change.
      escape();
      orientation = newConfig.orientation;
    }
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      int i = cursorAnchorInfo.getComposingTextStart();
      if ((winPos == WindowsPositionType.LEFT || winPos == WindowsPositionType.LEFT_UP) && i >= 0) {
        mPopupRectF = cursorAnchorInfo.getCharacterBounds(i);
      } else {
        mPopupRectF.left = cursorAnchorInfo.getInsertionMarkerHorizontal();
        mPopupRectF.top = cursorAnchorInfo.getInsertionMarkerTop();
        mPopupRectF.right = mPopupRectF.left;
        mPopupRectF.bottom = cursorAnchorInfo.getInsertionMarkerBottom();
      }
      cursorAnchorInfo.getMatrix().mapRect(mPopupRectF);
      if (mCandidateContainer != null) {
        mFloatingWindowTimer.postShowFloatingWindow();
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
        int n = newSelEnd - candidatesStart;
        Rime.RimeSetCaretPos(n);
        updateComposing();
      }
    }
    if ((candidatesStart == -1 && candidatesEnd == -1) && (newSelStart == 0 && newSelEnd == 0)) {
      // 上屏後，清除候選區
      escape();
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
    final LayoutInflater inflater = getLayoutInflater();
    inputRootBinding = InputRootBinding.inflate(inflater);
    // mInputRoot = (LinearLayout) inflater.inflate(R.layout.input_root, (ViewGroup) null);
    mKeyboardView = inputRootBinding.keyboard;
    mKeyboardView.setOnKeyboardActionListener(this);
    mKeyboardView.setShowHint(!Rime.getOption("_hide_key_hint"));

    // 初始化候选栏
    mCandidateContainer = inputRootBinding.scroll;
    mCandidate = mCandidateContainer.findViewById(R.id.candidate);
    mCandidate.setCandidateListener(this);

    mCandidateContainer.setPageStr(
        () -> handleKey(KeyEvent.KEYCODE_PAGE_DOWN, 0),
        () -> handleKey(KeyEvent.KEYCODE_PAGE_UP, 0));

    // 候选词悬浮窗的容器

    compositionContainerBinding = CompositionContainerBinding.inflate(inflater);
    hideComposition();
    mFloatingWindow = new PopupWindow(compositionContainerBinding.compositionContainer);
    mFloatingWindow.setClippingEnabled(false);
    mFloatingWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

    mComposition = (Composition) compositionContainerBinding.compositionContainer.getChildAt(0);

    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      mFloatingWindow.setWindowLayoutType(getDialogType());
    }

    setShowComment(!Rime.getOption("_hide_comment"));
    mCandidateContainer.setVisibility(
        !Rime.getOption("_hide_candidate") ? View.VISIBLE : View.GONE);

    liquidKeyboard.setView(inputRootBinding.liquidKeyboard);
    mainKeyboard = inputRootBinding.mainKeyboard;
    symbleKeyboard = inputRootBinding.symbolKeyboard;
    tabView = inputRootBinding.tabView;
    loadBackground();

    return inputRootBinding.inputRoot;
  }

  void setShowComment(boolean show_comment) {
    if (mCandidateContainer != null) mCandidate.setShowComment(show_comment);
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
    if (reset_ascii_mode) mAsciiMode = false;

    mKeyboardSwitch.init(getMaxWidth()); // 橫豎屏切換時重置鍵盤

    // Select a keyboard based on the input type of the editing field.
    mKeyboardSwitch.setKeyboard(keyboard);
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
    mKeyboardView.closing();
    escape();
    try {
      hideComposition();
    } catch (Exception e) {
      Timber.e(e, "Failed to show the PopupWindow.");
    }
  }

  private void bindKeyboardToInputView() {
    if (mKeyboardView != null) {
      // Bind the selected keyboard to the input view.
      Keyboard sk = mKeyboardSwitch.getCurrentKeyboard();
      mKeyboardView.setKeyboard(sk);
      updateCursorCapsToInputView();
    }
  }

  // 句首自動大小寫
  private void updateCursorCapsToInputView() {
    if (auto_caps.contentEquals("false") || TextUtils.isEmpty(auto_caps)) return;
    if ((auto_caps.contentEquals("true") || Rime.isAsciiMode())
        && (mKeyboardView != null && !mKeyboardView.isCapsOn())) {
      @Nullable final InputConnection ic = getCurrentInputConnection();
      if (ic != null) {
        int caps = 0;
        @Nullable final EditorInfo ei = getCurrentInputEditorInfo();
        if ((ei != null) && (ei.inputType != EditorInfo.TYPE_NULL)) {
          caps = ic.getCursorCapsMode(ei.inputType);
        }
        mKeyboardView.setShifted(false, caps != 0);
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
    if (effectManager != null) effectManager.textCommitSpeak(text);
    // mEffect.speakCommit(text);
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
    if (effectManager != null) effectManager.keyPressVibrate();
  }

  public void keyPressSound() {
    if (effectManager != null) effectManager.keyPressSound(0);
  }

  /**
   * 獲取光標處的字符
   *
   * @return 光標處的字符
   */
  /*
  private CharSequence getLastText() {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      return ic.getTextBeforeCursor(1, 0);
    }
    return "";
  } **/

  private boolean handleAction(int code, int mask) { // 編輯操作
    final @Nullable InputConnection ic = getCurrentInputConnection();
    if (ic == null) return false;
    if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {
      // android.R.id. + selectAll, startSelectingText, stopSelectingText, cut, copy, paste,
      // copyUrl, or switchInputMethod
      if (VERSION.SDK_INT >= VERSION_CODES.M) {
        if (code == KeyEvent.KEYCODE_V
            && Event.hasModifier(mask, KeyEvent.META_ALT_ON)
            && Event.hasModifier(mask, KeyEvent.META_SHIFT_ON)) {
          return ic.performContextMenuAction(android.R.id.pasteAsPlainText);
        }
        if (code == KeyEvent.KEYCODE_S && Event.hasModifier(mask, KeyEvent.META_ALT_ON)) {
          CharSequence cs = ic.getSelectedText(0);
          if (cs == null) ic.performContextMenuAction(android.R.id.selectAll);
          return ic.performContextMenuAction(android.R.id.shareText);
        }
        switch (code) {
          case KeyEvent.KEYCODE_Y:
            return ic.performContextMenuAction(android.R.id.redo);
          case KeyEvent.KEYCODE_Z:
            return ic.performContextMenuAction(android.R.id.undo);
        }
      }
      switch (code) {
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
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              int move_to = StringUitls.findNextSection(et.text, et.startOffset + et.selectionEnd);
              ic.setSelection(move_to, move_to);
              return true;
            }
            break;
          }
        case KeyEvent.KEYCODE_DPAD_LEFT:
          if (getPrefs().getOther().getSelectionSense()) {
            ExtractedTextRequest etr = new ExtractedTextRequest();
            etr.token = 0;
            ExtractedText et = ic.getExtractedText(etr, 0);
            if (et != null) {
              int move_to =
                  StringUitls.findPrevSection(et.text, et.startOffset + et.selectionStart);
              ic.setSelection(move_to, move_to);
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
    final Dialog dialog = getWindow();
    if (dialog == null) {
      return null;
    }
    final Window window = dialog.getWindow();
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
        mKeyboardSwitch.setKeyboard(event.getSelect());
        // 根據鍵盤設定中英文狀態，不能放在 Rime.onMessage 中做
        mTempAsciiMode = mKeyboardSwitch.getAsciiMode(); // 切換到西文鍵盤時不保存狀態
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
        s = Function.handle(this, event.getCommand(), arg);
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

  private boolean handleKey(int keyCode, int mask) { // 軟鍵盤
    keyUpNeeded = false;
    if (onRimeKey(Event.getRimeEvent(keyCode, mask))) {
      keyUpNeeded = true;
      Timber.i("Rime onKey");
    } else if (handleAction(keyCode, mask)
        || handleOption(keyCode)
        || handleEnter(keyCode)
        || handleBack(keyCode)) {
      Timber.i("Trime onKey");
    } else if (Function.openCategory(this, keyCode)) {
      Timber.i("Open category");
    } else {
      keyUpNeeded = true;
      return false;
    }
    return true;
  }

  private void sendKey(InputConnection ic, int key, int meta, int action) {
    final long now = System.currentTimeMillis();
    if (ic != null) ic.sendKeyEvent(new KeyEvent(now, now, action, key, 0, meta));
  }

  private void sendKeyDown(InputConnection ic, int key, int meta) {
    sendKey(ic, key, meta, KeyEvent.ACTION_DOWN);
  }

  private void sendKeyUp(InputConnection ic, int key, int meta) {
    sendKey(ic, key, meta, KeyEvent.ACTION_UP);
  }

  private void sendDownUpKeyEvents(int keyCode, int mask) {
    @Nullable final InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    final int states =
        KeyEvent.META_FUNCTION_ON
            | KeyEvent.META_SHIFT_MASK
            | KeyEvent.META_ALT_MASK
            | KeyEvent.META_CTRL_MASK
            | KeyEvent.META_META_MASK
            | KeyEvent.META_SYM_ON;
    ic.clearMetaKeyStates(states);
    if (mKeyboardView != null && mKeyboardView.isShifted()) {
      if (keyCode == KeyEvent.KEYCODE_MOVE_HOME
          || keyCode == KeyEvent.KEYCODE_MOVE_END
          || keyCode == KeyEvent.KEYCODE_PAGE_UP
          || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
          || (keyCode >= KeyEvent.KEYCODE_DPAD_UP && keyCode <= KeyEvent.KEYCODE_DPAD_RIGHT)) {
        mask |= KeyEvent.META_SHIFT_ON;
      }
    }

    if (Event.hasModifier(mask, KeyEvent.META_SHIFT_ON)) {
      sendKeyDown(
          ic, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {
      sendKeyDown(
          ic, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_ALT_ON)) {
      sendKeyDown(ic, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
    }

    boolean send_key_down_up = true;
    if (mask == 0 && mAsciiMode) {
      // 使用ASCII键盘输入英文字符时，直接上屏，跳过复杂的调用，从表面上解决issue #301 知乎输入英语后输入法失去焦点的问题
      String keyText = StringUitls.toCharString(keyCode);
      if (keyText.length() > 0) {
        ic.commitText(keyText, 1);
        send_key_down_up = false;
      }
    }

    if (send_key_down_up) {
      sendKeyDown(ic, keyCode, mask);
      sendKeyUp(ic, keyCode, mask);
    }

    if (Event.hasModifier(mask, KeyEvent.META_ALT_ON)) {
      sendKeyUp(ic, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_CTRL_ON)) {
      sendKeyUp(ic, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
    }
    if (Event.hasModifier(mask, KeyEvent.META_SHIFT_ON)) {
      sendKeyUp(
          ic, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }
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
    sendDownUpKeyEvents(keyCode, mask);
  }

  @Override
  public void onText(CharSequence text) { // 軟鍵盤
    Timber.i("onText = %s", text);
    if (effectManager != null) effectManager.keyPressSpeak(text);

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
    if (effectManager != null) {
      effectManager.keyPressVibrate();
      effectManager.keyPressSound(keyCode);
      effectManager.keyPressSpeak(keyCode);
    }
  }

  @Override
  public void onRelease(int keyCode) {
    if (keyUpNeeded) {
      onRimeKey(Event.getRimeEvent(keyCode, Rime.META_RELEASE_ON));
    }
  }

  @Override
  public void swipeLeft() {
    // no-op
  }

  @Override
  public void swipeRight() {
    // no-op
  }

  @Override
  public void swipeUp() {
    // no-op
  }

  /** 在鍵盤視圖中從上往下滑動，隱藏鍵盤 */
  @Override
  public void swipeDown() {
    // requestHideSelf(0);
  }

  @Override
  public void onPickCandidate(int i) {
    // Commit the picked candidate and suggest its following words.
    onPress(0);
    if (!isComposing()) {
      if (i >= 0) {
        Rime.toggleOption(i);
        updateComposing();
      }
    } else if (i == -4) onKey(KeyEvent.KEYCODE_PAGE_UP, 0);
    else if (i == -5) onKey(KeyEvent.KEYCODE_PAGE_DOWN, 0);
    else // if (Rime.selectCandidate(i))
    {
      handleKey(KeyEvent.KEYCODE_1 + i, 0);
    }
  }

  /** 獲得當前漢字：候選字、選中字、剛上屏字/光標前字/光標前所有字、光標後所有字 */
  private String getActiveText(int type) {
    if (type == 2) return Rime.RimeGetInput(); // 當前編碼
    String s = Rime.getComposingText(); // 當前候選
    if (TextUtils.isEmpty(s)) {
      final @Nullable InputConnection ic = getCurrentInputConnection();
      @Nullable CharSequence cs = ic != null ? ic.getSelectedText(0) : null; // 選中字
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
      String s = null;
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
      if (s == null) s = "";
      if (ic != null) {
        @Nullable final CharSequence cs = ic.getSelectedText(0);
        if (cs == null || !TextUtils.isEmpty(s)) {
          // 無選中文本或編碼不爲空時更新編輯區
          ic.setComposingText(s, 1);
        }
      }
    }
    if (ic != null && !isWinFixed()) cursorUpdated = ic.requestCursorUpdates(1);
    if (mCandidateContainer != null) {
      if (mShowWindow) {
        final int start_num = mComposition.setWindow(min_length, min_check);
        mCandidate.setText(start_num);
        if (isWinFixed() || !cursorUpdated) mFloatingWindowTimer.postShowFloatingWindow();
      } else {
        mCandidate.setText(0);
      }
    }
    if (mKeyboardView != null) mKeyboardView.invalidateComposingKeys();
    if (!onEvaluateInputViewShown()) setCandidatesViewShown(canCompose); // 實體鍵盤打字時顯示候選欄
  }

  public static int getDialogType() {
    if (VERSION.SDK_INT >= VERSION_CODES.P) {
      return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY; // Android P 中 AlertDialog 要顯示在最上層
    } else {
      return WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
    }
  }

  private void showDialog(@NonNull AlertDialog dialog) {
    final Window window = dialog.getWindow();
    final WindowManager.LayoutParams lp = window.getAttributes();
    if (mCandidateContainer != null) lp.token = mCandidateContainer.getWindowToken();
    lp.type = getDialogType();
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
    new SchemaPickerDialog(this, mCandidateContainer.getWindowToken()).show();
  }

  /** 彈出{@link ThemePickerDialog 主題對話框} */
  private void showThemeDialog() {
    new ThemePickerDialog(this, mCandidateContainer.getWindowToken()).show();
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
  private boolean handleEnter(int keyCode) { // 回車
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
  private void escape() {
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
        @ColorInt final Integer keyboardBackColor = mConfig.getCurrentColor_("back_color");
        if (keyboardBackColor != null) {
          BarUtils.setNavBarColor(window, keyboardBackColor);
        }
      } catch (Exception e) {
        Timber.e(e);
      }
    }
  }

  @Override
  public void updateFullscreenMode() {
    super.updateFullscreenMode();
    // TODO: 需要获取到文本编辑框、完成按钮，设置其色彩和尺寸。
    View inputArea = getWindow().findViewById(android.R.id.inputArea);
    int layoutHeight = isFullscreenMode() ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;

    if (isFullscreenMode()) {
      Timber.i("isFullScreen");
      // 全屏模式下，当键盘布局包含透明色时，用户能透过键盘看到待输入App的UI，影响全屏体验。故需填色。当前固定填充淡粉色，用于测试。
      inputArea.setBackgroundColor(parseColor("#ff660000"));
    } else {
      Timber.i("NotFullScreen");
      // 非全屏模式下，这个颜色似乎不会体现出来。为避免出现问题，填充浅灰色
      inputArea.setBackgroundColor(parseColor("#dddddddd"));
    }

    updateViewHeight(inputArea, layoutHeight);
    updateLayoutGravity(inputArea, Gravity.BOTTOM);
  }

  public boolean onEvaluateFullscreenMode() {
    if (orientation != Configuration.ORIENTATION_LANDSCAPE) return false;
    switch (mConfig.getFullscreenMode()) {
      case "auto":
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0) {
          return false;
        }
      case "always":
        return true;
      case "never":
        return false;
    }
    return false;
  }

  public void updateViewHeight(View view, int layoutHeight) {
    if (null == view) return;
    LayoutParams params = view.getLayoutParams();
    if (params != null && params.height != layoutHeight) {
      params.height = layoutHeight;
      view.setLayoutParams(params);
    }
  }

  public void updateLayoutGravity(View view, int layoutGravity) {
    if (null == view) return;
    LayoutParams params = view.getLayoutParams();

    if (params instanceof LinearLayout.LayoutParams) {
      LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
      if (lp.gravity != layoutGravity) {
        lp.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
    } else if (params instanceof FrameLayout.LayoutParams) {
      FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) params;
      if (lp.gravity != layoutGravity) {
        lp.gravity = layoutGravity;
        view.setLayoutParams(params);
      }
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
    clipBoard.addPrimaryClipChangedListener(
        () -> {
          if (mConfig.getClipboardMaxSize() != 0) {
            final ClipData clipData = clipBoard.getPrimaryClip();
            final ClipData.Item item = clipData.getItemAt(0);
            if (item == null) return;
            final String text = item.coerceToText(self).toString();

            final String text2 = StringUitls.stringReplacer(text, mConfig.getClipBoardCompare());
            if (text2.length() < 1 || text2.equals(ClipBoardString)) return;

            if (StringUitls.stringNotMatch(text, mConfig.getClipBoardOutput())) {
              ClipBoardString = text2;
              liquidKeyboard.addClipboardData(text);
            }
          }
        });
  }
}
