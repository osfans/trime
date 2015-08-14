#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <ctime>

#include <rime_api.h>
#include <rime/key_table.h>

#include <opencc/Config.hpp>
#include <opencc/Converter.hpp>
#define LOG_TAG "Rime-JNI"

#ifdef ANDROID
#include <android/log.h>
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)
#else
#define ALOGE printf
#endif

template <typename T, int N>
char (&ArraySizeHelper(T (&array)[N]))[N];
#define NELEMS(x) (sizeof(ArraySizeHelper(x)))
#define BUFSIZE 256

jstring newJstring(JNIEnv* env, const char* pat)
{
  if (!pat) return NULL;
  int n = strlen(pat);
  if (n == 0) return NULL;
  jclass strClass = env->FindClass("java/lang/String");
  jmethodID ctorID = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
  jbyteArray bytes = env->NewByteArray(n);
  env->SetByteArrayRegion(bytes, 0, n, (jbyte*)pat);
  jstring encoding = env->NewStringUTF("utf-8");
  jstring ret = (jstring)env->NewObject(strClass, ctorID, bytes, encoding);
  env->DeleteLocalRef(strClass);
  env->DeleteLocalRef(bytes);
  env->DeleteLocalRef(encoding);
  return ret;
}

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

static void set_notification_handler(JNIEnv *env, jobject thiz) { //TODO
  RimeSetNotificationHandler(&on_message, env);
}

static void check(JNIEnv *env, jobject thiz, jboolean full_check) {
  RimeStartMaintenance((Bool)full_check);
  if (RimeIsMaintenancing()) RimeJoinMaintenanceThread();
  RimeSetNotificationHandler(&on_message, env);
}

// entry and exit
static void initialize(JNIEnv *env, jobject thiz, jobject jtraits) {
  RIME_STRUCT(RimeTraits, traits);
  jclass jc = env->GetObjectClass(jtraits);
  jfieldID fid;
  jstring jshared_data_dir, juser_data_dir, japp_name;
  char* shared_data_dir;
  char* user_data_dir;
  char* app_name;

  fid = env->GetFieldID(jc, "shared_data_dir", "Ljava/lang/String;");
  jshared_data_dir = (jstring)env->GetObjectField(jtraits, fid);
  if (jshared_data_dir != NULL) {
    shared_data_dir = (char *)env->GetStringUTFChars(jshared_data_dir, NULL); 
    traits.shared_data_dir = shared_data_dir;
  }

  fid = env->GetFieldID(jc, "user_data_dir", "Ljava/lang/String;");
  juser_data_dir = (jstring)env->GetObjectField(jtraits, fid);
  if (juser_data_dir != NULL) {
    user_data_dir = (char *)env->GetStringUTFChars(juser_data_dir, NULL);
    traits.user_data_dir = user_data_dir;
  }

  fid = env->GetFieldID(jc, "app_name", "Ljava/lang/String;");
  japp_name = (jstring)env->GetObjectField(jtraits, fid);
  if (japp_name != NULL) {
    app_name = (char *)env->GetStringUTFChars(japp_name, NULL);
    traits.app_name = app_name;
  }

  ALOGE("setup...\n");
  RimeInitialize(&traits);

  env->ReleaseStringUTFChars(jshared_data_dir, shared_data_dir);
  env->ReleaseStringUTFChars(juser_data_dir, user_data_dir);
  env->ReleaseStringUTFChars(japp_name, app_name);
  env->DeleteLocalRef(jc);
}

static void finalize(JNIEnv *env, jobject thiz) {
  ALOGE("finalize...");
  RimeFinalize();
}

// deployment
static jboolean sync_user_data(JNIEnv *env, jobject thiz) {
  ALOGE("sync user data...");
  return RimeSyncUserData();
}

// session management
static jint create_session(JNIEnv *env, jobject thiz) {
  return RimeCreateSession();
}

static jboolean find_session(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeFindSession((RimeSessionId)session_id);
}

static jboolean destroy_session(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeDestroySession((RimeSessionId)session_id);
}

static void cleanup_stale_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupStaleSessions();
}

static void cleanup_all_sessions(JNIEnv *env, jobject thiz) {
  RimeCleanupAllSessions();
}

// input
static jboolean process_key(JNIEnv *env, jobject thiz, jint session_id, jint keycode, jint mask) {
  return RimeProcessKey((RimeSessionId)session_id, keycode, mask);
}

static jboolean commit_composition(JNIEnv *env, jobject thiz, jint session_id) {
  return RimeCommitComposition((RimeSessionId)session_id);
}

static void clear_composition(JNIEnv *env, jobject thiz, jint session_id) {
  RimeClearComposition((RimeSessionId)session_id);
}

