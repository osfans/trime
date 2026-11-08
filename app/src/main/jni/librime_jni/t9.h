// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <rime/composition.h>
#include <rime/dict/dictionary.h>
#include <rime/service.h>

#include "t9_assist.h"

namespace trime {

struct T9Span {
  int start = 0;
  int end = 0;
  std::string spelling;
  bool completion = false;
  bool locked = false;
  int sources = 0;
};

struct T9Snapshot {
  bool enabled = false;
  int revision = 0;
  std::string input;
  int focus = 0;
  bool can_undo = false;
  std::vector<T9Span> segments;
  std::vector<T9Span> choices;
};

// Owned by one Rime session, accessed exclusively on the Rime dispatcher.
class T9 {
 public:
  T9Snapshot Snapshot(rime::Session* session);
  bool Act(rime::Session* session, int revision, int action, int start, int end,
           const std::string& spelling);
  bool ProcessKey(rime::Session* session, int keycode, int mask);
  bool GetPreedit(rime::Session* session, rime::Preedit* preedit);
  bool MoveCaret(rime::Session* session, size_t display_position);
  std::string RawInput(rime::Session* session);
  std::string CommitText(std::string text) const;
  void Clear();
  void Reset();
  void SetAssistOptions(int options);

 private:
  struct Edit {
    std::string input;
    std::vector<T9Span> locks;
    int caret = 0;
    int focus = 0;
  };
  bool Sync(rime::Session* session);
  void BuildQuery();
  void Render(rime::Context* ctx);
  void Save();
  void Replace(int start, int end, const std::string& text);
  std::vector<T9Span> Edges() const;
  bool Exact(const std::string& spelling, int syllable) const;
  void AddAlternatives(T9Snapshot* snapshot);
  int RawPosition(size_t pos) const;

  rime::Schema* schema_ = nullptr;
  rime::the<rime::Dictionary> dictionary_;
  bool enabled_ = false;
  bool completion_ = true;
  std::string delimiters_ = " '";
  Edit edit_;
  std::string rendered_;
  std::vector<int> engine_to_raw_{0};
  std::vector<int> raw_to_engine_{0};
  std::vector<int> display_to_raw_{0};
  std::vector<Edit> history_;
  int revision_ = 0;
  int confirmed_ = 0;
  int assist_options_ = 0;
  bool assist_attempted_ = false;
  rime::the<T9Assist> assist_;
};

}  // namespace trime
