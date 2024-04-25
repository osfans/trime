# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# Since OpenCC doesn't include its headers in the binary dir, we need to install
# them manually.
file(GLOB LIBOPENCC_HEADERS OpenCC/src/*.hpp
     "${CMAKE_BINARY_DIR}/OpenCC/src/opencc_config.h")
file(COPY ${LIBOPENCC_HEADERS} DESTINATION "${CMAKE_BINARY_DIR}/include/opencc")
