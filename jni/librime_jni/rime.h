#ifndef RIME_H_
#define RIME_H_

#include "rime_jni.h"

#define BUFSIZE 256

void set_notification_handler(JNIEnv *env, jobject thiz);
void check(JNIEnv *env, jobject thiz, jboolean full_check);
void initialize(JNIEnv *env, jobject thiz, jobject jtraits);
void finalize(JNIEnv *env, jobject thiz);
jboolean deploy_schema(JNIEnv *env, jobject thiz, jstring schema_file);
jboolean deploy_config_file(JNIEnv *env, jobject thiz, jstring file_name, jstring version_key);
jboolean sync_user_data(JNIEnv *env, jobject thiz);
jint create_session(JNIEnv *env, jobject thiz);
jboolean find_session(JNIEnv *env, jobject thiz, jint session_id);
jboolean destroy_session(JNIEnv *env, jobject thiz, jint session_id);
void cleanup_stale_sessions(JNIEnv *env, jobject thiz);
void cleanup_all_sessions(JNIEnv *env, jobject thiz);
jboolean process_key(JNIEnv *env, jobject thiz, jint session_id, jint keycode, jint mask);
jboolean commit_composition(JNIEnv *env, jobject thiz, jint session_id);
void clear_composition(JNIEnv *env, jobject thiz, jint session_id);
jboolean get_commit(JNIEnv *env, jobject thiz, jint session_id, jobject jcommit);
jboolean get_context(JNIEnv *env, jobject thiz, jint session_id, jobject jcontext);
jboolean get_status(JNIEnv *env, jobject thiz, jint session_id, jobject jstatus);
void set_option(JNIEnv *env, jobject thiz, jint session_id, jstring option, jboolean value);
jboolean get_option(JNIEnv *env, jobject thiz, jint session_id, jstring option);
void set_property(JNIEnv *env, jobject thiz, jint session_id, jstring prop, jstring value);
jstring get_property(JNIEnv *env, jobject thiz, jint session_id, jstring prop);
jobject get_schema_list(JNIEnv *env, jobject thiz);
jstring get_current_schema(JNIEnv *env, jobject thiz, jint session_id);
jboolean select_schema(JNIEnv *env, jobject thiz, jint session_id, jstring schema_id);
jobject config_get_bool(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_bool(JNIEnv *env, jobject thiz, jstring name, jstring key, jboolean value);
jobject config_get_int(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_int(JNIEnv *env, jobject thiz, jstring name, jstring key, jint value);
jobject config_get_double(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_double(JNIEnv *env, jobject thiz, jstring name, jstring key, jdouble value);
jstring config_get_string(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_string(JNIEnv *env, jobject thiz, jstring name, jstring key, jstring value);
jint config_list_size(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean simulate_key_sequence(JNIEnv *env, jobject thiz, jint session_id, jstring key_sequence);
jstring get_input(JNIEnv *env, jobject thiz, jint session_id);
jint get_caret_pos(JNIEnv *env, jobject thiz, jint session_id);
void set_caret_pos(JNIEnv *env, jobject thiz, jint session_id, jint caret_pos);
jboolean select_candidate(JNIEnv *env, jobject thiz, jint session_id, jint index);
jstring get_version(JNIEnv *env, jobject thiz);
jint get_modifier_by_name(JNIEnv *env, jobject thiz, jstring name);
jint get_keycode_by_name(JNIEnv *env, jobject thiz, jstring name);
jboolean get_schema(JNIEnv *env, jobject thiz, jstring name, jobject jschema);
jobject config_get_list(JNIEnv *env, jobject thiz, jstring name, jstring key);
jobject config_get_map(JNIEnv *env, jobject thiz, jstring name, jstring key);
jobject config_get_value(JNIEnv *env, jobject thiz, jstring name, jstring key);

#endif  // RIME_H_
