#include <rime_levers_api.h>

#include "jni-utils.h"
#include "objconv.h"

// customize settings

static RimeLeversApi *get_levers() {
  return (RimeLeversApi *)(RimeFindModule("levers")->get_api());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getAvailableRimeSchemaList(JNIEnv *env,
                                                           jclass /* thiz */) {
  auto levers = get_levers();
  auto switcher = levers->switcher_settings_init();
  RimeSchemaList list = {0};
  levers->load_settings((RimeCustomSettings *)switcher);
  levers->get_available_schema_list(switcher, &list);
  auto array = rimeSchemaListToJObjectArray(env, list);
  levers->schema_list_destroy(&list);
  levers->custom_settings_destroy((RimeCustomSettings *)switcher);
  return array;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getSelectedRimeSchemaList(JNIEnv *env,
                                                          jclass /* thiz */) {
  auto levers = get_levers();
  auto switcher = levers->switcher_settings_init();
  RimeSchemaList list = {0};
  levers->load_settings((RimeCustomSettings *)switcher);
  levers->get_selected_schema_list(switcher, &list);
  auto array = rimeSchemaListToJObjectArray(env, list);
  levers->schema_list_destroy(&list);
  levers->custom_settings_destroy((RimeCustomSettings *)switcher);
  return array;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeSchemas(JNIEnv *env,
                                                  jclass /* thiz */,
                                                  jobjectArray array) {
  int schemaIdsLength = env->GetArrayLength(array);
  const char *entries[schemaIdsLength];
  for (int i = 0; i < schemaIdsLength; i++) {
    auto string = JRef<jstring>(env, env->GetObjectArrayElement(array, i));
    entries[i] = env->GetStringUTFChars(string, nullptr);
  }
  auto levers = get_levers();
  auto switcher = levers->switcher_settings_init();
  levers->load_settings((RimeCustomSettings *)switcher);
  levers->select_schemas(switcher, entries, schemaIdsLength);
  levers->save_settings((RimeCustomSettings *)switcher);
  levers->custom_settings_destroy((RimeCustomSettings *)switcher);
  for (int i = 0; i < schemaIdsLength; i++) {
    auto string = JRef<jstring>(env, env->GetObjectArrayElement(array, i));
    env->ReleaseStringUTFChars(string, entries[i]);
  }
  return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setRimeCustomConfigInt(
    JNIEnv *env, jclass clazz, jstring config_id,
    jobjectArray key_value_pairs) {
  auto levers = get_levers();
  auto custom =
      levers->custom_settings_init(CString(env, config_id), "rime.trime");
  levers->load_settings(custom);
  int arrayLength = env->GetArrayLength(key_value_pairs);
  for (int i = 0; i < arrayLength; i++) {
    auto pair = JRef<>(env, env->GetObjectArrayElement(key_value_pairs, i));
    auto key = CString(
        env, (jstring)env->CallObjectMethod(pair, GlobalRef->PairFirst));
    auto value =
        (jint)(size_t)env->CallObjectMethod(pair, GlobalRef->PairSecond);
    levers->customize_int(custom, key, value);
    levers->save_settings(custom);
  }
  levers->custom_settings_destroy(custom);
}
