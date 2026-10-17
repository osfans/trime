// SPDX-License-Identifier: GPL-3.0-or-later
// Compile with the pinned Rime menu/translation/candidate sources, not a guessed
// host-library private C++ ABI. No engine or application data is opened.
#include "candidate_ranking.h"

#include <iostream>
#include <set>
#include <stdexcept>

static void require(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
}

class MetadataCandidate : public rime::SimpleCandidate {
 public:
  explicit MetadataCandidate(size_t identifier)
      : rime::SimpleCandidate("fixture_learning_metadata", identifier, identifier + 4,
                              "phrase-" + std::to_string(identifier),
                              "comment-" + std::to_string(identifier),
                              "preedit-" + std::to_string(identifier)),
        identity(identifier) {
    set_quality(identifier + 0.125);
  }
  const size_t identity;
};

class LazyTranslation : public rime::Translation {
 public:
  explicit LazyTranslation(size_t count) : count_(count) {
    set_exhausted(count == 0);
  }
  bool Next() override {
    if (exhausted()) return false;
    ++next_calls;
    ++cursor_;
    current_.reset();
    set_exhausted(cursor_ == count_);
    return !exhausted();
  }
  rime::an<rime::Candidate> Peek() override {
    if (exhausted()) return nullptr;
    if (!current_) {
      current_ = rime::New<MetadataCandidate>(cursor_);
      ++created;
    }
    return current_;
  }
  size_t created = 0;
  size_t next_calls = 0;

 private:
  const size_t count_;
  size_t cursor_ = 0;
  rime::an<rime::Candidate> current_;
};

static size_t test(size_t count, size_t pinned_index) {
  auto lazy = rime::New<LazyTranslation>(count);
  auto original = rime::New<rime::Menu>();
  original->AddTranslation(lazy);
  const auto window = std::min(count, trime::PinnedTranslation::kWindow);
  auto pinned = rime::New<trime::PinnedTranslation>(
      original, pinned_index < count ? "phrase-" + std::to_string(pinned_index) : "missing-phrase");
  require(original->candidate_count() == window, "Constructor read beyond bounded 144-row prefix");
  require(lazy->created == window, "Constructor materialized the lazy tail");
  require(lazy->next_calls == window, "Constructor traversed more than its prefix");
  auto output = rime::New<rime::Menu>();
  output->AddTranslation(pinned);
  std::set<const rime::Candidate*> pointers;
  for (size_t index = 0; index < count; ++index) {
    size_t expected = index;
    if (pinned_index < window) {
      if (index == 0) expected = pinned_index;
      else if (index <= pinned_index) expected = index - 1;
    }
    auto candidate = output->GetCandidateAt(index);
    auto metadata = rime::As<MetadataCandidate>(candidate);
    require(metadata && metadata->identity == expected, "Reordered candidate has wrong retained identity");
    require(candidate == original->GetCandidateAt(expected), "Candidate objects were reconstructed");
    require(pointers.insert(candidate.get()).second, "An original candidate was duplicated");
    require(candidate->type() == "fixture_learning_metadata", "Learning type metadata changed");
    require(candidate->start() == expected && candidate->end() == expected + 4, "Input range changed");
    require(candidate->quality() == expected + 0.125, "Native quality changed");
    require(candidate->comment() == "comment-" + std::to_string(expected), "Comment changed");
    require(candidate->preedit() == "preedit-" + std::to_string(expected), "Preedit changed");
    if (index < window) require(pinned->indexOf(candidate) == index, "Retained selection maps to wrong display index");
  }
  require(pointers.size() == count, "Reordered menu lost a candidate");
  require(!output->GetCandidateAt(count), "Reordered menu invented a tail candidate");
  require(lazy->created == count, "Tail did not stay lazy until requested");
  require(lazy->next_calls == count, "Tail candidates were skipped or repeated");
  return count;
}

int main() {
  try {
    size_t checks = 0;
    size_t rows = 0;
    for (size_t count : {0, 1, 2, 143, 144, 145, 1000}) {
      for (size_t pin : {0, 1, 142, 143, 144, 999, 1001}) {
        rows += test(count, pin);
        ++checks;
      }
    }
    std::cout << "{\"passed\":true,\"cases\":" << checks
              << ",\"candidate_objects_checked\":" << rows
              << ",\"window\":" << trime::PinnedTranslation::kWindow
              << ",\"scope\":\"production ranking header with exact pinned Rime menu sources; not APK JNI or device UI\"}\n";
    return 0;
  } catch (const std::exception& failure) {
    std::cerr << failure.what() << '\n';
    return 1;
  }
}
