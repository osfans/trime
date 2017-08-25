#include "rime.h"
#include "levers.h"
#include <ctime>
#include <rime_api.h>

static jobject _get_value(JNIEnv *env, RimeConfig* config, const char* key);
static RimeSessionId _session_id = 0;

void on_message(void* context_object,
                RimeSessionId session_id,
                const char* message_type,
                const char* message_value) {
  if (_session_id == 0) return;
  JNIEnv* env = (JNIEnv*)context_object;
  if (env == NULL) return;
  jclass clazz = env->FindClass(CLASSNAME);
  if (clazz == NULL) return;
  jmethodID mid_static_method = env->GetStaticMethodID(clazz, "onMessage","(Ljava/lang/String;Ljava/lang/String;)V");
  if (mid_static_method == NULL) {
    env->DeleteLocalRef(clazz);
    return;
  }
  jstring str_arg1 = newJstring(env, message_type);
  jstring str_arg2 = newJstring(env, message_value);
  env->CallStaticVoidMethod(clazz, mid_static_method, str_arg1, str_arg2);
  env->DeleteLocalRef(clazz);
  env->DeleteLocalRef(str_arg1);
  env->DeleteLocalRef(str_arg2);
}

void set_notification_handler(JNIEnv *env, jobject thiz) { //TODO
  RimeSetNotificationHandler(&on_message, env);
}

void init_traits(JNIEnv *env, jstring shared_data_dir, jstring user_data_dir, void (*func)(RimeTraits *)) {
  RIME_STRUCT(RimeTraits, traits);
  const char* p_shared_data_dir = shared_data_dir == NULL ? NULL : env->GetStringUTFChars(shared_data_dir, NULL);
  const char* p_user_data_dir = user_data_dir == NULL ? NULL : env->GetStringUTFChars(user_data_dir, NULL);
  traits.shared_data_dir = p_shared_data_dir;
  traits.user_data_dir = p_user_data_dir;
  traits.app_name = APP_NAME;
  func(&traits);
  env->ReleaseStringUTFChars(shared_data_dir, p_shared_data_dir);
  env->ReleaseStringUTFChars(user_data_dir, p_user_data_dir);
}

void setup(JNIEnv *env, jobject /*thiz*/, jstring shared_data_dir, jstring user_data_dir) {
  init_traits(env, shared_data_dir, user_data_dir, RimeSetup);
}

// entry and exit
void initialize(JNIEnv *env, jobject thiz, jstring shared_data_dir, jstring user_data_dir) {
  init_traits(env, shared_data_dir, user_data_dir, RimeInitialize);
}

void finalize(JNIEnv *env, jobject thiz) {
  ALOGI("finalize...");
  RimeFinalize();
}

jboolean start_maintenance(JNIEnv *env, jobject thiz, jboolean full_check) {
  return RimeStartMaintenance((Bool)full_check);
}

jboolean is_maintenance_mode(JNIEnv *env, jobject thiz) {
  return RimeIsMaintenancing();
}

void join_maintenance_thread(JNIEnv *env, jobject thiz) {
  RimeJoinMaintenanceThread();
}

// deployment
void deployer_initialize(JNIEnv *env, jobject thiz, jstring shared_data_dir, jstring user_data_dir) {
  init_traits(env, shared_data_dir, user_data_dir, RimeDeployerInitialize);
}

jboolean prebuild(JNIEnv *env, jobject thiz) {
  return RimePrebuildAllSchemas();
}

jboolean deploy(JNIEnv *env, jobject thiz) {
  return RimeDeployWorkspace();
}

jboolean deploy_schema(JNIEnv *env, jobject thiz, jstring schema_file) {
  const char* s = schema_file == NULL ? NULL : env->GetStringUTFChars(schema_file, NULL);
  bool b = RimeDeploySchema(s);
  env->ReleaseStringUTFChars(schema_file, s);
  return b;
}

jboolean deploy_config_file(JNIEnv *env, jobject thiz, jstring file_name, jstring version_key) {
  const char* s = file_name == NULL ? NULL : env->GetStringUTFChars(file_name, NULL);
  const char* s2 = version_key == NULL ? NULL : env->GetStringUTFChars(version_key, NULL);
  bool b = RimeDeployConfigFile(s, s2);
  env->ReleaseStringUTFChars(file_name, s);
  env->ReleaseStringUTFChars(version_key, s2);
  return b;
}

