# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

# if you want to add some new plugins, add them to librime_jni/rime_jni.cc too
set(RIME_PLUGINS librime-lua librime-octagram librime-predict)

# symlink plugins
foreach(plugin ${RIME_PLUGINS})
  if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/${plugin}")
    file(CREATE_LINK "${CMAKE_SOURCE_DIR}/${plugin}"
         "${CMAKE_SOURCE_DIR}/librime/plugins/${plugin}" COPY_ON_ERROR SYMBOLIC)
  endif()
endforeach()

# librime-lua depends on the thirdparty sources in a separate submodule.
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty")
  file(CREATE_LINK "${CMAKE_SOURCE_DIR}/librime-lua-deps"
       "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty"
       COPY_ON_ERROR SYMBOLIC)
endif()

# librime
option(BUILD_TEST "Build librime tests" OFF)
option(BUILD_STATIC "Build librime statically" ON)
add_subdirectory(librime)
target_compile_options(
  rime-static PRIVATE "-Wno-error=deprecated-declarations")
target_link_libraries(rime-static PRIVATE trime-reproducible-build)
target_link_libraries(rime-lua-objs PRIVATE trime-reproducible-build)
target_link_libraries(rime-octagram-objs PRIVATE trime-reproducible-build)
