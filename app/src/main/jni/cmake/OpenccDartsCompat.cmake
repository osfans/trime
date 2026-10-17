# SPDX-License-Identifier: GPL-3.0-or-later

# OpenCC and Rime vendor different Darts definitions under the same namespace.
# Isolate OpenCC's private copy so their weak inline symbols cannot coalesce.
# This only changes names in owned build-tree files, never dictionary bytes.
function(trime_replace_opencc_darts_token path before after expected)
  file(READ "${path}" content)
  string(REPLACE "${before}" "" stripped "${content}")
  string(LENGTH "${content}" original_length)
  string(LENGTH "${stripped}" stripped_length)
  string(LENGTH "${before}" token_length)
  math(EXPR count "(${original_length} - ${stripped_length}) / ${token_length}")
  if(NOT count EQUAL expected)
    message(FATAL_ERROR "Re-audit the OpenCC Darts token ${before} in ${path}")
  endif()
  string(REPLACE "${before}" "${after}" content "${content}")
  file(WRITE "${path}" "${content}")
endfunction()

function(trime_adapt_opencc_darts source)
  set(header "${source}/deps/darts-clone-0.32h/include/darts.h")
  set(consumer "${source}/src/DartsDict.cpp")
  trime_require_dependency_text("${header}" "#ifndef DARTS_H_\n#define DARTS_H_")
  trime_require_dependency_text("${header}" "namespace Darts {")
  trime_require_dependency_text("${consumer}" "#include \"darts.h\"")
  trime_replace_opencc_darts_token("${header}"
    "DARTS_H_" "TRIME_OPENCC_DARTS_H_" 3)
  trime_replace_opencc_darts_token("${header}"
    "namespace Darts" "namespace TrimeOpenccDarts" 2)
  trime_replace_opencc_darts_token("${header}"
    "Darts::" "TrimeOpenccDarts::" 10)
  trime_replace_opencc_darts_token("${consumer}"
    "Darts::" "TrimeOpenccDarts::" 19)
endfunction()
