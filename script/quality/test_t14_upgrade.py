# SPDX-License-Identifier: GPL-3.0-or-later
"""Synthetic gate inputs only; these tests do not install or verify Android APKs."""

import base64
import copy
import hashlib
import json
from pathlib import Path
import re
import tempfile
from types import SimpleNamespace
import unittest
import zlib

import t14_upgrade as gate


class UpgradeGateTest(unittest.TestCase):
    def window_fixture(self, title=gate.EDITOR, user=0, ending="\n"):
        # Full dumpsys window includes this current-focus record on API21/35;
        # the narrower API35 'window windows' listing can omit it.
        return ("WINDOW MANAGER DISPLAY CONTENTS" + ending +
                f"  mCurrentFocus=Window{{7888ec1 u{user} {title}}}" + ending).encode()

    def test_focused_editor_accepts_exact_current_window_lf_and_crlf(self):
        for ending in ("\n", "\r\n"):
            with self.subTest(ending=ending):
                self.assertEqual(dict(window="7888ec1", user=0, title=gate.EDITOR),
                                 gate.focused_editor_window(self.window_fixture(ending=ending)))

    def test_editor_listing_resumed_activity_or_ime_target_is_not_focus(self):
        for raw in (f"  Window #0 Window{{7888ec1 u0 {gate.EDITOR}}}:\n",
                    f"  mFocusedApp=ActivityRecord{{7888ec1 u0 {gate.EDITOR} t8}}\n",
                    f"  topResumedActivity=ActivityRecord{{7888ec1 u0 {gate.EDITOR} t8}}\n",
                    f"  imeInputTarget in display# 0 Window{{7888ec1 u0 {gate.EDITOR}}}\n",
                    f"  previous mCurrentFocus=Window{{7888ec1 u0 {gate.EDITOR}}}\n"):
            with self.subTest(raw=raw):
                self.assertIsNone(gate.focused_editor_window(raw.encode()))

    def test_null_duplicate_wrong_user_and_wrong_editor_cannot_pass(self):
        correct = self.window_fixture()
        for raw in (b"  mCurrentFocus=null\n", correct + correct,
                    correct + b"  mCurrentFocus=null\n", self.window_fixture(user=10),
                    self.window_fixture(title=gate.EDITOR + "Extra"),
                    self.window_fixture(title=gate.PACKAGE + "/.ui.main.OtherActivity"),
                    self.window_fixture(title="com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher")):
            with self.subTest(raw=raw):
                self.assertIsNone(gate.focused_editor_window(raw))

    def test_foreground_anr_and_application_error_fail_without_matching_history(self):
        for title in ("Application Not Responding: com.android.systemui", "Application Error: example.app"):
            with self.subTest(title=title), self.assertRaisesRegex(RuntimeError, "Foreground Android"):
                gate.focused_editor_window(self.window_fixture(title=title))
        raw = self.window_fixture() + b"  Last ANR: Application Not Responding: example.app\n"
        raw += b"  Window #2 Window{abcdef u0 Application Error: example.app}:\n"
        self.assertIsNotNone(gate.focused_editor_window(raw))

    def test_current_process_error_flags_include_later_fields_on_both_platforms(self):
        for raw in (b"  debugging=false crashing=false null notResponding=true AppNotRespondingDialog{1} bad=false\n",
                    b"  debugging=false crashing=true AppErrorDialog{1} notResponding=false null bad=false\n",
                    b"  mCrashing=false null mNotResponding=true [AppNotRespondingDialog{1}] bad=false\r\n",
                    b"  mCrashing=true [AppErrorDialog{1}] mNotResponding=false null bad=false\n"):
            with self.subTest(raw=raw), self.assertRaisesRegex(RuntimeError, "active crash/ANR"):
                gate.require_no_ui_error(self.window_fixture(), raw)

    def test_process_error_parser_ignores_false_flags_and_unstructured_history(self):
        raw = (b"  debugging=false crashing=false null notResponding=false null bad=false\n"
               b"  mCrashing=false null mNotResponding=false null bad=false\n"
               b"  Last ANR: mCrashing=false mNotResponding=true\n"
               b"  reason=debugging=false crashing=true\n"
               b"  mCrashing=false previous_mNotResponding=true notResponding=trueish\n")
        self.assertEqual(gate.EDITOR, gate.require_no_ui_error(self.window_fixture(), raw)["title"])

    def activation_fixture(self, api, theme=True):
        # Minimal synthetic versions of fields observed in both platform dumps.
        connection = "mHaveConnect" if api == 21 else "mHaveConnection"
        manager = (f"Current Input Method Manager state:\n  mCurMethodId={gate.IME}\n"
                   f"  mCurId={gate.IME} {connection}=true mBoundToMethod=true\n"
                   "Input method service state for com.osfans.trime.ime.core.TrimeInputMethodService@123abc:\n").encode()
        original = (b'<map><string name="selected_theme">trime</string></map>' if theme else b'<map/>')
        return gate.seeded_preferences(original), manager

    def test_activation_requires_persisted_theme_and_exact_stable_bytes(self):
        for api in (21, 35):
            raw, manager = self.activation_fixture(api)
            pending, _ = self.activation_fixture(api, theme=False)
            with self.subTest(api=api):
                self.assertIsNone(gate.activation_sample(pending, pending, manager, api))
                self.assertIsNone(gate.activation_sample(pending, raw, manager, api))
                sample = gate.activation_sample(raw, raw, manager, api)
                self.assertEqual(("string", "trime"), sample["values"]["selected_theme"])
                self.assertEqual(gate.IME, sample["binding"]["ime"])
                self.assertIsNone(gate.activation_sample(raw, raw + b"\n", manager, api))
                self.assertIsNone(gate.activation_sample(b"<map>", raw, manager, api))

    def test_ime_listing_wrong_or_duplicate_binding_cannot_signal_activation(self):
        for api in (21, 35):
            raw, manager = self.activation_fixture(api)
            wrong = manager.replace(("mCurId=" + gate.IME).encode(), b"mCurId=other/.Ime")
            for invalid in (wrong, manager.replace(b"mBoundToMethod=true", b"mBoundToMethod=false"),
                            manager.split(b"Input method service state")[0], manager + manager,
                            manager.replace(("mCurMethodId=" + gate.IME).encode(), b"mCurMethodId=other/.Ime")):
                with self.subTest(api=api, invalid=invalid):
                    self.assertIsNone(gate.activation_sample(raw, raw, invalid, api))
            # A persisted theme alone is not evidence of a currently bound IME.
            self.assertIsNone(gate.activation_sample(raw, raw, b"Input Methods: " + gate.IME.encode(), api))

    def test_activation_does_not_wait_away_changed_or_removed_preferences(self):
        raw, manager = self.activation_fixture(21)
        expected = gate.preference_values(raw)
        changed = raw.replace(b">trime<", b">tongwenfeng<")
        missing, _ = self.activation_fixture(21, theme=False)
        for value in (changed, missing, raw.replace(b"</map>", b'<string name="unexpected">added</string></map>')):
            with self.subTest(value=value), self.assertRaisesRegex(RuntimeError, "nonvolatile preference"):
                gate.activation_sample(value, value, manager, 21, expected)

    def test_only_pid_is_exempt_from_full_preference_preservation(self):
        raw, manager = self.activation_fixture(35)
        before = gate.preference_values(raw)
        with_pid = raw.replace(b"</map>", b'<int name="general__pid" value="123"/></map>')
        self.assertIsNotNone(gate.activation_sample(with_pid, with_pid, manager, 35, before))
        for key in before:
            changed = dict(before)
            changed.pop(key)
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                gate.require_preserved_preferences(before, changed)

    def test_install_success_accepts_actual_push_and_streamed_line_endings(self):
        for raw in (b"Performing Push Install\n\tpkg: /data/local/tmp/old.apk\r\nSuccess\r\n",
                    b"Performing Streamed Install\nSuccess\n"):
            with self.subTest(raw=raw):
                gate.require_install_success(raw)

    def test_install_success_rejects_missing_ambiguous_or_failed_results(self):
        for raw in (b"", b"NotSuccess\n", b"Success extra\n", b"Success\r\nSuccess\n",
                    b"Success\nFailure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\r\n"):
            with self.subTest(raw=raw), self.assertRaises(RuntimeError):
                gate.require_install_success(raw)

    def test_preference_seed_preserves_unrelated_values_and_types(self):
        original = b'<map><string name="empty"/><set name="choices"><string>b</string><string>a</string></set><long name="count" value="17"/><boolean name="candidate_show_comments" value="true"/></map>'
        before = gate.preference_values(original)
        after = gate.preference_values(gate.seeded_preferences(original))
        for key in ("empty", "choices", "count"):
            self.assertEqual(before[key], after[key])
        for key, value in gate.PREFERENCE_FIXTURE.items():
            self.assertEqual(value, after[key])

    def test_duplicate_preference_and_unsupported_type_are_rejected(self):
        for raw in (b'<map><int name="x" value="1"/><string name="x">different</string></map>',
                    b'<map><unknown name="x" value="1"/></map>'):
            with self.subTest(raw=raw), self.assertRaises(RuntimeError):
                gate.preference_values(raw)

    def registered_fixture(self, original=b"patch: {old_user_setting: true}\n"):
        prefix = "__t10_upgrade_" + "a" * 32
        ids = ["luna_pinyin", prefix + "_full", prefix + "_t9"]
        registered = b"synthetic configuration bytes from owned old-APK registration\n"
        state = dict(format_version=3, prefix=prefix, original_enabled_schema_ids=ids[:1],
                     registered_enabled_schema_ids=ids, original_default_custom_existed=original is not None,
                     original_default_custom=base64.b64encode(original).decode() if original is not None else None,
                     registered_default_custom=base64.b64encode(registered).decode(),
                     enabled_schema_registration=dict(expected_ids=ids, actual_ids=ids,
                         default_custom_sha256=hashlib.sha256(registered).hexdigest(),
                         ordinary_maintenance_readback=True, normal_startup_full_check=False))
        return state, registered

    def test_registration_retains_install_bytes_and_separate_exact_cleanup_baseline(self):
        original = b"# user comment\r\npatch: {old_user_setting: true}\r\n"
        state, registered = self.registered_fixture(original)
        self.assertEqual(original, gate.require_registered_fixture(state, registered))
        self.assertNotEqual(original, registered)
        with self.assertRaisesRegex(RuntimeError, "actual old-APK bytes"):
            gate.require_registered_fixture(state, registered + b"# a host must not append a marker\n")

    def test_absent_original_configuration_is_preserved_as_absent_not_empty(self):
        state, registered = self.registered_fixture(None)
        self.assertIsNone(gate.require_registered_fixture(state, registered))
        state["original_default_custom"] = ""
        with self.assertRaisesRegex(RuntimeError, "existence"):
            gate.require_registered_fixture(state, registered)

    def test_unregistered_missing_or_different_native_readback_is_rejected(self):
        state, registered = self.registered_fixture()
        for field, value in (("actual_ids", ["luna_pinyin"]), ("ordinary_maintenance_readback", False),
                             ("normal_startup_full_check", True), ("default_custom_sha256", "0" * 64)):
            changed = copy.deepcopy(state)
            changed["enabled_schema_registration"][field] = value
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                gate.require_registered_fixture(changed, registered)
        state.pop("enabled_schema_registration")
        with self.assertRaises(RuntimeError):
            gate.require_registered_fixture(state, registered)

    def test_failed_old_state_or_corrupt_saved_configuration_cannot_be_accepted(self):
        state, registered = self.registered_fixture()
        for field, value in (("diagnostic_only", True), ("format_version", 2),
                             ("registered_default_custom", "not-base64!"),
                             ("original_default_custom", "not-base64!")):
            changed = copy.deepcopy(state)
            changed[field] = value
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                gate.require_registered_fixture(changed, registered)

    def artifact_case(self, mutate=lambda rows: None, old_kind="release", package=gate.PACKAGE):
        # The files are explicitly synthetic byte fixtures, never called real APKs.
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        rows = {}
        for index, kind in enumerate(("old", "new", "test")):
            path = root / (kind + ".synthetic")
            path.write_bytes(("not an Android APK: " + kind).encode())
            rows[kind] = dict(gate.bound(path), package=package + (".test" if kind == "test" else ""),
                              version_name="synthetic", version_code=10 + index,
                              debuggable=kind == "test", certificate_sha256="a" * 64,
                              min_sdk=21, abis=["x86_64"], instrumentation_target=package)
        mutate(rows)
        record = root / "record.json"
        record.write_text(json.dumps(dict(schema_version=1, task="T14", candidate_commit="b" * 40, apks=rows)))
        args = SimpleNamespace(package=package, artifact_record=record, artifact_record_sha256=gate.bound(record)["sha256"],
                               target_sha="b" * 40, api=21, old_kind=old_kind,
                               old_apk=Path(rows["old"]["path"]), new_apk=Path(rows["new"]["path"]),
                               test_apk=Path(rows["test"]["path"]))
        return args

    def test_monotonic_matching_record_is_accepted(self):
        record, inputs = gate.validate_artifacts(self.artifact_case())
        self.assertEqual("T14", record["task"])
        self.assertEqual(4, len(inputs))

    def test_explicit_same_package_accepts_upstream_and_fork_without_migration(self):
        for package in ("com.osfans.trime", "com.osfans.trime.debug"):
            with self.subTest(package=package):
                record, inputs = gate.validate_artifacts(self.artifact_case(package=package))
                self.assertEqual(package, record["apks"]["new"]["package"])
                self.assertEqual(package + ".test", record["apks"]["test"]["package"])
                self.assertEqual(4, len(inputs))

    def test_explicit_package_never_accepts_cross_package_or_wrong_test_target(self):
        for package in ("com.osfans.trime", "com.osfans.trime.debug"):
            other = "com.osfans.trime.debug" if package == "com.osfans.trime" else "com.osfans.trime"
            for mutation in (lambda rows: rows["new"].update(package=other),
                             lambda rows: rows["test"].update(instrumentation_target=other)):
                with self.subTest(package=package), self.assertRaises(RuntimeError):
                    gate.validate_artifacts(self.artifact_case(mutation, package=package))
            args = self.artifact_case(package=package)
            args.package = other
            with self.assertRaises(RuntimeError):
                gate.validate_artifacts(args)

    def test_package_components_reject_unsafe_or_ambiguous_identity(self):
        for value in ("", "trime", "com..trime", "com/trime", "com.trime;bad", None):
            with self.subTest(value=value), self.assertRaises(RuntimeError):
                gate.package_components(value)

    def test_nondefault_package_binds_exact_editor_and_ime(self):
        components = gate.package_components("com.osfans.trime.debug")
        raw = self.window_fixture(title=components["editor"])
        self.assertEqual(components["editor"], gate.focused_editor_window(raw, editor=components["editor"])["title"])
        self.assertIsNone(gate.focused_editor_window(raw))
        for api in (21, 35):
            preferences, manager = self.activation_fixture(api)
            manager = manager.replace(gate.IME.encode(), components["ime"].encode())
            self.assertIsNotNone(gate.activation_sample(preferences, preferences, manager, api, ime=components["ime"]))
            self.assertIsNone(gate.activation_sample(preferences, preferences, manager, api))

    def test_same_or_lower_version_is_rejected(self):
        for code in (9, 10):
            with self.subTest(code=code), self.assertRaises(RuntimeError):
                gate.validate_artifacts(self.artifact_case(lambda rows: rows["new"].update(version_code=code)))

    def test_changed_certificate_or_package_is_rejected(self):
        for field, value in (("certificate_sha256", "c" * 64), ("package", gate.PACKAGE + ".wrong")):
            with self.subTest(field=field), self.assertRaises(RuntimeError):
                gate.validate_artifacts(self.artifact_case(lambda rows: rows["new"].update({field: value})))

    def test_debug_new_release_and_wrong_old_kind_are_rejected(self):
        with self.assertRaises(RuntimeError):
            gate.validate_artifacts(self.artifact_case(lambda rows: rows["new"].update(debuggable=True)))
        with self.assertRaises(RuntimeError):
            gate.validate_artifacts(self.artifact_case(old_kind="debug"))

    def test_wrong_test_target_or_changed_bytes_are_rejected(self):
        with self.assertRaises(RuntimeError):
            gate.validate_artifacts(self.artifact_case(lambda rows: rows["test"].update(instrumentation_target="other")))
        args = self.artifact_case()
        args.new_apk.write_bytes(b"changed after inspection")
        with self.assertRaises(RuntimeError):
            gate.validate_artifacts(args)


