#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise candidate artifact names and hashes without SDK/device commands."""

import argparse
from contextlib import redirect_stderr
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import run_api21_saf_gate as gate


COMMIT = "a" * 40


class SafCandidateArtifactsTest(unittest.TestCase):
    def fixture(self, root, app_name="upstream-next-x86_64-debug.apk", test_name="upstream-next-androidTest.apk"):
        root.mkdir()
        for name, contents in ((app_name, b"synthetic app artifact"), (test_name, b"synthetic test artifact")):
            (root / name).write_bytes(contents)
        identity = {
            "git": COMMIT,
            "dirty": False,
            "source_unchanged_during_build": True,
            "artifacts": {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in (app_name, test_name)},
        }
        (root / "identity.json").write_text(json.dumps(identity), encoding="utf-8")
        return identity

    def test_different_version_names_keep_recorded_paths_and_hashes(self):
        with tempfile.TemporaryDirectory() as temporary:
            for version in ("3.3.14", "future-preview.2"):
                with self.subTest(version=version):
                    root = Path(temporary) / version
                    app_name = f"trime-{version}-x86_64-debug.apk"
                    test_name = f"trime-{version}-androidTest.apk"
                    self.fixture(root, app_name, test_name)
                    result = gate.validate_candidate(root, COMMIT, app_name, test_name)
                    for name, package in ((app_name, gate.PACKAGE), (test_name, gate.TEST_PACKAGE)):
                        self.assertEqual(result["artifacts"][package], {
                            "name": name,
                            "path": str(root / name),
                            "sha256": hashlib.sha256((root / name).read_bytes()).hexdigest(),
                        })

    def test_non_leaf_and_duplicate_names_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "candidate"
            self.fixture(root)
            for name in ("../outside.apk", "/outside.apk", "sub/app.apk", r"sub\app.apk", r"C:\outside.apk", "C:outside.apk", "app.apks", "app.apk\n", "app\0.apk", None):
                with self.subTest(name=name):
                    with self.assertRaises(ValueError):
                        gate.validate_candidate(root, COMMIT, name, "upstream-next-androidTest.apk")
                    with self.assertRaises(ValueError):
                        gate.validate_candidate(root, COMMIT, "upstream-next-x86_64-debug.apk", name)
            with self.assertRaises(ValueError):
                gate.validate_candidate(root, COMMIT, "upstream-next-x86_64-debug.apk", "upstream-next-x86_64-debug.apk")

    def test_missing_artifact_recorded_hash_and_changed_bytes_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            for kind in ("missing", "recorded_hash", "changed_bytes"):
                with self.subTest(kind=kind):
                    root = Path(temporary) / kind
                    identity = self.fixture(root)
                    app = "upstream-next-x86_64-debug.apk"
                    if kind == "missing":
                        (root / app).unlink()
                    elif kind == "recorded_hash":
                        identity["artifacts"][app] = "0" * 64
                        (root / "identity.json").write_text(json.dumps(identity), encoding="utf-8")
                    else:
                        (root / app).write_bytes(b"changed after freezing")
                    with self.assertRaises((RuntimeError, FileNotFoundError)):
                        gate.validate_candidate(root, COMMIT, app, "upstream-next-androidTest.apk")

    def test_source_identity_guards_remain_required(self):
        with tempfile.TemporaryDirectory() as temporary:
            for key, value in (("git", "b" * 40), ("dirty", True), ("source_unchanged_during_build", False)):
                with self.subTest(key=key):
                    root = Path(temporary) / key
                    identity = self.fixture(root)
                    identity[key] = value
                    (root / "identity.json").write_text(json.dumps(identity), encoding="utf-8")
                    with self.assertRaises(RuntimeError):
                        gate.validate_candidate(root, COMMIT, "upstream-next-x86_64-debug.apk", "upstream-next-androidTest.apk")

    def test_runtime_uses_validated_artifact_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "candidate"
            self.fixture(root)
            candidate = gate.validate_candidate(root, COMMIT, "upstream-next-x86_64-debug.apk", "upstream-next-androidTest.apk")
            args = argparse.Namespace(adb=Path(temporary) / "not-executed-adb", serial="synthetic", stage="roundtrip")
            with patch.object(gate.run_runtime, "run") as runtime:
                gate.run_runtime_phase(args, Path(temporary) / "phase-not-created", "content://synthetic/tree", candidate)
            namespace = runtime.call_args.args[0]
            self.assertEqual(namespace.apk, Path(candidate["artifacts"][gate.PACKAGE]["path"]))
            self.assertEqual(namespace.test_apk, Path(candidate["artifacts"][gate.TEST_PACKAGE]["path"]))
            self.assertEqual(namespace.probe, "saf")
            self.assertFalse(namespace.saf_revoke)

    def test_cli_requires_explicit_apks_before_resolving_adb(self):
        for apk_args in ([], ["--app-apk-name", "app.apk"], ["--test-apk-name", "test.apk"]):
            with self.subTest(apk_args=apk_args), patch.object(gate.sys, "argv", ["gate", "--stage", "roundtrip", "--candidate", "candidate", "--commit", COMMIT, "--output", "output", *apk_args]), patch.object(gate, "resolve_adb") as resolve, redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as error:
                    gate.main()
                self.assertEqual(error.exception.code, 2)
                resolve.assert_not_called()
        with patch.object(gate.sys, "argv", ["gate", "--selftest", "--app-apk-name", "app.apk"]), patch.object(gate, "selftest") as selftest, redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                gate.main()
            self.assertEqual(error.exception.code, 2)
            selftest.assert_not_called()


if __name__ == "__main__":
    unittest.main()
