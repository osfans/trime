# SPDX-License-Identifier: GPL-3.0-or-later
"""Synthetic parser/gate tests; these are never device acceptance evidence."""

import copy
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

from collect import check_device
from metrics import MEMORY_FIELDS, memory_convergence, parse_meminfo, percentile
from summarize import accuracy, accuracy_nonregression, main as summarize_main, read_trace, timings


def event(stage, stamp, request=1, editor=7, generation=9, **extra):
    return {"v": 1, "event": stage, "request": request, "editor": editor,
            "generation": generation, "t_ns": stamp, **extra}


def draw_events(request=1, offset=0):
    return [event("request", offset + 20_000_000, request=request, generation=8, feedback_ns=offset + 10_000_000, submitted_ns=offset + 20_000_000),
            event("rime_queued", offset + 24_000_000, request=request, generation=8),
            event("engine_start", offset + 30_000_000, request=request, generation=8),
            event("compute_end", offset + 60_000_000, request=request),
            event("candidate_draw", offset + 90_000_000, request=request)]


class TraceTest(unittest.TestCase):
    def test_stages_use_submission_and_matching_completed_snapshot(self):
        result = timings(draw_events())
        self.assertEqual(result["latencies"]["candidate_ms"]["p95_ms"], 70)
        self.assertEqual(result["latencies"]["feedback_to_candidate_ms"]["p95_ms"], 80)
        self.assertEqual(result["latencies"]["queue_ms"]["p95_ms"], 10)
        self.assertEqual(result["latencies"]["ime_queue_ms"]["p95_ms"], 4)
        self.assertEqual(result["latencies"]["rime_queue_ms"]["p95_ms"], 6)
        self.assertEqual(result["latencies"]["compute_ms"]["p95_ms"], 30)
        self.assertEqual(result["latencies"]["compute_to_candidate_ms"]["p95_ms"], 30)
        self.assertEqual(result["outcomes"]["unresolved"], 0)

    def test_superseded_input_remains_in_denominator(self):
        records = draw_events() + draw_events(2, 100_000_000)[:-1]
        records.append(event("stale", 180_000_000, request=2))
        result = timings(records)
        self.assertEqual(result["outcomes"]["submitted"], 2)
        self.assertEqual(result["outcomes"]["computed"], 2)
        self.assertEqual(result["outcomes"]["stale"], 1)
        self.assertEqual(result["draw_coverage"], 0.5)
        self.assertEqual(result["outcomes"]["unresolved"], 0)

    def test_lost_display_has_no_silent_success(self):
        result = timings(draw_events()[:-1])
        self.assertEqual(result["outcomes"]["unresolved"], 1)
        self.assertEqual(result["unresolved_request_ids"], [1])

    def test_old_snapshot_editor_and_repeated_stage_rejected(self):
        for mutate in (lambda records: records[-1].update(generation=8),
                       lambda records: records[-1].update(editor=6),
                       lambda records: records.append(copy.copy(records[-1])),
                       lambda records: records.append(event("stale", 80_000_000)),
                       lambda records: records[-2].update(t_ns=25_000_000)):
            records = draw_events()
            mutate(records)
            with self.assertRaises(ValueError):
                timings(records)

    def test_commit_and_cancelled_requests_are_accounted_for(self):
        records = draw_events()[:-1]
        records.append(event("editor_commit", 80_000_000))
        records.append(event("request", 100_000_000, request=2, feedback_ns=0, submitted_ns=100_000_000))
        records.append(event("cancelled", 110_000_000, request=2))
        result = timings(records)
        self.assertEqual(result["outcomes"]["committed"], 1)
        self.assertEqual(result["outcomes"]["cancelled"], 1)
        self.assertEqual(result["outcomes"]["unresolved"], 0)

    def test_rejecting_later_action_on_an_old_drawn_snapshot_is_not_a_stale_draw(self):
        records = draw_events() + [event("stale", 110_000_000)]
        result = timings(records)
        self.assertEqual(result["outcomes"]["drawn"], 1)
        self.assertEqual(result["outcomes"]["stale"], 1)

    def test_one_request_can_supersede_its_first_snapshot_and_draw_its_second(self):
        records = draw_events()[:-1] + [event("stale", 65_000_000, generation=9),
                                      event("compute_end", 70_000_000, generation=10),
                                      event("candidate_draw", 90_000_000, generation=10)]
        result = timings(records)
        self.assertEqual(result["outcomes"]["submitted"], 1)
        self.assertEqual(result["outcomes"]["stale"], 1)
        self.assertEqual(result["outcomes"]["drawn"], 1)
        self.assertEqual(result["outcomes"]["unresolved"], 0)
        self.assertEqual(result["latencies"]["compute_to_candidate_ms"]["p95_ms"], 20)

    def test_more_than_256_requests_do_not_turn_missing_measurements_into_cancellations(self):
        records = draw_events()[:-1]
        for request_id in range(2, 258):
            records.extend(draw_events(request_id, 100_000_000 * request_id))
        result = timings(records)
        self.assertEqual(result["outcomes"]["submitted"], 257)
        self.assertEqual(result["outcomes"]["computed"], 257)
        self.assertEqual(result["outcomes"]["drawn"], 256)
        self.assertEqual(result["outcomes"]["unresolved"], 1)
        self.assertEqual(result["unresolved_request_ids"], [1])
        self.assertEqual(result["outcomes"]["stale"], 0)
        self.assertEqual(result["outcomes"]["cancelled"], 0)

    def test_old_snapshot_stale_cannot_hide_a_newer_unresolved_snapshot(self):
        records = draw_events()[:-1] + [event("stale", 65_000_000, generation=9),
                                      event("compute_end", 70_000_000, generation=10)]
        result = timings(records)
        self.assertEqual(result["outcomes"]["unresolved"], 1)
        self.assertEqual(result["unresolved_request_ids"], [1])
        records.append(event("stale", 80_000_000, generation=10))
        self.assertEqual(timings(records)["outcomes"]["unresolved"], 0)

    def test_empty_candidate_refresh_is_a_clear_and_needs_the_matching_snapshot(self):
        records = draw_events()
        records[-1]["event"] = "candidate_clear"
        result = timings(records)
        self.assertEqual(result["outcomes"]["cleared"], 1)
        self.assertEqual(result["outcomes"]["drawn"], 0)
        self.assertEqual(result["outcomes"]["unresolved"], 0)
        self.assertEqual(result["latencies"]["candidate_ms"]["samples"], 0)
        self.assertEqual(result["latencies"]["candidate_clear_ms"]["p95_ms"], 70)
        self.assertEqual(result["display_update_coverage"], 1)
        self.assertEqual(result["draw_coverage"], 0)
        records[-1]["generation"] = 8
        with self.assertRaises(ValueError):
            timings(records)

    def test_clear_and_draw_cannot_both_finish_the_same_request(self):
        records = draw_events()[:-1] + [event("candidate_clear", 80_000_000)] + draw_events()[-1:]
        with self.assertRaises(ValueError):
            timings(records)

    def test_superseded_snapshot_cannot_clear_after_rejection(self):
        records = draw_events()[:-1] + [event("stale", 80_000_000), event("candidate_clear", 90_000_000)]
        with self.assertRaises(ValueError):
            timings(records)

    def test_newer_compute_rejects_old_display_in_same_request_without_stale_log(self):
        for stage in ("candidate_draw", "candidate_clear"):
            records = draw_events()[:-1] + [event("compute_end", 70_000_000, generation=10),
                                          event(stage, 90_000_000, generation=9)]
            with self.subTest(stage=stage), self.assertRaises(ValueError):
                timings(records)

    def test_newer_compute_rejects_old_display_across_requests_without_stale_log(self):
        # Deliberately group records by request, as independent logcat threads
        # can interleave emission: elapsed timestamps must determine freshness.
        newer = [event("request", 40_000_000, request=2, generation=9, feedback_ns=35_000_000, submitted_ns=40_000_000),
                 event("rime_queued", 45_000_000, request=2, generation=9),
                 event("engine_start", 65_000_000, request=2, generation=9),
                 event("compute_end", 70_000_000, request=2, generation=10),
                 event("candidate_draw", 100_000_000, request=2, generation=10)]
        for stage in ("candidate_draw", "candidate_clear"):
            old = draw_events()
            old[-1]["event"] = stage
            with self.subTest(stage=stage), self.assertRaises(ValueError):
                timings(old + newer)

    def test_display_before_a_newer_compute_is_valid_and_other_editors_are_independent(self):
        for stage in ("candidate_draw", "candidate_clear"):
            records = draw_events()
            records[-1]["event"] = stage
            records.append(event("compute_end", 110_000_000, generation=10))
            with self.subTest(stage=stage):
                self.assertEqual(timings(records)["outcomes"]["unresolved"], 0)
            other = draw_events(2)
            for record in other:
                record["editor"] = 8
                if record["event"] in ("compute_end", "candidate_draw"):
                    record["generation"] = 20
            self.assertEqual(timings(records + other)["outcomes"]["unresolved"], 0)

    def test_old_native_commit_is_valid_after_newer_compute_and_does_not_count_as_display(self):
        old = draw_events()[:-1] + [event("editor_commit", 90_000_000, generation=9)]
        newer = [event("request", 40_000_000, request=2, generation=9, feedback_ns=35_000_000, submitted_ns=40_000_000),
                 event("rime_queued", 45_000_000, request=2, generation=9),
                 event("engine_start", 65_000_000, request=2, generation=9),
                 event("compute_end", 70_000_000, request=2, generation=10),
                 event("candidate_draw", 100_000_000, request=2, generation=10)]
        result = timings(old + newer)
        self.assertEqual(result["outcomes"]["committed"], 1)
        self.assertEqual(result["outcomes"]["drawn"], 1)
        self.assertEqual(result["outcomes"]["unresolved"], 0)

    def test_direct_key_commit_needs_no_rime_stage_or_candidate_draw(self):
        records = [event("request", 20_000_000, generation=0, feedback_ns=10_000_000, submitted_ns=20_000_000),
                   event("editor_commit", 30_000_000, generation=0)]
        result = timings(records)
        self.assertEqual(result["physical_key_outcomes"]["submitted"], 1)
        self.assertEqual(result["physical_key_outcomes"]["committed"], 1)
        self.assertEqual(result["outcomes"]["computed"], 0)
        self.assertEqual(result["outcomes"]["unresolved"], 0)
        self.assertEqual(result["latencies"]["editor_commit_ms"]["p95_ms"], 10)
        self.assertEqual(result["latencies"]["feedback_to_editor_commit_ms"]["p95_ms"], 20)
        self.assertEqual(result["latencies"]["candidate_ms"]["samples"], 0)

    def test_native_commit_cannot_claim_a_snapshot_that_was_never_computed(self):
        records = [event("request", 20_000_000, generation=0, feedback_ns=10_000_000, submitted_ns=20_000_000),
                   event("editor_commit", 30_000_000, generation=9)]
        with self.assertRaises(ValueError):
            timings(records)

    def test_failed_job_can_have_a_valid_draw_but_is_not_a_cancelled_input(self):
        records = draw_events()[:-1] + [event("job_failed", 80_000_000)] + draw_events()[-1:]
        result = timings(records)
        self.assertEqual(result["outcomes"]["failed"], 1)
        self.assertEqual(result["failed_request_ids"], [1])
        self.assertEqual(result["outcomes"]["drawn"], 1)
        self.assertEqual(result["outcomes"]["cancelled"], 0)
        self.assertEqual(result["outcomes"]["unresolved"], 0)

    def test_unknown_feedback_is_excluded_from_feedback_latency(self):
        records = draw_events()
        records[0]["feedback_ns"] = 0
        self.assertEqual(timings(records)["latencies"]["feedback_to_candidate_ms"]["samples"], 0)
        self.assertEqual(timings(records)["latencies"]["candidate_ms"]["samples"], 0)
        self.assertEqual(timings(records)["latencies"]["control_candidate_ms"]["samples"], 1)
        self.assertEqual(timings(records)["control_outcomes"]["submitted"], 1)

    def test_old_logcat_backlog_and_other_pids_do_not_pollute_capture(self):
        text = "\n".join(f"10-09 00:00:01.000 {pid} 44 I TrimePerf: v=1 event=stale request=1 editor=7 generation=9 t_ns={stamp}"
                         for pid, stamp in ((12, 50), (13, 120), (12, 120), (12, 250)))
        self.assertEqual(len(read_trace(text, 12, 100, 200)), 1)
        self.assertEqual(timings(read_trace(text, 12, 100, 200))["pre_capture_requests"], 1)

    def test_unknown_schema_or_duplicate_field_is_not_ignored(self):
        for fields in ("v=2 event=stale request=1 editor=7 generation=9 t_ns=120",
                       "v=1 event=stale request=1 editor=7 generation=9 t_ns=120 t_ns=121"):
            with self.assertRaises(ValueError):
                read_trace("10-09 00:00:01.000 12 44 I TrimePerf: " + fields, 12, 100, 200)


