// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2023, RIME Developers
// Derived from librime-predict; Trime modifications retain the BSD license.
// See LICENSE in this directory for the conditions and disclaimer.
// Trime overlay; preserves the pinned model wire format and builder API.
#ifndef RIME_PREDICT_DB_H_
#define RIME_PREDICT_DB_H_

#include <darts.h>
#include <rime/dict/mapped_file.h>
#include <rime/dict/string_table.h>
#include <rime/dict/table.h>
#include <rime/resource.h>

namespace rime {
namespace predict {
struct Metadata {
  static const int kFormatMaxLength = 32;
  char format[kFormatMaxLength];
  uint32_t db_checksum;
  OffsetPtr<char> key_trie;
  uint32_t key_trie_size;
  OffsetPtr<char> value_trie;
  uint32_t value_trie_size;
};
using Candidates = ::rime::Array<::rime::table::Entry>;
struct RawEntry {
  string text;
  double weight;
};
using RawData = map<string, vector<RawEntry>>;
}  // namespace predict

class PredictDb : public MappedFile {
 public:
  explicit PredictDb(const path& file_path)
      : MappedFile(file_path),
        key_trie_(new Darts::DoubleArray),
        value_trie_(new StringTable) {}
  bool Load();
  bool Save();
  bool Build(const predict::RawData& data);
  predict::Candidates* Lookup(const string& query);
  string GetEntryText(const ::rime::table::Entry& entry);

 private:
  int WriteCandidates(const vector<predict::RawEntry>& candidates,
                      const table::Entry* entry);
  predict::Metadata* metadata_ = nullptr;
  the<Darts::DoubleArray> key_trie_;
  the<StringTable> value_trie_;
  // Parsers and candidate pointers refer only to this immutable, verified
  // snapshot, never a user-writable mmap. Load is idempotent for shared DbPool.
  vector<char> model_image_;
};
}  // namespace rime
#endif  // RIME_PREDICT_DB_H_
