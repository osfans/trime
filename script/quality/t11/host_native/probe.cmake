# Included in an owned copy of the exact production native top-level CMake,
# at the original add_subdirectory(librime_jni) line. No Android/JNI target.
if(NOT CMAKE_SYSTEM_NAME STREQUAL "Linux" OR ANDROID)
  message(FATAL_ERROR "This diagnostic is explicitly a Linux host build")
endif()
if(NOT BUILD_STATIC OR BUILD_SHARED_LIBS)
  message(FATAL_ERROR "Same-source host tests require the owned static graph")
endif()

foreach(dependency rime-static glog yaml-cpp snappy leveldb marisa libopencc)
  if(NOT TARGET ${dependency})
    message(FATAL_ERROR "Missing owned native dependency: ${dependency}")
  endif()
  get_target_property(imported ${dependency} IMPORTED)
  if(imported)
    message(FATAL_ERROR "Host distribution dependency is not admitted: ${dependency}")
  endif()
endforeach()
get_target_property(rime_type rime-static TYPE)
if(NOT rime_type STREQUAL "STATIC_LIBRARY")
  message(FATAL_ERROR "The bound librime target is not a static archive")
endif()

get_target_property(opencc_definitions libopencc COMPILE_DEFINITIONS)
if(NOT "RAPIDJSON_NOMEMBERITERATORCLASS" IN_LIST opencc_definitions)
  message(FATAL_ERROR "Production OpenCC pointer iterator definition is missing")
endif()

set(test_dir "${CMAKE_SOURCE_DIR}/../../../../script/quality/t11")
add_executable(prediction-native-test "${test_dir}/prediction_native_test.cc")
set_target_properties(prediction-native-test PROPERTIES
  CXX_STANDARD 17
  CXX_STANDARD_REQUIRED YES
  CXX_EXTENSIONS NO
  RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/host-bin")

# rime-static already owns the prediction object library and privacy/prediction
# guards. Compiling another predict_db.cc here would violate its class layout.
target_link_libraries(prediction-native-test PRIVATE rime-static ${CMAKE_DL_LIBS})
target_compile_definitions(prediction-native-test PRIVATE NDEBUG)
# Preserve the original host fixture warning policy; do not add warning filters.
target_compile_options(prediction-native-test PRIVATE
  -O0 -g0 -Wall -Wextra -Werror -Wno-unused-parameter)
target_link_options(prediction-native-test PRIVATE
  "-Wl,-Map=${CMAKE_BINARY_DIR}/prediction-native.map"
  "-Wl,--build-id=none")

# No fabricated build_config.h. Core, plugin objects and fixture all consume the
# same real configured build header and the same privacy/config Context types.
target_include_directories(prediction-native-test BEFORE PRIVATE
  "${CMAKE_BINARY_DIR}/trime-rime-headers"
  "${CMAKE_BINARY_DIR}/trime-rime-privacy"
  "${CMAKE_BINARY_DIR}/trime-rime-config"
  "${CMAKE_BINARY_DIR}/trime-rime-predict"
  "${CMAKE_BINARY_DIR}/librime/src"
  "${CMAKE_SOURCE_DIR}/librime/src"
  "${CMAKE_SOURCE_DIR}/librime/include"
  "${CMAKE_SOURCE_DIR}/librime_jni"
  "${CMAKE_SOURCE_DIR}/librime/deps/opencc/deps/darts-clone-0.32h/include"
  "${test_dir}")

# Generic model generation must accept a newly generated SHA, unlike the
# production bundled-model reader. Only namespace/guard references change in
# these owned copies of the pinned original generic source and bounded tool.
# They never define a second rime::PredictDb or register the prediction plugin.
set(host_model_info "")
if(TRIME_HOST_BUILD_MODEL_TOOL)
  set(model_dir "${test_dir}/model-tool")
  add_executable(prediction-model-native
    "${model_dir}/build_predict_db.cc" "${model_dir}/predict_db.cc")
  set_target_properties(prediction-model-native PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED YES
    CXX_EXTENSIONS NO
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/host-bin")
  target_link_libraries(prediction-model-native PRIVATE rime-static ${CMAKE_DL_LIBS})
  target_compile_definitions(prediction-model-native PRIVATE NDEBUG)
  target_compile_options(prediction-model-native PRIVATE
    -O0 -g0 -Wall -Wextra -Werror -Wno-unused-parameter)
  target_link_options(prediction-model-native PRIVATE
    "-Wl,-Map=${CMAKE_BINARY_DIR}/prediction-model.map"
    "-Wl,--build-id=none")
  target_include_directories(prediction-model-native BEFORE PRIVATE
    "${model_dir}"
    "${CMAKE_BINARY_DIR}/trime-rime-headers"
    "${CMAKE_BINARY_DIR}/trime-rime-privacy"
    "${CMAKE_BINARY_DIR}/trime-rime-config"
    "${CMAKE_BINARY_DIR}/librime/src"
    "${CMAKE_SOURCE_DIR}/librime/src"
    "${CMAKE_SOURCE_DIR}/librime/include"
    "${CMAKE_SOURCE_DIR}/librime_jni"
    "${CMAKE_SOURCE_DIR}/librime/deps/opencc/deps/darts-clone-0.32h/include")
  set(host_model_info "model_builder=$<TARGET_FILE:prediction-model-native>\n")
endif()

file(GENERATE OUTPUT "${CMAKE_BINARY_DIR}/host-targets.txt" CONTENT
"rime_archive=$<TARGET_FILE:rime-static>
glog_archive=$<TARGET_FILE:glog>
yaml_archive=$<TARGET_FILE:yaml-cpp>
snappy_archive=$<TARGET_FILE:snappy>
leveldb_archive=$<TARGET_FILE:leveldb>
marisa_archive=$<TARGET_FILE:marisa>
opencc_archive=$<TARGET_FILE:libopencc>
prediction_binary=$<TARGET_FILE:prediction-native-test>
${host_model_info}rime_sources=$<JOIN:$<TARGET_PROPERTY:rime-static,SOURCES>,\n>
rime_includes=$<JOIN:$<TARGET_PROPERTY:rime-static,INCLUDE_DIRECTORIES>,\n>
rime_compile_definitions=$<JOIN:$<TARGET_PROPERTY:rime-static,COMPILE_DEFINITIONS>,\n>
fixture_includes=$<JOIN:$<TARGET_PROPERTY:prediction-native-test,INCLUDE_DIRECTORIES>,\n>
fixture_compile_definitions=$<JOIN:$<TARGET_PROPERTY:prediction-native-test,COMPILE_DEFINITIONS>,\n>
fixture_link_libraries=$<JOIN:$<TARGET_PROPERTY:prediction-native-test,LINK_LIBRARIES>,\n>
opencc_definitions=$<JOIN:$<TARGET_PROPERTY:libopencc,COMPILE_DEFINITIONS>,\n>
")
