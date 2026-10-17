// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2023, RIME Developers
// Derived from librime-predict; Trime modifications retain the BSD license.
// See LICENSE in this directory for the conditions and disclaimer.
// Trime overlay for the pinned librime-predict database.
#include "predict_db.h"
#include "model_integrity.h"
#include "prediction_policy.h"
#include "trime_predict_model_hash.h"
#include <cerrno>
#include <cmath>
#include <algorithm>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <boost/algorithm/string.hpp>
#include <darts.h>
#include <rime/resource.h>
#include <rime/dict/mapped_file.h>
#include <rime/dict/string_table.h>

namespace rime {

const string kPredictFormat = "Rime::Predict/1.0";
const string kPredictFormatPrefix = "Rime::Predict/";

bool PredictDb::Load() {
  // DbPool may share this object across schemas. Its verified image and every
  // pointer into it remain stable for the entire PredictDb lifetime.
  if (!model_image_.empty()) return true;
  // A user path can be a FIFO or device. Never let opening it block the Rime
  // input queue, and classify/read the very same opened file descriptor.
  const int fd = ::open(file_path().c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
  if (fd < 0) return false;
  struct OpenedModel {
    int descriptor;
    ~OpenedModel() { ::close(descriptor); }
  } opened{fd};
  struct stat info {};
  if (::fstat(fd, &info) != 0 || !S_ISREG(info.st_mode)) return false;
  const auto length = info.st_size;
  if (length <= 0 || static_cast<std::uint64_t>(length) !=
                         trime::prediction::kBundledModelBytes ||
      static_cast<std::uint64_t>(length) >
                         trime::prediction::kMaximumModelBytes)
    return false;
  vector<char> image(static_cast<std::size_t>(length));
  std::size_t copied = 0;
  while (copied < image.size()) {
    const auto count = ::read(fd, image.data() + copied, image.size() - copied);
    if (count < 0 && errno == EINTR) continue;
    if (count <= 0) return false;
    copied += static_cast<std::size_t>(count);
  }
  if (trime::prediction::ModelSha256(
          reinterpret_cast<const unsigned char*>(image.data()), image.size()) !=
      trime::prediction::kBundledModelSha256)
    return false;
  if (image.size() < sizeof(predict::Metadata)) return false;
  const auto* metadata = reinterpret_cast<const predict::Metadata*>(image.data());
  if (!std::memchr(metadata->format, 0, predict::Metadata::kFormatMaxLength) ||
      string(metadata->format) != kPredictFormat)
    return false;
  std::int32_t key_relative = 0;
  std::int32_t value_relative = 0;
  const auto key_field = offsetof(predict::Metadata, key_trie);
  const auto value_field = offsetof(predict::Metadata, value_trie);
  std::memcpy(&key_relative, image.data() + key_field, sizeof(key_relative));
  std::memcpy(&value_relative, image.data() + value_field, sizeof(value_relative));
  std::size_t key_offset = 0;
  std::size_t value_offset = 0;
  if (!metadata->key_trie_size || !metadata->value_trie_size ||
      metadata->key_trie_size > image.size() / sizeof(std::uint32_t) ||
      !trime::prediction::ContainsRelativeRange(image.size(), key_field,
          key_relative, metadata->key_trie_size * sizeof(std::uint32_t), &key_offset) ||
      !trime::prediction::ContainsRelativeRange(image.size(), value_field,
          value_relative, metadata->value_trie_size, &value_offset) ||
      key_offset % alignof(std::uint32_t))
    return false;
  try {
    // Parse the same private snapshot that was hashed. Replacing or editing
    // the user path cannot alter these bytes after validation.
    auto value_trie = make_unique<StringTable>(image.data() + value_offset,
                                               metadata->value_trie_size);
    key_trie_->set_array(image.data() + key_offset, metadata->key_trie_size);
    value_trie_ = std::move(value_trie);
    model_image_ = std::move(image);
    metadata_ = reinterpret_cast<predict::Metadata*>(model_image_.data());
    return true;
  } catch (const std::exception&) {
    key_trie_->clear();
    return false;
  }
}

bool PredictDb::Save() {
  LOG(INFO) << "saving predict db: " << file_path();
  if (!key_trie_->total_size()) {
    LOG(ERROR) << "the trie has not been constructed!";
    return false;
  }
  return ShrinkToFit();
}

int PredictDb::WriteCandidates(const vector<predict::RawEntry>& candidates,
                               const table::Entry* entry) {
  auto* array = CreateArray<table::Entry>(candidates.size());
  auto* next = array->begin();
  for (size_t i = 0; i < candidates.size(); ++i) {
    *next++ = *entry++;
  }
  auto offset = reinterpret_cast<char*>(array) - address();
  return int(offset);
}

bool PredictDb::Build(const predict::RawData& data) {
  // create predict db
  int data_size = data.size();

  size_t entry_count = 0;
  for (const auto& kv : data) {
    entry_count += kv.second.size();
  }
  StringTableBuilder string_table;
  vector<table::Entry> entries(entry_count);
  vector<const char*> keys;
  keys.reserve(data_size);
  int i = 0;
  for (const auto& kv : data) {
    if (kv.second.empty())
      continue;
    for (const auto& candidate : kv.second) {
      string_table.Add(candidate.text, candidate.weight,
                       &entries[i].text.str_id());
      entries[i].weight = float(candidate.weight);
      ++i;
    }
    keys.push_back(kv.first.c_str());
  }
  // this writes to entry vector, which should be copied to entry array later
  string_table.Build();
  size_t value_trie_image_size = string_table.BinarySize();
  if (!Create(value_trie_image_size)) {
    LOG(ERROR) << "Error creating predict db file '" << file_path() << "'.";
    return false;
  }
  // create metadata in the beginning of file
  if (!Allocate<predict::Metadata>()) {
    LOG(ERROR) << "Error creating metadata in file '" << file_path() << "'.";
    return false;
  }

  // copy from entry vector to entry array
  const table::Entry* available_entries = &entries[0];
  vector<int> values;
  values.reserve(data_size);
  for (const auto& kv : data) {
    if (kv.second.empty())
      continue;
    values.push_back(WriteCandidates(kv.second, available_entries));
    available_entries += kv.second.size();
  }
  // build real key trie
  if (0 != key_trie_->build(data_size, keys.data(), NULL, values.data())) {
    LOG(ERROR) << "Error building double-array trie.";
    return false;
  }
  // save double-array image
  size_t key_trie_image_size = key_trie_->total_size();
  char* key_trie_image = Allocate<char>(key_trie_image_size);
  if (!key_trie_image) {
    LOG(ERROR) << "Error creating double-array image.";
    return false;
  }
  std::memcpy(key_trie_image, key_trie_->array(), key_trie_image_size);
  metadata_ = reinterpret_cast<predict::Metadata*>(address());
  metadata_->key_trie = key_trie_image;
  // double-array size (number of units)
  metadata_->key_trie_size = key_trie_->size();
  // save string table
  char* value_trie_image = Allocate<char>(value_trie_image_size);
  if (!value_trie_image) {
    LOG(ERROR) << "Error creating value trie image.";
    return false;
  }
  string_table.Dump(value_trie_image, value_trie_image_size);
  metadata_ = reinterpret_cast<predict::Metadata*>(address());
  metadata_->value_trie = value_trie_image;
  metadata_->value_trie_size = value_trie_image_size;
  value_trie_ =
      make_unique<StringTable>(value_trie_image, value_trie_image_size);
  // at last, complete the metadata
  std::strncpy(metadata_->format, kPredictFormat.c_str(),
               kPredictFormat.length());
  return true;
}

predict::Candidates* PredictDb::Lookup(const string& query) {
  if (model_image_.empty() || query.empty() ||
      query.size() > trime::prediction::kMaximumQueryBytes)
    return nullptr;
  const int result = key_trie_->exactMatchSearch<int>(query.c_str());
  if (result < 0 || result % alignof(predict::Candidates) ||
      !trime::prediction::ContainsRange(model_image_.size(), result,
                                       sizeof(std::uint32_t)))
    return nullptr;
  auto* candidates = reinterpret_cast<predict::Candidates*>(model_image_.data() + result);
  if (!candidates->size || candidates->size > trime::prediction::kMaximumModelCandidates ||
      !trime::prediction::ContainsRange(model_image_.size(), result,
          offsetof(predict::Candidates, at) + candidates->size * sizeof(table::Entry)))
    return nullptr;
  for (const auto& entry : *candidates) {
    if (entry.text.str_id() >= value_trie_->NumKeys() || !std::isfinite(entry.weight))
      return nullptr;
  }
  return candidates;
}

string PredictDb::GetEntryText(const ::rime::table::Entry& entry) {
  if (model_image_.empty() || entry.text.str_id() >= value_trie_->NumKeys())
    return string();
  try {
    return value_trie_->GetString(entry.text.str_id());
  } catch (const std::exception&) {
    return string();
  }
}

}  // namespace rime
