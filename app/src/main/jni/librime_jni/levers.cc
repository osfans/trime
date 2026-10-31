// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <rime_levers_api.h>

#include "jni-utils.h"
#include "objconv.h"

static RimeLeversApi* rime_get_levers_api() {
  return (RimeLeversApi*)rime_get_api()->find_module("levers")->get_api();
}

class SwitcherSettings {
 public:
  SwitcherSettings()
      : levers(rime_get_levers_api()),
        switcher(rime_get_levers_api()->switcher_settings_init()) {
    levers->load_settings((RimeCustomSettings*)switcher);
  }
  ~SwitcherSettings() {
    if (switcher)
      levers->custom_settings_destroy((RimeCustomSettings*)switcher);
  }

  std::vector<SchemaItem> availableSchemas() {
    std::vector<SchemaItem> result;
    RimeSchemaList list{};
    if (levers->get_available_schema_list(switcher, &list)) {
      result = SchemaItem::fromCList(list);
      levers->schema_list_destroy(&list);
    }
    return result;
  }

  std::vector<SchemaItem> selectedSchemas() {
    std::vector<SchemaItem> result;
    RimeSchemaList list{};
    if (levers->get_selected_schema_list(switcher, &list)) {
      result = SchemaItem::fromCList(list);
      levers->schema_list_destroy(&list);
    }
    return result;
  }

  bool selectSchemas(const std::vector<std::string>& schemas) {
    auto length = schemas.size();
    const char** input = new const char*[length];
    int i = 0;
    for (const auto& id : schemas) {
      input[i++] = id.c_str();
    }
    bool result =
        levers->select_schemas(switcher, input, static_cast<int>(length));
    if (result) {
      levers->save_settings((RimeCustomSettings*)switcher);
    }
    delete[] input;
    return result;
  }

  std::vector<std::string> userDictList() {
    std::vector<std::string> result;
    RimeUserDictIterator iter{};
    if (levers->user_dict_iterator_init(&iter)) {
      while (true) {
        auto name = levers->next_user_dict(&iter);
        if (!name) break;
        result.emplace_back(name);
      }
      levers->user_dict_iterator_destroy(&iter);
    }
    return result;
  }

  bool backupUserDict(std::string_view dictName) {
    return levers->backup_user_dict(dictName.data());
  }

  bool restoreUserDict(std::string_view snapshotFile) {
    return levers->restore_user_dict(snapshotFile.data());
  }

  int exportUserDict(std::string_view dictName, std::string_view textFile) {
    return levers->export_user_dict(dictName.data(), textFile.data());
  }

  int importUserDict(std::string_view dictName, std::string_view textFile) {
    return levers->import_user_dict(dictName.data(), textFile.data());
  }

 private:
  RimeLeversApi* levers;
  RimeSwitcherSettings* switcher;
};

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getAvailableRimeSchemaList(JNIEnv* env,
                                                           jclass /* thiz */) {
  SwitcherSettings switcher;
  return rimeSchemaListToJObjectArray(env, switcher.availableSchemas());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getSelectedRimeSchemaList(JNIEnv* env,
                                                          jclass /* thiz */) {
  SwitcherSettings switcher;
  return rimeSchemaListToJObjectArray(env, switcher.selectedSchemas());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeSchemas(JNIEnv* env,
                                                  jclass /* thiz */,
                                                  jobjectArray array) {
  auto schemas = stringArrayToStringVector(env, array);
  if (env->ExceptionCheck()) return JNI_FALSE;
  SwitcherSettings switcher;
  return switcher.selectSchemas(schemas);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_getUserDictList(
    JNIEnv* env, jclass clazz) {
  SwitcherSettings switcher;
  return stringVectorToJStringArray(env, switcher.userDictList());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_backupUserDict(
    JNIEnv* env, jclass clazz, jstring dict_name) {
  CString name(env, dict_name);
  if (env->ExceptionCheck()) return JNI_FALSE;
  SwitcherSettings switcher;
  return switcher.backupUserDict(*name);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_restoreUserDict(
    JNIEnv* env, jclass clazz, jstring snapshot_file) {
  CString file(env, snapshot_file);
  if (env->ExceptionCheck()) return JNI_FALSE;
  SwitcherSettings switcher;
  return switcher.restoreUserDict(*file);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_exportUserDict(
    JNIEnv* env, jclass clazz, jstring dict_name, jstring text_file) {
  CString name(env, dict_name);
  if (env->ExceptionCheck()) return -1;
  CString file(env, text_file);
  if (env->ExceptionCheck()) return -1;
  SwitcherSettings switcher;
  return switcher.exportUserDict(*name, *file);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_data_userdict_UserDictManager_importUserDict(
    JNIEnv* env, jclass clazz, jstring dict_name, jstring text_file) {
  CString name(env, dict_name);
  if (env->ExceptionCheck()) return -1;
  CString file(env, text_file);
  if (env->ExceptionCheck()) return -1;
  SwitcherSettings switcher;
  return switcher.importUserDict(*name, *file);
}
