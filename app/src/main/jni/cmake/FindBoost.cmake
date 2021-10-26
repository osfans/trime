set(Boot_FOUND TRUE)

# libraries that can't compile
set(BOOST_EXCLUDE_LIBRARIES "context;coroutine;fiber" CACHE STRING "" FORCE)

# we have to specify all libraries and their dependencies manually for Boost_LIBRARIES
# you can dump them from boost/tools/cmake/include/BoostRoot.cmake ${__boost_include_libraries}
set(BOOST_INSTALLED_LIBRARIES
  # used by librime directly:
  "algorithm"
  "any"
  "crc"
  "date_time"
  "dll"
  "filesystem"
  "format"
  "interprocess"
  "iostreams"
  "lexical_cast"
  "optional"
  "range"
  "regex"
  "scope_exit"
  "signals2"
  "utility"
  "uuid"
  # for librime-charcode:
  "locale"
  # dumped from ${__boost_include_libraries}:
  "array"
  "assert"
  "bind"
  "concept_check"
  "config"
  "core"
  "exception"
  "function"
  "iterator"
  "mpl"
  "static_assert"
  "throw_exception"
  "tuple"
  "type_traits"
  "unordered"
  "type_index"
  "integer"
  "io"
  "numeric_conversion"
  "smart_ptr"
  "tokenizer"
  "winapi"
  "move"
  "predef"
  "spirit"
  "system"
  "container_hash"
  "detail"
  "container"
  "intrusive"
  "preprocessor"
  "random"
  "conversion"
  "typeof"
  "parameter"
  "variant"
  "serialization"
  "tti"
  "function_types"
  "fusion"
  "endian"
  "phoenix"
  "pool"
  "proto"
  "thread"
  "dynamic_bitset"
  "mp11"
  "atomic"
  "chrono"
  "align"
  "ratio"
  "rational"
)

# see boost/tools/boost_install/BoostConfig.cmake
foreach(comp ${BOOST_INSTALLED_LIBRARIES})
  LIST(APPEND Boost_LIBRARIES Boost::${comp})
endforeach()

# for librime-charcode
set(Boost_LOCALE_LIBRARIES Boost::locale)
