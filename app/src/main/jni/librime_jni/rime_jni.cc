// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <rime/context.h>
#include <rime/key_event.h>
#include <rime/service.h>
#include <rime_api.h>

#include <algorithm>
#include <exception>
#include <memory>
#include <string>
#include <vector>

#include "frontend.h"
#include "jni-utils.h"
#include "objconv.h"
#include "session.h"
#include "t9.h"

#define MAX_BUFFER_LENGTH 2048

extern void rime_require_module_lua();
extern void rime_require_module_octagram();
extern void rime_require_module_predict();
// librime is compiled as a static library, we have to link modules explicitly
static void declare_librime_module_dependencies() {
  rime_require_module_lua();
  rime_require_module_octagram();
  rime_require_module_predict();
}

class Rime {
 public:
  Rime() : rime(rime_get_api()) {}
  Rime(Rime const&) = delete;
  void operator=(Rime const&) = delete;

  static Rime& Instance() {
    static Rime instance;
    return instance;
  }

  void startup(bool fullCheck,
               const RimeNotificationHandler& notificationHandler) {
    if (!rime) return;
    const char* userDir = getenv("RIME_USER_DATA_DIR");
    const char* sharedDir = getenv("RIME_SHARED_DATA_DIR");
    const char* versionName = getenv("RIME_DISTRIBUTION_VERSION");

    RimeTraits trime_traits{};
    RIME_STRUCT_INIT(RimeTraits, trime_traits);
    trime_traits.shared_data_dir = sharedDir;
    trime_traits.user_data_dir = userDir;
    trime_traits.log_dir = "";  // set empty log_dir to log to logcat only
    trime_traits.app_name = "rime.trime";
    trime_traits.distribution_name = "Trime";
    trime_traits.distribution_code_name = "trime";
    trime_traits.distribution_version = versionName;

    rime->setup(&trime_traits);
    rime->initialize(&trime_traits);
    rime->set_notification_handler(notificationHandler, GlobalRef->jvm);
    rime->start_maintenance(fullCheck);
  }

  bool deploySchema(std::string_view schemaFile) {
    return rime->deploy_schema(schemaFile.data());
  }

  bool deployConfigFile(std::string_view configFile,
                        std::string_view versionKey) {
    return rime->deploy_config_file(configFile.data(), versionKey.data());
  }

  bool processKey(int keycode, int mask) {
    auto current = rime::Service::instance().GetSession(session());
    return t9_.ProcessKey(current.get(), keycode, mask) ||
           rime->process_key(session(), keycode, mask);
  }

  bool simulateKeySequence(const std::string& sequence) {
    const auto state = t9Snapshot();
    const bool has_locked =
        std::any_of(state.segments.begin(), state.segments.end(),
                    [](const auto& span) { return span.locked; });
    if (!has_locked)
      return rime->simulate_key_sequence(session(), sequence.data());
    rime::KeySequence keys;
    if (!keys.Parse(sequence)) return false;
    for (const auto& key : keys) {
      // A T9 lock owns raw/engine position mapping, including macro edits.
      processKey(key.keycode(), key.modifier());
    }
    return true;
  }

  bool commitComposition() { return rime->commit_composition(session()); }

  void clearComposition() {
    t9_.Clear();
    rime->clear_composition(session());
  }

  trime::T9Snapshot t9Snapshot() {
    auto current = rime::Service::instance().GetSession(session());
    return t9_.Snapshot(current.get());
  }

  void setT9AssistOptions(int options) { t9_.SetAssistOptions(options); }

  bool t9Action(int revision, int action, int start, int end,
                const std::string& spelling) {
    auto current = rime::Service::instance().GetSession(session());
    return t9_.Act(current.get(), revision, action, start, end, spelling);
  }

  std::unique_ptr<CommitProto> commit() {
    RimeCommit data{};
    RIME_STRUCT_INIT(RimeCommit, data);
    if (rime->get_commit(session(), &data)) {
      auto p = std::make_unique<CommitProto>(&data);
      // Read the commit before syncing the already-cleared context: the T9
      // state still owns the mapping from private exact spellings to raw code.
      if (p->text) p->text = t9_.CommitText(*p->text);
      rime->free_commit(&data);
      return p;
    }
    return std::make_unique<CommitProto>();
  }

