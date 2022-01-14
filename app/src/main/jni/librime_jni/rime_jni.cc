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

jstring get_trime_version(JNIEnv *env, jobject thiz) {
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

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    declare_librime_module_dependencies();
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    registerNativeMethods(env, CLASSNAME, sMethods, NELEMS(sMethods));
    return JNI_VERSION_1_6;
}
