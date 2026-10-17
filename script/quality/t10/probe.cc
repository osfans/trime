// SPDX-License-Identifier: GPL-3.0-or-later
// A public-C-API learning probe. It never opens the application's user directory.
#include <rime_api.h>
#include <rime_levers_api.h>

#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>
#include <unistd.h>

namespace fs = std::filesystem;

static void require(bool value, const std::string& message) {
  if (!value) throw std::runtime_error(message);
}

static std::string json(const std::string& value) {
  std::string result = "\"";
  for (unsigned char c : value) {
    if (c == '"' || c == '\\') { result += '\\'; result += c; }
    else if (c == '\n') result += "\\n";
    else if (c == '\r') result += "\\r";
    else if (c == '\t') result += "\\t";
    else if (c < 32) throw std::runtime_error("Unexpected control character");
    else result += c;
  }
  return result + '"';
}

struct Case { const char* text; const char* spelling; };
static const std::vector<Case> cases = {{"茶雲翎", "cha'yun'ling"}, {"墨羽燈", "mo'yu'deng"}};

static std::string keys(std::string spelling, bool nine_key) {
  if (!nine_key) return spelling;
  std::string result;
  const std::string alphabet = "abcdefghijklmnopqrstuvwxyz";
  const std::string digits = "22233344455566677778889999";
  for (char c : spelling) {
    if (c == '\'') continue;
    const auto index = alphabet.find(c);
    require(index != std::string::npos, "Invalid fixture spelling");
    result += digits[index];
  }
  return result;
}

static std::string read_commit(RimeApi* api, RimeSessionId session) {
  RimeCommit commit{};
  RIME_STRUCT_INIT(RimeCommit, commit);
  if (!api->get_commit(session, &commit)) return "";
  std::string text = commit.text ? commit.text : "";
  api->free_commit(&commit);
  return text;
}

static void input(RimeApi* api, RimeSessionId session, const std::string& spelling) {
  api->clear_composition(session);
  require(read_commit(api, session).empty(), "Unread commit before query");
  for (char c : spelling) {
    require(api->process_key(session, c, 0), "Fixture key was not handled");
    require(read_commit(api, session).empty(), "Input unexpectedly committed while typing");
  }
}

static std::vector<std::string> candidates(RimeApi* api, RimeSessionId session) {
  std::vector<std::string> rows;
  RimeCandidateListIterator iterator{};
  if (api->candidate_list_begin(session, &iterator)) {
    while (rows.size() < 80 && api->candidate_list_next(&iterator)) {
      rows.emplace_back(iterator.candidate.text ? iterator.candidate.text : "");
    }
    api->candidate_list_end(&iterator);
  }
  return rows;
}

static size_t find(const std::vector<std::string>& rows, const std::string& target) {
  for (size_t index = 0; index < rows.size(); ++index) {
    if (rows[index] == target) return index;
  }
  throw std::runtime_error("Fictional phrase not in first 80 candidates: " + target);
}

static std::string field(RimeApi* api, RimeConfig* config, const char* key) {
  const char* value = api->config_get_cstring(config, key);
  return value ? json(value) : "null";
}

