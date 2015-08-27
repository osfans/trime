#include "rime.h"
#include "levers.h"
#include <ctime>
#include <rime_api.h>
#include <rime/key_table.h>

static jobject _get_value(JNIEnv *env, RimeConfig* config, const char* key);

void on_message(void* context_object,
                RimeSessionId session_id,
                const char* message_type,
                const char* message_value) {
  if (session_id == 0) return;
  JNIEnv* env = (JNIEnv*)context_object;
  if (env == NULL) return;
  jclass clazz = env->FindClass(CLASSNAME);
  if (clazz == NULL) return;
  jmethodID mid_static_method = env->GetStaticMethodID(clazz, "onMessage","(ILjava/lang/String;Ljava/lang/String;)V");
  if (mid_static_method == NULL) {
    env->DeleteLocalRef(clazz);
    return;
  }
  jstring str_arg1 = newJstring(env, message_type);
  jstring str_arg2 = newJstring(env, message_value);
  env->CallStaticVoidMethod(clazz, mid_static_method, session_id, str_arg1, str_arg2);
  env->DeleteLocalRef(clazz);
  env->DeleteLocalRef(str_arg1);
  env->DeleteLocalRef(str_arg2);
}

void set_notification_handler(JNIEnv *env, jobject thiz) { //TODO
  RimeSetNotificationHandler(&on_message, env);
}

void check(JNIEnv *env, jobject thiz, jboolean full_check) {
  RimeStartMaintenance((Bool)full_check);
  if (RimeIsMaintenancing()) RimeJoinMaintenanceThread();
  RimeSetNotificationHandler(&on_message, env);
}

void setup() {
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = SHARED_DATA_DIR;
  traits.user_data_dir = USER_DATA_DIR;
  traits.app_name = APP_NAME;
  RimeSetup(&traits);
}

// entry and exit
void initialize(JNIEnv *env, jobject thiz) {
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = SHARED_DATA_DIR;
  traits.user_data_dir = USER_DATA_DIR;
  traits.app_name = APP_NAME;
  RimeInitialize(&traits);
}

void finalize(JNIEnv *env, jobject thiz) {
  ALOGE("finalize...");
  RimeFinalize();
}

// deployment
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
  ALOGE("sync user data...");
  return RimeSyncUserData();
}

// session management
jint create_session(JNIEnv *env, jobject thiz) {
  return RimeCreateSession();
}

jboolean find_session(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeFindSession((RimeSessionId)session_id);
}

jboolean destroy_session(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeDestroySession((RimeSessionId)session_id);
}

void cleanup_stale_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupStaleSessions();
}

void cleanup_all_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupAllSessions();
}

// input
jboolean process_key(JNIEnv *env, jobject thiz, jint session_id, jint keycode, jint mask) {
  return RimeProcessKey((RimeSessionId)session_id, keycode, mask);
}

jboolean commit_composition(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeCommitComposition((RimeSessionId)session_id);
}

void clear_composition(JNIEnv *env, jobject thiz, jint session_id) {
  RimeClearComposition((RimeSessionId)session_id);
}

