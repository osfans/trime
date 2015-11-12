project(JNI)
cmake_minimum_required (VERSION 3.3)

# options
option(BUILD_TEST "Build and run tests" OFF)
OPTION (BUILD_SHARED_LIBS "Build Shared Libraries" ON)
option(ENABLE_LOGGING "Enable logging with google-glog library" ON)
option(BOOST_USE_SIGNALS2 "Boost use signals2 instead of signals" OFF)
option(LEVELDB_USE_SNAPPY "Leveldb use snappy" ON)

# includes
if(WIN32)
  include_directories(boost/libs/uuid/include)
endif()
include_directories(
  librime/include
  librime/thirdparty/include
  marisa-trie/lib
  leveldb/include
  yaml-cpp/include
  libiconv/libiconv-1.14/include
  leveldb/include
  snappy
  include
  )

# flags
add_definitions(-w -DOpencc_BUILT_AS_STATIC)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -fpic -frtti -fexceptions")
if(UNIX)
  add_definitions(-fPIC)
endif()

# snappy
set(SNAPPY_SOURCES
  snappy/snappy.cc
  snappy/snappy-sinksource.cc
  )
add_library(snappy-static STATIC ${SNAPPY_SOURCES})
if(NOT EXISTS ${CMAKE_CURRENT_LIST_DIR}/snappy/snappy-stubs-public.h)
add_custom_target(snappy_h
  COMMAND ${CMAKE_CURRENT_LIST_DIR}/snappy/autogen.sh
  COMMAND ./configure
  WORKING_DIRECTORY ${CMAKE_CURRENT_LIST_DIR}/snappy)
add_dependencies(snappy-static snappy_h)
endif()

