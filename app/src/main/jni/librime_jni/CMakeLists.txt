# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# opencc.cc converts user text dictionaries, which needs the headers and the
# library of the bundled OpenCC
find_package(Opencc REQUIRED)

file(GLOB RIME_JNI_SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/*.cc")
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})

# for reproducible build; GNU hash is enough above API 22
target_link_libraries(rime_jni PRIVATE rime-static ${Opencc_LIBRARY})
target_link_options(rime_jni PRIVATE "LINKER:--build-id=none")

target_include_directories(rime_jni PRIVATE
  "${CMAKE_BINARY_DIR}/librime/src"
  "${CMAKE_SOURCE_DIR}/librime/src"
  "${X11Keysym}"
  "${Opencc_INCLUDE_PATH}"
)
