# Native T9 regression tests

These tests run the production `t9.cc` and `t9_assist.cc` against a host build of
the repository's pinned, unmodified librime submodule. They use small, isolated
test dictionaries. They do not load prediction plugins, read an application's
user directory or exercise Android/JNI/UI code.

The controller cases cover exact syllable locks, ambiguous segmentation,
completion, editing, stale revisions, undo boundaries, raw-code Return,
UTF-16 preedit lengths and ordinary full/double-pinyin fallback. The assistance
cases cover independent optional rules, bounded extra choices, original-code
preservation and disabled-rule stale actions. The application currently passes
an assistance mask of zero; these tests do not enable those rules in the UI.
Both tests reopen their own user dictionary in a second process to verify
canonical learning without private spelling envelopes.

Use a C++17 compiler, CMake, Ninja and the host development libraries required by
librime: Boost.Regex, glog, yaml-cpp, LevelDB, marisa and OpenCC. Initialize the
repository's submodules first. From the repository root:

```sh
RIME_PLUGINS='' cmake -S app/src/main/jni/librime -B build/t9-host-engine -G Ninja \
  -DBUILD_SHARED_LIBS=OFF -DBUILD_STATIC=OFF -DBUILD_TEST=OFF \
  -DBUILD_DATA=OFF -DBUILD_SAMPLE=OFF -DENABLE_EXTERNAL_PLUGINS=OFF \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
cmake --build build/t9-host-engine --target rime-static --parallel 1
python3 app/src/test/native/t9/run.py --rime-build build/t9-host-engine \
  --double-schema /path/to/double_pinyin.schema.yaml --out build/t9-native-results
```

Supply the ordinary natural-code `double_pinyin.schema.yaml` from Rime's double
pinyin package. It is copied only into the disposable test directory, with a
test dictionary override. No third-party dictionary or double-pinyin schema is
vendored by this test. Use a new output directory for every run; failed evidence
is retained. The result records source, library, fixture and dependency hashes
and the exact compile/run commands. Build the engine in a fresh directory after
changing its source or dependencies; matching metadata alone cannot attest an
arbitrary pre-existing library.

The fixtures disable production simplification and replace the dictionary. A
host pass does not establish real-dictionary ranking, Android ABI/JNI behavior,
TalkBack interaction, touch handling, editor integration or device performance.
