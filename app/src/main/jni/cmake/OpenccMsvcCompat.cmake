# SPDX-License-Identifier: GPL-3.0-or-later

# Keep JSON byte lengths checked before narrowing to RapidJSON SizeType.
# Reject source drift and only adapt the owned OpenCC build-tree copy.
function(trime_adapt_opencc_msvc source)
  file(SHA256 "${source}/tools/CommandLineMain.cpp" actual)
  if(NOT actual STREQUAL "503d497613f559951489d809cf9cf7573c4c4c8809563defdb9af752adaa8cdb")
    message(FATAL_ERROR "Re-audit the OpenCC MSVC source SHA: ${source}/tools/CommandLineMain.cpp")
  endif()
  file(SHA256 "${source}/PrefixMatch.cpp" actual)
  if(NOT actual STREQUAL "1805525a77e0648c4a2bdaecf063e06b02e358fcfdd49cbdacce8db014bbce74")
    message(FATAL_ERROR "Re-audit the OpenCC MSVC source SHA: ${source}/PrefixMatch.cpp")
  endif()
  trime_replace_dependency_cmake("${source}/tools/CommandLineMain.cpp"
    [=[#include <iostream>]=]
    [=[#include <iostream>
#include <limits>]=])
  trime_replace_dependency_cmake("${source}/tools/CommandLineMain.cpp"
    [=[// Serializes the segmentation-only view]=]
    [=[rapidjson::SizeType CheckedJsonStringLength(size_t length) {
  if (length > (std::numeric_limits<rapidjson::SizeType>::max)()) {
    throw Exception("JSON string exceeds the supported size");
  }
  return static_cast<rapidjson::SizeType>(length);
}

// Serializes the segmentation-only view]=])
  file(READ "${source}/tools/CommandLineMain.cpp" content)
  string(REPLACE [=[writer.String(result.input.c_str(), result.input.size());]=] "" stripped "${content}")
  string(LENGTH "${content}" original_length)
  string(LENGTH "${stripped}" stripped_length)
  string(LENGTH [=[writer.String(result.input.c_str(), result.input.size());]=] token_length)
  math(EXPR count "(${original_length} - ${stripped_length}) / ${token_length}")
  if(NOT count EQUAL 2)
    message(FATAL_ERROR "Re-audit the OpenCC JSON call sites")
  endif()
  string(REPLACE [=[writer.String(result.input.c_str(), result.input.size());]=] [=[writer.String(result.input.c_str(), CheckedJsonStringLength(result.input.size()));]=] content "${content}")
  file(WRITE "${source}/tools/CommandLineMain.cpp" "${content}")
  file(READ "${source}/tools/CommandLineMain.cpp" content)
  string(REPLACE [=[writer.String(seg.c_str(), seg.size());]=] "" stripped "${content}")
  string(LENGTH "${content}" original_length)
  string(LENGTH "${stripped}" stripped_length)
  string(LENGTH [=[writer.String(seg.c_str(), seg.size());]=] token_length)
  math(EXPR count "(${original_length} - ${stripped_length}) / ${token_length}")
  if(NOT count EQUAL 3)
    message(FATAL_ERROR "Re-audit the OpenCC JSON call sites")
  endif()
  string(REPLACE [=[writer.String(seg.c_str(), seg.size());]=] [=[writer.String(seg.c_str(), CheckedJsonStringLength(seg.size()));]=] content "${content}")
  file(WRITE "${source}/tools/CommandLineMain.cpp" "${content}")
  trime_replace_dependency_cmake("${source}/tools/CommandLineMain.cpp"
    [=[writer.String(result.output.c_str(), result.output.size());]=]
    [=[writer.String(result.output.c_str(), CheckedJsonStringLength(result.output.size()));]=])
  trime_replace_dependency_cmake("${source}/tools/CommandLineMain.cpp"
    [=[writer.String(data, length)]=]
    [=[writer.String(data, CheckedJsonStringLength(length))]=])
  trime_replace_dependency_cmake("${source}/PrefixMatch.cpp"
    [=[#include "DictGroup.hpp"]=]
    [=[#include "DictGroup.hpp"
#include "Exception.hpp"]=])
  trime_replace_dependency_cmake("${source}/PrefixMatch.cpp"
    [=[void Unreachable() {
#if defined(_MSC_VER)
  __assume(false);
#elif defined(__GNUC__) || defined(__clang__)
  __builtin_unreachable();
#endif
}

]=]
    [=[]=])
  trime_replace_dependency_cmake("${source}/PrefixMatch.cpp"
    [=[    Unreachable();
    return Candidate{};]=]
    [=[    throw ShouldNotBeHere();]=])
endfunction()
