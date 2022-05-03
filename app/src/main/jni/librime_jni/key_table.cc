#include "key_table.h"
#include <rime/key_table.h>

extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_get_1modifier_1by_1name(JNIEnv *env, jclass thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetModifierByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_get_1keycode_1by_1name(JNIEnv *env, jclass thiz, jstring name) {
  const char* s = name == NULL ? NULL : env->GetStringUTFChars(name, NULL);
  int keycode = RimeGetKeycodeByName(s);
  env->ReleaseStringUTFChars(name, s);
  return keycode;
}
