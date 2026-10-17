// SPDX-License-Identifier: GPL-3.0-or-later
// A separate process owns each isolated Rime session. No Android app data is used.
#include <dlfcn.h>
#include <rime_api.h>
// The Rime C API macro conflicts with RapidJSON's Bool handler method.
#undef Bool
#include <rapidjson/document.h>
#include <rapidjson/istreamwrapper.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>

#include <chrono>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

using Json = rapidjson::Value;
using Allocator = rapidjson::Document::AllocatorType;

static Json text(const char* value, Allocator& a) {
  return Json(value ? value : "", a);
}

static void require(bool condition, const char* message) {
  if (!condition) throw std::runtime_error(message);
}

static void prepare_opencc(void* lib, const std::string& user) {
  // Match OpenCCDictManager's conversion, using the same library and C++ ABI.
#ifdef __ANDROID__
  const char* symbol = "_ZN6opencc17ConvertDictionaryERKNSt6__ndk112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEES8_S8_S8_";
#else
  const char* symbol = "_ZN6opencc17ConvertDictionaryERKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEES7_S7_S7_";
#endif
  using Convert = void (*)(const std::string&, const std::string&,
                          const std::string&, const std::string&);
  auto convert = reinterpret_cast<Convert>(dlsym(lib, symbol));
  require(convert, "APK OpenCC converter ABI is unsupported");
  const auto dir = std::filesystem::path(user) / "opencc";
  require(std::filesystem::is_directory(dir), "Missing isolated OpenCC seed");
  for (const auto& entry : std::filesystem::directory_iterator(dir)) {
    if (entry.path().extension() != ".txt") continue;
    auto output = entry.path();
    output.replace_extension(".ocd2");
    if (!std::filesystem::exists(output)) {
      convert(entry.path().string(), output.string(), "text", "ocd2");
    }
    require(std::filesystem::file_size(output) > 0, "Empty compiled OpenCC dictionary");
  }
  auto open = reinterpret_cast<void* (*)(const char*)>(dlsym(lib, "opencc_open"));
  auto close = reinterpret_cast<int (*)(void*)>(dlsym(lib, "opencc_close"));
  auto line = reinterpret_cast<char* (*)(void*, const char*, size_t)>(dlsym(lib, "opencc_convert_utf8"));
  auto free_line = reinterpret_cast<void (*)(char*)>(dlsym(lib, "opencc_convert_utf8_free"));
  require(open && close && line && free_line, "Missing OpenCC C API");
  void* converter = open((dir / "t2s.json").c_str());
  require(converter && converter != reinterpret_cast<void*>(-1), "Cannot open deployed t2s.json");
  char* converted = line(converter, u8"\u6f22\u5b57", static_cast<size_t>(-1));
  bool correct = converted && std::strcmp(converted, u8"\u6c49\u5b57") == 0;
  if (converted) free_line(converted);
  close(converter);
  require(correct, "OpenCC traditional-to-simplified validation failed");
}

static Json candidates(RimeApi* api, RimeSessionId session, int limit,
                       Allocator& a) {
  Json result(rapidjson::kArrayType);
  RimeCandidateListIterator it{};
  if (api->candidate_list_begin(session, &it)) {
    while (result.Size() < static_cast<unsigned>(limit) &&
           api->candidate_list_next(&it)) {
      Json item(rapidjson::kObjectType);
      item.AddMember("text", text(it.candidate.text, a), a);
      item.AddMember("comment", text(it.candidate.comment, a), a);
      result.PushBack(item, a);
    }
    api->candidate_list_end(&it);
  }
  return result;
}

static void input(RimeApi* api, RimeSessionId session, const char* keys) {
  api->clear_composition(session);
  for (const unsigned char* p = reinterpret_cast<const unsigned char*>(keys);
       *p; ++p) {
    require((*p >= '2' && *p <= '9') || *p == '\'', "Invalid T9 test input");
    require(api->process_key(session, *p, 0), "Rime did not handle a test key");
    RIME_STRUCT(RimeCommit, commit);
    if (api->get_commit(session, &commit)) {
      api->free_commit(&commit);
      throw std::runtime_error("Unexpected commit while composing");
    }
  }
}

