// SPDX-FileCopyrightText: 2015 - 2024 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

#include <rime/key_event.h>
#include <rime/key_table.h>

#include "jni-utils.h"

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_RimeKeyEvent_parse(JNIEnv* env, jclass clazz,
                                              jstring repr) {
  CString text(env, repr);
  if (env->ExceptionCheck()) return nullptr;
  rime::KeyEvent ke;
  ke.Parse(*text);
  JString description(env, ke.repr());
  if (!*description) return nullptr;
  return env->NewObject(GlobalRef->KeyEvent, GlobalRef->KeyEventInit,
                        ke.keycode(), ke.modifier(), *description);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_RimeKeyEvent_getModifierByName(JNIEnv* env,
                                                          jclass /* thiz */,
                                                          jstring name) {
  CString text(env, name);
  if (env->ExceptionCheck()) return 0;
  return RimeGetModifierByName(text);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_RimeKeyEvent_getKeycodeByName(JNIEnv* env,
                                                         jclass /* thiz */,
                                                         jstring name) {
  CString text(env, name);
  if (env->ExceptionCheck()) return 0;
  return RimeGetKeycodeByName(text);
}