jboolean sync_user_data(JNIEnv *env, jobject thiz) {
  ALOGI("sync user data...");
  return RimeSyncUserData();
}

// session management
jint create_session(JNIEnv *env, jobject thiz) {
  _session_id = RimeCreateSession();
  return _session_id;
}

jboolean find_session(JNIEnv *env, jobject thiz) {
  return RimeFindSession((RimeSessionId)_session_id);
}

jboolean destroy_session(JNIEnv *env, jobject thiz) {
  bool ret = RimeDestroySession((RimeSessionId)_session_id);
  _session_id = 0;
  return ret;
}

void cleanup_stale_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupStaleSessions();
}

void cleanup_all_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupAllSessions();
}

// input
jboolean process_key(JNIEnv *env, jobject thiz, jint keycode, jint mask) {
  return RimeProcessKey((RimeSessionId)_session_id, keycode, mask);
}

jboolean commit_composition(JNIEnv *env, jobject thiz) {
  return RimeCommitComposition((RimeSessionId)_session_id);
}

void clear_composition(JNIEnv *env, jobject thiz) {
  RimeClearComposition((RimeSessionId)_session_id);
}

// output
jboolean get_commit(JNIEnv *env, jobject thiz, jobject jcommit) {
  RIME_STRUCT(RimeCommit, commit);
  Bool r = RimeGetCommit((RimeSessionId)_session_id, &commit);
  if (r) {
    jclass jc = env->GetObjectClass(jcommit);
    jfieldID fid;
    fid = env->GetFieldID(jc, "data_size", "I");
    env->SetIntField(jcommit, fid, commit.data_size);
    fid = env->GetFieldID(jc, "text", "Ljava/lang/String;");
    env->SetObjectField(jcommit, fid, newJstring(env, commit.text));
    env->DeleteLocalRef(jc);
    RimeFreeCommit(&commit);
  }
  return r;
}

