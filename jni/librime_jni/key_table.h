#ifndef KEY_TABLE_H_
#define KEY_TABLE_H_

#include "rime_jni.h"

jint get_modifier_by_name(JNIEnv *env, jobject thiz, jstring name);
jint get_keycode_by_name(JNIEnv *env, jobject thiz, jstring name);

#endif  // KEY_TABLE_H_
