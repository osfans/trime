# SPDX-License-Identifier: GPL-3.0-or-later

# Search by a borrowed key instead of allocating a polymorphic query entry.
# Keep the pinned submodule untouched and reject upstream source drift.
function(trime_adapt_opencc_lookup source)
  # The default implementation deliberately declines the fast prefix path.
  # Unnamed arguments keep its virtual signature and behavior unchanged.
  trime_replace_dependency_cmake("${source}/Dict.hpp"
    [=[  virtual PrefixMatchView MatchPrefixValue(const char* word,
                                           size_t len) const {]=]
    [=[  virtual PrefixMatchView MatchPrefixValue(const char*, size_t) const {]=])
  # Returning a local unique_ptr already selects its converting move. Preserve
  # ownership transfer without redundant explicit moves in the prefix matcher.
  trime_replace_dependency_cmake("${source}/PrefixMatch.cpp"
    [=[      CollectAllLeafDicts(dict, leaf.get());
      return std::move(leaf);]=]
    [=[      CollectAllLeafDicts(dict, leaf.get());
      return leaf;]=])
  trime_replace_dependency_cmake("${source}/PrefixMatch.cpp"
    [=[    return std::move(group);]=]
    [=[    return group;]=])
  trime_replace_dependency_cmake("${source}/PrefixMatch.cpp"
    [=[  leaf->AddDict(dict);
  return std::move(leaf);]=]
    [=[  leaf->AddDict(dict);
  return leaf;]=])
  trime_replace_dependency_cmake("${source}/TextDict.cpp"
    [=[#include <cassert>]=]
    [=[#include <cassert>
#include <string_view>]=])
  trime_replace_dependency_cmake("${source}/TextDict.cpp"
    [=[  std::unique_ptr<DictEntry> entry(
      new NoValueDictEntry(std::string(word, len)));
  const auto& found = std::lower_bound(lexicon->begin(), lexicon->end(), entry,
                                       DictEntry::UPtrLessThan);
  if ((found != lexicon->end()) && ((*found)->Key() == entry->Key())) {]=]
    [=[  const std::string_view key(word, len);
  const auto found = std::lower_bound(
      lexicon->begin(), lexicon->end(), key,
      [](const std::unique_ptr<DictEntry>& entry, std::string_view query) {
        return entry->KeyView() < query;
      });
  if ((found != lexicon->end()) && ((*found)->KeyView() == key)) {]=])
endfunction()
