// SPDX-License-Identifier: GPL-3.0-or-later
#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/key_event.h>
#include <rime/lever/user_dict_manager.h>
#include <rime/menu.h>
#include <rime/schema.h>
#include <rime_api.h>
#include <algorithm>
#include <chrono>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include "../../app/src/main/jni/librime_jni/t9.h"

using namespace rime;
static int checks = 0;
static void check(bool ok, const std::string& message) {
  if (!ok) throw std::runtime_error(message);
  std::cout << "PASS " << ++checks << " " << message << '\n';
}
static long pss() {
  std::ifstream file("/proc/self/smaps_rollup");
  std::string line;
  long sum = 0;
  while (std::getline(file, line))
    if (line.rfind("Pss:", 0) == 0) sum += std::stol(line.substr(4));
  return sum;
}

using ReferenceIndex = std::map<std::string, std::vector<std::string>>;
static ReferenceIndex reference_index(Config* config, const Script& syllables, int rule) {
  const char* names[] = {"nl", "z_zh", "c_ch", "s_sh", "en_eng", "in_ing"};
  auto script = syllables;
  if (rule >= 0) {
    Projection fuzzy;
    if (!fuzzy.Load(config->GetList(std::string("trime/t9_fuzzy/") + names[rule]))) return {};
    fuzzy.Apply(&script);
    for (auto item = script.begin(); item != script.end();) {
      auto& values = item->second;
      values.erase(std::remove_if(values.begin(), values.end(), [&](const auto& s) {
        return s.str == item->first;
      }), values.end());
      if (values.empty()) item = script.erase(item);
      else ++item;
    }
  }
  Projection numeric;
  check(numeric.Load(config->GetList("speller/algebra")), "reference algebra loads");
  numeric.Apply(&script);
  ReferenceIndex result;
  for (const auto& [key, spellings] : script) {
    if (key.empty() || key.size() > 6 || key.find_first_not_of("23456789") != std::string::npos) continue;
    for (const auto& spelling : spellings)
      if (spelling.properties.type == kNormalSpelling && !spelling.properties.is_correction)
        result[key].push_back(spelling.str);
  }
  return result;
}

static void verify_batched_projection(Config* config) {
  Script syllables;
  for (const auto* spelling : {"ni", "li", "nve", "lve", "nue", "lue", "nu", "lu", "nv", "lv",
       "zao", "zhao", "ca", "cha", "sa", "sha", "zen", "zeng", "lin", "ling", "ning",
       "ju", "jue", "qu", "que", "xu", "xue", "yu", "yue", "xian", "xi", "an"})
    syllables.AddSyllable(spelling);
  trime::T9Assist index;
  check(index.Build(config, syllables, 511), "batched index builds");
  auto exact = reference_index(config, syllables, -1);
  for (int rule = 0; rule < 6; ++rule) {
    auto reference = reference_index(config, syllables, rule);
    for (const auto& [key, values] : reference) {
      std::set<std::string> expected, actual;
      for (const auto& value : values)
        if (std::find(exact[key].begin(), exact[key].end(), value) == exact[key].end()) expected.insert(value);
      for (const auto& alternative : index.Find(key, 1 << rule))
        if (alternative.length == static_cast<int>(key.size())) {
          check(alternative.sources == (1 << rule), "batch projection retains independent source");
          actual.insert(alternative.spelling);
        }
      check(actual == expected, "batched numeric projection matches separate-rule reference: " + key);
    }
  }
  auto algebra = config->GetList("speller/algebra");
  config->SetItem("speller/algebra", New<ConfigList>());
  check(!index.Build(config, syllables, 511), "invalid rebuild fails closed");
  check(index.Find("54", 511).empty(), "failed rebuild cannot leak previous suggestions");
  config->SetItem("speller/algebra", algebra);
  check(index.Build(config, syllables, 0), "disabled fuzzy rules rebuild successfully");
  check(index.Find("54", 1).empty(), "successful rebuild drops old fuzzy indexes");
  Script excessive;
  for (int i = 0; i < 4097; ++i) excessive.AddSyllable(std::to_string(i));
  check(!index.Build(config, excessive, 511), "canonical input bound enforced before projection");
}

