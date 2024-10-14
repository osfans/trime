# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

find_package(Opencc REQUIRED)

set(RIME_JNI_SOURCES
  key_table.cc
  levers.cc
  opencc.cc
  rime_jni.cc
)
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})
target_link_libraries(rime_jni rime-static ${Opencc_LIBRARY})
target_include_directories(rime_jni PRIVATE
  "${CMAKE_SOURCE_DIR}/librime/src"
  "${Opencc_INCLUDE_PATH}"
)


execute_process(
  COMMAND git --git-dir ${CMAKE_SOURCE_DIR}/../../../../.git describe --tags
  OUTPUT_VARIABLE TRIME_VERSION
)
string(STRIP ${TRIME_VERSION} TRIME_VERSION)

execute_process(
  COMMAND git --git-dir ${CMAKE_SOURCE_DIR}/OpenCC/.git describe --tags
  OUTPUT_VARIABLE OPENCC_VERSION
)
string(STRIP ${OPENCC_VERSION} OPENCC_VERSION)

execute_process(
  COMMAND git --git-dir ${CMAKE_SOURCE_DIR}/librime/.git describe --tags --exclude "latest"
  OUTPUT_VARIABLE LIBRIME_VERSION
)
string(STRIP ${LIBRIME_VERSION} LIBRIME_VERSION)

target_compile_definitions(rime_jni PRIVATE
  TRIME_VERSION="${TRIME_VERSION}"
  OPENCC_VERSION="${OPENCC_VERSION}"
  LIBRIME_VERSION="${LIBRIME_VERSION}"
)
