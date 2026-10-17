#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Summarize T05 instrumentation metrics, keeping every failed recovery visible."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import statistics


def summarize(data):
    groups = data["groups"]
    if len(groups) != 2 or [g["all_rules"] for g in groups] != [False, True]:
        raise ValueError("Expected exactly one off/on pair")
    before, after = [g["records"] for g in groups]
    if len(before) != 24 or len(after) != 24:
        raise ValueError("Expected the fixed 24-item exact corpus")
    if len({(r["pinyin"], r["input"]) for r in before}) != len(before):
        raise ValueError("Duplicate exact-corpus item")
    unchanged = 0
    for old, new in zip(before, after):
        if (old["pinyin"], old["input"]) != (new["pinyin"], new["input"]):
            raise ValueError("Mismatched corpus order")
        if old["candidates"] != new["candidates"]:
            raise ValueError("Exact candidate regression: " + old["pinyin"])
        if old["suggestions"] != 0 or not 0 <= new["suggestions"] <= 32:
            raise ValueError("Suggestion bound/default invariant failed")
        unchanged += 1
    measurements = []
    for group in groups:
        times = sorted(r["last_key_and_80_candidates_ms"] for r in group["records"])
        if any(not math.isfinite(t) or t < 0 for t in times) or group["app_pss_kib"] <= 0:
            raise ValueError("Invalid latency or PSS sample")
        measurements.append({
            "all_rules": group["all_rules"],
            "median_ms": statistics.median(times),
            "p95_ms": times[math.ceil(0.95 * len(times)) - 1],
            "app_pss_kib": group["app_pss_kib"],
            "mean_extra_choices": statistics.mean(r["suggestions"] for r in group["records"]),
            "max_extra_choices": max(r["suggestions"] for r in group["records"]),
        })
        first = sorted(r["first_key_ms"] for r in group["records"])
        if any(not math.isfinite(t) or t < 0 for t in first):
            raise ValueError("Invalid first-key latency")
        measurements[-1].update(first_key_median_ms=statistics.median(first),
                                first_key_p95_ms=first[math.ceil(0.95 * len(first)) - 1],
                                first_key_max_ms=max(first),
                                first_input_first_key_ms=group["records"][0]["first_key_ms"],
                                subsequent_first_key_median_ms=statistics.median(
                                    r["first_key_ms"] for r in group["records"][1:]))
    records = data["recovery"]
    if len(records) != 24 or len({(r["rule"], r["input"], r["target"]) for r in records}) != 24:
        raise ValueError("Expected 24 unique fixed mistakes")
    rules = []
    for rule in range(9):
        cases = [r for r in records if r["rule"] == rule]
        if len(cases) != (2 if rule < 6 else 4):
            raise ValueError("Incomplete single-rule coverage")
        if any(r["sources"] not in (0, 1 << rule) for r in cases):
            raise ValueError("An individually enabled rule leaked other sources")
        rules.append({"rule": rule, "total": len(cases),
                      "before": sum(r["before"] for r in cases),
                      "after": sum(r["after"] for r in cases)})
    recovery = {}
    for name, subset in (("fuzzy", records[:12]), ("typo", records[12:])):
        expected = set(range(6)) if name == "fuzzy" else {6, 7, 8}
        if {r["rule"] for r in subset} != expected:
            raise ValueError("Unexpected mistake group order")
        recovery[name] = {"total": len(subset), "before": sum(r["before"] for r in subset),
                          "after": sum(r["after"] for r in subset)}
    return {
        "version": data["version"], "version_code": data["version_code"],
        "scope": data["scope"], "exact_candidate_lists_unchanged": unchanged,
        "measurements": measurements, "recovery": recovery, "rules": rules,
        "improved": [r for r in records if not r["before"] and r["after"]],
        "regressed": [r for r in records if r["before"] and not r["after"]],
        "unrecovered": [r for r in records if not r["after"]],
        "limitations": ["Explicit whole-syllable selection, not automatic Hanzi Top-1",
                        "Single sequential off/on run on a debug native-bridge emulator",
                        "Sampled PSS includes allocator/UI warmup; not a causal memory delta or peak",
                        "First-key timing includes lazy index initialization; all timings exclude display presentation"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("metrics", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    raw = args.metrics.read_bytes()
    result = summarize(json.loads(raw))
    result["metrics_sha256"] = hashlib.sha256(raw).hexdigest()
    with args.output.open("x") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


if __name__ == "__main__":
    main()
