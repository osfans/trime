# SPDX-License-Identifier: GPL-3.0-or-later

cmake_minimum_required(VERSION 3.18)
project(trime_opencc_data LANGUAGES CXX)

if(NOT IS_DIRECTORY "${TRIME_OPENCC_SOURCE_DIR}/src")
  message(FATAL_ERROR "TRIME_OPENCC_SOURCE_DIR must name the pinned OpenCC source")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ENABLE_GTEST OFF CACHE BOOL "" FORCE)
set(ENABLE_BENCHMARK OFF CACHE BOOL "" FORCE)
set(BUILD_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)
set(BUILD_OPENCC_JIEBA_PLUGIN OFF CACHE BOOL "" FORCE)
set(OPENCC_ENABLE_INSTALL OFF CACHE BOOL "" FORCE)
set(OPENCC_DICT_FORMAT text CACHE STRING "" FORCE)
set(USE_SYSTEM_MARISA OFF CACHE BOOL "" FORCE)
# The owned copy has no .git. Use OpenCC's supported archive-version fallback,
# while Gradle separately tracks the exact pinned source contents.
set(CMAKE_DISABLE_FIND_PACKAGE_Git TRUE)

include("${CMAKE_CURRENT_LIST_DIR}/../ThirdPartyCmake.cmake")
trime_add_dependency("${TRIME_OPENCC_SOURCE_DIR}" OpenCC 3.12)
include("${CMAKE_CURRENT_LIST_DIR}/../OpenccWorkarounds.cmake")
