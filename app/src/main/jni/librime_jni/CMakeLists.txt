# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

find_package(Opencc REQUIRED)

aux_source_directory(. RIME_JNI_SOURCES)
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})
target_link_libraries(rime_jni rime-static ${Opencc_LIBRARY})
target_include_directories(rime_jni PRIVATE
  "${CMAKE_BINARY_DIR}/librime/src"
  "${CMAKE_SOURCE_DIR}/librime/src"
  "${Opencc_INCLUDE_PATH}"
)


execute_process(
  COMMAND git --git-dir ${CMAKE_SOURCE_DIR}/../../../../.git describe --tags
  OUTPUT_VARIABLE TRIME_VERSION
)
string(STRIP ${TRIME_VERSION} TRIME_VERSION)

target_compile_definitions(rime_jni PRIVATE
  TRIME_VERSION="${TRIME_VERSION}"
)
