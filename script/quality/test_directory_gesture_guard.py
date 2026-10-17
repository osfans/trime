# SPDX-License-Identifier: GPL-3.0-or-later
"""Synthetic target-state fixtures; they do not replace device gesture evidence."""

import unittest

from directory_gesture_guard import PICKER, interaction_target_evidence


TOKEN = "6d7fd49"
MARKER = "T13_DIRECTORY_synthetic"
START = (
    "10-15 16:36:40.948  4282  4282 I T13DirectoryGuard: " + MARKER + "_START\n"
).encode()
END = (
    "10-15 16:36:53.016  4401  4401 I T13DirectoryGuard: " + MARKER + "_END\n"
).encode()


def target(token=TOKEN, extra="PointerEventDispatcher0 (server)", pid=608, tid=725):
    return (
        f"10-15 16:36:17.836  {pid}  {tid} I input_interaction: "
        f"Interaction with: {token} {PICKER} (server), {extra}, \n"
    ).encode()


class DirectoryTargetEvidenceTest(unittest.TestCase):
    def evidence(self, prefix, interval):
        return interaction_target_evidence(prefix, interval, MARKER, TOKEN)

    def test_actual_target_change_inside_markers(self):
        result = self.evidence(b"", START + target() + END)
        self.assertEqual("actual_target_change_inside_gesture", result["kind"])
        self.assertEqual(1, result["observed_target_changes"])

    def test_same_target_need_not_be_logged_again(self):
        result = self.evidence(target(), START + END)
        self.assertEqual("unchanged_last_actual_target_state", result["kind"])
        self.assertEqual(0, result["observed_target_changes"])
        self.assertEqual(target().decode().rstrip("\n"), result["selected_raw"])

    def test_missing_actual_target_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.evidence(b"", START + END)

    def test_latest_prior_target_overrides_older_matching_target(self):
        with self.assertRaises(AssertionError):
            self.evidence(target() + target("fffffff"), START + END)

    def test_target_change_before_start_is_not_ignored(self):
        with self.assertRaises(AssertionError):
            self.evidence(target(), target("fffffff") + START + END)

    def test_target_change_away_then_back_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.evidence(target(), START + target("fffffff") + target() + END)

    def test_unknown_additional_receiver_is_rejected(self):
        for prefix, interval in (
            (target(extra="PopupWindow:unexpected (server)"), START + END),
            (target(), START + target(extra="PopupWindow:unexpected (server)") + END),
        ):
            with self.subTest(prefix=prefix), self.assertRaises(AssertionError):
                self.evidence(prefix, interval)

    def test_missing_duplicate_or_reversed_markers_are_rejected(self):
        for interval in (START, END, START + START + END, START + END + END, END + START):
            with self.subTest(interval=interval), self.assertRaises(AssertionError):
                self.evidence(target(), interval)

    def test_unheaded_message_is_not_an_actual_target_record(self):
        with self.assertRaises(AssertionError):
            self.evidence(b"pretend " + target(), START + END)

    def test_dispatcher_change_inside_gesture_is_rejected(self):
        with self.assertRaises(AssertionError):
            self.evidence(target(), START + target() + target(pid=999, tid=888) + END)


if __name__ == "__main__":
    unittest.main()
