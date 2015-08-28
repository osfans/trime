#ifndef OPENCC_H_
#define OPENCC_H_

#include "rime_jni.h"

jstring get_opencc_version(JNIEnv *env, jobject thiz);
jstring opencc_convert(JNIEnv *env, jobject thiz, jstring line, jstring name);
void opencc_convert_dictionary(JNIEnv *env, jobject thiz, jstring jinputFileName,
    jstring joutputFileName, jstring jformatFrom, jstring jformatTo);

#endif  // OPENCC_H_