class MemoryTest(unittest.TestCase):
    def test_pss_column_is_not_rss_or_private_dirty(self):
        text = """                   Pss  Private Private SwapPss Rss Heap
                  Total Dirty Clean
        Native Heap   4000   3000  500  0  11000 12000
        Dalvik Heap   2000   1500  200  0  10000 11000
       Dalvik Other    300    200  100  0   1000  1100
            Gfx dev     50     50    0  0    500
         EGL mtrack    200    200    0  0    200
          GL mtrack    400    400    0  0    400
              TOTAL   9000   8000 1000  0  25000
           Java Heap:  1700
           TOTAL PSS:  9000     TOTAL RSS: 25000
        """
        result = parse_meminfo(text)
        self.assertEqual(result["native_pss_kib"], 4000)
        self.assertEqual(result["java_pss_kib"], 2300)
        self.assertEqual(result["graphics_pss_kib"], 650)
        self.assertEqual(result["total_pss_kib"], 9000)

    def test_unavailable_gpu_is_unknown(self):
        result = parse_meminfo("Native Heap 100 90 0\nDalvik Heap 20 15 0\nTOTAL 200 180 0\n")
        self.assertIsNone(result["graphics_pss_kib"])

    def test_stable_residency_growing_cache_and_missing_samples(self):
        stable = {round_id: [{field: 10000 + round_id * 20 for field in MEMORY_FIELDS} for _ in range(3)] for round_id in range(1, 6)}
        self.assertTrue(memory_convergence(stable)["within_budget"])
        growing = {round_id: [{field: 10000 + round_id * 3000 for field in MEMORY_FIELDS} for _ in range(3)] for round_id in range(1, 6)}
        self.assertFalse(memory_convergence(growing)["within_budget"])
        stable[5][2]["graphics_pss_kib"] = None
        self.assertFalse(memory_convergence(stable)["within_budget"])
        with self.assertRaises(ValueError):
            memory_convergence({1: stable[1][:2]})

    def test_missing_required_native_table_is_not_zero(self):
        with self.assertRaises(ValueError):
            parse_meminfo("Java Heap: 100\nNative Heap: 200\nTOTAL PSS: 300\n")

    def test_nearest_rank_p95_and_invalid_numbers(self):
        self.assertEqual(percentile(list(range(1, 101))), 95)
        with self.assertRaises(ValueError):
            percentile([float("nan")])


