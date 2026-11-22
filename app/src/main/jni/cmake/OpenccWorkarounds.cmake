# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# OpenCC doesn't put its headers in the binary dir, so copy them manually.
file(
  GLOB LIBOPENCC_HEADERS CONFIGURE_DEPENDS
  "${CMAKE_SOURCE_DIR}/librime/deps/opencc/src/*.hpp")
file(COPY ${LIBOPENCC_HEADERS} DESTINATION "${CMAKE_BINARY_DIR}/include/opencc")
