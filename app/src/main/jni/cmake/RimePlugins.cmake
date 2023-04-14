# if you want to add some new plugins, add them to librime_jni/rime_jni.cc too
set(RIME_PLUGINS
  librime-lua
  librime-charcode
  librime-octagram
  librime-predict
)

# plugins didn't use target_link_libraries, the usage-requirements won't work, include manually
set(PLUGIN_INCLUDES "")
find_package(Boost)
foreach(boost_lib ${Boost_LIBRARIES})
  unset(includes)
  get_target_property(includes ${boost_lib} INTERFACE_INCLUDE_DIRECTORIES)
  list(APPEND PLUGIN_INCLUDES ${includes})
endforeach()
include_directories(${PLUGIN_INCLUDES})

# move plugins
file(GLOB old_plugin_files "librime/plugins/*")
foreach(file ${old_plugin_files})
  if(IS_DIRECTORY ${file}) # plugin is directory
    file(REMOVE "${file}")
  endif()
endforeach()
foreach(plugin ${RIME_PLUGINS})
  execute_process(COMMAND ln -s 
    "${CMAKE_SOURCE_DIR}/${plugin}"
    "${CMAKE_SOURCE_DIR}/librime/plugins"
  )
endforeach()

# librime-lua
file(REMOVE "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty")
execute_process(COMMAND ln -s
  "${CMAKE_SOURCE_DIR}/librime-lua-deps"
  "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty"
)

# librime-charcode
option(BUILD_WITH_ICU "" OFF)
# TODO: replace with TOUCH after cmake >= 3.12
file(WRITE "${CMAKE_BINARY_DIR}/include/boost/asio.hpp" "")
