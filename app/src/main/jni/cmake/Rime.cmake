# if you want to add some new plugins, add them to librime_jni/rime_jni.cc too
set(RIME_PLUGINS
    librime-lua
    librime-charcode
    librime-octagram
    librime-predict
)

# symlink plugins
foreach(plugin ${RIME_PLUGINS})
    execute_process(COMMAND ln -sv
        "${CMAKE_SOURCE_DIR}/${plugin}"
        "${CMAKE_SOURCE_DIR}/librime/plugins"
    )
endforeach()

# librime-lua
file(REMOVE "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty")
execute_process(COMMAND ln -sv
    "${CMAKE_SOURCE_DIR}/librime-lua-deps"
    "${CMAKE_SOURCE_DIR}/librime/plugins/librime-lua/thirdparty"
)

# librime-charcode
option(BUILD_WITH_ICU "" OFF)

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
add_subdirectory(librime)

target_link_libraries(rime-charcode-objs
    Boost::asio
    Boost::locale
)

target_link_libraries(rime-lua-objs
    Boost::optional
)
