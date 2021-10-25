# install opencc headers manually
file(GLOB LIBOPENCC_HEADERS
  OpenCC/src/*.hpp
  OpenCC/src/*.h
  "${CMAKE_BINARY_DIR}/OpenCC/src/opencc_config.h"
)
make_directory("${CMAKE_BINARY_DIR}/include/opencc")
foreach(header ${LIBOPENCC_HEADERS})
  configure_file(${header} "${CMAKE_BINARY_DIR}/include/opencc" COPYONLY)
endforeach()
