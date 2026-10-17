// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <glog/logging.h>
#include <rime/context.h>
#include <rime/dict/dictionary.h>
#include <rime/key_event.h>
#include <rime/schema.h>
#include <rime/service.h>
#include <rime/ticket.h>
#include <rime_api.h>

#include <algorithm>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "candidate_ranking.h"
#include "frontend.h"
#include "jni-utils.h"
#include "objconv.h"
#include "predict/prediction_key_policy.h"
#include "session.h"
#include "t9.h"
#include "trime_prediction_guard.h"
#include "trime_privacy_guard.h"

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
               const RimeNotificationHandler& notificationHandler,
               bool runMaintenance = true) {
    privacy_mode_ = false;
    trime::SetPrivacyModeEnabled(false);
    trime::SetPredictionRequested(false);
    FLAGS_minloglevel = google::GLOG_WARNING;
    if (!rime) return;
    const char* userDir = getenv("RIME_USER_DATA_DIR");
    const char* sharedDir = getenv("RIME_SHARED_DATA_DIR");
    const char* versionName = getenv("RIME_DISTRIBUTION_VERSION");

    RIME_STRUCT(RimeTraits, trime_traits)
    trime_traits.shared_data_dir = sharedDir;
    trime_traits.user_data_dir = userDir;
    trime_traits.log_dir = "";  // set empty log_dir to log to logcat only
    // Keep warnings/errors without logging every vocabulary entry or input
    // query.
    trime_traits.min_log_level = 1;
    trime_traits.app_name = "rime.trime";
    trime_traits.distribution_name = "Trime";
    trime_traits.distribution_code_name = "trime";
    trime_traits.distribution_version = versionName;

    // Logging/module declarations are process-wide. initialize() below
    // reapplies directories and traits and reloads modules after every
    // finalize().
    static std::once_flag setup_once;
    std::call_once(setup_once, [&] { rime->setup(&trime_traits); });
    rime->initialize(&trime_traits);
    rime->set_notification_handler(notificationHandler, GlobalRef->jvm);
    // A rolled-back context must open its restored artifacts without running
    // detect_modifications: restoring files itself updates directory mtimes.
    if (runMaintenance) rime->start_maintenance(fullCheck);
    // Startup runs on RimeDispatcher, never the Android UI thread. Do not
    // publish READY or create a session while deployment is still writing data.
    rime->join_maintenance_thread();
  }

  bool deploySchema(std::string_view schemaFile) {
    return rime->deploy_schema(schemaFile.data());
  }

  bool hasUsableDictionary() {
    auto current = rime::Service::instance().GetSession(session());
    if (!current || !current->schema() ||
        current->schema()->schema_id() == ".default")
      return false;
    auto component = rime::Dictionary::Require("dictionary");
    if (!component) return false;
    std::unique_ptr<rime::Dictionary> dictionary(
        component->Create(rime::Ticket(current->schema(), "translator")));
    return dictionary && dictionary->Load();
  }

  bool deployConfigFile(std::string_view configFile,
                        std::string_view versionKey) {
    return rime->deploy_config_file(configFile.data(), versionKey.data());
  }

  bool processKey(int keycode, int mask) {
    auto current = rime::Service::instance().GetSession(session());
    // T9 intercepts physical digits before Rime processors. Remove the
    // optional next-word menu first so this very key starts fresh T9 input.
    if (hasPrediction() &&
        trime::prediction::ShouldDismissForKey(rime::KeyEvent(keycode, mask)))
      clearPredictionContext();
    const bool handled = t9_.ProcessKey(current.get(), keycode, mask) ||
                         rime->process_key(session(), keycode, mask);
    applyCandidatePins();
    return handled;
  }

  bool simulateKeySequence(const std::string& sequence) {
    const auto state = t9Snapshot();
    const bool has_locked =
        std::any_of(state.segments.begin(), state.segments.end(),
                    [](const auto& span) { return span.locked; });
    rime::KeySequence keys;
    if (!keys.Parse(sequence)) return false;
    for (const auto& key : keys) {
      if (has_locked) {
        processKey(key.keycode(), key.modifier());
      } else {
        // Preserve native macro caret/undo semantics. The T9 physical-key
        // interceptor is only used for macros that already own a locked span.
        if (hasPrediction() && trime::prediction::ShouldDismissForKey(key))
          clearPredictionContext();
        rime->process_key(session(), key.keycode(), key.modifier());
        applyCandidatePins();
      }
    }
    return true;
  }

  bool commitComposition() {
    if (hasPrediction()) {
      clearPredictionContext();
      return false;
    }
    return rime->commit_composition(session());
  }

  bool hasPrediction() {
    const auto id = session(false);
    auto current = id ? rime::Service::instance().GetSession(id) : nullptr;
    return current && current->context() &&
           !current->context()->composition().empty() &&
           current->context()->composition().back().HasTag("prediction");
  }

  bool clearPredictionContext() {
    // Ordinary composition survives this boundary. Keep its original/ranked
    // menu pair so the next snapshot does not wrap the same menu again.
    const auto id = session(false);
    auto current = id ? rime::Service::instance().GetSession(id) : nullptr;
    return current && current->ResetPredictionContexts(false, false);
  }

  bool setPredictionEnabled(bool enabled) {
    trime::SetPredictionRequested(enabled);
    const bool allowed =
        enabled && !privacy_mode_ && !trime::IsPrivacyModeEnabled();
    const auto id = session(false);
    auto current = id ? rime::Service::instance().GetSession(id) : nullptr;
    if (current) current->ResetPredictionContexts(allowed, true);
    return allowed &&
           (!current || !current->context()->get_option("ascii_mode"));
  }

  void clearComposition() {
    clearPredictionContext();
    ranked_menu_.reset();
    original_menu_.reset();
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
    RIME_STRUCT(RimeCommit, data)
    if (rime->get_commit(session(), &data)) {
      auto p = std::make_unique<CommitProto>(&data);
      p->text = t9_.CommitText(*p->text);
      rime->free_commit(&data);
      return p;
    }
    return std::make_unique<CommitProto>();
  }

  std::unique_ptr<ContextProto> context(bool includeMenu = true) {
    RIME_STRUCT(RimeContext, data)
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
    RIME_STRUCT(RimeStatus, data)
    if (rime->get_status(session(), &data)) {
      auto p = std::make_unique<StatusProto>(&data);
      rime->free_status(&data);
      return p;
    }
    return std::make_unique<StatusProto>();
  }

  void setOption(std::string_view key, bool value) {
    if (key == "prediction") {
      clearPredictionContext();
      value = value && trime::IsPredictionRequested() && !privacy_mode_ &&
              !trime::IsPrivacyModeEnabled() &&
              !rime->get_option(session(), "ascii_mode");
    } else if (key == "ascii_mode") {
      clearPredictionContext();
    }
    rime->set_option(session(), key.data(), value);
    if (key == "ascii_mode") {
      const bool effective = trime::IsPredictionRequested() && !privacy_mode_ &&
                             !trime::IsPrivacyModeEnabled() && !value;
      rime->set_option(session(), "prediction", effective);
    }
  }

  bool getOption(std::string_view key) {
    return rime->get_option(session(), key.data());
  }

  int commitHistorySizeForTesting() {
    const auto id = session(false);
    auto current = id ? rime::Service::instance().GetSession(id) : nullptr;
    return current && current->context()
               ? static_cast<int>(current->context()->commit_history().size())
               : 0;
  }

  bool privacyGuardEnabledForTesting() const {
    return trime::IsPrivacyModeEnabled();
  }

  void setPrivacyMode(bool enabled) {
    // Suppress native content logs before touching state from the previous
    // editor. Keep the process guard enabled until a private editor has been
    // fully cleared on exit.
    FLAGS_minloglevel = google::GLOG_FATAL;
    trime::SetPrivacyModeEnabled(true);
    ranked_menu_.reset();
    original_menu_.reset();
    privacy_mode_ = enabled;

    {
      trime::PrivacyTransitionBoundary transition_boundary;
      t9_.Clear();
      const auto id = session(enabled);
      auto current = id ? rime::Service::instance().GetSession(id) : nullptr;
      if (current) {
        current->ResetPrivacyContexts(enabled);
      }
    }

    if (!enabled) {
      privacy_mode_ = false;
      trime::SetPrivacyModeEnabled(false);
      FLAGS_minloglevel = google::GLOG_WARNING;
    }
    setPredictionEnabled(trime::IsPredictionRequested());
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
    return std::move(result);
  }

  bool selectSchema(std::string_view schemaId) {
    clearPredictionContext();
    ranked_menu_.reset();
    original_menu_.reset();
    t9_.Reset();
    const bool selected = rime->select_schema(session(), schemaId.data());
    if (selected) setPredictionEnabled(trime::IsPredictionRequested());
    return selected;
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
    if (hasPrediction() &&
        (!trime::IsPredictionRequested() || trime::IsPrivacyModeEnabled())) {
      clearPredictionContext();
      return false;
    }
    if (global) {
      return rime->select_candidate(session(), index);
    } else {
      return rime->select_candidate_on_current_page(session(), index);
    }
  }

  bool deleteCandidate(size_t index, bool global) {
    if (privacy_mode_ || hasPrediction()) return false;
    auto current = rime::Service::instance().GetSession(session());
    if (!current || !current->context()->HasMenu()) return false;
    auto& segment = current->context()->composition().back();
    const auto page_size = current->schema()->page_size();
    if (page_size <= 0 || (!global && index >= static_cast<size_t>(page_size)))
      return false;
    const auto actual =
        global ? index
               : (segment.selected_index / page_size) * page_size + index;
    if (!segment.GetCandidateAt(actual)) return false;
    auto previous_menu = segment.menu;
    const bool accepted =
        global ? rime->delete_candidate(session(), index)
               : rime->delete_candidate_on_current_page(session(), index);
    // Rime's Boolean acknowledges dispatch even when no translator handles it.
    // A handling Memory refreshes the menu; this still does not promise durable
    // storage success, which the UI accurately describes as a forget request.
    return accepted &&
           (current->context()->composition().empty() ||
            current->context()->composition().back().menu != previous_menu);
  }

  bool changePage(bool backward) {
    return rime->change_page(session(), backward);
  }

  void suspendUserDictionarySession() {
    t9_.Reset();
    ranked_menu_.reset();
    original_menu_.reset();
    session_.reset();
    rime->cleanup_all_sessions();
  }

  void setCandidatePins(const std::vector<std::string>& schemas,
                        const std::vector<std::string>& inputs,
                        const std::vector<std::string>& texts) {
    if (schemas.size() != inputs.size() || schemas.size() != texts.size() ||
        schemas.size() > 512)
      throw std::invalid_argument("Invalid candidate pin list");
    std::map<std::pair<std::string, std::string>, std::string> replacement;
    for (size_t i = 0; i < schemas.size(); ++i) {
      if (schemas[i].empty() || inputs[i].empty() || texts[i].empty())
        throw std::invalid_argument("Invalid candidate pin");
      replacement[{schemas[i], inputs[i]}] = texts[i];
    }
    auto current = rime::Service::instance().GetSession(session(false));
    if (current && current->context()->HasMenu()) {
      auto& segment = current->context()->composition().back();
      if (segment.menu == ranked_menu_ && original_menu_) {
        segment.menu = original_menu_;
        segment.selected_index = 0;
      }
    }
    ranked_menu_.reset();
    original_menu_.reset();
    pins_ = std::move(replacement);
    applyCandidatePins();
  }

  std::vector<std::string> candidatePinIdentity(int index, bool global) {
    if (privacy_mode_ || hasPrediction()) return {};
    auto current = rime::Service::instance().GetSession(session());
    if (!current || !current->context()->HasMenu()) return {};
    auto& segment = current->context()->composition().back();
    if (!global) {
      int page_size = 5;
      current->schema()->config()->GetInt("menu/page_size", &page_size);
      if (page_size <= 0 || index < 0 || index >= page_size) return {};
      index += (segment.selected_index / page_size) * page_size;
    }
    if (index < 0 ||
        index >= static_cast<int>(trime::PinnedTranslation::kWindow))
      return {};
    auto candidate = segment.GetCandidateAt(index);
    if (!candidate) return {};
    return {currentSchemaId(), rawInput(), candidate->text()};
  }

  void applyCandidatePins() {
    auto current = rime::Service::instance().GetSession(session(false));
    if (!current || !current->context()->HasMenu()) {
      ranked_menu_.reset();
      original_menu_.reset();
      return;
    }
    auto& segment = current->context()->composition().back();
    if (segment.menu == ranked_menu_) return;
    ranked_menu_.reset();
    original_menu_.reset();
    if (privacy_mode_ || segment.HasTag("prediction")) return;
    auto found = pins_.find({currentSchemaId(), rawInput()});
    if (found == pins_.end()) return;
    original_menu_ = segment.menu;
    auto selected = segment.GetSelectedCandidate();
    const auto selected_index = segment.selected_index;
    auto translation =
        rime::New<trime::PinnedTranslation>(original_menu_, found->second);
    ranked_menu_ = rime::New<rime::Menu>();
    ranked_menu_->AddTranslation(translation);
    segment.menu = ranked_menu_;
    // A guessed first candidate becomes the pinned candidate. Explicit
    // navigation retains its selected object even when its index moves within
    // the window.
    if (selected_index > 0 &&
        selected_index < trime::PinnedTranslation::kWindow)
      segment.selected_index = translation->indexOf(selected);
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
    return std::move(result);
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
    if (privacy_mode_) {
      FLAGS_minloglevel = google::GLOG_FATAL;
      trime::SetPrivacyModeEnabled(true);
    }
    t9_.Reset();
    ranked_menu_.reset();
    original_menu_.reset();
    session_.reset();
    rime->finalize();
    privacy_mode_ = false;
    trime::SetPrivacyModeEnabled(false);
    trime::SetPredictionRequested(false);
    FLAGS_minloglevel = google::GLOG_WARNING;
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
  bool privacy_mode_ = false;
  std::map<std::pair<std::string, std::string>, std::string> pins_;
  rime::an<rime::Menu> ranked_menu_;
  rime::an<rime::Menu> original_menu_;

  RimeSessionId session(bool requestNewSession = true) {
    if (!session_ && requestNewSession) {
      try {
        auto newSession = std::make_shared<SessionHolder>();
        session_ = newSession;
        auto current = rime::Service::instance().GetSession(newSession->id());
        if (current) {
          current->ResetPredictionContexts(
              trime::IsPredictionRequested() && !privacy_mode_, true);
        }
        if (privacy_mode_) {
          auto current = rime::Service::instance().GetSession(newSession->id());
          if (current && current->context()) {
            current->context()->set_option("trime_privacy_mode", true);
          }
        }
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
  Rime::Instance().setT9AssistOptions(options);
  auto state = Rime::Instance().t9Snapshot();
  JRef<jclass> span_class(env,
                          env->FindClass("com/osfans/trime/core/T9SpanProto"));
  auto span_init =
      env->GetMethodID(span_class, "<init>", "(IILjava/lang/String;ZZI)V");
  auto spans = [&](const std::vector<trime::T9Span>& items) {
    auto result = env->NewObjectArray(items.size(), span_class, nullptr);
    for (size_t i = 0; i < items.size(); ++i) {
      const auto& s = items[i];
      JRef item(env, env->NewObject(span_class, span_init, s.start, s.end,
                                    *JString(env, s.spelling), s.completion,
                                    s.locked, s.sources));
      env->SetObjectArrayElement(result, i, item);
    }
    return result;
  };
  JRef segments(env, spans(state.segments));
  JRef choices(env, spans(state.choices));
  JRef<jclass> state_class(
      env, env->FindClass("com/osfans/trime/core/T9StateProto"));
  auto init =
      env->GetMethodID(state_class, "<init>",
                       "(ZILjava/lang/String;IZ[Lcom/osfans/trime/core/"
                       "T9SpanProto;[Lcom/osfans/trime/core/T9SpanProto;)V");
  return env->NewObject(state_class, init, state.enabled, state.revision,
                        *JString(env, state.input), state.focus, state.can_undo,
                        *segments, *choices);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_performRimeT9Action(JNIEnv* env, jclass,
                                                    jint revision, jint action,
                                                    jint start, jint end,
                                                    jstring spelling,
                                                    jint options) {
  Rime::Instance().setT9AssistOptions(options);
  return Rime::Instance().t9Action(revision, action, start, end,
                                   *CString(env, spelling));
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* jvm, void* reserved) {
  GlobalRef = new GlobalRefSingleton(jvm);
  declare_librime_module_dependencies();
  return JNI_VERSION_1_6;
}

static void initializeRime(JNIEnv* env, jstring shared_dir, jstring user_dir,
                           jstring version_name, bool full_check,
                           bool run_maintenance) {
  jniCall(env, [&] {
    // for rime shared data dir
    setenv("RIME_SHARED_DATA_DIR", CString(env, shared_dir), 1);
    // for rime user data dir
    setenv("RIME_USER_DATA_DIR", CString(env, user_dir), 1);
    setenv("RIME_DISTRIBUTION_VERSION", CString(env, version_name), 1);

    auto notificationHandler =
        [](void* context_object, RimeSessionId session_id,
           const char* message_type, const char* message_value) {
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
          env->CallStaticVoidMethod(
              GlobalRef->Rime, GlobalRef->HandleRimeMessage, type, *vararg);
        };

    Rime::Instance().startup(full_check, notificationHandler, run_maintenance);
  });
}

extern "C" JNIEXPORT void JNICALL Java_com_osfans_trime_core_Rime_startupRime(
    JNIEnv* env, jclass /* clazz */, jstring shared_dir, jstring user_dir,
    jstring version_name, jboolean full_check) {
  initializeRime(env, shared_dir, user_dir, version_name, full_check, true);
}

extern "C" JNIEXPORT void JNICALL Java_com_osfans_trime_core_Rime_restoreRime(
    JNIEnv* env, jclass /* clazz */, jstring shared_dir, jstring user_dir,
    jstring version_name) {
  initializeRime(env, shared_dir, user_dir, version_name, false, false);
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_exitRime(JNIEnv* env, jclass /* thiz */) {
  jniCall(env, [&] { Rime::Instance().exit(); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_suspendRimeUserDictionarySession(JNIEnv* env,
                                                                 jclass) {
  jniCall(env, [&] { Rime::Instance().suspendUserDictionarySession(); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setRimeCandidatePins(JNIEnv* env, jclass,
                                                     jobjectArray schemas,
                                                     jobjectArray inputs,
                                                     jobjectArray texts) {
  jniCall(env, [&] {
    Rime::Instance().setCandidatePins(stringArrayToStringVector(env, schemas),
                                      stringArrayToStringVector(env, inputs),
                                      stringArrayToStringVector(env, texts));
  });
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_osfans_trime_core_Rime_getRimeCandidatePinIdentity(JNIEnv* env, jclass,
                                                            jint index,
                                                            jboolean global) {
  return jniCall(env, [&]() -> jobjectArray {
    return stringVectorToJStringArray(
        env, Rime::Instance().candidatePinIdentity(index, global));
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_hasUsableRimeDictionary(JNIEnv* env,
                                                        jclass /* thiz */) {
  return jniCall(env, [&] { return Rime::Instance().hasUsableDictionary(); });
}

// deployment
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeSchemaFile(JNIEnv* env,
                                                     jclass /* thiz */,
                                                     jstring schema_file) {
  return jniCall(env, [&] {
    return Rime::Instance().deploySchema(*CString(env, schema_file));
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deployRimeConfigFile(JNIEnv* env,
                                                     jclass /* thiz */,
                                                     jstring file_name,
                                                     jstring version_key) {
  return jniCall(env, [&] {
    return Rime::Instance().deployConfigFile(*CString(env, file_name),
                                             *CString(env, version_key));
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_syncRimeUserData(JNIEnv* env,
                                                 jclass /* thiz */) {
  return jniCall(env, [&] { return Rime::Instance().sync(); });
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
  Rime::Instance().setOption(*CString(env, option), value);
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_core_Rime_setRimePrivacyMode(JNIEnv*, jclass,
                                                   jboolean enabled) {
  Rime::Instance().setPrivacyMode(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_setRimePredictionEnabled(JNIEnv* env, jclass,
                                                         jboolean enabled) {
  return jniCall(env, [&] {
    return Rime::Instance().setPredictionEnabled(enabled == JNI_TRUE);
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_clearRimePredictionContext(JNIEnv* env,
                                                           jclass) {
  return jniCall(env,
                 [&] { return Rime::Instance().clearPredictionContext(); });
}

extern "C" JNIEXPORT jint JNICALL
Java_com_osfans_trime_core_Rime_getRimeCommitHistorySizeForTesting(JNIEnv*,
                                                                   jclass) {
  return Rime::Instance().commitHistorySizeForTesting();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_isRimePrivacyGuardEnabledForTesting(JNIEnv*,
                                                                    jclass) {
  return Rime::Instance().privacyGuardEnabledForTesting();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_getRimeOption(JNIEnv* env, jclass /* thiz */,
                                              jstring option) {
  return Rime::Instance().getOption(*CString(env, option));
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
  return Rime::Instance().selectSchema(*CString(env, schema_id));
}

// testing
extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_simulateRimeKeySequence(JNIEnv* env,
                                                        jclass /* thiz */,
                                                        jstring key_sequence) {
  return Rime::Instance().simulateKeySequence(CString(env, key_sequence));
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
  if (index < 0) return JNI_FALSE;
  return jniCall(env, [&] {
    return static_cast<jboolean>(
        Rime::Instance().selectCandidate(static_cast<size_t>(index), global));
  });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_osfans_trime_core_Rime_deleteRimeCandidate(JNIEnv* env,
                                                    jclass /* thiz */,
                                                    jint index,
                                                    jboolean global) {
  if (index < 0) return JNI_FALSE;
  return jniCall(env, [&] {
    return static_cast<jboolean>(
        Rime::Instance().deleteCandidate(static_cast<size_t>(index), global));
  });
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
  return jniCall(env, [&]() -> jobject {
    Rime::Instance().applyCandidatePins();
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
      jCandidates = rimeCandidatesPagedToJObject(env, context->menu,
                                                 is_horizontal_layout);
    } else {
      auto [size, highlighted, list] = Rime::Instance().getBulkCandidates();
      auto jList =
          JRef<jobjectArray>(env, rimeCandidateListToJObjectArray(env, list));
      jCandidates = env->NewObject(GlobalRef->CandidatesBulk,
                                   GlobalRef->CandidatesBulkInit, size,
                                   highlighted, *jList);
    }
    // This flag describes the very same native snapshot as the candidate
    // objects above; no per-candidate or extra frontend JNI query is needed.
    if (!jCandidates || env->ExceptionCheck()) return nullptr;
    const auto set_prediction = paging_mode
                                    ? GlobalRef->CandidatesPagedSetPrediction
                                    : GlobalRef->CandidatesBulkSetPrediction;
    if (!set_prediction) return nullptr;
    env->CallVoidMethod(
        jCandidates, set_prediction,
        Rime::Instance().hasPrediction() ? JNI_TRUE : JNI_FALSE);
    if (env->ExceptionCheck()) return nullptr;
    return rimeResponseToJObject(env, jCommit, jComposition, jCandidates,
                                 jStatus);
  });
}
