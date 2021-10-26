if(DEFINED ENV{CAPNP})
  # FORCE to override CapnProtoConfig.cmake
  set(CAPNP_EXECUTABLE "$ENV{CAPNP}" CACHE FILEPATH "" FORCE)
else()
  find_program(CAPNP_EXECUTABLE capnp)
endif()

if(DEFINED ENV{CAPNPC_CXX})
  set(CAPNPC_CXX_EXECUTABLE "$ENV{CAPNPC_CXX}" CACHE FILEPATH "" FORCE)
else()
  # Also search in the same directory that `capnp` was found in
  get_filename_component(capnp_dir "${CAPNP_EXECUTABLE}" DIRECTORY)
  find_program(CAPNPC_CXX_EXECUTABLE capnpc-c++ HINTS "${capnp_dir}")
endif()

set(CAPNPC_IMPORT_DIRS "${CMAKE_SOURCE_DIR}/capnproto/c++/src")

# placeholder, targets file will be installed later
# use WRITE instead of TOUCH to avoid duplicated targets while buildCMakeRelWithDebInfo
file(WRITE "${CMAKE_BINARY_DIR}/capnproto/c++/cmake/CapnProtoTargets.cmake" "")
set(CapnProto_DIR "${CMAKE_BINARY_DIR}/capnproto/c++/cmake")
