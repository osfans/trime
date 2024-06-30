// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <rime_levers_api.h>

#include "jni-utils.h"
#include "objconv.h"
#include "types.h"

// customize settings

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getAvailableRimeSchemaList(JNIEnv *env,
                                                           jclass /* thiz */) {
  jobjectArray array = nullptr;
  CustomConfig().use([env, &array](auto api, auto settings) {
    SchemaList list(api);
    api->get_available_schema_list((RimeSwitcherSettings *)settings, &list);
    array = rimeSchemaListToJObjectArray(env, *list);
  });
  return array;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getSelectedRimeSchemaList(JNIEnv *env,
                                                          jclass /* thiz */) {
  jobjectArray array = nullptr;
  CustomConfig().use([env, &array](auto api, auto settings) {
    SchemaList list(api);
    api->get_selected_schema_list((RimeSwitcherSettings *)settings, &list);
    array = rimeSchemaListToJObjectArray(env, *list);
  });
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
  CustomConfig().use([schemaIdsLength, &entries](auto api, auto settings) {
    api->select_schemas((RimeSwitcherSettings *)settings, entries,
                        schemaIdsLength);
  });
  for (int i = 0; i < schemaIdsLength; i++) {
    auto string = JRef<jstring>(env, env->GetObjectArrayElement(array, i));
    env->ReleaseStringUTFChars(string, entries[i]);
  }
  return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setRimeCustomConfigItem(JNIEnv *env,
                                                        jclass clazz,
                                                        jstring config_id,
                                                        jobject k2v) {
  CustomConfig(*CString(env, config_id))
      .use([env, k2v](RimeLeversApi *api, auto settings) {
        auto set =
            JRef<>(env, env->CallObjectMethod(k2v, GlobalRef->MapEntries));
        auto iter = JRef<>(env, env->CallObjectMethod(set, GlobalRef->SetIter));
        while (env->CallBooleanMethod(iter, GlobalRef->IterHasNext)) {
          auto entry =
              JRef<>(env, env->CallObjectMethod(iter, GlobalRef->IterNext));
          auto key = CString(
              env, (jstring)env->CallObjectMethod(entry, GlobalRef->MapEntryK));
          auto value =
              JRef<>(env, env->CallObjectMethod(entry, GlobalRef->MapEntryV));
          if (env->IsInstanceOf(value, GlobalRef->String)) {
            api->customize_string(settings, key, CString(env, (jstring)*value));
          } else if (env->IsInstanceOf(value, GlobalRef->Integer)) {
            auto cValue = env->CallIntMethod(value, GlobalRef->IntegerV);
            api->customize_int(settings, key, cValue);
          } else if (env->IsInstanceOf(value, GlobalRef->Double)) {
            auto cValue = env->CallDoubleMethod(value, GlobalRef->DoubleV);
            api->customize_double(settings, key, cValue);
          } else if (env->IsInstanceOf(value, GlobalRef->Boolean)) {
            auto cValue = env->CallBooleanMethod(value, GlobalRef->BooleanV);
            api->customize_bool(settings, key, cValue);
          }
        }
      });
}
