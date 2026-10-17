# SPDX-License-Identifier: GPL-3.0-or-later

# Keep the pinned Rime repositories untouched. Privacy enforcement is applied
# to build-tree copies so an upstream source drift fails configuration instead
# of silently dropping a guard.
function(trime_adapt_rime_privacy)
  set(output "${CMAKE_BINARY_DIR}/trime-rime-privacy")
  set(root "${CMAKE_BINARY_DIR}/trime-rime-privacy-stage")
  set(rime_files
      rime/context.h
      rime/context.cc
      rime/engine.cc
      rime/service.h
      rime/service.cc
      rime/dict/user_dictionary.cc
      rime/gear/ascii_composer.h
      rime/gear/ascii_composer.cc
      rime/gear/chord_composer.cc
      rime/gear/streaming_chord_processor.h
      rime/gear/streaming_chord_processor.cc
      rime/gear/key_binder.h
      rime/gear/key_binder.cc
      rime/gear/navigator.h
      rime/gear/navigator.cc
      rime/gear/punctuator.h
      rime/gear/punctuator.cc
      rime/gear/script_translator.h
      rime/gear/script_translator.cc
      rime/gear/table_translator.cc)
  foreach(relative IN LISTS rime_files)
    set(original "${CMAKE_SOURCE_DIR}/librime/src/${relative}")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${original}")
    configure_file("${original}" "${root}/${relative}" COPYONLY)
  endforeach()

  trime_replace_dependency_cmake("${root}/rime/context.h"
    "  Notifier& commit_notifier() { return commit_notifier_; }"
    "  Notifier& commit_output_notifier() { return commit_output_notifier_; }\n  Notifier& commit_notifier() { return commit_notifier_; }")
  trime_replace_dependency_cmake("${root}/rime/context.h"
    "  KeyEventNotifier& unhandled_key_notifier() { return unhandled_key_notifier_; }"
    "  KeyEventNotifier& unhandled_key_output_notifier() {\n    return unhandled_key_output_notifier_;\n  }\n  KeyEventNotifier& unhandled_key_notifier() { return unhandled_key_notifier_; }")
  trime_replace_dependency_cmake("${root}/rime/context.h"
    "  KeyEventNotifier unhandled_key_notifier_;"
    "  KeyEventNotifier unhandled_key_notifier_;\n  // Kept last so downstream plugins compiled against the pinned public\n  // header retain the offsets of all existing members.\n  Notifier commit_output_notifier_;\n  KeyEventNotifier unhandled_key_output_notifier_;")

  trime_replace_dependency_cmake("${root}/rime/context.cc"
    "#include <rime/segmentation.h>"
    "#include <rime/segmentation.h>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/context.cc"
    [=[  // notify the engine and interesting components
  commit_notifier_(this);]=]
    [=[  // Always deliver the committed text to the application. Learning and
  // plugin observers must not receive a private composition.
  commit_output_notifier_(this);
  if (!trime::IsPrivacyModeEnabled()) {
    commit_notifier_(this);
  }]=])
  trime_replace_dependency_cmake("${root}/rime/context.cc"
    "  delete_notifier_(this);"
    "  if (!trime::IsPrivacyModeEnabled()) {\n    delete_notifier_(this);\n  }")

  trime_replace_dependency_cmake("${root}/rime/engine.cc"
    "#include <rime/translation.h>"
    "#include <rime/translation.h>\n\n#include \"trime_privacy_guard.h\"\n#include \"trime_prediction_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/engine.cc"
    [=[    return Switches::kContinue;
  });
}]=]
    [=[    return Switches::kContinue;
  });
  // This runs after every complete component/schema initialization, including
  // the native switcher path that does not call the frontend select-schema JNI.
  // A schema reset can neither bypass the frontend gate nor disable an active
  // user request solely because its default prediction switch is off.
  context_->set_option("prediction", trime::IsPredictionRequested() &&
                         !trime::IsPrivacyModeEnabled() &&
                         !context_->get_option("ascii_mode"));
}]=])
  trime_replace_dependency_cmake("${root}/rime/engine.cc"
    "  context_->commit_notifier().connect([this](Context* ctx) { OnCommit(ctx); });"
    "  context_->commit_output_notifier().connect(\n      [this](Context* ctx) { OnCommit(ctx); });")
  trime_replace_dependency_cmake("${root}/rime/engine.cc"
    [=[  // record unhandled keys, eg. spaces, numbers, bksp's.
  // skip internal synthetic key events which won't reach the client app.
  if (!synthetic) {
    context_->commit_history().Push(key_event);
  }]=]
    [=[  // Neither private input nor internal synthetic keys may become
  // learning, prediction or phrase-encoding history.
  if (!synthetic && !trime::IsPrivacyModeEnabled()) {
    context_->commit_history().Push(key_event);
  }]=])
  trime_replace_dependency_cmake("${root}/rime/engine.cc"
    [=[  // notify interested parties
  context_->unhandled_key_notifier()(context_.get(), key_event);]=]
    [=[  // Internal state cleanup must still run for a directly committed key.
  // Memory and plugin observers remain excluded from private input.
  context_->unhandled_key_output_notifier()(context_.get(), key_event);
  if (!trime::IsPrivacyModeEnabled()) {
    context_->unhandled_key_notifier()(context_.get(), key_event);
  }]=])
  trime_replace_dependency_cmake("${root}/rime/engine.cc"
    "  context_->commit_history().Push(CommitRecord{\"raw\", text});"
    "  if (!trime::IsPrivacyModeEnabled()) {\n    context_->commit_history().Push(CommitRecord{\"raw\", text});\n  }")
  trime_replace_dependency_cmake("${root}/rime/engine.cc"
    "  context_->commit_history().Push(ctx->composition(), ctx->input());"
    "  if (!trime::IsPrivacyModeEnabled()) {\n    context_->commit_history().Push(ctx->composition(), ctx->input());\n  }")

  # Session::context() follows Engine::active_engine(). While the schema
  # switcher is open that is the switcher's temporary Context rather than the
  # primary engine Context that still owns the editor composition and gear
  # caches. Reset both contexts and leave the switcher coherently before an
  # editor/privacy boundary is published.
  trime_replace_dependency_cmake("${root}/rime/service.h"
    "  void ClearComposition();"
    "  void ClearComposition();\n  void ResetPrivacyContexts(bool enabled);\n  bool ResetPredictionContexts(bool enabled, bool update_option);")
  trime_replace_dependency_cmake("${root}/rime/service.cc"
    "#include <rime/service.h>"
    "#include <rime/service.h>\n#include <rime/switcher.h>\n#include \"trime_prediction_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/service.cc"
    [=[void Session::ClearComposition() {
  if (!engine_)
    return;
  engine_->context()->AbortComposition();
}

void Session::ApplySchema(Schema* schema) {]=]
    [=[void Session::ClearComposition() {
  if (!engine_)
    return;
  engine_->context()->AbortComposition();
}

void Session::ResetPrivacyContexts(bool enabled) {
  if (!engine_)
    return;

  auto reset_context = [enabled](Context* context) {
    if (!context)
      return;
    context->commit_history().clear();
    context->Clear();
    context->set_option("trime_privacy_mode", enabled);
    // Update observers and plugins run synchronously from Clear(). Do not let
    // one of them restore contextual history at the boundary.
    context->commit_history().clear();
  };

  Engine* primary = engine_.get();
  Engine* active = engine_->active_engine();
  if (active && active != primary) {
    reset_context(active->context());
    if (auto* switcher = dynamic_cast<Switcher*>(active);
        switcher && switcher->attached_engine() == primary) {
      switcher->Deactivate();
    } else {
      // The switcher is currently the only alternate core engine. Also reset
      // an extension-provided alternate engine instead of carrying it across
      // an editor boundary.
      engine_->set_active_engine();
    }
  }
  reset_context(primary->context());
}

bool Session::ResetPredictionContexts(bool enabled, bool update_option) {
  if (!engine_)
    return false;
  bool had_prediction = false;
  trime::PredictionTransitionBoundary boundary;
  auto reset_context = [&](Context* context) {
    if (!context)
      return;
    context->commit_history().clear();
    const bool predicted = !context->composition().empty() &&
        context->composition().back().HasTag("prediction");
    had_prediction |= predicted;
    if (predicted)
      context->Clear();
    else
      context->update_notifier()(context);
    if (update_option)
      context->set_option("prediction", enabled && !context->get_option("ascii_mode"));
    context->commit_history().clear();
  };
  Engine* primary = engine_.get();
  Engine* active = engine_->active_engine();
  if (active && active != primary)
    reset_context(active->context());
  reset_context(primary->context());
  return had_prediction;
}

void Session::ApplySchema(Schema* schema) {]=])

  trime_replace_dependency_cmake("${root}/rime/dict/user_dictionary.cc"
    "#include <rime/dict/user_dictionary.h>"
    "#include <rime/dict/user_dictionary.h>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/dict/user_dictionary.cc"
    [=[    double initial_credibility) {
  if (!table_ || !prism_ || !loaded() ||]=]
    [=[    double initial_credibility) {
  if (trime::IsPrivacyModeEnabled())
    return nullptr;
  if (!table_ || !prism_ || !loaded() ||]=])
  trime_replace_dependency_cmake("${root}/rime/dict/user_dictionary.cc"
    [=[                                   string* resume_key) {
  TickCount present_tick = tick_ + 1;]=]
    [=[                                   string* resume_key) {
  const string kEnd = "\xff";
  if (trime::IsPrivacyModeEnabled()) {
    if (resume_key)
      *resume_key = kEnd;
    return 0;
  }
  TickCount present_tick = tick_ + 1;]=])
  trime_replace_dependency_cmake("${root}/rime/dict/user_dictionary.cc"
    [=[  const string kEnd = "\xff";
  string key;]=]
    [=[  string key;]=])
  trime_replace_dependency_cmake("${root}/rime/dict/user_dictionary.cc"
    [=[                                 const string& new_entry_prefix) {
  string code_str(entry.custom_code);]=]
    [=[                                 const string& new_entry_prefix) {
  if (trime::IsPrivacyModeEnabled())
    return false;
  string code_str(entry.custom_code);]=])
  trime_replace_dependency_cmake("${root}/rime/dict/user_dictionary.cc"
    [=[bool UserDictionary::UpdateTickCount(TickCount increment) {
  tick_ += increment;]=]
    [=[bool UserDictionary::UpdateTickCount(TickCount increment) {
  if (trime::IsPrivacyModeEnabled())
    return false;
  tick_ += increment;]=])

  # setPrivacyMode scopes a transition-boundary flag around Context::Clear().
  # Use that narrow signal to erase cross-editor caches without treating every
  # private commit that clears the context as an editor boundary.
  trime_replace_dependency_cmake("${root}/rime/gear/ascii_composer.h"
    "  void OnContextUpdate(Context* ctx);"
    "  void OnContextUpdate(Context* ctx);\n  void OnPrivacyContextUpdate(Context* ctx);")
  trime_replace_dependency_cmake("${root}/rime/gear/ascii_composer.h"
    "  connection commit_connection_;"
    "  connection commit_connection_;\n  connection privacy_connection_;")
  trime_replace_dependency_cmake("${root}/rime/gear/ascii_composer.cc"
    "#include <rime/gear/ascii_composer.h>"
    "#include <rime/gear/ascii_composer.h>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/gear/ascii_composer.cc"
    [=[      KeyBindingProcessor<AsciiComposer>(action_definitions) {
  LoadConfig(ticket.schema);]=]
    [=[      KeyBindingProcessor<AsciiComposer>(action_definitions) {
  LoadConfig(ticket.schema);
  privacy_connection_ = engine_->context()->update_notifier().connect(
      [this](Context* ctx) { OnPrivacyContextUpdate(ctx); });]=])
  # OnCommit only clears this processor's transient physical-key ledger. It
  # must still run on private commits, unlike learning/plugin observers.
  trime_replace_dependency_cmake("${root}/rime/gear/ascii_composer.cc"
    "ctx->commit_notifier().connect([this](Context* ctx) { OnCommit(ctx); });"
    "ctx->commit_output_notifier().connect([this](Context* ctx) { OnCommit(ctx); });")
  trime_replace_dependency_cmake("${root}/rime/gear/ascii_composer.cc"
    [=[AsciiComposer::~AsciiComposer() {
  update_connection_.disconnect();
  commit_connection_.disconnect();
}]=]
    [=[AsciiComposer::~AsciiComposer() {
  update_connection_.disconnect();
  commit_connection_.disconnect();
  privacy_connection_.disconnect();
}]=])
  trime_replace_dependency_cmake("${root}/rime/gear/ascii_composer.cc"
    [=[void AsciiComposer::OnCommit(Context* ctx) {
  // 上屏結算時，物理賬本歸零
  raw_keys_.clear();
  cached_input_.clear();
  cached_raw_keys_.clear();
}]=]
    [=[void AsciiComposer::OnCommit(Context* ctx) {
  // 上屏結算時，物理賬本歸零
  raw_keys_.clear();
  cached_input_.clear();
  cached_raw_keys_.clear();
}

void AsciiComposer::OnPrivacyContextUpdate(Context* ctx) {
  if (!trime::IsPrivacyTransitionBoundary()) {
    return;
  }
  const bool had_inline_ascii = is_inline_ascii_;
  is_inline_ascii_ = false;
  raw_keys_.clear();
  cached_input_.clear();
  cached_raw_keys_.clear();
  if (had_inline_ascii) {
    ctx->set_option("ascii_mode", false);
  }
  toggle_with_caps_ = false;
  ResetModifierState();
  toggle_expired_ = std::chrono::steady_clock::time_point{};
}]=])

  # ChordComposer retains printable keys even when they are not handled. The
  # engine suppresses its unhandled-key callback in privacy mode, so explicitly
  # clear the raw sequence and the in-progress chord at the editor boundary.
  trime_replace_dependency_cmake("${root}/rime/gear/chord_composer.cc"
    "#include <rime/gear/key_binding_processor.h>"
    "#include <rime/gear/key_binding_processor.h>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/gear/chord_composer.cc"
    "  unhandled_key_connection_ = ctx->unhandled_key_notifier().connect("
    "  unhandled_key_connection_ =\n      ctx->unhandled_key_output_notifier().connect(")
  trime_replace_dependency_cmake("${root}/rime/gear/chord_composer.cc"
    [=[void ChordComposer::OnContextUpdate(Context* ctx) {
  if (ctx->IsComposing()) {]=]
    [=[void ChordComposer::OnContextUpdate(Context* ctx) {
  if (trime::IsPrivacyTransitionBoundary()) {
    raw_sequence_.clear();
    state_.Clear();
    editing_chord_ = false;
    sending_chord_ = false;
    composing_ = false;
    return;
  }
  if (ctx->IsComposing()) {]=])

  # New streaming chords retain pending physical keys even with an empty
  # composition. Clear only transient state at an editor/privacy boundary;
  # do not replay a key, commit text or change configured mappings/timing.
  trime_replace_dependency_cmake("${root}/rime/gear/streaming_chord_processor.h"
    "class Projection;"
    "class Context;\nclass Projection;")
  trime_replace_dependency_cmake("${root}/rime/gear/streaming_chord_processor.h"
    "  StreamingChordProcessor(const Ticket& ticket);"
    "  StreamingChordProcessor(const Ticket& ticket);\n  ~StreamingChordProcessor() override;")
  trime_replace_dependency_cmake("${root}/rime/gear/streaming_chord_processor.h"
    "  void ResetTracking();"
    "  void ResetTracking();\n  void OnPrivacyContextUpdate(Context* ctx);")
  trime_replace_dependency_cmake("${root}/rime/gear/streaming_chord_processor.h"
    "  bool is_replaying_ = false;"
    "  bool is_replaying_ = false;\n  connection privacy_connection_;")
  trime_replace_dependency_cmake("${root}/rime/gear/streaming_chord_processor.cc"
    [=[#include "streaming_chord_processor.h"]=]
    [=[#include "streaming_chord_processor.h"
#include "trime_privacy_guard.h"]=])
  trime_replace_dependency_cmake("${root}/rime/gear/streaming_chord_processor.cc"
    [=[StreamingChordProcessor::StreamingChordProcessor(const Ticket& ticket)
    : Processor(ticket) {
  Config* config = engine_->schema()->config();]=]
    [=[StreamingChordProcessor::StreamingChordProcessor(const Ticket& ticket)
    : Processor(ticket) {
  privacy_connection_ = engine_->context()->update_notifier().connect(
      [this](Context* ctx) { OnPrivacyContextUpdate(ctx); });
  Config* config = engine_->schema()->config();]=])
  trime_replace_dependency_cmake("${root}/rime/gear/streaming_chord_processor.cc"
    [=[void StreamingChordProcessor::ResetTracking() {
  ClearPendingPrompt();]=]
    [=[StreamingChordProcessor::~StreamingChordProcessor() {
  privacy_connection_.disconnect();
}

void StreamingChordProcessor::OnPrivacyContextUpdate(Context*) {
  if (!trime::IsPrivacyTransitionBoundary()) {
    return;
  }
  // Session::ResetPrivacyContexts already clears the Context. Do not call
  // ResetTracking/ClearPendingPrompt here: those can recursively clear it.
  last_key_event_ = {};
  pending_solo_key_ = {};
  pressed_chord_keys_.clear();
  current_chord_start_ = 0;
  is_chord_open_ = false;
  is_replaying_ = false;
}

void StreamingChordProcessor::ResetTracking() {
  ClearPendingPrompt();]=])

  # ReinterpretPagingKey retains the actual paging key/count. Clear both and
  # the recursion flag at both editor boundaries.
  trime_replace_dependency_cmake("${root}/rime/gear/key_binder.h"
    "namespace rime {\n\nstruct KeyBinding;"
    "namespace rime {\n\nclass Context;\nstruct KeyBinding;")
  trime_replace_dependency_cmake("${root}/rime/gear/key_binder.h"
    "  KeyBinder(const Ticket& ticket);"
    "  KeyBinder(const Ticket& ticket);\n  virtual ~KeyBinder();")
  trime_replace_dependency_cmake("${root}/rime/gear/key_binder.h"
    "  void PerformKeyBinding(const KeyBinding& binding);"
    "  void PerformKeyBinding(const KeyBinding& binding);\n  void OnContextUpdate(Context* ctx);")
  trime_replace_dependency_cmake("${root}/rime/gear/key_binder.h"
    "  int paging_keystroke_count_ = 0;"
    "  int paging_keystroke_count_ = 0;\n  connection update_connection_;")
  trime_replace_dependency_cmake("${root}/rime/gear/key_binder.cc"
    "#include <rime/gear/key_binder.h>"
    "#include <rime/gear/key_binder.h>\n\n#include \"trime_privacy_guard.h\"")
  include("${CMAKE_CURRENT_FUNCTION_LIST_DIR}/RimeKeyBinderCompat.cmake")
  trime_adapt_key_binder_ascii("${root}/rime/gear/key_binder.cc")
  trime_replace_dependency_cmake("${root}/rime/gear/key_binder.cc"
    [=[KeyBinder::KeyBinder(const Ticket& ticket)
    : Processor(ticket), key_bindings_(new KeyBindings) {
  LoadConfig();
}]=]
    [=[KeyBinder::KeyBinder(const Ticket& ticket)
    : Processor(ticket), key_bindings_(new KeyBindings) {
  LoadConfig();
  update_connection_ = engine_->context()->update_notifier().connect(
      [this](Context* ctx) { OnContextUpdate(ctx); });
}

KeyBinder::~KeyBinder() {
  update_connection_.disconnect();
}

void KeyBinder::OnContextUpdate(Context*) {
  if (trime::IsPrivacyTransitionBoundary()) {
    redirecting_ = false;
    last_paging_key_ = 0;
    paging_keystroke_count_ = 0;
  }
}]=])
  # Navigator caches the complete input string and its derived spans.
  trime_replace_dependency_cmake("${root}/rime/gear/navigator.h"
    "  void OnSelect(Context*);"
    "  void OnSelect(Context*);\n  void OnContextUpdate(Context*);")
  trime_replace_dependency_cmake("${root}/rime/gear/navigator.h"
    "  connection select_connection_;"
    "  connection select_connection_;\n  connection update_connection_;")
  trime_replace_dependency_cmake("${root}/rime/gear/navigator.cc"
    "#include <rime/gear/translator_commons.h>"
    "#include <rime/gear/translator_commons.h>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/gear/navigator.cc"
    [=[  select_connection_ = engine_->context()->select_notifier().connect(
      [this](Context* ctx) { OnSelect(ctx); });]=]
    [=[  select_connection_ = engine_->context()->select_notifier().connect(
      [this](Context* ctx) { OnSelect(ctx); });
  update_connection_ = engine_->context()->update_notifier().connect(
      [this](Context* ctx) { OnContextUpdate(ctx); });]=])
  trime_replace_dependency_cmake("${root}/rime/gear/navigator.cc"
    [=[Navigator::~Navigator() {
  select_connection_.disconnect();
}]=]
    [=[Navigator::~Navigator() {
  select_connection_.disconnect();
  update_connection_.disconnect();
}]=])
  trime_replace_dependency_cmake("${root}/rime/gear/navigator.cc"
    [=[void Navigator::OnSelect(Context* ctx) {
  spans_.Clear();
}]=]
    [=[void Navigator::OnSelect(Context* ctx) {
  spans_.Clear();
}

void Navigator::OnContextUpdate(Context*) {
  if (trime::IsPrivacyTransitionBoundary()) {
    input_.clear();
    spans_.Clear();
  }
}]=])

  # Paired punctuation stores a one-bit alternation value per definition.
  trime_replace_dependency_cmake("${root}/rime/gear/punctuator.h"
    "class Engine;"
    "class Context;\nclass Engine;")
  trime_replace_dependency_cmake("${root}/rime/gear/punctuator.h"
    "  Punctuator(const Ticket& ticket);"
    "  Punctuator(const Ticket& ticket);\n  virtual ~Punctuator();")
  trime_replace_dependency_cmake("${root}/rime/gear/punctuator.h"
    "  bool PairPunct(const an<ConfigItem>& definition);"
    "  bool PairPunct(const an<ConfigItem>& definition);\n  void OnContextUpdate(Context* ctx);")
  trime_replace_dependency_cmake("${root}/rime/gear/punctuator.h"
    "  map<an<ConfigItem>, int> oddness_;"
    "  map<an<ConfigItem>, int> oddness_;\n  connection update_connection_;")
  trime_replace_dependency_cmake("${root}/rime/gear/punctuator.cc"
    "#include <rime/gear/punctuator.h>"
    "#include <rime/gear/punctuator.h>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake("${root}/rime/gear/punctuator.cc"
    [=[  config_.LoadConfig(engine_);
}

static bool punctuation_is_translated]=]
    [=[  config_.LoadConfig(engine_);
  update_connection_ = engine_->context()->update_notifier().connect(
      [this](Context* ctx) { OnContextUpdate(ctx); });
}

Punctuator::~Punctuator() {
  update_connection_.disconnect();
}

void Punctuator::OnContextUpdate(Context*) {
  if (trime::IsPrivacyTransitionBoundary()) {
    oddness_.clear();
  }
}

static bool punctuation_is_translated]=])

  # ScriptTranslator can retain an unfinished phrase batch between commits.
  # It contains selected candidate text, so discard it at every editor boundary.
  trime_replace_dependency_cmake("${root}/rime/gear/script_translator.h"
    "  ScriptTranslator(const Ticket& ticket);"
    "  ScriptTranslator(const Ticket& ticket);\n  virtual ~ScriptTranslator();")
  trime_replace_dependency_cmake("${root}/rime/gear/script_translator.h"
    "  bool SaveCommitEntry(CommitEntry& commit_entry);"
    "  bool SaveCommitEntry(CommitEntry& commit_entry);\n  void OnContextUpdate(Context* ctx);")
  trime_replace_dependency_cmake("${root}/rime/gear/script_translator.h"
    "  vector<an<Phrase>> queue_;"
    "  vector<an<Phrase>> queue_;\n  connection update_connection_;")
  trime_replace_dependency_cmake("${root}/rime/gear/script_translator.cc"
    [=[  if (enable_correction_) {
    if (auto* corrector = Corrector::Require("corrector")) {
      corrector_.reset(corrector->Create(ticket));
    }
  }
}]=]
    [=[  if (enable_correction_) {
    if (auto* corrector = Corrector::Require("corrector")) {
      corrector_.reset(corrector->Create(ticket));
    }
  }
  update_connection_ = engine_->context()->update_notifier().connect(
      [this](Context* ctx) { OnContextUpdate(ctx); });
}

ScriptTranslator::~ScriptTranslator() {
  update_connection_.disconnect();
}

void ScriptTranslator::OnContextUpdate(Context*) {
  if (trime::IsPrivacyTransitionBoundary()) {
    queue_.clear();
  }
}]=])

  foreach(translator script table)
    trime_replace_dependency_cmake(
      "${root}/rime/gear/${translator}_translator.cc"
      "#include <rime/gear/translator_commons.h>"
      "#include <rime/gear/translator_commons.h>\n\n#include \"trime_privacy_guard.h\"")
    trime_replace_dependency_cmake(
      "${root}/rime/gear/${translator}_translator.cc"
      "  return !contextual_suggestions_ ? string()"
      "  return trime::IsPrivacyModeEnabled() || !contextual_suggestions_ ? string()")
  endforeach()

  # Publish only changed content so repeated configuration stays incremental.
  foreach(relative IN LISTS rime_files)
    configure_file("${root}/${relative}" "${output}/${relative}" COPYONLY)
  endforeach()
  set(root "${output}")
  get_target_property(sources rime-static SOURCES)
  foreach(relative IN LISTS rime_files)
    if(relative MATCHES "\\.cc$")
      set(matches 0)
      foreach(source IN LISTS sources)
        if(source STREQUAL relative OR source STREQUAL "${CMAKE_SOURCE_DIR}/librime/src/${relative}")
          list(REMOVE_ITEM sources "${source}")
          math(EXPR matches "${matches} + 1")
        endif()
      endforeach()
      if(NOT matches EQUAL 1)
        message(FATAL_ERROR "Re-audit Rime privacy source registration: ${relative}")
      endif()
      list(APPEND sources "${root}/${relative}")
    endif()
  endforeach()
  set_property(TARGET rime-static PROPERTY SOURCES "${sources}")
  target_sources(rime-static PRIVATE
                 "${CMAKE_SOURCE_DIR}/librime_jni/trime_privacy_guard.cc")
  target_include_directories(rime-static BEFORE PUBLIC "${root}")
  target_include_directories(rime-static PRIVATE
                             "${CMAKE_SOURCE_DIR}/librime_jni")

  # Plugin object libraries are compiled independently from rime-static. Keep
  # every translation unit on the same Context definition as the adapted core.
  foreach(plugin_target rime-lua-objs rime-octagram-objs rime-predict-objs)
    target_include_directories(${plugin_target} BEFORE PRIVATE "${root}")
  endforeach()

  # Lua exposes the raw Db interface in addition to UserDictionary. Guard its
  # mutating methods so a processor cannot bypass the core user-dictionary
  # checks while handling private input.
  set(lua_ext_original "${CMAKE_SOURCE_DIR}/librime-lua/src/types_ext.cc")
  set(lua_ext_stage
      "${CMAKE_BINARY_DIR}/trime-rime-lua-privacy-stage/types_ext.cc")
  set(lua_ext_output
      "${CMAKE_BINARY_DIR}/trime-rime-lua-privacy/types_ext.cc")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
               "${lua_ext_original}")
  configure_file("${lua_ext_original}" "${lua_ext_stage}" COPYONLY)
  trime_replace_dependency_cmake("${lua_ext_stage}"
    "#include <rime/dict/user_dictionary.h>"
    "#include <rime/dict/user_dictionary.h>\n\n#include \"trime_privacy_guard.h\"")
  trime_replace_dependency_cmake("${lua_ext_stage}"
    [=[    auto a = LuaType<an<T>>::todata(L, 1);
    string key,value;]=]
    [=[    auto a = LuaType<an<T>>::todata(L, 1);
    if (trime::IsPrivacyModeEnabled())
      return 0;
    string key,value;]=])
  trime_replace_dependency_cmake("${lua_ext_stage}"
    [=[namespace UserDbReg{
  using T = Db;
  using A = DbAccessor;]=]
    [=[namespace UserDbReg{
  using T = Db;
  using A = DbAccessor;

  class EmptyDbAccessor : public DbAccessor {
   public:
    bool Reset() override { return true; }
    bool Jump(const string&) override { return false; }
    bool GetNextRecord(string*, string*) override { return false; }
    bool exhausted() override { return true; }
  };]=])
  trime_replace_dependency_cmake("${lua_ext_stage}"
    [=[  optional<string> fetch(an<T> t, const string& key) {
    string res;
    if ( t->Fetch(key,&res) )
      return res;
    return {};
  }
]=]
    [=[  bool open(an<T> t) {
    return !trime::IsPrivacyModeEnabled() && t->Open();
  }

  an<A> query(an<T> t, const string& key) {
    if (trime::IsPrivacyModeEnabled())
      return New<EmptyDbAccessor>();
    return t->Query(key);
  }

  optional<string> fetch(an<T> t, const string& key) {
    if (trime::IsPrivacyModeEnabled())
      return {};
    string res;
    if ( t->Fetch(key,&res) )
      return res;
    return {};
  }

  bool update(an<T> t, const string& key, const string& value) {
    return !trime::IsPrivacyModeEnabled() && t->Update(key, value);
  }

  bool erase(an<T> t, const string& key) {
    return !trime::IsPrivacyModeEnabled() && t->Erase(key);
  }
]=])
  trime_replace_dependency_cmake("${lua_ext_stage}"
    [=[  static const luaL_Reg methods[] = {
    {"open", WRAPMEM(T, Open)},]=]
    [=[  static const luaL_Reg methods[] = {
    {"open", WRAP(open)},]=])
  trime_replace_dependency_cmake("${lua_ext_stage}"
    [=[    {"query", WRAPMEM(T, Query)}, // query(prefix_key) return DbAccessor]=]
    [=[    {"query", WRAP(query)}, // query(prefix_key) return DbAccessor]=])
  trime_replace_dependency_cmake("${lua_ext_stage}"
    [=[    {"update", WRAPMEM(T, Update)}, // update(key,value) return bool
    {"erase", WRAPMEM(T, Erase)}, // erase(key) return bool]=]
    [=[    {"update", WRAP(update)}, // update(key,value) return bool
    {"erase", WRAP(erase)}, // erase(key) return bool]=])
  configure_file("${lua_ext_stage}" "${lua_ext_output}" COPYONLY)
  get_target_property(lua_sources rime-lua-objs SOURCES)
  set(lua_ext_matches 0)
  foreach(source IN LISTS lua_sources)
    if(source STREQUAL "src/types_ext.cc" OR
       source STREQUAL "${CMAKE_SOURCE_DIR}/librime-lua/src/types_ext.cc" OR
       source STREQUAL "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/src/types_ext.cc")
      list(REMOVE_ITEM lua_sources "${source}")
      math(EXPR lua_ext_matches "${lua_ext_matches} + 1")
    endif()
  endforeach()
  if(NOT lua_ext_matches EQUAL 1)
    message(FATAL_ERROR "Re-audit Rime Lua privacy source registration: src/types_ext.cc")
  endif()
  list(APPEND lua_sources "${lua_ext_output}")
  set_property(TARGET rime-lua-objs PROPERTY SOURCES "${lua_sources}")

  include("${CMAKE_CURRENT_FUNCTION_LIST_DIR}/RimePredictCompat.cmake")
  trime_adapt_rime_predict()
endfunction()
