// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <rime_api.h>

#include <string>
#include <vector>

#include "jni-utils.h"
#include "objconv.h"

#define MAX_BUFFER_LENGTH 2048

extern void rime_require_module_lua();
extern void rime_require_module_charcode();
extern void rime_require_module_octagram();
extern void rime_require_module_predict();
// librime is compiled as a static library, we have to link modules explicitly
static void declare_librime_module_dependencies() {
  rime_require_module_lua();
  rime_require_module_charcode();
  rime_require_module_octagram();
  rime_require_module_predict();
}

class Rime {
 public:
  Rime() : rime(rime_get_api()) {}
  Rime(Rime const &) = delete;
  void operator=(Rime const &) = delete;

  static Rime &Instance() {
    static Rime instance;
    return instance;
  }

  bool isRunning() { return session != 0; }

  void startup(bool fullCheck,
               const RimeNotificationHandler &notificationHandler) {
    if (!rime) return;
    const char *userDir = getenv("RIME_USER_DATA_DIR");
    const char *sharedDir = getenv("RIME_SHARED_DATA_DIR");

    RIME_STRUCT(RimeTraits, trime_traits)
    trime_traits.shared_data_dir = sharedDir;
    trime_traits.user_data_dir = userDir;
    trime_traits.log_dir = "";  // set empty log_dir to log to logcat only
    trime_traits.app_name = "rime.trime";
    trime_traits.distribution_name = "Rime";
    trime_traits.distribution_code_name = "trime";
    trime_traits.distribution_version = TRIME_VERSION;

    if (firstRun) {
      rime->setup(&trime_traits);
      firstRun = false;
    }
    rime->initialize(&trime_traits);
    rime->set_notification_handler(notificationHandler, GlobalRef->jvm);
    if (rime->start_maintenance(fullCheck) && rime->is_maintenance_mode()) {
      rime->join_maintenance_thread();
    }

    session = rime->create_session();
    if (!session) {
      return;
    }
  }

  bool processKey(int keycode, int mask) {
    return rime->process_key(session, keycode, mask);
  }

  bool simulateKeySequence(const std::string &sequence) {
    return rime->simulate_key_sequence(session, sequence.data());
  }

  bool commitComposition() { return rime->commit_composition(session); }

  void clearComposition() { rime->clear_composition(session); }

  void setOption(const std::string &key, bool value) {
    rime->set_option(session, key.c_str(), value);
  }

  bool getOption(const std::string &key) {
    return rime->get_option(session, key.c_str());
  }

  std::string currentSchemaId() {
    char result[MAX_BUFFER_LENGTH];
    return rime->get_current_schema(session, result, MAX_BUFFER_LENGTH) ? result
                                                                        : "";
  }

  bool selectSchema(const std::string &schemaId) {
    return rime->select_schema(session, schemaId.c_str());
  }

  std::string rawInput() { return rime->get_input(session); }

  size_t caretPosition() { return rime->get_caret_pos(session); }

  void setCaretPosition(size_t caretPos) {
    rime->set_caret_pos(session, caretPos);
  }

  bool selectCandidateOnCurrentPage(size_t index) {
    return rime->select_candidate_on_current_page(session, index);
  }

  bool deleteCandidateOnCurrentPage(size_t index) {
    return rime->delete_candidate_on_current_page(session, index);
  }

  bool selectCandidate(size_t index) {
    return rime->select_candidate(session, index);
  }

  bool forgetCandidate(size_t index) {
    return rime->delete_candidate(session, index);
  }

  std::string stateLabel(const std::string &optionName, bool state) {
    return rime->get_state_label(session, optionName.c_str(), state);
  }

  using CandidateItem = std::pair<std::string, std::string>;
  using CandidateList = std::vector<CandidateItem>;

  CandidateList getCandidates(int startIndex, int limit) {
    CandidateList result;
    result.reserve(limit);
    RimeCandidateListIterator iter{nullptr};
    if (rime->candidate_list_from_index(session, &iter, startIndex)) {
      int count = 0;
      while (rime->candidate_list_next(&iter)) {
        if (count >= limit) break;
        std::string text(iter.candidate.text);
        std::string comment;
        if (iter.candidate.comment) {
          comment = iter.candidate.comment;
        }
        auto item = std::make_pair(text, comment);
        result.emplace_back(std::move(item));
        ++count;
      }
      rime->candidate_list_end(&iter);
    }
    return std::move(result);
  }

  void exit() {
    rime->destroy_session(session);
    session = 0;
    rime->finalize();
  }

  bool sync() { return rime->sync_user_data(); }

  RimeSessionId sessionId() const { return session; }

 private:
  RimeApi *rime;
  RimeSessionId session = 0;

  bool firstRun = true;
};

// check rime status
static inline bool is_rime_running() { return Rime::Instance().isRunning(); }