  std::unique_ptr<ContextProto> context(bool includeMenu = true) {
    RimeContext data{};
    RIME_STRUCT_INIT(RimeContext, data);
    auto s = session();
    rime::Preedit preedit;
    auto current = rime::Service::instance().GetSession(s);
    const bool t9Preedit = t9_.GetPreedit(current.get(), &preedit);
    if (rime->get_context(s, &data)) {
      auto input = rime->get_input(s);
      auto caretPos = rime->get_caret_pos(s);
      auto p =
          std::make_unique<ContextProto>(&data, input, caretPos, includeMenu);
      if (t9Preedit) {
        auto begin = preedit.text.data();
        p->composition.length = distance(begin, begin + preedit.text.size());
        p->composition.cursorPos = distance(begin, begin + preedit.caret_pos);
        p->composition.selStart = distance(begin, begin + preedit.sel_start);
        p->composition.selEnd = distance(begin, begin + preedit.sel_end);
        p->composition.preedit = preedit.text;
      }
      rime->free_context(&data);
      return p;
    }
    return std::make_unique<ContextProto>();
  }

  std::unique_ptr<StatusProto> status() {
    RimeStatus data{};
    RIME_STRUCT_INIT(RimeStatus, data);
    if (rime->get_status(session(), &data)) {
      auto p = std::make_unique<StatusProto>(&data);
      rime->free_status(&data);
      return p;
    }
    return std::make_unique<StatusProto>();
  }

  void setOption(std::string_view key, bool value) {
    rime->set_option(session(), key.data(), value);
  }

  bool getOption(std::string_view key) {
    return rime->get_option(session(), key.data());
  }

  std::string currentSchemaId() {
    char result[MAX_BUFFER_LENGTH];
    return rime->get_current_schema(session(), result, MAX_BUFFER_LENGTH)
               ? result
               : "";
  }

  std::vector<SchemaItem> schemaList() {
    std::vector<SchemaItem> result;
    RimeSchemaList list{};
    if (rime->get_schema_list(&list)) {
      result = SchemaItem::fromCList(list);
      rime->free_schema_list(&list);
    }
    return result;
  }

  bool selectSchema(std::string_view schemaId) {
    t9_.Reset();
    return rime->select_schema(session(), schemaId.data());
  }

  std::string rawInput() {
    auto current = rime::Service::instance().GetSession(session());
    return t9_.RawInput(current.get());
  }

  size_t caretPosition() { return rime->get_caret_pos(session()); }

  void setCaretPosition(size_t caretPos) {
    auto current = rime::Service::instance().GetSession(session());
    if (t9_.MoveCaret(current.get(), caretPos)) return;
    rime->set_caret_pos(session(), caretPos);
  }

  bool selectCandidate(size_t index, bool global) {
    if (global) {
      return rime->select_candidate(session(), index);
    } else {
      return rime->select_candidate_on_current_page(session(), index);
    }
  }

  bool deleteCandidate(size_t index, bool global) {
    if (global) {
      return rime->delete_candidate(session(), index);
    } else {
      return rime->delete_candidate_on_current_page(session(), index);
    }
  }

  bool changePage(bool backward) {
    return rime->change_page(session(), backward);
  }

  std::vector<CandidateProto> getCandidates(int startIndex, int limit) {
    std::vector<CandidateProto> result;
    result.reserve(limit);
    RimeCandidateListIterator iter{};
    if (rime->candidate_list_from_index(session(), &iter, startIndex)) {
      int count = 0;
      while (rime->candidate_list_next(&iter)) {
        if (count >= limit) break;
        result.emplace_back(iter.candidate);
        ++count;
      }
      rime->candidate_list_end(&iter);
    }
    return result;
  }

  std::tuple<int, int, std::vector<CandidateProto>> getBulkCandidates() {
    constexpr int limit = 16;
    auto list = getCandidates(0, limit);
    // use -1 to indicate it's not sure how many candidates now
    auto size = list.size() < limit ? list.size() : -1;
    auto highlighted = rime_get_highlighted_candidate_index(session());
    return std::make_tuple(size, highlighted, std::move(list));
  }

