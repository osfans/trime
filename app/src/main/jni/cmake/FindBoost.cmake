# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

set(Boost_FOUND TRUE)

list(TRANSFORM BOOST_INCLUDE_LIBRARIES PREPEND Boost:: OUTPUT_VARIABLE
                                                       Boost_LIBRARIES)

file(
  GLOB __boost_installed_libs
  LIST_DIRECTORIES true
  RELATIVE "${CMAKE_BINARY_DIR}"
  "${CMAKE_BINARY_DIR}/boost/libs/*")

foreach(__lib ${__boost_installed_libs})
  set(__full_dir "${CMAKE_SOURCE_DIR}/${__lib}/include")
  list(APPEND Boost_INCLUDE_DIRS "${__full_dir}")
endforeach()

# for librime-charcode
set(Boost_LOCALE_LIBRARIES Boost::locale)
