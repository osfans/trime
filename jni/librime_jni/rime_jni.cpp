#include <jni.h>
#include <string.h>
#include <rime_api.h>
#define LOG_TAG "RIME-JNI"

#ifdef ANDROID
#include <android/log.h>
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)
#else
#include <stdio.h>
#define ALOGE printf
#endif

template <typename T, int N>
char (&ArraySizeHelper(T (&array)[N]))[N];
#define NELEMS(x) (sizeof(ArraySizeHelper(x)))

void print_status(RimeStatus *status) {
  ALOGE("schema: %s / %s\n",
         status->schema_id, status->schema_name);
  ALOGE("status: ");
  if (status->is_disabled) ALOGE("disabled ");
  if (status->is_composing) ALOGE("composing ");
  if (status->is_ascii_mode) ALOGE("ascii ");
  if (status->is_full_shape) ALOGE("full_shape ");
  if (status->is_simplified) ALOGE("simplified ");
}

void print_menu(RimeMenu *menu) {
  if (menu->num_candidates == 0) return;
  ALOGE("page: %d%c (of size %d)\n",
         menu->page_no + 1,
         menu->is_last_page ? '$' : ' ',
         menu->page_size);
  for (int i = 0; i < menu->num_candidates; ++i) {
    bool highlighted = i == menu->highlighted_candidate_index;
    ALOGE("%d. %c%s%c%s\n",
           i + 1,
           highlighted ? '[' : ' ',
           menu->candidates[i].text,
           highlighted ? ']' : ' ',
           menu->candidates[i].comment ? menu->candidates[i].comment : "");
  }
}

void print_context(RimeContext *context) {
  if (context->composition.length > 0) {
    print_menu(&context->menu);
  }
  else {
    ALOGE("(not composing)\n");
  }
}

void on_message(void* context_object,
                RimeSessionId session_id,
                const char* message_type,
                const char* message_value) {
  ALOGE("message: [%lu] [%s] %s\n", session_id, message_type, message_value);
}

static void start(JNIEnv *env, jobject thiz, jboolean full_check) {
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = "/sdcard/rime";
  traits.user_data_dir = "/sdcard/rime";
  traits.app_name = "rime.java";
  ALOGE("setup...\n");
  RimeInitialize(&traits);
  RimeStartMaintenance((Bool)full_check);
  if (RimeIsMaintenancing()) RimeJoinMaintenanceThread();
  RimeSetNotificationHandler(&on_message, NULL);
}

static void set_notification_handler(JNIEnv *env, jobject thiz) { //TODO
  RimeSetNotificationHandler(&on_message, NULL);
}

// entry and exit

static void finalize(JNIEnv *env, jobject thiz) {
  ALOGE("finalize...");
  RimeFinalize();
}

// session management
static jint create_session(JNIEnv *env, jobject thiz) {
  RimeSessionId session_id = RimeCreateSession();
  RimeSetOption(session_id, "soft_cursor", True);
  return session_id;
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
  return RimeClearComposition((RimeSessionId)session_id);
}

// output
static jboolean get_commit(JNIEnv *env, jobject thiz, jint session_id) {
  RIME_STRUCT(RimeCommit, commit);
  Bool r = RimeGetCommit((RimeSessionId)session_id, &commit);
  if (r) {
    jclass jc = env->GetObjectClass(thiz);
    jfieldID fid = env->GetFieldID(jc, "commit_text", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, env->NewStringUTF(commit.text));
    RimeFreeCommit(&commit);
  }
  else ALOGE("no commit\n");
  return r;
}

static jboolean get_status(JNIEnv *env, jobject thiz, jint session_id) {
  RIME_STRUCT(RimeStatus, status);
  Bool r = RimeGetStatus(session_id, &status);
  if (r) {
    jclass jc = env->GetObjectClass(thiz);
    jfieldID fid = env->GetFieldID(jc, "schema_id", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, env->NewStringUTF(status.schema_id));
    fid = env->GetFieldID(jc, "schema_name", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, env->NewStringUTF(status.schema_name));
    fid = env->GetFieldID(jc, "is_disabled", "Z");
    env->SetBooleanField(thiz, fid, status.is_disabled);
    fid = env->GetFieldID(jc, "is_composing", "Z");
    env->SetBooleanField(thiz, fid, status.is_composing);
    fid = env->GetFieldID(jc, "is_ascii_mode", "Z");
    env->SetBooleanField(thiz, fid, status.is_ascii_mode);
    fid = env->GetFieldID(jc, "is_full_shape", "Z");
    env->SetBooleanField(thiz, fid, status.is_full_shape);
    fid = env->GetFieldID(jc, "is_simplified", "Z");
    env->SetBooleanField(thiz, fid, status.is_simplified);
    fid = env->GetFieldID(jc, "is_traditional", "Z");
    env->SetBooleanField(thiz, fid, status.is_traditional);
    fid = env->GetFieldID(jc, "is_ascii_punct", "Z");
    env->SetBooleanField(thiz, fid, status.is_ascii_punct);
    RimeFreeStatus(&status);
  }
  return r;
}

