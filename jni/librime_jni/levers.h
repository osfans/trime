#ifndef LEVERS_H_
#define LEVERS_H_

#include "rime_jni.h"
#include <rime_levers_api.h>

jboolean customize_bool(JNIEnv *env, jobject thiz, jstring name, jstring key, jboolean value);
jboolean customize_int(JNIEnv *env, jobject thiz, jstring name, jstring key, jint value);
jboolean customize_double(JNIEnv *env, jobject thiz, jstring name, jstring key, jdouble value);
jboolean customize_string(JNIEnv *env, jobject thiz, jstring name, jstring key, jstring value);

#endif  // LEVERS_H_
