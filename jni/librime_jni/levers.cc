#include "levers.h"

// customize settings

static RimeLeversApi* get_levers() {
  return (RimeLeversApi*)(rime_get_api()->find_module("levers")->get_api());
}

jboolean customize_bool(JNIEnv *env, jobject thiz, jstring name, jstring key, jboolean value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    if (levers->customize_bool(settings, s, value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
  }
  levers->custom_settings_destroy(settings);
  return b;
}

jboolean customize_int(JNIEnv *env, jobject thiz, jstring name, jstring key, jint value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    if (levers->customize_int(settings, s, value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
  }
  levers->custom_settings_destroy(settings);
  return b;
}

jboolean customize_double(JNIEnv *env, jobject thiz, jstring name, jstring key, jdouble value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    if (levers->customize_double(settings, s, value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
  }
  levers->custom_settings_destroy(settings);
  return b;
}

jboolean customize_string(JNIEnv *env, jobject thiz, jstring name, jstring key, jstring value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, NULL);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, NULL);
    const char* c_value = env->GetStringUTFChars(value, NULL);
    if (levers->customize_string(settings, s, c_value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
    env->ReleaseStringUTFChars(value, c_value);
  }
  levers->custom_settings_destroy(settings);
  return b;
}
