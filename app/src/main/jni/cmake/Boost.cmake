set(BOOST_VER 1.83.0)

if(NOT EXISTS "${CMAKE_SOURCE_DIR}/boost")
    message(STATUS "Downloading Boost ${BOOST_VER} ......")
    file(
        DOWNLOAD "https://github.com/boostorg/boost/releases/download/boost-${BOOST_VER}/boost-${BOOST_VER}.tar.xz" boost-${BOOST_VER}.tar.xz
        EXPECTED_HASH SHA256=c5a0688e1f0c05f354bbd0b32244d36085d9ffc9f932e8a18983a9908096f614
        SHOW_PROGRESS
    )
    file(ARCHIVE_EXTRACT INPUT boost-${BOOST_VER}.tar.xz
        DESTINATION ${CMAKE_SOURCE_DIR}
    )
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
  optional
)

add_subdirectory(boost EXCLUDE_FROM_ALL)
