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

package com.osfans.trime.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.data.DataManager;
import com.osfans.trime.data.opencc.OpenCCDictManager;
import com.osfans.trime.data.schema.SchemaManager;
import java.util.Map;
import kotlin.Pair;
import kotlinx.coroutines.channels.BufferOverflow;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.MutableSharedFlow;
import kotlinx.coroutines.flow.SharedFlow;
import kotlinx.coroutines.flow.SharedFlowKt;
import timber.log.Timber;

/**
 * Rime與OpenCC的Java實現
 *
 * @see <a href="https://github.com/rime/librime">Rime</a> <a
 *     href="https://github.com/BYVoid/OpenCC">OpenCC</a>
 */
public class Rime {
  public final SharedFlow<RimeEvent> rimeNotiFlow = FlowKt.asSharedFlow(rimeNotiFlow_);

  /** Rime編碼區 */
  public static class RimeComposition {
    int length;
    int cursor_pos;
    int sel_start;
    int sel_end;
    String preedit;
    byte[] bytes;

    public String getText() {
      if (length == 0) return "";
      bytes = preedit.getBytes();
      return preedit;
    }

    public int getStart() {
      if (length == 0) return 0;
      return new String(bytes, 0, sel_start).length();
    }

    public int getEnd() {
      if (length == 0) return 0;
      return new String(bytes, 0, sel_end).length();
    }
  }

  /** Rime候選區，包含多個{@link CandidateListItem 候選項} */
  public static class RimeMenu {
    int page_size;
    int page_no;
    boolean is_last_page;
    int highlighted_candidate_index;
    int num_candidates;
    CandidateListItem[] candidates;
  }

  /** Rime上屏的字符串 */
  public static class RimeCommit {
    int data_size;
    // v0.9
    String text;
  }

  /** Rime環境，包括 {@link RimeComposition 編碼區} 、{@link RimeMenu 候選區} */
  public static class RimeContext {
    // v0.9
    RimeComposition composition;
    RimeMenu menu;
    // v0.9.2
    String commit_text_preview;
    String[] select_labels;

    public CandidateListItem[] getCandidates() {
      int numCandidates = menu != null ? menu.num_candidates : 0;
      Timber.d("setWindow getCandidates() numCandidates=%s", numCandidates);
      return numCandidates != 0 ? menu.candidates : new CandidateListItem[0];
    }
  }

  /** Rime狀態 */
  public static class RimeStatus {
    // v0.9
    String schema_id;
    String schema_name;
    boolean is_disabled;
    boolean is_composing;
    boolean is_ascii_mode;
    boolean is_full_shape;
    boolean is_simplified;
    boolean is_traditional;
    boolean is_ascii_punct;
  }

  private static Rime self;

  private static final RimeCommit mCommit = new RimeCommit();
  private static final RimeContext mContext = new RimeContext();
  private static final RimeStatus mStatus = new RimeStatus();
  private static boolean isHandlingRimeNotification;

  public static final MutableSharedFlow<RimeEvent> rimeNotiFlow_ =
      SharedFlowKt.MutableSharedFlow(0, 15, BufferOverflow.DROP_OLDEST);

  static {
    System.loadLibrary("rime_jni");
  }

  @NonNull
  private static AppPrefs getAppPrefs() {
    return AppPrefs.defaultInstance();
  }
  ;

  /*
  Android SDK包含了如下6个修饰键的状态，其中function键会被trime消费掉，因此只处理5个键
  Android和librime对按键命名并不一致。读取可能有误。librime按键命名见如下链接，
  https://github.com/rime/librime/blob/master/src/rime/key_table.cc
   */
  public static int META_SHIFT_ON = getRimeModifierByName("Shift");
  public static int META_CTRL_ON = getRimeModifierByName("Control");
  public static int META_ALT_ON = getRimeModifierByName("Alt");
  public static int META_SYM_ON = getRimeModifierByName("Super");
  public static int META_META_ON = getRimeModifierByName("Meta");

  public static int META_RELEASE_ON = getRimeModifierByName("Release");

  public static boolean hasMenu() {
    return isComposing() && mContext.menu.num_candidates != 0;
  }

  public static boolean hasLeft() {
    return hasMenu() && mContext.menu.page_no != 0;
  }

  public static boolean hasRight() {
    return hasMenu() && !mContext.menu.is_last_page;
  }

  public static boolean isPaging() {
    return hasLeft();
  }

