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
  private int session_id;
  private static Rime self;
  private static Logger Log = Logger.getLogger(Rime.class.getSimpleName());
  private String kRightArrow = "→ ";
  private String kRadioSelected = " ✓";

  //RimeComposition;
  int composition_length;
  int composition_cursor_pos;
  int composition_sel_start;
  int composition_sel_end;
  String composition_preedit;

  //RimeCandidate
  String[] candidates_text = new String[10];
  String[] candidates_comment = new String[10];

  //RimeMenu
  int menu_page_size;
  int menu_page_no;
  boolean menu_is_last_page;
  int menu_highlighted_candidate_index;
  int menu_num_candidates;
  String menu_select_keys;
  
  //RimeCommit
  String commit_text;
  
  //RimeContext
  String commit_text_preview;

  //RimeStatus
  boolean is_composing;
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
    return is_composing && menu_page_no != 0;
  }

  public boolean hasRight() {
    return is_composing && menu_num_candidates != 0 && !menu_is_last_page;
  }

  public boolean isComposing() {
    return is_composing;
  }

  public String getCompositionText() {
    if (composition_length > 0) return composition_preedit;
    else return "";
  }

  public static Rime getRime(){
    if(self == null) self = new Rime();
    return self;
  }

  public String getComposingText() {
    return commit_text_preview != null ? commit_text_preview : "";
  }

  public Rime() {
    init(true);
    initSwitches();
  }

  public void initSwitches() {
    String config = getCurrentSchema() + ".schema";
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

  public void getStatus() {
    is_composing = is_composing(session_id);
    int n = states.length;
    for (int i = 0; i < n; i++) states[i] = getOption(options[i]);
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
    start("/sdcard/rime", "/sdcard/rime");
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
    return commit_text;
  }

  public boolean getCommit() {
    commit_text = get_commit_text(session_id);
    return commit_text != null && !commit_text.isEmpty();
  }

  public boolean getContexts() {
    boolean b = get_context(session_id);
    getStatus();
    Log.info( "compose="+is_composing+",preview="+commit_text_preview);
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
    return is_composing ? menu_num_candidates : options.length;
  }

  public String[] getCandidates() {
    if (!is_composing) return getStatusTexts();
    if (menu_num_candidates == 0) return null;
    String[] r = new String[menu_num_candidates];
    for(int i = 0; i < menu_num_candidates; i++) r[i] = candidates_text[i];
    return r;
  }

  public String[] getComments() {
    if (!is_composing) return getStatusComments();
    if (menu_num_candidates == 0) return null;
    String[] r = new String[menu_num_candidates];
    for(int i = 0; i < menu_num_candidates; i++) r[i] = candidates_comment[i];
    return r;
  }

  public String getCandidate(int i) {
    if (!is_composing) return getStatusTexts()[i];
    if (menu_num_candidates == 0) return null;
    return candidates_text[i];
  }

  public int getCandHighlightIndex() {
    return is_composing ? menu_highlighted_candidate_index : -1;
  }

  public String getComment(int i) {
    if (!is_composing) return getStatusComments()[i];
    if (menu_num_candidates == 0) return null;
    return candidates_comment[i];
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
    index += menu_page_no * menu_page_size; //從頭開始
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

  public String getCurrentSchema() {
    return get_current_schema(session_id);
  }

  public int getCurrentSchemaId() {
    String schema_id = get_current_schema(session_id);
    List<String> schemas = Arrays.asList(get_schema_ids());
    return schemas.indexOf(schema_id);
  }

  public boolean selectSchema(String schema_id) {
    boolean b = select_schema(session_id, schema_id);
    getContexts();
    return b;
  }

  public boolean selectSchema(int id) {
    List<String> schemas = Arrays.asList(get_schema_ids());
    String schema_id = getCurrentSchema();
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
  public static native final void start(String shared_data_dir, String user_data_dir);
  public static native final void check(boolean full_check);
  public static native final void finalize1();

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
  public static native final String get_commit_text(int session_id);
  public native final boolean get_context(int session_id);
  public static native final boolean is_composing(int session_id);

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