int main(int argc, char** argv) {
  RimeApi* api = nullptr;
  RimeSessionId session = 0;
  bool initialized = false;
  try {
    require(argc == 7, "Usage: probe LIB SHARED USER CORPUS measure|train RUN_ID");
    std::string phase(argv[5]);
    require(phase == "measure" || phase == "train", "Invalid phase");
    std::ifstream marker(std::string(argv[3]) + "/../owner.txt");
    std::string owner;
    std::getline(marker, owner);
    require(owner == argv[6] && !owner.empty(), "Missing isolated directory owner");
    std::ifstream stream(argv[4]);
    require(stream.good(), "Cannot open corpus");
    rapidjson::IStreamWrapper wrapper(stream);
    rapidjson::Document corpus;
    corpus.ParseStream(wrapper);
    require(!corpus.HasParseError() && corpus.IsObject(), "Invalid corpus JSON");
    require(corpus.HasMember("schema") && corpus["schema"].IsString() &&
                corpus.HasMember("options") && corpus["options"].IsObject() &&
                corpus.HasMember("cases") && corpus["cases"].IsArray() &&
                corpus.HasMember("learning") && corpus["learning"].IsArray() &&
                corpus.HasMember("candidate_limit") && corpus["candidate_limit"].IsInt(),
            "Invalid corpus structure");
    int limit = corpus["candidate_limit"].GetInt();
    require(limit > 0 && limit <= 200, "Invalid candidate limit");
    void* lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!lib) throw std::runtime_error(dlerror());
    prepare_opencc(lib, argv[3]);
    auto get_api = reinterpret_cast<RimeApi* (*)()>(dlsym(lib, "rime_get_api"));
    require(get_api, "Library does not export rime_get_api");
    api = get_api();
    require(RIME_PROVIDED(api, candidate_list_end) &&
                RIME_PROVIDED(api, get_version), "Unsupported Rime API");
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = argv[2];
    traits.user_data_dir = argv[3];
    traits.app_name = "rime.trime_baseline";
    traits.distribution_name = "Trime baseline probe";
    traits.distribution_code_name = "trime_baseline";
    traits.distribution_version = "1";
    traits.log_dir = "";
    traits.min_log_level = 2;
    api->setup(&traits);
    api->initialize(&traits);
    initialized = true;
    api->start_maintenance(true);
    api->join_maintenance_thread();
    session = api->create_session();
    require(session != 0, "Cannot create Rime session");
    require(api->select_schema(session, corpus["schema"].GetString()),
            "Cannot select test schema after deployment");
    for (auto it = corpus["options"].MemberBegin();
         it != corpus["options"].MemberEnd(); ++it) {
      require(it->value.IsBool(), "Option must be boolean");
      api->set_option(session, it->name.GetString(), it->value.GetBool());
    }
    char selected[256]{};
    require(api->get_current_schema(session, selected, sizeof(selected)) &&
                std::strcmp(selected, corpus["schema"].GetString()) == 0,
            "Active schema mismatch");
    rapidjson::Document result(rapidjson::kObjectType);
    auto& a = result.GetAllocator();
    result.AddMember("run_id", text(argv[6], a), a);
    result.AddMember("phase", text(argv[5], a), a);
    result.AddMember("schema", text(selected, a), a);
    result.AddMember("rime_version", text(api->get_version(), a), a);
    result.AddMember("opencc_validated", true, a);
    Json options(rapidjson::kObjectType);
    for (auto it = corpus["options"].MemberBegin();
         it != corpus["options"].MemberEnd(); ++it) {
      bool actual = api->get_option(session, it->name.GetString());
      require(actual == it->value.GetBool(), "Runtime option mismatch");
      options.AddMember(text(it->name.GetString(), a), Json(actual), a);
    }
    result.AddMember("options", options, a);
    Json records(rapidjson::kArrayType);
    if (phase == "train") {
      for (const auto& step : corpus["learning"].GetArray()) {
        require(step.IsObject() && step.HasMember("input") && step["input"].IsString() &&
                    step.HasMember("text") && step["text"].IsString() &&
                    step.HasMember("repeat") && step["repeat"].IsInt(), "Invalid learning step");
        int repeat = step["repeat"].GetInt();
        require(repeat > 0 && repeat <= 100, "Invalid learning repetitions");
        for (int n = 0; n < repeat; ++n) {
          input(api, session, step["input"].GetString());
          auto choices = candidates(api, session, 200, a);
          int index = -1;
          for (unsigned i = 0; i < choices.Size(); ++i) {
            if (std::strcmp(choices[i]["text"].GetString(), step["text"].GetString()) == 0) {
              index = static_cast<int>(i);
              break;
            }
          }
          require(index >= 0, "Learning target not found in first 200 candidates");
          require(api->select_candidate(session, index), "Learning selection failed");
          RIME_STRUCT(RimeCommit, commit);
          require(api->get_commit(session, &commit), "Learning did not commit a full candidate");
          std::string committed(commit.text ? commit.text : "");
          api->free_commit(&commit);
          require(committed == step["text"].GetString(), "Learning commit mismatch");
          Json record(rapidjson::kObjectType);
          record.AddMember("input", text(step["input"].GetString(), a), a);
          record.AddMember("committed", text(committed.c_str(), a), a);
          record.AddMember("candidate_index", index, a);
          record.AddMember("iteration", n + 1, a);
          records.PushBack(record, a);
        }
      }
    } else {
      for (const auto& test : corpus["cases"].GetArray()) {
        require(test.IsObject() && test.HasMember("id") && test["id"].IsString() &&
                    test.HasMember("input") && test["input"].IsString(), "Invalid test case");
        auto start = std::chrono::steady_clock::now();
        input(api, session, test["input"].GetString());
        RIME_STRUCT(RimeContext, ctx);
        require(api->get_context(session, &ctx), "Cannot read Rime context");
        Json record(rapidjson::kObjectType);
        record.AddMember("id", text(test["id"].GetString(), a), a);
        record.AddMember("input", text(test["input"].GetString(), a), a);
        record.AddMember("raw_input", text(api->get_input(session), a), a);
        record.AddMember("preedit", text(ctx.composition.preedit, a), a);
        record.AddMember("commit_preview", text(ctx.commit_text_preview, a), a);
        api->free_context(&ctx);
        record.AddMember("candidates", candidates(api, session, limit, a), a);
        double millis = std::chrono::duration<double, std::milli>(
            std::chrono::steady_clock::now() - start).count();
        record.AddMember("engine_query_ms", millis, a);
        require(!record["candidates"].Empty(), "No candidates for baseline case");
        records.PushBack(record, a);
        api->clear_composition(session);
      }
    }
    api->clear_composition(session);
    api->destroy_session(session);
    session = 0;
    api->finalize();
    initialized = false;
    result.AddMember("records", records, a);
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
    result.Accept(writer);
    std::cout << buffer.GetString() << '\n';
    return 0;
  } catch (const std::exception& error) {
    if (session) api->destroy_session(session);
    if (initialized) api->finalize();
    std::cerr << "Baseline failed: " << error.what() << '\n';
    return 1;
  }
}
