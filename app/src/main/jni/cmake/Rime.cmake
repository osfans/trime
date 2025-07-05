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

# librime-lua
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty")
  file(CREATE_LINK "${CMAKE_SOURCE_DIR}/librime-lua-deps"
       "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty"
       COPY_ON_ERROR SYMBOLIC)
endif()

find_package(Git REQUIRED)
if(NOT Git_FOUND)
    message(FATAL_ERROR "Git not found!")
endif()

set(PATCH_FILE "${CMAKE_SOURCE_DIR}/patches/lua.patch")
set(PATCH_STAMP "${CMAKE_CURRENT_BINARY_DIR}/.git_patch_applied")

add_custom_command(
  OUTPUT ${PATCH_STAMP}
  COMMAND ${GIT_EXECUTABLE} apply ${PATCH_FILE} || true
  COMMAND ${CMAKE_COMMAND} -E touch ${PATCH_STAMP}
  COMMAND_EXPAND_LISTS
  WORKING_DIRECTORY "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty"
  DEPENDS ${PATCH_FILE}
  VERBATIM
)

add_custom_target(apply_git_patch ALL
  DEPENDS ${PATCH_STAMP}
)

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
add_subdirectory(librime)
add_dependencies(rime-static apply_git_patch)
target_compile_options(
  rime-static PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=." "-Wno-error=deprecated-declarations")

target_compile_options(
  rime-lua-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

target_compile_options(
  rime-octagram-objs PRIVATE "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
