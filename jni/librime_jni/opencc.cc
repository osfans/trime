#include "opencc.h"
#include <opencc/Config.hpp>
#include <opencc/Converter.hpp>

// opencc

jstring get_opencc_version(JNIEnv *env, jobject thiz) {
  return newJstring(env, OPENCC_VERSION);
}

jstring opencc_convert(JNIEnv *env, jobject thiz, jstring line, jstring name) {
  if (name == NULL) return line;
  const char* s = env->GetStringUTFChars(name, NULL);
  string str(s);
  opencc::Config config;
  opencc::ConverterPtr converter = config.NewFromFile(str);
  env->ReleaseStringUTFChars(name, s);
  s = env->GetStringUTFChars(line, NULL);
  str.assign(s);
  const string& converted = converter->Convert(str);
  env->ReleaseStringUTFChars(line, s);
  s = converted.c_str();
  return newJstring(env, s);
}
