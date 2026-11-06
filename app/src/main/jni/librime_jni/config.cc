#include <rime_api.h>

#include "jni-utils.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_osfans_trime_core_RimeConfig_openRimeConfig(JNIEnv* env,
                                                     jclass /* thiz */,
                                                     jstring config_id) {
  auto id = CString(env, config_id);
  if (env->ExceptionCheck()) return 0;
  auto api = rime_get_api();
  auto config = new RimeConfig;
  if (!api->config_open(id, config)) {
    delete config;
    return 0;
  }
  return reinterpret_cast<jlong>(config);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_osfans_trime_core_RimeConfig_openRimeUserConfig(JNIEnv* env,
                                                         jclass /* thiz */,
                                                         jstring config_id) {
  auto id = CString(env, config_id);
  if (env->ExceptionCheck()) return 0;
  auto api = rime_get_api();
  auto config = new RimeConfig;
  if (!api->user_config_open(id, config)) {
    delete config;
    return 0;
  }
  return reinterpret_cast<jlong>(config);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_osfans_trime_core_RimeConfig_openRimeSchema(JNIEnv* env,
                                                     jclass /* thiz */,
                                                     jstring schema_id) {
  auto id = CString(env, schema_id);
  if (env->ExceptionCheck()) return 0;
  auto api = rime_get_api();
  auto config = new RimeConfig;
  if (!api->schema_open(id, config)) {
    delete config;
    return 0;
  }
  return reinterpret_cast<jlong>(config);
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_RimeConfig_closeRimeConfig(JNIEnv* env,
                                                      jclass /* thiz */,
                                                      jlong peer) {
  auto api = rime_get_api();
  auto config = reinterpret_cast<RimeConfig*>(peer);
  api->config_close(config);
  delete config;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_RimeConfig_getRimeConfigInt(JNIEnv* env,
                                                       jclass /* thiz */,
                                                       jlong peer,
                                                       jstring key) {
  auto cKey = CString(env, key);
  if (env->ExceptionCheck()) return nullptr;
  auto api = rime_get_api();
  int value;
  if (!api->config_get_int(reinterpret_cast<RimeConfig*>(peer), cKey, &value)) {
    return nullptr;
  }
  return env->NewObject(GlobalRef->Integer, GlobalRef->IntegerInit, value);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_RimeConfig_getRimeConfigString(JNIEnv* env,
                                                          jclass /* thiz */,
                                                          jlong peer,
                                                          jstring key) {
  auto cKey = CString(env, key);
  if (env->ExceptionCheck()) return nullptr;
  auto api = rime_get_api();
  const char* value =
      api->config_get_cstring(reinterpret_cast<RimeConfig*>(peer), cKey);
  if (!value) return nullptr;
  return makeJavaString(env, value);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_RimeConfig_getRimeConfigListItemPath(
    JNIEnv* env, jclass /* thiz */, jlong peer, jstring key) {
  auto cKey = CString(env, key);
  if (env->ExceptionCheck()) return nullptr;
  auto api = rime_get_api();
  auto config = reinterpret_cast<RimeConfig*>(peer);
  auto size = static_cast<int>(api->config_list_size(config, cKey));
  RimeConfigIterator iter;
  int i = 0;
  auto array = env->NewObjectArray(size, GlobalRef->String, nullptr);
  if (!api->config_begin_list(&iter, config, cKey)) return array;
  while (api->config_next(&iter)) {
    env->SetObjectArrayElement(array, i++, JString(env, iter.path));
  }
  api->config_end(&iter);
  return array;
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_RimeConfig_setRimeConfigBool(JNIEnv* env,
                                                        jclass /* thiz */,
                                                        jlong peer, jstring key,
                                                        jboolean value) {
  auto cKey = CString(env, key);
  if (env->ExceptionCheck()) return;
  auto api = rime_get_api();
  api->config_set_bool(reinterpret_cast<RimeConfig*>(peer), cKey, value);
}