class EvidenceTest(unittest.TestCase):
    def _cli_summary(self, mode):
        # Complete synthetic evidence exercises the CLI budget gate rather
        # than deriving an acceptance claim from parser-only assertions.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            captures = []
            for round_id in range(1, 4):
                path = root / f"round-{round_id}"
                path.mkdir()
                captures.append(path)
                offset = round_id * 1_000_000_000
                records = draw_events(round_id, offset)
                if mode == "job_failed" and round_id == 1:
                    records.insert(-1, event("job_failed", offset + 80_000_000, request=round_id))
                elif mode == "clear":
                    records = []
                    for number in range(1000):
                        records.extend(draw_events((round_id - 1) * 1000 + number + 1, offset))
                        records[-1]["event"] = "candidate_clear"
                manifest = {"format": 1, "status": "complete", "physical_device": True, "debuggable": False,
                            "installed_apk_sha256": "a" * 64, "release_apk_sha256": "a" * 64,
                            "serial": "synthetic-test-only", "properties": {}, "display_configuration": {},
                            "boot_id": "test", "pid": 12, "package": "synthetic.test", "protocol_sha256": "b" * 64,
                            "phase": "steady", "round": round_id, "samples": 3, "seconds": 180, "interval_seconds": 5,
                            "start_elapsed_ns": offset, "end_elapsed_ns": offset + 200_000_000}
                (path / "capture.json").write_text(json.dumps(manifest))
                (path / "trace.log").write_text("\n".join("10-09 00:00:01.000 12 44 I TrimePerf: " + " ".join(f"{key}={value}" for key, value in record.items()) for record in records) + "\n")
                (path / "memory.jsonl").write_text("\n".join(json.dumps({"pid": 12, "t_ns": offset + stamp, **{field: 10000 for field in MEMORY_FIELDS}}) for stamp in (10_000_000, 100_000_000, 180_000_000)) + "\n")
            evidence = root / "accuracy.json"
            evidence.write_text(json.dumps({"complete": True, "corpus_sha256": "c" * 64, "cases": [
                {"id": "test-only", "expected_sha256": "d" * 64, "committed_sha256": "d" * 64}]}))
            output = root / "summary.json"
            argv = ["summarize.py", *(str(path) for path in captures), "--output", str(output),
                    "--accuracy", str(evidence), "--minimum-draws", "1000" if mode == "clear" else "1", "--tail-rounds", "3"]
            with mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()):
                summarize_main()
            return json.loads(output.read_text())

    def test_failed_job_blocks_full_budget_gate_even_with_fast_valid_draws(self):
        result = self._cli_summary("job_failed")
        self.assertFalse(result["budget_checks_passed"])
        self.assertFalse(result["checks"]["input_jobs_succeeded"])
        self.assertTrue(all(value for key, value in result["checks"].items() if key != "input_jobs_succeeded"))
        self.assertEqual(result["outcomes"]["drawn"], 3)
        self.assertEqual(result["outcomes"]["failed"], 1)

    def test_thousands_of_fast_empty_refreshes_do_not_satisfy_candidate_display_budget(self):
        result = self._cli_summary("clear")
        self.assertFalse(result["budget_checks_passed"])
        self.assertFalse(result["checks"]["sufficient_candidate_draws"])
        self.assertFalse(result["checks"]["latency_budget"])
        self.assertTrue(all(value for key, value in result["checks"].items() if key not in {"sufficient_candidate_draws", "latency_budget"}))
        self.assertEqual(result["outcomes"]["cleared"], 3000)
        self.assertEqual(result["outcomes"]["drawn"], 0)
        self.assertEqual(result["latencies"]["candidate_ms"]["samples"], 0)

    def test_emulator_and_debug_builds_are_rejected(self):
        props = {"ro.kernel.qemu": "", "ro.hardware": "physical"}
        package = "versionCode=123\nflags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]"
        check_device("device-123", props, package)
        for serial, values, dump in (("emulator-5560", props, package),
                                      ("device-123", {**props, "ro.kernel.qemu": "1"}, package),
                                      ("device-123", props, package.replace("HAS_CODE", "DEBUGGABLE")),
                                      ("device-123", props, "unknown package")):
            with self.assertRaises(ValueError):
                check_device(serial, values, dump)

    def test_accuracy_regression_cannot_be_hidden_by_other_improvements(self):
        before = accuracy({"complete": True, "corpus_sha256": "a" * 64, "cases": [
            {"id": "correct", "expected_sha256": "b" * 64, "committed_sha256": "b" * 64},
            {"id": "wrong", "expected_sha256": "c" * 64, "committed_sha256": "d" * 64}]})
        after = copy.deepcopy(before)
        after["cases"][0]["committed_sha256"] = "d" * 64
        after["cases"][1]["committed_sha256"] = "c" * 64
        self.assertEqual(before["correct"], after["correct"])
        result = accuracy_nonregression(before, after)
        self.assertFalse(result["nonregression"])
        self.assertEqual(result["regressed_case_ids"], ["correct"])
        after["corpus_sha256"] = "f" * 64
        with self.assertRaises(ValueError):
            accuracy_nonregression(before, after)


if __name__ == "__main__":
    unittest.main()
