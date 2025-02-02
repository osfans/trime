/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#pragma once

#include <rime_api.h>

#include "helper-types.h"
#include "jni-utils.h"

inline jobject rimeSchemaListItemToJObject(JNIEnv *env,
                                           const SchemaItem &item) {
  return env->NewObject(GlobalRef->SchemaListItem,
                        GlobalRef->SchemaListItemInit,
                        *JString(env, item.schemaId), *JString(env, item.name));
}

inline jobjectArray rimeSchemaListToJObjectArray(
    JNIEnv *env, const std::vector<SchemaItem> &list) {
  jobjectArray array = env->NewObjectArray(static_cast<int>(list.size()),
                                           GlobalRef->SchemaListItem, nullptr);
  int i = 0;
  for (const auto &item : list) {
    auto jItem = JRef(env, rimeSchemaListItemToJObject(env, item));
    env->SetObjectArrayElement(array, i++, jItem);
  }
  return array;
}

inline std::vector<std::string> stringArrayToStringVector(JNIEnv *env,
                                                          jobjectArray array) {
  auto length = env->GetArrayLength(array);
  std::vector<std::string> result;
  result.reserve(length);
  for (int i = 0; i < length; ++i) {
    CString cstr(
        env, reinterpret_cast<jstring>(env->GetObjectArrayElement(array, i)));
    result.emplace_back(cstr);
  }
  return std::move(result);
}

inline jobject rimeCandidateItemToJObject(JNIEnv *env,
                                          const CandidateItem &item) {
  return env->NewObject(GlobalRef->CandidateItem, GlobalRef->CandidateItemInit,
                        *JString(env, item.text), *JString(env, item.comment));
}

inline jobjectArray rimeCandidateListToJObjectArray(
    JNIEnv *env, const std::vector<CandidateItem> &list) {
  jobjectArray array = env->NewObjectArray(static_cast<int>(list.size()),
                                           GlobalRef->CandidateItem, nullptr);
  int i = 0;
  for (const auto &item : list) {
    auto jItem = JRef(env, rimeCandidateItemToJObject(env, item));
    env->SetObjectArrayElement(array, i++, jItem);
  }
  return array;
}
