// SPDX-License-Identifier: GPL-3.0-or-later
// Exact-byte lookup regression against the real adapted OpenCC library.
// The oracle scans app-owned key records; it does not reproduce lower_bound.
#include <algorithm>
#include <array>
#include <cstdint>
#include <iostream>
#include <map>
#include <set>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "DictEntry.hpp"
#include "Lexicon.hpp"
#include "TextDict.hpp"

namespace {
std::size_t assertions = 0;
std::size_t queries = 0;
std::size_t dictionaries = 0;
std::map<std::string, std::size_t> groups;

void Check(bool valid, const char* message) {
  if (!valid) {
    throw std::runtime_error(message);
  }
  ++assertions;
}

struct Record {
  std::string key;
  std::vector<std::string> values;
  const opencc::DictEntry* entry = nullptr;
};

class Dictionary {
 public:
  explicit Dictionary(std::vector<std::string> keys) {
    std::set<std::string> distinct;
    lexicon = std::make_shared<opencc::Lexicon>();
    records.reserve(keys.size());
    std::size_t maximum = 0;
    for (std::size_t i = 0; i < keys.size(); ++i) {
      Check(distinct.insert(keys[i]).second, "fixture key duplicated");
      std::vector<std::string> values;
      if (i % 3 != 0) {
        values.push_back("value-" + std::to_string(i));
      }
      if (i % 3 == 2) {
        values.push_back(std::string("second\0value", 12));
      }
      auto* entry = opencc::DictEntryFactory::New(keys[i], values);
      records.push_back({keys[i], values, entry});
      lexicon->Add(entry);  // The real lexicon exclusively owns each entry.
      maximum = (std::max)(maximum, keys[i].size());
    }
    lexicon->Sort();
    Check(lexicon->IsSorted(), "real lexicon not sorted");
    Check(lexicon->IsUnique(), "real lexicon not unique");
    dict = std::make_shared<opencc::TextDict>(lexicon);
    Check(dict->KeyMaxLength() == maximum, "maximum key byte length changed");
    ++dictionaries;
  }

  // No KeyView, comparator or binary search is used by this independent oracle.
  const Record* Linear(const char* word, std::size_t length) const {
    for (const auto& record : records) {
      if (record.key.size() == length &&
          std::equal(record.key.begin(), record.key.end(), word)) {
        return &record;
      }
    }
    return nullptr;
  }