  public static boolean isComposing() {
    return mStatus.is_composing;
  }

  public static boolean isAsciiMode() {
    return mStatus.is_ascii_mode;
  }

  public static boolean isAsciiPunch() {
    return mStatus.is_ascii_punct;
  }

  public static boolean showAsciiPunch() {
    return mStatus.is_ascii_punct || mStatus.is_ascii_mode;
  }

  public static RimeComposition getComposition() {
    if (mContext == null) return null;
    return mContext.composition;
  }

  public static String getCompositionText() {
    RimeComposition composition = getComposition();
    return (composition == null || composition.preedit == null) ? "" : composition.preedit;
  }

  public static String getComposingText() {
    if (mContext.commit_text_preview == null) return "";
    return mContext.commit_text_preview;
  }

  public Rime(boolean full_check) {
    startup(full_check);
    self = this;
  }

  public static void initSchema() {
    Timber.d("initSchema() RimeSchema");
    SchemaManager.init(getCurrentRimeSchema());
    Timber.d("initSchema() getStatus");
    getStatus();
  }

  @SuppressWarnings("UnusedReturnValue")
  private static boolean getStatus() {
    SchemaManager.updateSwitchOptions();
    return getRimeStatus(mStatus);
  }

  private static void startup(boolean full_check) {
    isHandlingRimeNotification = false;

    DataManager.sync();
    final String sharedDataDir = getAppPrefs().getProfile().getSharedDataDir();
    final String userDataDir = getAppPrefs().getProfile().getUserDataDir();

    Timber.i("Starting up Rime APIs ...");
    startupRime(sharedDataDir, userDataDir, full_check);

    Timber.i("Updating schema switchers ...");
    initSchema();
  }

  public static void deploy() {
    exitRime();
    startup(true);
  }

  public static String getCommitText() {
    return mCommit.text;
  }

  public static boolean getCommit() {
    return getRimeCommit(mCommit);
  }

  public static void getContexts() {
    Timber.i("\t<TrimeInput>\tgetContexts() get_context");
    // get_context() 是耗时操作
    getRimeContext(mContext);
    Timber.i("\t<TrimeInput>\tgetContexts() getStatus");
    getStatus();
    Timber.i("\t<TrimeInput>\tgetContexts() finish");
  }

  public static boolean isVoidKeycode(int keycode) {
    int XK_VoidSymbol = 0xffffff;
    return keycode <= 0 || keycode == XK_VoidSymbol;
  }

  // KeyProcess 调用JNI方法发送keycode和mask
  public static boolean processKey(int keycode, int mask) {
    Timber.i("\t<TrimeInput>\tonkey()\tkeycode=%s, mask=%s", keycode, mask);
    if (isVoidKeycode(keycode)) return false;
    // 此处调用native方法是耗时操作
    final boolean b = processRimeKey(keycode, mask);
    Timber.i(
        "\t<TrimeInput>\tonkey()\tkeycode=%s, mask=%s, process_key result=%s", keycode, mask, b);
    getContexts();
    Timber.i("\t<TrimeInput>\tonkey()\tfinish");
    return b;
  }

  public static boolean isValidText(CharSequence text) {
    if (text == null || text.length() == 0) return false;
    int ch = text.toString().codePointAt(0);
    return ch >= 0x20 && ch < 0x80;
  }

  public static boolean onText(CharSequence text) {
    if (!isValidText(text)) return false;
    boolean b = simulateKeySequence(text.toString().replace("{}", "{braceleft}{braceright}"));
    Timber.i("simulate key sequence = %s, input = %s", b, text);
    getContexts();
    return b;
  }

  public static CandidateListItem[] getCandidatesOrStatusSwitches() {
    final boolean showSwitches = getAppPrefs().getKeyboard().getSwitchesEnabled();
    if (!isComposing() && showSwitches) return SchemaManager.getStatusSwitches();
    return mContext.getCandidates();
  }

  public static CandidateListItem[] getCandidatesWithoutSwitch() {
    if (isComposing()) return mContext.getCandidates();
    return new CandidateListItem[0];
  }

  public static String[] getSelectLabels() {
    return mContext.select_labels;
  }

  public static int getCandHighlightIndex() {
    return isComposing() ? mContext.menu.highlighted_candidate_index : -1;
  }

  public static boolean commitComposition() {
    boolean b = commitRimeComposition();
    getContexts();
    return b;
  }

