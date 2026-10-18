# SPDX-License-Identifier: GPL-3.0-or-later

# Match the pinned raw Git bytes. Reject CRLF or source changes rather than
# adapting a different MARISA revision or silently loosening the audit.
function(trime_require_opencc_marisa_sha path expected)
  if(NOT EXISTS "${path}" OR IS_DIRECTORY "${path}")
    message(FATAL_ERROR "Re-audit the OpenCC MARISA source: ${path}")
  endif()
  file(SHA256 "${path}" actual)
  if(NOT actual STREQUAL expected)
    message(FATAL_ERROR "Re-audit the OpenCC MARISA source SHA: ${path}")
  endif()
endfunction()

function(trime_adapt_opencc_marisa source)
  set(base "${source}/deps/marisa-0.3.1/lib/marisa/grimoire")
  # Validate all inputs before changing any owned file.
  trime_require_opencc_marisa_sha("${base}/io/reader.cc"
    "6416633a5ee6df06871b6ac6c8d0fc1d85d62c77aedc850a868e39a161c62c77")
  trime_require_opencc_marisa_sha("${base}/io/writer.cc"
    "925180e32050fec79a3a45dc9e553d4df728f5ad2f56295033c77cf904cd3ae8")
  trime_require_opencc_marisa_sha("${base}/vector/bit-vector.cc"
    "0645c6848aa325a2558d04dec14ea35a5ea06dcc06cbe13a411af1887cc0ce97")

  # The existing minimum already limits each Windows request to INT_MAX,
  # which is representable by the unsigned-int count required by _read/_write.
  # Keep all partial-I/O and remaining-byte accounting unchanged.
  foreach(name reader writer)
    trime_replace_dependency_cmake("${base}/io/${name}.cc"
      "      const unsigned int count = (size < CHUNK_SIZE) ? size : CHUNK_SIZE;"
      [=[      const unsigned int count =
          static_cast<unsigned int>((size < CHUNK_SIZE) ? size : CHUNK_SIZE);]=])
  endforeach()
  # countr_zero returns size_t. Keep that type without changing its value or
  # the subsequent bit selection/shift operations.
  trime_replace_dependency_cmake("${base}/vector/bit-vector.cc"
    "  const int skip = countr_zero(x >> 7);"
    "  const std::size_t skip = countr_zero(x >> 7);")
endfunction()