static jboolean get_context(JNIEnv *env, jobject thiz, jint session_id) {
  RIME_STRUCT(RimeContext, context);
  Bool r = RimeGetContext(session_id, &context);
  if (r) {
    jclass jc = env->GetObjectClass(thiz);
    jfieldID fid;
    fid = env->GetFieldID(jc, "commit_text_preview", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, env->NewStringUTF(context.commit_text_preview));

    fid = env->GetFieldID(jc, "menu_num_candidates", "I");
    env->SetIntField(thiz, fid, context.menu.num_candidates);
    fid = env->GetFieldID(jc, "menu_page_size", "I");
    env->SetIntField(thiz, fid, context.menu.page_size);
    fid = env->GetFieldID(jc, "menu_page_no", "I");
    env->SetIntField(thiz, fid, context.menu.page_no);
    fid = env->GetFieldID(jc, "menu_highlighted_candidate_index", "I");
    env->SetIntField(thiz, fid, context.menu.highlighted_candidate_index);
    fid = env->GetFieldID(jc, "menu_is_last_page", "Z");
    env->SetBooleanField(thiz, fid, context.menu.is_last_page);
    fid = env->GetFieldID(jc, "menu_select_keys", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, env->NewStringUTF(context.menu.select_keys));

    fid = env->GetFieldID(jc, "composition_length", "I");
    env->SetIntField(thiz, fid, context.composition.length);
    fid = env->GetFieldID(jc, "composition_cursor_pos", "I");
    env->SetIntField(thiz, fid, context.composition.cursor_pos);
    fid = env->GetFieldID(jc, "composition_sel_start", "I");
    env->SetIntField(thiz, fid, context.composition.sel_start);
    fid = env->GetFieldID(jc, "composition_sel_end", "I");
    env->SetIntField(thiz, fid, context.composition.sel_end);
    fid = env->GetFieldID(jc, "composition_preedit", "Ljava/lang/String;");
    env->SetObjectField(thiz, fid, env->NewStringUTF(context.composition.preedit));

    int n = context.menu.num_candidates;
    fid = env->GetFieldID(jc, "candidates_text", "[Ljava/lang/String;");
    jobjectArray texts = (jobjectArray) env->GetObjectField(thiz, fid);
    fid = env->GetFieldID(jc, "candidates_comment", "[Ljava/lang/String;");
    jobjectArray comments = (jobjectArray) env->GetObjectField(thiz, fid);
    for (int i = 0; i < n;  ++i) {
      env->SetObjectArrayElement(texts,i,env->NewStringUTF(context.menu.candidates[i].text));  
      env->SetObjectArrayElement(comments,i,env->NewStringUTF(context.menu.candidates[i].comment));  
    }
    RimeFreeContext(&context);
  }
  return r;
}

//testing
static jboolean simulate_key_sequence(JNIEnv *env, jobject thiz, jint session_id, jstring key_sequence) {
  const char* str = env->GetStringUTFChars(key_sequence, NULL); 
  if (str == NULL) { //不要忘记检测，否则分配内存失败会抛出异常
     return false; /* OutOfMemoryError already thrown */ 
  } 
  jboolean r = RimeSimulateKeySequence((RimeSessionId)session_id, str);
  env->ReleaseStringUTFChars(key_sequence, str);
  return r;
}

static jstring get_version(JNIEnv *env, jobject thiz) {
  RimeApi* rime = rime_get_api();
  const char* c = rime->get_version();
  return env->NewStringUTF(c);
}

static const JNINativeMethod sMethods[] = {
    // init
    {
        const_cast<char *>("start"),
        const_cast<char *>("(Z)V"),
        reinterpret_cast<void *>(start)
    },
    {
        const_cast<char *>("set_notification_handler"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(set_notification_handler)
    },
    // entry and exit
    {
        const_cast<char *>("finalize1"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(finalize)
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
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(get_commit)
    },
    {
        const_cast<char *>("get_context"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(get_context)
    },
    {
        const_cast<char *>("get_status"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(get_status)
    },
    // test
    {
        const_cast<char *>("simulate_key_sequence"),
        const_cast<char *>("(ILjava/lang/String;)Z"),
        reinterpret_cast<void *>(simulate_key_sequence)
    },
    {
        const_cast<char *>("get_version"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_version)
    },
};

int registerNativeMethods(JNIEnv *env, const char *const className, const JNINativeMethod *methods,
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

