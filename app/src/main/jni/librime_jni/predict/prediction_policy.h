// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <string>

namespace trime::prediction {

constexpr int kDefaultCandidates = 5;
constexpr int kMaximumCandidates = 8;
constexpr int kDefaultIterations = 1;
constexpr int kMaximumIterations = 2;
constexpr std::size_t kMaximumModelBytes = 64 * 1024;
constexpr std::size_t kMaximumModelCandidates = 32;
constexpr std::size_t kMaximumQueryBytes = 96;

inline int CandidateLimit(int configured) noexcept {
  return configured <= 0 ? kDefaultCandidates
                         : std::min(configured, kMaximumCandidates);
}

inline int IterationLimit(int configured) noexcept {
  return configured <= 0 ? kDefaultIterations
                         : std::min(configured, kMaximumIterations);
}

inline bool IsPredictableCommit(const std::string& type,
                               const std::string& text) noexcept {
  if (text.empty() || text.size() > kMaximumQueryBytes ||
      type == "punct" || type == "raw" || type == "thru")
    return false;
  // This compact model contains Han words. ASCII, spaces and punctuation end
  // the next-word context rather than looking like another Chinese commit.
  return std::none_of(text.begin(), text.end(), [](unsigned char ch) {
    return ch < 0x80;
  });
}

inline bool ContainsRange(std::size_t total, std::size_t offset,
                          std::size_t length) noexcept {
  return offset <= total && length <= total - offset;
}

inline bool ContainsRelativeRange(std::size_t total, std::size_t field,
                                  std::int32_t relative,
                                  std::size_t length,
                                  std::size_t* offset) noexcept {
  const auto destination = static_cast<std::int64_t>(field) + relative;
  if (!relative || destination < 0 ||
      static_cast<std::uint64_t>(destination) > total)
    return false;
  *offset = static_cast<std::size_t>(destination);
  return ContainsRange(total, *offset, length);
}

}  // namespace trime::prediction
