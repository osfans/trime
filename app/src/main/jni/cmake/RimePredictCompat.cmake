# SPDX-License-Identifier: GPL-3.0-or-later

function(trime_adapt_rime_predict)
  set(original "${CMAKE_SOURCE_DIR}/librime-predict/src")
  set(owned "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/../librime_jni/predict")
  set(output "${CMAKE_BINARY_DIR}/trime-rime-predict")
  # These overlays preserve the public component API and database wire format.
  # An upstream change requires review, rather than silently using stale code.
  set(pinned
    "predictor.cc|989e810a69778aa02381345fcf54c02dee80e3ab35a6974fe7cc0690ad20c0b7"
    "predictor.h|6d5e40f23868d3b4687441b509425411040226f3cacf9e95046b6d3604af1399"
    "predict_engine.cc|b81cf09293d5619adc10375fb273854f0b0b4c1dc2c78064aca9538dacb374fe"
    "predict_engine.h|5744c35156fec751cc9f0abbe27c0dae0ac7db7b130d1c23041d2bffd2a20fd3"
    "predict_db.cc|459b1c7a889e3792e30cf19d874c9acfd9b7423203c4cd3d46e933539768fd19"
    "predict_db.h|210b07c8b81437921a54d5cd11bf8d1d47044debe000e5497409e18e8e28df0f"
    "predict_translator.cc|60bb642a1bc7f22d856e7e271a843972bc90b48d295d9c7d0ced3ff4cf43af7b"
    "predict_translator.h|c33dd3e2affef5b964654302e3dba6301d2c130af9ec787f4024c9ec8f67910c"
    "predict_module.cc|38e26a42ea74c06e13715154f8d413b05e16a2cbd2b319d1182ba2c8961d9df9")
  foreach(binding IN LISTS pinned)
    string(REPLACE "|" ";" fields "${binding}")
    list(GET fields 0 name)
    list(GET fields 1 expected)
    file(SHA256 "${original}/${name}" actual)
    if(NOT actual STREQUAL expected)
      message(FATAL_ERROR "Re-audit Rime predict source: ${name}")
    endif()
    set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${original}/${name}")
    if(name STREQUAL "predict_module.cc" OR name STREQUAL "predict_translator.h")
      # Quoted includes search beside the source before include directories.
      # Keep every plugin TU beside the same headers to avoid ODR violations.
      configure_file("${original}/${name}" "${output}/${name}" COPYONLY)
    else()
      configure_file("${owned}/${name}" "${output}/${name}" COPYONLY)
    endif()
  endforeach()
  foreach(name prediction_policy.h prediction_key_policy.h model_integrity.h)
    configure_file("${owned}/${name}" "${output}/${name}" COPYONLY)
  endforeach()
  configure_file("${owned}/LICENSE" "${output}/LICENSE" COPYONLY)

  set(model "${CMAKE_CURRENT_FUNCTION_LIST_DIR}/../../assets/shared/trime_predict.db")
  file(SIZE "${model}" model_size)
  file(SHA256 "${model}" model_sha256)
  if(model_size GREATER 65536 OR model_size LESS 52)
    message(FATAL_ERROR "The bundled prediction model exceeds its 64 KiB budget")
  endif()
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${model}")
  set(header "// Generated from the bundled model; never reads a user-supplied sidecar.\n#pragma once\n#include <cstddef>\nnamespace trime::prediction {\ninline constexpr std::size_t kBundledModelBytes = ${model_size};\ninline constexpr char kBundledModelSha256[] = \"${model_sha256}\";\n}\n")
  file(GENERATE OUTPUT "${output}/trime_predict_model_hash.h" CONTENT "${header}")

  get_target_property(sources rime-predict-objs SOURCES)
  foreach(name predictor.cc predict_engine.cc predict_db.cc predict_translator.cc predict_module.cc)
    set(matches 0)
    foreach(source IN LISTS sources)
      if(source STREQUAL "src/${name}" OR
         source STREQUAL "${original}/${name}" OR
         source STREQUAL "${CMAKE_SOURCE_DIR}/librime/plugins/librime-predict/src/${name}")
        list(REMOVE_ITEM sources "${source}")
        math(EXPR matches "${matches} + 1")
      endif()
    endforeach()
    if(NOT matches EQUAL 1)
      message(FATAL_ERROR "Re-audit Rime predict source registration: ${name}")
    endif()
    list(APPEND sources "${output}/${name}")
  endforeach()
  set_property(TARGET rime-predict-objs PROPERTY SOURCES "${sources}")
  target_include_directories(rime-predict-objs BEFORE PRIVATE
    "${output}" "${CMAKE_SOURCE_DIR}/librime_jni" "${original}")
  target_sources(rime-static PRIVATE "${CMAKE_SOURCE_DIR}/librime_jni/trime_prediction_guard.cc")
endfunction()