  void exit() {
    t9_.Reset();
    session_.reset();
    rime->finalize();
  }

  bool sync() {
    t9_.Reset();
    session_.reset();
    return rime->sync_user_data();
  }

 private:
  RimeApi* rime;
  trime::T9 t9_;
  std::shared_ptr<SessionHolder> session_;

  RimeSessionId session(bool requestNewSession = true) {
    if (!session_ && requestNewSession) {
      try {
        auto newSession = std::make_shared<SessionHolder>();
        session_ = newSession;
      } catch (...) {
        session_ = nullptr;
      }
    }
    if (!session_) {
      return 0;
    }
    return session_->id();
  }
};

GlobalRefSingleton* GlobalRef;

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeT9State(JNIEnv* env, jclass,
                                               jint options) {
  if (env->ExceptionCheck()) return nullptr;
  try {
    Rime::Instance().setT9AssistOptions(options);
    auto state = Rime::Instance().t9Snapshot();
    JRef<jclass> span_class(
        env, env->FindClass("com/osfans/trime/core/T9SpanProto"));
    if (!*span_class) return nullptr;
    auto span_init =
        env->GetMethodID(span_class, "<init>", "(IILjava/lang/String;ZZI)V");
    if (!span_init) return nullptr;
    auto spans = [&](const std::vector<trime::T9Span>& items) -> jobjectArray {
      auto result = env->NewObjectArray(items.size(), span_class, nullptr);
      if (!result) return nullptr;
      for (size_t i = 0; i < items.size(); ++i) {
        const auto& span = items[i];
        JString spelling(env, span.spelling);
        if (env->ExceptionCheck()) {
          env->DeleteLocalRef(result);
          return nullptr;
        }
        JRef item(env, env->NewObject(span_class, span_init, span.start,
                                      span.end, *spelling, span.completion,
                                      span.locked, span.sources));
        if (!*item) {
          env->DeleteLocalRef(result);
          return nullptr;
        }
        env->SetObjectArrayElement(result, i, item);
        if (env->ExceptionCheck()) {
          env->DeleteLocalRef(result);
          return nullptr;
        }
      }
      return result;
    };
    JRef segments(env, spans(state.segments));
    if (!*segments) return nullptr;
    JRef choices(env, spans(state.choices));
    if (!*choices) return nullptr;
    JRef<jclass> state_class(
        env, env->FindClass("com/osfans/trime/core/T9StateProto"));
    if (!*state_class) return nullptr;
    auto init =
        env->GetMethodID(state_class, "<init>",
                         "(ZILjava/lang/String;IZ[Lcom/osfans/trime/core/"
                         "T9SpanProto;[Lcom/osfans/trime/core/T9SpanProto;)V");
    if (!init) return nullptr;
    JString input(env, state.input);
    if (env->ExceptionCheck()) return nullptr;
    return env->NewObject(state_class, init, state.enabled, state.revision,
                          *input, state.focus, state.can_undo, *segments,
                          *choices);
  } catch (const std::exception& error) {
    if (!env->ExceptionCheck()) throwJavaException(env, error.what());
  } catch (...) {
    if (!env->ExceptionCheck())
      throwJavaException(env, "Unable to read T9 state");
  }
  return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_performRimeT9Action(JNIEnv* env, jclass,
                                                    jint revision, jint action,
                                                    jint start, jint end,
                                                    jstring spelling,
                                                    jint options) {
  if (env->ExceptionCheck()) return JNI_FALSE;
  if (!spelling) {
    throwJavaException(env, "T9 spelling must not be null");
    return JNI_FALSE;
  }
  try {
    CString text(env, spelling);
    if (env->ExceptionCheck()) return JNI_FALSE;
    Rime::Instance().setT9AssistOptions(options);
    return Rime::Instance().t9Action(revision, action, start, end, *text);
  } catch (const std::exception& error) {
    if (!env->ExceptionCheck()) throwJavaException(env, error.what());
  } catch (...) {
    if (!env->ExceptionCheck())
      throwJavaException(env, "Unable to change T9 state");
  }
  return JNI_FALSE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* jvm, void* reserved) {
  GlobalRef = new GlobalRefSingleton(jvm);
  declare_librime_module_dependencies();
  return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL Java_com_osfans_trime_core_Rime_startupRime(
    JNIEnv* env, jclass clazz, jstring shared_dir, jstring user_dir,
    jstring version_name, jboolean full_check) {
  CString sharedDir(env, shared_dir);
  if (env->ExceptionCheck()) return;
  CString userDir(env, user_dir);
  if (env->ExceptionCheck()) return;
  CString versionName(env, version_name);
  if (env->ExceptionCheck()) return;
  // for rime shared data dir
  setenv("RIME_SHARED_DATA_DIR", sharedDir, 1);
  // for rime user data dir
  setenv("RIME_USER_DATA_DIR", userDir, 1);
  setenv("RIME_DISTRIBUTION_VERSION", versionName, 1);

  auto notificationHandler = [](void* context_object, RimeSessionId session_id,
                                const char* message_type,
                                const char* message_value) {
    auto env = GlobalRef->AttachEnv();
    int type = 0;  // unknown
    if (strcmp(message_type, "schema") == 0) {
      type = 1;
    } else if (strcmp(message_type, "option") == 0) {
      type = 2;
    } else if (strcmp(message_type, "deploy") == 0) {
      type = 3;
    }
    auto vararg = JRef<jobjectArray>(
        env, env->NewObjectArray(1, GlobalRef->Object, nullptr));
    env->SetObjectArrayElement(vararg, 0, JString(env, message_value));
    env->CallStaticVoidMethod(GlobalRef->Rime, GlobalRef->HandleRimeMessage,
                              type, *vararg);
  };

  Rime::Instance().startup(full_check, notificationHandler);
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_exitRime(JNIEnv* env, jclass /* thiz */) {
  Rime::Instance().exit();
}

// deployment
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeSchemaFile(JNIEnv* env,
                                                     jclass /* thiz */,
                                                     jstring schema_file) {
  CString schemaFile(env, schema_file);
  if (env->ExceptionCheck()) return JNI_FALSE;
  return Rime::Instance().deploySchema(*schemaFile);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeConfigFile(JNIEnv* env,
                                                     jclass /* thiz */,
                                                     jstring file_name,
                                                     jstring version_key) {
  CString fileName(env, file_name);
  if (env->ExceptionCheck()) return JNI_FALSE;
  CString versionKey(env, version_key);
  if (env->ExceptionCheck()) return JNI_FALSE;
  return Rime::Instance().deployConfigFile(*fileName, *versionKey);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_syncRimeUserData(JNIEnv* env,
                                                 jclass /* thiz */) {
  return Rime::Instance().sync();
}

// input
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_processRimeKey(JNIEnv* env, jclass /* thiz */,
                                               jint keycode, jint mask) {
  return Rime::Instance().processKey(keycode, mask);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_commitRimeComposition(JNIEnv* env,
                                                      jclass /* thiz */) {
  return Rime::Instance().commitComposition();
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_clearRimeComposition(JNIEnv* env,
                                                     jclass /* thiz */) {
  Rime::Instance().clearComposition();
}

// output
extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeCommit(JNIEnv* env, jclass /* thiz */) {
  auto commit = Rime::Instance().commit();
  return rimeCommitToJObject(env, *commit);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeContext(JNIEnv* env, jclass /* thiz */) {
  auto context = Rime::Instance().context();
  return rimeContextToJObject(env, *context);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeStatus(JNIEnv* env, jclass /* thiz */) {
  auto status = Rime::Instance().status();
  return rimeStatusToJObject(env, *status);
}

// runtime options
extern "C" JNIEXPORT void JNICALL Java_com_osfans_trime_core_Rime_setRimeOption(
    JNIEnv* env, jclass /* thiz */, jstring option, jboolean value) {
  CString optionName(env, option);
  if (env->ExceptionCheck()) return;
  Rime::Instance().setOption(*optionName, value);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_getRimeOption(JNIEnv* env, jclass /* thiz */,
                                              jstring option) {
  CString optionName(env, option);
  if (env->ExceptionCheck()) return JNI_FALSE;
  return Rime::Instance().getOption(*optionName);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getRimeSchemaList(JNIEnv* env,
                                                  jclass /* thiz */) {
  return rimeSchemaListToJObjectArray(env, Rime::Instance().schemaList());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getCurrentRimeSchema(JNIEnv* env,
                                                     jclass /* thiz */) {
  return makeJavaString(env, Rime::Instance().currentSchemaId());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeSchema(JNIEnv* env, jclass /* thiz */,
                                                 jstring schema_id) {
  CString schemaId(env, schema_id);
  if (env->ExceptionCheck()) return JNI_FALSE;
  return Rime::Instance().selectSchema(*schemaId);
}

// testing
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_simulateRimeKeySequence(JNIEnv* env,
                                                        jclass /* thiz */,
                                                        jstring key_sequence) {
  CString keySequence(env, key_sequence);
  if (env->ExceptionCheck()) return JNI_FALSE;
  return Rime::Instance().simulateKeySequence(keySequence);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_core_Rime_getRimeRawInput(JNIEnv* env,
                                                jclass /* thiz */) {
  return makeJavaString(env, Rime::Instance().rawInput());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeCaretPos(JNIEnv* env,
                                                jclass /* thiz */) {
  return static_cast<jint>(Rime::Instance().caretPosition());
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setRimeCaretPos(JNIEnv* env, jclass /* thiz */,
                                                jint caret_pos) {
  Rime::Instance().setCaretPosition(caret_pos);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_selectRimeCandidate(JNIEnv* env,
                                                    jclass /* thiz */,
                                                    jint index,
                                                    jboolean global) {
  return Rime::Instance().selectCandidate(index, global);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deleteRimeCandidate(JNIEnv* env,
                                                    jclass /* thiz */,
                                                    jint index,
                                                    jboolean global) {
  return Rime::Instance().deleteCandidate(index, global);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_changeRimeCandidatePage(JNIEnv* env,
                                                        jclass clazz,
                                                        jboolean backward) {
  return Rime::Instance().changePage(backward);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getRimeCandidates(JNIEnv* env, jclass clazz,
                                                  jint start_index,
                                                  jint limit) {
  return rimeCandidateListToJObjectArray(
      env, Rime::Instance().getCandidates(start_index, limit));
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_osfans_trime_core_Rime_getRimeResponse(JNIEnv* env, jclass clazz,
                                                jboolean paging_mode) {
  auto commit = Rime::Instance().commit();
  // the menu is only needed in paging mode, otherwise its candidates would be
  // duplicated by the bulk candidates query below
  auto context = Rime::Instance().context(paging_mode);
  auto status = Rime::Instance().status();
  auto jCommit = JRef(env, rimeCommitToJObject(env, *commit));
  auto jComposition =
      JRef(env, rimeCompositionToJObject(env, context->composition));
  auto jStatus = JRef(env, rimeStatusToJObject(env, *status));
  // keep the local references alive until RimeResponse is constructed below
  jobject jCandidates = nullptr;
  if (paging_mode) {
    // the candidate layout is queried right where the page is built, so the
    // consumer does not need a separate rime option round-trip per key
    auto& rime = Rime::Instance();
    bool is_horizontal_layout =
        rime.getOption("_linear") || rime.getOption("_horizontal");
    jCandidates =
        rimeCandidatesPagedToJObject(env, context->menu, is_horizontal_layout);
  } else {
    auto [size, highlighted, list] = Rime::Instance().getBulkCandidates();
    auto jList =
        JRef<jobjectArray>(env, rimeCandidateListToJObjectArray(env, list));
    jCandidates =
        env->NewObject(GlobalRef->CandidatesBulk, GlobalRef->CandidatesBulkInit,
                       size, highlighted, *jList);
  }
  return rimeResponseToJObject(env, jCommit, jComposition, jCandidates,
                               jStatus);
}