int main(int argc, char** argv) {
  if (argc != 3 && argc != 4) return 2;
  auto api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = argv[1];
  traits.user_data_dir = argv[2];
  traits.app_name = "rime.trime_t05_test";
  traits.log_dir = "";
  traits.min_log_level = 2;
  api->setup(&traits);
  api->initialize(&traits);
  try {
    if (argc == 4) {
      UserDictManager manager(&Service::instance().deployer());
      auto path = std::string(argv[2]) + "/learning.tsv";
      check(manager.Export("t9_test", rime::path(path)) > 0, "repair learning survives process restart");
      std::ifstream input(path);
      std::stringstream contents; contents << input.rdbuf();
      check(contents.str().find(u8"你好\tni hao") != std::string::npos, "repair learns canonical ni hao");
      check(contents.str().find('~') == std::string::npos && contents.str().find("54 426") == std::string::npos,
            "wrong digit code and private envelopes are not learned");
      api->finalize();
      return 0;
    }
    api->start_maintenance(true); api->join_maintenance_thread();
    auto id = api->create_session();
    check(api->select_schema(id, "luna_pinyin_t9"), "deploy isolated T05 fixture");
    auto session = Service::instance().GetSession(id);
    auto ctx = session->context();
    verify_batched_projection(session->schema()->config());
    trime::T9 t9;
    auto no_commit = [&] {
      RIME_STRUCT(RimeCommit, commit);
      bool committed = api->get_commit(id, &commit);
      if (committed) api->free_commit(&commit);
      check(!committed, "no automatic commit from assistance");
    };
    auto fresh = [&](const std::string& keys, int options) {
      ctx->Clear(); t9.Reset(); t9.SetAssistOptions(options);
      for (char key : keys) {
        if (!t9.ProcessKey(session.get(), key, 0)) api->process_key(id, key, 0);
      }
      return t9.Snapshot(session.get());
    };
    auto menu = [&] {
      std::vector<std::string> values;
      if (ctx->composition().empty() || !ctx->composition().back().menu) return values;
      for (int i = 0; i < 80; ++i) {
        auto candidate = ctx->composition().back().menu->GetCandidateAt(i);
        if (!candidate) break;
        values.push_back(candidate->text() + ":" + std::to_string(candidate->end()));
      }
      return values;
    };
    auto has = [&](const trime::T9Snapshot& state, const std::string& spelling, int end, int sources) {
      return std::any_of(state.choices.begin(), state.choices.end(), [&](const auto& choice) {
        return choice.start == 0 && choice.end == end && choice.spelling == spelling &&
               (sources == 0 || (choice.sources & sources) == sources);
      });
    };
    struct Case { const char* input; const char* target; int rule; };
    const Case fuzzy[] = {
      {"54", "ni", 0}, {"64", "li", 0}, {"926", "zhao", 1}, {"9426", "zao", 1},
      {"22", "cha", 2}, {"242", "ca", 2}, {"72", "sha", 3}, {"742", "sa", 3},
      {"936", "zeng", 4}, {"9364", "zen", 4}, {"546", "ling", 5}, {"5464", "lin", 5},
    };
    const Case typos[] = {
      {"54", "ni", 6}, {"6", "ni", 7}, {"644", "ni", 8},
      {"526", "hao", 6}, {"46", "hao", 7}, {"4226", "hao", 8},
      {"826", "zao", 6}, {"96", "zao", 7}, {"9226", "zao", 8},
      {"6465", "ning", 6}, {"644", "ning", 7}, {"64664", "ning", 8},
    };
    const std::map<std::string, std::string> hanzi = {
      {"ni", u8"你"}, {"li", u8"李"}, {"zao", u8"早"}, {"zhao", u8"找"},
      {"ca", u8"擦"}, {"cha", u8"茶"}, {"sa", u8"撒"}, {"sha", u8"沙"},
      {"zen", u8"怎"}, {"zeng", u8"增"}, {"lin", u8"林"}, {"ling", u8"零"},
      {"hao", u8"好"}, {"ning", u8"宁"},
    };
    int before = 0, after = 0;
    auto recovery = [&](const Case& test) {
      int length = std::string(test.input).size();
      auto old = fresh(test.input, 0);
      auto exact_menu = menu();
      bool old_hit = has(old, test.target, length, 0);
      before += old_hit;
      auto state = fresh(test.input, 1 << test.rule);
      bool hit = has(state, test.target, length, 1 << test.rule);
      after += hit;
      check(hit, std::string("single rule recovery ") + test.input + " -> " + test.target);
      check(menu() == exact_menu, "assistance leaves the entire first-80 Hanzi menu unchanged");
      check(state.choices.size() <= old.choices.size() + trime::T9Assist::kMaxChoices, "bounded extra choices");
      for (size_t i = 0; i < old.choices.size(); ++i)
        check(old.choices[i].spelling == state.choices[i].spelling && old.choices[i].end == state.choices[i].end &&
              state.choices[i].sources == 0, "exact choices precede suggestions");
      check(t9.Act(session.get(), state.revision, 1, 0, length, test.target), "apply recovered spelling without commit");
      auto corrected_menu = menu();
      auto goal = hanzi.at(test.target) + ":" + std::to_string(ctx->input().size());
      check(std::find(corrected_menu.begin(), corrected_menu.end(), goal) != corrected_menu.end(), "recovered spelling returns its Hanzi");
      check(t9.RawInput(session.get()) == test.input, "recovery preserves original digit code");
      check(t9.Act(session.get(), t9.Snapshot(session.get()).revision, 3, 0, 0, ""), "every repair can be undone");
      auto current_revision = t9.Snapshot(session.get()).revision;
      t9.SetAssistOptions(0);
      check(!t9.Act(session.get(), current_revision, 1, 0, length, test.target), "disabled-rule stale action rejected");
      check(!has(t9.Snapshot(session.get()), test.target, length, 1 << test.rule), "individual rule can be disabled");
      no_commit();
      std::cout << "CASE " << test.rule << ' ' << test.input << ' ' << test.target << ' ' << old_hit << ' ' << hit << '\n';
    };
    for (const auto& test : fuzzy) recovery(test);
    std::cout << "RECOVERY fuzzy " << before << ' ' << after << " 12\n";
    before = after = 0;
    for (const auto& test : typos) recovery(test);
    std::cout << "RECOVERY typo " << before << ' ' << after << " 12\n";
    check(!has(fresh("24", 1 << 6), "ni", 2, 1 << 6), "no QWERTY/diagonal substitution");
    check(!has(fresh("624", 1 << 8), "ni", 3, 1 << 8), "repeat repair does not remove an arbitrary digit");
    check(!has(fresh("85", 511), "ni", 2, 0), "two simultaneous errors are not searched");
    check(has(fresh("54", 65), "ni", 2, 65), "independent fuzzy and typo sources merge without duplicate choices");
    auto cached = fresh("54", 1);
    auto config = session->schema()->config();
    auto fuzzy_rule = config->GetList("trime/t9_fuzzy/nl");
    config->SetItem("trime/t9_fuzzy/nl", New<ConfigList>());
    check(t9.Act(session.get(), cached.revision, 1, 0, 2, "ni"), "lock before clearing editing state");
    ctx->Clear();
    t9.Clear();
    auto empty = t9.Snapshot(session.get());
    check(empty.input.empty() && !empty.can_undo && empty.segments.empty(), "clear removes locks, raw input and undo history");
    check(empty.revision != cached.revision, "clear invalidates old UI actions");
    api->process_key(id, '5', 0); api->process_key(id, '4', 0);
    check(has(t9.Snapshot(session.get()), "ni", 2, 1), "clear reuses the already-built schema index");
    t9.Reset();
    check(!has(t9.Snapshot(session.get()), "ni", 2, 1), "resource reset rebuilds from changed schema rules");
    config->SetItem("trime/t9_fuzzy/nl", fuzzy_rule);
    t9.Reset();
    check(has(t9.Snapshot(session.get()), "ni", 2, 1), "resource reset restores the actual schema rules");
    no_commit();
    auto plain = fresh("64426", 0);
    auto plain_menu = menu();
    auto all = fresh("64426", 511);
    check(menu() == plain_menu && all.choices.size() <= plain.choices.size() + 32, "all rules preserve exact sentence ranking");
    for (size_t i = 0; i < plain.choices.size(); ++i)
      check(plain.choices[i].spelling == all.choices[i].spelling && all.choices[i].sources == 0, "all-rules exact prefix invariant");

    auto repair = fresh("54426", 1 << 6);
    check(has(repair, "ni", 2, 1 << 6), "middle-sentence repair is available");
    check(t9.Act(session.get(), repair.revision, 1, 0, 2, "ni"), "lock corrected syllable");
    auto locked = t9.Snapshot(session.get());
    check(locked.input == "54426" && ctx->input() == "~ni~426", "raw digits and following tail preserved");
    check(locked.segments[0].sources == (1 << 6), "locked preedit preserves correction provenance");
    no_commit();
    check(t9.Act(session.get(), locked.revision, 3, 0, 0, ""), "undo correction");
    check(ctx->input() == "54426", "undo restores original engine query");
    repair = t9.Snapshot(session.get());
    check(t9.Act(session.get(), repair.revision, 1, 0, 2, "ni"), "reapply correction");
    check(t9.ProcessKey(session.get(), XK_Home, 0) && t9.ProcessKey(session.get(), XK_Delete, 0), "delete at correction boundary unlocks");
    check(t9.RawInput(session.get()) == "54426" && ctx->input() == "54426", "unlock does not delete a code unit");
    check(t9.Act(session.get(), t9.Snapshot(session.get()).revision, 3, 0, 0, ""), "undo boundary unlock");
    check(ctx->input() == "~ni~426", "undo restores corrected lock");
    t9.SetAssistOptions(0);
    auto disabled = t9.Snapshot(session.get());
    check(ctx->input() == "~ni~426" && disabled.segments[0].sources == 64,
          "disabling a rule preserves an explicitly selected lock and its source");
    check(t9.ProcessKey(session.get(), XK_End, 0), "move to full sentence end");
    int found = -1;
    for (int i = 0; i < 80; ++i) {
      auto candidate = ctx->composition().back().menu->GetCandidateAt(i);
      if (!candidate) break;
      if (candidate->text() == u8"你好" && candidate->end() == ctx->input().size()) { found = i; break; }
    }
    check(found >= 0 && api->select_candidate(id, found), "explicit corrected Hanzi selection");
    RIME_STRUCT(RimeCommit, commit);
    check(api->get_commit(id, &commit) && std::string(commit.text) == u8"你好", "corrected sentence committed once");
    api->free_commit(&commit); no_commit();

    // Measure only lookup/snapshot work after input, not UI latency or deployment peaks.
    for (int options : {0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 511}) {
      fresh("64426", options);
      std::vector<double> times;
      size_t choices = 0;
      for (int i = 0; i < 40; ++i) {
        auto start = std::chrono::steady_clock::now();
        choices = std::max(choices, t9.Snapshot(session.get()).choices.size());
        times.push_back(std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start).count());
      }
      std::sort(times.begin(), times.end());
      std::cout << "METRIC " << options << ' ' << times[20] << ' ' << times[38] << ' ' << pss() << ' ' << choices << '\n';
    }
    fresh("64", 511);
    api->set_option(id, "ascii_mode", true);
    check(!t9.Snapshot(session.get()).enabled, "ASCII mode never offers repairs");
    api->set_option(id, "ascii_mode", false);
    api->clear_composition(id); api->destroy_session(id); api->finalize();
    return 0;
  } catch (const std::exception& error) {
    std::cerr << "T05 failed: " << error.what() << '\n';
    api->finalize();
    return 1;
  }
}
