# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

set(BOOST_VER 1.85.0)

if(NOT EXISTS "boost-${BOOST_VER}-cmake.tar.xz")
  message(STATUS "Downloading Boost ${BOOST_VER} ......")
  file(
    DOWNLOAD
    "https://github.com/boostorg/boost/releases/download/boost-${BOOST_VER}/boost-${BOOST_VER}-cmake.tar.xz"
    boost-${BOOST_VER}.tar.xz
    EXPECTED_HASH
      SHA256=0a9cc56ceae46986f5f4d43fe0311d90cf6d2fa9028258a95cab49ffdacf92ad
    SHOW_PROGRESS)

  message(STATUS "Remove older version Boost")
  file(REMOVE_RECURSE "${CMAKE_SOURCE_DIR}/boost")
endif()

if(NOT EXISTS "${CMAKE_SOURCE_DIR}/boost")
  message(STATUS "Extracting Boost ${BOOST_VER} ......")
  file(ARCHIVE_EXTRACT INPUT boost-${BOOST_VER}.tar.xz DESTINATION
       ${CMAKE_SOURCE_DIR})
  file(RENAME "boost-${BOOST_VER}" boost)
endif()

set(BOOST_INCLUDE_LIBRARIES
    algorithm
    crc
    dll
    interprocess
    range
    regex
    scope_exit
    signals2
    utility
    uuid
    # librime-charcode
    locale)

add_subdirectory(boost EXCLUDE_FROM_ALL)