jboolean get_context(JNIEnv *env, jobject thiz, jobject jcontext) {
  RIME_STRUCT(RimeContext, context);
  Bool r = RimeGetContext(_session_id, &context);
  if (r) {
    jclass jc = env->GetObjectClass(jcontext);
    jfieldID fid;
    fid = env->GetFieldID(jc, "data_size", "I");
    env->SetIntField(jcontext, fid, context.data_size);
    fid = env->GetFieldID(jc, "commit_text_preview", "Ljava/lang/String;");
    env->SetObjectField(jcontext, fid, newJstring(env, context.commit_text_preview));
    jclass jc1 = env->FindClass(CLASSNAME "$RimeMenu");
    jobject jobj = (jobject) env->AllocObject(jc1);
    fid = env->GetFieldID(jc1, "num_candidates", "I");
    env->SetIntField(jobj, fid, context.menu.num_candidates);
    fid = env->GetFieldID(jc1, "page_size", "I");
    env->SetIntField(jobj, fid, context.menu.page_size);
    fid = env->GetFieldID(jc1, "page_no", "I");
    env->SetIntField(jobj, fid, context.menu.page_no);
    fid = env->GetFieldID(jc1, "highlighted_candidate_index", "I");
    env->SetIntField(jobj, fid, context.menu.highlighted_candidate_index);
    fid = env->GetFieldID(jc1, "is_last_page", "Z");
    env->SetBooleanField(jobj, fid, context.menu.is_last_page);
    fid = env->GetFieldID(jc1, "select_keys", "Ljava/lang/String;");
    env->SetObjectField(jobj, fid, newJstring(env, context.menu.select_keys));

    fid = env->GetFieldID(jc, "select_labels", "[Ljava/lang/String;");
    Bool has_labels = RIME_STRUCT_HAS_MEMBER(context, context.select_labels) && context.select_labels;
    if (has_labels) {
      int n = context.menu.page_size;
      jclass jcs = env->FindClass("java/lang/String");
      jobjectArray jlabels = (jobjectArray) env->NewObjectArray(n, jcs, NULL);
      for (int i = 0; i < n; ++i) {
        env->SetObjectArrayElement(jlabels, i, newJstring(env, context.select_labels[i]));
      }
      env->SetObjectField(jcontext, fid, jlabels);
      env->DeleteLocalRef(jlabels);
      env->DeleteLocalRef(jcs);
    } else {
      env->SetObjectField(jcontext, fid, NULL);
    }

    int n = context.menu.num_candidates;
    jclass jc2 = env->FindClass(CLASSNAME "$RimeCandidate");
    jobjectArray jcandidates = (jobjectArray) env->NewObjectArray(n, jc2, NULL);
    for (int i = 0; i < n;  ++i) {
      jobject jcandidate = (jobject) env->AllocObject(jc2);
      fid = env->GetFieldID(jc2, "text", "Ljava/lang/String;");
      env->SetObjectField(jcandidate, fid, newJstring(env, context.menu.candidates[i].text));
      fid = env->GetFieldID(jc2, "comment", "Ljava/lang/String;");
      env->SetObjectField(jcandidate, fid, newJstring(env, context.menu.candidates[i].comment));
      env->SetObjectArrayElement(jcandidates, i, jcandidate);
      env->DeleteLocalRef(jcandidate);
    }
    fid = env->GetFieldID(jc1, "candidates", "[L" CLASSNAME "$RimeCandidate;");
    env->SetObjectField(jobj, fid, jcandidates);
    env->DeleteLocalRef(jcandidates);

    fid = env->GetFieldID(jc, "menu", "L" CLASSNAME "$RimeMenu;");
    env->SetObjectField(jcontext, fid, jobj);

    jc1 = env->FindClass(CLASSNAME "$RimeComposition");
    jobj = (jobject) env->AllocObject(jc1);
    fid = env->GetFieldID(jc1, "length", "I");
    env->SetIntField(jobj, fid, context.composition.length);
    fid = env->GetFieldID(jc1, "cursor_pos", "I");
    env->SetIntField(jobj, fid, context.composition.cursor_pos);
    fid = env->GetFieldID(jc1, "sel_start", "I");
    env->SetIntField(jobj, fid, context.composition.sel_start);
    fid = env->GetFieldID(jc1, "sel_end", "I");
    env->SetIntField(jobj, fid, context.composition.sel_end);
    fid = env->GetFieldID(jc1, "preedit", "Ljava/lang/String;");
    env->SetObjectField(jobj, fid, newJstring(env, context.composition.preedit));
    fid = env->GetFieldID(jc, "composition", "L" CLASSNAME "$RimeComposition;");
    env->SetObjectField(jcontext, fid, jobj);

    env->DeleteLocalRef(jc);
    env->DeleteLocalRef(jc1);
    env->DeleteLocalRef(jc2);
    RimeFreeContext(&context);
  }
  return r;
}

jboolean get_status(JNIEnv *env, jobject thiz, jobject jstatus) {
  RIME_STRUCT(RimeStatus, status);
  Bool r = RimeGetStatus(_session_id, &status);
  if (r) {
    jclass jc = env->GetObjectClass(jstatus);
    jfieldID fid;
    fid = env->GetFieldID(jc, "data_size", "I");
    env->SetIntField(jstatus, fid, status.data_size);
    fid = env->GetFieldID(jc, "schema_id", "Ljava/lang/String;");
    env->SetObjectField(jstatus, fid, newJstring(env, status.schema_id));
    fid = env->GetFieldID(jc, "schema_name", "Ljava/lang/String;");
    env->SetObjectField(jstatus, fid, newJstring(env, status.schema_name));
    fid = env->GetFieldID(jc, "is_disabled", "Z");
    env->SetBooleanField(jstatus, fid, status.is_disabled);
    fid = env->GetFieldID(jc, "is_composing", "Z");
    env->SetBooleanField(jstatus, fid, status.is_composing);
    fid = env->GetFieldID(jc, "is_ascii_mode", "Z");
    env->SetBooleanField(jstatus, fid, status.is_ascii_mode);
    fid = env->GetFieldID(jc, "is_full_shape", "Z");
    env->SetBooleanField(jstatus, fid, status.is_full_shape);
    fid = env->GetFieldID(jc, "is_simplified", "Z");
    env->SetBooleanField(jstatus, fid, status.is_simplified);
    fid = env->GetFieldID(jc, "is_traditional", "Z");
    env->SetBooleanField(jstatus, fid, status.is_traditional);
    fid = env->GetFieldID(jc, "is_ascii_punct", "Z");
    env->SetBooleanField(jstatus, fid, status.is_ascii_punct);
    env->DeleteLocalRef(jc);
    RimeFreeStatus(&status);
  }
  return r;
}

