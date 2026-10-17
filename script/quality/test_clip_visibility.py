# SPDX-License-Identifier: GPL-3.0-or-later
"""Host regressions for accepting real UI results only with scoped raw logs."""
import json
from pathlib import Path
import tempfile
import unittest

from run_clip_visibility import finalize_log_review


PID = 9680
MARKER = "BEGIN-CLIP-TEST"
BOUNDARY = f"09-10 00:02:53.000  2000  2000 I TrimeRuntimeAudit: {MARKER}\n"
CLEAN = f"09-10 00:02:53.100  {PID}  {PID} D [main] TrimeInputMethodService: editor active\n"
LEAK = (f"09-10 00:02:53.500  {PID}  {PID} E WindowManager: android.view.WindowLeaked: "
        "Activity com.osfans.trime.debug/com.osfans.trime.ui.main.MainActivity has leaked window DecorView\n")


class ClipVisibilityLogGateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.out = Path(self.temporary.name)
        self.report = {"functional_passed": True, "restoration": {"passed": True}}

    def review(self, raw, pids=(PID,)):
        if raw is not None:
            (self.out / "logcat-full.txt").write_text(raw)
        finalize_log_review(self.report, self.out, set(pids), MARKER)
        if raw is not None:
            self.assertEqual((self.out / "logcat-full.txt").read_text(), raw)
        return self.report

    def test_missing_log_file_rejects_successful_ui(self):
        result = self.review(None)
        self.assertFalse(result["passed"])
        self.assertFalse(result["diagnostics_passed"])
        self.assertIn("FileNotFoundError", result["log_review_error"])

    def test_missing_marker_rejects_unscoped_log(self):
        result = self.review(CLEAN)
        self.assertFalse(result["passed"])
        self.assertIn("capture boundary", result["log_review_error"])

    def test_duplicate_marker_rejects_ambiguous_scope(self):
        result = self.review(BOUNDARY + CLEAN + BOUNDARY + CLEAN)
        self.assertFalse(result["passed"])
        self.assertIn("capture boundary", result["log_review_error"])

    def test_other_process_records_do_not_prove_app_capture(self):
        result = self.review(BOUNDARY + CLEAN.replace(str(PID), "1234"))
        self.assertFalse(result["passed"])
        self.assertFalse(result["capture_has_records"])
        self.assertIn("no application records", result["log_review_error"])

    def test_missing_app_pid_cannot_pass_with_full_log(self):
        result = self.review(BOUNDARY + CLEAN, pids=())
        self.assertFalse(result["passed"])
        self.assertFalse(result["capture_has_records"])

    def test_real_window_leak_rejects_ui_success_and_preserves_diagnostic(self):
        result = self.review(BOUNDARY + CLEAN + LEAK)
        self.assertTrue(result["functional_passed"])
        self.assertTrue(result["capture_has_records"])
        self.assertFalse(result["passed"])
        self.assertFalse(result["diagnostics_passed"])
        self.assertEqual(result["project_diagnostic_count"], 1)
        audit = json.loads((self.out / "runtime-audit.json").read_text())
        self.assertEqual(audit["known_project_diagnostics"], [LEAK.strip()])
        self.assertIn(LEAK.strip(), (self.out / "app-logcat.txt").read_text())

    def test_queue_diagnostic_rejects_successful_ui(self):
        warning = f"09-10 00:02:54.000 {PID} {PID} W [rime-main] RimeDispatcher: job has waited 2300 ms\n"
        result = self.review(BOUNDARY + CLEAN + warning)
        self.assertFalse(result["passed"])
        self.assertEqual(result["project_diagnostic_count"], 1)

    def test_platform_warning_is_retained_without_claiming_warning_free(self):
        warning = f"09-10 00:02:54.000 {PID} {PID} W HWUI: Unknown dataspace 0\n"
        result = self.review(BOUNDARY + CLEAN + warning)
        self.assertTrue(result["passed"])
        self.assertTrue(result["diagnostics_passed"])
        self.assertFalse(result["warning_free"])
        self.assertTrue(result["requires_log_review"])
        self.assertEqual(result["warning_count"], 1)

    def test_pre_marker_fault_is_retained_but_outside_current_scope(self):
        identity = '{"source_sha256":"frozen-driver-sha"}\n'
        (self.out / "identity.json").write_text(identity)
        result = self.review(LEAK + BOUNDARY + CLEAN)
        self.assertTrue(result["passed"])
        self.assertEqual(result["project_diagnostic_count"], 0)
        self.assertEqual((self.out / "logcat-after-marker.txt").read_text(), CLEAN)
        self.assertEqual((self.out / "identity.json").read_text(), identity)

    def test_clean_log_cannot_override_failed_restoration(self):
        self.report["restoration"]["passed"] = False
        result = self.review(BOUNDARY + CLEAN)
        self.assertTrue(result["diagnostics_passed"])
        self.assertFalse(result["passed"])


if __name__ == "__main__":
    unittest.main()
