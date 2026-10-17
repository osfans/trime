# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# Install headers from the same patched build-tree copy used by libopencc, so
# JNI and Rime consumers use the resource-safe inline file wrappers too.
get_target_property(TRIME_OPENCC_SOURCE_DIR libopencc SOURCE_DIR)
file(GLOB LIBOPENCC_HEADERS "${TRIME_OPENCC_SOURCE_DIR}/*.hpp"
     "${CMAKE_BINARY_DIR}/OpenCC/src/opencc_config.h")
file(COPY ${LIBOPENCC_HEADERS} DESTINATION "${CMAKE_BINARY_DIR}/include/opencc")

# RapidJSON 1.1's supported pointer iterators avoid its deprecated std::iterator
# base. JSON types are private to OpenCC's configuration implementation.
target_compile_definitions(libopencc PRIVATE RAPIDJSON_NOMEMBERITERATORCLASS)