  opencc::LexiconPtr lexicon;
  opencc::TextDictPtr dict;
  std::vector<Record> records;
};

void Query(Dictionary& dictionary, const std::string& key,
           const std::string& group) {
  // The requested span lies inside a live buffer and is followed by a non-NUL
  // byte, even for length0. No C-string terminator is available at its boundary.
  std::vector<char> buffer{'<', '['};
  buffer.insert(buffer.end(), key.begin(), key.end());
  buffer.push_back(']');
  buffer.push_back('>');
  const auto original = buffer;
  const char* word = buffer.data() + 2;
  const Record* expected = dictionary.Linear(word, key.size());
  const auto result = dictionary.dict->Match(word, key.size());
  Check(result.IsNull() == (expected == nullptr), "lookup differs from linear oracle");
  Check(buffer == original, "lookup changed caller buffer");
  ++queries;
  ++groups[group];
  if (expected == nullptr) {
    return;
  }
  Check(result.Get() == expected->entry, "lookup returned a different entry object");
  Check(result.Get()->Key() == expected->key, "entry key bytes changed");
  Check(result.Get()->KeyView() == std::string_view(expected->key), "entry key view changed");
  Check(result.Get()->Values() == expected->values, "entry values changed");
  Check(result.Get()->NumValues() == expected->values.size(), "entry value count changed");
  const std::string& default_value = expected->values.empty() ? expected->key : expected->values.front();
  Check(result.Get()->GetDefault() == default_value, "default entry value changed");
  Check(result.Get()->GetDefaultView() == std::string_view(default_value), "default value view changed");
  // Returned entries must belong to the dictionary, not borrow the query bytes.
  std::fill(buffer.begin(), buffer.end(), '!');
  Check(result.Get() == expected->entry && result.Get()->Key() == expected->key,
        "entry identity or bytes depended on query-buffer lifetime");
}

std::vector<std::string> MainKeys(bool empty_key) {
  std::vector<std::string> keys{
      "A", "a", "ab", "abc", "m", "z", "zz", "zzzz",
      std::string("\0", 1), std::string("\0a", 2),
      std::string("a\0", 2), std::string("a\0b", 3),
      "\xc3\xa9", "e\xcc\x81",  // two-byte NFC and distinct combining form
      "\xe6\xbc\xa2", "\xe6\xbc\xa2\xe8\xaa\x9e",  // Han, three-byte units
      "\xe6\xb1\x89", "\xf0\xa0\x80\x80",  // four-byte U+20000
      std::string(257, 'q'), std::string("\xff", 1)};
  if (empty_key) {
    keys.push_back("");
  }
  return keys;
}

void EmptyDictionary() {
  Dictionary empty({});
  for (const auto& key : MainKeys(true)) {
    Query(empty, key, "empty_dictionary");
  }
}

void MainDictionary(bool empty_key) {
  Dictionary dictionary(MainKeys(empty_key));
  const auto keys = MainKeys(empty_key);
  for (const auto& key : keys) {
    Query(dictionary, key, "stored_key_identity");
    for (std::size_t length = 0; length <= key.size(); ++length) {
      Query(dictionary, key.substr(0, length), "bounded_prefix_lengths");
    }
    Query(dictionary, key + "!", "extended_key_miss");
    Query(dictionary, "!" + key, "prepended_key_miss");
  }
  for (const auto& key : std::vector<std::string>{
           "", "0", "B", "aa", "abd", "n", "zzz", "zzzzz",
           std::string("\0\0", 2), std::string("a\0c", 3),
           "\xe6\xbc", "\xf0\xa0\x80", std::string(4097, 'x')}) {
    Query(dictionary, key, "miss_and_zero_length");
  }
  for (const auto& key : std::vector<std::string>{
           "\xc3\xa9", "e\xcc\x81", "\xe6\xbc\xa2",
           "\xe6\xbc\xa2\xe8\xaa\x9e", "\xe6\xb1\x89", "\xf0\xa0\x80\x80"}) {
    Query(dictionary, key, "utf8_exact_bytes");
  }
  Query(dictionary, "a", "embedded_nul_length_distinction");
  Query(dictionary, std::string("a\0", 2), "embedded_nul_length_distinction");
  Query(dictionary, std::string("a\0b", 3), "embedded_nul_length_distinction");
  Query(dictionary, std::string("\0", 1), "embedded_nul_length_distinction");
  Query(dictionary, "", empty_key ? "present_empty_key" : "absent_empty_key");

  // A deterministic corpus includes embedded NUL and high bytes independently
  // of the sorted fixture. No UTF8 decoding/normalization is expected here.
  std::uint32_t state = 0x13579bdfU;
  for (std::size_t trial = 0; trial < 512; ++trial) {
    std::string query;
    const std::size_t length = trial % 13;
    for (std::size_t i = 0; i < length; ++i) {
      state = state * 1664525U + 1013904223U;
      query.push_back(static_cast<char>((state >> 24) & 0xffU));
    }
    Query(dictionary, query, "deterministic_binary_queries");
  }
  for (const auto& record : dictionary.records) {
    const auto view = record.entry->KeyView();
    const auto result = dictionary.dict->Match(view.data(), view.size());
    Check(!result.IsNull() && result.Get() == record.entry,
          "dictionary-owned query alias changed lookup identity");
    ++queries;
    ++groups["dictionary_owned_query_alias"];
  }
}

void AllSingleBytes() {
  std::vector<std::string> keys;
  for (unsigned int byte = 0; byte < 256; ++byte) {
    keys.emplace_back(1, static_cast<char>(byte));
  }
  Dictionary dictionary(std::move(keys));
  for (unsigned int byte = 0; byte < 256; ++byte) {
    const std::string key(1, static_cast<char>(byte));
    Query(dictionary, key, "all_256_single_bytes");
    Query(dictionary, key + "!", "single_byte_extended_miss");
  }
  Query(dictionary, "", "byte_dictionary_empty_miss");
}

void Print(bool passed) {
  std::cout << "{\"passed\":" << (passed ? "true" : "false")
            << ",\"dictionaries\":" << dictionaries << ",\"queries\":" << queries
            << ",\"assertions\":" << assertions << ",\"linear_oracle\":true"
            << ",\"real_library\":true,\"groups\":{";
  bool first = true;
  for (const auto& pair : groups) {
    if (!first) {
      std::cout << ',';
    }
    first = false;
    std::cout << '"' << pair.first << "\":" << pair.second;
  }
  std::cout << "}}\n";
}
}  // namespace

int main() {
  try {
    EmptyDictionary();
    MainDictionary(false);
    MainDictionary(true);
    AllSingleBytes();
    Check(dictionaries == 4, "a lookup dictionary cohort did not complete");
    Check(groups.at("all_256_single_bytes") == 256, "not all byte values tested");
    Check(groups.at("present_empty_key") == 1 && groups.at("absent_empty_key") == 1,
          "both zero-length outcomes were not tested");
    Print(true);
    return 0;
  } catch (const std::exception& error) {
    Print(false);
    std::cerr << error.what() << '\n';
    return 1;
  }
}
