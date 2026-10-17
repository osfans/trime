# SPDX-License-Identifier: GPL-3.0-or-later

# Adapt the owned OpenCC build-tree copy only. Keep malformed input diagnostics
# and public interfaces unchanged while releasing resources on parser failures.
function(trime_adapt_opencc_resources source)
  trime_replace_dependency_cmake("${source}/MarisaDict.cpp"
    [=[#include <unordered_map>]=]
    [=[#include <unordered_map>
#include <vector>]=])
  trime_replace_dependency_cmake("${source}/MarisaDict.cpp"
    [=[  void* buffer = malloc(sizeof(char) * headerLen);
  size_t bytesRead = fread(buffer, sizeof(char), headerLen, fp);
  if (bytesRead != headerLen || memcmp(buffer, OCD2_HEADER, headerLen) != 0) {
    throw InvalidFormat("Invalid OpenCC dictionary header");
  }
  free(buffer);]=]
    [=[  std::vector<char> buffer(headerLen);
  size_t bytesRead = fread(buffer.data(), sizeof(char), headerLen, fp);
  if (bytesRead != headerLen || memcmp(buffer.data(), OCD2_HEADER, headerLen) != 0) {
    throw InvalidFormat("Invalid OpenCC dictionary header");
  }]=])

  trime_replace_dependency_cmake("${source}/SerializableDict.hpp"
    [=[#include "Dict.hpp"]=]
    [=[#include <memory>

#include "Dict.hpp"]=])
  trime_replace_dependency_cmake("${source}/SerializableDict.hpp"
    [=[    FILE* fp = OpenSerializableFileUtf8(fileName, "wb");
    if (fp == NULL) {
      throw FileNotWritable(fileName);
    }
    SerializeToFile(fp);
    fclose(fp);]=]
    [=[    std::unique_ptr<FILE, decltype(&fclose)> output(
        OpenSerializableFileUtf8(fileName, "wb"), fclose);
    if (!output) {
      throw FileNotWritable(fileName);
    }
    SerializeToFile(output.get());]=])
  trime_replace_dependency_cmake("${source}/SerializableDict.hpp"
    [=[    if (fp == NULL) {
      return false;
    }
    std::shared_ptr<DICT> loadedDict = DICT::NewFromFile(fp);
    fclose(fp);
    *dict = loadedDict;
    return true;]=]
    [=[    std::unique_ptr<FILE, decltype(&fclose)> input(fp, fclose);
    if (!input) {
      return false;
    }
    std::shared_ptr<DICT> loadedDict = DICT::NewFromFile(input.get());
    *dict = loadedDict;
    return true;]=])
endfunction()
