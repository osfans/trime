// SPDX-License-Identifier: GPL-3.0-or-later
#include "t9_assist.h"

#include <algorithm>
#include <cstdlib>
#include <tuple>

namespace trime {
namespace {
bool Digits(const std::string& text) {
  return !text.empty() && text.size() <= T9Assist::kMaxInput &&
         std::all_of(text.begin(), text.end(),
                     [](char c) { return c >= '2' && c <= '9'; });
}

bool Adjacent(char a, char b) {
  // Edge-sharing keys in the physical 123/456/789 grid; 1 is not a code key.
  int x = a - '1', y = b - '1';
  return std::abs(x / 3 - y / 3) + std::abs(x % 3 - y % 3) == 1;
}
}  // namespace

bool T9Assist::Build(rime::Config* config, const rime::Script& syllables,
                     int options) {
  exact_.clear();
  for (auto& index : fuzzy_) index.clear();
  if (!config || syllables.size() > 4096) return false;
  rime::Projection numeric;
  if (!numeric.Load(config->GetList("speller/algebra"))) return false;
  struct Origin {
    int index;
    std::string spelling;
  };
  std::map<std::string, Origin> origins;
  rime::Script combined;
  auto append = [&](const rime::Script& script, int index) {
    for (const auto& [key, spellings] : script) {
      for (auto spelling : spellings) {
        if (index && spelling.str == key) continue;
        // Private build-time identities keep each rule's properties
        // independent.
        auto token = std::to_string(index) + ":" + spelling.str;
        origins.emplace(token, Origin{index, spelling.str});
        spelling.str = std::move(token);
        combined[key].push_back(std::move(spelling));
      }
    }
  };
  append(syllables, 0);
  const char* names[] = {"nl", "z_zh", "c_ch", "s_sh", "en_eng", "in_ing"};
  for (int rule = 0; rule < 6; ++rule) {
    if (!(options & (1 << rule))) continue;
    rime::Projection fuzzy;
    if (!fuzzy.Load(
            config->GetList(std::string("trime/t9_fuzzy/") + names[rule])))
      continue;
    // Start from canonical letters for EVERY rule, never from another fuzzy
    // index.
    auto script = syllables;
    fuzzy.Apply(&script);
    append(script, rule + 1);
  }
  // Project each shared letter key once, preserving canonical spelling and
  // source.
  numeric.Apply(&combined);
  std::array<Index, 7> indexes;
  std::array<size_t, 7> counts{};
  for (const auto& [key, spellings] : combined) {
    if (!Digits(key) || key.size() > 6) continue;
    for (const auto& spelling : spellings) {
      if (spelling.properties.type != rime::kNormalSpelling ||
          spelling.properties.is_correction)
        continue;
      const auto& origin = origins.at(spelling.str);
      if (++counts[origin.index] > 8192) return false;
      indexes[origin.index][key].push_back(origin.spelling);
    }
  }
  for (auto& index : indexes) {
    for (auto& [key, values] : index) {
      std::sort(values.begin(), values.end());
      values.erase(std::unique(values.begin(), values.end()), values.end());
    }
  }
  if (indexes[0].empty()) return false;
  exact_ = std::move(indexes[0]);
  for (int rule = 0; rule < 6; ++rule)
    fuzzy_[rule] = std::move(indexes[rule + 1]);
  return true;
}

std::vector<T9Alternative> T9Assist::Find(const std::string& input,
                                          int options) const {
  std::map<std::pair<int, std::string>, int> matches;
  const int length = std::min(static_cast<int>(input.size()), kMaxInput);
  for (int end = 1; end <= length; ++end) {
    auto key = input.substr(0, end);
    if (!Digits(key)) break;
    auto add = [&](const Index& index, const std::string& query, int source) {
      auto found = index.find(query);
      if (found == index.end()) return;
      auto exact = exact_.find(key);
      for (const auto& spelling : found->second) {
        if (exact != exact_.end() &&
            std::binary_search(exact->second.begin(), exact->second.end(),
                               spelling))
          continue;
        matches[{end, spelling}] |= source;
      }
    };
    for (int rule = 0; rule < 6; ++rule)
      if (options & (1 << rule)) add(fuzzy_[rule], key, 1 << rule);
    if (options & (1 << 6)) {
      for (int pos = 0; pos < end; ++pos) {
        auto corrected = key;
        for (char digit = '2'; digit <= '9'; ++digit) {
          if (!Adjacent(key[pos], digit)) continue;
          corrected[pos] = digit;
          add(exact_, corrected, 1 << 6);
        }
      }
    }
    if (options & (1 << 7)) {
      for (int pos = 0; pos <= end; ++pos) {
        for (char digit = '2'; digit <= '9'; ++digit) {
          auto corrected = key;
          corrected.insert(pos, 1, digit);
          add(exact_, corrected, 1 << 7);
        }
      }
    }
    if (options & (1 << 8)) {
      for (int pos = 1; pos < end; ++pos) {
        if (key[pos] != key[pos - 1]) continue;
        auto corrected = key;
        corrected.erase(pos, 1);
        add(exact_, corrected, 1 << 8);
      }
    }
  }
  std::vector<T9Alternative> result;
  for (const auto& [key, sources] : matches)
    result.push_back({key.first, key.second, sources});
  // Prefer corrections covering more of the focused input, with a stable tie
  // order.
  std::sort(result.begin(), result.end(), [](const auto& a, const auto& b) {
    if (a.length != b.length) return a.length > b.length;
    return std::tie(a.sources, a.spelling) < std::tie(b.sources, b.spelling);
  });
  return result;
}
}  // namespace trime
