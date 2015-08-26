#ifndef OPENCC_H_
#define OPENCC_H_

#include "rime_jni.h"

jstring get_opencc_version(JNIEnv *env, jobject thiz);
jstring opencc_convert(JNIEnv *env, jobject thiz, jstring line, jstring name);

#endif  // OPENCC_H_
