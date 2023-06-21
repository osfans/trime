# install opencc headers manually
file(GLOB LIBOPENCC_HEADERS
  "${CMAKE_SOURCE_DIR}/OpenCC/src/*.hpp"
  "${CMAKE_SOURCE_DIR}/OpenCC/src/*.h"
  "${CMAKE_BINARY_DIR}/OpenCC/src/opencc_config.h"
)
file(MAKE_DIRECTORY "${CMAKE_BINARY_DIR}/include/opencc")
foreach(header ${LIBOPENCC_HEADERS})
  configure_file(${header} "${CMAKE_BINARY_DIR}/include/opencc" COPYONLY)
endforeach()