  public static void clearComposition() {
    clearRimeComposition();
    getContexts();
  }

  public static boolean selectCandidate(int index) {
    boolean b = selectRimeCandidateOnCurrentPage(index);
    getContexts();
    return b;
  }

  public static boolean deleteCandidate(int index) {
    boolean b = deleteRimeCandidateOnCurrentPage(index);
    getContexts();
    return b;
  }

  public static void setOption(String option, boolean value) {
    if (isHandlingRimeNotification) return;
    setRimeOption(option, value);
  }

  public static boolean getOption(String option) {
    return getRimeOption(option);
  }

  public static void toggleOption(String option) {
    boolean b = getOption(option);
    setOption(option, !b);
  }

  private static boolean isEmpty(@NonNull String s) {
    return s.contentEquals(".default"); // 無方案
  }

  public static boolean isEmpty() {
    return isEmpty(getCurrentRimeSchema());
  }

  public static String getSchemaName() {
    return mStatus.schema_name;
  }

  public static boolean selectSchema(String schemaId) {
    Timber.d("Selecting schemaId=%s", schemaId);
    boolean b = selectRimeSchema(schemaId);
    getContexts();
    return b;
  }

  public static Rime get(boolean full_check) {
    if (self == null) {
      if (full_check) {
        OpenCCDictManager.buildOpenCCDict();
      }
      self = new Rime(full_check);
    }
    return self;
  }

  public static Rime get() {
    return get(false);
  }

  public static void RimeSetCaretPos(int caret_pos) {
    setRimeCaretPos(caret_pos);
    getContexts();
  }

  public static void handleRimeNotification(
      @NonNull String messageType, @NonNull String messageValue) {
    isHandlingRimeNotification = true;
    final RimeEvent event = RimeEvent.create(messageType, messageValue);
    Timber.d("Handling Rime notification: %s", event);
    rimeNotiFlow_.tryEmit(event);
    isHandlingRimeNotification = false;
  }

  // init
  public static native void startupRime(
      @NonNull String sharedDir, @NonNull String userDir, boolean fullCheck);

  public static native void exitRime();

  public static native boolean deployRimeSchemaFile(@NonNull String schemaFile);

  public static native boolean deployRimeConfigFile(
      @NonNull String fileName, @NonNull String versionKey);

  public static native boolean syncRimeUserData();

  // input
  public static native boolean processRimeKey(int keycode, int mask);

  public static native boolean commitRimeComposition();

  public static native void clearRimeComposition();

  // output
  public static native boolean getRimeCommit(RimeCommit commit);

  public static native boolean getRimeContext(RimeContext context);

  public static native boolean getRimeStatus(RimeStatus status);

  // runtime options
  public static native void setRimeOption(@NonNull String option, boolean value);

  public static native boolean getRimeOption(@NonNull String option);

  @NonNull
  public static native SchemaListItem[] getRimeSchemaList();

  @NonNull
  public static native String getCurrentRimeSchema();

  public static native boolean selectRimeSchema(@NonNull String schemaId);

  @Nullable
  public static native Map<String, Object> getRimeConfigMap(
      @NonNull String configId, @NonNull String key);

  public static native void setRimeCustomConfigInt(
      @NonNull String configId, @NonNull Pair<String, Integer>[] keyValuePairs);

  // testing
  public static native boolean simulateKeySequence(@NonNull String keySequence);

  public static native String getRimeRawInput();

  public static native int getRimeCaretPos();

  public static native void setRimeCaretPos(int caretPos);

  public static native boolean selectRimeCandidateOnCurrentPage(int index);

  public static native boolean deleteRimeCandidateOnCurrentPage(int index);

  public static native String getLibrimeVersion();

  // module
  public static native boolean runRimeTask(String task_name);

  public static native String getRimeSharedDataDir();

  public static native String getRimeUserDataDir();

  public static native String getRimeSyncDir();

  public static native String getRimeUserId();

  // key_table
  public static native int getRimeModifierByName(@NonNull String name);

  public static native int getRimeKeycodeByName(@NonNull String name);

  @NonNull
  public static native SchemaListItem[] getAvailableRimeSchemaList();

  @NonNull
  public static native SchemaListItem[] getSelectedRimeSchemaList();

  public static native boolean selectRimeSchemas(@NonNull String[] schemaIds);

  @Nullable
  public static native String getRimeStateLabel(@NonNull String optionName, boolean state);
}
