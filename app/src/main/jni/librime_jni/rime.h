#ifndef RIME_H_
#define RIME_H_

#include "rime_jni.h"

#define BUFSIZE 256

#ifndef CLASSNAME
#define CLASSNAME "com/osfans/trime/Rime"
#endif

#ifndef APP_NAME
#define APP_NAME "Rime-JNI"
#endif

void setup(JNIEnv *env, jobject thiz, jstring shared_data_dir, jstring user_data_dir);
void set_notification_handler(JNIEnv *env, jobject thiz);
// entry and exit
void initialize(JNIEnv *env, jobject thiz, jstring shared_data_dir, jstring user_data_dir);
void finalize(JNIEnv *env, jobject thiz);
jboolean start_maintenance(JNIEnv *env, jobject thiz, jboolean full_check);
jboolean is_maintenance_mode(JNIEnv *env, jobject thiz);
void join_maintenance_thread(JNIEnv *env, jobject thiz);
// deployment
void deployer_initialize(JNIEnv *env, jobject thiz, jstring shared_data_dir, jstring user_data_dir);
jboolean prebuild(JNIEnv *env, jobject thiz);
jboolean deploy(JNIEnv *env, jobject thiz);
jboolean deploy_schema(JNIEnv *env, jobject thiz, jstring schema_file);
jboolean deploy_config_file(JNIEnv *env, jobject thiz, jstring file_name, jstring version_key);
jboolean sync_user_data(JNIEnv *env, jobject thiz);
// session management
jint create_session(JNIEnv *env, jobject thiz);
jboolean find_session(JNIEnv *env, jobject thiz);
jboolean destroy_session(JNIEnv *env, jobject thiz);
void cleanup_stale_sessions(JNIEnv *env, jobject thiz);
void cleanup_all_sessions(JNIEnv *env, jobject thiz);
// input
jboolean process_key(JNIEnv *env, jobject thiz, jint keycode, jint mask);
jboolean commit_composition(JNIEnv *env, jobject thiz);
void clear_composition(JNIEnv *env, jobject thiz);
// output
jboolean get_commit(JNIEnv *env, jobject thiz, jobject jcommit);
jboolean get_context(JNIEnv *env, jobject thiz, jobject jcontext);
jboolean get_status(JNIEnv *env, jobject thiz, jobject jstatus);
// runtime options
void set_option(JNIEnv *env, jobject thiz, jstring option, jboolean value);
jboolean get_option(JNIEnv *env, jobject thiz, jstring option);
void set_property(JNIEnv *env, jobject thiz, jstring prop, jstring value);
jstring get_property(JNIEnv *env, jobject thiz, jstring prop);
jobject get_schema_list(JNIEnv *env, jobject thiz);
jstring get_current_schema(JNIEnv *env, jobject thiz);
jboolean select_schema(JNIEnv *env, jobject thiz, jstring schema_id);
// configuration
jobject config_get_bool(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_bool(JNIEnv *env, jobject thiz, jstring name, jstring key, jboolean value);
jobject config_get_int(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_int(JNIEnv *env, jobject thiz, jstring name, jstring key, jint value);
jobject config_get_double(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_double(JNIEnv *env, jobject thiz, jstring name, jstring key, jdouble value);
jstring config_get_string(JNIEnv *env, jobject thiz, jstring name, jstring key);
jboolean config_set_string(JNIEnv *env, jobject thiz, jstring name, jstring key, jstring value);
jint config_list_size(JNIEnv *env, jobject thiz, jstring name, jstring key);
jobject config_get_list(JNIEnv *env, jobject thiz, jstring name, jstring key);
jobject config_get_map(JNIEnv *env, jobject thiz, jstring name, jstring key);
jobject config_get_value(JNIEnv *env, jobject thiz, jstring name, jstring key);
jobject schema_get_value(JNIEnv *env, jobject thiz, jstring schema_id, jstring key);

jboolean simulate_key_sequence(JNIEnv *env, jobject thiz, jstring key_sequence);
jstring get_input(JNIEnv *env, jobject thiz);
jint get_caret_pos(JNIEnv *env, jobject thiz);
void set_caret_pos(JNIEnv *env, jobject thiz, jint caret_pos);
jboolean select_candidate(JNIEnv *env, jobject thiz, jint index);
jboolean select_candidate_on_current_page(JNIEnv *env, jobject thiz, jint index);
jstring get_version(JNIEnv *env, jobject thiz);
jstring get_librime_version(JNIEnv *env, jobject thiz);
//module
jboolean run_task(JNIEnv *env, jobject thiz, jstring task_name);
jstring get_shared_data_dir(JNIEnv *env, jobject thiz);
jstring get_user_data_dir(JNIEnv *env, jobject thiz);
jstring get_sync_dir(JNIEnv *env, jobject thiz);
jstring get_user_id(JNIEnv *env, jobject thiz);

#endif  // RIME_H_
