cmake_minimum_required (VERSION 3.10.0)

project(trime-native-lib VERSION 3.0.0)

set(ANDROID_STL c++_shared)
set(CMAKE_CXX_STANDARD 14)

# For reproducible build
add_link_options("LINKER:--hash-style=gnu,--build-id=none")

set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt")
    message(STATUS "Downloading prebuilt.tar.zst")
    file(DOWNLOAD https://github.com/WhiredPlanck/trime-prebuilt/releases/download/0.4.2/prebuilt.tar.zst
            "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt.tar.zst" SHOW_PROGRESS
         EXPECTED_HASH SHA256=0b9dbf2e18196bdc9b89cd278559cb0db6a3256c90897b7183da4585ac1d6efb)

    message(STATUS "Extracting prebuilt.tar.zst")
    file(ARCHIVE_EXTRACT INPUT "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt.tar.zst"
         DESTINATION "${CMAKE_CURRENT_SOURCE_DIR}" VERBOSE
    )
endif()

set(PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt")

# Ref: https://cmake.org/cmake/help/latest/variable/CMAKE_FIND_ROOT_PATH.html
set(CMAKE_FIND_ROOT_PATH ${CMAKE_FIND_ROOT_PATH}
    "${PREBUILT_DIR}/glog/${ANDROID_ABI}" # for librime to locate glog
    "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}" # for librime to locate yaml-cpp
    "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}" # for librime to locate leveldb
    "${PREBUILT_DIR}/marisa-trie/${ANDROID_ABI}" # for librime and OpenCC to locate marisa-trie
    # fixme: this will just make CMake only find librime/src instead of librime/include
    # "${CMAKE_CURRENT_SOURCE_DIR}/librime" # for librime to locate bundle X11/keysym.h
)

# set for librime-charcode
set(ICONV_LIBRARY "${PREBUILT_DIR}/libiconv-libicu/${ANDROID_ABI}/lib/libiconv.so")
set(ICONV_INCLUDE_DIR "${PREBUILT_DIR}/libiconv-libicu/${ANDROID_ABI}/include")

# prebuilt snappy
set(Snappy_DIR "${PREBUILT_DIR}/snappy/${ANDROID_ABI}/lib/cmake/Snappy")
find_package(Snappy)

link_libraries(
    log # for glog to link
    Snappy::snappy # for leveldb to link
)
link_directories(
    "${PREBUILT_DIR}/marisa-trie/${ANDROID_ABI}/lib" # for OpenCC to link marisa-trie
    "${PREBUILT_DIR}/libiconv-libicu/${ANDROID_ABI}/lib" # for Boost to link libiconv and libicu
)
include_directories(
     "${PREBUILT_DIR}/marisa-trie/${ANDROID_ABI}/include" # for OpenCC to include marisa-trie
     "${CMAKE_CURRENT_SOURCE_DIR}/librime/include" # for librime to include bundle X11/keysym.h
)

# prebuilt boost
set(BOOST_VERSION "1.80.0")
set(BOOST_MODULES headers atomic filesystem regex system locale chrono thread)
set(BOOST_ROOT "${PREBUILT_DIR}/boost/${ANDROID_ABI}")
set(Boost_DIR "${BOOST_ROOT}/lib/cmake/Boost-${BOOST_VERSION}")
foreach(mod IN LISTS BOOST_MODULES)
    set("boost_${mod}_DIR" "${BOOST_ROOT}/lib/cmake/boost_${mod}-${BOOST_VERSION}")
endforeach()

option(ENABLE_DARTS "" OFF)
option(USE_SYSTEM_MARISA "" TRUE)
option(BUILD_SHARED_LIBS "" OFF)
add_subdirectory(OpenCC)
include(OpenccWorkarounds)

include(RimePlugins)

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
option(BUILD_SHARED_LIBS "" ON)
option(BOOST_USE_CXX11 "" ON)
# workaround for librime/tools/rime_api_console.cc
# TODO: fix it in upstream
string(REPLACE "-Werror=format-security" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
string(REPLACE "-Wformat" "" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
add_definitions("-Wno-format")
# workaround end
add_subdirectory(librime)

add_subdirectory(librime_jni)
