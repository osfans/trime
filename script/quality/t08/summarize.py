#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Summarize release/physical T08 captures without hiding superseded input."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics

from metrics import memory_convergence, percentile


LOG_LINE = re.compile(r"^\s*\d{2}-\d{2}\s+[\d:.]+\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s?(.*)$")
EVENTS = {"request", "rime_queued", "engine_start", "compute_end", "candidate_draw", "candidate_clear", "editor_commit", "stale", "cancelled", "job_failed", "keyboard_cache"}


MAX_TRACE_RECORDS = 100000


def file_sha256(path):
    result = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def read_trace(text, pid, start_ns, end_ns):
    records = []
    for line in text.splitlines() if isinstance(text, str) else text:
        match = LOG_LINE.match(line)
        if not match or match[4].strip() != "TrimePerf" or int(match[1]) != pid:
            continue
        tokens = match[5].split()
        if any("=" not in token for token in tokens):
            raise ValueError("Malformed TrimePerf record")
        fields = dict(token.split("=", 1) for token in tokens)
        if len(fields) != len(tokens) or fields.get("v") != "1" or fields.get("event") not in EVENTS:
            raise ValueError("Unknown or duplicate TrimePerf fields/version/event")
        try:
            record = {key: value if key == "event" else int(value) for key, value in fields.items()}
        except ValueError as error:
            raise ValueError("TrimePerf fields must be content-free integers") from error
        if "t_ns" not in record or record["t_ns"] < 0:
            raise ValueError("Missing elapsedRealtime timestamp")
        if not start_ns <= record["t_ns"] <= end_ns:
            continue
        if record["event"] != "keyboard_cache":
            if any(record.get(key, -1) < 0 for key in ("request", "editor", "generation")):
                raise ValueError("Missing request/editor/generation association")
        records.append(record)
        if len(records) > MAX_TRACE_RECORDS:
            raise ValueError("Too many trace records for a bounded host analysis; use shorter captures")
    return records


