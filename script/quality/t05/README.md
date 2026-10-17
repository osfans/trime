# T05: separate phonetic fuzzing and digit repair

Development version: `3.3.13-t9.7-dev.1` / `20261110`. Not a published release.

## Interaction contract

Keyboard settings expose nine independent, default-off switches. The six phonetic
rules are n/l, z/zh, c/ch, s/sh, en/eng and in/ing. Three separate typo rules cover
an edge-adjacent numeric key substitution, one missing digit, and one repeated
adjacent digit. No diagonal substitution, transposition, arbitrary extra-digit
deletion or two-error search is performed.

The original exact and completion pinyin choices stay first. Extra suggestions
show their canonical spelling and source on two lines; accessibility descriptions
include the source. Selecting one locks that syllable, not a Hanzi. The ordinary
Hanzi menu is unchanged until that explicit choice. Select a Hanzi to commit.
Raw digits, following segments, original offsets, unlock, boundary deletion and
undo use the existing T01/T02 controller. Locked segments retain provenance.

Turning off a rule invalidates stale suggestions immediately, but does not undo
an explicitly chosen lock or erase editing history. Outside an enabled T9 schema,
these settings have no effect. Existing completion is not relabeled as correction.

## Engine choice

The pinned librime revision is `33e78140250125871856cdc5b42ddc6a5fcd3cd4`.
Its [corrector implementation](https://github.com/rime/librime/blob/33e78140250125871856cdc5b42ddc6a5fcd3cd4/src/rime/dict/corrector.cc) uses `NearSearchCorrector` with QWERTY
adjacency, including a horizontal number row, not this keyboard's 3x3 geometry.
The component's edit-distance selection is disabled with `#if 0`; the restricted
distance helper also uses QWERTY substitution costs. Enabling that component is
not a valid numeric-key correction implementation.

Instead, `T9Assist` reuses librime's `Projection` and the active dictionary's
canonical syllable table. Syllables must pass the controller's exact atomic-prism
check. Each enabled fuzzy rule independently projects letters using
`trime/t9_fuzzy/<rule>`, then applies the active schema's `speller/algebra` to make
numeric keys. Rules are never chained with another fuzzy rule or typo search.
Unchanged canonical spellings are removed from each fuzzy script; they are
already covered by the exact index and cannot add a new suggestion. The canonical
and fuzzy scripts then share one numeric projection. Private build-time identities
retain each origin's spelling properties and rule without chaining rules or
changing the persistent schema. They never enter Rime's query, user DB or UI.
Only normal, non-correction numeric spellings enter the index.

Typo lookup enumerates one bounded numeric edit against that exact index, not
letter combinations and not Hanzi candidate comments. Matching `(raw end,
canonical spelling)` pairs are deduplicated. `T9Span.sources` is a structured bit
mask carried through JNI, Kotlin, the UI and undo history:

| Bit | Rule |
|---:|---|
| 0 | n/l |
| 1 | z/zh |
| 2 | c/ch |
| 3 | s/sh |
| 4 | en/eng |
| 5 | in/ing |
| 6 | adjacent numeric key |
| 7 | missing digit |
| 8 | repeated digit |

Multiple independently matching sources can share one suggestion. Source 0 means
the pre-existing exact/completion path. It does not mean an arbitrary unknown
correction source. Candidate comments are not a protocol.

Bounds: at most seven raw digits from the focused segment (six-letter syllable
plus one repetition), 4,096 canonical syllables, 8,192 references per index and
32 additional visible choices. Each query performs at most 455 index lookups;
it stops at separators, non-code input and the next independently locked segment.
Index construction is lazy and is discarded on schema/resource reset or option
changes. Clearing or cancelling composition only clears editing state and reuses
the same index; the native probe verifies reuse and schema invalidation separately.
Unrecognized custom schemas fail closed for unavailable fuzzy rules. Custom
copies need the new `trime/t9_fuzzy` section and redeployment, not replacement of
the user's whole configuration or deletion of their dictionaries.

## Verification

`fixtures/` is an authored, tiny dictionary with both directions of every fuzzy
pair. Completion is disabled in this fixture to isolate fuzzy/typo behavior;
the actual APK benchmark retains the normal Luna completion behavior.

`script/t9/assist_test.cc` checks 12 fuzzy and 12 typo cases, exact-menu invariance,
source tags, limits, individual disabling, stale actions, raw input/undo, explicit
commit and canonical user learning across process restart. Run it against the
**library extracted from the APK under test**, not an older engine:

```sh
python3 -B script/t9/run.py --cxx app/.cxx/Debug/4d506gs4/arm64-v8a \
  --library /absolute/frozen/lib/arm64-v8a/librime_jni.so \
  --adb /absolute/android-sdk/platform-tools/adb --serial emulator-5560 \
  --source assist_test.cc
```

Also run `--source controller_test.cc` for existing editing/full/double-pinyin
regressions. Every native run creates its own UUID directory and fresh user DB.

For UI and full-Luna measurements, install matching app/test APKs on an API 29+
**read-only, no-snapshot emulator**, deploy, and select Trime. Never clear real app
data or reset personal dictionaries. Do not run Gradle and the emulator together
on a memory-constrained host.

```sh
python3 -B script/quality/run_t03.py --probe t05 --case 360 \
  --adb /absolute/android-sdk/platform-tools/adb --serial emulator-5560 \
  --output build/t05/ui-360
```

Other display cases can use `--geometry-only` to skip repeated measurements.
Both built-in themes still check a real suggestion click, source-label bounds,
stable main-key positions, undo and exactly-once Hanzi commit. Preferences and
display overrides are restored. The instrumented editor is temporary.

The 24-item exact corpus compares all first-80 `CandidateProto` values, not just
Top-1. The 24 mistake cases count recovery only when choosing the intended whole
syllable returns a representative Hanzi and undo restores the raw code. Metrics
are saved as `metrics.json`, with sampled whole-app PSS and final-key-plus-menu
latency. This is explicit-assistance recovery, **not automatic Hanzi Top-1**.
Completion may already recover some mistakes with every new rule disabled.

The first-key metric includes lazy index construction when needed; clearing
composition must reuse the same schema index for subsequent corpus items.
The summary includes the first input's first key, the maximum and the subsequent
23 inputs' median so initialization cost cannot be hidden inside a warm median;
the final-key metric includes reading the first 80 candidates. Neither measures
screen presentation. T05's batch deadline is 900 seconds (outer interaction
deadline 1,200 seconds); this is a test-suite timeout, not an accepted typing
latency. T02/T03 keep their original deadlines. Progress is emitted for each
corpus item so an interrupted run cannot be mistaken for a completed comparison.

