# SPDX-FileCopyrightText: 2026 Rime community
# SPDX-License-Identifier: GPL-3.0-or-later

cmake_minimum_required(VERSION 3.18.0)
project(native_plugin_test LANGUAGES CXX)

get_filename_component(TRIME_SOURCE_DIR "../../main/jni" ABSOLUTE)
set(RIME_SOURCE_DIR "${TRIME_SOURCE_DIR}/librime")

add_library(rime_jni SHARED IMPORTED)
set_target_properties(rime_jni PROPERTIES IMPORTED_LOCATION "${TRIME_LIBRARY}")

add_library(rime-sample SHARED
  "${RIME_SOURCE_DIR}/sample/src/sample_module.cc"
  "${RIME_SOURCE_DIR}/sample/src/trivial_translator.cc")
target_compile_features(rime-sample PRIVATE cxx_std_17)
target_compile_definitions(rime-sample PRIVATE GLOG_USE_GLOG_EXPORT)
file(GLOB boost_includes "${TRIME_SOURCE_DIR}/boost/libs/*/include")
target_include_directories(rime-sample PRIVATE
  "${RIME_SOURCE_DIR}/src"
  "${RIME_SOURCE_DIR}/deps/glog/src"
  "${TRIME_BUILD_DIR}/librime/src"
  "${TRIME_BUILD_DIR}/librime/deps/glog"
  ${boost_includes})
target_link_libraries(rime-sample PRIVATE rime_jni)
