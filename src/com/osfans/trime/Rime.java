/*
 * Copyright 2015 osfans
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
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.File;

/** Rime與OpenCC的Java實現
 * @see <a href="https://github.com/rime/librime">Rime</a>
 * <a href="https://github.com/BYVoid/OpenCC">OpenCC</a>
 */
public class Rime
{
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
  };

  /** Rime候選項 */
  public static class RimeCandidate {
    String text;
    String comment;
  };

  /** Rime候選區，包含多個{@link RimeCandidate 候選項} */
  public static class RimeMenu {
    int page_size;
    int page_no;
    boolean is_last_page;
    int highlighted_candidate_index;
    int num_candidates;
    RimeCandidate[] candidates;
    String select_keys;
  };

  /** Rime上屏的字符串 */
  public static class RimeCommit {
    int data_size;
    // v0.9
    String text;
  }

  /** Rime環境，包括
   * {@link RimeComposition 編碼區} 、{@link RimeMenu 候選區}*/
  public static class RimeContext {
    int data_size;
    // v0.9
    RimeComposition composition;
    RimeMenu menu;
    // v0.9.2
    String commit_text_preview;
    String[] select_labels;

    public int size() {
      if (menu == null) return 0;
      return menu.num_candidates;
    }

    public RimeCandidate[] getCandidates() {
      return size() == 0 ? null : menu.candidates;
    }
  };

  /** Rime狀態 */
  public static class RimeStatus {
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

  /** Rime方案 */
  public static class RimeSchema {
    private String kRightArrow = "→ ";
    private String kRadioSelected = " ✓";

    Map<String, Object> schema = new HashMap<String, Object>();
    List<Map<String, Object>> switches = new ArrayList<Map<String, Object>>();

    public RimeSchema(String schema_id) {
      Object o;
      o = schema_get_value(schema_id, "schema");
      if (o == null || !(o instanceof Map)) return;
      schema = (Map<String, Object>)o;
      o = schema_get_value(schema_id, "switches");
      if (o == null || !(o instanceof List)) return;
      switches = (List<Map<String, Object>>)o;
      check(); //檢查不在選單中顯示的選項
    }

    public void check() {
      if (switches.isEmpty()) return;
      for (Iterator it = switches.iterator(); it.hasNext();) {
        Map<String, Object> o = (Map<String, Object>) it.next();
        if (!o.containsKey("states")) it.remove();
      }
    }

    public RimeCandidate[] getCandidates() {
      if (switches.isEmpty()) return null;
      RimeCandidate[] candidates = new RimeCandidate[switches.size()];
      int i = 0;
      for (Map<String, Object> o: switches) {
        candidates[i] = new RimeCandidate();
        List<String> states = (List<String>)o.get("states");
        Integer value = (Integer)o.get("value");
        if (value == null) value = 0;
        candidates[i].text = states.get(value);
        candidates[i].comment = o.containsKey("options") ? "" : kRightArrow + states.get(1 - value);
        i++;
      }
      return candidates;
    }

    public void getValue(int session_id) {
      if (switches.isEmpty()) return; //無方案
      for (int j = 0; j < switches.size(); j++) {
        Map<String, Object> o =  switches.get(j);
        if (o.containsKey("options")) {
          List<String> options = (List<String>)o.get("options");
          for (int i = 0; i < options.size(); i++) {
            String s = options.get(i);
            if (Rime.get_option(session_id, s)) {
              o.put("value", i);
              break;
            }
          }
        } else {
          o.put("value", Rime.get_option(session_id, o.get("name").toString()) ? 1 : 0);
        }
        switches.set(j, o);
      }
    }

    public void toggleOption(int i) {
      if (switches.isEmpty()) return;
      Map<String, Object> o =  switches.get(i);
      Integer value = (Integer)o.get("value");
      if (value == null) value = 0;
      if (o.containsKey("options")) {
        List<String> options = (List<String>)o.get("options");
        Rime.setOption(options.get(value), false);
        value = (value + 1) % options.size();
        Rime.setOption(options.get(value), true);
      } else {
        value = 1 - value;
        Rime.setOption(o.get("name").toString(), value == 1);
      }
      o.put("value", value);
      switches.set(i, o);
    }
  };

  private static int session_id;
  private static Rime self;
  private static Logger Log = Logger.getLogger(Rime.class.getSimpleName());

  private static RimeCommit mCommit = new RimeCommit();
  private static RimeContext mContext = new RimeContext();
  private static RimeStatus mStatus = new RimeStatus();
  private static RimeSchema mSchema;
  private static List mSchemaList;

  static{
    System.loadLibrary("rime");
    System.loadLibrary("rime_jni");
  }

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

  public static RimeComposition getComposition() {
    if (mContext == null) return null;
    return mContext.composition;
  }

  public static String getComposingText() {
    if (mContext == null || mContext.commit_text_preview == null) return "";
    return mContext.commit_text_preview;
  }

  public Rime(boolean full_check) {
    init(full_check);
    self = this;
  }

  public static void initSchema() {
    mSchemaList = get_schema_list();
    String schema_id = getSchemaId();
    mSchema = new RimeSchema(schema_id);
    getStatus();
  }

  public static boolean getStatus() {
    mSchema.getValue(session_id);
    return get_status(session_id, mStatus);
  }

  public static void init(boolean full_check) {
    initialize();
    check(full_check);
    set_notification_handler();
    deployConfigFile();
    createSession();
    if (session_id == 0) {
      Log.severe( "Error creating rime session");
      return;
    }
    initSchema();
  }

  public static void destroy() {
    destroySession();
    finalize1();
    self = null;
  }

  public static void createSession() {
    if (session_id == 0 || !find_session(session_id)) session_id = create_session();
  }

  public static void destroySession() {
    if (session_id != 0) {
      destroy_session(session_id);
      session_id = 0;
    }
  }

  public static String getCommitText() {
    return mCommit.text;
  }

  public static boolean getCommit() {
    return get_commit(session_id, mCommit);
  }

  public static boolean getContexts() {
    boolean b = get_context(session_id, mContext);
    getStatus();
    return b;
  }

  public static boolean onKey(int keycode, int mask) {
    boolean b = process_key(session_id, keycode, mask);
    Log.info( "b="+b+",keycode="+keycode+",mask="+mask);
    getContexts();
    return b;
  }

  public static boolean onKey(int[] event) {
    if (event != null && event.length == 2) return onKey(event[0], event[1]);
    return false;
  }

  public static boolean onText(CharSequence text) {
    if(text == null || text.length() == 0) return false;
    boolean b = simulate_key_sequence(session_id, text.toString());
    Log.info( "b="+b+",input="+text);
    getContexts();
    return b;
  }

  public static RimeCandidate[] getCandidates() {
    if (!isComposing()) return mSchema.getCandidates();
    return mContext.getCandidates();
  }

  public static String[] getSelectLabels() {
    if (mContext != null && mContext.size() > 0) {
      if (mContext.select_labels != null) return mContext.select_labels;
      if (mContext.menu.select_keys != null) return mContext.menu.select_keys.split("\\B");
      int n = mContext.size();
      String[] labels = new String[n];
      for (int i = 0; i < n; i++) {
        labels[i] = String.valueOf((i + 1)% 10);
      }
      return labels;
    }
    return null;
  }

  public static int getCandHighlightIndex() {
    return isComposing() ? mContext.menu.highlighted_candidate_index : -1;
  }

  public static boolean commitComposition() {
    boolean b = commit_composition(session_id);
    getContexts();
    return b;
  }

  public static void clearComposition() {
    clear_composition(session_id);
    getContexts();
  }

  public static boolean selectCandidate(int index) {
    boolean b = select_candidate_on_current_page(session_id, index);
    getContexts();
    return b;
  }

  public static void setOption(String option, boolean value) {
    set_option(session_id, option, value);
  }

  public static boolean getOption(String option) {
    return get_option(session_id, option);
  }

  public static void toggleOption(String option) {
    boolean b = getOption(option);
    setOption(option, !b);
  }

  public static void toggleOption(int i) {
    mSchema.toggleOption(i);
  }

  public static void setProperty(String prop, String value) {
    set_property(session_id, prop, value);
  }

  public static String getProperty(String prop) {
    return get_property(session_id, prop);
  }

  public static String getSchemaId() {
    return get_current_schema(session_id);
  }

  public static boolean isEmpty(String s) {
    return s.contentEquals(".default"); //無方案
  }

  public static boolean isEmpty() {
    return isEmpty(getSchemaId());
  }

  public static String[] getSchemaNames() {
    int n = mSchemaList.size();
    String[] names = new String[n];
    int i = 0;
    for (Object o: mSchemaList) {
      Map<String, String> m = (Map<String, String>)o;
      names[i++] = m.get("name");
    }
    return names;
  }

  public static int getSchemaIndex() {
    String schema_id = getSchemaId();
    int i = 0;
    for (Object o: mSchemaList) {
        Map<String, String> m = (Map<String, String>)o;
        if (m.get("schema_id").contentEquals(schema_id)) return i;
        i++;
    }
    return 0;
  }

  public static String getSchemaName() {
    return mStatus.schema_name;
  }

  public static boolean selectSchema(String schema_id) {
    boolean b = select_schema(session_id, schema_id);
    getContexts();
    return b;
  }

  public static boolean selectSchema(int id) {
    int n = mSchemaList.size();
    if (id < 0 || id >= n) return false;
    String schema_id = getSchemaId();
    Map<String, String> m = (Map<String, String>)mSchemaList.get(id);
    String target = m.get("schema_id");
    if (target.contentEquals(schema_id)) return false;
    return selectSchema(target);
  }

  public static Rime get(boolean full_check){
    if (self == null) self = new Rime(full_check);
    return self;
  }

  public static Rime get(){
    return get(false);
  }

  public static String RimeGetInput() {
    String s = get_input(session_id);
    return s == null ? "" : s;
  }

  public static int RimeGetCaretPos() {
    return get_caret_pos(session_id);
  }

  public static void RimeSetCaretPos(int caret_pos) {
    set_caret_pos(session_id, caret_pos);
    getContexts();
  }

  public static void onMessage(int session_id, String message_type, String message_value) {
    Log.info(String.format("message: [%d] [%s] %s", session_id, message_type, message_value));
    Trime trime = Trime.getService();
    switch (message_type) {
      case "schema":
        initSchema();
        if (trime != null) {
          trime.initKeyboard();
          trime.updateComposing();
        }
        break;
      case "option":
        getStatus();
        if (trime != null) {
          trime.invalidateKeyboard(); //鍵盤狀態
          if (message_value.endsWith("ascii_mode")) trime.setLanguage(isAsciiMode());
        }
        break;
    }
  }

  public static String openccConvert(String line, String name) {
    if (name != null) {
      File f = new File(Config.SDCARD + "rime/opencc", name);
      if (f.exists()) return opencc_convert(line, f.getAbsolutePath());
    }
    return line;
  }

  public static boolean deployConfigFile() {
    return deploy_config_file("trime.yaml", "config_version");
  }

  public static void check(boolean full_check) {
    start_maintenance(full_check);
    if (is_maintenance_mode()) join_maintenance_thread();
  }

  public static boolean syncUserData() {
    boolean b = sync_user_data();
    destroy();
    get();
    return b;
  }

  // init
  public static native final void setup();
  public static native final void set_notification_handler();

  // entry and exit
  public static native final void initialize();
  public static native final void finalize1();
  public static native final boolean start_maintenance(boolean full_check);
  public static native final boolean is_maintenance_mode();
  public static native final void join_maintenance_thread();

  // deployment
  public static native final void deployer_initialize();
  public static native final boolean prebuild();
  public static native final boolean deploy();
  public static native final boolean deploy_schema(String schema_file);
  public static native final boolean deploy_config_file(String file_name, String version_key);
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
  public static native final String get_property(int session_id, String prop);
  public static native final List get_schema_list();
  public static native final String get_current_schema(int session_id);
  public static native final boolean select_schema(int session_id, String schema_id);

  // configuration
  public static native final Boolean config_get_bool(String name, String key);
  public static native final boolean config_set_bool(String name, String key, boolean value);
  public static native final Integer config_get_int(String name, String key);
  public static native final boolean config_set_int(String name, String key, int value);
  public static native final Double config_get_double(String name, String key);
  public static native final boolean config_set_double(String name, String key, double value);
  public static native final String config_get_string(String name, String key);
  public static native final boolean config_set_string(String name, String key, String value);
  public static native final int config_list_size(String name, String key);
  public static native final List config_get_list(String name, String key);
  public static native final Map config_get_map(String name, String key);
  public static native final Object config_get_value(String name, String key);
  public static native final Object schema_get_value(String name, String key);

  // testing
  public static native final boolean simulate_key_sequence(int session_id, String key_sequence);

  public static native final String get_input(int session_id);
  public static native final int get_caret_pos(int session_id);
  public static native final void set_caret_pos(int session_id, int caret_pos);
  public static native final boolean select_candidate(int session_id, int index);
  public static native final boolean select_candidate_on_current_page(int session_id, int index);
  public static native final String get_version();
  public static native final String get_librime_version();

  // module
  public static native final boolean run_task(String task_name);
  public static native final String get_shared_data_dir();
  public static native final String get_user_data_dir();
  public static native final String get_sync_dir();
  public static native final String get_user_id();

  // key_table
  public static native final int get_modifier_by_name(String name);
  public static native final int get_keycode_by_name(String name);

  // customize setting
  public static native final boolean customize_bool(String name, String key, boolean value);
  public static native final boolean customize_int(String name, String key, int value);
  public static native final boolean customize_double(String name, String key, double value);
  public static native final boolean customize_string(String name, String key, String value);
  public static native final List<Map<String,String>> get_available_schema_list();
  public static native final List<Map<String,String>> get_selected_schema_list();
  public static native final boolean select_schemas(String[] schema_id_list);

  // opencc
  public static native final String get_opencc_version();
  public static native final String opencc_convert(String line, String name);
  public static native final void opencc_convert_dictionary(String inputFileName, String outputFileName,
                       String formatFrom, String formatTo);

}
