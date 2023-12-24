set(BOOST_VER 1.84.0)

if(NOT EXISTS "boost-${BOOST_VER}.tar.xz")
  message(STATUS "Downloading Boost ${BOOST_VER} ......")
  file(
    DOWNLOAD
    "https://github.com/boostorg/boost/releases/download/boost-${BOOST_VER}/boost-${BOOST_VER}.tar.xz"
    boost-${BOOST_VER}.tar.xz
    EXPECTED_HASH
      SHA256=2e64e5d79a738d0fa6fb546c6e5c2bd28f88d268a2a080546f74e5ff98f29d0e
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
    date_time
    dll
    interprocess
    range
    regex
    scope_exit
    signals2
    utility
    uuid
    # librime-charcode
    locale
    asio
    # librime-lua
    optional)

add_subdirectory(boost EXCLUDE_FROM_ALL)
