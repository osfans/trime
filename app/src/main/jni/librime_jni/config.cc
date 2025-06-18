#include <rime_api.h>

#include "jni-utils.h"

#define DECLARE_OPEN(JName, CAPI)                                \
  extern "C" JNIEXPORT jlong JNICALL                             \
      Java_com_osfans_trime_core_RimeConfig_openRime##JName(     \
          JNIEnv *env, jclass clazz, jstring config_id) {        \
    auto *config = new RimeConfig{nullptr};                      \
    if (rime_get_api()->CAPI(CString(env, config_id), config)) { \
      return (jlong)config;                                      \
    }                                                            \
    return 0;                                                    \
  }

DECLARE_OPEN(Schema, schema_open)
DECLARE_OPEN(Config, config_open)
DECLARE_OPEN(UserConfig, user_config_open)

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_RimeConfig_getRimeConfigString(JNIEnv *env,
                                                          jclass clazz,
                                                          jlong ptr,
                                                          jstring key) {
  auto *config = (RimeConfig *)ptr;
  if (auto value =
          rime_get_api()->config_get_cstring(config, CString(env, key))) {
    return env->NewStringUTF(value);
  }
  return nullptr;
}

#define DECLARE_GET(JName, CType, CAPI, Class, Method)             \
  extern "C" JNIEXPORT jobject JNICALL                             \
      Java_com_osfans_trime_core_RimeConfig_getRimeConfig##JName(  \
          JNIEnv *env, jclass clazz, jlong ptr, jstring key) {     \
    auto *config = (RimeConfig *)ptr;                              \
    CType value;                                                   \
    if (rime_get_api()->CAPI(config, CString(env, key), &value)) { \
      return env->NewObject(Class, Method, value);                 \
    }                                                              \
    return nullptr;                                                \
  }

DECLARE_GET(Bool, Bool, config_get_bool, GlobalRef->Boolean,
            GlobalRef->BooleanInit)
DECLARE_GET(Double, double, config_get_double, GlobalRef->Double,
            GlobalRef->DoubleInit)
DECLARE_GET(Int, int, config_get_int, GlobalRef->Integer,
            GlobalRef->IntegerInit)

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_osfans_trime_core_RimeConfig_getRimeConfigList(JNIEnv *env,
                                                        jclass clazz, jlong ptr,
                                                        jstring key) {
  auto *config = (RimeConfig *)ptr;
  auto *iter = new RimeConfigIterator;
  auto *api = rime_get_api();
  auto cKey = CString(env, key);
  if (api->config_begin_list(iter, config, cKey)) {
    auto size = static_cast<int>(api->config_list_size(config, cKey));
    jlong buf[size];
    int i = 0;
    while (api->config_next(iter)) {
      auto *subConfig = new RimeConfig{nullptr};
      if (api->config_get_item(config, iter->path, subConfig)) {
        buf[i] = (jlong)subConfig;
      }
      ++i;
    }
    api->config_end(iter);
    auto array = env->NewLongArray(size);
    env->SetLongArrayRegion(array, 0, size, buf);
    return array;
  }
  return nullptr;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_RimeConfig_getRimeConfigMap(JNIEnv *env,
                                                       jclass clazz, jlong ptr,
                                                       jstring key) {
  auto *config = (RimeConfig *)ptr;
  auto *iter = new RimeConfigIterator;
  auto *api = rime_get_api();
  auto cKey = CString(env, key);
  if (api->config_begin_map(iter, config, cKey)) {
    auto map =
        env->NewObject(GlobalRef->HashMap, GlobalRef->HashMapInit, nullptr);
    while (api->config_next(iter)) {
      auto *subConfig = new RimeConfig{nullptr};
      if (api->config_get_item(config, iter->path, subConfig)) {
        env->CallObjectMethod(
            map, GlobalRef->HashMapPut, *JString(env, iter->key),
            *JRef<>(env, env->NewObject(GlobalRef->Long, GlobalRef->LongInit,
                                        (jlong)subConfig)));
      }
    }
    api->config_end(iter);
    return map;
  }
  return nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_osfans_trime_core_RimeConfig_getRimeConfigItem(JNIEnv *env,
                                                        jclass clazz, jlong ptr,
                                                        jstring key) {
  auto *config = (RimeConfig *)ptr;
  auto *subConfig = new RimeConfig{nullptr};
  if (rime_get_api()->config_get_item(config, CString(env, key), subConfig)) {
    return (jlong)subConfig;
  }
  return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_RimeConfig_closeRimeConfig(JNIEnv *env, jclass clazz,
                                                      jlong ptr) {
  auto *config = (RimeConfig *)ptr;
  rime_get_api()->config_close(config);
}