static bool is_save_option(const char* p) {
  bool is_save = false;
  std::string option_name(p);
  if (option_name.empty()) return is_save;
  RimeConfig config = {0};
  bool b = RimeConfigOpen("default", &config);
  if (!b) return is_save;
  const char *key = "switcher/save_options";
  RimeConfigIterator iter = {0};
  RimeConfigBeginList(&iter, &config, key);
  while(RimeConfigNext(&iter)) {
    std::string item(RimeConfigGetCString(&config, iter.path));
    if (option_name == item) {
      is_save = true;
      break;
    }
  }
  RimeConfigEnd(&iter);
  RimeConfigClose(&config);
  return is_save;
}

// runtime options
void set_option(JNIEnv *env, jobject thiz, jstring option, jboolean value) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  std::string option_name(s);
  RimeConfig config = {0};
  bool b;
  if (is_save_option(s)) {
    b = RimeConfigOpen("user", &config);
    if (b) {
      std::string str("var/option/");
      str += option_name;
      b = RimeConfigSetBool(&config, str.c_str(), value);
    }
    RimeConfigClose(&config);
  }
  RimeSetOption(_session_id, s, value);
  env->ReleaseStringUTFChars(option, s);
}

jboolean get_option(JNIEnv *env, jobject thiz, jstring option) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  bool value = RimeGetOption(_session_id, s);
  env->ReleaseStringUTFChars(option, s);
  return value;
}

void set_property(JNIEnv *env, jobject thiz, jstring prop, jstring value) {
  const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
  const char* v = value == NULL ? NULL : env->GetStringUTFChars(value, NULL);
  RimeSetProperty(_session_id, s, v);
  env->ReleaseStringUTFChars(prop, s);
  env->ReleaseStringUTFChars(value, v);
}

jstring get_property(JNIEnv *env, jobject thiz, jstring prop) {
  const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
  char value[BUFSIZE] = {0};
  bool b = RimeGetProperty(_session_id, s, value, BUFSIZE);
  env->ReleaseStringUTFChars(prop, s);
  return b ? newJstring(env, value) : NULL;
}

jobject get_schema_list(JNIEnv *env, jobject thiz) {
  RimeSchemaList list;
  jobject jobj = NULL;
  if (RimeGetSchemaList(&list)) jobj = _get_schema_list(env, &list);
  RimeFreeSchemaList(&list);
  return jobj;
}

jstring get_current_schema(JNIEnv *env, jobject thiz) {
  char current[BUFSIZE] = {0};
  bool b = RimeGetCurrentSchema(_session_id, current, sizeof(current));
  if (b) return newJstring(env, current);
  return NULL;
}

jboolean select_schema(JNIEnv *env, jobject thiz, jstring schema_id) {
  const char* s = schema_id == NULL ? NULL : env->GetStringUTFChars(schema_id, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen("user", &config);
  if (b) {
    b = RimeConfigSetString(&config, "var/previously_selected_schema", s);
    std::string str(s);
    str = "var/schema_access_time/" + str;
    b = RimeConfigSetInt(&config, str.c_str(), time(NULL));
  }
  RimeConfigClose(&config);
  bool value = RimeSelectSchema(_session_id, s);
  env->ReleaseStringUTFChars(schema_id, s);
  return value;
}

// configuration
jobject config_get_bool(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  Bool value;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    b = RimeConfigGetBool(&config, s, &value);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  if (!b) return NULL;
  jclass jc = env->FindClass("java/lang/Boolean");
  jmethodID ctorID = env->GetMethodID(jc, "<init>", "(Z)V");
  jobject ret = (jobject)env->NewObject(jc, ctorID, value);
  env->DeleteLocalRef(jc);
  return ret;
}

jboolean config_set_bool(JNIEnv *env, jobject thiz, jstring name, jstring key, jboolean value) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    b = RimeConfigSetBool(&config, s, value);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  return b;
}

