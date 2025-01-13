// SPDX-FileCopyrightText: 2015 - 2024 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

#include <rime/key_event.h>
#include <rime/key_table.h>

#include "jni-utils.h"

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_RimeKeyEvent_parse(JNIEnv *env, jclass clazz,
                                              jstring repr) {
  rime::KeyEvent ke;
  ke.Parse(*CString(env, repr));
  return env->NewObject(GlobalRef->KeyEvent, GlobalRef->KeyEventInit,
                        ke.keycode(), ke.modifier(), *JString(env, ke.repr()));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_RimeKeyEvent_getModifierByName(JNIEnv *env,
                                                          jclass /* thiz */,
                                                          jstring name) {
  return RimeGetModifierByName(CString(env, name));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_RimeKeyEvent_getKeycodeByName(JNIEnv *env,
                                                         jclass /* thiz */,
                                                         jstring name) {
  return RimeGetKeycodeByName(CString(env, name));
}