Use `python3 -B script/quality/t05/summarize.py PATH/metrics.json --output NEW.json`
to reproduce the summary; it rejects incomplete pairs, changed exact candidate
lists, source leakage and unbounded suggestions. `test_t05.py` tests those checks.

Results and limitations belong in [VALIDATION-T05.md](../VALIDATION-T05.md).

## Startup And Index Regression

`RimeInitializationTest` ensures loading the Kotlin API class does not load the
Android native engine on its caller thread. Native loading happens on `rime-main`.
`SetupResponsivenessProbe` (`am instrument -w -r -e setup true` with the usual
runner) measures the main looper through wizard launch, 300 coalesced refresh
requests, actual navigation and pause. It does not change storage modes or clear
data. Require both `passed=true` and `INSTRUMENTATION_CODE: -1`.

`run_startup.py --adb PATH --serial emulator-5560 --output NEWDIR --runs 5`
records the installed APK checksum and repeats that wizard probe in separate
processes. It refuses non-emulators. The runner's `-e shutdown true` probe checks
that losing the last client during STARTING eventually stops the engine, while a
new client cancels the pending shutdown. It does not clear app data. Native logs
retain warnings/errors but no longer print each preset dictionary entry or query.

The native assist tests compare the batched projection against the previous
separate-rule reference, and check rebuild failure and disabled-index cleanup.
For a build-only benchmark, use `script/t9/run.py --source assist_benchmark.cc
--benchmark-shared PATH ...` with a directory containing isolated system resources
and matching deployed `build/*.bin` / YAML files. Run the same driver against both
frozen APK libraries in alternating order. The runner creates a fresh UUID user
directory each time; it must not point at a personal user database. Five index
build timings exclude dictionary loading, Rime menu work and rendering. They must
not be described as end-to-end keyboard latency.

Follow-up findings and measured results: [T05 risk report](../VALIDATION-T05-RISK.md).
