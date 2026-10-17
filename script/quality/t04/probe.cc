// SPDX-License-Identifier: GPL-3.0-or-later
// Bound to the APK's Rime C++ ABI using its existing native compile database.
#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/menu.h>
#include <rime/schema.h>
#include <rime/service.h>
#include <rime/lever/user_dict_manager.h>
#include <rime_api.h>
#undef Bool
#include <rapidjson/document.h>
#include <rapidjson/istreamwrapper.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>
#include <dlfcn.h>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>

using Json = rapidjson::Value;
using Alloc = rapidjson::Document::AllocatorType;
static void check(bool ok, const char* message) {
  if (!ok) throw std::runtime_error(message);
}
static Json str(const std::string& s, Alloc& a) { return Json(s.c_str(), a); }
static double ms(std::chrono::steady_clock::time_point start) {
  return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count();
}
static long pss() {
  std::ifstream file("/proc/self/smaps_rollup");
  if (!file) file.open("/proc/self/smaps");
  std::string line;
  long sum = 0;
  while (std::getline(file, line)) {
    if (line.rfind("Pss:", 0) == 0) sum += std::stol(line.substr(4));
  }
  return sum ? sum : -1;
}
static void opencc(const std::string& user) {
  using Convert = void (*)(const std::string&, const std::string&, const std::string&, const std::string&);
  auto convert = reinterpret_cast<Convert>(dlsym(RTLD_DEFAULT,
      "_ZN6opencc17ConvertDictionaryERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEES8_S8_S8_"));
  check(convert, "Unsupported APK OpenCC ABI");
  for (const auto& entry : std::filesystem::directory_iterator(user + "/opencc")) {
    if (entry.path().extension() != ".txt") continue;
    auto out = entry.path(); out.replace_extension(".ocd2");
    if (!std::filesystem::exists(out)) convert(entry.path().string(), out.string(), "text", "ocd2");
    check(std::filesystem::file_size(out) > 0, "OpenCC conversion failed");
  }
}
static std::string encode(std::string code, bool digits) {
  std::string result;
  const std::string alphabet = "abcdefghijklmnopqrstuvwxyz";
  const std::string keys = "22233344455566677778889999";
  for (char c : code) {
    if (c == ' ') { if (!digits) result += '\''; continue; }
    if (c == '\'') { result += c; continue; }
    auto i = alphabet.find(c);
    check(i != std::string::npos, "Invalid corpus spelling");
    result += digits ? keys[i] : c;
  }
  return result;
}
static void input(RimeApi* api, RimeSessionId id, const std::string& keys) {
  api->clear_composition(id);
  for (char c : keys) {
    check(api->process_key(id, c, 0), "Unhandled input");
    RIME_STRUCT(RimeCommit, commit);
    if (api->get_commit(id, &commit)) {
      api->free_commit(&commit);
      throw std::runtime_error("Unexpected editor commit");
    }
  }
}
static rime::an<rime::Candidate> candidate(rime::Context* ctx, int i) {
  auto& comp = ctx->composition();
  if (comp.empty() || !comp.back().menu) return nullptr;
  return comp.back().menu->GetCandidateAt(i);
}
static Json choices(rime::Context* ctx, int limit, Alloc& a) {
  Json rows(rapidjson::kArrayType);
  for (int i = 0; i < limit; ++i) {
    auto c = candidate(ctx, i);
    if (!c) break;
    Json row(rapidjson::kObjectType);
    row.AddMember("text", str(c->text(), a), a);
    row.AddMember("end", static_cast<unsigned>(c->end()), a);
    row.AddMember("full", c->end() == ctx->input().size(), a);
    row.AddMember("quality", c->quality(), a);
    rows.PushBack(row, a);
  }
  return rows;
}
static Json cost(RimeApi* api, RimeSessionId id, const std::string& goal, int limit, Alloc& a) {
  auto ctx = rime::Service::instance().GetSession(id)->context();
  std::string matched;
  Json steps(rapidjson::kArrayType);
  bool complete = false;
  int nonfirst = 0;
  for (int step = 0; step < 64; ++step) {
    int best = -1;
    std::string longest;
    size_t end = 0;
    for (int i = 0; i < limit; ++i) {
      auto c = candidate(ctx, i);
      if (!c) break;
      const auto& word = c->text();
      if (!word.empty() && goal.compare(matched.size(), word.size(), word) == 0 &&
          word.size() > longest.size() &&
          ((matched.size() + word.size() == goal.size()) == (c->end() == ctx->input().size()))) {
        best = i; longest = word; end = c->end();
      }
    }
    if (best < 0) break;
    nonfirst += best != 0;
    Json selection(rapidjson::kObjectType);
    selection.AddMember("index", best, a);
    selection.AddMember("text", str(longest, a), a);
    selection.AddMember("end", static_cast<unsigned>(end), a);
    steps.PushBack(selection, a);
    matched += longest;
    if (matched == goal) { complete = true; break; }
    check(api->select_candidate(id, best), "Partial selection failed");
    RIME_STRUCT(RimeCommit, commit);
    if (api->get_commit(id, &commit)) {
      api->free_commit(&commit);
      throw std::runtime_error("Cost replay unexpectedly committed and contaminated learning");
    }
  }
  Json result(rapidjson::kObjectType);
  result.AddMember("complete", complete, a);
  result.AddMember("nonfirst_selections", nonfirst, a);
  // Each intermediate selection counts once; only a non-first final choice is extra.
  result.AddMember("extra_selections", complete ? static_cast<int>(steps.Size()) - 1 +
                   (steps[steps.Size() - 1]["index"].GetInt() != 0) : -1, a);
  result.AddMember("steps", steps, a);
  api->clear_composition(id);
  return result;
}

