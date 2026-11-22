# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# Boost is provided by the downloaded CMake distribution in the build tree.
set(Boost_FOUND TRUE)
set(Boost_VERSION ${BOOST_VERSION})

# Export the Boost components as the namespaced targets librime expects.
list(TRANSFORM BOOST_INCLUDE_LIBRARIES PREPEND Boost:: OUTPUT_VARIABLE
                                                       Boost_LIBRARIES)

# include directories
file(
  GLOB __boost_installed_libs
  LIST_DIRECTORIES true
  RELATIVE "${CMAKE_BINARY_DIR}"
  "${CMAKE_BINARY_DIR}/boost/libs/*")

foreach(__lib IN LISTS __boost_installed_libs)
  list(APPEND Boost_INCLUDE_DIRS "${CMAKE_SOURCE_DIR}/${__lib}/include")
endforeach()
