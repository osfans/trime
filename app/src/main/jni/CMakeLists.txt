cmake_minimum_required (VERSION 3.10.0)

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
install(TARGETS iconv EXPORT boost_locale-targets
  ARCHIVE DESTINATION dummy # remove this line after cmake >= 3.18
)

option(BOOST_FILESYSTEM_DISABLE_STATX "" ON) # remove this option after targetSdkVersion >= 30
add_subdirectory(boost)

option(WITH_GFLAGS "Use gflags" OFF)
option(WITH_GTEST "Use googletest" OFF)
option(WITH_UNWIND "Enable libunwind support" OFF)
add_subdirectory(librime/deps/glog)

option(YAML_CPP_BUILD_TOOLS "" OFF)
# TODO: remove below options after librime > 1.7.3
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

# TODO: use librime/thirdparty/src/opencc instead after librime > 1.7.3
add_subdirectory(OpenCC)
include(OpenccWorkarounds)

include(RimePlugins)

option(BUILD_TEST "" OFF)
include_directories(
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/librime/include"
    PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/librime/deps/glog"
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/librime/deps/glog/src"
)
# workaround for librime/tools/rime_api_console.cc
# TODO: fix it in upstream
string(REPLACE "-Werror=format-security" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
string(REPLACE "-Wformat" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
add_definitions("-Wno-format")
# workaround end
add_subdirectory(librime)

add_subdirectory(librime_jni)
