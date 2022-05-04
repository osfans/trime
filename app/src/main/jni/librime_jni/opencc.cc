#include <string>
#include <opencc/Common.hpp>
#include <opencc/SimpleConverter.hpp>
#include <opencc/DictConverter.hpp>
#include "rime_jni.h"
using namespace opencc;
using std::string;

// opencc

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_get_1opencc_1version(JNIEnv *env, jclass thiz) {
  return env->NewStringUTF(OPENCC_VERSION);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_opencc_1convert(JNIEnv *env, jclass thiz, jstring line, jstring name) {
  if (name == NULL) return line;
  const char* s = env->GetStringUTFChars(name, NULL);
  string str(s);
  SimpleConverter converter(str);
  env->ReleaseStringUTFChars(name, s);
  const char* input = env->GetStringUTFChars(line, NULL);
  const string& converted = converter.Convert(input);
  env->ReleaseStringUTFChars(line, input);
  s = converted.c_str();
  return env->NewStringUTF(s);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_opencc_1convert_1dictionary(JNIEnv *env, jclass thiz, jstring jinputFileName,
    jstring joutputFileName, jstring jformatFrom, jstring jformatTo) {
  const char* s = env->GetStringUTFChars(jinputFileName, NULL);
  string inputFileName(s);
  env->ReleaseStringUTFChars(jinputFileName, s);
  s = env->GetStringUTFChars(joutputFileName, NULL);
  string outputFileName(s);
  env->ReleaseStringUTFChars(joutputFileName, s);
  s = env->GetStringUTFChars(jformatFrom, NULL);
  string formatFrom(s);
  env->ReleaseStringUTFChars(jformatFrom, s);
  s = env->GetStringUTFChars(jformatTo, NULL);
  string formatTo(s);
  env->ReleaseStringUTFChars(jformatTo, s);
  ConvertDictionary(inputFileName, outputFileName, formatFrom, formatTo);
}
