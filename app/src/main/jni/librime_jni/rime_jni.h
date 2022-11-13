#ifndef RIME_JNI_H_
#define RIME_JNI_H_

#include <jni.h>
#include <rime_api.h>
#include <rime_levers_api.h>

jobject rimeSchemaListToJObject(JNIEnv *env, RimeSchemaList* list);

#endif  // RIME_JNI_H_
