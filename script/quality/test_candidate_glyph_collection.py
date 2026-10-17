# SPDX-License-Identifier: GPL-3.0-or-later
"""Offline peer review of actual host collection code; no Android process runs."""
import copy
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zlib

import run_runtime as runner

IDS = {"trime": "c07a4e98-b736-444c-8d19-6c8321729555", "tongwenfeng.trime": "8c759562-e5d4-420a-8cd1-66d080618eb8"}
PACKAGE = "com.osfans.trime.debug"
ADB = ["mock-adb", "-s", "emulator-review-only"]


def status(theme, identifier):
    return f"INSTRUMENTATION_STATUS: candidate_glyph_case={theme}\nINSTRUMENTATION_STATUS: candidate_glyph_run_id={identifier}\nINSTRUMENTATION_STATUS_CODE: 0\n"


def png(width=2, height=2, color=(32, 48, 64, 255)):
    """Small, complete RGBA PNG with normal IHDR/IDAT/IEND and valid CRCs."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    scanlines = (b"\x00" + bytes(color) * width) * height
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(scanlines)) + chunk(b"IEND", b"")


def fixture():
    remote = {}
    for theme, identifier in IDS.items():
        count = 1 if theme == "trime" else 2
        artifacts = {"screen.png": png(color=(32, 48, 64 + count, 255))}
        artifacts.update({f"label-{index}.png": png(color=(96, 112, 128 + index, 255)) for index in range(count)})
        report = {"run_id": identifier, "theme": theme, "fixture": "native T9 64",
                  "hardware_capture_before_software_draw": True, "passed": True,
                  "native_state": {"editor": 4, "generation": 8, "request": 0,
                                   "raw_input": "64", "raw_input_source": "t9Cached.input",
                                   "schema_id": "luna_pinyin_t9", "capture_start_ns": 100000,
                                   "capture_end_ns": 200000},
                  "labels": [{"text": "你" if index == 0 else "ni", "accessibility_passed": True,
                              "glyph_passed": True} for index in range(count)],
                  "attachments_sha256": {name: hashlib.sha256(data).hexdigest() for name, data in artifacts.items()}}
        remote[theme] = {"report": report, "artifacts": artifacts}
    return remote


class CandidateGlyphCollectionTest(unittest.TestCase):
    def setUp(self):
        self.remote = fixture()
        self.output = "INSTRUMENTATION_STATUS: stream=unrelated\nINSTRUMENTATION_STATUS_CODE: 0\n" + "".join(status(theme, identifier) for theme, identifier in IDS.items())
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.destination = Path(self.temporary.name) / "candidate-glyph"
        self.calls = []

    def collect(self):
        def pull(command, timeout):
            self.assertEqual(command[:-1], [*ADB, "exec-out", "run-as", PACKAGE, "cat"])
            self.assertEqual(timeout, 60)
            prefix = "files/runtime-audit/candidate-glyph/"
            self.assertTrue(command[-1].startswith(prefix))
            theme, name = command[-1][len(prefix):].split("/", 1)
            self.calls.append((theme, name))
            if name == "report.json":
                return json.dumps(self.remote[theme]["report"]).encode()
            return self.remote[theme]["artifacts"][name]
        with patch.object(runner.subprocess, "check_output", side_effect=pull):
            result = runner.collect_candidate_glyphs(ADB, PACKAGE, self.output, self.destination)
        persisted = json.loads((self.destination / "collection.json").read_text())
        self.assertEqual(persisted, result)
        return result

    def test_positive_both_themes_one_and_two_native_labels(self):
        result = self.collect()
        self.assertTrue(result["collected"])
        self.assertTrue(result["passed"])
        self.assertEqual(set(result["cases"]), set(IDS))
        self.assertEqual(len(self.calls), 7)
        for theme in IDS:
            manifest_path = self.destination / theme / "report.json"
            self.assertEqual(result["cases"][theme]["report_sha256"], hashlib.sha256(manifest_path.read_bytes()).hexdigest())

    def test_wrong_nonce_is_rejected_and_original_report_retained(self):
        self.remote["trime"]["report"]["run_id"] = IDS["tongwenfeng.trime"]
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertIn("different probe run", result["error"])
        self.assertTrue((self.destination / "trime/report.json").is_file())

    def test_wrong_theme_is_rejected(self):
        self.remote["trime"]["report"]["theme"] = "tongwenfeng.trime"
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertIn("different probe run", result["error"])

    def test_nonce_and_case_from_different_status_bundles_cannot_pair(self):
        self.output = "INSTRUMENTATION_STATUS: candidate_glyph_case=trime\nINSTRUMENTATION_STATUS_CODE: 0\nINSTRUMENTATION_STATUS: candidate_glyph_run_id=" + IDS["trime"] + "\nINSTRUMENTATION_STATUS_CODE: 0\n"
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertEqual(self.calls, [])

    def test_attachment_hash_mismatch_preserves_mismatched_bytes(self):
        self.remote["trime"]["artifacts"]["screen.png"] = b"actual mismatching bytes"
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertIn("hash mismatch", result["error"])
        self.assertEqual((self.destination / "trime/screen.png").read_bytes(), b"actual mismatching bytes")

    def test_missing_one_theme_cannot_pass(self):
        self.output = status("trime", IDS["trime"])
        result = self.collect()
        self.assertFalse(result["collected"])
        self.assertFalse(result["passed"])
        self.assertTrue(result["cases"]["trime"]["passed"])

    def test_failed_report_is_collected_with_all_attachments_but_not_passed(self):
        self.remote["trime"]["report"]["passed"] = False
        self.remote["trime"]["report"]["labels"][0]["glyph_passed"] = False
        result = self.collect()
        self.assertTrue(result["collected"])
        self.assertFalse(result["passed"])
        self.assertEqual(len(self.calls), 7)
        self.assertTrue((self.destination / "trime/screen.png").is_file())
        self.assertTrue((self.destination / "tongwenfeng.trime/label-1.png").is_file())

    def test_duplicate_theme_status_rejects_ambiguous_runs(self):
        self.output += status("trime", IDS["trime"])
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertEqual(self.calls, [])

    def test_malformed_uuid_rejects_before_any_pull(self):
        self.output = self.output.replace(IDS["trime"], "-" * 36)
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertEqual(self.calls, [])

    def test_unexpected_attachment_path_is_not_pulled(self):
        self.remote["trime"]["report"]["attachments_sha256"]["../private"] = "0" * 64
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertEqual(self.calls, [("trime", "report.json")])

    def test_missing_screen_cannot_pass_even_when_flags_claim_success(self):
        self.remote["trime"]["report"]["attachments_sha256"].pop("screen.png")
        result = self.collect()
        self.assertTrue(result["collected"])
        self.assertFalse(result["passed"])

    def invalid_png(self, data, name="screen.png"):
        self.remote["trime"]["artifacts"][name] = data
        # Matching hash and positive probe flags must not make malformed bytes pass.
        self.remote["trime"]["report"]["attachments_sha256"][name] = hashlib.sha256(data).hexdigest()
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertIn("invalid PNG header", result["error"])
        self.assertEqual((self.destination / "trime" / name).read_bytes(), data)
        self.assertTrue((self.destination / "trime/report.json").is_file())

    def test_empty_screen_with_matching_hash_and_positive_flags_fails(self):
        self.invalid_png(b"")

    def test_truncated_png_with_matching_hash_fails(self):
        data = png()
        for size in (1, 8, 16, 24, 32):
            with self.subTest(size=size):
                self.destination = Path(self.temporary.name) / f"truncated-{size}"
                self.invalid_png(data[:size])

    def test_wrong_png_magic_with_matching_hash_fails(self):
        data = png()
        self.invalid_png(b"NOTPNG!!" + data[8:])

    def test_wrong_ihdr_type_or_length_with_matching_hash_fails(self):
        original = png()
        for index, value in enumerate((b"\x00\x00\x00\x0cIHDR", b"\x00\x00\x00\x0dTEXT")):
            with self.subTest(header=value):
                self.destination = Path(self.temporary.name) / f"bad-ihdr-{index}"
                self.invalid_png(original[:8] + value + original[16:])

    def test_zero_png_width_or_height_with_matching_hash_fails(self):
        for width, height in ((0, 2), (2, 0)):
            with self.subTest(width=width, height=height):
                self.destination = Path(self.temporary.name) / f"zero-{width}-{height}"
                self.invalid_png(png(width, height))

    def test_over_limit_png_dimensions_with_matching_hash_fail(self):
        for width, height in ((4097, 1), (1, 4097)):
            with self.subTest(width=width, height=height):
                self.destination = Path(self.temporary.name) / f"large-{width}-{height}"
                self.invalid_png(png(width, height))

    def test_over_limit_png_file_with_matching_hash_fails(self):
        header = png()
        self.invalid_png(header + b"\x00" * (24 * 1024 * 1024 + 1 - len(header)))

    def test_false_capture_order_fails(self):
        self.remote["trime"]["report"]["hardware_capture_before_software_draw"] = False
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertIn("capture order or fixture", result["error"])

    def test_wrong_native_fixture_fails(self):
        self.remote["trime"]["report"]["fixture"] = "native T9 644"
        result = self.collect()
        self.assertFalse(result["passed"])
        self.assertIn("capture order or fixture", result["error"])

    def native_cases(self, changes):
        original = copy.deepcopy(self.remote)
        for index, (key, value) in enumerate(changes):
            with self.subTest(key=key, value=value):
                self.remote = copy.deepcopy(original)
                self.calls = []
                self.destination = Path(self.temporary.name) / f"bad-native-{index}"
                native = self.remote["trime"]["report"]["native_state"]
                if key is None:
                    self.remote["trime"]["report"]["native_state"] = value
                else:
                    native[key] = value
                result = self.collect()
                self.assertFalse(result["passed"])
                self.assertIn("native state is missing or invalid", result["error"])
                self.assertTrue((self.destination / "trime/report.json").is_file())

    def test_native_state_must_be_an_object(self):
        self.native_cases([(None, None), (None, []), (None, "64"), (None, {})])

    def test_native_raw_schema_and_source_must_match_actual_t9_fixture(self):
        self.native_cases([("raw_input", "644"), ("raw_input", 64), ("raw_input_source", "guessed"),
                           ("schema_id", "luna_pinyin"), ("schema_id", "t9_pinyin")])

    def test_native_editor_and_generation_must_be_positive_integers(self):
        self.native_cases([("editor", 0), ("generation", -1), ("editor", True),
                           ("generation", 8.0), ("editor", "4")])

    def test_native_capture_times_must_be_positive_and_monotonic(self):
        self.native_cases([("capture_start_ns", 0), ("capture_end_ns", -1),
                           ("capture_start_ns", True), ("capture_end_ns", 200000.0),
                           ("capture_end_ns", 99999)])

    def test_native_request_must_be_nonnegative_integer_when_present(self):
        self.native_cases([("request", -1), ("request", True), ("request", 1.0), ("request", "0")])

    def test_malformed_labels_fail_with_collection_record_and_already_pulled_artifacts(self):
        original = copy.deepcopy(self.remote)
        for index, labels in enumerate((None, {}, "wrong", 7, [None], ["wrong"])):
            with self.subTest(labels=labels):
                self.remote = copy.deepcopy(original)
                self.remote["trime"]["report"]["labels"] = labels
                self.calls = []
                self.destination = Path(self.temporary.name) / f"bad-labels-{index}"
                result = self.collect()
                self.assertFalse(result["passed"])
                self.assertIn("Invalid candidate glyph label records", result["error"])
                self.assertTrue((self.destination / "trime/report.json").is_file())
                self.assertTrue((self.destination / "trime/screen.png").is_file())
                self.assertTrue((self.destination / "trime/label-0.png").is_file())

    def test_failed_partial_screen_keeps_screen_and_does_not_reuse_old_labels(self):
        manifest = self.remote["trime"]["report"]
        manifest["passed"] = False
        manifest["labels"] = []
        manifest["attachments_sha256"].pop("label-0.png")
        result = self.collect()
        self.assertTrue(result["collected"])
        self.assertFalse(result["passed"])
        self.assertTrue((self.destination / "trime/screen.png").is_file())
        self.assertFalse((self.destination / "trime/label-0.png").exists())
        self.assertTrue((self.destination / "tongwenfeng.trime/label-1.png").is_file())

    def test_negative_label_flags_reject_even_when_report_claims_pass(self):
        self.remote["trime"]["report"]["labels"][0]["accessibility_passed"] = False
        result = self.collect()
        self.assertTrue(result["collected"])
        self.assertFalse(result["passed"])

    def test_reused_host_destination_cannot_overwrite_first_failed_evidence(self):
        first = self.collect()
        self.assertTrue(first["passed"])
        old = (self.destination / "trime/report.json").read_bytes()
        second = self.collect()
        self.assertFalse(second["passed"])
        self.assertEqual((self.destination / "trime/report.json").read_bytes(), old)


if __name__ == "__main__":
    unittest.main(verbosity=2)
