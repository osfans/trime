// SPDX-License-Identifier: GPL-3.0-or-later
// Exercises the actual Trime overlays against host librime, not a second
// implementation of the prediction state machine. Android/T9/JNI is separate.
#include "predictor.h"
#include "predict_engine.h"
#include "predict_translator.h"
#include "model_integrity.h"
#include "prediction_policy.h"
#include "prediction_key_policy.h"
#include "trime_prediction_guard.h"
#include "trime_privacy_guard.h"
#include "candidate_ranking.h"
#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/engine.h>
#include <rime/key_event.h>
#include <rime/menu.h>
#include <rime/schema.h>
#include <rime/translation.h>
#include <rime_api.h>
#include <glog/logging.h>
#include <fstream>
#include <dlfcn.h>
#include <sys/stat.h>
#include <iostream>
#include <map>
#include <stdexcept>

namespace {
int checks = 0;
void Check(bool valid, const char* message) {
  if (!valid) throw std::runtime_error(message);
  ++checks;
}

class TestEngine final : public rime::Engine {
 public:
  TestEngine() = default;
};

bool HasPrediction(rime::Context* context) {
  return !context->composition().empty() &&
         context->composition().back().HasTag("prediction");
}

void AddCandidate(rime::Context* context, const std::string& type,
                  const std::string& text) {
  context->Clear();
  context->set_input("input");
  rime::Segment segment(0, 5);
  segment.status = rime::Segment::kSelected;
  segment.menu = rime::New<rime::Menu>();
  auto translation = rime::New<rime::FifoTranslation>();
  translation->Append(rime::New<rime::SimpleCandidate>(type, 0, 5, text));
  segment.menu->AddTranslation(translation);
  context->composition().AddSegment(segment);
  Check(context->GetCommitText() == text, "fixture selected commit text mismatch");
}

std::vector<std::string> Suggestions(rime::Context* context,
                                    rime::PredictEngine* engine) {
  if (!HasPrediction(context)) return {};
  std::vector<std::string> result;
  auto translation = engine->Translate(context->composition().back());
  if (!translation) return result;
  while (!translation->exhausted()) {
    result.push_back(translation->Peek()->text());
    translation->Next();
  }
  return result;
}

void CommitWord(rime::Context* context, const std::string& type,
                const std::string& text) {
  AddCandidate(context, type, text);
  Check(context->Commit(), "fixture commit failed");
}

void CommitPrediction(rime::Context* context, const std::string& text,
                      bool filtered = false) {
  Check(HasPrediction(context), "prediction selection requires current segment");
  auto& segment = context->composition().back();
  segment.status = rime::Segment::kSelected;
  segment.menu = rime::New<rime::Menu>();
  auto translation = rime::New<rime::FifoTranslation>();
  rime::an<rime::Candidate> candidate = rime::New<rime::SimpleCandidate>(
      "prediction", segment.end, segment.end, text);
  if (filtered) {
    candidate = rime::New<rime::ShadowCandidate>(candidate, "simplified", text);
    candidate = rime::New<rime::UniquifiedCandidate>(candidate, "uniquified", text);
  }
  translation->Append(candidate);
  segment.menu->AddTranslation(translation);
  Check(context->GetCommitText() == text, "prediction fixture selected text");
  Check(context->Commit(), "prediction fixture commit");
}

void Write(const rime::path& file, const std::vector<char>& image) {
  std::ofstream stream(file, std::ios::binary);
  stream.write(image.data(), static_cast<std::streamsize>(image.size()));
  Check(static_cast<bool>(stream), "fixture write failed");
}

#include "ranking_boundary_test.h"

void Run(const rime::path& model, const rime::path& scratch,
         RimeModule* linked_module) {
  using namespace trime::prediction;
  Check(ModelSha256(nullptr, 0) ==
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "empty SHA256");
  const std::string abc = "abc";
  Check(ModelSha256(reinterpret_cast<const unsigned char*>(abc.data()), abc.size()) ==
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "abc SHA256");
  const std::string padding_boundary(56, 'a');
  Check(ModelSha256(reinterpret_cast<const unsigned char*>(padding_boundary.data()),
                    padding_boundary.size()) ==
        "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a", "padding SHA256");
  Check(CandidateLimit(0) == 5 && CandidateLimit(-1) == 5 &&
        CandidateLimit(1) == 1 && CandidateLimit(1000) == 8, "candidate hard limits");
  Check(IterationLimit(0) == 1 && IterationLimit(-1) == 1 &&
        IterationLimit(2) == 2 && IterationLimit(1000) == 2, "iteration hard limits");
  std::size_t offset = 0;
  Check(!ContainsRange(10, 11, 0) && !ContainsRange(10, 8, 3) &&
        ContainsRange(10, 8, 2), "range bounds");
  Check(!ContainsRelativeRange(10, 4, -5, 1, &offset) &&
        !ContainsRelativeRange(10, 4, 0, 1, &offset) &&
        ContainsRelativeRange(10, 4, -3, 9, &offset) && offset == 1,
        "relative bounds");

  auto db = rime::New<rime::PredictDb>(model);
  Check(db->Load(), "bundled model load failed");
  const auto* candidates = db->Lookup("你好");
  Check(candidates && candidates->size == 10, "ten-model-candidate fixture");
  Check(db->GetEntryText(candidates->at[0]) == "世界", "expected next word");
  Check(db->Load() && db->Lookup("你好") == candidates, "idempotent stable candidate pointers");
  Check(!db->Lookup("") && !db->Lookup(std::string(kMaximumQueryBytes + 1, 'x')),
        "query byte limit");

  std::ifstream input(model, std::ios::binary);
  const std::vector<char> bytes((std::istreambuf_iterator<char>(input)), {});
  for (std::size_t index : {std::size_t(0), std::size_t(36), std::size_t(80), bytes.size()-1}) {
    auto damaged = bytes;
    damaged[index] ^= 1;
    const auto file = scratch / ("damaged-" + std::to_string(index) + ".db");
    Write(file, damaged);
    rime::PredictDb invalid(file);
    Check(!invalid.Load() && !invalid.Lookup("你好"), "corrupt model must fail safely");
  }
  for (std::size_t size : {std::size_t(0), bytes.size()-1, bytes.size()+1,
                           kMaximumModelBytes+1}) {
    const auto file = scratch / ("sized-" + std::to_string(size) + ".db");
    Write(file, std::vector<char>(size));
    rime::PredictDb invalid(file);
    Check(!invalid.Load(), "invalid model size accepted");
  }
  rime::PredictDb missing(scratch / "missing.db");
  Check(!missing.Load(), "missing model accepted");
  const auto replaced = scratch / "replaced.db";
  Write(replaced, bytes);
  rime::PredictDb stable(replaced);
  Check(stable.Load(), "snapshot model load");
  const auto* before = stable.Lookup("你好");
  Write(replaced, std::vector<char>(bytes.size()));
  Check(stable.Load() && stable.Lookup("你好") == before &&
        stable.GetEntryText(before->at[0]) == "世界", "loaded snapshot changed with user file");

  // A FIFO has no writer: opening it would block this test until its timeout.
  // This proves the first disabled commit does not read or hash the model,
  // even if a schema enables its local prediction option.
  const auto disabled_file = scratch / "disabled-model.db";
  Check(mkfifo(disabled_file.c_str(), 0600) == 0, "default-off FIFO fixture");
  TestEngine disabled_engine;
  auto* disabled_context = disabled_engine.context();
  disabled_context->set_option("prediction", true);
  auto disabled_db = rime::New<rime::PredictDb>(disabled_file);
  auto disabled_predict = rime::New<rime::PredictEngine>(disabled_db, 1, 5);
  rime::Predictor disabled_processor(rime::Ticket(&disabled_engine), disabled_predict);
  Check(!trime::IsPredictionRequested(), "process prediction should default off");
  CommitWord(disabled_context, "phrase", "你好");
  Check(!HasPrediction(disabled_context) &&
        !disabled_predict->Predict(disabled_context, "你好"),
        "default-off schema bypassed global gate or read model");
  Check(disabled_processor.ProcessKeyEvent(rime::KeyEvent('2', 0)) == rime::kNoop,
        "default-off processor swallowed input");
  disabled_context->PushInput('2');
  Check(disabled_context->input() == "2", "default-off base input failed");
  trime::SetPredictionRequested(true);
  // The same FIFO now takes the enabled, real commit path. O_NONBLOCK and
  // fstat on its opened descriptor must reject it without waiting for a writer.
  Check(!disabled_db->Load(), "enabled FIFO model did not fail safely");
  CommitWord(disabled_context, "phrase", "你好");
  Check(!HasPrediction(disabled_context), "enabled FIFO created a prediction");
  Check(disabled_processor.ProcessKeyEvent(rime::KeyEvent('2', 0)) == rime::kNoop,
        "enabled FIFO swallowed base input");
  disabled_context->PushInput('2');
  Check(disabled_context->input() == "2", "enabled FIFO blocked base input");
  rime::PredictDb directory(scratch);
  Check(!directory.Load() && !directory.Lookup("你好"), "directory model accepted");
  rime::PredictDb device(rime::path{"/dev/null"});
  Check(!device.Load() && !device.Lookup("你好"), "nonregular device model accepted");
  // Exercise the actual plugin module translation unit and registered factory,
  // whose quoted includes must use the same overlay headers as all other TUs.
  auto* module = linked_module;
  Check(module && module->initialize, "actual predict module was not linked");
  module->initialize();
  TestEngine registered_engine;
  auto* registered_context = registered_engine.context();
  registered_context->set_option("prediction", true);
  rime::PredictEngineComponent factory_probe;
  auto factory_engine = factory_probe.GetInstance(rime::Ticket(&registered_engine));
  Check(factory_engine != nullptr, "factory failed to load the checked model");
  auto* registered_factory = rime::Processor::Require("predictor");
  Check(registered_factory != nullptr, "actual module processor registration");
  std::unique_ptr<rime::Processor> registered_processor(
      registered_factory->Create(rime::Ticket(&registered_engine)));
  CommitWord(registered_context, "phrase", "你好");
  Check(HasPrediction(registered_context), "registered factory failed actual prediction");
  auto* translator_factory = rime::Translator::Require("predict_translator");
  Check(translator_factory != nullptr, "actual module translator registration");
  std::unique_ptr<rime::Translator> registered_translator(
      translator_factory->Create(rime::Ticket(&registered_engine)));
  auto registered_translation = registered_translator->Query(
      "", registered_context->composition().back());
  std::size_t registered_count = 0;
  while (registered_translation && !registered_translation->exhausted()) {
    ++registered_count;
    registered_translation->Next();
  }
  Check(registered_count == 5, "registered translator candidate cap");
  TestEngine engine;
  auto* context = engine.context();
  context->set_option("prediction", true);
  auto predict = rime::New<rime::PredictEngine>(db, 1, 5);
  rime::Predictor processor(rime::Ticket(&engine), predict);
  rime::PredictTranslator translator(rime::Ticket(&engine), predict);
  CommitWord(context, "phrase", "你好");
  Check(HasPrediction(context), "normal commit did not predict");
  Check(Suggestions(context, predict.get()).size() == 5, "default candidate count not five");
  Check(translator.Query("", context->composition().back()) != nullptr,
        "prediction translator rejected valid placeholder");
  Check(!translator.Query("pinyin", context->composition().back()),
        "unfinished pinyin confused with next-word prediction");
  context->update_notifier()(context);
  Check(HasPrediction(context), "harmless update removed prediction");
  context->Clear();
  Check(!HasPrediction(context), "clear replayed old commit");

  for (int key : std::vector<int>{'2', '9', 'a', ' ', '.', '/', '@', XK_Return, XK_BackSpace, XK_Delete, XK_Escape}) {
    CommitWord(context, "phrase", "你好");
    Check(processor.ProcessKeyEvent(rime::KeyEvent(key, 0)) == rime::kNoop,
          "real editing key was swallowed");
    Check(!HasPrediction(context) && predict->query().empty(),
          "real editing key retained prediction");
    context->PushInput('2');
    Check(context->input() == "2", "typing after dismiss did not start new input");
    context->Clear();
  }
  CommitWord(context, "phrase", "你好");
  Check(processor.ProcessKeyEvent(rime::KeyEvent(XK_Down, 0)) == rime::kNoop &&
        HasPrediction(context), "navigation unexpectedly committed or cleared prediction");
  Check(trime::prediction::ShouldDismissForKey(rime::KeyEvent(XK_Shift_R, 0)) &&
        trime::prediction::ShouldDismissForKey(rime::KeyEvent(XK_Shift_R, kReleaseMask)),
        "ASCII Shift press/release must dismiss before ascii_composer");
  context->set_option("prediction", false);
  Check(!HasPrediction(context) && predict->query().empty(), "switch-off retained prediction");
  context->set_option("prediction", true);
  Check(!HasPrediction(context), "switch-on replayed old commit");
  CommitWord(context, "phrase", "你好");
  context->set_option("ascii_mode", true);
  Check(!HasPrediction(context) && !context->get_option("prediction"),
        "ASCII switch retained prediction processing");
  context->set_option("ascii_mode", false);
  Check(!HasPrediction(context) && context->get_option("prediction"),
        "ASCII exit replayed context or failed to restore preference");
  CommitWord(context, "phrase", "你好");
  Check(HasPrediction(context), "fresh word after ASCII exit did not predict");
  context->Clear();

  for (const char* type : {"raw", "punct", "thru"}) {
    CommitWord(context, type, "你好");
    Check(!HasPrediction(context), "non-word commit caused prediction");
  }
  CommitWord(context, "phrase", "Android 16");
  Check(!HasPrediction(context), "ASCII context caused prediction");
  CommitWord(context, "phrase", "你好");
  trime::SetPrivacyModeEnabled(true);
  context->update_notifier()(context);
  Check(!HasPrediction(context) && predict->query().empty(), "privacy retained prediction");
  CommitWord(context, "phrase", "你好");
  Check(!HasPrediction(context), "privacy processed prediction");
  trime::SetPrivacyModeEnabled(false);
  context->update_notifier()(context);
  Check(!HasPrediction(context), "privacy exit replayed context");
  CommitWord(context, "phrase", "你好");
  {
    trime::PredictionTransitionBoundary boundary;
    context->update_notifier()(context);
  }
  Check(!HasPrediction(context), "editor boundary retained prediction");
  context->update_notifier()(context);
  Check(!HasPrediction(context), "editor boundary replayed context");
  CommitWord(context, "phrase", "你好");
  trime::SetPredictionRequested(false);
  context->update_notifier()(context);
  Check(!HasPrediction(context), "frontend disable was bypassed by schema");
  trime::SetPredictionRequested(true);

  CommitWord(context, "phrase", "你好");
  CommitPrediction(context, "世界", true);
  Check(!HasPrediction(context), "one-iteration cap failed");
  // A normal commit starts a new independent chain.
  CommitWord(context, "phrase", "你好");
  Check(HasPrediction(context), "new word failed to reset chain budget");
  context->Clear();
  TestEngine engine2;
  auto* context2 = engine2.context();
  context2->set_option("prediction", true);
  auto bounded = rime::New<rime::PredictEngine>(db, 1000, 1000);
  rime::Predictor processor2(rime::Ticket(&engine2), bounded);
  CommitWord(context2, "phrase", "你好");
  Check(Suggestions(context2, bounded.get()).size() == 8, "hard candidate cap/off-by-one");
  CommitPrediction(context2, "世界", true);
  Check(HasPrediction(context2), "second-iteration allowance failed");
  CommitPrediction(context2, "你好", true);
  Check(!HasPrediction(context2), "hard iteration cap failed");
  TestEngine no_model_engine;
  rime::Predictor no_model(rime::Ticket(&no_model_engine), nullptr);
  no_model_engine.context()->set_option("prediction", true);
  Check(no_model.ProcessKeyEvent(rime::KeyEvent('2', 0)) == rime::kNoop,
        "missing model swallowed base input");
  no_model_engine.context()->PushInput('2');
  Check(no_model_engine.context()->input() == "2", "missing model blocked base input");
}
}  // namespace

