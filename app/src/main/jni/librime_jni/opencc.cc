// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <opencc/DictConverter.hpp>
#include <opencc/Exception.hpp>

#include "jni-utils.h"

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_openCCDictConv(
    JNIEnv* env, jclass clazz, jstring src, jstring dest) {
  try {
    auto src_file = CString(env, src);
    auto dest_file = CString(env, dest);
    opencc::ConvertDictionary(src_file, dest_file, "text", "ocd2");
  } catch (const opencc::Exception& e) {
    throwJavaException(env, e.what());
  }
}
