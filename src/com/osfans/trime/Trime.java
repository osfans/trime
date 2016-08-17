/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.osfans.trime;

import android.content.res.Configuration;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.Window;
import android.view.WindowManager;
import android.app.AlertDialog;
import android.os.Handler;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION;

import java.util.logging.Logger;
import java.util.Locale;

/** {@link InputMethodService 輸入法}主程序 */
public class Trime extends InputMethodService implements 
    KeyboardView.OnKeyboardActionListener, Candidate.CandidateListener {

  private static Logger Log = Logger.getLogger(Trime.class.getSimpleName());
  private static Trime self;
  protected KeyboardView mKeyboardView; //軟鍵盤
  private KeyboardSwitch mKeyboardSwitch;
  private Config mConfig; //配置
  private Effect mEffect; //音效
  private Candidate mCandidate; //候選
  private Composition mComposition; //編碼
  private LinearLayout mCompositionContainer;
  private FrameLayout mCandidateContainer;
  private PopupWindow mFloatingWindow;
  private PopupTimer mFloatingWindowTimer = new PopupTimer();
  private AlertDialog mOptionsDialog; //對話框

  private int orientation;
  private boolean canCompose;
  private boolean enterAsLineBreak;
  private boolean inlinePreedit, inlineCode; //嵌入首選
  private boolean display_tray_icon;
  private boolean mTempAsciiMode; //臨時中英文狀態
  private boolean mAsciiMode; //默認中英文狀態
  private boolean reset_ascii_mode; //重置中英文狀態
  private String soft_cursor = "soft_cursor"; //軟光標
  private String horizontal = "_horizontal"; //水平模式，左、右方向鍵選中前一個、後一個候選字，上、下方向鍵翻頁
  private Locale[] locales = new Locale[2];

  private class PopupTimer extends Handler implements Runnable {
    private int mParentLocation[] = new int[2];

    void postShowFloatingWindow() {
      mCompositionContainer.measure(LayoutParams.WRAP_CONTENT,
              LayoutParams.WRAP_CONTENT);
      mFloatingWindow.setWidth(mCompositionContainer.getMeasuredWidth());
      mFloatingWindow.setHeight(mCompositionContainer.getMeasuredHeight());
      post(this);
    }

    void cancelShowing() {
      if (mFloatingWindow.isShowing()) mFloatingWindow.dismiss();
      removeCallbacks(this);
    }

    public void run() {
      if (mCandidateContainer == null || mCandidateContainer.getWindowToken() == null) return;
      mCandidateContainer.getLocationInWindow(mParentLocation);

      if (!mFloatingWindow.isShowing()) {
        mFloatingWindow.showAtLocation(mCandidateContainer,
                Gravity.LEFT | Gravity.TOP, mParentLocation[0],
                mParentLocation[1] -mFloatingWindow.getHeight());
      } else {
        mFloatingWindow
        .update(mParentLocation[0],
                mParentLocation[1] - mFloatingWindow.getHeight(),
                mFloatingWindow.getWidth(),
                mFloatingWindow.getHeight());
      }
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    self = this;

    mEffect = new Effect(this);
    Config.prepareRime(this);
    mConfig = Config.get(this);
    Rime.setOption(soft_cursor, mConfig.getBoolean(soft_cursor)); //軟光標
    Rime.setOption(horizontal, mConfig.getBoolean("horizontal")); //水平模式
    inlinePreedit = mConfig.getBoolean("inline_preedit");
    inlineCode = mConfig.getBoolean("inline_code");
    display_tray_icon = mConfig.getBoolean("display_tray_icon");
    reset_ascii_mode = mConfig.getBoolean("reset_ascii_mode");
    mEffect.reset();
    mKeyboardSwitch = new KeyboardSwitch(this);

    String s;
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
    else locales[0] = Locale.ENGLISH;

    orientation = getResources().getConfiguration().orientation;
    // Use the following line to debug IME service.
    //android.os.Debug.waitForDebugger();
  }

  public void setLanguage(boolean isAsciiMode) {
    if (!mTempAsciiMode) mAsciiMode = isAsciiMode; //切換中西文時保存狀態
    mEffect.setLanguage(locales[isAsciiMode ? 1 : 0]);
  }

  public void invalidate() {
    Rime.get();
    if (mConfig != null) mConfig.destroy();
    mConfig = new Config(this);
    reset();
    Rime.setOption(soft_cursor, mConfig.getBoolean(soft_cursor)); //軟光標
    Rime.setOption(horizontal, mConfig.getBoolean("horizontal")); //水平模式
  }

  public void invalidateKeyboard() {
    if (mKeyboardView != null) mKeyboardView.invalidateAllKeys();
  }

  /**
   * 重置鍵盤、候選條、狀態欄等
   * !!注意，如果其中調用Rime.setOption，切換方案會卡住
   */
  public void reset() {
    mConfig.reset();
    inlinePreedit = mConfig.getBoolean("inline_preedit");
    inlineCode = mConfig.getBoolean("inline_code");
    display_tray_icon = mConfig.getBoolean("display_tray_icon");
    reset_ascii_mode = mConfig.getBoolean("reset_ascii_mode");
    if (mKeyboardSwitch != null) mKeyboardSwitch.reset();
    if (mCandidateContainer != null) {
      mCandidateContainer.setBackgroundColor(mConfig.getColor("back_color"));
      mCandidate.reset();
      mComposition.reset();
    }
    if (null != mFloatingWindow && mFloatingWindow.isShowing()) {
        mFloatingWindowTimer.cancelShowing();
        mFloatingWindow.dismiss();
    }
    if (mKeyboardView != null) mKeyboardView.reset(); //實體鍵盤無軟鍵盤
    mEffect.reset();
  }

  public void initKeyboard() {
    reset();
    bindKeyboardToInputView();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    self = null;
    if (mConfig.getBoolean("destroy_on_quit")) {
      Rime.destroy();
      mConfig.destroy();
      mConfig = null;
      System.exit(0); //清理內存
    }
  }

  public static Trime getService() {
    return self;
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    if (orientation != newConfig.orientation) {
      // Clear composing text and candidates for orientation change.
      escape();
      orientation = newConfig.orientation;
    }
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
      int newSelEnd, int candidatesStart, int candidatesEnd) {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, 
        candidatesStart, candidatesEnd);
    if ((candidatesEnd != -1) &&
        ((newSelStart != candidatesEnd) || (newSelEnd != candidatesEnd))) {
      //移動光標時，更新候選區
      if ((newSelEnd < candidatesEnd) && (newSelEnd >= candidatesStart)) {
        int n = newSelEnd - candidatesStart;
        Rime.RimeSetCaretPos(n);
        updateComposing();
      }
    }
    if ((candidatesStart == -1 && candidatesEnd == -1) &&
        (newSelStart == 0 && newSelEnd == 0)) {
      //上屏後，清除候選區
      escape();
    }
    // Update the caps-lock status for the current cursor position.
    //updateCursorCapsToInputView();
  }

  @Override
  public void onComputeInsets(InputMethodService.Insets outInsets) {
    super.onComputeInsets(outInsets);
    outInsets.contentTopInsets = outInsets.visibleTopInsets;
  }

  @Override
  public View onCreateInputView() {
    mKeyboardView = (KeyboardView) getLayoutInflater().inflate(
        R.layout.input, null);
    mKeyboardView.setOnKeyboardActionListener(this);
    return mKeyboardView;
  }

  @Override
  public View onCreateCandidatesView() {
    LayoutInflater inflater = getLayoutInflater();
    mCompositionContainer = (LinearLayout) inflater.inflate(
            R.layout.composition_container, null);
    if (null != mFloatingWindow && mFloatingWindow.isShowing()) {
      mFloatingWindowTimer.cancelShowing();
      mFloatingWindow.dismiss();
    }
    mFloatingWindow = new PopupWindow(this);
    mFloatingWindow.setClippingEnabled(false);
    mFloatingWindow.setBackgroundDrawable(null);
    mFloatingWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    mFloatingWindow.setContentView(mCompositionContainer);
    mComposition = (Composition) mCompositionContainer.getChildAt(0);

    mCandidateContainer = (FrameLayout) inflater.inflate(R.layout.candidate_container, null);
    mCandidateContainer.setBackgroundColor(mConfig.getColor("back_color"));
    mCandidate = (Candidate) mCandidateContainer.findViewById(R.id.candidate);
    mCandidate.setCandidateListener(this);
    return mCandidateContainer;
  }

  /**
   * 重置鍵盤、候選條、狀態欄等，進入文本框時通常會調用。
   * @param attribute 文本框的{@link EditorInfo 屬性}
   * @param restarting 是否重啓
   */
  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    super.onStartInput(attribute, restarting);
    canCompose = false;
    enterAsLineBreak = false;
    mTempAsciiMode = false;
    int inputType = attribute.inputType;
    int inputClass = inputType & InputType.TYPE_MASK_CLASS;
    int variation = inputType & InputType.TYPE_MASK_VARIATION;
    String keyboard = null;
    switch (inputClass) {
      case InputType.TYPE_CLASS_NUMBER:
      case InputType.TYPE_CLASS_PHONE:
      case InputType.TYPE_CLASS_DATETIME:
        mTempAsciiMode = true;
        keyboard = "number";
        break;
      case InputType.TYPE_CLASS_TEXT:
      default:
        if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
          // Make enter-key as line-breaks for messaging.
          enterAsLineBreak = true;
        }
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
         || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
         || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
         || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
         || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
         ) {
           mTempAsciiMode = true;
        } else {
          canCompose = true;
        }
        break;
    }
    Rime.get();
    if (reset_ascii_mode) mAsciiMode = false;
    // Select a keyboard based on the input type of the editing field.
    mKeyboardSwitch.init(getMaxWidth()); //橫豎屏切換時重置鍵盤
    mKeyboardSwitch.setKeyboard(keyboard); //設定默認鍵盤
    updateAsciiMode();
    updateComposing();
    if (!onEvaluateInputViewShown()) setCandidatesViewShown(canCompose && !Rime.isEmpty()); //實體鍵盤
    if (display_tray_icon) showStatusIcon(R.drawable.status); //狀態欄圖標
  }

  @Override
  public void onStartInputView(EditorInfo attribute, boolean restarting) {
    super.onStartInputView(attribute, restarting);
    bindKeyboardToInputView();
    setCandidatesViewShown(!Rime.isEmpty());
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    super.onFinishInputView(finishingInput);
    // Dismiss any pop-ups when the input-view is being finished and hidden.
    mKeyboardView.closing();
    escape();
    try {
      mFloatingWindowTimer.cancelShowing();
      mFloatingWindow.dismiss();
    } catch (Exception e) {
      Log.info("Fail to show the PopupWindow.");
    }
  }


  private void bindKeyboardToInputView() {
    if (mKeyboardView != null) {
      // Bind the selected keyboard to the input view.
      Keyboard sk = (Keyboard)mKeyboardSwitch.getCurrentKeyboard();
      mKeyboardView.setKeyboard(sk);
      //updateCursorCapsToInputView();
    }
  }

  private boolean isComposing() {
    return Rime.isComposing();
  }

  /**
   * 指定字符串上屏
   * @param text 要上屏的字符串
   */
  public void commitText(CharSequence text) {
    if (text == null) return;
    mEffect.speakCommit(text);
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) ic.commitText(text, 1);
    if (!isComposing()) Rime.commitComposition(); //自動上屏
  }

  /**
   * 從Rime獲得字符串並上屏
   * @return 是否成功上屏
   */
  private boolean commitText() {
    boolean r = Rime.getCommit();
    if (r) commitText(Rime.getCommitText());
    return r;
  }

  /**
   * 獲取光標處的字符
   * @return 光標處的字符
   */
  private CharSequence getLastText() {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      return ic.getTextBeforeCursor(1,0);
    }
    return "";
  }

  public boolean handleAciton(int code, int mask) { //編輯操作
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return false;
    if (code == KeyEvent.KEYCODE_CLEAR) {
      ic.beginBatchEdit();
      ic.performContextMenuAction(android.R.id.selectAll);
      ic.performContextMenuAction(android.R.id.cut);
      ic.endBatchEdit();
      return true;
    }
    if (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB) return false;
    if (KeyEvent.metaStateHasModifiers(mask, KeyEvent.META_CTRL_ON)) {
      // android.R.id. + selectAll, startSelectingText, stopSelectingText, cut, copy, paste, copyUrl, or switchInputMethod
      if (code == KeyEvent.KEYCODE_A)
        return ic.performContextMenuAction(android.R.id.selectAll);
      if (code == KeyEvent.KEYCODE_X)
        return ic.performContextMenuAction(android.R.id.cut);
      if (code == KeyEvent.KEYCODE_C)
        return ic.performContextMenuAction(android.R.id.copy);
      if (code == KeyEvent.KEYCODE_V)
        return ic.performContextMenuAction(android.R.id.paste);
    }
    return false; 
  }

  /**
   * 如果爲{@link KeyEvent#KEYCODE_BACK Back鍵}，則隱藏鍵盤
   * @param keyCode {@link KeyEvent#getKeyCode() 鍵碼}
   * @return 是否處理了Back鍵事件
   * */
  private boolean handleBack(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      requestHideSelf(0);
      return true;
    }
    return false;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (canCompose && onKeyEvent(event)) return true;
    return super.onKeyDown(keyCode, event);
  }

  /** 處理實體鍵盤事件
   * @param event {@link KeyEvent 按鍵事件}
   * @return 是否成功處理
   * */
  private boolean onKeyEvent(KeyEvent event) {
    Log.info("onKeyEvent="+event);
    int keyCode = event.getKeyCode();
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
        keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
        keyCode == KeyEvent.KEYCODE_HOME ||
        keyCode == KeyEvent.KEYCODE_MENU ||
        keyCode == KeyEvent.KEYCODE_SEARCH) return false;
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      escape(); //返回鍵清屏
      return false;
    }
    if (!isComposing()) {
      if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_ENTER)
      return false;
    }

    if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
      if (KeyEvent.KEYCODE_SPACE == keyCode && event.isCtrlPressed())
        return handleOption(KeyEvent.KEYCODE_MENU); //切換輸入法
    }

    int c = event.getUnicodeChar();
    if (c > 0x20) { //實體鍵盤空格、回車等發送onKey，字符發送onText
      onText(String.valueOf((char)c));
      return true;
    }
    onKey(keyCode, event.getMetaState());
    return true;
  }

  public void onEvent(Event event) {
    String s = event.getText();
    if (!Function.isEmpty(s)) {
      onText(s);
    } else if (event.code > 0) {
      int code = event.code;
      if (code == KeyEvent.KEYCODE_SWITCH_CHARSET) { //切換狀態
        Rime.toggleOption(event.getToggle());
        commitText();
        updateComposing();
      } else if (code == KeyEvent.KEYCODE_EISU) { //切換鍵盤
        mKeyboardSwitch.setKeyboard(event.select);
        //根據鍵盤設定中英文狀態，不能放在Rime.onMessage中做
        mTempAsciiMode = mKeyboardSwitch.getAsciiMode(); //切換到西文鍵盤時不保存狀態
        updateAsciiMode();
        bindKeyboardToInputView();
        updateComposing();
      } else if (code == KeyEvent.KEYCODE_FUNCTION) { //命令直通車
        s = Function.handle(this, event.command, event.option);
        if (s != null) {
          commitText(s);
          updateComposing();
        }
      } else if (code == KeyEvent.KEYCODE_VOICE_ASSIST) { //語音輸入
        new Speech(this).start();
      } else if (code == KeyEvent.KEYCODE_SETTINGS) { //全局設定
        Function.showPrefDialog(this);
      } else if (code == KeyEvent.KEYCODE_PROG_RED) { //配色方案
        showColorDialog();
      } else {
        onKey(event.code, event.mask);
      }
    }
  }

  public void onKey(int primaryCode, int mask) { //軟鍵盤
    if (handleOption(primaryCode)) {
      Log.info("onOption");
    } else if (Rime.onKey(Event.getRimeEvent(primaryCode, mask))) {
      Log.info("Rime onKey");
      commitText();
      updateComposing();
    } else if (handleEnter(primaryCode)
      || handleAciton(primaryCode, mask)
      || handleBack(primaryCode)) {
      Log.info("Trime onKey");
    } else {
      Log.info("send Key");
      sendDownUpKeyEvents(primaryCode);
    }
  }

  public void onText(CharSequence text) { //軟鍵盤
    Log.info("onText="+text);
    mEffect.speakKey(text);
    Rime.onText(text);
    if(!commitText() && !isComposing()) commitText(text);
    updateComposing();
  }

  public void onPress(int primaryCode) {
    mEffect.vibrate();
    mEffect.playSound(primaryCode);
    mEffect.speakKey(primaryCode);
  }

  public void onRelease(int primaryCode) {
    if (Rime.onKey(Event.getRimeEvent(primaryCode, 1<<30))) {
      commitText();
      updateComposing();
    }
  }

  public void swipeLeft() {
    // no-op
  }

  public void swipeRight() {
    // no-op
  }

  public void swipeUp() {
    // no-op
  }

  /** 在鍵盤視圖中從上往下滑動，隱藏鍵盤 */
  public void swipeDown() {
    requestHideSelf(0);
  }

  public void onPickCandidate(int i) {
    // Commit the picked candidate and suggest its following words.
    if (!isComposing()) {
      if (i >=0) {
        Rime.toggleOption(i);
        updateComposing();
      }
    } else if (i == -4) onKey(KeyEvent.KEYCODE_PAGE_UP, 0);
    else if (i == -5) onKey(KeyEvent.KEYCODE_PAGE_DOWN, 0);
    else if (Rime.selectCandidate(i)) {
      commitText();
      updateComposing();
    }
  }

  /** 更新Rime的中西文狀態、編輯區文本 */
  public void updateComposing() {
    if (inlinePreedit || inlineCode) { //嵌入首選
      String s = inlineCode ? Rime.RimeGetInput() : Rime.getComposingText();
      if (s == null) s = "";
      InputConnection ic = getCurrentInputConnection();
      if (ic != null) {
        CharSequence cs = ic.getSelectedText(0);
        if (cs == null || !Function.isEmpty(s)) {
          // 無選中文本或編碼不爲空時更新編輯區
          ic.setComposingText(s, 1);
        }
      }
    }
    if (mCandidateContainer != null) {
      mCandidate.setText();
      //setCandidatesViewShown(canCompose); //InputType爲0x80000時無候選條
      mComposition.setText();
      mFloatingWindowTimer.postShowFloatingWindow();
    }
    if (mKeyboardView != null) mKeyboardView.invalidateComposingKeys();
  }

  private void showDialog(AlertDialog dialog) {
    Window window = dialog.getWindow();
    WindowManager.LayoutParams lp = window.getAttributes();
    if (mCandidateContainer != null) lp.token = mCandidateContainer.getWindowToken();
    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
    window.setAttributes(lp);
    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    dialog.show();
  }

  /** 彈出{@link ColorDialog 配色對話框} */
  private void showColorDialog() {
    AlertDialog dialog = new ColorDialog(this).getDialog();
    showDialog(dialog);
  }

  /** 彈出{@link SchemaDialog 輸入法方案對話框} */
  private void showSchemaDialog() {
    AlertDialog dialog = new SchemaDialog(this).getDialog();
    showDialog(dialog);
  }

  private boolean handleOption(int keyCode) {
    if (keyCode == KeyEvent.KEYCODE_MENU) {
      if (mOptionsDialog != null && mOptionsDialog.isShowing()) return true; //對話框單例
      AlertDialog.Builder builder = new AlertDialog.Builder(this)
      .setTitle(R.string.ime_name)
      .setIcon(R.drawable.icon)
      .setCancelable(true)
      .setNegativeButton(R.string.other_ime, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          di.dismiss();
          ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
        }
      })
      .setPositiveButton(R.string.set_ime, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          Function.showPrefDialog(Trime.this); //全局設置
          di.dismiss();
        }
      });
      if (Rime.isEmpty()) builder.setMessage(R.string.no_schemas); //提示安裝碼表
      else {
        builder.setNeutralButton(R.string.pref_schemas, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface di, int id) {
            showSchemaDialog(); //部署方案
            di.dismiss();
          }
        });
        builder.setSingleChoiceItems(Rime.getSchemaNames(), Rime.getSchemaIndex(),
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface di, int id) {
              di.dismiss();
              Rime.selectSchema(id); //切換方案
              Rime.setOption(soft_cursor, mConfig.getBoolean(soft_cursor)); //軟光標
              Rime.setOption(horizontal, mConfig.getBoolean("horizontal")); //水平模式
            }
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
   * */
  private boolean handleEnter(int keyCode) { //回車
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
}