def timings(records):
    # Logcat can interleave the Rime and main threads. Check display freshness
    # in elapsed-time order across requests, independently of stale logging.
    latest_computed = {}
    for record in sorted(records, key=lambda item: (item["t_ns"], item["event"] != "compute_end")):
        if record["event"] == "compute_end":
            latest_computed[record["editor"]] = max(latest_computed.get(record["editor"], 0), record["generation"])
        elif record["event"] in ("candidate_draw", "candidate_clear"):
            if latest_computed.get(record["editor"], 0) > record["generation"]:
                raise ValueError("Candidate display uses an older generation than the editor's completed state")
    requests = {}
    cache_records = []
    boundary_events = 0
    for record in records:
        if record["event"] == "keyboard_cache":
            cache_records.append(record)
            continue
        if record["request"] == 0:
            boundary_events += 1
            continue
        requests.setdefault(record["request"], []).append(record)
    values = {key: [] for key in ("candidate_ms", "control_candidate_ms", "feedback_to_candidate_ms", "candidate_clear_ms", "control_candidate_clear_ms", "feedback_to_candidate_clear_ms", "ime_queue_ms", "rime_queue_ms", "queue_ms", "compute_ms", "compute_to_candidate_ms", "compute_to_candidate_clear_ms", "editor_commit_ms", "feedback_to_editor_commit_ms")}
    outcomes = {"submitted": 0, "computed": 0, "drawn": 0, "cleared": 0, "committed": 0, "stale": 0, "cancelled": 0, "failed": 0, "unresolved": 0}
    physical_outcomes = {key: 0 for key in outcomes}
    control_outcomes = {key: 0 for key in outcomes}
    terminal_without_request = 0
    unresolved = []
    failed = []
    for request_id, events in sorted(requests.items()):
        submitted = [event for event in events if event["event"] == "request"]
        if not submitted:
            # Recording can begin while a previous request is already running.
            terminal_without_request += 1
            continue
        if len(submitted) != 1:
            raise ValueError("Duplicate request identity")
        request = submitted[0]
        if any(event["editor"] != request["editor"] for event in events):
            raise ValueError("Asynchronous result belongs to a different editor")
        if not 0 <= request.get("feedback_ns", -1) <= request.get("submitted_ns", -1) <= request["t_ns"]:
            raise ValueError("Invalid feedback/submission timestamps")
        if any(event["t_ns"] < request["submitted_ns"] for event in events):
            raise ValueError("Stage precedes request submission")
        by_stage = {name: [event for event in events if event["event"] == name] for name in EVENTS}
        for name in ("rime_queued", "engine_start", "candidate_draw", "candidate_clear"):
            if len(by_stage[name]) > 1:
                raise ValueError("Duplicate request stage: " + name)
        outcomes["submitted"] += 1
        cohort = physical_outcomes if request["feedback_ns"] > 0 else control_outcomes
        cohort["submitted"] += 1
        for stage, count in (("compute_end", "computed"), ("candidate_draw", "drawn"), ("candidate_clear", "cleared"), ("editor_commit", "committed"), ("stale", "stale"), ("cancelled", "cancelled"), ("job_failed", "failed")):
            outcomes[count] += bool(by_stage[stage])
            cohort[count] += bool(by_stage[stage])
        if by_stage["job_failed"]:
            failed.append(request_id)
        display = by_stage["candidate_draw"] + by_stage["candidate_clear"]
        if len(display) > 1:
            raise ValueError("A request has more than one display terminal")
        if display and (any(event["generation"] == display[0]["generation"] and event["t_ns"] <= display[0]["t_ns"] for event in by_stage["stale"])
                        or any(event["t_ns"] <= display[0]["t_ns"] for event in by_stage["cancelled"])):
            raise ValueError("A superseded/cancelled candidate reached the display stage")
        if by_stage["engine_start"]:
            engine = by_stage["engine_start"][0]
            if not by_stage["rime_queued"] or by_stage["rime_queued"][0]["t_ns"] > engine["t_ns"]:
                raise ValueError("Engine start has no earlier native-queue ingress")
            queued = by_stage["rime_queued"][0]
            values["ime_queue_ms"].append((queued["t_ns"] - request["submitted_ns"]) / 1_000_000)
            values["rime_queue_ms"].append((engine["t_ns"] - queued["t_ns"]) / 1_000_000)
            values["queue_ms"].append((engine["t_ns"] - request["submitted_ns"]) / 1_000_000)
        else:
            engine = None
        if by_stage["compute_end"]:
            if engine is None:
                raise ValueError("Computed input has no engine-start record")
            for computed in by_stage["compute_end"]:
                if computed["t_ns"] < engine["t_ns"]:
                    raise ValueError("Compute completion precedes engine start")
                values["compute_ms"].append((computed["t_ns"] - engine["t_ns"]) / 1_000_000)
        if display:
            shown = display[0]
            computed = [event for event in by_stage["compute_end"] if event["generation"] == shown["generation"]]
            if len(computed) != 1 or computed[0]["t_ns"] > shown["t_ns"]:
                raise ValueError("Candidate display has no matching completed snapshot")
            suffix = "candidate_ms" if shown["event"] == "candidate_draw" else "candidate_clear_ms"
            values[suffix if request["feedback_ns"] > 0 else "control_" + suffix].append((shown["t_ns"] - request["submitted_ns"]) / 1_000_000)
            values["compute_to_" + suffix].append((shown["t_ns"] - computed[0]["t_ns"]) / 1_000_000)
            if request["feedback_ns"] > 0:
                values["feedback_to_" + suffix].append((shown["t_ns"] - request["feedback_ns"]) / 1_000_000)
        for committed in by_stage["editor_commit"]:
            if committed["generation"] > 0 and not any(computed["generation"] == committed["generation"] and computed["t_ns"] <= committed["t_ns"] for computed in by_stage["compute_end"]):
                raise ValueError("Editor commit has no matching completed snapshot")
            values["editor_commit_ms"].append((committed["t_ns"] - request["submitted_ns"]) / 1_000_000)
            if request["feedback_ns"] > 0:
                values["feedback_to_editor_commit_ms"].append((committed["t_ns"] - request["feedback_ns"]) / 1_000_000)
        accounted = any(by_stage[stage] for stage in ("candidate_draw", "candidate_clear", "editor_commit", "cancelled", "job_failed"))
        if not accounted and by_stage["compute_end"]:
            latest = max(by_stage["compute_end"], key=lambda computed: computed["t_ns"])
            accounted = any(stale["generation"] == latest["generation"] and stale["t_ns"] >= latest["t_ns"] for stale in by_stage["stale"])
        elif not accounted:
            accounted = bool(by_stage["stale"])
        if not accounted:
            outcomes["unresolved"] += 1
            cohort["unresolved"] += 1
            unresolved.append(request_id)
    summaries = {name: {"samples": len(samples), "median_ms": statistics.median(samples), "p95_ms": percentile(samples), "max_ms": max(samples)}
                 if samples else {"samples": 0} for name, samples in values.items()}
    return {"latencies": summaries, "outcomes": outcomes,
            "physical_key_outcomes": physical_outcomes, "control_outcomes": control_outcomes,
            "draw_coverage": outcomes["drawn"] / outcomes["submitted"] if outcomes["submitted"] else 0,
            "display_update_coverage": (outcomes["drawn"] + outcomes["cleared"]) / outcomes["submitted"] if outcomes["submitted"] else 0,
            "unresolved_request_ids": unresolved, "pre_capture_requests": terminal_without_request,
            "failed_request_ids": failed,
            "control_events": boundary_events, "cache_records": cache_records}


