# Built-In Rime Data Adaptation

`prepareRimeAssets` runs after `installOpenCCData` and before checksum generation.
It copies source assets into `app/build/generated/rimeAssets`. The pinned Rime
submodules and the symlinks in `src/main/assets` are not edited. Android packages
the prepared directory plus `app/build/generated/rimeChecksums`.

Only the packaged `luna_pinyin.dict.yaml` header selects `luna_pinyin_essay` and
gets a `-trime-v1` version suffix. Its TSV body and upstream attribution remain
unchanged. The original `essay.txt` remains available to stroke and custom
dictionaries. Source and output hashes, plus rejected rows, are recorded under
`app/build/reports/rime-assets`; retain them with each tested APK.

The acceptance predicate mirrors the pinned `EntryCollector` and `ScriptEncoder`:

- Keep every explicit dictionary word's essay row for frequency lookup.
- Only a dictionary entry with one code unit becomes an encoder translation.
- Apply absolute/percentage/preset weights and the 5% reading threshold.
- Accept an inferred phrase only if it can be segmented into available translated
  words and is at most 32 Unicode code points. A trie and boolean position table
  answer acceptance without enumerating pronunciation combinations.
- Preserve accepted essay rows verbatim and in order. Do not normalize output
  punctuation, invent readings, change weights or use benchmark answers.

This excludes only entries for which the pinned encoder cannot produce a code;
it does not add support for those words. Updated dictionary header semantics,
uncoded rows, duplicate readings, custom columns, imports or table encoders fail
the build and require review rather than silently applying an incompatible rule.

Run `:build-logic:convention:test`, verify the packaged checksum map, and perform
a new unseeded deployment plus native/candidate regressions after any change.
Source-tree tests alone do not prove the generated APK resources are correct.

## Native Config Overlay

`RimeConfigCompat.cmake` adapts six pinned source/header files in the build tree.
It preserves existing overloads, explicitly marks optional loads, and retains the
normal missing-required-file and YAML-parser diagnostics. New installation/user
metadata and not-yet-built cache files do not get probed as required input files.
Missing optional resources still retain their source paths for dependency
timestamps, so installing a previously missing grammar can trigger rebuilding.

Failed links clear partial compiled config data; `ConfigFileUpdate` and workspace
updates propagate that failure. Trime refuses stale theme fallback after a failed
rebuild, and failed startup maintenance cannot publish READY.

Each replacement must match exactly once. A changed upstream declaration fails
configuration with a re-audit message. No global log-level downgrade, prebuilt
cache seeding, artificial empty config file or submodule edit is used.
Generation uses a staging directory and copies only changed content to the
published overlay, so reconfiguration does not invalidate every native object.

Run `test_rime_config_cmake.py` for source isolation/drift guards, then the real
`engine`, `shutdown` and `startupFailure` instrumentation probes on both APIs.
The injected-failure runs must retain their expected error logs; they are not
warning-free runs.