GlobalRefSingleton *GlobalRef;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
  GlobalRef = new GlobalRefSingleton(jvm);
  declare_librime_module_dependencies();
  return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL Java_com_osfans_trime_core_Rime_startupRime(
    JNIEnv *env, jclass clazz, jstring shared_dir, jstring user_dir,
    jboolean full_check) {
  if (is_rime_running()) {
    return;
  }

  // for rime shared data dir
  setenv("RIME_SHARED_DATA_DIR", CString(env, shared_dir), 1);
  // for rime user data dir
  setenv("RIME_USER_DATA_DIR", CString(env, user_dir), 1);

  auto notificationHandler = [](void *context_object, RimeSessionId session_id,
                                const char *message_type,
                                const char *message_value) {
    auto env = GlobalRef->AttachEnv();
    env->CallStaticVoidMethod(
        GlobalRef->Rime, GlobalRef->HandleRimeNotification,
        *JString(env, message_type), *JString(env, message_value));
  };

  Rime::Instance().startup(full_check, notificationHandler);
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_exitRime(JNIEnv *env, jclass /* thiz */) {
  if (!is_rime_running()) {
    return;
  }
  Rime::Instance().exit();
}

// deployment
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeSchemaFile(JNIEnv *env,
                                                     jclass /* thiz */,
                                                     jstring schema_file) {
  return rime_get_api()->deploy_schema(CString(env, schema_file));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeConfigFile(JNIEnv *env,
                                                     jclass /* thiz */,
                                                     jstring file_name,
                                                     jstring version_key) {
  return rime_get_api()->deploy_config_file(CString(env, file_name),
                                            CString(env, version_key));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_syncRimeUserData(JNIEnv *env,
                                                 jclass /* thiz */) {
  return Rime::Instance().sync();
}

// input
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_processRimeKey(JNIEnv *env, jclass /* thiz */,
                                               jint keycode, jint mask) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().processKey(keycode, mask);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_commitRimeComposition(JNIEnv *env,
                                                      jclass /* thiz */) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().commitComposition();
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_clearRimeComposition(JNIEnv *env,
                                                     jclass /* thiz */) {
  if (!is_rime_running()) {
    return;
  }
  Rime::Instance().clearComposition();
}

// output
extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeCommit(JNIEnv *env, jclass /* thiz */) {
  if (!is_rime_running()) {
    return nullptr;
  }
  RIME_STRUCT(RimeCommit, commit)
  auto rime = rime_get_api();
  jobject obj = nullptr;
  if (rime->get_commit(Rime::Instance().sessionId(), &commit)) {
    obj = rimeCommitToJObject(env, commit);
    rime->free_commit(&commit);
  }
  return obj;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeContext(JNIEnv *env, jclass /* thiz */) {
  if (!is_rime_running()) {
    return nullptr;
  }
  RIME_STRUCT(RimeContext, context)
  auto rime = rime_get_api();
  auto session = Rime::Instance().sessionId();
  jobject obj = nullptr;
  if (rime->get_context(session, &context)) {
    std::string_view input(rime->get_input(session));
    int caretPos = static_cast<int>(rime->get_caret_pos(session));
    obj = rimeContextToJObject(env, context, input, caretPos);
    rime->free_context(&context);
  }
  return obj;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeStatus(JNIEnv *env, jclass /* thiz */) {
  if (!is_rime_running()) {
    return nullptr;
  }
  RIME_STRUCT(RimeStatus, status)
  auto rime = rime_get_api();
  jobject obj = nullptr;
  if (rime->get_status(Rime::Instance().sessionId(), &status)) {
    obj = rimeStatusToJObject(env, status);
    rime->free_status(&status);
  }
  return obj;
}

static bool is_save_option(const char *p) {
  auto rime = rime_get_api();
  bool is_save = false;
  std::string option_name(p);
  if (option_name.empty()) return is_save;
  RimeConfig config = {nullptr};
  bool b = rime->config_open("default", &config);
  if (!b) return is_save;
  const char *key = "switcher/save_options";
  RimeConfigIterator iter = {nullptr};
  rime->config_begin_list(&iter, &config, key);
  while (rime->config_next(&iter)) {
    std::string item(rime->config_get_cstring(&config, iter.path));
    if (option_name == item) {
      is_save = true;
      break;
    }
  }
  rime->config_end(&iter);
  rime->config_close(&config);
  return is_save;
}

// runtime options
extern "C" JNIEXPORT void JNICALL Java_com_osfans_trime_core_Rime_setRimeOption(
    JNIEnv *env, jclass /* thiz */, jstring option, jboolean value) {
  if (!is_rime_running()) {
    return;
  }
  auto rime = rime_get_api();
  RimeConfig user = {nullptr};
  auto opt = CString(env, option);
  bool b;
  if (is_save_option(opt)) {
    if (rime->user_config_open("user", &user)) {
      std::string key = "var/option/" + std::string(opt);
      rime->config_set_bool(&user, key.c_str(), value);
      rime->config_close(&user);
    }
  }
  Rime::Instance().setOption(opt, value);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_getRimeOption(JNIEnv *env, jclass /* thiz */,
                                              jstring option) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().getOption(CString(env, option));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getRimeSchemaList(JNIEnv *env,
                                                  jclass /* thiz */) {
  auto rime = rime_get_api();
  RimeSchemaList list = {0};
  rime->get_schema_list(&list);
  auto array = rimeSchemaListToJObjectArray(env, list);
  rime->free_schema_list(&list);
  return array;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getCurrentRimeSchema(JNIEnv *env,
                                                     jclass /* thiz */) {
  if (!is_rime_running()) {
    return env->NewStringUTF("");
  }
  return env->NewStringUTF(Rime::Instance().currentSchemaId().c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeSchema(JNIEnv *env, jclass /* thiz */,
                                                 jstring schema_id) {
  auto rime = rime_get_api();
  RimeConfig user = {nullptr};
  auto schema = CString(env, schema_id);
  if (rime->user_config_open("user", &user)) {
    rime->config_set_string(&user, "var/previously_selected_schema", schema);
    std::string key = "var/schema_access_time/" + std::string(schema);
    rime->config_set_int(&user, key.c_str(), time(nullptr));
    rime->config_close(&user);
  }
  return Rime::Instance().selectSchema(schema);
}

// testing
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_simulateRimeKeySequence(JNIEnv *env,
                                                        jclass /* thiz */,
                                                        jstring key_sequence) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().simulateKeySequence(CString(env, key_sequence));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeRawInput(JNIEnv *env,
                                                jclass /* thiz */) {
  if (!is_rime_running()) {
    return env->NewStringUTF("");
  }
  return env->NewStringUTF(Rime::Instance().rawInput().data());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeCaretPos(JNIEnv *env,
                                                jclass /* thiz */) {
  if (!is_rime_running()) {
    return -1;
  }
  return static_cast<jint>(Rime::Instance().caretPosition());
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setRimeCaretPos(JNIEnv *env, jclass /* thiz */,
                                                jint caret_pos) {
  if (!is_rime_running()) {
    return;
  }
  Rime::Instance().setCaretPosition(caret_pos);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeCandidateOnCurrentPage(
    JNIEnv *env, jclass /* thiz */, jint index) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().selectCandidateOnCurrentPage(index);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deleteRimeCandidateOnCurrentPage(
    JNIEnv *env, jclass /* thiz */, jint index) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().deleteCandidateOnCurrentPage(index);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeCandidate(JNIEnv *env,
                                                    jclass /* thiz */,
                                                    jint index) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().selectCandidate(index);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_forgetRimeCandidate(JNIEnv *env,
                                                    jclass /* thiz */,
                                                    jint index) {
  if (!is_rime_running()) {
    return false;
  }
  return Rime::Instance().forgetCandidate(index);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getLibrimeVersion(JNIEnv *env,
                                                  jclass /* thiz */) {
  return env->NewStringUTF(LIBRIME_VERSION);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeConfigMap(JNIEnv *env, jclass clazz,
                                                 jstring config_id,
                                                 jstring key) {
  auto rime = rime_get_api();
  RimeConfig config = {nullptr};
  jobject value = nullptr;
  if (rime->config_open(CString(env, config_id), &config)) {
    value = rimeConfigMapToJObject(env, &config, CString(env, key));
    rime->config_close(&config);
  }
  return value;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_runRimeTask(JNIEnv *env, jclass /* thiz */,
                                            jstring task_name) {
  auto rime = rime_get_api();
  const char *s = env->GetStringUTFChars(task_name, nullptr);
  RimeConfig config = {nullptr};
  Bool b = rime->run_task(s);
  env->ReleaseStringUTFChars(task_name, s);
  return b;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeSharedDataDir(JNIEnv *env,
                                                     jclass /* thiz */) {
  return env->NewStringUTF(rime_get_api()->get_shared_data_dir());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeUserDataDir(JNIEnv *env,
                                                   jclass /* thiz */) {
  return env->NewStringUTF(rime_get_api()->get_user_data_dir());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeSyncDir(JNIEnv *env, jclass /* thiz */) {
  return env->NewStringUTF(rime_get_api()->get_sync_dir());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeUserId(JNIEnv *env, jclass /* thiz */) {
  return env->NewStringUTF(rime_get_api()->get_user_id());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeStateLabel(JNIEnv *env,
                                                  jclass /* thiz */,
                                                  jstring option_name,
                                                  jboolean state) {
  if (!is_rime_running()) {
    return nullptr;
  }
  return env->NewStringUTF(
      Rime::Instance().stateLabel(CString(env, option_name), state).c_str());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getRimeCandidates(JNIEnv *env, jclass clazz,
                                                  jint start_index,
                                                  jint limit) {
  if (!is_rime_running()) {
    return nullptr;
  }
  auto candidates = Rime::Instance().getCandidates(start_index, limit);
  int size = static_cast<int>(candidates.size());
  jobjectArray array =
      env->NewObjectArray(size, GlobalRef->CandidateItem, nullptr);
  for (int i = 0; i < size; i++) {
    auto &candidate = candidates[i];
    auto item = JRef<>(env, env->NewObject(GlobalRef->CandidateItem,
                                           GlobalRef->CandidateItemInit,
                                           *JString(env, candidate.second),
                                           *JString(env, candidate.first)));
    env->SetObjectArrayElement(array, i, item);
  }
  return array;
}
