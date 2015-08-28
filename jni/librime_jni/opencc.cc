#include "opencc.h"
#include <opencc/Common.hpp>
#include <opencc/SimpleConverter.hpp>

// opencc

jstring get_opencc_version(JNIEnv *env, jobject thiz) {
  return newJstring(env, OPENCC_VERSION);
}

jstring opencc_convert(JNIEnv *env, jobject thiz, jstring line, jstring name) {
  if (name == NULL) return line;
  const char* s = env->GetStringUTFChars(name, NULL);
  std::string str(s);
  opencc::SimpleConverter converter(str);
  env->ReleaseStringUTFChars(name, s);
  const char* input = env->GetStringUTFChars(line, NULL);
  const string& converted = converter.Convert(input);
  env->ReleaseStringUTFChars(line, input);
  s = converted.c_str();
  return newJstring(env, s);
}
