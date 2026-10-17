// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

#include <cstdio>
#include <memory>
#include <opencc/Common.hpp>
#include <opencc/Exception.hpp>
#include <opencc/MarisaDict.hpp>
#include <opencc/SimpleConverter.hpp>
#include <opencc/TextDict.hpp>
#include <stdexcept>
#include <string>

#include "jni-utils.h"

namespace {
using FileHandle = std::unique_ptr<FILE, decltype(&fclose)>;

template <typename Dictionary>
auto loadDictionary(const char* path) {
  FileHandle input(fopen(path, "rb"), fclose);
  if (!input) throw opencc::FileNotFound(path);
  auto dictionary = Dictionary::NewFromFile(input.get());
  if (ferror(input.get()))
    throw std::runtime_error(std::string("Cannot read dictionary: ") + path);
  return dictionary;
}

void serializeDictionary(const opencc::SerializableDict& dictionary,
                         const char* path) {
  FileHandle output(fopen(path, "wb"), fclose);
  if (!output) throw opencc::FileNotWritable(path);
  dictionary.SerializeToFile(output.get());
  const bool written = fflush(output.get()) == 0 && ferror(output.get()) == 0;
  const int closed = fclose(output.release());
  if (!written || closed != 0) throw opencc::FileNotWritable(path);
}
}  // namespace

// opencc

extern "C" JNIEXPORT jstring JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_openCCLineConv(
    JNIEnv* env, jclass clazz, jstring input, jstring config_file_name) {
  return jniCall(env, [&]() -> jstring {
    opencc::SimpleConverter converter(CString(env, config_file_name));
    const auto text = static_cast<std::string>(CString(env, input));
    // OpenCC's segmenter stops at NUL even for its std::string overload.
    // Treat NUL as a boundary and preserve the rest of the editor's text.
    if (text.find('\0') == std::string::npos)
      return makeJavaString(env, converter.Convert(text));
    std::string converted;
    size_t start = 0;
    for (;;) {
      const auto end = text.find('\0', start);
      converted += converter.Convert(text.substr(start, end - start));
      if (end == std::string::npos) break;
      converted += '\0';
      start = end + 1;
    }
    return makeJavaString(env, converted);
  });
}

extern "C" JNIEXPORT void JNICALL
Java_com_osfans_trime_data_opencc_OpenCCDictManager_openCCDictConv(
    JNIEnv* env, jclass clazz, jstring src, jstring dest, jboolean mode) {
  jniCall(env, [&] {
    try {
      auto src_file = CString(env, src);
      auto dest_file = CString(env, dest);
      // Formats are explicit: deployment uses .tmp snapshots and destinations.
      if (mode) {
        auto source = loadDictionary<opencc::MarisaDict>(src_file);
        auto converted = opencc::TextDict::NewFromDict(*source);
        serializeDictionary(*converted, dest_file);
      } else {
        auto source = loadDictionary<opencc::TextDict>(src_file);
        auto converted = opencc::MarisaDict::NewFromDict(*source);
        serializeDictionary(*converted, dest_file);
      }
      if (!mode) {
        // Validate the produced dictionary before Kotlin atomically publishes
        // it. This checks readability, not arbitrary malformed-input safety or
        // fsync.
        loadDictionary<opencc::MarisaDict>(dest_file);
      }
    } catch (const opencc::Exception& error) {
      // OpenCC's Exception does not inherit std::exception, unlike Marisa's.
      throw std::runtime_error(error.what());
    }
  });
}
