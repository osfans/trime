# SPDX-License-Identifier: GPL-3.0-or-later
"""Content-free T08 measurements; Android meminfo values are KiB of PSS."""

import math
import re
import statistics


def percentile(values, fraction=0.95):
    ordered = sorted(values)
    if not ordered or any(not math.isfinite(v) or v < 0 for v in ordered):
        raise ValueError("Expected non-negative finite measurements")
    return ordered[math.ceil(fraction * len(ordered)) - 1]


def parse_meminfo(text):
    """Read the detailed table's first (PSS) column, not private/RSS columns.

    Java means Dalvik Heap plus Dalvik Other. Graphics means Gfx dev, EGL
    mtrack and GL mtrack; unavailable GPU accounting remains None. Keep the
    full dump alongside these values for device-specific interpretation.
    """
    rows = {}
    for match in re.finditer(
        r"^\s*(Native Heap|Dalvik Heap|Dalvik Other|Gfx dev|EGL mtrack|GL mtrack)\s+(\d+)\s+",
        text, re.M,
    ):
        if match[1] in rows:
            raise ValueError("Duplicate meminfo row: " + match[1])
        rows[match[1]] = int(match[2])
    total = re.search(r"^\s*TOTAL PSS:\s*(\d+)\b", text, re.M)
    if total is None:
        total = re.search(r"^\s*TOTAL\s+(\d+)\s+", text, re.M)
    if total is None or "Native Heap" not in rows or "Dalvik Heap" not in rows:
        raise ValueError("Missing detailed Java/native/total PSS rows")
    graphics = [rows[name] for name in ("Gfx dev", "EGL mtrack", "GL mtrack") if name in rows]
    return {
        "java_pss_kib": rows["Dalvik Heap"] + rows.get("Dalvik Other", 0),
        "native_pss_kib": rows["Native Heap"],
        "graphics_pss_kib": sum(graphics) if graphics else None,
        "total_pss_kib": int(total[1]),
        "rows_pss_kib": rows,
    }


MEMORY_FIELDS = ("java_pss_kib", "native_pss_kib", "graphics_pss_kib", "total_pss_kib")


def memory_convergence(rounds, tail_rounds=5, tolerance_kib=4096, tolerance_percent=5):
    """Compare medians at the end of each repeated, identical workload round.

    The provisional tolerance is a reviewable resident-growth budget, not a
    promise about peak allocation or a universal limit for all Android devices.
    """
    if tail_rounds < 3 or tolerance_kib < 0 or tolerance_percent < 0:
        raise ValueError("Invalid resident-growth budget")
    summaries = []
    for number, samples in sorted(rounds.items()):
        if len(samples) < 3:
            raise ValueError("Each round needs at least three memory samples")
        tail = samples[-3:]
        summaries.append({"round": number, **{
            field: statistics.median([sample[field] for sample in tail])
            if all(sample.get(field) is not None for sample in tail) else None
            for field in MEMORY_FIELDS
        }})
    enough = len(summaries) >= tail_rounds
    window = summaries[-tail_rounds:]
    buckets = {}
    for field in MEMORY_FIELDS:
        values = [entry[field] for entry in window]
        if not values or any(value is None for value in values):
            buckets[field] = {"complete": False, "within_budget": False}
            continue
        # A positive least-squares trend catches continued per-round growth
        # even if the last round happens to fall after an allocator/GC dip.
        xmean = (len(values) - 1) / 2
        ymean = statistics.mean(values)
        denominator = sum((index - xmean) ** 2 for index in range(len(values)))
        slope = sum((index - xmean) * (value - ymean) for index, value in enumerate(values)) / denominator if denominator else 0
        growth = max(0, values[-1] - values[0], slope * (len(values) - 1))
        budget = max(tolerance_kib, values[0] * tolerance_percent / 100)
        buckets[field] = {
            "complete": enough, "resident_growth_kib": round(growth, 3),
            "slope_kib_per_round": round(slope, 3), "budget_kib": round(budget, 3),
            "within_budget": enough and growth <= budget,
        }
    return {
        "rounds": summaries, "tail_rounds_required": tail_rounds,
        "tolerance_kib": tolerance_kib, "tolerance_percent": tolerance_percent,
        "buckets": buckets, "within_budget": enough and all(item["within_budget"] for item in buckets.values()),
    }
