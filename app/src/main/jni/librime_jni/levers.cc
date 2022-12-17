#include "jni-common.h"
#include "rime_jni.h"

// customize settings

static RimeLeversApi* get_levers() {
  return (RimeLeversApi*) (RimeFindModule("levers")->get_api());
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getAvailableRimeSchemaList(JNIEnv *env, jclass /* thiz */) {
  auto levers = get_levers();
  auto switcher = levers->switcher_settings_init();
  RimeSchemaList list = {0};
  levers->load_settings((RimeCustomSettings *) switcher);
  levers->get_available_schema_list(switcher, &list);
  auto array = rimeSchemaListToJObjectArray(env, list);
  levers->schema_list_destroy(&list);
  levers->custom_settings_destroy((RimeCustomSettings *) switcher);
  return array;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getSelectedRimeSchemaList(JNIEnv *env, jclass /* thiz */) {
  auto levers = get_levers();
  auto switcher = levers->switcher_settings_init();
  RimeSchemaList list = {0};
  levers->load_settings((RimeCustomSettings *) switcher);
  levers->get_selected_schema_list(switcher, &list);
  auto array = rimeSchemaListToJObjectArray(env, list);
  levers->schema_list_destroy(&list);
  levers->custom_settings_destroy((RimeCustomSettings *) switcher);
  return array;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeSchemas(JNIEnv *env, jclass /* thiz */, jobjectArray array) {
  int schemaIdsLength = env->GetArrayLength(array);
  const char* entries[schemaIdsLength];
  for (int i = 0; i < schemaIdsLength; i++) {
    auto string = JRef<jstring>(env, env->GetObjectArrayElement(array, i));
    entries[i] = env->GetStringUTFChars(string, nullptr);
  }
  auto levers = get_levers();
  auto switcher = levers->switcher_settings_init();
  levers->load_settings((RimeCustomSettings *) switcher);
  levers->select_schemas(switcher, entries, schemaIdsLength);
  levers->save_settings((RimeCustomSettings *) switcher);
  levers->custom_settings_destroy((RimeCustomSettings *) switcher);
  for (int i = 0; i < schemaIdsLength; i++) {
      auto string = JRef<jstring>(env, env->GetObjectArrayElement(array, i));
      env->ReleaseStringUTFChars(string, entries[i]);
  }
  return true;
}