// output
static jboolean get_commit(JNIEnv *env, jobject thiz, jint session_id, jobject jcommit) {
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

static jboolean get_context(JNIEnv *env, jobject thiz, jint session_id, jobject jcontext) {
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

static jboolean get_status(JNIEnv *env, jobject thiz, jint session_id, jobject jstatus) {
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
static void set_option(JNIEnv *env, jobject thiz, jint session_id, jstring option, jboolean value) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  RimeSetOption(session_id, s, value);
  env->ReleaseStringUTFChars(option, s);
}

static jboolean get_option(JNIEnv *env, jobject thiz, jint session_id, jstring option) {
  const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
  bool value = RimeGetOption(session_id, s);
  env->ReleaseStringUTFChars(option, s);
  return value;
}

static void set_property(JNIEnv *env, jobject thiz, jint session_id, jstring prop, jstring value) {
  const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
  const char* v = value == NULL ? NULL : env->GetStringUTFChars(value, NULL);
  RimeSetProperty(session_id, s, v);
  env->ReleaseStringUTFChars(prop, s);
  env->ReleaseStringUTFChars(value, v);
}

static jstring get_property(JNIEnv *env, jobject thiz, jint session_id, jstring prop, jstring defaultvalue) {
  const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
  char value[BUFSIZE] = {0};
  bool b = RimeGetProperty(session_id, s, value, BUFSIZE);
  env->ReleaseStringUTFChars(prop, s);
  return b ? newJstring(env, value) : defaultvalue;
}

static jobjectArray get_schema_names(JNIEnv *env, jobject thiz) {
  RimeSchemaList list;
  bool value =RimeGetSchemaList(&list);
  jobjectArray ret = NULL;
  if (value) {
    int n = list.size;
    ret = (jobjectArray) env->NewObjectArray(n, env->FindClass("java/lang/String"), NULL);
    for (size_t i = 0; i < list.size; ++i) {
      env->SetObjectArrayElement(ret, i, newJstring(env, list.list[i].name));
    }
    RimeFreeSchemaList(&list);
  }
  return ret;
}

static jobjectArray get_schema_ids(JNIEnv *env, jobject thiz) {
  RimeSchemaList list;
  bool value =RimeGetSchemaList(&list);
  jobjectArray ret = NULL;
  if (value) {
    int n = list.size;
    ret = (jobjectArray) env->NewObjectArray(n, env->FindClass("java/lang/String"), NULL);
    for (size_t i = 0; i < list.size; ++i) {
      env->SetObjectArrayElement(ret, i, newJstring(env, list.list[i].schema_id));
    }
    RimeFreeSchemaList(&list);
  }
  return ret;
}

static jstring get_current_schema(JNIEnv *env, jobject thiz, jint session_id) {
  char current[BUFSIZE] = {0};
  bool value = RimeGetCurrentSchema(session_id, current, sizeof(current));
  if (value) return newJstring(env, current);
  return NULL;
}

static jboolean select_schema(JNIEnv *env, jobject thiz, jint session_id, jstring schema_id) {
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
static jboolean config_get_bool(JNIEnv *env, jobject thiz, jstring name, jstring key, jboolean defaultvalue) {
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
  return b ? value : defaultvalue;
}

static jboolean config_set_bool(JNIEnv *env, jobject thiz, jstring name, jstring key, jboolean value) {
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

static jint config_get_int(JNIEnv *env, jobject thiz, jstring name, jstring key, jint defaultvalue) {
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
  return b ? value :defaultvalue;
}

static jboolean config_set_int(JNIEnv *env, jobject thiz, jstring name, jstring key, jint value) {
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

static jdouble config_get_double(JNIEnv *env, jobject thiz, jstring name, jstring key, jdouble defaultvalue) {
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
  return b ? value : defaultvalue;
}

static jboolean config_set_double(JNIEnv *env, jobject thiz, jstring name, jstring key, jdouble value) {
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

static jstring config_get_string(JNIEnv *env, jobject thiz, jstring name, jstring key, jstring defaultvalue) {
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
  return b ? newJstring(env, value) : defaultvalue;
}

static jboolean config_set_string(JNIEnv *env, jobject thiz, jstring name, jstring key, jstring value) {
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

static jint config_list_size(JNIEnv *env, jobject thiz, jstring name, jstring key) {
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
static jboolean simulate_key_sequence(JNIEnv *env, jobject thiz, jint session_id, jstring key_sequence) {
  const char* str = key_sequence == NULL ? NULL : env->GetStringUTFChars(key_sequence, NULL); 
  if (str == NULL) return false; /* OutOfMemoryError already thrown */
  jboolean r = RimeSimulateKeySequence((RimeSessionId)session_id, str);
  env->ReleaseStringUTFChars(key_sequence, str);
  return r;
}

static jstring get_input(JNIEnv *env, jobject thiz, jint session_id) {
  const char* c = rime_get_api()->get_input(session_id);
  return newJstring(env, c);
}

static jint get_caret_pos(JNIEnv *env, jobject thiz, jint session_id) {
  return rime_get_api()->get_caret_pos(session_id);
}

static void set_caret_pos(JNIEnv *env, jobject thiz, jint session_id, jint caret_pos) {
  return rime_get_api()->set_caret_pos(session_id, caret_pos);
}

static jboolean select_candidate(JNIEnv *env, jobject thiz, jint session_id, jint index) {
  return rime_get_api()->select_candidate(session_id, index);
}

static jstring get_version(JNIEnv *env, jobject thiz, jstring module) {
  const char* c = module == NULL ? NULL : env->GetStringUTFChars(module, NULL);

  jstring s = NULL;
  if (c == NULL) s = newJstring(env, rime_get_api()->get_version());
  else if (!strcmp(c, "opencc")) s = newJstring(env, OPENCC_VERSION);
  else if (!strcmp(c, "librime")) s = newJstring(env, LIBRIME_VERSION);
  else s = newJstring(env, rime_get_api()->get_version());

  env->ReleaseStringUTFChars(module, c);
  return s;
}

static jint get_modifier_by_name(JNIEnv *env, jobject thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetModifierByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
}

static jint get_keycode_by_name(JNIEnv *env, jobject thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetKeycodeByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
}

static jobjectArray get_string_list(JNIEnv *env, RimeConfig* config, const char* key) {
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

static jboolean get_schema(JNIEnv *env, jobject thiz, jstring name, jobject jschema) {
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

// opencc
static jstring opencc_convert(JNIEnv *env, jobject thiz, jstring line, jstring name) {
  if (name == NULL) return line;
  const char* s = env->GetStringUTFChars(name, NULL);
  string str(s);
  opencc::Config config;
  opencc::ConverterPtr converter = config.NewFromFile(str);
  env->ReleaseStringUTFChars(name, s);
  s = env->GetStringUTFChars(line, NULL);
  str.assign(s);
  const string& converted = converter->Convert(str);
  env->ReleaseStringUTFChars(line, s);
  s = converted.c_str();
  return newJstring(env, s);
}

static const JNINativeMethod sMethods[] = {
    // init
    {
        const_cast<char *>("initialize"),
        const_cast<char *>("(L" CLASSNAME "$RimeTraits;)V"),
        reinterpret_cast<void *>(initialize)
    },
    {
        const_cast<char *>("set_notification_handler"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(set_notification_handler)
    },
    {
        const_cast<char *>("check"),
        const_cast<char *>("(Z)V"),
        reinterpret_cast<void *>(check)
    },
    // entry and exit
    {
        const_cast<char *>("finalize1"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(finalize)
    },
    // deployment
    {
        const_cast<char *>("sync_user_data"),
        const_cast<char *>("()Z"),
        reinterpret_cast<void *>(sync_user_data)
    },
    // session management
    {
        const_cast<char *>("create_session"),
        const_cast<char *>("()I"),
        reinterpret_cast<void *>(create_session)
    },
    {
        const_cast<char *>("find_session"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(find_session)
    },
    {
        const_cast<char *>("destroy_session"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(destroy_session)
    },
    {
        const_cast<char *>("cleanup_stale_sessions"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(cleanup_stale_sessions)
    },
    {
        const_cast<char *>("cleanup_all_sessions"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(cleanup_all_sessions)
    },
    // input
    {
        const_cast<char *>("process_key"),
        const_cast<char *>("(III)Z"),
        reinterpret_cast<void *>(process_key)
    },
    {
        const_cast<char *>("commit_composition"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(commit_composition)
    },
    {
        const_cast<char *>("clear_composition"),
        const_cast<char *>("(I)V"),
        reinterpret_cast<void *>(clear_composition)
    },
    // output
    {
        const_cast<char *>("get_commit"),
        const_cast<char *>("(IL" CLASSNAME "$RimeCommit;)Z"),
        reinterpret_cast<void *>(get_commit)
    },
    {
        const_cast<char *>("get_context"),
        const_cast<char *>("(IL" CLASSNAME "$RimeContext;)Z"),
        reinterpret_cast<void *>(get_context)
    },
    {
        const_cast<char *>("get_status"),
        const_cast<char *>("(IL" CLASSNAME "$RimeStatus;)Z"),
        reinterpret_cast<void *>(get_status)
    },
    // runtime options
    {
        const_cast<char *>("set_option"),
        const_cast<char *>("(ILjava/lang/String;Z)V"),
        reinterpret_cast<void *>(set_option)
    },
    {
        const_cast<char *>("get_option"),
        const_cast<char *>("(ILjava/lang/String;)Z"),
        reinterpret_cast<void *>(get_option)
    },
    {
        const_cast<char *>("set_property"),
        const_cast<char *>("(ILjava/lang/String;Ljava/lang/String;)V"),
        reinterpret_cast<void *>(set_property)
    },
    {
        const_cast<char *>("get_property"),
        const_cast<char *>("(ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void *>(get_property)
    },
    {
        const_cast<char *>("get_schema_names"),
        const_cast<char *>("()[Ljava/lang/String;"),
        reinterpret_cast<void *>(get_schema_names)
    },
    {
        const_cast<char *>("get_schema_ids"),
        const_cast<char *>("()[Ljava/lang/String;"),
        reinterpret_cast<void *>(get_schema_ids)
    },
    {
        const_cast<char *>("get_current_schema"),
        const_cast<char *>("(I)Ljava/lang/String;"),
        reinterpret_cast<void *>(get_current_schema)
    },
    {
        const_cast<char *>("select_schema"),
        const_cast<char *>("(ILjava/lang/String;)Z"),
        reinterpret_cast<void *>(select_schema)
    },
    // configuration
    {
        const_cast<char *>("config_get_bool"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Z)Z"),
        reinterpret_cast<void *>(config_get_bool)
    },
    {
        const_cast<char *>("config_set_bool"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Z)Z"),
        reinterpret_cast<void *>(config_set_bool)
    },
    {
        const_cast<char *>("config_get_int"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;I)I"),
        reinterpret_cast<void *>(config_get_int)
    },
    {
        const_cast<char *>("config_set_int"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;I)Z"),
        reinterpret_cast<void *>(config_set_int)
    },
    {
        const_cast<char *>("config_get_double"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;D)D"),
        reinterpret_cast<void *>(config_get_double)
    },
    {
        const_cast<char *>("config_set_double"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;D)Z"),
        reinterpret_cast<void *>(config_set_int)
    },
    {
        const_cast<char *>("config_get_string"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void *>(config_get_string)
    },
    {
        const_cast<char *>("config_set_string"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(config_set_string)
    },
    {
        const_cast<char *>("config_list_size"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)I"),
        reinterpret_cast<void *>(config_list_size)
    },
    // test
    {
        const_cast<char *>("simulate_key_sequence"),
        const_cast<char *>("(ILjava/lang/String;)Z"),
        reinterpret_cast<void *>(simulate_key_sequence)
    },
    {
        const_cast<char *>("get_input"),
        const_cast<char *>("(I)Ljava/lang/String;"),
        reinterpret_cast<void *>(get_input)
    },
    {
        const_cast<char *>("get_caret_pos"),
        const_cast<char *>("(I)I"),
        reinterpret_cast<void *>(get_caret_pos)
    },
    {
        const_cast<char *>("set_caret_pos"),
        const_cast<char *>("(II)V"),
        reinterpret_cast<void *>(set_caret_pos)
    },
    {
        const_cast<char *>("select_candidate"),
        const_cast<char *>("(II)Z"),
        reinterpret_cast<void *>(select_candidate)
    },
    {
        const_cast<char *>("get_version"),
        const_cast<char *>("(Ljava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void *>(get_version)
    },
    // key_table
    {
        const_cast<char *>("get_modifier_by_name"),
        const_cast<char *>("(Ljava/lang/String;)I"),
        reinterpret_cast<void *>(get_modifier_by_name)
    },
    {
        const_cast<char *>("get_keycode_by_name"),
        const_cast<char *>("(Ljava/lang/String;)I"),
        reinterpret_cast<void *>(get_keycode_by_name)
    },
    {
        const_cast<char *>("get_schema"),
        const_cast<char *>("(Ljava/lang/String;L" CLASSNAME "$RimeSchema;)Z"),
        reinterpret_cast<void *>(get_schema)
    },
    // opencc
     {
        const_cast<char *>("opencc_convert"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void *>(opencc_convert)
    },
};

int registerNativeMethods(JNIEnv *env, const char * className, const JNINativeMethod *methods,
        const int numMethods) {
    jclass clazz = env->FindClass(className);
    if (!clazz) {
        ALOGE("Native registration unable to find class '%s'\n", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, methods, numMethods) != 0) {
        ALOGE("RegisterNatives failed for '%s'\n", className);
        env->DeleteLocalRef(clazz);
        return JNI_FALSE;
    }
    env->DeleteLocalRef(clazz);
    return JNI_TRUE;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    // Get jclass with env->FindClass.
    // Register methods with env->RegisterNatives.
    //const char *const kClassPathName = "com/osfans/trime/Rime";
    registerNativeMethods(env, CLASSNAME, sMethods, NELEMS(sMethods));

    return JNI_VERSION_1_6;
}
