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
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class Rime
{
  public class RimeTraits {
    int data_size;
    // v0.9
    String shared_data_dir;
    String user_data_dir;
    String distribution_name;
    String distribution_code_name;
    String distribution_version;
    // v1.0
    /*!
     * Pass a C-string constant in the format "rime.x"
     * where 'x' is the name of your application.
     * Add prefix "rime." to ensure old log files are automatically cleaned.
     */
    String app_name;

    //! A list of modules to load before initializing
    String[] modules;
  };

  public class RimeComposition {
    int length;
    int cursor_pos;
    int sel_start;
    int sel_end;
    String preedit;
  };

  public class RimeCandidate {
    String text;
    String comment;
  };

  public class RimeMenu {
    int page_size;
    int page_no;
    boolean is_last_page;
    int highlighted_candidate_index;
    int num_candidates;
    RimeCandidate[] candidates;
    String select_keys;
  };
  
  public class RimeCommit {
    int data_size;
    // v0.9
    String text;
  }

  public class RimeContext {
    int data_size;
    // v0.9
    RimeComposition composition;
    RimeMenu menu;
    // v0.9.2
    String commit_text_preview;
  };

  public class RimeStatus {
    int data_size;
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
  };

  RimeCommit mCommit = new RimeCommit();
  RimeContext mContext = new RimeContext();
  RimeStatus mStatus = new RimeStatus();

  private int session_id;
  private static Rime self;
  private static Logger Log = Logger.getLogger(Rime.class.getSimpleName());
  private String kRightArrow = "→ ";
  private String kRadioSelected = " ✓";

  //RimeStatus
  String[] options;
  List<String> radios = new ArrayList<String>();
  boolean[] states;
  String[][] switchStates;
  String[][] switchComments;

  static{
    System.loadLibrary("rime");
    System.loadLibrary("rime_jni");
  }

  public boolean hasLeft() {
    return isComposing() && mContext.menu.page_no != 0;
  }

  public boolean hasRight() {
    return isComposing() && mContext.menu.num_candidates != 0 && !mContext.menu.is_last_page;
  }

  public boolean isComposing() {
    return mStatus.is_composing;
  }

  public String getCompositionText() {
    if (mContext.composition.length > 0) return mContext.composition.preedit;
    else return "";
  }

  public static Rime getRime(){
    if(self == null) self = new Rime();
    return self;
  }

  public String getComposingText() {
    return mContext.commit_text_preview != null ? mContext.commit_text_preview : "";
  }

  public Rime() {
    init(true);
    initSwitches();
  }

  public void initSwitches() {
    String config = getSchemaId() + ".schema";
    int n1 = config_list_size(config, "switches");
    List<String> nameList = new ArrayList<String>();
    List<String> stateList = new ArrayList<String>();
    radios.clear();
    for (int i = 0; i < n1; i++) {
      String k = "switches/@"+i;
      String s = config_get_string(config, k+"/name", "");
      if (!s.isEmpty()) {
        nameList.add(s);
        stateList.add(config_get_string(config, k+"/states/@0", ""));
        stateList.add(config_get_string(config, k+"/states/@1", ""));
      } else {
        int n2 = config_list_size(config, k + "/options");
        for (int j = 0; j < n2; j++) {
          s = config_get_string(config, k + "/options/@" + j, "");
          radios.add(s);
          nameList.add(s);
          stateList.add(config_get_string(config, k + "/states/@" + j, ""));
          stateList.add("");
        }
      }
    }
    int n = nameList.size();
    options = new String[n];
    states = new boolean[n];
    nameList.toArray(options);
    switchStates = new String[n][2];
    switchComments = new String[n][2];
    for (int i = 0; i < n; i ++) {
      states[i] = getOption(options[i]);
      String off = stateList.get(i * 2);
      String on = stateList.get(i * 2 + 1);
      switchStates[i][0] = off;
      if (on.isEmpty()) {
        switchStates[i][1] = off;
        switchComments[i][0] = "";
        switchComments[i][1] = kRadioSelected;
      } else {
        switchStates[i][1] = on;
        switchComments[i][0] = kRightArrow + on;
        switchComments[i][1] = kRightArrow + off;
      }
    }
    getStatus();
  }

  public boolean getStatus() {
    boolean r = get_status(session_id, mStatus);
    int n = states.length;
    for (int i = 0; i < n; i++) states[i] = getOption(options[i]);
    return r;
  }

  public String[] getStatusTexts() {
    int n = states.length;
    String[] s = new String[n];
    for (int i = 0; i < n; i++) s[i] = switchStates[i][states[i] ? 1 : 0];
    return s;
  }

  public String[] getStatusComments() {
    int n = states.length;
    String[] s = new String[n];
    for (int i = 0; i < n; i++) s[i] = switchComments[i][states[i] ? 1 : 0];
    return s;
  }

  public void init(boolean full_check) {
    RimeTraits traits = new RimeTraits();
    traits.user_data_dir = "/sdcard/rime";
    traits.shared_data_dir = "/sdcard/rime";
    traits.app_name = "rime.trime";
    initialize(traits);
    check(full_check);
    createSession();
    if (session_id == 0) Log.severe( "Error creating rime session");
    initSwitches();
  }

  public void destroy() {
    destroySession();
    finalize1();
    self = null;
  }

  public void createSession() {
    if (session_id == 0 || !find_session(session_id)) session_id = create_session();
  }

  public void destroySession() {
    if (session_id != 0) {
      destroy_session(session_id);
      session_id = 0;
    }
  }

  public String getCommitText() {
    return mCommit.text;
  }

  public boolean getCommit() {
    return get_commit(session_id, mCommit);
  }

  public boolean getContexts() {
    boolean b = get_context(session_id, mContext);
    getStatus();
    return b;
  }

  public boolean onKey(int keycode, int mask) {
    boolean b = process_key(session_id, keycode, mask);
    Log.info( "b="+b+",keycode="+keycode);
    getContexts();
    return b;
  }

  public boolean onKey(int[] event) {
    if (event != null && event.length == 2) return onKey(event[0], event[1]);
    return false;
  }

  public boolean onText(CharSequence text) {
    if(text == null || text.length() == 0) return false;
    boolean b = simulate_key_sequence(session_id, text.toString());
    Log.info( "b="+b+",input="+text);
    getContexts();
    return b;
  }

  public int getCandNum() {
    getStatus();
    return isComposing() ? mContext.menu.num_candidates : options.length;
  }

  public String[] getCandidates() {
    if (!isComposing()) return getStatusTexts();
    int n = mContext.menu.num_candidates;
    if (n == 0) return null;
    String[] r = new String[n];
    for(int i = 0; i < n; i++) r[i] = mContext.menu.candidates[i].text;
    return r;
  }

  public String[] getComments() {
    if (!isComposing()) return getStatusComments();
    int n = mContext.menu.num_candidates;
    if (n == 0) return null;
    String[] r = new String[n];
    for(int i = 0; i < n; i++) r[i] = mContext.menu.candidates[i].comment;
    return r;
  }

  public String getCandidate(int i) {
    if (!isComposing()) return getStatusTexts()[i];
    if (mContext.menu.num_candidates == 0) return null;
    return mContext.menu.candidates[i].text;
  }

  public String getComment(int i) {
    if (!isComposing()) return getStatusComments()[i];
    if (mContext.menu.num_candidates == 0) return null;
    return mContext.menu.candidates[i].comment;
  }

  public int getCandHighlightIndex() {
    return isComposing() ? mContext.menu.highlighted_candidate_index : -1;
  }

  public boolean commitComposition() {
    boolean b = commit_composition(session_id);
    Log.info("commitComposition");
    getContexts();
    return b;
  }

  public void clearComposition() {
    clear_composition(session_id);
    Log.info("clearComposition");
    getContexts();
  }

  public boolean selectCandidate(int index) {
    index += mContext.menu.page_no * mContext.menu.page_size; //從頭開始
    boolean b = select_candidate(session_id, index);
    Log.info("selectCandidate");
    getContexts();
    return b;
  }

  public void setOption(String option, boolean value) {
    set_option(session_id, option, value);
  }

  public boolean getOption(String option) {
    return get_option(session_id, option);
  }

  public void toggleOption(String option) {
    boolean r = getOption(option);
    if (radios.contains(option)) {
      if (r) return;
      for (String s: radios) {
        if (getOption(s)) {
          setOption(s, false);
          break;
        }
      }
      setOption(option, true);
    } else setOption(option, !r);
  }

  public void setProperty(String prop, String value) {
    set_property(session_id, prop, value);
  }

  public String getProperty(String prop, String defaultvalue) {
    return get_property(session_id, prop, defaultvalue);
  }

  public void toggleOption(int i) {
    if (i >= 0 && i < options.length) {
      toggleOption(options[i]);
    }
  }

  public String getSchemaId() {
    return get_current_schema(session_id);
  }

  public int getSchemaIndex() {
    String schema_id = getSchemaId();
    List<String> schemas = Arrays.asList(get_schema_ids());
    return schemas.indexOf(schema_id);
  }

  public String getSchemaName() {
    return mStatus.schema_name;
  }

  public boolean selectSchema(String schema_id) {
    boolean b = select_schema(session_id, schema_id);
    getContexts();
    return b;
  }

  public boolean selectSchema(int id) {
    List<String> schemas = Arrays.asList(get_schema_ids());
    String schema_id = getSchemaId();
    if (schemas.indexOf(schema_id) == id) return false;
    return select_schema(session_id, schemas.get(id));
  }

  public String RimeGetInput() {
    return get_input(session_id);
  }

  public int RimeGetCaretPos() {
    return get_caret_pos(session_id);
  }

  public static void onMessage(int session_id, String message_type, String message_value) {
    Log.info(String.format("message: [%d] [%s] %s", session_id, message_type, message_value));
    switch (message_type) {
      case "schema":
        Rime.getRime().initSwitches();
        Trime trime = Trime.getService();
        if (trime != null) {
          trime.initKeyboard();
          trime.updateComposing();
        }
        break;
    }
  }

  // init
  public static native final int get_api();
  public static native final void set_notification_handler();
  public static native final void initialize(RimeTraits traits);
  public static native final void finalize1();
  public static native final void check(boolean full_check);

  // deployment
  public static native final boolean sync_user_data();

  // session management
  public static native final int create_session();
  public static native final boolean find_session(int session_id);
  public static native final boolean destroy_session(int session_id);
  public static native final void cleanup_stale_sessions();
  public static native final void cleanup_all_sessions();

  // input
  public static native final boolean process_key(int session_id, int keycode, int mask);
  public static native final boolean commit_composition(int session_id);
  public static native final void clear_composition(int session_id);

  // output
  public static native final boolean get_commit(int session_id, RimeCommit commit);
  public static native final boolean get_context(int session_id, RimeContext context);
  public static native final boolean get_status(int session_id, RimeStatus status);

  // runtime options
  public static native final void set_option(int session_id, String option, boolean value);
  public static native final boolean get_option(int session_id, String option);
  public static native final void set_property(int session_id, String prop, String value);
  public static native final String get_property(int session_id, String prop, String defaultvalue);
  public static native final String[] get_schema_names();
  public static native final String[] get_schema_ids();
  public static native final String get_current_schema(int session_id);
  public static native final boolean select_schema(int session_id, String schema_id);

  // configuration
  public static native final boolean config_get_bool(String name, String key, boolean defaultvalue);
  public static native final boolean config_set_bool(String name, String key, boolean value);
  public static native final int config_get_int(String name, String key, int defaultvalue);
  public static native final boolean config_set_int(String name, String key, int value);
  public static native final double config_get_double(String name, String key, double defaultvalue);
  public static native final boolean config_set_double(String name, String key, double value);
  public static native final String config_get_string(String name, String key, String defaultvalue);
  public static native final boolean config_set_string(String name, String key, String value);
  public static native final int config_list_size(String name, String key);

  // testing
  public static native final boolean simulate_key_sequence(int session_id, String key_sequence);

  public static native final String get_input(int session_id);
  public static native final int get_caret_pos(int session_id);
  public static native final boolean select_candidate(int session_id, int index);
  public static native final String get_version();

  // key_table
  public static native final int get_modifier_by_name(String name);
  public static native final int get_keycode_by_name(String name);
}
