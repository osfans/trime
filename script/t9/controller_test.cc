// SPDX-License-Identifier: GPL-3.0-or-later
#include <rime/context.h>
#include <rime/key_event.h>
#include <rime/lever/user_dict_manager.h>
#include <rime/schema.h>
#include <rime_api.h>

#include <algorithm>
#include <fstream>
#include <iostream>
#include <sstream>
#include <stdexcept>

#include "../../app/src/main/jni/librime_jni/helper-types.h"
#include "../../app/src/main/jni/librime_jni/t9.h"

using namespace rime;
static int checks = 0;
static void check(bool ok, const std::string& message) {
  if (!ok) throw std::runtime_error(message);
  std::cout << "PASS " << ++checks << " " << message << std::endl;
}

int main(int argc, char** argv) {
  if (argc != 3 && argc != 4) return 2;
  const std::string supplementary = u8"𠮷你 426";
  check(::distance(supplementary.data(),
                   supplementary.data() + supplementary.size()) == 7,
        "JNI preedit length counts UTF-16 including surrogate pairs");
  check(::distance(supplementary.data(), supplementary.data() + 4) == 2,
        "JNI caret after supplementary Hanzi uses two UTF-16 units");
  auto api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = argv[1];
  traits.user_data_dir = argv[2];
  traits.app_name = "rime.trime_t9_test";
  traits.log_dir = "";
  traits.min_log_level = 2;
  api->setup(&traits);
  api->initialize(&traits);
  auto learning_check = [&] {
    UserDictManager manager(&Service::instance().deployer());
    auto path = std::string(argv[2]) + "/learning.tsv";
    check(manager.Export("t9_test", rime::path(path)) > 0,
          "learning persisted in Rime user dictionary");
    std::ifstream file(path);
    std::stringstream buffer;
    buffer << file.rdbuf();
    auto data = buffer.str();
    check(data.find(u8"你好\tni hao") != std::string::npos,
          "learned record uses canonical syllables");
    check(data.find('~') == std::string::npos,
          "private spelling never leaks into learned code");
  };
  if (argc == 4) {
    learning_check();
    api->finalize();
    return 0;
  }
  api->start_maintenance(true);
  api->join_maintenance_thread();
  check(api->deploy_schema(
            (std::string(argv[1]) + "/luna_pinyin.schema.yaml").c_str()),
        "deploy actual full-pinyin rules");
  check(api->deploy_schema(
            (std::string(argv[1]) + "/double_pinyin.schema.yaml").c_str()),
        "deploy actual double-pinyin rules");
  auto id = api->create_session();
  check(api->select_schema(id, "luna_pinyin_t9"), "deploy fixture");
  auto session = Service::instance().GetSession(id);
  auto ctx = session->context();
  trime::T9 t9;
  auto no_commit = [&] {
    RIME_STRUCT(RimeCommit, c);
    bool committed = api->get_commit(id, &c);
    if (committed) api->free_commit(&c);
    check(!committed, "no editor commit from syllable/edit action");
  };
  auto type = [&](const std::string& input) {
    for (char c : input) {
      if (!t9.ProcessKey(session.get(), c, 0)) api->process_key(id, c, 0);
      t9.Snapshot(session.get());
    }
    no_commit();
  };
  auto fresh = [&](const std::string& input) {
    ctx->Clear();
    t9.Reset();
    type(input);
  };
  auto focus = [&](int pos) {
    auto s = t9.Snapshot(session.get());
    check(t9.Act(session.get(), s.revision, 0, pos, 0, ""),
          "focus raw position " + std::to_string(pos));
  };
  auto lock = [&](const std::string& spelling, int end) {
    auto s = t9.Snapshot(session.get());
    check(std::any_of(s.choices.begin(), s.choices.end(),
                      [&](const auto& c) {
                        return c.spelling == spelling && c.end == end;
                      }),
          "syllable index contains " + spelling);
    check(t9.Act(session.get(), s.revision, 1, s.focus, end, spelling),
          "lock " + spelling);
    no_commit();
  };
  auto action = [&](int kind, int start = 0) {
    auto s = t9.Snapshot(session.get());
    check(t9.Act(session.get(), s.revision, kind, start, 0, ""),
          "edit action " + std::to_string(kind));
    no_commit();
  };
  auto texts = [&] {
    std::vector<std::string> result;
    RimeCandidateListIterator it{};
    if (api->candidate_list_begin(id, &it)) {
      while (api->candidate_list_next(&it))
        result.emplace_back(it.candidate.text);
      api->candidate_list_end(&it);
    }
    return result;
  };
  auto contains = [&](const std::string& text) {
    auto list = texts();
    return std::find(list.begin(), list.end(), text) != list.end();
  };
  fresh("64");
  check(t9.ProcessKey(session.get(), 'z', kControlMask) &&
            t9.RawInput(session.get()) == "6",
        "Ctrl-Z uses raw editing history without locks");
  check(t9.ProcessKey(session.get(), 'y', kControlMask) &&
            t9.RawInput(session.get()) == "6",
        "editor redo cannot mutate an active Rime composition");
  fresh("64");
  auto initial = t9.Snapshot(session.get());
  check(initial.enabled, "T9 enabled");
  lock("ni", 2);
  check(contains(u8"你") && !contains(u8"米"), "ni excludes mi");
  check(!t9.Act(session.get(), initial.revision, 1, 0, 2, "mi"),
        "stale tap rejected");
  focus(0);
  lock("mi", 2);
  check(contains(u8"米") && !contains(u8"你"), "replace ni with mi");
  action(2, 0);
  check(ctx->input() == "64", "unlock restores exact digits");
  action(3);
  check(ctx->input() == "~mi~", "undo unlock");
  fresh("64426");
  lock("ni", 2);
  check(ctx->input() == "~ni~426", "preserve suffix 426");
  check(contains(u8"你好") && !contains(u8"米高"),
        "constrain first syllable only");
  Preedit preedit;
  check(t9.GetPreedit(session.get(), &preedit) && preedit.text == "ni 426",
        "preedit hides private envelope");
  focus(0);
  check(t9.GetPreedit(session.get(), &preedit) &&
            preedit.sel_end < preedit.text.size(),
        "current syllable highlight excludes unresolved tail");
  check(t9.MoveCaret(session.get(), 4) && ctx->caret_pos() == 5,
        "display caret maps after digit 4");
  type("2");
  check(t9.Snapshot(session.get()).input == "644226",
        "insert in middle preserves original digit positions");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0),
        "delete inserted middle digit");
  check(ctx->input() == "~ni~426", "middle delete leaves lock intact");
  check(t9.ProcessKey(session.get(), XK_Home, 0), "home navigation");
  check(t9.ProcessKey(session.get(), XK_Right, 0) && ctx->caret_pos() == 4,
        "right skips atomic lock envelope");
  check(t9.ProcessKey(session.get(), XK_End, 0), "end navigation");
  focus(2);
  lock("hao", 5);
  check(contains(u8"你好") && !contains(u8"泥稿"), "progressive lock");
  type("62");
  check(ctx->input() == "~ni~~hao~62", "continue after locks");
  lock("ma", 7);
  focus(2);
  lock("gao", 5);
  check(ctx->input() == "~ni~~gao~~ma~",
        "replace middle syllable preserves neighbors");
  check(ctx->caret_pos() == 9, "middle focus moves engine caret");
  check(t9.ProcessKey(session.get(), XK_End, 0), "return to sentence end");
  check(contains(u8"你高吗") && !contains(u8"你好吗"),
        "middle constraint affects Hanzi");
  action(3);
  check(t9.ProcessKey(session.get(), XK_End, 0),
        "undo restores editable middle; navigate to end");
  check(contains(u8"你好吗"), "undo middle replacement");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0), "backspace handled");
  check(ctx->input() == "~ni~~hao~62", "backspace unlocks last syllable first");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0),
        "second backspace handled");
  check(t9.Snapshot(session.get()).input == "644266",
        "second backspace deletes one original digit");
  fresh("9426");
  lock("xian", 4);
  check(contains(u8"先") && !contains(u8"西安"),
        "atomic xian cannot split into xi an");
  action(2, 0);
  lock("xi", 2);
  check(ctx->input() == "~xi~26", "shorter segmentation keeps suffix");
  lock("an", 4);
  check(contains(u8"西安"), "xi an remains separately selectable");
  fresh("64'426'62");
  lock("ni", 2);
  check(t9.Snapshot(session.get()).focus == 3,
        "advance to next syllable across explicit separator");
  lock("hao", 6);
  lock("ma", 9);
  check(ctx->input() == "~ni~'~hao~'~ma~" && contains(u8"你好吗"),
        "explicit separators survive progressive locking");
  fresh("646");
  auto completion = t9.Snapshot(session.get());
  check(std::any_of(
            completion.choices.begin(), completion.choices.end(),
            [](const auto& c) { return c.spelling == "ming" && c.completion; }),
        "completion distinguished in engine graph");
  lock("ming", 3);
  check(contains(u8"明"), "completion queries full syllable");
  action(2, 0);
  check(ctx->input() == "646",
        "unlock completion restores short original input");
  fresh("646");
  lock("ming", 3);
  check(t9.RawInput(session.get()) == "646",
        "raw API restores completion input");
  api->process_key(id, XK_Return, 0);
  RIME_STRUCT(RimeCommit, raw_commit);
  check(api->get_commit(id, &raw_commit),
        "Return keeps raw-code commit behavior");
  check(t9.CommitText(raw_commit.text) == "646",
        "raw commit hides exact envelope");
  api->free_commit(&raw_commit);
  for (bool locked : {false, true}) {
    const std::string original = locked ? "64426" : "64";
    fresh(original);
    if (locked) lock("ni", 2);
    check(t9.MoveCaret(session.get(), locked ? 3 : 1),
          "position caret inside raw-code commit input");
    check(!t9.ProcessKey(session.get(), XK_Return, 0),
          "Return delegates to the schema editor");
    check(api->process_key(id, XK_Return, 0), "schema handles raw Return");
    RIME_STRUCT(RimeCommit, middle_raw_commit);
    check(api->get_commit(id, &middle_raw_commit), "middle Return commits");
    check(t9.CommitText(middle_raw_commit.text) == original,
          "middle Return preserves the entire original input");
    api->free_commit(&middle_raw_commit);
    check(ctx->input().empty(), "middle Return leaves no pending tail");
    no_commit();
  }
  fresh("64426");
  lock("ni", 2);
  lock("hao", 5);
  check(t9.CommitText(u8"你好") == u8"你好",
        "normal Hanzi commit is untouched");
  ctx->set_option("ascii_mode", true);
  check(t9.RawInput(session.get()) == "64426",
        "ASCII switch sees original digits");
  ctx->set_option("ascii_mode", false);
  fresh("64426");
  lock("ni", 2);
  auto list = texts();
  auto chosen = std::find(list.begin(), list.end(), u8"你好");
  check(
      chosen != list.end() && api->select_candidate(id, chosen - list.begin()),
      "select Hanzi candidate");
  RIME_STRUCT(RimeCommit, commit);
  check(api->get_commit(id, &commit), "only Hanzi selection commits");
  check(std::string(commit.text) == u8"你好", "editor receives ni hao Hanzi");
  api->free_commit(&commit);
  check(!t9.Snapshot(session.get()).enabled, "commit clears lock state");
  check(!t9.Act(session.get(), initial.revision, 3, 0, 0, ""),
        "undo cannot cross commit");
  fresh("64426");
  lock("ni", 2);
  list = texts();
  chosen = std::find(list.begin(), list.end(), u8"你");
  check(
      chosen != list.end() && api->select_candidate(id, chosen - list.begin()),
      "select partial Hanzi directly");
  no_commit();
  check(t9.GetPreedit(session.get(), &preedit) && preedit.text == u8"你 426",
        "confirmed Hanzi stays visible");
  check(!t9.Snapshot(session.get()).can_undo,
        "syllable undo cannot cross a Hanzi selection");
  lock("hao", 5);
  list = texts();
  chosen = std::find(list.begin(), list.end(), u8"好");
  check(
      chosen != list.end() && api->select_candidate(id, chosen - list.begin()),
      "select remaining Hanzi");
  RIME_STRUCT(RimeCommit, partial_commit);
  check(api->get_commit(id, &partial_commit),
        "partial selections eventually commit");
  check(std::string(partial_commit.text) == u8"你好",
        "partial selection preserves whole input");
  api->free_commit(&partial_commit);
  fresh("64426");
  lock("ni", 2);
  list = texts();
  chosen = std::find(list.begin(), list.end(), u8"你");
  check(chosen != list.end() &&
            api->select_candidate(id, chosen - list.begin()),
        "select Hanzi prefix before raw Return");
  no_commit();
  check(t9.MoveCaret(session.get(), std::string(u8"你 ").size()),
        "position raw Return after confirmed Hanzi");
  check(!t9.ProcessKey(session.get(), XK_Return, 0) &&
            api->process_key(id, XK_Return, 0),
        "raw Return handles a confirmed prefix");
  RIME_STRUCT(RimeCommit, confirmed_raw_commit);
  check(api->get_commit(id, &confirmed_raw_commit),
        "raw Return commits the confirmed prefix and tail");
  check(t9.CommitText(confirmed_raw_commit.text) == u8"你426",
        "raw Return preserves confirmed Hanzi without repeating its code");
  api->free_commit(&confirmed_raw_commit);
  check(ctx->input().empty(), "confirmed raw Return clears the composition");
  no_commit();
  fresh("64426");
  lock("ni", 2);
  list = texts();
  chosen = std::find(list.begin(), list.end(), u8"你");
  check(
      chosen != list.end() && api->select_candidate(id, chosen - list.begin()),
      "partial selection before middle correction");
  check(t9.GetPreedit(session.get(), &preedit) && preedit.text == u8"你 426",
        "selected prefix retains original digit tail");
  check(t9.MoveCaret(session.get(), 5),
        "byte caret after selected Hanzi and first tail digit");
  type("2");
  check(t9.RawInput(session.get()) == "644226",
        "middle correction after selected Hanzi preserves codes");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0),
        "remove middle correction");
  check(t9.ProcessKey(session.get(), XK_End, 0),
        "finish corrected partial sentence");
  list = texts();
  chosen = std::find(list.begin(), list.end(), u8"好");
  check(
      chosen != list.end() && api->select_candidate(id, chosen - list.begin()),
      "choose corrected suffix without resubmitting prefix");
  RIME_STRUCT(RimeCommit, corrected_commit);
  check(api->get_commit(id, &corrected_commit) &&
            std::string(corrected_commit.text) == u8"你好",
        "middle correction commits the sentence exactly once");
  api->free_commit(&corrected_commit);
  no_commit();
  fresh("64426");
  lock("ni", 2);
  list = texts();
  chosen = std::find(list.begin(), list.end(), u8"你");
  check(
      chosen != list.end() && api->select_candidate(id, chosen - list.begin()),
      "select prefix before changing its reading");
  check(t9.MoveCaret(session.get(), 0), "move back into selected prefix");
  check(t9.ProcessKey(session.get(), XK_Delete, 0),
        "reopen selected prefix constraint");
  focus(0);
  lock("mi", 2);
  check(t9.ProcessKey(session.get(), XK_End, 0),
        "return to end after prefix correction");
  list = texts();
  chosen = std::find(list.begin(), list.end(), u8"米高");
  check(
      chosen != list.end() && api->select_candidate(id, chosen - list.begin()),
      "choose sentence with corrected prefix");
  RIME_STRUCT(RimeCommit, prefix_commit);
  check(api->get_commit(id, &prefix_commit) &&
            std::string(prefix_commit.text) == u8"米高",
        "old selected prefix is not duplicated in corrected commit");
  api->free_commit(&prefix_commit);
  no_commit();
  fresh("64426");
  lock("ni", 2);
  lock("hao", 5);
  check(t9.ProcessKey(session.get(), XK_Home, 0), "move before first lock");
  check(t9.ProcessKey(session.get(), XK_Delete, 0), "forward delete unlocks");
  check(
      t9.Snapshot(session.get()).input == "64426" && ctx->input() == "64~hao~",
      "forward unlock retains later lock");
  check(t9.ProcessKey(session.get(), XK_Delete, 0),
        "forward delete removes raw digit");
  check(t9.Snapshot(session.get()).input == "4426" && ctx->input() == "4~hao~",
        "later lock shifts by original input delta");
  fresh("64426");
  lock("ni", 2);
  lock("hao", 5);
  api->process_key(id, XK_BackSpace, kControlMask);
  check(t9.Snapshot(session.get()).input == "64" && ctx->input() == "~ni~",
        "Rime syllable deletion reconciles raw positions");
  api->set_option(id, "ascii_mode", true);
  check(!t9.Snapshot(session.get()).enabled, "ASCII mode hides disambiguation");
  api->set_option(id, "ascii_mode", false);
  api->process_key(id, XK_Escape, 0);
  check(!t9.Snapshot(session.get()).enabled, "escape clears disambiguation");
  no_commit();
  fresh("9464");
  lock("xing", 4);
  check(contains(u8"行"), "polyphonic xing reading");
  fresh("4264");
  lock("hang", 4);
  check(contains(u8"行"), "polyphonic hang reading");
  fresh("683");
  lock("nve", 3);
  check(contains(u8"虐"), "nue alias maps through prism to nve");
  fresh("nihao");
  check(contains(u8"你好"), "alphabetic input remains usable");
  fresh("644267");
  check(t9.GetPreedit(session.get(), &preedit) && preedit.text == "644267",
        "ambiguous digits and incomplete tail never masquerade as pinyin");
  lock("ni", 2);
  check(t9.GetPreedit(session.get(), &preedit) && preedit.text == "ni 4267",
        "only confirmed constraints become pinyin");
  focus(2);
  check(t9.ProcessKey(session.get(), XK_End, 0),
        "move from middle syllable to unfinished tail");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0),
        "delete unfinished tail");
  check(t9.RawInput(session.get()) == "64426", "one code unit removed");
  action(3);
  check(t9.RawInput(session.get()) == "644267",
        "undo restores incomplete tail");
  action(4);
  check(ctx->input().empty() && !t9.Snapshot(session.get()).enabled,
        "cancel discards composition and its undo, without committing");
  fresh("64");
  check(t9.ProcessKey(session.get(), XK_Home, 0), "unlocked home");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0) &&
            t9.RawInput(session.get()) == "64",
        "backspace at composition start cannot reach editor");
  check(t9.MoveCaret(session.get(), 2) && ctx->caret_pos() == 2,
        "unlocked display supports moving to the end");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0), "unlocked delete");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0), "delete last code unit");
  check(t9.Snapshot(session.get()).can_undo,
        "empty composition retains editing undo");
  action(3);
  check(t9.RawInput(session.get()) == "6", "undo last code deletion");
  action(3);
  check(t9.RawInput(session.get()) == "64", "undo unlocked deletion");
  action(4);
  check(!t9.ProcessKey(session.get(), XK_BackSpace, 0),
        "empty backspace reaches editor");
  check(!t9.ProcessKey(session.get(), '\'', 0),
        "apostrophe outside composition keeps punctuation behavior");
  fresh("6");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0),
        "delete last digit before space");
  check(!t9.ProcessKey(session.get(), ' ', 0) &&
            !t9.Snapshot(session.get()).can_undo,
        "editor space closes the empty-composition undo boundary");
  fresh("64426");
  check(t9.MoveCaret(session.get(), 1), "caret inside ambiguous syllable");
  lock("ni", 2);
  check(ctx->caret_pos() == 4,
        "locking snaps an interior caret to the syllable end");
  check(t9.ProcessKey(session.get(), XK_BackSpace, 0) &&
            t9.RawInput(session.get()) == "64426",
        "backspace after interior lock only unlocks");
  for (const auto& input : {"xi'an", "xian", "nv", "nue", "lv", "lue"}) {
    fresh(input);
    check(t9.GetPreedit(session.get(), &preedit) && preedit.text == input,
          std::string("preserve original spelling: ") + input);
    const std::string expected = std::string(input) == "xi'an"  ? u8"西安"
                                 : std::string(input) == "xian" ? u8"先"
                                 : std::string(input) == "nv"   ? u8"女"
                                 : std::string(input) == "nue"  ? u8"虐"
                                 : std::string(input) == "lv"   ? u8"绿"
                                                                : u8"略";
    check(contains(expected), std::string("dictionary accepts ") + input);
    check(t9.MoveCaret(session.get(), 1), "position inside original spelling");
    type("2");
    check(t9.ProcessKey(session.get(), XK_BackSpace, 0),
          "delete middle insertion");
    check(t9.RawInput(session.get()) == input,
          "middle edit loses no original input");
    action(3);
    action(3);
    check(t9.RawInput(session.get()) == input, "undo round trip");
    no_commit();
  }
  ctx->Clear();
  auto schema = new Schema("luna_pinyin_t9");
  schema->config()->SetBool("trime/t9", false);
  session->ApplySchema(schema);
  t9.Reset();
  type("nihao");
  check(!t9.Snapshot(session.get()).enabled && contains(u8"你好"),
        "non-T9 scheme passes through");
  ctx->Clear();
  check(api->select_schema(id, "luna_pinyin"), "switch to full pinyin");
  type("nihao");
  check(!t9.Snapshot(session.get()).enabled && contains(u8"你好"),
        "full pinyin unaffected");
  ctx->Clear();
  check(api->select_schema(id, "double_pinyin"), "switch to double pinyin");
  type("nihk");
  check(!t9.Snapshot(session.get()).enabled && contains(u8"你好"),
        "double pinyin unaffected");
  ctx->Clear();
  api->destroy_session(id);
  session.reset();
  t9.Reset();
  learning_check();
  api->finalize();
  std::cout << "All " << checks << " checks passed" << std::endl;
}
