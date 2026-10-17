# SPDX-License-Identifier: GPL-3.0-or-later

function(trime_require_dependency_text path expected)
  file(READ "${path}" content)
  string(FIND "${content}" "${expected}" first)
  string(FIND "${content}" "${expected}" last REVERSE)
  if(first LESS 0 OR NOT first EQUAL last)
    message(FATAL_ERROR "Re-audit the Trime CMake adaptation for ${path}")
  endif()
endfunction()

function(trime_replace_dependency_cmake path before after)
  trime_require_dependency_text("${path}" "${before}")
  file(READ "${path}" content)
  string(REPLACE "${before}" "${after}" content "${content}")
  file(WRITE "${path}" "${content}")
endfunction()

# OpenCC 1.4.2 already declares modern policy compatibility and host Python3.
# Validate that audited interface without changing its declarations or commands.
function(trime_validate_opencc_cmake source)
  trime_require_dependency_text("${source}/CMakeLists.txt"
                                "cmake_minimum_required(VERSION 3.12)")
  trime_require_dependency_text("${source}/data/CMakeLists.txt"
                                "find_package(Python3 REQUIRED COMPONENTS Interpreter)")
endfunction()

# CMake 3.31 has no CMAKE_POLICY_VERSION_MINIMUM. Adapt build-tree copies only;
# never edit pinned submodules or suppress policy/deprecation diagnostics.
function(trime_add_dependency source_dir binary_dir minimum)
  get_filename_component(source "${source_dir}" ABSOLUTE BASE_DIR
                         "${CMAKE_CURRENT_SOURCE_DIR}")
  set(destination "${CMAKE_CURRENT_BINARY_DIR}/trime-deps/${binary_dir}")
  file(GLOB_RECURSE inputs CONFIGURE_DEPENDS "${source}/*")
  list(FILTER inputs EXCLUDE REGEX "(^|/)\\.git(/|$)")
  set_property(
    DIRECTORY
    APPEND
    PROPERTY CMAKE_CONFIGURE_DEPENDS ${inputs})

  # Regenerate this owned directory so deleted upstream files cannot linger.
  file(REMOVE_RECURSE "${destination}")
  file(
    COPY "${source}/"
    DESTINATION "${destination}"
    PATTERN ".git" EXCLUDE)
  if(binary_dir STREQUAL "OpenCC")
    if(NOT minimum STREQUAL "3.12")
      message(FATAL_ERROR "Re-audit the Trime OpenCC minimum CMake version")
    endif()
    trime_validate_opencc_cmake("${destination}")
    include("${CMAKE_CURRENT_FUNCTION_LIST_DIR}/OpenccResourceSafety.cmake")
    trime_adapt_opencc_resources("${destination}/src")
  else()
    trime_replace_dependency_cmake(
      "${destination}/CMakeLists.txt"
      "cmake_minimum_required(VERSION ${minimum})"
      "cmake_minimum_required(VERSION ${minimum}...3.10)")
  endif()
  add_subdirectory("${destination}" "${binary_dir}")
endfunction()
