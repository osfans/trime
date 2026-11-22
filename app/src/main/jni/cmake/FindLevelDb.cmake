# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# leveldb is added as a subdirectory of the top-level build.
set(LevelDb_FOUND TRUE)
set(LevelDb_LIBRARY leveldb)
set(LevelDb_INCLUDE_PATH "${CMAKE_SOURCE_DIR}/librime/deps/leveldb/include")
