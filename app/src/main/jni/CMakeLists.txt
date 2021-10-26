cmake_minimum_required (VERSION 3.10.0)
project(jni)

set(ANDROID_STL c++_shared)
set(CMAKE_CXX_STANDARD 14)

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

include(Iconv)
# workaround for boost install
install(TARGETS iconv EXPORT boost_locale-targets
  ARCHIVE DESTINATION dummy # remove this line after cmake >= 3.18
)

add_subdirectory(boost)

option(BUILD_TESTING "" OFF)
option(EXTERNAL_CAPNP "" ON)
add_subdirectory(capnproto)
include(CapnprotoWorkarounds)

#add_subdirectory(glog)

add_subdirectory(yaml-cpp)

option(LEVELDB_BUILD_TESTS "" OFF)
option(LEVELDB_BUILD_BENCHMARKS "" OFF)
option(LEVELDB_INSTALL "" OFF)
add_subdirectory(leveldb)

add_subdirectory(OpenCC)
include(OpenccWorkarounds)

include(RimePlugins)

option(BUILD_TEST "" OFF)
option(ENABLE_LOGGING "" OFF)
include_directories("librime/thirdparty/include") # for X11/keysym.h
include_directories("capnproto/c++/src")
# workaround for librime/tools/rime_api_console.cc
# TODO: fix it in upstream
string(REPLACE "-Werror=format-security" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
string(REPLACE "-Wformat" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
add_definitions("-Wno-format")
# workaround end
add_subdirectory(librime)

add_subdirectory(librime_jni)
