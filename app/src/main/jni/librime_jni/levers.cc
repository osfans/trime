#include "levers.h"
#include "rime.h"

// customize settings

static RimeLeversApi* get_levers() {
  return (RimeLeversApi*)(RimeFindModule("levers")->get_api());
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

jobject _get_schema_list(JNIEnv *env, RimeSchemaList* list) {
  if (list == NULL) return NULL;
  jclass jc = env->FindClass("java/util/ArrayList");
  if (jc == NULL) return NULL;
  jmethodID init = env->GetMethodID(jc, "<init>", "()V");
  jobject schema_list = env->NewObject(jc, init);
  jmethodID add = env->GetMethodID(jc, "add", "(Ljava/lang/Object;)Z");

  jclass mapClass = env->FindClass("java/util/HashMap");
  if (mapClass == NULL) return NULL;
  jmethodID mapInit = env->GetMethodID(mapClass, "<init>", "()V");
  jmethodID put = env->GetMethodID(mapClass, "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  int n = list->size;
  if (n > 0) {
    RimeLeversApi* api_ = get_levers();
    for (int i = 0; i < n; i++) {
      jobject schema_item = env->NewObject(mapClass, mapInit);
      jstring key;
      jstring value;
      RimeSchemaListItem& item(list->list[i]);
      key = newJstring(env, "schema_id");
      value = newJstring(env, item.schema_id);
      env->CallObjectMethod(schema_item, put, key, value);
      if (item.name) {
        key = newJstring(env, "name");
        value = newJstring(env, item.name);
        env->CallObjectMethod(schema_item, put, key, value);
      }
      if (0 && item.reserved) { //workaround for jni string overflow
        RimeSchemaInfo* info = (RimeSchemaInfo*) item.reserved;
        key = newJstring(env, "version");
        value = newJstring(env, api_->get_schema_version(info));
        env->CallObjectMethod(schema_item, put, key, value);
        key = newJstring(env, "author");
        value = newJstring(env, api_->get_schema_author(info));
        env->CallObjectMethod(schema_item, put, key, value);
        key = newJstring(env, "description");
        value = newJstring(env, api_->get_schema_description(info));
        env->CallObjectMethod(schema_item, put, key, value);
      }
      env->DeleteLocalRef(key);
      env->DeleteLocalRef(value);

      env->CallBooleanMethod(schema_list, add, schema_item);
      env->DeleteLocalRef(schema_item);
    }
  }
  env->DeleteLocalRef(mapClass);
  env->DeleteLocalRef(jc);
  return schema_list;
}

jobject get_available_schema_list(JNIEnv *env, jobject thiz) {
  RimeLeversApi* api_ = get_levers();
  RimeSwitcherSettings* settings_ = api_->switcher_settings_init();
  RimeSchemaList list = {0};
  jobject jobj = NULL;
  Bool b = api_->load_settings((RimeCustomSettings*)settings_);
  if (b) {
    api_->get_available_schema_list(settings_, &list);
    jobj = _get_schema_list(env, &list);
    api_->schema_list_destroy(&list);
    api_->custom_settings_destroy((RimeCustomSettings*)settings_);
  }
  return jobj;
}

jobject get_selected_schema_list(JNIEnv *env, jobject thiz) {
  RimeLeversApi* api_ = get_levers();
  RimeSwitcherSettings* settings_ = api_->switcher_settings_init();
  RimeSchemaList list = {0};
  jobject jobj = NULL;
  Bool b = api_->load_settings((RimeCustomSettings*)settings_);
  if (b) {
    api_->get_selected_schema_list(settings_, &list);
    jobj = _get_schema_list(env, &list);
    api_->schema_list_destroy(&list);
    api_->custom_settings_destroy((RimeCustomSettings*)settings_);
  }
  return jobj;
}

jboolean select_schemas(JNIEnv *env, jobject thiz, jobjectArray stringArray) {
  if (stringArray == NULL) return false;
  int count = env->GetArrayLength(stringArray);
  if (count == 0) return false;
  const char** schema_id_list = new const char*[count];
  for (int i = 0; i < count; i++) {
    jstring string = (jstring) env->GetObjectArrayElement(stringArray, i);
    const char *rawString = env->GetStringUTFChars(string, NULL);
    schema_id_list[i] = rawString;
  }
  RimeLeversApi* api_ = get_levers();
  RimeSwitcherSettings* settings_ = api_->switcher_settings_init();
  RimeCustomSettings * custom_settings_ = (RimeCustomSettings *)settings_;
  Bool b = api_->load_settings(custom_settings_);
  if (b) {
    b = api_->select_schemas(settings_, schema_id_list, count);
    api_->save_settings(custom_settings_);
    api_->custom_settings_destroy(custom_settings_);
  }
  for (int i = 0; i < count; i++) {
    jstring string = (jstring) env->GetObjectArrayElement(stringArray, i);
    const char *rawString = schema_id_list[i];
    env->ReleaseStringUTFChars(string, rawString);
  }
  delete[] schema_id_list;
  return b;
}
