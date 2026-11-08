// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <rime/algo/algebra.h>

#include <array>

namespace trime {

struct T9Alternative {
  int length;
  std::string spelling;
  int sources;
};

// Single-rule letter fuzzing and single-edit digit repair are separate indexes.
class T9Assist {
 public:
  static constexpr int kRules = 9;
  static constexpr int kAllRules = (1 << kRules) - 1;
  static constexpr int kMaxInput = 7;
  static constexpr int kMaxChoices = 32;
  bool Build(rime::Config* config, const rime::Script& syllables, int options);
  std::vector<T9Alternative> Find(const std::string& input, int options) const;

 private:
  using Index = std::map<std::string, std::vector<std::string>>;
  Index exact_;
  std::array<Index, 6> fuzzy_;
};

}  // namespace trime
