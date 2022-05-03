#include "rime_jni.h"
#include "rime.h"
#include "levers.h"
#include "key_table.h"
#include "opencc.h"

template <typename T, int N>
char (&ArraySizeHelper(T (&array)[N]))[N];
#define NELEMS(x) (sizeof(ArraySizeHelper(x)))

jstring newJstring(JNIEnv* env, const char* pat)
{
  if (pat == NULL) return NULL;
  int n = strlen(pat);
  if (n == 0) return NULL;
  jclass strClass = env->FindClass("java/lang/String");
  jmethodID init = env->GetMethodID(strClass, "<init>", "([BLjava/lang/String;)V");
  jbyteArray bytes = env->NewByteArray(n);
  env->SetByteArrayRegion(bytes, 0, n, (jbyte*)pat);
  jstring encoding = env->NewStringUTF("utf-8");
  jstring ret = (jstring)env->NewObject(strClass, init, bytes, encoding);
  env->DeleteLocalRef(strClass);
  env->DeleteLocalRef(bytes);
  env->DeleteLocalRef(encoding);
  return ret;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1trime_1version(JNIEnv *env, jclass thiz) {
  return newJstring(env, TRIME_VERSION);
}

static const JNINativeMethod sMethods[] = {
    // init
    {
        const_cast<char *>("setup"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)V"),
        reinterpret_cast<void *>(setup)
    },
    {
        const_cast<char *>("set_notification_handler"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(set_notification_handler)
    },
    // entry and exit
    {
        const_cast<char *>("initialize"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)V"),
        reinterpret_cast<void *>(initialize)
    },
    {
        const_cast<char *>("finalize1"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(finalize)
    },
    {
        const_cast<char *>("start_maintenance"),
        const_cast<char *>("(Z)Z"),
        reinterpret_cast<void *>(start_maintenance)
    },
    {
        const_cast<char *>("is_maintenance_mode"),
        const_cast<char *>("()Z"),
        reinterpret_cast<void *>(is_maintenance_mode)
    },
    {
        const_cast<char *>("join_maintenance_thread"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(join_maintenance_thread)
    },
    // deployment
    {
        const_cast<char *>("deployer_initialize"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)V"),
        reinterpret_cast<void *>(deployer_initialize)
    },
    {
        const_cast<char *>("prebuild"),
        const_cast<char *>("()Z"),
        reinterpret_cast<void *>(prebuild)
    },
    {
        const_cast<char *>("deploy"),
        const_cast<char *>("()Z"),
        reinterpret_cast<void *>(deploy)
    },
    {
        const_cast<char *>("deploy_schema"),
        const_cast<char *>("(Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(deploy_schema)
    },
    {
        const_cast<char *>("deploy_config_file"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(deploy_config_file)
    },
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
        const_cast<char *>("()Z"),
        reinterpret_cast<void *>(find_session)
    },
    {
        const_cast<char *>("destroy_session"),
        const_cast<char *>("()Z"),
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
        const_cast<char *>("(II)Z"),
        reinterpret_cast<void *>(process_key)
    },
    {
        const_cast<char *>("commit_composition"),
        const_cast<char *>("()Z"),
        reinterpret_cast<void *>(commit_composition)
    },
    {
        const_cast<char *>("clear_composition"),
        const_cast<char *>("()V"),
        reinterpret_cast<void *>(clear_composition)
    },
    // output
    {
        const_cast<char *>("get_commit"),
        const_cast<char *>("(L" CLASSNAME "$RimeCommit;)Z"),
        reinterpret_cast<void *>(get_commit)
    },
    {
        const_cast<char *>("get_context"),
        const_cast<char *>("(L" CLASSNAME "$RimeContext;)Z"),
        reinterpret_cast<void *>(get_context)
    },
    {
        const_cast<char *>("get_status"),
        const_cast<char *>("(L" CLASSNAME "$RimeStatus;)Z"),
        reinterpret_cast<void *>(get_status)
    },
    // runtime options
    {
        const_cast<char *>("set_option"),
        const_cast<char *>("(Ljava/lang/String;Z)V"),
        reinterpret_cast<void *>(set_option)
    },
    {
        const_cast<char *>("get_option"),
        const_cast<char *>("(Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(get_option)
    },
    {
        const_cast<char *>("set_property"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)V"),
        reinterpret_cast<void *>(set_property)
    },
    {
        const_cast<char *>("get_property"),
        const_cast<char *>("(Ljava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void *>(get_property)
    },
    {
        const_cast<char *>("get_schema_list"),
        const_cast<char *>("()Ljava/util/List;"),
        reinterpret_cast<void *>(get_schema_list)
    },
    {
        const_cast<char *>("get_current_schema"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_current_schema)
    },
    {
        const_cast<char *>("select_schema"),
        const_cast<char *>("(Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(select_schema)
    },
    // configuration
    {
        const_cast<char *>("config_get_bool"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Boolean;"),
        reinterpret_cast<void *>(config_get_bool)
    },
    {
        const_cast<char *>("config_set_bool"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Z)Z"),
        reinterpret_cast<void *>(config_set_bool)
    },
    {
        const_cast<char *>("config_get_int"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Integer;"),
        reinterpret_cast<void *>(config_get_int)
    },
    {
        const_cast<char *>("config_set_int"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;I)Z"),
        reinterpret_cast<void *>(config_set_int)
    },
    {
        const_cast<char *>("config_get_double"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Double;"),
        reinterpret_cast<void *>(config_get_double)
    },
    {
        const_cast<char *>("config_set_double"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;D)Z"),
        reinterpret_cast<void *>(config_set_double)
    },
    {
        const_cast<char *>("config_get_string"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
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
    {
        const_cast<char *>("config_get_list"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;"),
        reinterpret_cast<void *>(config_get_list)
    },
    {
        const_cast<char *>("config_get_map"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/util/Map;"),
        reinterpret_cast<void *>(config_get_map)
    },
    {
        const_cast<char *>("config_get_value"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;"),
        reinterpret_cast<void *>(config_get_value)
    },
    {
        const_cast<char *>("schema_get_value"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;"),
        reinterpret_cast<void *>(schema_get_value)
    },
    // customize settings
    {
        const_cast<char *>("customize_bool"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Z)Z"),
        reinterpret_cast<void *>(customize_bool)
    },
    {
        const_cast<char *>("customize_int"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;I)Z"),
        reinterpret_cast<void *>(customize_int)
    },
    {
        const_cast<char *>("customize_double"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;D)Z"),
        reinterpret_cast<void *>(customize_double)
    },
    {
        const_cast<char *>("customize_string"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(customize_string)
    },
    {
        const_cast<char *>("get_available_schema_list"),
        const_cast<char *>("()Ljava/util/List;"),
        reinterpret_cast<void *>(get_available_schema_list)
    },
    {
        const_cast<char *>("get_selected_schema_list"),
        const_cast<char *>("()Ljava/util/List;"),
        reinterpret_cast<void *>(get_selected_schema_list)
    },
    {
        const_cast<char *>("select_schemas"),
        const_cast<char *>("([Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(select_schemas)
    },
    // test
    {
        const_cast<char *>("simulate_key_sequence"),
        const_cast<char *>("(Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(simulate_key_sequence)
    },
    {
        const_cast<char *>("get_input"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_input)
    },
    {
        const_cast<char *>("get_caret_pos"),
        const_cast<char *>("()I"),
        reinterpret_cast<void *>(get_caret_pos)
    },
    {
        const_cast<char *>("set_caret_pos"),
        const_cast<char *>("(I)V"),
        reinterpret_cast<void *>(set_caret_pos)
    },
    {
        const_cast<char *>("select_candidate"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(select_candidate)
    },
    {
        const_cast<char *>("select_candidate_on_current_page"),
        const_cast<char *>("(I)Z"),
        reinterpret_cast<void *>(select_candidate_on_current_page)
    },
    {
        const_cast<char *>("get_version"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_version)
    },
    {
        const_cast<char *>("get_librime_version"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_librime_version)
    },
    // module
    {
        const_cast<char *>("run_task"),
        const_cast<char *>("(Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(run_task)
    },
    {
        const_cast<char *>("get_shared_data_dir"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_shared_data_dir)
    },
    {
        const_cast<char *>("get_user_data_dir"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_user_data_dir)
    },
    {
        const_cast<char *>("get_sync_dir"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_sync_dir)
    },
    {
        const_cast<char *>("get_user_id"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_user_id)
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
    // opencc
    {
        const_cast<char *>("get_opencc_version"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_opencc_version)
    },
    {
        const_cast<char *>("opencc_convert"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void *>(opencc_convert)
    },
    {
        const_cast<char *>("opencc_convert_dictionary"),
        const_cast<char *>("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"),
        reinterpret_cast<void *>(opencc_convert_dictionary)
    },
    {
        const_cast<char *>("get_trime_version"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(get_trime_version)
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

extern void rime_require_module_lua();
extern void rime_require_module_charcode();
extern void rime_require_module_octagram();
// librime is compiled as a static library, we have to link modules explicitly
static void declare_librime_module_dependencies() {
    rime_require_module_lua();
    rime_require_module_charcode();
    rime_require_module_octagram();
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* jvm, void* reserved)
{
    GlobalRef = new GlobalRefSingleton(jvm);
    declare_librime_module_dependencies();
    JNIEnv* env;
    if (jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    registerNativeMethods(env, CLASSNAME, sMethods, NELEMS(sMethods));
    return JNI_VERSION_1_6;
}

static jobject _get_value(JNIEnv *env, RimeConfig* config, const char* key);
static RimeSessionId _session_id = 0;

void on_message(void* context_object,
                RimeSessionId session_id,
                const char* message_type,
                const char* message_value) {
    if (_session_id == 0) return;
    JavaVM* jvm = (JavaVM*)context_object;
    JNIEnv* env;
    if (jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        // JNI_ERR
        return;
    }
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
extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_set_1notification_1handler(JNIEnv *env, jclass thiz) { //TODO
    JavaVM* jvm;
    env->GetJavaVM(&jvm);
    RimeSetNotificationHandler(&on_message, jvm);
}

void init_traits(JNIEnv *env, jstring shared_data_dir, jstring user_data_dir, void (*func)(RimeTraits *)) {
    RIME_STRUCT(RimeTraits, traits);
    const char* p_shared_data_dir = shared_data_dir == NULL ? NULL : env->GetStringUTFChars(shared_data_dir, NULL);
    const char* p_user_data_dir = user_data_dir == NULL ? NULL : env->GetStringUTFChars(user_data_dir, NULL);
    traits.shared_data_dir = p_shared_data_dir;
    traits.user_data_dir = p_user_data_dir;
    traits.app_name = APP_NAME;
    RimeSetupLogging(APP_NAME);
    func(&traits);
    env->ReleaseStringUTFChars(shared_data_dir, p_shared_data_dir);
    env->ReleaseStringUTFChars(user_data_dir, p_user_data_dir);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setup(JNIEnv *env, jclass clazz, jstring shared_data_dir, jstring user_data_dir) {
    init_traits(env, shared_data_dir, user_data_dir, RimeSetup);
}

// entry and exit
extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_initialize(JNIEnv *env, jclass thiz, jstring shared_data_dir, jstring user_data_dir) {
    init_traits(env, shared_data_dir, user_data_dir, RimeInitialize);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_finalize1(JNIEnv *env, jclass thiz) {
    ALOGI("finalize...");
    RimeFinalize();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_start_1maintenance(JNIEnv *env, jclass thiz, jboolean full_check) {
    return RimeStartMaintenance((Bool)full_check);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_is_1maintenance_1mode(JNIEnv *env, jclass thiz) {
    return RimeIsMaintenancing();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_join_1maintenance_1thread(JNIEnv *env, jclass thiz) {
    RimeJoinMaintenanceThread();
}

// deployment
extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_deployer_1initialize(JNIEnv *env, jclass thiz, jstring shared_data_dir, jstring user_data_dir) {
    init_traits(env, shared_data_dir, user_data_dir, RimeDeployerInitialize);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_prebuild(JNIEnv *env, jclass thiz) {
    return RimePrebuildAllSchemas();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deploy(JNIEnv *env, jclass thiz) {
    return RimeDeployWorkspace();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deploy_1schema(JNIEnv *env, jclass thiz, jstring schema_file) {
    const char* s = schema_file == NULL ? NULL : env->GetStringUTFChars(schema_file, NULL);
    bool b = RimeDeploySchema(s);
    env->ReleaseStringUTFChars(schema_file, s);
    return b;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deploy_1config_1file(JNIEnv *env, jclass thiz, jstring file_name, jstring version_key) {
    const char* s = file_name == NULL ? NULL : env->GetStringUTFChars(file_name, NULL);
    const char* s2 = version_key == NULL ? NULL : env->GetStringUTFChars(version_key, NULL);
    bool b = RimeDeployConfigFile(s, s2);
    env->ReleaseStringUTFChars(file_name, s);
    env->ReleaseStringUTFChars(version_key, s2);
    return b;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_sync_1user_1data(JNIEnv *env, jclass thiz) {
    ALOGI("sync user data...");
    return RimeSyncUserData();
}

// session management
extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_create_1session(JNIEnv *env, jclass thiz) {
    _session_id = RimeCreateSession();
    return _session_id;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_find_1session(JNIEnv *env, jclass thiz) {
    return RimeFindSession((RimeSessionId)_session_id);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_destroy_1session(JNIEnv *env, jclass thiz) {
    bool ret = RimeDestroySession((RimeSessionId)_session_id);
    _session_id = 0;
    return ret;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_cleanup_1stale_1sessions(JNIEnv *env, jclass thiz) {
    RimeCleanupStaleSessions();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_cleanup_1all_1sessions(JNIEnv *env, jclass thiz) {
    RimeCleanupAllSessions();
}

// input
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_process_1key(JNIEnv *env, jclass thiz, jint keycode, jint mask) {
    return RimeProcessKey((RimeSessionId)_session_id, keycode, mask);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_commit_1composition(JNIEnv *env, jclass thiz) {
    return RimeCommitComposition((RimeSessionId)_session_id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_clear_1composition(JNIEnv *env, jclass thiz) {
    RimeClearComposition((RimeSessionId)_session_id);
}

// output
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1commit(JNIEnv *env, jclass thiz, jobject jcommit) {
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1context(JNIEnv *env, jclass thiz, jobject jcontext) {
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1status(JNIEnv *env, jclass thiz, jobject jstatus) {
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
extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_set_1option(JNIEnv *env, jclass thiz, jstring option, jboolean value) {
    const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
    std::string option_name(s);
    RimeConfig config = {0};
    bool b;
    if (is_save_option(s)) {
        b = RimeUserConfigOpen("user", &config);
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_get_1option(JNIEnv *env, jclass thiz, jstring option) {
    const char* s = option == NULL ? NULL : env->GetStringUTFChars(option, NULL);
    bool value = RimeGetOption(_session_id, s);
    env->ReleaseStringUTFChars(option, s);
    return value;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_set_1property(JNIEnv *env, jclass thiz, jstring prop, jstring value) {
    const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
    const char* v = value == NULL ? NULL : env->GetStringUTFChars(value, NULL);
    RimeSetProperty(_session_id, s, v);
    env->ReleaseStringUTFChars(prop, s);
    env->ReleaseStringUTFChars(value, v);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1property(JNIEnv *env, jclass thiz, jstring prop) {
    const char* s = prop == NULL ? NULL : env->GetStringUTFChars(prop, NULL);
    char value[BUFSIZE] = {0};
    bool b = RimeGetProperty(_session_id, s, value, BUFSIZE);
    env->ReleaseStringUTFChars(prop, s);
    return b ? newJstring(env, value) : NULL;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_get_1schema_1list(JNIEnv *env, jclass thiz) {
    RimeSchemaList list;
    jobject jobj = NULL;
    if (RimeGetSchemaList(&list)) jobj = _get_schema_list(env, &list);
    RimeFreeSchemaList(&list);
    return jobj;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1current_1schema(JNIEnv *env, jclass thiz) {
    char current[BUFSIZE] = {0};
    bool b = RimeGetCurrentSchema(_session_id, current, sizeof(current));
    if (b) return newJstring(env, current);
    return NULL;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_select_1schema(JNIEnv *env, jclass thiz, jstring schema_id) {
    const char* s = schema_id == NULL ? NULL : env->GetStringUTFChars(schema_id, NULL);
    RimeConfig config = {0};
    Bool b = RimeUserConfigOpen("user", &config);
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
extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1bool(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1bool(JNIEnv *env, jclass thiz, jstring name, jstring key, jboolean value) {
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

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1int(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1int(JNIEnv *env, jclass thiz, jstring name, jstring key, jint value) {
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

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1double(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1double(JNIEnv *env, jclass thiz, jstring name, jstring key, jdouble value) {
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

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1string(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_config_1set_1string(JNIEnv *env, jclass thiz, jstring name, jstring key, jstring value) {
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

extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_config_1list_1size(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_simulate_1key_1sequence(JNIEnv *env, jclass thiz, jstring key_sequence) {
    const char* str = key_sequence == NULL ? NULL : env->GetStringUTFChars(key_sequence, NULL);
    if (str == NULL) return false; /* OutOfMemoryError already thrown */
    jboolean r = RimeSimulateKeySequence((RimeSessionId)_session_id, str);
    env->ReleaseStringUTFChars(key_sequence, str);
    return r;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1input(JNIEnv *env, jclass thiz) {
    const char* c = rime_get_api()->get_input(_session_id);
    return newJstring(env, c);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_get_1caret_1pos(JNIEnv *env, jclass thiz) {
    return rime_get_api()->get_caret_pos(_session_id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_set_1caret_1pos(JNIEnv *env, jclass thiz, jint caret_pos) {
    return rime_get_api()->set_caret_pos(_session_id, caret_pos);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_select_1candidate(JNIEnv *env, jclass thiz, jint index) {
    return rime_get_api()->select_candidate(_session_id, index);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_select_1candidate_1on_1current_1page(JNIEnv *env, jclass thiz, jint index) {
    return rime_get_api()->select_candidate_on_current_page(_session_id, index);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1version(JNIEnv *env, jclass thiz) {
    return newJstring(env, rime_get_api()->get_version());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1librime_1version(JNIEnv *env, jclass thiz) {
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

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1list(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1map(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_config_1get_1value(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_schema_1get_1value(JNIEnv *env, jclass thiz, jstring name, jstring key) {
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

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_run_1task(JNIEnv *env, jclass thiz, jstring task_name) {
    const char* s = env->GetStringUTFChars(task_name, NULL);
    RimeConfig config = {0};
    Bool b = RimeRunTask(s);
    env->ReleaseStringUTFChars(task_name, s);
    return b;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1shared_1data_1dir(JNIEnv *env, jclass thiz) {
    return newJstring(env, RimeGetSharedDataDir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1user_1data_1dir(JNIEnv *env, jclass thiz) {
    return newJstring(env, RimeGetUserDataDir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1sync_1dir(JNIEnv *env, jclass thiz) {
    return newJstring(env, RimeGetSyncDir());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1user_1id(JNIEnv *env, jclass thiz) {
    return newJstring(env, RimeGetUserId());
}
