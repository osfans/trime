// SPDX-License-Identifier: GPL-3.0-or-later
// Exercise the real OpenCC Darts API; synthetic native-endian legacy fixtures
// follow the pinned upstream DartsDictTest, not a second lookup implementation.
/*
 * Legacy64 fixture construction is adapted from OpenCC DartsDictTest.cpp.
 * Copyright 2015-2026 Carbo Kuo and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iterator>
#include <limits>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "DartsDict.hpp"
#include "Lexicon.hpp"
#include "TextDict.hpp"

namespace {
using Bytes = std::vector<char>;
namespace fs = std::filesystem;
std::size_t checks = 0;
std::size_t valid_readers = 0;
std::size_t rejected_readers = 0;

void Check(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
  ++checks;
}

template <typename T>
T Read(const Bytes& bytes, std::size_t offset) {
  Check(offset <= bytes.size() && sizeof(T) <= bytes.size() - offset,
        "fixture integer is out of bounds");
  T value;
  std::memcpy(&value, bytes.data() + offset, sizeof(value));
  return value;
}

template <typename T>
void Set(Bytes& bytes, std::size_t offset, T value) {
  Check(offset <= bytes.size() && sizeof(T) <= bytes.size() - offset,
        "fixture mutation is out of bounds");
  std::memcpy(bytes.data() + offset, &value, sizeof(value));
}

void Copy(const Bytes& source, std::size_t& offset, Bytes& target,
          std::size_t length) {
  Check(offset <= source.size() && length <= source.size() - offset,
        "fixture byte span is out of bounds");
  const char* first = source.data() + offset;
  target.insert(target.end(), first, first + length);
  offset += length;
}

template <typename T>
void Append(Bytes& bytes, T value) {
  const char* first = reinterpret_cast<const char*>(&value);
  bytes.insert(bytes.end(), first, first + sizeof(value));
}

Bytes Load(const fs::path& path) {
  std::ifstream file(path, std::ios::binary);
  Check(file.good(), "cannot read retained dictionary fixture");
  Bytes result{std::istreambuf_iterator<char>(file), {}};
  Check(!file.bad(), "dictionary fixture read failed");
  return result;
}

void Save(const fs::path& path, const Bytes& bytes) {
  std::ofstream file(path, std::ios::binary | std::ios::trunc);
  file.write(bytes.data(), static_cast<std::streamsize>(bytes.size()));
  file.close();
  Check(!file.fail(), "dictionary fixture write failed");
}

Bytes Legacy64(const Bytes& fixed) {
  Bytes legacy;
  std::size_t offset = 0;
  Copy(fixed, offset, legacy, 12);  // OPENCCDARTS1 magic stays byte-identical.
  const auto darts_bytes = Read<std::uint32_t>(fixed, offset);
  offset += sizeof(std::uint32_t);
  Check(darts_bytes % sizeof(std::uint32_t) == 0, "fixed array is misaligned");
  Append<std::uint64_t>(legacy, static_cast<std::uint64_t>(darts_bytes) * 2);
  for (std::size_t i = 0; i < darts_bytes / sizeof(std::uint32_t); ++i) {
    Append<std::uint64_t>(legacy, Read<std::uint32_t>(fixed, offset));
    offset += sizeof(std::uint32_t);
  }
  const auto field = [&]() {
    const auto value = Read<std::uint32_t>(fixed, offset);
    offset += sizeof(std::uint32_t);
    Append<std::uint64_t>(legacy, value);
    return value;
  };
  const auto items = field();
  const auto key_bytes = field();
  Copy(fixed, offset, legacy, key_bytes);
  const auto value_bytes = field();
  Copy(fixed, offset, legacy, value_bytes);
  for (std::uint32_t i = 0; i < items; ++i) {
    const auto values = field();
    field();  // key offset
    for (std::uint32_t j = 0; j < values; ++j) field();
  }
  Check(offset == fixed.size(), "legacy conversion left unparsed source bytes");
  return legacy;
}

void Verify(const opencc::DartsDictPtr& dict) {
  for (std::size_t length = 1; length <= 65; ++length) {
    const std::string key(length, 'a');
    const auto result = dict->Match(key.data(), key.size());
    Check(!result.IsNull() && result.Get()->Key() == key &&
              result.Get()->GetDefault() == "v" + std::to_string(length),
          "exact match changed");
  }
  const std::string query = std::string(65, 'a') + "!";
  const auto prefix = dict->MatchPrefix(query.data(), query.size());
  Check(!prefix.IsNull() && prefix.Get()->Key() == std::string(65, 'a') &&
            prefix.Get()->GetDefault() == "v65",
        "longest prefix beyond 64 matches changed");
  const auto fast = dict->MatchPrefixValue(query.data(), query.size());
  Check(fast.matched && fast.keyLength == 65 && fast.value == "v65",
        "public prefix view changed");
  const std::string han = "漢語";
  const auto chinese = dict->Match(han.data(), han.size());
  Check(!chinese.IsNull() && chinese.Get()->Values() ==
            std::vector<std::string>({"汉语", "中文"}),
        "multi-value UTF-8 entry changed");
  Check(dict->Match("missing", 7).IsNull(), "missing key unexpectedly matched");
  Check(dict->KeyMaxLength() == 65, "maximum key length changed");
}

void Accept(const fs::path& directory, const std::string& name,
            const Bytes& bytes, bool fixed) {
  const auto path = directory / (name + ".ocd");
  Save(path, bytes);
  auto from_file = opencc::SerializableDict::NewFromFile<opencc::DartsDict>(path.string());
  auto from_buffer = opencc::DartsDict::NewFromBuffer(bytes.data(), bytes.size());
  Verify(from_file);
  Verify(from_buffer);
  valid_readers += 2;
  if (fixed) {
    const auto roundtrip = directory / "fixed32-roundtrip.ocd";
    from_buffer->opencc::SerializableDict::SerializeToFile(roundtrip.string());
    Check(Load(roundtrip) == bytes, "fixed32 wire bytes changed on round trip");
  } else {
    bool rejected = false;
    try {
      from_buffer->opencc::SerializableDict::SerializeToFile(
          (directory / "legacy64-serialize-rejected.ocd").string());
    } catch (const opencc::InvalidFormat& error) {
      rejected = std::string(error.what()).find("Cannot serialize a legacy 64-bit") != std::string::npos;
    }
    Check(rejected, "legacy64 serialization no longer rejects safely");
  }
}

void Reject(const fs::path& directory, const std::string& name, const Bytes& bytes,
            const char* expected_message, const char* expected_file_message = nullptr) {
  const auto path = directory / (name + ".ocd");
  Save(path, bytes);  // Keep each actual rejected input as evidence.
  for (bool file_reader : {false, true}) {
    bool rejected = false;
    try {
      if (file_reader) {
        (void)opencc::SerializableDict::NewFromFile<opencc::DartsDict>(path.string());
      } else {
        (void)opencc::DartsDict::NewFromBuffer(bytes.data(), bytes.size());
      }
    } catch (const opencc::InvalidFormat& error) {
      const char* expected = file_reader && expected_file_message != nullptr
                                 ? expected_file_message : expected_message;
      Check(std::string(error.what()).find(expected) != std::string::npos,
            "malformed fixture reached a different rejection path");
      rejected = true;
    }
    Check(rejected, "malformed dictionary was not rejected as InvalidFormat");
    ++rejected_readers;
  }
}

void Malformed(const fs::path& directory, const Bytes& source, bool wide) {
  const std::string name = wide ? "legacy64-" : "fixed32-";
  const std::size_t array_start = wide ? 20 : 16;
  const std::size_t width = wide ? 8 : 4;
  const auto array_bytes = wide ? Read<std::uint64_t>(source, 12)
                                : Read<std::uint32_t>(source, 12);
  const auto mutate_field = [&](Bytes& bytes, std::size_t offset, std::uint64_t value) {
    if (wide) Set<std::uint64_t>(bytes, offset, value);
    else Set<std::uint32_t>(bytes, offset, static_cast<std::uint32_t>(value));
  };
  auto bytes = source;
  mutate_field(bytes, 12, array_bytes + source.size());
  Reject(directory, name + "oversized", bytes, "dartsSize exceeds file size");
  bytes = source;
  mutate_field(bytes, 12, array_bytes - 1);
  Reject(directory, name + "misaligned", bytes, wide ? "unit alignment" : "darts alignment");
  bytes = source;
  // Keep the fixed32 probe nonzero so this reaches fixed32 validation rather
  // than accidentally selecting the legacy64 reader (as a zero root would).
  mutate_field(bytes, array_start, wide ? 0 : 0x100);
  Reject(directory, name + "invalid-root", bytes,
         wide ? "Invalid legacy OCD dictionary darts data" : "Invalid OpenCC dictionary darts data");
  bytes = source;
  bool leaf = false;
  for (std::size_t offset = array_start; offset < array_start + array_bytes; offset += width) {
    const auto unit = wide ? Read<std::uint64_t>(source, offset)
                           : Read<std::uint32_t>(source, offset);
    if ((unit & ((1U << 31) | 0xffU)) > 0xffU) {
      mutate_field(bytes, offset, std::numeric_limits<std::uint32_t>::max());
      leaf = true;
      break;
    }
  }
  Check(leaf, "fixture has no leaf value to corrupt");
  Reject(directory, name + "invalid-value", bytes,
         wide ? "Invalid legacy OCD dictionary darts data" : "Invalid OpenCC dictionary darts data");
  bytes = source;
  bytes.resize(array_start + static_cast<std::size_t>(array_bytes) - 1);
  Reject(directory, name + "truncated-array", bytes, "dartsSize exceeds file size",
         wide ? "legacy OCD dictionary size mismatch" : "OpenCC dictionary size of darts mismatch");
  bytes = source;
  bytes.pop_back();
  Reject(directory, name + "truncated-binary", bytes, "binary dictionary (truncated)");
}
}  // namespace

int main(int argc, char** argv) {
  try {
    Check(argc == 2, "expected a fresh fixture directory");
    const fs::path directory(argv[1]);
    Check(fs::create_directory(directory), "fixture directory already exists");
    auto lexicon = std::make_shared<opencc::Lexicon>();
    for (std::size_t length = 1; length <= 65; ++length) {
      lexicon->Add(opencc::DictEntryFactory::New(std::string(length, 'a'),
                                                "v" + std::to_string(length)));
    }
    lexicon->Add(opencc::DictEntryFactory::New("漢語", std::vector<std::string>{"汉语", "中文"}));
    lexicon->Sort();
    opencc::TextDict text(lexicon);
    auto built = opencc::DartsDict::NewFromDict(text);
    Verify(built);
    const auto path = directory / "built-fixed32.ocd";
    built->opencc::SerializableDict::SerializeToFile(path.string());
    const auto fixed = Load(path);
    Check(fixed.size() > 20 && std::string(fixed.data(), 12) == "OPENCCDARTS1",
          "wire magic changed");
    Check(Read<std::uint32_t>(fixed, 12) % 4 == 0 &&
              Read<std::uint32_t>(fixed, 16) != 0,
          "not the actual fixed32 wire format");
    const auto legacy = Legacy64(fixed);
    Accept(directory, "fixed32", fixed, true);
    Accept(directory, "legacy64", legacy, false);
    Malformed(directory, fixed, false);
    Malformed(directory, legacy, true);
    Check(valid_readers == 4 && rejected_readers == 24, "reader coverage changed");
    std::cout << "{\"passed\":true,\"real_library\":true,\"valid_readers\":"
              << valid_readers << ",\"rejected_readers\":" << rejected_readers
              << ",\"deep_prefix_keys\":65,\"checks\":" << checks << "}\n";
    return 0;
  } catch (const opencc::Exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  }
}