def accuracy(data):
    digest = re.compile(r"[0-9a-f]{64}")
    if not digest.fullmatch(data.get("corpus_sha256", "")) or data.get("complete") is not True:
        raise ValueError("Accuracy evidence must identify the complete frozen corpus")
    cases = data.get("cases", [])
    if not cases or len({case["id"] for case in cases}) != len(cases):
        raise ValueError("Accuracy evidence needs unique corpus case IDs")
    for case in cases:
        if any(not digest.fullmatch(case.get(field, "")) for field in ("expected_sha256", "committed_sha256")):
            raise ValueError("Accuracy evidence requires expected and actual commit hashes")
    return {"corpus_sha256": data["corpus_sha256"], "cases": cases,
            "total": len(cases), "correct": sum(case["expected_sha256"] == case["committed_sha256"] for case in cases)}


def accuracy_nonregression(before, after):
    if before["corpus_sha256"] != after["corpus_sha256"]:
        raise ValueError("Baseline and candidate accuracy corpora differ")
    old = {case["id"]: case for case in before["cases"]}
    new = {case["id"]: case for case in after["cases"]}
    if old.keys() != new.keys() or any(old[key]["expected_sha256"] != new[key]["expected_sha256"] for key in old):
        raise ValueError("Accuracy case IDs or expected outputs differ")
    regressed = [key for key in old if old[key]["committed_sha256"] == old[key]["expected_sha256"] and new[key]["committed_sha256"] != new[key]["expected_sha256"]]
    return {"nonregression": not regressed and after["correct"] >= before["correct"], "regressed_case_ids": regressed}