// output
jboolean get_commit(JNIEnv *env, jobject thiz, jint session_id, jobject jcommit) {
  RIME_STRUCT(RimeCommit, commit);
  Bool r = RimeGetCommit((RimeSessionId)session_id, &commit);
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

jboolean get_context(JNIEnv *env, jobject thiz, jint session_id, jobject jcontext) {
  RIME_STRUCT(RimeContext, context);
  Bool r = RimeGetContext(session_id, &context);
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

jboolean get_status(JNIEnv *env, jobject thiz, jint session_id, jobject jstatus) {
  RIME_STRUCT(RimeStatus, status);
  Bool r = RimeGetStatus(session_id, &status);
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

// runtime options
void set_option(JNIEnv *env, jobject thiz, jint session_id, jstring option, jboolean value) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  RimeSetOption(session_id, s, value);
  env->ReleaseStringUTFChars(option, s);
}

jboolean get_option(JNIEnv *env, jobject thiz, jint session_id, jstring option) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  bool value = RimeGetOption(session_id, s);
  env->ReleaseStringUTFChars(option, s);
  return value;
}

void set_property(JNIEnv *env, jobject thiz, jint session_id, jstring prop, jstring value) {
  const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
  const char* v = value == NULL ? NULL : env->GetStringUTFChars(value, NULL);
  RimeSetProperty(session_id, s, v);
  env->ReleaseStringUTFChars(prop, s);
  env->ReleaseStringUTFChars(value, v);
}

jstring get_property(JNIEnv *env, jobject thiz, jint session_id, jstring prop) {
  const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
  char value[BUFSIZE] = {0};
  bool b = RimeGetProperty(session_id, s, value, BUFSIZE);
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

jstring get_current_schema(JNIEnv *env, jobject thiz, jint session_id) {
  char current[BUFSIZE] = {0};
  bool b = RimeGetCurrentSchema(session_id, current, sizeof(current));
  if (b) return newJstring(env, current);
  return NULL;
}

jboolean select_schema(JNIEnv *env, jobject thiz, jint session_id, jstring schema_id) {
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
  bool value = RimeSelectSchema(session_id, s);
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
jboolean simulate_key_sequence(JNIEnv *env, jobject thiz, jint session_id, jstring key_sequence) {
  const char* str = key_sequence == NULL ? NULL : env->GetStringUTFChars(key_sequence, NULL); 
  if (str == NULL) return false; /* OutOfMemoryError already thrown */
  jboolean r = RimeSimulateKeySequence((RimeSessionId)session_id, str);
  env->ReleaseStringUTFChars(key_sequence, str);
  return r;
}

jstring get_input(JNIEnv *env, jobject thiz, jint session_id) {
  const char* c = rime_get_api()->get_input(session_id);
  return newJstring(env, c);
}

jint get_caret_pos(JNIEnv *env, jobject thiz, jint session_id) {
  return rime_get_api()->get_caret_pos(session_id);
}

void set_caret_pos(JNIEnv *env, jobject thiz, jint session_id, jint caret_pos) {
  return rime_get_api()->set_caret_pos(session_id, caret_pos);
}

jboolean select_candidate(JNIEnv *env, jobject thiz, jint session_id, jint index) {
  return rime_get_api()->select_candidate(session_id, index);
}

jstring get_version(JNIEnv *env, jobject thiz) {
  //jstring s = newJstring(env, rime_get_api()->get_version());
  return newJstring(env, LIBRIME_VERSION);
}

jint get_modifier_by_name(JNIEnv *env, jobject thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetModifierByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
}

jint get_keycode_by_name(JNIEnv *env, jobject thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetKeycodeByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
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

jboolean get_schema(JNIEnv *env, jobject thiz, jstring name, jobject jschema) {
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeConfig config = {0};
  Bool r = RimeSchemaOpen(s, &config);
  env->ReleaseStringUTFChars(name, s);
  if (r) {
    jclass jc = env->GetObjectClass(jschema);
    jfieldID fid;
    fid = env->GetFieldID(jc, "schema_id", "Ljava/lang/String;");
    env->SetObjectField(jschema, fid, newJstring(env, RimeConfigGetCString(&config, "schema/schema_id")));
    fid = env->GetFieldID(jc, "name", "Ljava/lang/String;");
    env->SetObjectField(jschema, fid, newJstring(env, RimeConfigGetCString(&config, "schema/name")));
    fid = env->GetFieldID(jc, "version", "Ljava/lang/String;");
    env->SetObjectField(jschema, fid, newJstring(env, RimeConfigGetCString(&config, "schema/version")));
    fid = env->GetFieldID(jc, "description", "Ljava/lang/String;");
    env->SetObjectField(jschema, fid, newJstring(env, RimeConfigGetCString(&config, "schema/description")));
    fid = env->GetFieldID(jc, "author", "[Ljava/lang/String;");
    jobjectArray jauthor = get_string_list(env, &config, "schema/author");
    env->SetObjectField(jschema, fid, jauthor);
    env->DeleteLocalRef(jauthor);

    jobjectArray jswitches = NULL;
    int n = RimeConfigListSize(&config, "switches");
    if (n > 0) {
      int i = 0;
      jclass jc1 = env->FindClass(CLASSNAME "$RimeSwitches");
      jswitches = (jobjectArray) env->NewObjectArray(n, jc1, NULL);
      RimeConfigIterator iter = {0};
      RimeConfigBeginList(&iter, &config, "switches");
      while(RimeConfigNext(&iter)) {
        char path[BUFSIZE] = {0};
        char value[BUFSIZE] = {0};
        jobject jobj = env->AllocObject(jc1);
        jobject jobj2 = NULL;
        fid = env->GetFieldID(jc1, "is_radio", "Z");
        sprintf(path, "%s/name", iter.path);
        if (RimeConfigGetString(&config, path, value, BUFSIZE)) { //name
          env->SetBooleanField(jobj, fid, false);
          fid = env->GetFieldID(jc1, "name", "Ljava/lang/String;");
          env->SetObjectField(jobj, fid, newJstring(env, value));
        } else { //option list
          env->SetBooleanField(jobj, fid, true);
          sprintf(path, "%s/options", iter.path);
          jobj2 = get_string_list(env, &config, path);
          fid = env->GetFieldID(jc1, "options", "[Ljava/lang/String;");
          env->SetObjectField(jobj, fid, jobj2);
          env->DeleteLocalRef(jobj2);
        }
        sprintf(path, "%s/states", iter.path);
        jobj2 = get_string_list(env, &config, path);
        fid = env->GetFieldID(jc1, "states", "[Ljava/lang/String;");
        env->SetObjectField(jobj, fid, jobj2);
        env->SetObjectArrayElement(jswitches, i++, jobj);
        env->DeleteLocalRef(jobj2);
        env->DeleteLocalRef(jobj);
      }
      RimeConfigEnd(&iter);
      env->DeleteLocalRef(jc1);
    }
    fid = env->GetFieldID(jc, "switches", "[L" CLASSNAME "$RimeSwitches;");
    env->SetObjectField(jschema, fid, jswitches);
    env->DeleteLocalRef(jswitches);

    env->DeleteLocalRef(jc);
    RimeConfigClose(&config);
  }
  return r;
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
    env->CallObjectMethod(jobj, add, o);
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
  if (RimeConfigGetBool(config, key, &b_value)) {
    jc = env->FindClass("java/lang/Boolean");
    init = env->GetMethodID(jc, "<init>", "(Z)V");
    ret = (jobject)env->NewObject(jc, init, b_value);
    env->DeleteLocalRef(jc);
    return ret;
  }
  int i_value;
  if (RimeConfigGetInt(config, key, &i_value)) {
    jc = env->FindClass("java/lang/Integer");
    init = env->GetMethodID(jc, "<init>", "(I)V");
    ret = (jobject)env->NewObject(jc, init, i_value);
    env->DeleteLocalRef(jc);
    return ret;
  }
  double d_value;
  if (RimeConfigGetDouble(config, key, &d_value)) {
    jc = env->FindClass("java/lang/Double");
    init = env->GetMethodID(jc, "<init>", "(D)V");
    ret = (jobject)env->NewObject(jc, init, d_value);
    env->DeleteLocalRef(jc);
    return ret;
  }
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
  }
  RimeConfigClose(&config);
  return ret;
}