class CacheBoundaryTest(unittest.TestCase):
    """Metadata protocol fixtures only; these bytes are not native dictionaries."""

    def fixture(self):
        prefix = "__t10_upgrade_" + "a" * 32
        before, after = {}, {}

        def prism(schema):
            # Only the verified fixed metadata/payload layout is under test.
            return (b"Rime::Prism/4.0".ljust(32, b"\0") + b"DICT" +
                    zlib.crc32(schema, 0xffffffff).to_bytes(4, "little") + b"synthetic-payload")

        for suffix in ("full", "t9"):
            name = "build/" + prefix + "_" + suffix
            raw = (b"__build_info:\n  rime_version: 1.17.0\n  timestamps:\n"
                   b"    default: 10\n    key_bindings: 11\nmenu:\n  page_size: 5")
            before[name + ".schema.yaml"] = raw
            before[name + ".prism.bin"] = prism(raw)
            changed = raw.replace(b"default: 10", b"default: 12") if suffix == "t9" else raw
            after[name + ".schema.yaml"] = changed
            after[name + ".prism.bin"] = prism(changed)
        tree = {path: hashlib.sha256(raw).hexdigest() for path, raw in before.items()}
        tree.update({prefix + ".userdb/" + name: "b" * 64 for name in
                     ("000019.ldb", "000021.ldb", "000022.log", "CURRENT", "LOCK", "LOG", "LOG.old", "MANIFEST-000020")})
        tree.update({prefix + name: "c" * 64 for name in (".dict.yaml", "_full.schema.yaml", "_t9.schema.yaml")})
        tree.update({"build/" + prefix + name: "d" * 64 for name in (".table.bin", ".reverse.bin")})
        state = dict(prefix=prefix, package=gate.PACKAGE, uid=10053, old_version_code=10,
                     old_version_name="synthetic-old", owned_tree=tree)
        return state, json.dumps(state).encode(), before, after

    def test_metadata_only_handoff_retains_all_thirteen_original_hashes(self):
        state, raw, before, after = self.fixture()
        boundary = gate.cache_boundary(state, raw, before, after)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), boundary["state_sha256"])
        expected = dict(state["owned_tree"])
        for path, row in boundary["cache_files"].items():
            expected[path] = row["after_sha256"]
        persistent = set(expected) - set(boundary["cache_files"])
        self.assertEqual(13, len(persistent))
        self.assertEqual(17, len(expected))
        for path in persistent:
            self.assertEqual(state["owned_tree"][path], expected[path])
            observed = dict(expected); observed[path] = "0" * 64
            self.assertNotEqual(expected, observed)
        gate.cache_boundary(state, raw, before, before)

    def test_variable_complete_leveldb_tables_preserve_the_original_map(self):
        state, _, before, after = self.fixture()
        for extra in (0, 1, 5):
            changed = copy.deepcopy(state)
            for index in range(extra):
                changed["owned_tree"][state["prefix"] + ".userdb/" + str(100 + index) + ".sst"] = "e" * 64
            raw = json.dumps(changed).encode()
            original = copy.deepcopy(changed)
            with self.subTest(files=17 + extra):
                self.assertIs(changed["owned_tree"], gate.require_owned_tree(changed["prefix"], changed["owned_tree"]))
                boundary = gate.cache_boundary(changed, raw, before, after)
                self.assertEqual(4, len(boundary["cache_files"]))
                self.assertEqual(original, changed)
                self.assertEqual(hashlib.sha256(raw).hexdigest(), boundary["state_sha256"])

    def test_missing_fixed_or_required_database_structure_rejected(self):
        state, _, _, _ = self.fixture()
        stem = state["prefix"] + ".userdb/"
        fixed = [path for path in state["owned_tree"] if not path.startswith(stem)]
        missing = [[path] for path in fixed + [stem + name for name in ("CURRENT", "LOCK", "LOG")]]
        missing += [[path for path in state["owned_tree"] if path.startswith(stem) and re.fullmatch(pattern, path[len(stem):])]
                    for pattern in (r"MANIFEST-[0-9]+", r"[0-9]+\.(?:ldb|sst)", r"[0-9]+\.log")]
        for paths in missing:
            tree = dict(state["owned_tree"])
            for path in paths:
                del tree[path]
            with self.subTest(missing=paths), self.assertRaises(RuntimeError):
                gate.require_owned_tree(state["prefix"], tree)

    def test_unowned_nested_unknown_files_and_invalid_hash_or_number_rejected(self):
        state, _, _, _ = self.fixture()
        prefix = state["prefix"]
        for invalid in (None, 1, prefix[:-1], prefix + "x", "../" + prefix, prefix.upper()):
            with self.subTest(prefix=invalid), self.assertRaisesRegex(RuntimeError, "Untrusted owned fixture prefix"):
                gate.require_owned_tree(invalid, state["owned_tree"])
        for path in (prefix + "x.userdb/000100.ldb", prefix + ".userdb/../000100.ldb",
                     prefix + ".userdb/nested/000100.ldb", "/" + prefix + ".userdb/000100.ldb",
                     prefix + ".userdb/unknown", prefix + ".userdb/18446744073709551616.ldb"):
            tree = dict(state["owned_tree"]); tree[path] = "e" * 64
            with self.subTest(path=path), self.assertRaises(RuntimeError):
                gate.require_owned_tree(prefix, tree)
        for digest in ("x" * 64, "A" * 64, "a" * 63, None):
            tree = dict(state["owned_tree"]); tree[prefix + ".userdb/CURRENT"] = digest
            with self.subTest(digest=digest), self.assertRaises(RuntimeError):
                gate.require_owned_tree(prefix, tree)
        tree = dict(state["owned_tree"])
        for name in ("18446744073709551615.sst", "000023.dbtmp", "MANIFEST-000024"):
            tree[prefix + ".userdb/" + name] = "e" * 64
        self.assertIs(tree, gate.require_owned_tree(prefix, tree))

    def test_complete_stopped_install_tree_rejects_extra_missing_or_changed_userdb(self):
        state, raw, before, after = self.fixture()
        boundary = gate.cache_boundary(state, raw, before, after)
        expected = dict(state["owned_tree"])
        expected.update({path: row["after_sha256"] for path, row in boundary["cache_files"].items()})
        gate.require_stopped_install_tree(state, boundary, expected)
        table = state["prefix"] + ".userdb/000019.ldb"
        for mode in ("extra", "missing", "changed"):
            observed = dict(expected)
            if mode == "extra":
                observed[state["prefix"] + ".userdb/000099.ldb"] = "e" * 64
            elif mode == "missing":
                del observed[table]
            else:
                observed[table] = "0" * 64
            with self.subTest(mode=mode), self.assertRaisesRegex(RuntimeError, "complete stopped install tree"):
                gate.require_stopped_install_tree(state, boundary, observed)

    def test_schema_semantic_and_nonnumeric_changes_rejected_with_correct_crc(self):
        state, raw, before, after = self.fixture()
        schema = "build/" + state["prefix"] + "_t9.schema.yaml"
        prism = schema.replace(".schema.yaml", ".prism.bin")
        for old, new in ((b"page_size: 5", b"page_size: 6"), (b"default: 12", b'default: "12"')):
            altered = dict(after)
            altered[schema] = altered[schema].replace(old, new)
            altered[prism] = (altered[prism][:36] +
                              zlib.crc32(altered[schema], 0xffffffff).to_bytes(4, "little") + altered[prism][40:])
            with self.subTest(new=new), self.assertRaises(RuntimeError):
                gate.cache_boundary(state, raw, before, altered)

    def test_prism_format_dictionary_payload_unpaired_or_wrong_crc_rejected(self):
        state, raw, before, after = self.fixture()
        prism = "build/" + state["prefix"] + "_t9.prism.bin"
        for template, offset in ((after, 0), (after, 32), (after, 40), (after, 36), (before, 36)):
            altered = dict(template)
            data = bytearray(altered[prism]); data[offset] ^= 1; altered[prism] = bytes(data)
            with self.subTest(offset=offset, unchanged_schema=template is before), self.assertRaises(RuntimeError):
                gate.cache_boundary(state, raw, before, altered)

    def test_missing_extra_or_rewritten_learning_origin_rejected(self):
        state, raw, before, after = self.fixture()
        for mode in ("missing", "extra", "before"):
            first, last = dict(before), dict(after)
            if mode == "missing":
                del last[next(iter(last))]
            elif mode == "extra":
                last["build/unowned.prism.bin"] = b"extra"
            else:
                path = "build/" + state["prefix"] + "_t9.prism.bin"
                changed_state = copy.deepcopy(state)
                changed_state["owned_tree"][path] = "0" * 64
                with self.assertRaisesRegex(RuntimeError, "immutable learning-origin"):
                    gate.cache_boundary(changed_state, json.dumps(changed_state).encode(), first, last)
                continue
            with self.subTest(mode=mode), self.assertRaises(RuntimeError):
                gate.cache_boundary(state, raw, first, last)


if __name__ == "__main__":
    unittest.main()