# marisa
file(GLOB_RECURSE MARISA_SOURCES marisa-trie/lib/marisa/*.cc)
add_library(marisa-static STATIC ${MARISA_SOURCES})

# iconv
set(ICONV_SOURCES
  libiconv/libiconv-1.14/libcharset/lib/localcharset.c
  libiconv/libiconv-1.14/lib/iconv.c
  libiconv/libiconv-1.14/lib/relocatable.c
  )
add_library(iconv-static STATIC ${ICONV_SOURCES})
target_compile_definitions(iconv-static PRIVATE
  ANDROID LIBDIR="c" BUILDING_LIBICONV IN_LIBRARY
  )
target_include_directories(iconv-static BEFORE PRIVATE
  libiconv/libiconv-1.14/libcharset
  libiconv/libiconv-1.14/lib
  libiconv/libiconv-1.14/libcharset/include
  )

# boost
file(GLOB BOOST_SOURCES
  boost/libs/filesystem/src/*.cpp
  boost/libs/regex/src/*.cpp
  boost/libs/system/src/error_code.cpp
  boost/libs/locale/src/encoding/codepage.cpp
  )
if(NOT BOOST_USE_SIGNALS2)
  add_definitions(-DBOOST_SIGNALS_NO_DEPRECATION_WARNING)
  file(GLOB BOOST_SIGNAL_SOURCES boost/libs/signals/src/*.cpp)
endif()
add_library(boost-static STATIC ${BOOST_SOURCES} ${BOOST_SIGNAL_SOURCES})
target_compile_definitions(boost-static PRIVATE BOOST_LOCALE_WITH_ICONV)
target_link_libraries(boost-static iconv-static)
if(WIN32)
target_link_libraries(boost-static boost_thread-mt.a)
endif()

# leveldb
set(LEVELDB_SOURCES
  leveldb/db/builder.cc
  leveldb/db/c.cc
  leveldb/db/db_impl.cc
  leveldb/db/db_iter.cc
  leveldb/db/dbformat.cc
  leveldb/db/filename.cc
  leveldb/db/log_reader.cc
  leveldb/db/log_writer.cc
  leveldb/db/memtable.cc
  leveldb/db/repair.cc
  leveldb/db/table_cache.cc
  leveldb/db/version_edit.cc
  leveldb/db/version_set.cc
  leveldb/db/write_batch.cc
  leveldb/table/block.cc
  leveldb/table/block_builder.cc
  leveldb/table/filter_block.cc
  leveldb/table/format.cc
  leveldb/table/iterator.cc
  leveldb/table/merger.cc
  leveldb/table/table.cc
  leveldb/table/table_builder.cc
  leveldb/table/two_level_iterator.cc
  leveldb/util/arena.cc
  leveldb/util/bloom.cc
  leveldb/util/cache.cc
  leveldb/util/coding.cc
  leveldb/util/comparator.cc
  leveldb/util/crc32c.cc
  leveldb/util/env.cc
  leveldb/util/filter_policy.cc
  leveldb/util/hash.cc
  leveldb/util/histogram.cc
  leveldb/util/logging.cc
  leveldb/util/options.cc
  leveldb/util/status.cc
  )
if(UNIX)
list(APPEND LEVELDB_SOURCES
  leveldb/util/env_posix.cc
  leveldb/port/port_posix.cc
  )
elseif(WIN32)
list(APPEND LEVELDB_SOURCES
  leveldb/port/port_win.cc
  leveldb/util/env_boost.cc
  leveldb/util/win_logger.cc
  )
endif(UNIX)
set(LEVELDB_FLAGS _REENTRANT NDEBUG LEVELDB_ATOMIC_PRESENT)
if(ANDROID)
list(APPEND LEVELDB_FLAGS OS_ANDROID LEVELDB_PLATFORM_POSIX)
elseif(UNIX)
list(APPEND LEVELDB_FLAGS OS_LINUX LEVELDB_PLATFORM_POSIX)
elseif(WIN32)
list(APPEND LEVELDB_FLAGS OS_WINDOWS LEVELDB_PLATFORM_WINDOWS)
endif()
add_library(leveldb-static STATIC ${LEVELDB_SOURCES})
target_include_directories(leveldb-static BEFORE PRIVATE leveldb)
target_compile_definitions(leveldb-static PRIVATE ${LEVELDB_FLAGS})
target_compile_options(leveldb-static PRIVATE -fno-builtin-memcmp)
if(LEVELDB_USE_SNAPPY)
target_compile_definitions(leveldb-static PRIVATE SNAPPY)
target_link_libraries(leveldb-static snappy-static)
endif()

# yaml-cpp
#add_subdirectory(yaml-cpp)
file(GLOB YAML-CPP_SOURCES yaml-cpp/src/*.cpp)
add_library(yaml-cpp-static STATIC ${YAML-CPP_SOURCES})

# miniglog
#add_subdirectory(miniglog)
set(GLOG_SOURCES miniglog/miniglog/logging.cc)
add_library(miniglog-static STATIC ${GLOG_SOURCES})

# opencc
#add_subdirectory(OpenCC)
set(OPENCC_SOURCES
  OpenCC/src/BinaryDict.cpp
  OpenCC/src/Config.cpp
  OpenCC/src/Conversion.cpp
  OpenCC/src/ConversionChain.cpp
  OpenCC/src/Converter.cpp
  OpenCC/src/DartsDict.cpp
  OpenCC/src/Dict.cpp
  OpenCC/src/DictEntry.cpp
  OpenCC/src/DictGroup.cpp
  OpenCC/src/MaxMatchSegmentation.cpp
  OpenCC/src/PhraseExtract.cpp
  OpenCC/src/SimpleConverter.cpp
  OpenCC/src/Segmentation.cpp
  OpenCC/src/TextDict.cpp
  OpenCC/src/UTF8StringSlice.cpp
  OpenCC/src/UTF8Util.cpp
  )
add_library(opencc-static STATIC ${OPENCC_SOURCES})
target_include_directories(opencc-static BEFORE PRIVATE
  OpenCC/deps/darts-clone
  OpenCC/deps/rapidjson-0.11
  OpenCC/deps/tclap-1.2.1
  )
target_compile_definitions(opencc-static PRIVATE VERSION="1.0.3")
if(ANDROID)
elseif(UNIX)
target_compile_definitions(opencc-static PRIVATE PKGDATADIR="/usr/share/opencc")
endif()

# rime
#add_subdirectory(librime)
find_package (Threads)
file(GLOB_RECURSE RIME_SOURCES librime/src/*.cc)
add_library(rime SHARED ${RIME_SOURCES})
target_link_libraries(rime
  opencc-static
  yaml-cpp-static
  marisa-static
  miniglog-static
  leveldb-static
  boost-static
  ${CMAKE_THREAD_LIBS_INIT}
  )
target_compile_definitions(rime PRIVATE
  RIME_BUILD_SHARED_LIBS
  RIME_VERSION="1.2.9"
  RIME_ENABLE_LOGGING
  RIME_EXPORTS
)
if(BOOST_USE_SIGNALS2)
  target_compile_definitions(rime PRIVATE BOOST_SIGNALS2)
endif(BOOST_USE_SIGNALS2)
if(ANDROID)
  target_link_libraries(rime log atomic)
endif()

# rime tools
if(ANDROID)
else()
foreach(tool rime_api_console rime_console rime_deployer rime_dict_manager rime_patch)
add_executable(${tool} "librime/tools/${tool}.cc")
target_link_libraries(${tool} rime ${CMAKE_THREAD_LIBS_INIT})
endforeach(tool)
endif(ANDROID)

# rime_jni
if(UNIX)
file(GLOB RIME_JNI_SOURCES librime_jni/*.cc)
add_library(rime_jni SHARED ${RIME_JNI_SOURCES})
target_link_libraries(rime_jni rime)
execute_process(
  COMMAND git --git-dir ${CMAKE_CURRENT_LIST_DIR}/OpenCC/.git describe --tags
  OUTPUT_VARIABLE OPENCC_VERSION
  )
string(STRIP ${OPENCC_VERSION} OPENCC_VERSION)
execute_process(
  COMMAND git --git-dir ${CMAKE_CURRENT_LIST_DIR}/librime/.git describe --tags
  OUTPUT_VARIABLE LIBRIME_VERSION
  )
string(STRIP ${LIBRIME_VERSION} LIBRIME_VERSION)
target_compile_definitions(rime_jni PRIVATE
  CLASSNAME="com/osfans/trime/Rime"
  OPENCC_VERSION="${OPENCC_VERSION}"
  LIBRIME_VERSION="${LIBRIME_VERSION}"
  )

if(ANDROID)
  target_link_libraries(rime_jni log)
else(ANDROID)
  target_include_directories(rime_jni BEFORE PRIVATE
    /usr/lib/jvm/default/include
    /usr/lib/jvm/default/include/linux
  )
endif(ANDROID)
endif(UNIX)