jobject config_get_int(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  int value;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    b = RimeConfigGetInt(&config, s, &value);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  if (!b) return NULL;
  jclass jc = env->FindClass("java/lang/Integer");
  jmethodID ctorID = env->GetMethodID(jc, "<init>", "(I)V");
  jobject ret = (jobject)env->NewObject(jc, ctorID, value);
  env->DeleteLocalRef(jc);
  return ret;
}

jboolean config_set_int(JNIEnv *env, jobject thiz, jstring name, jstring key, jint value) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    b = RimeConfigSetInt(&config, s, value);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  return b;
}

jobject config_get_double(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  double value;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    b = RimeConfigGetDouble(&config, s, &value);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  if (!b) return NULL;
  jclass jc = env->FindClass("java/lang/Double");
  jmethodID ctorID = env->GetMethodID(jc, "<init>", "(D)V");
  jobject ret = (jobject)env->NewObject(jc, ctorID, value);
  env->DeleteLocalRef(jc);
  return ret;
}

jboolean config_set_double(JNIEnv *env, jobject thiz, jstring name, jstring key, jdouble value) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    b = RimeConfigSetDouble(&config, s, value);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  return b;
}

jstring config_get_string(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  char value[BUFSIZE] = {0};
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    b = RimeConfigGetString(&config, s, value, BUFSIZE);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  return b ? newJstring(env, value) : NULL;
}

jboolean config_set_string(JNIEnv *env, jobject thiz, jstring name, jstring key, jstring value) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    const char* v = env->GetStringUTFChars(value, NULL);
    b = RimeConfigSetString(&config, s, v);
    env->ReleaseStringUTFChars(key, s);
    env->ReleaseStringUTFChars(key, v);
  }
  RimeConfigClose(&config);
  return b;
}

jint config_list_size(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  int value = 0;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    value = RimeConfigListSize(&config, s);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  return value;
}

//testing
jboolean simulate_key_sequence(JNIEnv *env, jobject thiz, jstring key_sequence) {
  const char* str = key_sequence == NULL ? NULL : env->GetStringUTFChars(key_sequence, NULL); 
  if (str == NULL) return false; /* OutOfMemoryError already thrown */
  jboolean r = RimeSimulateKeySequence((RimeSessionId)_session_id, str);
  env->ReleaseStringUTFChars(key_sequence, str);
  return r;
}

jstring get_input(JNIEnv *env, jobject thiz) {
  const char* c = rime_get_api()->get_input(_session_id);
  return newJstring(env, c);
}

jint get_caret_pos(JNIEnv *env, jobject thiz) {
  return rime_get_api()->get_caret_pos(_session_id);
}

void set_caret_pos(JNIEnv *env, jobject thiz, jint caret_pos) {
  return rime_get_api()->set_caret_pos(_session_id, caret_pos);
}

jboolean select_candidate(JNIEnv *env, jobject thiz, jint index) {
  return rime_get_api()->select_candidate(_session_id, index);
}

jboolean select_candidate_on_current_page(JNIEnv *env, jobject thiz, jint index) {
  return rime_get_api()->select_candidate_on_current_page(_session_id, index);
}

jstring get_version(JNIEnv *env, jobject thiz) {
  return newJstring(env, rime_get_api()->get_version());
}

jstring get_librime_version(JNIEnv *env, jobject thiz) {
  return newJstring(env, LIBRIME_VERSION);
}

jobjectArray get_string_list(JNIEnv *env, RimeConfig* config, const char* key) {
  jobjectArray jobj = NULL;
  jclass jc = env->FindClass("java/lang/String");
  int n = RimeConfigListSize(config, key);
  if (n > 0) {
    jobj = (jobjectArray) env->NewObjectArray(n, jc, NULL);
    RimeConfigIterator iter = {0};
    RimeConfigBeginList(&iter, config, key);
    int i = 0;
    while(RimeConfigNext(&iter)) {
      env->SetObjectArrayElement(jobj, i++, newJstring(env, RimeConfigGetCString(config, iter.path)));
    }
    RimeConfigEnd(&iter);
  }
  env->DeleteLocalRef(jc);
  return jobj;
}

