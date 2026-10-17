# SPDX-License-Identifier: GPL-3.0-or-later

function(trime_adapt_key_binder_ascii source)
  # X11 keycodes can exceed unsigned-char range. Passing them to isalpha is
  # undefined, and casting would incorrectly treat functional keys as bytes.
  # This URL paging reinterpretation only needs literal ASCII letters.
  trime_replace_dependency_cmake("${source}"
    [=[      std::isalpha(ch)) {]=]
    [=[      ((ch >= 'a' && ch <= 'z') ||
       (ch >= 'A' && ch <= 'Z'))) {]=])
endfunction()