int main(int argc, char** argv) {
  if (argc != 3) return 2;
  google::InitGoogleLogging(argv[0]);
  FLAGS_minloglevel = google::GLOG_FATAL;
  auto* api = rime_get_api();
  // The host distribution loads installed plugins during initialize(). Hold
  // the linked module before that so its same-name system plugin cannot turn
  // this test into an accidental test of /usr/lib/rime-plugins/*.so.
  auto* linked_module = RimeFindModule("predict");
  Dl_info origin{};
  if (!linked_module || !dladdr(reinterpret_cast<void*>(linked_module->initialize), &origin) ||
      !origin.dli_fname || !std::filesystem::equivalent(argv[0], origin.dli_fname)) {
    std::cerr << "linked prediction module is not the current executable\n";
    return 1;
  }
  RimeTraits traits{};
  RIME_STRUCT_INIT(RimeTraits, traits);
  traits.shared_data_dir = argv[2];
  traits.user_data_dir = argv[2];
  traits.min_log_level = 3;
  traits.log_dir = "";
  api->setup(&traits);
  api->initialize(&traits);
  try {
    Run(rime::path{argv[1]}, rime::path{argv[2]}, linked_module);
    CheckOrdinaryRankingPredictionBoundaries();
    api->finalize();
    std::cout << "PASS " << checks << " actual native prediction checks\n";
    return 0;
  } catch (const std::exception& error) {
    api->finalize();
    std::cerr << error.what() << '\n';
    return 1;
  }
}
