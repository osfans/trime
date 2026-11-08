// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <opencc/Common.hpp>
#include <opencc/DictConverter.hpp>
#include <opencc/Exception.hpp>
#include <opencc/SimpleConverter.hpp>
#include <string>

#include "jni-utils.h"

// opencc

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_openCCLineConv(
    JNIEnv* env, jclass clazz, jstring input, jstring config_file_name) {
  try {
    auto config = CString(env, config_file_name);
    if (env->ExceptionCheck()) return nullptr;
    auto text = CString(env, input);
    if (env->ExceptionCheck()) return nullptr;
    opencc::SimpleConverter converter(config);
    return makeJavaString(env,
                          converter.Convert(static_cast<std::string>(text)));
  } catch (const opencc::Exception& e) {
    throwJavaException(env, e.what());
    return nullptr;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_openCCDictConv(
    JNIEnv* env, jclass clazz, jstring src, jstring dest, jboolean mode) {
  auto src_file = CString(env, src);
  if (env->ExceptionCheck()) return;
  auto dest_file = CString(env, dest);
  if (env->ExceptionCheck()) return;
  try {
    if (mode) {
      opencc::ConvertDictionary(src_file, dest_file, "ocd2", "text");
    } else {
      opencc::ConvertDictionary(src_file, dest_file, "text", "ocd2");
    }
  } catch (const opencc::Exception& e) {
    throwJavaException(env, e.what());
  }
}
