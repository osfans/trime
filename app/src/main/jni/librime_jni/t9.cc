// SPDX-License-Identifier: GPL-3.0-or-later
#include "t9.h"

#include <rime/algo/syllabifier.h>
#include <rime/candidate.h>
#include <rime/context.h>
#include <rime/key_event.h>
#include <rime/schema.h>
#include <rime/ticket.h>

#include <algorithm>
#include <tuple>

namespace trime {
using namespace rime;

void T9::Reset() {
  schema_ = nullptr;
  dictionary_.reset();
  assist_.reset();
  assist_attempted_ = false;
  enabled_ = false;
  completion_ = true;
  delimiters_ = " '";
  Clear();
}

void T9::Clear() {
  // Ending a composition must not rebuild schema-dependent indexes on the next
  // key.
  edit_ = {};
  rendered_.clear();
  engine_to_raw_ = {0};
  raw_to_engine_ = {0};
  display_to_raw_ = {0};
  history_.clear();
  confirmed_ = 0;
  ++revision_;
}

int T9::RawPosition(size_t pos) const {
  return engine_to_raw_[std::min(pos, engine_to_raw_.size() - 1)];
}

void T9::SetAssistOptions(int options) {
  options &= T9Assist::kAllRules;
  if (assist_options_ == options) return;
  assist_options_ = options;
  assist_.reset();
  assist_attempted_ = false;
  ++revision_;  // A click from the previous settings must not apply a disabled
                // rule.
}

bool T9::Sync(Session* session) {
  if (!session) return false;
  if (schema_ != session->schema()) {
    Reset();
    schema_ = session->schema();
    auto config = schema_->config();
    config->GetBool("trime/t9", &enabled_);
    if (enabled_) {
      auto component = Dictionary::Require("dictionary");
      if (component)
        dictionary_.reset(component->Create(Ticket(schema_, "translator")));
      enabled_ = dictionary_ && dictionary_->Load();
      config->GetBool("translator/enable_completion", &completion_);
      config->GetString("speller/delimiter", &delimiters_);
      std::string alphabet;
      config->GetString("speller/alphabet", &alphabet);
      enabled_ = enabled_ && alphabet.find('~') != std::string::npos;
    }
  }
  auto ctx = session->context();
  if (!enabled_) return false;
  const auto& actual = ctx->input();
  if (actual != rendered_) {
    if (!edit_.locks.empty() && !actual.empty()) {
      // Reconcile edits performed by Rime itself (e.g. editor/key-binder
      // actions).
      size_t begin = 0, old_end = rendered_.size(), new_end = actual.size();
      while (begin < old_end && begin < new_end &&
             rendered_[begin] == actual[begin])
        ++begin;
      while (old_end > begin && new_end > begin &&
             rendered_[old_end - 1] == actual[new_end - 1]) {
        --old_end;
        --new_end;
      }
      auto replacement = actual.substr(begin, new_end - begin);
      // Never retain fragments of the private exact-spelling envelope.
      replacement.erase(
          std::remove(replacement.begin(), replacement.end(), '~'),
          replacement.end());
      int start = RawPosition(begin), end = RawPosition(old_end);
      Replace(start, end, replacement);
      edit_.caret = start + replacement.size();
      history_.clear();
      Render(ctx);
    } else {
      edit_ = {actual, {}, static_cast<int>(ctx->caret_pos()), 0};
      rendered_ = actual;
      engine_to_raw_.resize(actual.size() + 1);
      raw_to_engine_.resize(actual.size() + 1);
      for (int i = 0; i <= static_cast<int>(actual.size()); ++i)
        engine_to_raw_[i] = raw_to_engine_[i] = i;
      history_.clear();
      ++revision_;
    }
  }
  edit_.caret = RawPosition(ctx->caret_pos());
  int confirmed = RawPosition(ctx->composition().GetConfirmedPosition());
  if (confirmed != confirmed_) {
    // Syllable undo must not undo a Hanzi selection made through Rime's menu.
    history_.clear();
    confirmed_ = confirmed;
    ++revision_;
  }
  if (edit_.focus < confirmed) edit_.focus = confirmed;
  return !ctx->get_option("ascii_mode") &&
         std::all_of(edit_.input.begin(), edit_.input.end(), [](char c) {
           return (c >= '2' && c <= '9') || (c >= 'a' && c <= 'z') ||
                  c == '\'' || c == ' ';
         });
}

void T9::Save() {
  if (history_.size() == 32) history_.erase(history_.begin());
  history_.push_back(edit_);
}

void T9::BuildQuery() {
  rendered_.clear();
  engine_to_raw_ = {0};
  raw_to_engine_.assign(edit_.input.size() + 1, 0);
  size_t lock_index = 0;
  for (int pos = 0; pos < static_cast<int>(edit_.input.size());) {
    raw_to_engine_[pos] = rendered_.size();
    if (lock_index < edit_.locks.size() &&
        edit_.locks[lock_index].start == pos) {
      const auto& lock = edit_.locks[lock_index++];
      std::string atom = "~" + lock.spelling + "~";
      int base = rendered_.size();
      for (size_t i = 0; i < atom.size(); ++i) {
        rendered_ += atom[i];
        engine_to_raw_.push_back(i + 1 == atom.size() ? lock.end : lock.start);
      }
      for (int i = lock.start + 1; i < lock.end; ++i) raw_to_engine_[i] = base;
      pos = lock.end;
      raw_to_engine_[pos] = rendered_.size();
    } else {
      rendered_ += edit_.input[pos++];
      engine_to_raw_.push_back(pos);
      raw_to_engine_[pos] = rendered_.size();
    }
  }
}

void T9::Render(Context* ctx) {
  BuildQuery();
  edit_.caret =
      std::clamp(edit_.caret, 0, static_cast<int>(edit_.input.size()));
  // set_input recomposes through the original translator; no synthetic key or
  // commit.
  ctx->set_input(rendered_);
  ctx->set_caret_pos(raw_to_engine_[edit_.caret]);
  ++revision_;
}

void T9::Replace(int start, int end, const std::string& text) {
  int delta = static_cast<int>(text.size()) - (end - start);
  edit_.locks.erase(
      std::remove_if(edit_.locks.begin(), edit_.locks.end(),
                     [&](const auto& lock) {
                       return start == end
                                  ? (lock.start < start && start < lock.end)
                                  : (lock.start < end && start < lock.end);
                     }),
      edit_.locks.end());
  for (auto& lock : edit_.locks) {
    if (lock.start >= end) {
      lock.start += delta;
      lock.end += delta;
    }
  }
  edit_.input.replace(start, end - start, text);
  edit_.focus = std::min(start, static_cast<int>(edit_.input.size()));
}

bool T9::Exact(const std::string& spelling, int syllable) const {
  int value;
  if (!dictionary_->prism()->GetValue("~" + spelling + "~", &value))
    return false;
  auto access = dictionary_->prism()->QuerySpelling(value);
  bool found = false;
  for (; !access.exhausted(); access.Next()) {
    if (access.syllable_id() != syllable || access.properties().is_correction)
      return false;
    found = true;
  }
  return found;
}

std::vector<T9Span> T9::Edges() const {
  SyllableGraph graph;
  Syllabifier(delimiters_, completion_)
      .BuildSyllableGraph(rendered_, *dictionary_->prism(), &graph);
  std::vector<T9Span> result;
  for (const auto& [begin, ends] : graph.edges) {
    for (const auto& [end, spellings] : ends) {
      int start = RawPosition(begin), finish = RawPosition(end);
      while (start < finish &&
             delimiters_.find(edit_.input[start]) != std::string::npos)
        ++start;
      while (finish > start &&
             delimiters_.find(edit_.input[finish - 1]) != std::string::npos)
        --finish;
      if (start >= finish) continue;
      for (const auto& [syllable, properties] : spellings) {
        auto spelling = dictionary_->primary_table()->GetSyllableById(syllable);
        if (!Exact(spelling, syllable)) continue;
        result.push_back(
            {start, finish, spelling, properties.type == kCompletion, false});
      }
    }
  }
  // Rime's sentence graph only completes after its farthest exact match. The
  // prism also exposes whole-syllable completions (e.g. 646 -> ming), even when
  // 64 already forms a shorter syllable. Enumerate dictionary keys, not
  // letters.
  if (completion_) {
    for (const auto& [begin, type] : graph.vertices) {
      auto prefix = rendered_.substr(begin);
      if (prefix.empty() || prefix.find_first_of("~ '") != std::string::npos)
        continue;
      std::vector<Prism::Match> matches;
      dictionary_->prism()->ExpandSearch(prefix, &matches, 0);
      for (const auto& match : matches) {
        if (match.length <= prefix.size()) continue;
        for (auto access = dictionary_->prism()->QuerySpelling(match.value);
             !access.exhausted(); access.Next()) {
          auto sid = access.syllable_id();
          auto spelling = dictionary_->primary_table()->GetSyllableById(sid);
          if (Exact(spelling, sid))
            result.push_back({RawPosition(begin),
                              static_cast<int>(edit_.input.size()), spelling,
                              true, false});
        }
      }
    }
  }
  std::sort(result.begin(), result.end(), [](const auto& a, const auto& b) {
    return std::tie(a.start, a.completion, a.end, a.spelling) <
           std::tie(b.start, b.completion, b.end, b.spelling);
  });
  result.erase(std::unique(result.begin(), result.end(),
                           [](const auto& a, const auto& b) {
                             return a.start == b.start && a.end == b.end &&
                                    a.spelling == b.spelling;
                           }),
               result.end());
  return result;
}

T9Snapshot T9::Snapshot(Session* session) {
  T9Snapshot result;
  if (!Sync(session)) return result;
  result.enabled = !edit_.input.empty() || !history_.empty();
  result.revision = revision_;
  result.input = edit_.input;
  result.can_undo = !history_.empty();
  if (edit_.input.empty()) return result;
  auto edges = Edges();
  int confirmed =
      RawPosition(session->context()->composition().GetConfirmedPosition());
  for (int pos = confirmed; pos < static_cast<int>(edit_.input.size());) {
    if (delimiters_.find(edit_.input[pos]) != std::string::npos) {
      ++pos;
      continue;
    }
    auto locked = std::find_if(edit_.locks.begin(), edit_.locks.end(),
                               [&](const auto& l) { return l.start == pos; });
    if (locked != edit_.locks.end()) {
      result.segments.push_back(*locked);
      pos = locked->end;
      continue;
    }
    int end = pos + 1;
    for (const auto& edge : edges)
      if (edge.start == pos) end = std::max(end, edge.end);
    // A tentative parse must never swallow an independently locked syllable.
    for (const auto& lock : edit_.locks)
      if (lock.start > pos) end = std::min(end, lock.start);
    result.segments.push_back(
        {pos, end, edit_.input.substr(pos, end - pos), false, false});
    pos = end;
  }
  if (result.segments.empty()) return result;
  while (edit_.focus < static_cast<int>(edit_.input.size()) &&
         delimiters_.find(edit_.input[edit_.focus]) != std::string::npos)
    ++edit_.focus;
  auto focus = std::find_if(
      result.segments.begin(), result.segments.end(), [&](const auto& s) {
        return s.start <= edit_.focus && edit_.focus < s.end;
      });
  if (focus == result.segments.end()) focus = std::prev(result.segments.end());
  edit_.focus = focus->start;
  // For a locked segment, query alternatives using its original digits while
  // retaining all other locks. This does not touch the live Rime context.
  auto saved_locks = edit_.locks;
  if (focus->locked) {
    edit_.locks.erase(
        std::remove_if(edit_.locks.begin(), edit_.locks.end(),
                       [&](const auto& l) { return l.start == edit_.focus; }),
        edit_.locks.end());
  }
  if (focus->locked) {
    BuildQuery();
    edges = Edges();
  }
  for (const auto& edge : edges) {
    if (edge.start != edit_.focus) continue;
    bool overlap = std::any_of(
        edit_.locks.begin(), edit_.locks.end(), [&](const auto& lock) {
          return lock.start < edge.end && edge.start < lock.end;
        });
    if (!overlap) result.choices.push_back(edge);
  }
  edit_.locks = std::move(saved_locks);
  BuildQuery();
  result.enabled = true;
  result.revision = revision_;
  result.input = edit_.input;
  result.focus = edit_.focus;
  result.can_undo = !history_.empty();
  AddAlternatives(&result);
  return result;
}

void T9::AddAlternatives(T9Snapshot* snapshot) {
  if (!assist_options_) return;
  if (!assist_attempted_) {
    assist_attempted_ = true;
    rime::Script syllables;
    const auto count = dictionary_->primary_table()->metadata()->num_syllables;
    if (count > 4096) return;
    for (uint32_t id = 0; id < count; ++id) {
      auto spelling = dictionary_->primary_table()->GetSyllableById(id);
      if (spelling.empty() || spelling.size() > 6 ||
          !std::all_of(spelling.begin(), spelling.end(),
                       [](char c) { return c >= 'a' && c <= 'z'; }) ||
          !Exact(spelling, id))
        continue;
      syllables.AddSyllable(spelling);
    }
    auto index = std::make_unique<T9Assist>();
    if (index->Build(schema_->config(), syllables, assist_options_))
      assist_ = std::move(index);
  }
  if (!assist_) return;
  int start = snapshot->focus;
  int end = std::min(static_cast<int>(edit_.input.size()),
                     start + T9Assist::kMaxInput);
  for (const auto& lock : edit_.locks)
    if (lock.start > start) end = std::min(end, lock.start);
  auto alternatives =
      assist_->Find(edit_.input.substr(start, end - start), assist_options_);
  int added = 0;
  for (const auto& alternative : alternatives) {
    const int finish = start + alternative.length;
    if (std::any_of(snapshot->choices.begin(), snapshot->choices.end(),
                    [&](const auto& choice) {
                      return choice.start == start && choice.end == finish &&
                             choice.spelling == alternative.spelling;
                    }))
      continue;
    snapshot->choices.push_back({start, finish, alternative.spelling, false,
                                 false, alternative.sources});
    if (++added == T9Assist::kMaxChoices) break;
  }
}

bool T9::Act(Session* session, int revision, int action, int start, int end,
             const std::string& spelling) {
  auto state = Snapshot(session);
  if (!state.enabled || revision != revision_) return false;
  if (action == 0) {
    auto segment =
        std::find_if(state.segments.begin(), state.segments.end(),
                     [&](const auto& s) { return s.start == start; });
    if (segment == state.segments.end()) return false;
    edit_.focus = start;
    edit_.caret = segment->end;
    session->context()->set_caret_pos(raw_to_engine_[edit_.caret]);
    ++revision_;
    return true;
  }
  if (action == 3) {
    if (history_.empty()) return false;
    edit_ = history_.back();
    history_.pop_back();
    Render(session->context());
    return true;
  }
  if (action == 4) {
    session->context()->AbortComposition();
    Clear();
    return true;
  }
  if (action == 2) {
    auto lock = std::find_if(edit_.locks.begin(), edit_.locks.end(),
                             [&](const auto& l) { return l.start == start; });
    if (lock == edit_.locks.end()) return false;
    Save();
    edit_.caret = lock->end;
    edit_.locks.erase(lock);
    edit_.focus = start;
  } else if (action == 1) {
    auto choice = std::find_if(
        state.choices.begin(), state.choices.end(), [&](const auto& c) {
          return c.start == start && c.end == end && c.spelling == spelling;
        });
    if (choice == state.choices.end()) return false;
    Save();
    edit_.locks.erase(
        std::remove_if(edit_.locks.begin(), edit_.locks.end(),
                       [&](const auto& l) { return l.start == start; }),
        edit_.locks.end());
    auto lock = *choice;
    lock.locked = true;
    edit_.locks.push_back(lock);
    std::sort(edit_.locks.begin(), edit_.locks.end(),
              [](const auto& a, const auto& b) { return a.start < b.start; });
    if (start <= edit_.caret && edit_.caret <= end) edit_.caret = end;
    edit_.focus = end;
  } else {
    return false;
  }
  Render(session->context());
  return true;
}

bool T9::ProcessKey(Session* session, int keycode, int mask) {
  if (!Sync(session)) return false;
  KeyEvent key(keycode, mask);
  if (key.ctrl() && !key.release() && keycode == 'z') {
    if (!history_.empty()) return Act(session, revision_, 3, 0, 0, "");
    return !edit_.input.empty();
  }
  if (key.ctrl() && !key.release() && keycode == 'y')
    return !edit_.input.empty() || !history_.empty();
  if (mask != 0) return false;
  auto passthrough = [&] {
    if (edit_.input.empty() && !history_.empty()) {
      history_.clear();
      ++revision_;
    }
    return false;
  };
  auto ctx = session->context();
  int caret = edit_.caret;
  if (keycode == XK_Return && !edit_.input.empty()) {
    // The schema's raw-code commit stops at the caret unless it is at the end.
    edit_.caret = edit_.input.size();
    edit_.focus = edit_.caret;
    ctx->set_caret_pos(raw_to_engine_.back());
    ++revision_;
    return false;
  }
  if (keycode == XK_Escape && (!edit_.input.empty() || !history_.empty())) {
    ctx->AbortComposition();
    Clear();
    return true;
  }
  if (keycode == XK_Left || keycode == XK_Right || keycode == XK_Home ||
      keycode == XK_End) {
    if (edit_.input.empty()) return passthrough();
    int next = keycode == XK_Home ? 0
               : keycode == XK_End
                   ? edit_.input.size()
                   : std::clamp(caret + (keycode == XK_Left ? -1 : 1), 0,
                                static_cast<int>(edit_.input.size()));
    for (const auto& lock : edit_.locks) {
      if (lock.start < next && next < lock.end)
        next = keycode == XK_Left ? lock.start : lock.end;
    }
    ctx->set_caret_pos(raw_to_engine_[next]);
    edit_.caret = next;
    edit_.focus = next;
    ++revision_;
    return true;
  }
  if (keycode == XK_BackSpace || keycode == XK_Delete) {
    if (edit_.input.empty()) {
      return passthrough();
    }
    int target = keycode == XK_BackSpace ? caret - 1 : caret;
    if (target < 0 || target >= static_cast<int>(edit_.input.size()))
      // At the composition edge, never delete already committed editor text.
      return !edit_.input.empty();
    Save();
    auto lock = std::find_if(
        edit_.locks.begin(), edit_.locks.end(),
        [&](const auto& l) { return l.start <= target && target < l.end; });
    if (lock != edit_.locks.end()) {
      edit_.focus = lock->start;
      edit_.locks.erase(lock);
    } else {
      Replace(target, target + 1, "");
      edit_.caret = keycode == XK_BackSpace ? target : caret;
    }
  } else if ((keycode >= '2' && keycode <= '9') ||
             (keycode >= 'a' && keycode <= 'z') || keycode == '\'') {
    if (keycode == '\'' && edit_.input.empty()) return passthrough();
    const int focus = edit_.focus;
    const bool append = caret == static_cast<int>(edit_.input.size());
    const bool focused_lock =
        std::any_of(edit_.locks.begin(), edit_.locks.end(),
                    [&](const auto& lock) { return lock.start == focus; });
    Save();
    Replace(caret, caret, std::string(1, static_cast<char>(keycode)));
    if (append && !focused_lock) edit_.focus = focus;
    edit_.caret = caret + 1;
  } else {
    return passthrough();
  }
  Render(ctx);
  return true;
}

bool T9::GetPreedit(Session* session, Preedit* preedit) {
  if (!Sync(session) || edit_.input.empty()) return false;
  std::vector<T9Span> chunks;
  auto ctx = session->context();
  int confirmed = 0;
  for (const auto& segment : ctx->composition()) {
    if (segment.status < Segment::kSelected) continue;
    auto candidate = segment.GetSelectedCandidate();
    if (!candidate) continue;
    int start = RawPosition(segment.start), end = RawPosition(candidate->end());
    if (end > start) chunks.push_back({start, end, candidate->text()});
    confirmed = end;
  }
  for (const auto& lock : edit_.locks)
    if (lock.start >= confirmed) chunks.push_back(lock);
  std::sort(chunks.begin(), chunks.end(),
            [](const auto& a, const auto& b) { return a.start < b.start; });
  preedit->text.clear();
  display_to_raw_ = {0};
  std::vector<size_t> raw_to_display(edit_.input.size() + 1, 0);
  size_t ci = 0;
  for (int pos = 0; pos < static_cast<int>(edit_.input.size());) {
    bool chunk = ci < chunks.size() && chunks[ci].start == pos;
    int end = chunk ? chunks[ci].end : pos + 1;
    auto label = chunk ? chunks[ci++].spelling : edit_.input.substr(pos, 1);
    bool follows_chunk = ci > 0 && chunks[ci - 1].end == pos;
    if (pos > 0 && (chunk || follows_chunk) && !preedit->text.empty() &&
        delimiters_.find(preedit->text.back()) == std::string::npos &&
        delimiters_.find(label.front()) == std::string::npos) {
      preedit->text += ' ';
      display_to_raw_.push_back(pos);
    }
    for (int p = pos; p < end; ++p) raw_to_display[p] = preedit->text.size();
    for (size_t i = 0; i < label.size(); ++i) {
      preedit->text += label[i];
      display_to_raw_.push_back(i + 1 == label.size() ? end : pos);
    }
    pos = end;
    raw_to_display[pos] = preedit->text.size();
  }
  preedit->caret_pos = raw_to_display[edit_.caret];
  preedit->sel_start = raw_to_display[std::min(edit_.focus, edit_.caret)];
  preedit->sel_end = preedit->caret_pos;
  return true;
}

bool T9::MoveCaret(Session* session, size_t display_position) {
  Preedit preedit;
  if (!GetPreedit(session, &preedit)) return false;
  int raw =
      display_to_raw_[std::min(display_position, display_to_raw_.size() - 1)];
  session->context()->set_caret_pos(raw_to_engine_[raw]);
  edit_.caret = raw;
  edit_.focus = raw;
  ++revision_;
  return true;
}

std::string T9::RawInput(Session* session) {
  if (!session) return "";
  Sync(session);
  return enabled_ ? edit_.input : session->context()->input();
}

std::string T9::CommitText(std::string text) const {
  // Consume the commit before syncing the now-cleared context. Raw-code commits
  // can contain confirmed Hanzi followed by still-unconfirmed exact envelopes.
  size_t pos = 0;
  for (const auto& lock : edit_.locks) {
    if (lock.start < confirmed_) continue;
    const auto atom = "~" + lock.spelling + "~";
    auto found = text.find(atom, pos);
    if (found == std::string::npos) continue;
    auto raw = edit_.input.substr(lock.start, lock.end - lock.start);
    text.replace(found, atom.size(), raw);
    pos = found + raw.size();
  }
  return text;
}
}  // namespace trime
