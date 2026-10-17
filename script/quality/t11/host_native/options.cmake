# Host-only dependency isolation for the owned production native source copy.
# These are actual OpenCC 1.4.2 options, not unused -D command-line variables.
set(ENABLE_GTEST OFF CACHE BOOL "" FORCE)
set(ENABLE_BENCHMARK OFF CACHE BOOL "" FORCE)
set(BUILD_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(BUILD_OPENCC_JIEBA_PLUGIN OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON OFF CACHE BOOL "" FORCE)

# Android's production graph supplies glog without gflags. Do not silently
# add a host-distribution gflags library through librime's optional search.
set(CMAKE_DISABLE_FIND_PACKAGE_Gflags TRUE)
set(CMAKE_FIND_USE_PACKAGE_REGISTRY OFF)
set(CMAKE_FIND_USE_SYSTEM_PACKAGE_REGISTRY OFF)

# OpenCC explicitly supports source archives through its built-in 1.4.2 fallback.
# A copied tree has no .git; prevent GitVersion from describing the outer Trime
# repository. The real OpenCC Git SHA is bound separately, never invented here.
set(CMAKE_DISABLE_FIND_PACKAGE_Git TRUE)

# The default production graph selects its three in-tree plugins. An inherited
# distribution override would select a different graph despite matching pins.
if(DEFINED ENV{RIME_PLUGINS})
  message(FATAL_ERROR "Unset the inherited RIME_PLUGINS override for this graph")
endif()

# Produce compile_commands.json for concrete header/toolchain provenance.
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
