#include "rime_jni.h"

// customize settings

static RimeLeversApi* get_levers() {
  return (RimeLeversApi*) (RimeFindModule("levers")->get_api());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_customize_1bool(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jboolean value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, nullptr);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, nullptr);
    if (levers->customize_bool(settings, s, value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
  }
  levers->custom_settings_destroy(settings);
  return b;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_customize_1int(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jint value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, nullptr);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, nullptr);
    if (levers->customize_int(settings, s, value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
  }
  levers->custom_settings_destroy(settings);
  return b;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_customize_1double(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jdouble value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, nullptr);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, nullptr);
    if (levers->customize_double(settings, s, value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
  }
  levers->custom_settings_destroy(settings);
  return b;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_customize_1string(JNIEnv *env, jclass /* thiz */, jstring name, jstring key, jstring value) {
  RimeLeversApi* levers = get_levers();
  const char* s = env->GetStringUTFChars(name, nullptr);
  RimeCustomSettings* settings = levers->custom_settings_init(s, TAG);
  Bool b = levers->load_settings(settings);
  env->ReleaseStringUTFChars(name, s);
  if (b) {
    s = env->GetStringUTFChars(key, nullptr);
    const char* c_value = env->GetStringUTFChars(value, nullptr);
    if (levers->customize_string(settings, s, c_value)) levers->save_settings(settings);
    env->ReleaseStringUTFChars(key, s);
    env->ReleaseStringUTFChars(value, c_value);
  }
  levers->custom_settings_destroy(settings);
  return b;
}

jobject rimeSchemaListToJObject(JNIEnv *env, RimeSchemaList* list) {
  if (list == nullptr) return nullptr;
  jclass ArrayList = env->FindClass("java/util/ArrayList");
  jmethodID ArrayListInit = env->GetMethodID(ArrayList, "<init>", "()V");
  jmethodID add = env->GetMethodID(ArrayList, "add", "(Ljava/lang/Object;)Z");
  jobject schema_list = env->NewObject(ArrayList, ArrayListInit);
  
  jclass HashMap = env->FindClass("java/util/HashMap");
  jmethodID HashMapInit = env->GetMethodID(HashMap, "<init>", "()V");
  jmethodID HashMapPut = env->GetMethodID(HashMap, "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  size_t n = list->size;
  if (n > 0) {
    for (int i = 0; i < n; i++) {
      jobject schema_item = env->NewObject(HashMap, HashMapInit);
      RimeSchemaListItem& item(list->list[i]);
      env->CallObjectMethod(schema_item, HashMapPut,
                            *JString(env, "schema_id"),
                            *JString(env, item.schema_id));
      if (item.name) {
        env->CallObjectMethod(schema_item, HashMapPut,
                              *JString(env, "name"),
                              *JString(env, item.name));
      }
      /**
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
      } */

      env->CallBooleanMethod(schema_list, add, schema_item);
      env->DeleteLocalRef(schema_item);
    }
  }
  env->DeleteLocalRef(HashMap);
  env->DeleteLocalRef(ArrayList);
  return schema_list;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_get_1available_1schema_1list(JNIEnv *env, jclass /* thiz */) {
  RimeLeversApi* api_ = get_levers();
  RimeSwitcherSettings* settings_ = api_->switcher_settings_init();
  RimeSchemaList list = {0};
  jobject jobj = nullptr;
  Bool b = api_->load_settings((RimeCustomSettings*)settings_);
  if (b) {
    api_->get_available_schema_list(settings_, &list);
    jobj = rimeSchemaListToJObject(env, &list);
    api_->schema_list_destroy(&list);
    api_->custom_settings_destroy((RimeCustomSettings*)settings_);
  }
  return jobj;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_get_1selected_1schema_1list(JNIEnv *env, jclass /* thiz */) {
  RimeLeversApi* api_ = get_levers();
  RimeSwitcherSettings* settings_ = api_->switcher_settings_init();
  RimeSchemaList list = {0};
  jobject jobj = nullptr;
  Bool b = api_->load_settings((RimeCustomSettings*)settings_);
  if (b) {
    api_->get_selected_schema_list(settings_, &list);
    jobj = rimeSchemaListToJObject(env, &list);
    api_->schema_list_destroy(&list);
    api_->custom_settings_destroy((RimeCustomSettings*)settings_);
  }
  return jobj;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_select_1schemas(JNIEnv *env, jclass /* thiz */, jobjectArray stringArray) {
  if (stringArray == nullptr) return false;
  int count = env->GetArrayLength(stringArray);
  if (count == 0) return false;
  const char** schema_id_list = new const char*[count];
  for (int i = 0; i < count; i++) {
    auto string = (jstring) env->GetObjectArrayElement(stringArray, i);
    const char *rawString = env->GetStringUTFChars(string, nullptr);
    schema_id_list[i] = rawString;
  }
  RimeLeversApi* api_ = get_levers();
  RimeSwitcherSettings* settings_ = api_->switcher_settings_init();
  auto *custom_settings_ = (RimeCustomSettings *) settings_;
  Bool b = api_->load_settings(custom_settings_);
  if (b) {
    b = api_->select_schemas(settings_, schema_id_list, count);
    api_->save_settings(custom_settings_);
    api_->custom_settings_destroy(custom_settings_);
  }
  for (int i = 0; i < count; i++) {
    auto string = (jstring) env->GetObjectArrayElement(stringArray, i);
    const char *rawString = schema_id_list[i];
    env->ReleaseStringUTFChars(string, rawString);
  }
  delete[] schema_id_list;
  return b;
}
