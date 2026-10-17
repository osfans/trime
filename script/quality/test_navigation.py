# SPDX-License-Identifier: GPL-3.0-or-later
import unittest
import xml.etree.ElementTree as ET

from run_navigation import capture_test_window, select_test_ime, validate_action_ids


class ActionIdentityTest(unittest.TestCase):
    def test_toolbar_actions_have_resolvable_resource_ids(self):
        root = ET.fromstring('<hierarchy><node content-desc="Deploy" resource-id="trime:id/action_deploy"/>'
                             '<node content-desc="Test input" resource-id="trime:id/action_test_input"/></hierarchy>')
        validate_action_ids(root, "trime")

    def test_anonymous_or_wrong_action_ids_are_rejected(self):
        for resource in ("", "0", "trime:id/action_deploy", "other:id/action_test_input"):
            root = ET.fromstring('<hierarchy><node content-desc="Deploy" resource-id="trime:id/action_deploy"/>'
                                 f'<node content-desc="Test input" resource-id="{resource}"/></hierarchy>')
            with self.assertRaisesRegex(AssertionError, "Unresolvable"):
                validate_action_ids(root, "trime")


class ImePreparationTest(unittest.TestCase):
    def run_selection(self, observations, attempts=10):
        values = iter(observations)
        commands = []

        def shell(*command):
            commands.append(command)
            if command[:2] == ("ime", "set"):
                return "selected"
            return next(values)

        result = select_test_ime(shell, "trime/ime", attempts, sleep=lambda _: None)
        return result, commands

    def test_requires_four_stable_observations(self):
        result, commands = self.run_selection(["trime/ime"] * 4)
        self.assertTrue(result["selected"])
        self.assertEqual(len(result["attempts"]), 1)
        self.assertEqual(len(commands), 5)

    def test_delayed_reset_reselects_and_retains_evidence(self):
        result, _ = self.run_selection(["trime/ime", "latin/ime"] + ["trime/ime"] * 4)
        self.assertTrue(result["selected"])
        self.assertEqual(len(result["attempts"]), 2)
        self.assertEqual(result["attempts"][0]["observed"], ["trime/ime", "latin/ime"])

    def test_permanent_failure_is_bounded(self):
        result, commands = self.run_selection(["latin/ime"] * 2, attempts=2)
        self.assertFalse(result["selected"])
        self.assertEqual(len(result["attempts"]), 2)
        self.assertEqual(len(commands), 4)


class HierarchyCaptureTest(unittest.TestCase):
    def test_null_root_never_reads_the_previous_file(self):
        calls = []
        outputs = iter(["ERROR: null root", "UI hierarchy dumped to: /owned.xml"])

        def shell(*command):
            calls.append(command)
            if command[0] == "uiautomator":
                return next(outputs)
            if command[0] == "cat":
                return '<hierarchy><node text="ready"/></hierarchy>'
            return ""

        root, _, history = capture_test_window(shell, "/owned.xml", sleep=lambda _: None)
        self.assertIsNotNone(root)
        self.assertEqual(len(history), 2)
        self.assertEqual(calls.count(("rm", "-f", "/owned.xml")), 2)
        self.assertEqual(calls.count(("cat", "/owned.xml")), 1)

    def test_waits_for_the_target_and_retains_intermediate_snapshot(self):
        snapshots = iter(['<hierarchy><node text="loading"/></hierarchy>', '<hierarchy><node text="ready"/></hierarchy>'])

        def shell(*command):
            if command[0] == "uiautomator":
                return "UI hierarchy dumped to: /owned.xml"
            return next(snapshots) if command[0] == "cat" else ""

        root, _, history = capture_test_window(shell, "/owned.xml", (("text", "ready"),), sleep=lambda _: None)
        self.assertIsNotNone(root)
        self.assertIn("loading", history[0]["xml"])

    def test_invalid_snapshot_fails_closed(self):
        def shell(*command):
            return "UI hierarchy dumped to: /owned.xml" if command[0] == "uiautomator" else ""

        root, xml, history = capture_test_window(shell, "/owned.xml", attempts=2, sleep=lambda _: None)
        self.assertIsNone(root)
        self.assertIsNone(xml)
        self.assertEqual(len(history), 2)
