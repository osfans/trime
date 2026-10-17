// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <rime/menu.h>
#include <rime/translation.h>

#include <algorithm>
#include <vector>

namespace trime {

// Hold original candidate objects, including native phrase-learning metadata.
// Only the first bounded window is searched; the remaining menu stays lazy.
class PinnedTranslation : public rime::Translation {
 public:
  static constexpr size_t kWindow = 144;

  PinnedTranslation(rime::an<rime::Menu> original, const std::string& pinned)
      : original_(std::move(original)) {
    for (size_t i = 0; i < kWindow; ++i) {
      auto candidate = original_->GetCandidateAt(i);
      if (!candidate) break;
      prefix_.push_back(candidate);
    }
    auto found =
        std::find_if(prefix_.begin(), prefix_.end(),
                     [&](const auto& c) { return c->text() == pinned; });
    if (found != prefix_.end()) std::rotate(prefix_.begin(), found, found + 1);
    set_exhausted(prefix_.empty());
  }

  bool Next() override {
    if (exhausted()) return false;
    ++cursor_;
    set_exhausted(!Peek());
    return !exhausted();
  }

  rime::an<rime::Candidate> Peek() override {
    if (cursor_ < prefix_.size()) return prefix_[cursor_];
    return original_->GetCandidateAt(cursor_);
  }

  size_t indexOf(const rime::an<rime::Candidate>& candidate) const {
    auto found = std::find(prefix_.begin(), prefix_.end(), candidate);
    return found == prefix_.end() ? prefix_.size() : found - prefix_.begin();
  }

 private:
  rime::an<rime::Menu> original_;
  std::vector<rime::an<rime::Candidate>> prefix_;
  size_t cursor_ = 0;
};

}  // namespace trime