static jobject _get_list(JNIEnv *env, RimeConfig* config, const char* key) {
  RimeConfigIterator iter = {0};
  bool b = RimeConfigBeginList(&iter, config, key);
  if (!b) return NULL;
  jclass jc = env->FindClass("java/util/ArrayList");
  if(jc == NULL) return NULL;
  jmethodID init = env->GetMethodID(jc, "<init>", "()V");
  jobject jobj = env->NewObject(jc, init);
  jmethodID add = env->GetMethodID(jc, "add", "(Ljava/lang/Object;)Z");
  while (RimeConfigNext(&iter)) {
    jobject o = _get_value(env, config, iter.path);
    env->CallBooleanMethod(jobj, add, o);
    env->DeleteLocalRef(o);
  }
  RimeConfigEnd(&iter);
  env->DeleteLocalRef(jc);
  return jobj;
}

jobject config_get_list(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  jobject value = NULL;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    value = _get_list(env, &config, s);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  return value;
}

static jobject _get_map(JNIEnv *env, RimeConfig* config, const char* key) {
  RimeConfigIterator iter = {0};
  bool b = RimeConfigBeginMap(&iter, config, key);
  if (!b) return NULL;
  jclass jc = env->FindClass("java/util/HashMap");
  if(jc == NULL) return NULL;
  jmethodID init = env->GetMethodID(jc, "<init>", "()V");
  jobject jobj = env->NewObject(jc, init);
  jmethodID put = env->GetMethodID(jc, "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  while (RimeConfigNext(&iter)) {
    jstring s = newJstring(env, iter.key);
    jobject o = _get_value(env, config, iter.path);
    env->CallObjectMethod(jobj, put, s, o);
    env->DeleteLocalRef(s);
    env->DeleteLocalRef(o);
  }
  RimeConfigEnd(&iter);
  env->DeleteLocalRef(jc);
  return jobj;
}

jobject config_get_map(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  jobject value = NULL;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    value = _get_map(env, &config, s);
    env->ReleaseStringUTFChars(key, s);
  }
  RimeConfigClose(&config);
  return value;
}

jobject _get_value(JNIEnv *env, RimeConfig* config, const char* key) {
  jobject ret;
  jclass jc;
  jmethodID init;
  Bool b_value;

  const char *value = RimeConfigGetCString(config, key);
  if (value != NULL) return newJstring(env, value);
  ret = _get_list(env, config, key);
  if (ret) return ret;
  ret = _get_map(env, config, key);
  return ret;
}

jobject config_get_value(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeConfigOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  jobject ret = NULL;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    ret = _get_value(env, &config, s);
    env->ReleaseStringUTFChars(key, s);
    RimeConfigClose(&config);
  }
  return ret;
}

jobject schema_get_value(JNIEnv *env, jobject thiz, jstring name, jstring key) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool b = RimeSchemaOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  jobject ret = NULL;
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    ret = _get_value(env, &config, s);
    env->ReleaseStringUTFChars(key, s);
    RimeConfigClose(&config);
  }
  return ret;
}

jboolean run_task(JNIEnv *env, jobject thiz, jstring task_name) {
  const char* s = env->GetStringUTFChars(task_name, NULL);
  RimeConfig config = {0};
  Bool b = RimeRunTask(s);
  env->ReleaseStringUTFChars(task_name, s);
  return b;
}

jstring get_shared_data_dir(JNIEnv *env, jobject thiz) {
  return newJstring(env, RimeGetSharedDataDir());
}

jstring get_user_data_dir(JNIEnv *env, jobject thiz) {
  return newJstring(env, RimeGetUserDataDir());
}

jstring get_sync_dir(JNIEnv *env, jobject thiz) {
  return newJstring(env, RimeGetSyncDir());
}

jstring get_user_id(JNIEnv *env, jobject thiz) {
  return newJstring(env, RimeGetUserId());
}
