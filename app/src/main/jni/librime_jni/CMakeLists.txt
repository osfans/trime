# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

find_package(Opencc REQUIRED)

aux_source_directory(. RIME_JNI_SOURCES)
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})
target_link_libraries(rime_jni -Wl,--whole-archive rime-static
  -Wl,--no-whole-archive ${Opencc_LIBRARY} dl)
# Share C++ RTTI identities between the host and plugins.
target_link_options(rime_jni PRIVATE "LINKER:-z,global")
target_include_directories(rime_jni PRIVATE
  "${CMAKE_BINARY_DIR}/librime/src"
  "${CMAKE_SOURCE_DIR}/librime/src"
  "${Opencc_INCLUDE_PATH}"
)
