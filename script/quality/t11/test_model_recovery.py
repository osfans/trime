#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real model publication recovery using temporary files only."""

import hashlib
import json
from pathlib import Path
import stat
import tempfile
import unittest
from unittest import mock

import build_predict_db as model


class PublicationFailure(OSError):
    pass


class ModelRecoveryTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="trime-model-recovery-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        assets = self.root / "assets"
        assets.mkdir()
        self.asset = assets / "model.db"
        self.manifest = assets / "model.db.json"
        self.original = (b"original model\0\n", b'{"generation":0}\n')
        self.updated = (b"updated model\0\n", b'{"generation":1}\n')
        self.modes = (0o640, 0o600)
        for path, image, mode in zip(self.targets, self.original, self.modes):
            path.write_bytes(image)
            path.chmod(mode)
        staged = assets / "stage"
        staged.mkdir()
        self.staged_model = staged / "model.db"
        self.staged_manifest = staged / "model.json"
        self.staged_model.write_bytes(self.updated[0])
        self.staged_manifest.write_bytes(self.updated[1])
        patch = mock.patch.multiple(model, ROOT=self.root, ASSET=self.asset,
                                    MANIFEST=self.manifest)
        patch.start()
        self.addCleanup(patch.stop)
        self.replace = model.os.replace
        self.verify_sources = mock.Mock(side_effect=self.check_prepublication)

    @property
    def targets(self):
        return self.asset, self.manifest

    def recovery_directory(self):
        directories = list((self.root / "build/t11").glob("model-recovery-*"))
        self.assertEqual(len(directories), 1)
        return directories[0]

    def check_backups(self):
        recovery = self.recovery_directory()
        before = json.loads((recovery / "before.json").read_text(encoding="utf-8"))
        self.assertEqual(before["recovery_directory"], str(recovery))
        self.assertFalse(before["pair_atomic"])
        self.assertTrue(before["backups_retained"])
        self.assertEqual(len(before["entries"]), 2)
        for entry, target, image, mode in zip(
                before["entries"], self.targets, self.original, self.modes):
            self.assertEqual(entry["target"], str(target))
            backup = Path(entry["backup"]["path"])
            self.assertEqual(backup.parent, recovery)
            self.assertEqual(backup.read_bytes(), image)
            self.assertEqual(entry["backup"], model.file_binding(backup))
            self.assertEqual(entry["bytes"], len(image))
            self.assertEqual(entry["sha256"], hashlib.sha256(image).hexdigest())
            self.assertEqual(entry["mode"], mode)
        return recovery

    def check_prepublication(self):
        # before.json and both retained backups must exist before the audit runs.
        self.check_backups()
        self.assertEqual(tuple(path.read_bytes() for path in self.targets), self.original)

    def publish(self):
        return model.publish_model_files(self.staged_model, self.staged_manifest,
                                         self.verify_sources)

    def check_outcome(self, state, images, attempted=None, primary=None):
        self.verify_sources.assert_called_once_with()
        recovery = self.check_backups()
        outcome = json.loads((recovery / "outcome.json").read_text(encoding="utf-8"))
        self.assertEqual(outcome["state"], state)
        self.assertEqual(outcome["recovery_directory"], str(recovery))
        self.assertFalse(outcome["pair_atomic"])
        self.assertTrue(outcome["backups_retained"])
        self.assertEqual(tuple(path.read_bytes() for path in self.targets), images)
        if attempted is not None:
            self.assertEqual(outcome["attempted_targets"], [str(path) for path in attempted])
        if primary is not None:
            self.assertEqual(outcome["primary_error"], repr(primary))
            self.assertTrue(any(str(recovery) in note for note in primary.__notes__))
        if state == "rolled-back":
            self.assertEqual(outcome["rollback_errors"], [])
            self.assertEqual(tuple(stat.S_IMODE(path.stat().st_mode)
                                   for path in self.targets), self.modes)
        return outcome

    def test_success_retains_original_pair_and_reports_commit(self):
        recovery = self.publish()
        self.assertEqual(recovery, self.recovery_directory())
        self.check_outcome("committed", self.updated)
        self.assertFalse(self.staged_model.exists())
        self.assertFalse(self.staged_manifest.exists())

    def test_second_replace_failure_restores_pair_and_original_exception(self):
        primary = PublicationFailure("second replacement failed")

        def replace(source, target):
            if Path(source) == self.staged_manifest:
                raise primary
            return self.replace(source, target)

        with mock.patch.object(model.os, "replace", side_effect=replace):
            with self.assertRaises(PublicationFailure) as caught:
                self.publish()
        self.assertIs(caught.exception, primary)
        self.check_outcome("rolled-back", self.original, self.targets, primary)

    def test_second_replace_keyboard_interrupt_restores_pair(self):
        primary = KeyboardInterrupt("interrupt during second replacement")

        def replace(source, target):
            if Path(source) == self.staged_manifest:
                raise primary
            return self.replace(source, target)

        with mock.patch.object(model.os, "replace", side_effect=replace):
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.publish()
        self.assertIs(caught.exception, primary)
        self.check_outcome("rolled-back", self.original, self.targets, primary)

    def test_rollback_failure_preserves_backups_and_primary_exception(self):
        primary = PublicationFailure("second replacement failed")
        rollback_error = PublicationFailure("model restoration failed")

        def replace(source, target):
            if Path(source) == self.staged_manifest:
                raise primary
            if Path(source).name.startswith("restore-") and Path(target) == self.asset:
                raise rollback_error
            return self.replace(source, target)

        with mock.patch.object(model.os, "replace", side_effect=replace):
            with self.assertRaises(PublicationFailure) as caught:
                self.publish()
        self.assertIs(caught.exception, primary)
        outcome = self.check_outcome("rollback-failed", (self.updated[0], self.original[1]),
                                     self.targets, primary)
        self.assertEqual(len(outcome["rollback_errors"]), 1)
        failure = outcome["rollback_errors"][0]
        self.assertEqual(failure["target"], str(self.asset))
        self.assertEqual(failure["error"], repr(rollback_error))
        self.assertEqual(Path(failure["backup"]["path"]).read_bytes(), self.original[0])
        self.assertTrue(any("rollback incomplete" in note for note in primary.__notes__))

    def test_source_verification_failure_never_replaces_targets(self):
        primary = ValueError("source binding changed")

        def verify_sources():
            self.check_prepublication()
            raise primary

        self.verify_sources.side_effect = verify_sources
        with mock.patch.object(model.os, "replace", wraps=self.replace) as replace:
            with self.assertRaises(ValueError) as caught:
                self.publish()
            replace.assert_not_called()
        self.assertIs(caught.exception, primary)
        self.check_outcome("not-published", self.original, (), primary)

    def test_first_replace_effect_before_interrupt_is_restored(self):
        primary = KeyboardInterrupt("first replacement completed before interrupt")

        def replace(source, target):
            result = self.replace(source, target)
            if Path(source) == self.staged_model:
                self.assertEqual(self.asset.read_bytes(), self.updated[0])
                raise primary
            return result

        with mock.patch.object(model.os, "replace", side_effect=replace):
            with self.assertRaises(KeyboardInterrupt) as caught:
                self.publish()
        self.assertIs(caught.exception, primary)
        self.check_outcome("rolled-back", self.original, (self.asset,), primary)


if __name__ == "__main__":
    unittest.main()
