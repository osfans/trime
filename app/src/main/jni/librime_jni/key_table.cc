#include <rime/key_table.h>

#include "jni-utils.h"

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeModifierByName(JNIEnv *env,
                                                      jclass /* thiz */,
                                                      jstring name) {
  return RimeGetModifierByName(CString(env, name));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeKeycodeByName(JNIEnv *env,
                                                     jclass /* thiz */,
                                                     jstring name) {
  return RimeGetKeycodeByName(CString(env, name));
}
