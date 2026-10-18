# SPDX-License-Identifier: GPL-3.0-or-later

# Interrupted deployment can leave empty/truncated generated dictionaries.
# Keep the pinned source untouched and let the existing compiler rebuild them.
function(trime_adapt_rime_mapped_file)
  set(output "${CMAKE_BINARY_DIR}/trime-rime-mapped-file")
  set(stage "${CMAKE_BINARY_DIR}/trime-rime-mapped-file-stage")
  set(files rime/dict/mapped_file.cc rime/dict/mapped_file.h)
  foreach(relative IN LISTS files)
    set(original "${CMAKE_SOURCE_DIR}/librime/src/${relative}")
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${original}")
    configure_file("${original}" "${stage}/${relative}" COPYONLY)
  endforeach()

  trime_replace_dependency_cmake("${stage}/rime/dict/mapped_file.cc"
    [=[bool MappedFile::OpenReadOnly() {
  if (!Exists()) {
    LOG(ERROR) << "attempt to open non-existent file '" << file_path_ << "'.";
    return false;
  }
  file_.reset(new MappedFileImpl(file_path_, MappedFileImpl::kOpenReadOnly));]=]
    [=[bool MappedFile::OpenReadOnly() {
  if (!Exists()) {
    LOG(ERROR) << "attempt to open non-existent file '" << file_path_ << "'.";
    return false;
  }
  // mmap cannot open a zero-length cache left by interrupted deployment.
  // Return a cache miss so the normal dictionary compiler can rebuild it.
  // Other filesystem failures retain their original exceptions/diagnostics.
  if (std::filesystem::file_size(file_path_) == 0) {
    return false;
  }
  file_.reset(new MappedFileImpl(file_path_, MappedFileImpl::kOpenReadOnly));]=])
  trime_replace_dependency_cmake("${stage}/rime/dict/mapped_file.h"
    [=[  if (!IsOpen() || offset > size_)
    return NULL;
  return reinterpret_cast<T*>(address() + offset);]=]
    [=[  if (!IsOpen() || offset > size_ || sizeof(T) > size_ - offset)
    return NULL;
  return reinterpret_cast<T*>(address() + offset);]=])

  # Copy only changed bytes so a no-op reconfigure keeps native objects current.
  foreach(relative IN LISTS files)
    configure_file("${stage}/${relative}" "${output}/${relative}" COPYONLY)
  endforeach()
  get_target_property(sources rime-static SOURCES)
  set(relative rime/dict/mapped_file.cc)
  set(matches 0)
  foreach(source IN LISTS sources)
    if(source STREQUAL relative OR source STREQUAL "${CMAKE_SOURCE_DIR}/librime/src/${relative}")
      list(REMOVE_ITEM sources "${source}")
      math(EXPR matches "${matches} + 1")
    endif()
  endforeach()
  if(NOT matches EQUAL 1)
    message(FATAL_ERROR "Re-audit Rime source registration: ${relative}")
  endif()
  list(APPEND sources "${output}/${relative}")
  set_property(TARGET rime-static PROPERTY SOURCES "${sources}")
  target_include_directories(rime-static BEFORE PUBLIC "${output}")
  # Plugin object libraries do not inherit rime-static's usage requirements.
  # Every caller must see the same inline Find<T> definition and bounds check.
  foreach(plugin_target rime-lua-objs rime-octagram-objs rime-predict-objs)
    target_include_directories(${plugin_target} BEFORE PRIVATE "${output}")
  endforeach()
endfunction()