int main(int argc, char** argv) {
  auto api = rime_get_api();
  bool initialized = false;
  try {
    check(argc == 7, "Usage: probe SHARED USER CORPUS deploy|measure|train|export OWNER PROFILE");
    std::ifstream owner(std::string(argv[2]) + "/../owner.txt");
    std::string marker; std::getline(owner, marker);
    check(marker == argv[5] && !marker.empty(), "Not an owned test directory");
    std::ifstream source(argv[3]);
    rapidjson::IStreamWrapper stream(source);
    rapidjson::Document corpus; corpus.ParseStream(stream);
    check(!corpus.HasParseError() && corpus.IsObject(), "Invalid corpus");
    const std::string phase(argv[4]);
    check(phase == "deploy" || phase == "measure" || phase == "train" || phase == "export", "Invalid phase");
    auto start = std::chrono::steady_clock::now();
    opencc(argv[2]);
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = argv[1]; traits.user_data_dir = argv[2];
    traits.app_name = "rime.trime_t04"; traits.log_dir = ""; traits.min_log_level = 2;
    api->setup(&traits); api->initialize(&traits); initialized = true;
    if (phase == "deploy") { api->start_maintenance(true); api->join_maintenance_thread(); }
    auto id = api->create_session();
    check(api->select_schema(id, corpus["schema"].GetString()), "Schema deployment failed");
    rapidjson::Document result(rapidjson::kObjectType);
    auto& a = result.GetAllocator();
    result.AddMember("phase", str(phase, a), a);
    result.AddMember("owner", str(marker, a), a);
    result.AddMember("profile", str(argv[6], a), a);
    result.AddMember("rime_version", str(api->get_version(), a), a);
    result.AddMember("startup_or_deploy_ms", ms(start), a);
    Json config(rapidjson::kObjectType);
    auto schema = rime::Service::instance().GetSession(id)->schema()->config();
    for (const char* key : {"translator/dictionary", "translator/user_dict", "translator/prism",
                            "translator/enable_user_dict", "translator/enable_sentence",
                            "translator/enable_completion", "translator/initial_quality",
                            "translator/max_sentences", "translator/sentence_cutoff_threshold",
                            "grammar/language", "engine/filters"}) {
      std::string value;
      if (schema->GetString(key, &value)) config.AddMember(str(key, a), str(value, a), a);
      else config.AddMember(str(key, a), Json(), a);
    }
    result.AddMember("effective_config", config, a);
    Json records(rapidjson::kArrayType);
    long peak = pss();
    if (phase == "measure") {
      int limit = corpus["candidate_limit"].GetInt();
      check(limit > 0 && limit <= 200, "Invalid candidate limit");
      for (const auto& test : corpus["cases"].GetArray()) {
        for (bool simplified : {false, true}) {
          api->set_option(id, "simplification", simplified);
          check(api->get_option(id, "simplification") == simplified, "Option mismatch");
          std::string keys = encode(test[4].GetString(), true);
          auto begin = std::chrono::steady_clock::now();
          input(api, id, keys);
          auto ctx = rime::Service::instance().GetSession(id)->context();
          auto list = choices(ctx, limit, a);
          Json row(rapidjson::kObjectType);
          row.AddMember("id", str(test[0].GetString(), a), a);
          row.AddMember("simplified", simplified, a);
          row.AddMember("input", str(keys, a), a);
          row.AddMember("preedit", str(ctx->GetPreedit().text, a), a);
          row.AddMember("query_ms", ms(begin), a);
          row.AddMember("candidates", list, a);
          row.AddMember("cost", cost(api, id, test[simplified ? 5 : 6].GetString(), limit, a), a);
          peak = std::max(peak, pss());
          records.PushBack(row, a);
        }
      }
    } else if (phase == "train") {
      api->set_option(id, "simplification", false);
      for (const auto& step : corpus["learning"].GetArray()) {
        for (int n = 0; n < step["repeat"].GetInt(); ++n) {
          input(api, id, encode(step["pinyin"].GetString(), false));
          auto ctx = rime::Service::instance().GetSession(id)->context();
          int found = -1;
          for (int i = 0; i < 200; ++i) {
            auto c = candidate(ctx, i);
            if (!c) break;
            if (c->text() == step["traditional"].GetString() && c->end() == ctx->input().size()) { found = i; break; }
          }
          if (found < 0) throw std::runtime_error(std::string("Learning target unavailable in first 200: ") +
                                                step["traditional"].GetString());
          check(api->select_candidate(id, found), "Learning selection failed");
          RIME_STRUCT(RimeCommit, commit);
          check(api->get_commit(id, &commit), "Learning did not commit");
          std::string text = commit.text; api->free_commit(&commit);
          check(text == step["traditional"].GetString(), "Learning committed wrong text");
          records.PushBack(str(text, a), a);
        }
      }
    }
    api->clear_composition(id); api->destroy_session(id);
    if (phase == "export") {
      rime::UserDictManager manager(&rime::Service::instance().deployer());
      auto count = manager.Export("luna_pinyin", rime::path(std::string(argv[2]) + "/learning.tsv"));
      result.AddMember("exported_entries", count, a);
    }
    result.AddMember("sampled_peak_pss_kib", peak, a);
    result.AddMember("records", records, a);
    api->finalize(); initialized = false;
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer); result.Accept(writer);
    std::cout << buffer.GetString() << '\n';
    return 0;
  } catch (const std::exception& error) {
    if (initialized) api->finalize();
    std::cerr << "T04 failed: " << error.what() << '\n';
    return 1;
  }
}