int main(int argc, char** argv) {
  RimeApi* api = rime_get_api();
  bool initialized = false;
  try {
    require(argc == 7 || argc == 8,
            "Usage: probe SHARED USER DICTIONARY PHASE OWNER OUTPUT [SNAPSHOT]");
    const fs::path user = fs::canonical(argv[2]);
    std::ifstream marker(user.parent_path() / "owner.txt");
    std::string owner;
    std::getline(marker, owner);
    require(!owner.empty() && owner == argv[5], "Directory not owned by this test");
    const std::string dictionary(argv[3]), phase(argv[4]);
    require(dictionary.rfind("__t10_", 0) == 0, "Fixture dictionary needs __t10_ prefix");
    require(dictionary.find_first_not_of("_abcdefghijklmnopqrstuvwxyz0123456789") == std::string::npos,
            "Unsafe fixture dictionary name");
    require(fs::path(argv[6]).parent_path() == user.parent_path(), "Output must belong to run directory");
    const char* modules[] = {"default", "levers", nullptr};
    RimeTraits traits{};
    RIME_STRUCT_INIT(RimeTraits, traits);
    traits.shared_data_dir = argv[1];
    traits.user_data_dir = argv[2];
    traits.app_name = "rime.trime_t10_host";
    traits.modules = modules;
    traits.min_log_level = 2;
    traits.log_dir = "";
    api->setup(&traits);
    api->initialize(&traits);
    initialized = true;
    // Each phase is a new process. Levers otherwise sees a default relative sync
    // directory before its deployer has read these owned traits/installation ID.
    api->deployer_initialize(&traits);
    require(api->run_task("installation_update"), "Owned installation initialization failed");
    require(RIME_PROVIDED(api, delete_candidate), "Engine lacks candidate deletion");
    auto module = api->find_module("levers");
    require(module && module->get_api, "Engine lacks public levers API");
    auto levers = reinterpret_cast<RimeLeversApi*>(module->get_api());
    require(RIME_PROVIDED(levers, import_user_dict), "Incomplete user dictionary API");
    std::string records = "[";
    auto append = [&](const std::string& row) {
      if (records.size() > 1) records += ',';
      records += row;
    };

    if (phase == "deploy") {
      api->start_maintenance(true);
      api->join_maintenance_thread();
    }
    if (phase == "deploy" || phase == "inspect" || phase == "inspect-forgotten" ||
        phase == "learn" || phase == "relearn" || phase == "forget") {
      auto session = api->create_session();
      require(session != 0, "Could not create test session");
      for (bool nine_key : {false, true}) {
        const std::string schema = dictionary + (nine_key ? "_t9" : "_full");
        require(api->select_schema(session, schema.c_str()), "Could not select fixture schema");
        api->set_option(session, "ascii_mode", false);
        if (phase == "deploy") {
          RimeConfig config{};
          require(api->schema_open(schema.c_str(), &config), "Deployed config is missing");
          append("{\"schema\":" + json(schema) + ",\"dictionary\":" +
                 field(api, &config, "translator/dictionary") + ",\"user_dict\":" +
                 field(api, &config, "translator/user_dict") + ",\"prism\":" +
                 field(api, &config, "translator/prism") + ",\"version\":" +
                 field(api, &config, "schema/version") + "}");
          api->config_close(&config);
          continue;
        }
        if (nine_key && (phase == "learn" || phase == "relearn" || phase == "forget")) continue;
        for (size_t index = 0; index < cases.size(); ++index) {
          if ((phase == "relearn" || phase == "forget") && index != 0) continue;
          const auto& test = cases[index];
          const int repeats = phase == "learn" ? 3 : 1;
          for (int repeat = 0; repeat < repeats; ++repeat) {
            const auto spelling = keys(test.spelling, nine_key);
            input(api, session, spelling);
            const auto rows = candidates(api, session);
            const auto rank = find(rows, test.text);
            // Inspection never selects/commits and therefore cannot learn answers.
            if (phase == "inspect") require(rank == 0, "Learned phrase is not first after restart/schema switch");
            if (phase == "forget") {
              require(api->delete_candidate(session, rank), "Candidate deletion returned failure");
              require(read_commit(api, session).empty(), "Deletion unexpectedly committed");
            } else if (phase == "learn" || phase == "relearn") {
              require(api->select_candidate(session, rank), "Learning selection failed");
              require(read_commit(api, session) == test.text, "Wrong or absent editor commit");
              require(read_commit(api, session).empty(), "Duplicate editor commit");
              // Closing a transaction through the actual unhandled-key path, without
              // waiting for time-based heuristics or reverting a recent learned commit.
              api->process_key(session, 0xff1b, 0);
              require(read_commit(api, session).empty(), "Escape unexpectedly committed");
            }
            append("{\"schema\":" + json(schema) + ",\"text\":" + json(test.text) +
                   ",\"input\":" + json(spelling) + ",\"rank\":" +
                   std::to_string(rank) + ",\"repeat\":" + std::to_string(repeat) + "}");
            api->clear_composition(session);
          }
        }
      }
      require(api->destroy_session(session), "Could not release user DB session");
      api->cleanup_all_sessions();
    } else if (phase == "export") {
      const int count = levers->export_user_dict(dictionary.c_str(), argv[6]);
      require(count >= 0, "User dictionary export failed");
      append("{\"entries\":" + std::to_string(count) + "}");
    } else if (phase == "backup") {
      require(levers->backup_user_dict(dictionary.c_str()), "User dictionary backup failed");
      char path[4096]{};
      api->get_user_data_sync_dir(path, sizeof(path));
      const auto snapshot = fs::path(path) / (dictionary + ".userdb.txt");
      require(fs::is_regular_file(snapshot) && fs::file_size(snapshot) > 0, "Backup snapshot missing");
      append("{\"snapshot\":" + json(snapshot.string()) + "}");
    } else if (phase == "restore" || phase == "reject-invalid-restore") {
      require(argc == 8, "Restore requires snapshot path");
      require(fs::weakly_canonical(argv[7]).string().rfind(user.parent_path().string() + '/', 0) == 0,
              "Snapshot must belong to run directory");
      const bool restored = levers->restore_user_dict(argv[7]);
      require(restored == (phase == "restore"), "Restore success did not match expected outcome");
      append("{\"restored\":" + std::string(restored ? "true" : "false") + "}");
    } else {
      throw std::runtime_error("Unknown phase");
    }
    const std::string version(api->get_version());
    api->finalize();
    initialized = false;
    std::cout << "{\"passed\":true,\"phase\":" << json(phase)
              << ",\"pid\":" << ::getpid() << ",\"owner\":" << json(owner)
              << ",\"rime_version\":" << json(version) << ",\"records\":" << records << "]}\n";
    return 0;
  } catch (const std::exception& failure) {
    if (initialized) api->finalize();
    std::cerr << failure.what() << '\n';
    return 1;
  }
}