def load_captures(paths):
    manifests, records, memory = [], [], {}
    for path in paths:
        manifest = json.loads((path / "capture.json").read_text())
        if manifest.get("format") != 1 or manifest.get("status") != "complete" or not manifest.get("physical_device") or manifest.get("debuggable") is not False:
            raise ValueError("Incomplete, nonphysical or debuggable capture: " + str(path))
        if manifest["installed_apk_sha256"] != manifest["release_apk_sha256"]:
            raise ValueError("Installed APK hash mismatch")
        if manifests:
            for field in ("serial", "properties", "display_configuration", "boot_id", "pid", "package", "release_apk_sha256", "protocol_sha256", "phase", "seconds", "interval_seconds"):
                if manifest[field] != manifests[0][field]:
                    raise ValueError("Captures differ in " + field)
            if manifest["start_elapsed_ns"] <= manifests[-1]["end_elapsed_ns"]:
                raise ValueError("Captures overlap or are not in chronological order")
        number = manifest["round"]
        if number in memory:
            raise ValueError("Duplicate workload round")
        if manifests and number <= manifests[-1]["round"]:
            raise ValueError("Workload rounds must increase in capture order")
        samples = [json.loads(line) for line in (path / "memory.jsonl").read_text().splitlines()]
        if len(samples) != manifest["samples"] or any(sample["pid"] != manifest["pid"] for sample in samples):
            raise ValueError("Memory sample count/PID does not match capture")
        if any(not manifest["start_elapsed_ns"] <= sample["t_ns"] <= manifest["end_elapsed_ns"] for sample in samples):
            raise ValueError("Memory sample outside capture interval")
        if any(a["t_ns"] >= b["t_ns"] for a, b in zip(samples, samples[1:])):
            raise ValueError("Memory samples are not strictly chronological")
        for sample in samples:
            for field in ("java_pss_kib", "native_pss_kib", "graphics_pss_kib", "total_pss_kib"):
                if sample.get(field) is not None and (not isinstance(sample[field], int) or sample[field] < 0):
                    raise ValueError("Invalid PSS measurement")
            if sample.get("total_pss_kib", 0) <= 0:
                raise ValueError("Missing/zero total PSS")
        memory[number] = samples
        with (path / "trace.log").open() as source:
            records.extend(read_trace(source, manifest["pid"], manifest["start_elapsed_ns"], manifest["end_elapsed_ns"]))
        if len(records) > MAX_TRACE_RECORDS:
            raise ValueError("Too many combined trace records; use a shorter frozen measurement protocol")
        manifests.append(manifest)
    return manifests, records, memory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("captures", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--accuracy", type=Path)
    parser.add_argument("--baseline", type=Path, help="Earlier T08 summary from the same physical device and frozen protocol")
    parser.add_argument("--minimum-draws", type=int, default=1000)
    parser.add_argument("--tail-rounds", type=int, default=5)
    parser.add_argument("--growth-kib", type=int, default=4096)
    parser.add_argument("--growth-percent", type=float, default=5)
    args = parser.parse_args()
    if args.minimum_draws < 1:
        parser.error("--minimum-draws must be positive")
    manifests, records, memory = load_captures(args.captures)
    first = manifests[0]
    result = {"format": 1, "phase": first["phase"], "identity": {key: first[key] for key in ("serial", "properties", "display_configuration", "package", "release_apk_sha256", "protocol_sha256")},
              **timings(records), "memory": memory_convergence(memory, args.tail_rounds, args.growth_kib, args.growth_percent),
              "accuracy": accuracy(json.loads(args.accuracy.read_text())) if args.accuracy else None,
              "minimum_draws": args.minimum_draws,
              "limitations": ["Draw submission measures the candidate View draw callback; physical display presentation requires a separate frame/presentation trace",
                              "Memory values are sampled PSS, not allocator peak; Java is Dalvik Heap+Dalvik Other, graphics is Gfx dev+EGL/GL mtrack",
                              "Absent GPU accounting is unknown, not zero", "The operator must perform the frozen repeated typing/theme protocol and verify real hardware",
                              "Trace-enabled release identity must be preserved; baseline and candidate require the same instrumentation", "First deployment is separate from steady-state acceptance"]}
    result["identity"]["sampling_configuration"] = {key: first[key] for key in ("seconds", "interval_seconds")}
    result["capture_sha256"] = [{"directory": str(path), **{name: file_sha256(path / name)
                                  for name in ("capture.json", "trace.log", "memory.jsonl")}} for path in args.captures]
    candidate = result["latencies"]["candidate_ms"]
    latency_pass = candidate.get("p95_ms", float("inf")) <= 100
    result["latency_budget"] = {"p95_target_ms": 100, "meets_target": latency_pass, "baseline_improvement_percent": None, "meets_30_percent_fallback": False}
    accuracy_pass = result["accuracy"] is not None and result["accuracy"]["correct"] == result["accuracy"]["total"]
    if args.baseline:
        baseline = json.loads(args.baseline.read_text())
        if baseline["phase"] != result["phase"]:
            raise ValueError("First-deployment and steady baselines cannot be combined")
        for field in ("serial", "properties", "display_configuration", "package", "protocol_sha256", "sampling_configuration"):
            if baseline["identity"][field] != result["identity"][field]:
                raise ValueError("Baseline differs in " + field)
        old_p95 = baseline["latencies"]["candidate_ms"].get("p95_ms")
        if old_p95 is None or old_p95 <= 0 or "p95_ms" not in candidate:
            raise ValueError("Baseline/candidate lacks valid candidate draw timings")
        improvement = (1 - candidate["p95_ms"] / old_p95) * 100
        baseline_complete = (baseline["latencies"]["candidate_ms"]["samples"] >= args.minimum_draws
                             and baseline["outcomes"]["unresolved"] == 0
                             and baseline["outcomes"].get("failed", 0) == 0
                             and baseline.get("minimum_draws") == args.minimum_draws)
        fallback = baseline_complete and old_p95 > 100 and improvement >= 30
        result["latency_budget"].update(baseline_improvement_percent=improvement, meets_30_percent_fallback=fallback)
        result["latency_budget"]["baseline_sufficient"] = baseline_complete
        latency_pass |= fallback
        if baseline.get("accuracy") and result["accuracy"]:
            result["accuracy_comparison"] = accuracy_nonregression(baseline["accuracy"], result["accuracy"])
            accuracy_pass = result["accuracy_comparison"]["nonregression"]
        else:
            accuracy_pass = False
    checks = {"steady_phase": result["phase"] == "steady", "sufficient_candidate_draws": candidate["samples"] >= args.minimum_draws,
              "all_submitted_requests_accounted_for": result["outcomes"]["unresolved"] == 0 and result["outcomes"]["submitted"] > 0,
              "input_jobs_succeeded": result["outcomes"]["failed"] == 0,
              "latency_budget": latency_pass, "resident_growth_budget": result["memory"]["within_budget"], "accuracy_nonregression": accuracy_pass}
    result["checks"] = checks
    result["budget_checks_passed"] = all(checks.values())
    with args.output.open("x") as destination:
        json.dump(result, destination, ensure_ascii=False, indent=2)
        destination.write("\n")
    print(json.dumps({"budget_checks_passed": result["budget_checks_passed"], "checks": checks, "outcomes": result["outcomes"]}, indent=2))


if __name__ == "__main__":
    main()
