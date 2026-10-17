# SPDX-License-Identifier: GPL-3.0-or-later

# Keep one header definition for the engine, plugins and frontend. Apply these
# warning fixes to guarded build-tree copies, leaving the pinned sources intact.
function(trime_adapt_rime_headers)
  set(root "${CMAKE_BINARY_DIR}/trime-rime-headers")
  set(stage "${CMAKE_BINARY_DIR}/trime-rime-headers-stage")
  foreach(relative include/darts.h src/rime/dict/vocabulary.h)
    set(original "${CMAKE_SOURCE_DIR}/librime/${relative}")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${original}")
    string(REGEX REPLACE "^(include|src)/" "" target "${relative}")
    configure_file("${original}" "${stage}/${target}" COPYONLY)
  endforeach()
  # The internal value_type is int. A top-level const on this scalar return
  # type has no effect on either the result or the member function's constness.
  trime_replace_dependency_cmake("${stage}/darts.h"
    "  const value_type values(std::size_t id) const {"
    "  value_type values(std::size_t id) const {")
  # Spell out the existing usual arithmetic conversion. In particular, do not
  # change the behavior of negative matching lengths or narrow code.size().
  trime_replace_dependency_cmake("${stage}/rime/dict/vocabulary.h"
    "matching_code_size == code.size()"
    "static_cast<Code::size_type>(matching_code_size) == code.size()")
  trime_replace_dependency_cmake("${stage}/rime/dict/vocabulary.h"
    "matching_code_size < code.size()"
    "static_cast<Code::size_type>(matching_code_size) < code.size()")
  foreach(relative darts.h rime/dict/vocabulary.h)
    configure_file("${stage}/${relative}" "${root}/${relative}" COPYONLY)
  endforeach()
  target_include_directories(rime-static BEFORE PUBLIC "${root}")
  foreach(plugin_target rime-lua-objs rime-octagram-objs rime-predict-objs)
    target_include_directories(${plugin_target} BEFORE PRIVATE "${root}")
  endforeach()
endfunction()
