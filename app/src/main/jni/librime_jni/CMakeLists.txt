# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

find_package(Opencc REQUIRED)

set(RIME_JNI_SOURCES config.cc frontend.cc key.cc levers.cc opencc.cc rime_jni.cc t9.cc t9_assist.cc)
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})
target_link_libraries(rime_jni rime-static ${Opencc_LIBRARY})
target_include_directories(rime_jni BEFORE PRIVATE
  "${CMAKE_BINARY_DIR}/trime-rime-headers"
  "${CMAKE_BINARY_DIR}/trime-rime-privacy"
  "${CMAKE_BINARY_DIR}/librime/src"
  "${CMAKE_SOURCE_DIR}/librime/src"
  "${Opencc_INCLUDE_PATH}"
)
