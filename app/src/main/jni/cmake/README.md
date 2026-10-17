<!--
SPDX-FileCopyrightText: 2015 - 2024 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

Please format CMake file with https://github.com/cheshirekow/cmake_format
```
make cmake-format
```

## Pinned Dependency Adaptations

`ThirdPartyCmake.cmake` creates private build-tree copies of snappy, leveldb and
OpenCC. Submodule files and commits stay untouched. The copies retain upstream
licenses and are refreshed on reconfiguration, including when source files are
added or removed.

- Preserve snappy and leveldb's minimum CMake versions, but declare policy
  compatibility through 3.10 with a version range. This resolves CMake 3.31's
  deprecated pre-3.10 policy compatibility without disabling diagnostics.
- OpenCC 1.4.2 already requires CMake 3.12 and uses
  `FindPython3 COMPONENTS Interpreter`. Validate those declarations exactly
  once and keep them unchanged; the old pre-3.10 policy and `FindPythonInterp`
  replacements no longer apply. Only a host interpreter is required; no Python
  runtime or development libraries are linked into Android. The owned copy
  uses OpenCC's official archive version fallback, scoped to that dependency,
  so it cannot accidentally report the outer Trime repository's Git tag.
- Keep the OpenCC exception-path buffer and file-handle ownership guards in the
  owned copy. Its UTF-8-aware `OpenSerializableFileUtf8` opener is retained for
  both reads and writes, with RAII closing files even when parsing or writing
  throws. A stateless file closer calls `std::fclose` directly, avoiding function
  type attributes in the deleter template argument on newer host compilers.
- Use a borrowed `std::string_view` for OpenCC text-dictionary lookup. This
  removes the temporary polymorphic allocation that triggers GCC 16's
  `-Warray-bounds` diagnostics, preserving lexicon byte ordering, explicit
  input length and the identity of the returned entry. The no-op default fast
  prefix method keeps its virtual signature with unnamed parameters; returning
  local matcher pointers uses the existing implicit ownership transfer.
- Compile the plugin minor-version lower-bound check only when the required
  minimum is positive. The pinned unsigned minor version cannot be below zero;
  descriptor size, major-version and plugin-type checks remain intact.
- Build the regional-phrase host converter through `opencc-host/CMakeLists.txt`.
  It uses the same owned-copy adaptations as Android, including the supported
  RapidJSON pointer-iterator definition. The pinned submodule stays unchanged.

Each expected declaration must occur exactly once. If a pinned dependency is
updated and the declaration no longer matches, configuration fails with a
re-audit message instead of silently dropping the adaptation. Re-evaluate or
remove the relevant adaptation after such an update.

The current toolchain is CMake 3.31.6. `CMAKE_POLICY_VERSION_MINIMUM` cannot be
used here because it was added in CMake 4.0. See the official documentation for
[policy version ranges](https://cmake.org/cmake/help/v3.31/command/cmake_minimum_required.html),
[the external policy floor](https://cmake.org/cmake/help/latest/variable/CMAKE_POLICY_VERSION_MINIMUM.html),
and [the Python module migration](https://cmake.org/cmake/help/latest/policy/CMP0148.html).

Run the isolated regression tests with the chosen CMake executable:

```sh
CMAKE_EXECUTABLE="$ANDROID_HOME/cmake/3.31.6/bin/cmake" \
  python3 -B -m unittest discover -s script/quality -p test_third_party_cmake.py -v
```

The tests promote developer and deprecation warnings to errors and cover all
legacy policy adaptations and modern OpenCC validation, host Python execution,
unchanged originals, UTF-8 opener retention, refresh/deletion,
and rejection of changed or ambiguous patch targets.

The separate `script/quality/run_opencc_lookup_control.py` builds the real
adapted library with optimized, warning-as-error compilation. Its independent
linear lookup oracle checks entry types, byte boundaries, embedded NULs, UTF-8,
empty dictionaries and returned-entry identity. It does not replace Android
JNI or device tests.
