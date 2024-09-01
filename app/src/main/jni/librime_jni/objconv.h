/*
 * SPDX-FileCopyrightText: 2024 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#ifndef TRIME_OBJCONV_H
#define TRIME_OBJCONV_H

#include <rime_api.h>

#include "jni-utils.h"

inline jobject rimeCommitToJObject(JNIEnv *env, const RimeCommit &commit) {
  return env->NewObject(GlobalRef->CommitProto, GlobalRef->CommitProtoInit,
                        *JString(env, commit.text));
}

inline jobject rimeContextToJObject(JNIEnv *env, const RimeContext &context,
                                    std::string_view input, int caretPos) {
  jobject composition = env->NewObject(GlobalRef->CompositionProto,
                                       GlobalRef->CompositionProtoDefault);
  if (RIME_STRUCT_HAS_MEMBER(context, context.composition)) {
    composition = env->NewObject(
        GlobalRef->CompositionProto, GlobalRef->CompositionProtoInit,
        context.composition.length, context.composition.cursor_pos,
        context.composition.sel_start, context.composition.sel_end,
        *JString(env, context.composition.preedit),
        *JString(env, context.commit_text_preview));
  }

  jobject menu =
      env->NewObject(GlobalRef->MenuProto, GlobalRef->MenuProtoDefault);
  if (RIME_STRUCT_HAS_MEMBER(context, context.menu)) {
    const auto &src = context.menu;
    const auto numCandidates = src.num_candidates;
    const auto selectKeysSize = src.select_keys ? strlen(src.select_keys) : 0;
    auto destSelectLabels = JRef<jobjectArray>(
        env,
        env->NewObjectArray(src.num_candidates, GlobalRef->String, nullptr));
    auto destCandidates = JRef<jobjectArray>(
        env, env->NewObjectArray(src.num_candidates, GlobalRef->CandidateProto,
                                 nullptr));
    for (int i = 0; i < numCandidates; ++i) {
      std::string label;
      if (i < src.page_size && RIME_PROVIDED(&context, select_labels)) {
        label = context.select_labels[i];
      } else if (i < selectKeysSize) {
        label = std::string(1, src.select_keys[i]);
      } else {
        label = std::to_string((i + 1) % 10);
      }
      label.append(" ");
      env->SetObjectArrayElement(destSelectLabels, i, JString(env, label));
      const auto &item = src.candidates[i];
      auto candidate = JRef<>(env, env->NewObject(GlobalRef->CandidateProto,
                                                  GlobalRef->CandidateProtoInit,
                                                  *JString(env, item.text),
                                                  *JString(env, item.comment),
                                                  *JString(env, label)));
      env->SetObjectArrayElement(destCandidates, i, candidate);
    }
    menu = env->NewObject(GlobalRef->MenuProto, GlobalRef->MenuProtoInit,
                          src.page_size, src.page_no, src.is_last_page,
                          src.highlighted_candidate_index, *destCandidates,
                          *JString(env, src.select_keys), *destSelectLabels);
  }

  return env->NewObject(GlobalRef->ContextProto, GlobalRef->ContextProtoInit,
                        *JRef<>(env, composition), *JRef<>(env, menu),
                        *JString(env, input.data()), caretPos);
}

inline jobject rimeStatusToJObject(JNIEnv *env, const RimeStatus &status) {
  return env->NewObject(GlobalRef->StatusProto, GlobalRef->StatusProtoInit,
                        *JString(env, status.schema_id),
                        *JString(env, status.schema_name), status.is_disabled,
                        status.is_composing, status.is_ascii_mode,
                        status.is_full_shape, status.is_simplified,
                        status.is_traditional, status.is_ascii_punct);
}

inline jobject rimeConfigValueToJObject(JNIEnv *env, RimeConfig *config,
                                        const std::string &key);

inline jobject rimeConfigListToJObject(JNIEnv *env, RimeConfig *config,
                                       const std::string &key) {
  auto rime = rime_get_api();
  RimeConfigIterator iter = {nullptr};
  if (!rime->config_begin_list(&iter, config, key.c_str())) return nullptr;
  auto size = rime->config_list_size(config, key.c_str());
  auto obj =
      env->NewObject(GlobalRef->ArrayList, GlobalRef->ArrayListInit, size);
  int i = 0;
  while (rime->config_next(&iter)) {
    auto e = JRef<>(env, rimeConfigValueToJObject(env, config, iter.path));
    env->CallVoidMethod(obj, GlobalRef->ArrayListAdd, i++, *e);
  }
  rime->config_end(&iter);
  return obj;
}

inline jobject rimeConfigMapToJObject(JNIEnv *env, RimeConfig *config,
                                      const std::string &key) {
  auto rime = rime_get_api();
  RimeConfigIterator iter = {nullptr};
  if (!rime->config_begin_map(&iter, config, key.c_str())) return nullptr;
  auto obj = env->NewObject(GlobalRef->HashMap, GlobalRef->HashMapInit);
  while (rime->config_next(&iter)) {
    auto v = JRef<>(env, rimeConfigValueToJObject(env, config, iter.path));
    env->CallObjectMethod(obj, GlobalRef->HashMapPut, *JString(env, iter.key),
                          *v);
  }
  rime->config_end(&iter);
  return obj;
}

inline jobject rimeConfigValueToJObject(JNIEnv *env, RimeConfig *config,
                                        const std::string &key) {
  auto rime = rime_get_api();

  const char *value;
  if ((value = rime->config_get_cstring(config, key.c_str()))) {
    return env->NewStringUTF(value);
  }
  jobject list;
  if ((list = rimeConfigListToJObject(env, config, key))) {
    return list;
  }
  return rimeConfigMapToJObject(env, config, key);
}

inline jobjectArray rimeSchemaListToJObjectArray(JNIEnv *env,
                                                 RimeSchemaList &list) {
  jobjectArray array = env->NewObjectArray(static_cast<int>(list.size),
                                           GlobalRef->SchemaListItem, nullptr);
  for (int i = 0; i < list.size; i++) {
    auto item = list.list[i];
    auto obj = JRef<>(
        env,
        env->NewObject(GlobalRef->SchemaListItem, GlobalRef->SchemaListItemInit,
                       *JString(env, item.schema_id ? item.schema_id : ""),
                       *JString(env, item.name ? item.name : "")));
    env->SetObjectArrayElement(array, i, obj);
  }
  return array;
}

#endif  // TRIME_OBJCONV_H
