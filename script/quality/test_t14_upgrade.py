# SPDX-License-Identifier: GPL-3.0-or-later
"""Synthetic gate inputs only; these tests do not install or verify Android APKs."""

import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

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


if __name__ == "__main__":
    unittest.main()
