#include <opencc/Common.hpp>
#include <opencc/DictConverter.hpp>
#include <opencc/Exception.hpp>
#include <opencc/SimpleConverter.hpp>
#include <string>

#include "jni-utils.h"

// opencc

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_getOpenCCVersion(
    JNIEnv *env, jclass clazz) {
  return env->NewStringUTF(OPENCC_VERSION);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_openCCLineConv(
    JNIEnv *env, jclass clazz, jstring input, jstring config_file_name) {
  try {
    opencc::SimpleConverter converter(CString(env, config_file_name));
    return env->NewStringUTF(converter.Convert(*CString(env, input)).data());
  } catch (const opencc::Exception &e) {
    throwJavaException(env, e.what());
    return env->NewStringUTF("");
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_openCCDictConv(
    JNIEnv *env, jclass clazz, jstring src, jstring dest, jboolean mode) {
  auto src_file = CString(env, src);
  auto dest_file = CString(env, dest);
  try {
    if (mode) {
      opencc::ConvertDictionary(src_file, dest_file, "ocd2", "text");
    } else {
      opencc::ConvertDictionary(src_file, dest_file, "text", "ocd2");
    }
  } catch (const opencc::Exception &e) {
    throwJavaException(env, e.what());
  }
}
