# SPDX-License-Identifier: GPL-3.0-or-later
import struct
import unittest
import uuid
from unittest.mock import Mock, patch
from pathlib import Path
from tempfile import TemporaryDirectory

from runtime_audit import audit_log, device_audit_lock, filter_pid_log, instrumentation_passed, instrumentation_pid, log_after_marker, record_logcat, validate_screenshots


class RuntimeAuditTest(unittest.TestCase):
    def test_device_lock_rejects_overlap_and_releases_after_failure(self):
        serial = str(uuid.uuid4())
        with self.assertRaisesRegex(ValueError, "probe failed"):
            with device_audit_lock(serial):
                with self.assertRaisesRegex(RuntimeError, "Another audit owns"):
                    with device_audit_lock(serial):
                        self.fail("Overlapping probes acquired the device")
                with device_audit_lock(serial + "-other"):
                    pass
                raise ValueError("probe failed")
        with device_audit_lock(serial):
            pass

    def test_pid_belongs_to_the_probe_not_a_later_ime_restart(self):
        output = "INSTRUMENTATION_STATUS: audit_pid=1234\nINSTRUMENTATION_RESULT: passed=true\n"
        self.assertEqual(1234, instrumentation_pid(output))

    def test_missing_duplicate_and_invalid_process_id_cannot_select_logs(self):
        for output in ("", "INSTRUMENTATION_STATUS: audit_pid=0\n", "audit_pid=12\n",
                       "INSTRUMENTATION_STATUS: audit_pid=12\n" * 2):
            with self.assertRaises(ValueError):
                instrumentation_pid(output)

    def test_missing_or_unparseable_logs_are_not_warning_free(self):
        for log in ("", "adb: device disconnected"):
            result = audit_log(log)
            self.assertFalse(result["capture_has_records"])
            self.assertFalse(result["warning_free"])

    def test_log_recorder_lives_through_probe_and_is_reaped(self):
        process = Mock()
        process.poll.return_value = None
        with TemporaryDirectory() as folder, patch("runtime_audit.subprocess.Popen", return_value=process) as start:
            with record_logcat(["adb", "-s", "emulator-5560"], Path(folder) / "full.log"):
                start.assert_called_once()
                process.terminate.assert_not_called()
            process.terminate.assert_called_once()
            process.wait.assert_called_once_with(timeout=10)

    def test_an_early_log_recorder_exit_cannot_pass_as_complete_evidence(self):
        process = Mock()
        process.poll.return_value = 0
        with TemporaryDirectory() as folder, patch("runtime_audit.subprocess.Popen", return_value=process):
            with self.assertRaisesRegex(RuntimeError, "evidence is incomplete"):
                with record_logcat(["adb"], Path(folder) / "full.log"):
                    pass

    def test_legacy_log_filter_preserves_every_level_and_requires_exact_pid(self):
        selected = "09-08 12:00:00.001 1234 5678 W Trime: warning\n"
        selected += "09-08 12:00:00.002 1234 5678 I Trime: detail\n"
        other = "09-08 12:00:00.003 91234 5678 E Other: PID 1234 in message\n"
        self.assertEqual(selected, filter_pid_log(other + selected, 1234))

    def test_capture_boundary_keeps_every_new_level_not_previous_faults(self):
        old = '09-08 12:00:00.001 1234 5678 E Trime: injected fault\n'
        boundary = '09-08 12:00:00.002 2000 2000 I TrimeRuntimeAudit: BEGIN-test\n'
        fresh = '09-08 12:00:00.003 1234 5678 I Trime: ready\n09-08 12:00:00.004 1234 5678 E Trime: real defect\n'
        self.assertEqual(fresh, log_after_marker(old + boundary + fresh, 'BEGIN-test'))
        self.assertEqual(1, len(audit_log(log_after_marker(old + boundary + fresh, 'BEGIN-test'))['warnings']))

    def test_capture_boundary_rejects_missing_duplicate_and_wrong_tag(self):
        boundary = '09-08 12:00:00.002 2000 2000 I TrimeRuntimeAudit: BEGIN-test\n'
        for log in ('', boundary * 2, boundary.replace('TrimeRuntimeAudit', 'Other'), boundary.replace('BEGIN-test', 'BEGIN-other')):
            with self.assertRaises(ValueError):
                log_after_marker(log, 'BEGIN-test')

    def test_legacy_shell_log_trailing_space_does_not_lose_boundary(self):
        boundary = '09-08 12:00:00.002 2000 2000 I TrimeRuntimeAudit: BEGIN-test \r\n'
        fresh = '09-08 12:00:00.003 1234 5678 E Trime: real defect\n'
        self.assertEqual(fresh, log_after_marker(boundary + fresh, 'BEGIN-test'))
        with self.assertRaises(ValueError):
            log_after_marker(boundary.replace('BEGIN-test ', 'BEGIN-test-more '), 'BEGIN-test')

    def test_boundary_without_new_records_is_not_warning_free(self):
        boundary = '09-08 12:00:00.002 2000 2000 I TrimeRuntimeAudit: BEGIN-test\n'
        self.assertFalse(audit_log(log_after_marker(boundary, 'BEGIN-test'))['warning_free'])

    def test_every_warning_is_retained_including_platform(self):
        log = ("09-08 12:00:00.001 1234 5678 W HWUI    : platform warning\n"
               "09-08 12:00:00.002 1234 5678 E unfamiliar: unknown error\n"
               "09-08 12:00:00.003 1234 5678 I Trime: ready\n")
        result = audit_log(log)
        self.assertFalse(result["warning_free"])
        self.assertEqual(2, len(result["warnings"]))
        self.assertEqual("unknown error", result["warnings"][1]["message"])

    def test_known_warning_is_a_defect_even_at_info_level(self):
        result = audit_log("09-08 12:00:00.001 1234 5678 I Trime: Unrecognized key 'x'")
        self.assertFalse(result["warning_free"])
        self.assertEqual(1, len(result["known_project_diagnostics"]))

    def test_new_application_warnings_do_not_need_a_known_message_pattern(self):
        log = ("09-08 12:00:00.001 1234 5678 W rime.trime: newly introduced warning\n"
               "09-08 12:00:00.002 1234 5678 E [worker] FutureComponent: unexpected failure\n"
               "09-08 12:00:00.003 1234 5678 W HWUI: retained platform warning\n")
        result = audit_log(log)
        self.assertEqual(2, len(result["known_project_diagnostics"]))
        self.assertEqual(3, len(result["warnings"]))
        self.assertFalse(result["warning_free"])

    def test_framework_tags_do_not_hide_incorrect_application_api_usage(self):
        for tag, message in (
            ("ans.trime.debug", "Invalid resource ID 0x00000000."),
            ("ParcelFileDescriptor", "Peer didn't provide a comm channel; unable to check for errors"),
            ("WindowOnBackDispatcher", "OnBackInvokedCallback is not enabled for the application."),
            ("IInputConnectionWrapper", "requestCursorAnchorInfo on inactive InputConnection"),
        ):
            result = audit_log(f"09-08 12:00:00.001 1234 5678 W {tag}: {message}\n")
            self.assertEqual(1, len(result["known_project_diagnostics"]))
            self.assertFalse(result["warning_free"])

    def test_activity_window_leak_is_a_project_defect_under_framework_tag(self):
        leak = ("09-10 00:02:53.500 9680 9680 E WindowManager: android.view.WindowLeaked: "
                "Activity com.osfans.trime.ui.main.MainActivity has leaked window "
                "DecorView@1234[MainActivity] that was originally added here\n")
        result = audit_log(leak)
        self.assertEqual(1, len(result["known_project_diagnostics"]))
        self.assertEqual(1, len(result["warnings"]))
        self.assertFalse(result["warning_free"])

    def test_protocol_rejects_contradictory_and_duplicate_success(self):
        ok = "INSTRUMENTATION_RESULT: passed=true\nINSTRUMENTATION_CODE: -1\n"
        self.assertTrue(instrumentation_passed(0, ok))
        for output in [ok + "FAIL: test\n", ok + ok, "prefix " + ok, ok + "INSTRUMENTATION_FAILED: disconnected"]:
            self.assertFalse(instrumentation_passed(0, output))
        self.assertFalse(instrumentation_passed(1, ok))

    def test_missing_identical_or_invalid_screenshots_fail(self):
        png = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 720, 1600) + bytes(9)
        with self.assertRaises(ValueError):
            validate_screenshots("t05", {})
        with self.assertRaises(ValueError):
            validate_screenshots("t05", {"t05-trime.png": png, "t05-tongwenfeng.png": png})
        with self.assertRaises(ValueError):
            validate_screenshots("t05", {"t05-trime.png": png, "t05-tongwenfeng.png": b"broken"})
        validate_screenshots("t05", {"t05-trime.png": png, "t05-tongwenfeng.png": png + b"different"})

    def test_layout_requires_all_hand_modes_for_both_real_themes(self):
        png = b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + struct.pack(">II", 720, 1600) + bytes(9)
        shots = {f"t03-{theme}-360x800-font1.0{suffix}.png": png + theme.encode()
                 for theme in ("trime", "tongwenfeng.trime")
                 for suffix in ("", "-left", "-right", "-height80")}
        validate_screenshots("t03", shots)
        shots.pop("t03-tongwenfeng.trime-360x800-font1.0-left.png")
        with self.assertRaises(ValueError):
            validate_screenshots("t03", shots)


if __name__ == "__main__":
    unittest.main()
