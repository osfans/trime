# if you want to add some new plugins, add them to librime_jni/rime_jni.cc too
set(RIME_PLUGINS librime-lua librime-charcode librime-octagram librime-predict)

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

# librime-charcode
option(BUILD_WITH_ICU "" OFF)

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
add_subdirectory(librime)

target_link_libraries(rime-charcode-objs Boost::asio Boost::locale)

target_link_libraries(rime-lua-objs Boost::optional)
