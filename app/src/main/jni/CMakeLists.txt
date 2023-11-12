cmake_minimum_required (VERSION 3.18.0)

project(trime-lib VERSION 3.0.0)

set(ANDROID_STL c++_shared)
set(CMAKE_CXX_STANDARD 14)

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

# general options
option(BUILD_SHARED_LIBS "" OFF)
option(BUILD_TESTING "" OFF)

# iconv for Boost::locale
include(Iconv)
# workaround for boost install
install(TARGETS iconv EXPORT boost_locale-targets)

set(BOOST_VER 1.83.0)
if(NOT EXISTS ${CMAKE_SOURCE_DIR}/boost)
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
add_subdirectory(boost)

option(WITH_GFLAGS "Use gflags" OFF)
option(WITH_GTEST "Use googletest" OFF)
option(WITH_UNWIND "Enable libunwind support" OFF)
add_subdirectory(librime/deps/glog)

option(YAML_CPP_BUILD_TOOLS "" OFF)
option(YAML_CPP_BUILD_TESTS "" OFF)
option(YAML_CPP_INSTALL "" OFF)
add_subdirectory(librime/deps/yaml-cpp)

# enable compress for leveldb
option(SNAPPY_BUILD_TESTS "" OFF)
option(SNAPPY_BUILD_BENCHMARKS "" OFF)
option(SNAPPY_INSTALL "" OFF)
add_subdirectory(snappy)

set(HAVE_SNAPPY TRUE)
option(LEVELDB_BUILD_TESTS "" OFF)
option(LEVELDB_BUILD_BENCHMARKS "" OFF)
option(LEVELDB_INSTALL "" OFF)
add_subdirectory(librime/deps/leveldb)

add_subdirectory(OpenCC)
include(OpenccWorkarounds)

include(RimePlugins)

option(BUILD_TEST "" OFF)
include_directories(
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/librime/include"
    PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/librime/deps/glog"
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/librime/deps/glog/src"
)

add_subdirectory(librime)

add_subdirectory(librime_jni)
