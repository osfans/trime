/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#pragma once

#include <rime_api.h>

#include "helper-types.h"
#include "jni-utils.h"

inline jobject rimeSchemaListItemToJObject(JNIEnv* env,
                                           const SchemaItem& item) {
  return env->NewObject(GlobalRef->SchemaListItem,
                        GlobalRef->SchemaListItemInit,
                        *JString(env, item.schemaId), *JString(env, item.name));
}

inline jobjectArray rimeSchemaListToJObjectArray(
    JNIEnv* env, const std::vector<SchemaItem>& list) {
  jobjectArray array = env->NewObjectArray(static_cast<int>(list.size()),
                                           GlobalRef->SchemaListItem, nullptr);
  int i = 0;
  for (const auto& item : list) {
    auto jItem = JRef(env, rimeSchemaListItemToJObject(env, item));
    env->SetObjectArrayElement(array, i++, jItem);
  }
  return array;
}

inline std::vector<std::string> stringArrayToStringVector(JNIEnv* env,
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

inline jobject rimeCandidateItemToJObject(JNIEnv* env,
                                          const CandidateItem& item) {
  return env->NewObject(GlobalRef->CandidateItem, GlobalRef->CandidateItemInit,
                        *JString(env, item.text), *JString(env, item.comment));
}

inline jobjectArray rimeCandidateListToJObjectArray(
    JNIEnv* env, const std::vector<CandidateItem>& list) {
  jobjectArray array = env->NewObjectArray(static_cast<int>(list.size()),
                                           GlobalRef->CandidateItem, nullptr);
  int i = 0;
  for (const auto& item : list) {
    auto jItem = JRef(env, rimeCandidateItemToJObject(env, item));
    env->SetObjectArrayElement(array, i++, jItem);
  }
  return array;
}

inline jobjectArray stringVectorToJStringArray(
    JNIEnv* env, const std::vector<std::string>& strings) {
  jobjectArray array = env->NewObjectArray(static_cast<int>(strings.size()),
                                           GlobalRef->String, nullptr);
  int i = 0;
  for (const auto& s : strings) {
    env->SetObjectArrayElement(array, i++, JString(env, s));
  }
  return array;
}

inline jobject rimeCommitToJObject(JNIEnv* env, const CommitProto& commit) {
  return env->NewObject(GlobalRef->CommitProto, GlobalRef->CommitProtoInit,
                        commit.text ? *JString(env, *commit.text) : nullptr);
}

inline jobject rimeCandidateToJObject(JNIEnv* env,
                                      const CandidateProto& candidate) {
  return env->NewObject(
      GlobalRef->CandidateProto, GlobalRef->CandidateProtoInit,
      *JString(env, candidate.text),
      candidate.comment ? *JString(env, *candidate.comment) : nullptr,
      *JString(env, candidate.label));
}

inline jobject rimeCompositionToJObject(JNIEnv* env,
                                        const CompositionProto& composition) {
  return env->NewObject(
      GlobalRef->CompositionProto, GlobalRef->CompositionProtoInit,
      composition.length, composition.cursorPos, composition.selStart,
      composition.selEnd,
      composition.preedit ? *JString(env, *composition.preedit) : nullptr,
      composition.commitTextPreview
          ? *JString(env, *composition.commitTextPreview)
          : nullptr);
}

inline jobject rimeMenuToJObject(JNIEnv* env, const MenuProto& menu) {
  int numCandidates = static_cast<int>(menu.candidates.size());
  auto jCandidates = JRef<jobjectArray>(
      env,
      env->NewObjectArray(numCandidates, GlobalRef->CandidateProto, nullptr));
  for (int i = 0; i < numCandidates; ++i) {
    auto jCandidate =
        JRef(env, rimeCandidateToJObject(env, menu.candidates[i]));
    env->SetObjectArrayElement(jCandidates, i, jCandidate);
  }
  return env->NewObject(GlobalRef->MenuProto, GlobalRef->MenuProtoInit,
                        menu.pageSize, menu.pageNumber, menu.isLastPage,
                        menu.highlightedCandidateIndex, *jCandidates,
                        *JString(env, menu.selectKeys),
                        stringVectorToJStringArray(env, menu.selectLabels));
}

inline jobject rimeContextToJObject(JNIEnv* env, const ContextProto& context) {
  return env->NewObject(GlobalRef->ContextProto, GlobalRef->ContextProtoInit,
                        rimeCompositionToJObject(env, context.composition),
                        rimeMenuToJObject(env, context.menu),
                        *JString(env, context.input), context.caretPos);
}

inline jobject rimeStatusToJObject(JNIEnv* env, const StatusProto& status) {
  return env->NewObject(GlobalRef->StatusProto, GlobalRef->StatusProtoInit,
                        *JString(env, status.schemaId),
                        *JString(env, status.schemaName), status.isDisabled,
                        status.isComposing, status.isAsciiMode,
                        status.isFullShape, status.isSimplified,
                        status.isTraditional, status.isAsciiPunct);
}
