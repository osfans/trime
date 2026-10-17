#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compare immutable T04 evidence, with a development-only profile nomination."""
import argparse
from pathlib import Path

from run import b, export_rows, extra_selections, metrics, validate_records


def records(root, profile, cohort):
    return b.read_json(root / profile / cohort / "2-measure.json")["records"]


def sufficient_top1_gain(old, new):
    # 6.25 percentage points = 1/16; displayed rounded percentages are not a gate.
    return (old["n"] > 0 and new["n"] > 0 and
            16 * (new["top1_hits"] * old["n"] - old["top1_hits"] * new["n"]) >= old["n"] * new["n"])


def load_evidence(root):
    report = b.read_json(root / "report.json")
    if report["state"] != "complete":
        raise ValueError("Cannot summarize an incomplete run as a benchmark")
    corpus = b.read_json(root / "corpus.json")
    if b.digest(root / "corpus.json") != report["corpus_sha256"]:
        raise ValueError("Corpus changed")
    if "tool_sources_sha256" in report and b.tree_hashes(root / "tools") != report["tool_sources_sha256"]:
        raise ValueError("Tool source snapshot changed")
    profiles = report["profiles"]
    for profile, data in profiles.items():
        if b.tree_hashes(root / profile / "shared") != data["input_sha256"]["shared"]:
            raise ValueError("Shared input changed")
        if b.tree_hashes(root / profile / "seed") != data["input_sha256"]["seed"]:
            raise ValueError("Seed changed")
        for cohort, result in data["cohorts"].items():
            for key, value in result.items():
                if key[:1].isdigit() and b.digest(root / profile / cohort / (key + ".json")) != value["sha256"]:
                    raise ValueError("Result changed")
            if b.tree_hashes(root / profile / cohort / "user") != result["runtime_files_sha256"]:
                raise ValueError("Runtime data changed")
            folder = root / profile / cohort
            validate_records(records(root, profile, cohort), corpus)
            exported = folder / "1-export.tsv"
            if exported.read_bytes() != (folder / "3-export.tsv").read_bytes():
                raise ValueError("Measurement changed the user dictionary export")
            if (folder / "3-export.tsv").read_bytes() != (folder / "user/learning.tsv").read_bytes():
                raise ValueError("Export differs from the hashed runtime copy")
            rows = export_rows(exported)
            if cohort == "cold" and rows:
                raise ValueError("Cold cohort was not empty")
            if cohort == "learned":
                codes = {(parts[0], " ".join(parts[1].split()))
                         for line in rows if len(parts := line.split("\t")) >= 2}
                expected = {(step["traditional"], step["pinyin"]) for step in corpus["learning"]}
                if not expected.issubset(codes) or any("~" in code for _, code in codes):
                    raise ValueError("Learning did not preserve canonical pinyin across restart")
    return report, corpus


def comparison(root):
    report, corpus = load_evidence(root)
    cases = {case[0]: case for case in corpus["cases"]}
    profiles = report["profiles"]
    if "luna" not in profiles:
        raise ValueError("Comparison requires a completed luna baseline")
    # Derive every published metric from hashed traces, not cached driver totals.
    for profile, data in profiles.items():
        for kind, result in data["cohorts"].items():
            measured = records(root, profile, kind)
            result["metrics"] = {f"{split}/{'simp' if simp else 'trad'}": metrics(measured, cases, split, simp)
                                 for split in ("dev", "holdout") for simp in (False, True)}
    def cohort(profile, kind):
        return profiles[profile]["cohorts"][kind]
    def score(profile):
        return sum(cohort(profile, kind)["metrics"]["dev/" + mode]["top1_hits"]
                   for kind in ("cold", "learned") for mode in ("simp", "trad"))
    # CLI profile order must not change the default decision on a tie.
    preference = ["luna", "common", "extended", "balanced", "compact"]
    nominee = max(profiles, key=lambda profile: (score(profile), -preference.index(profile)))
    gates = []
    if nominee != "luna":
        for kind in ("cold", "learned"):
            for mode in ("simp", "trad"):
                old = cohort("luna", kind)["metrics"]["holdout/" + mode]
                new = cohort(nominee, kind)["metrics"]["holdout/" + mode]
                gates.extend([
                    (f"{kind}/{mode}: Top-1 +6.25pp", sufficient_top1_gain(old, new)),
                    (f"{kind}/{mode}: Top-3 non-regression", new["top3"] >= old["top3"]),
                    (f"{kind}/{mode}: sentence non-regression", new["sentence_exact"] >= old["sentence_exact"]),
                    (f"{kind}/{mode}: resolved coverage non-regression", new["selection_unresolved"] <= old["selection_unresolved"]),
                    (f"{kind}/{mode}: measured PSS growth <=20 MiB",
                     cohort("luna", kind)["sampled_peak_engine_pss_kib"] > 0 and
                     0 < cohort(nominee, kind)["sampled_peak_engine_pss_kib"] <=
                     cohort("luna", kind)["sampled_peak_engine_pss_kib"] + 20480)])
        gates.append(("fresh deployment <=2x baseline",
                      0 < cohort(nominee, "cold")["fresh_deploy_ms"] <= cohort("luna", "cold")["fresh_deploy_ms"] * 2))
    default = nominee if gates and all(passed for _, passed in gates) else "luna"
    lines = ["# T04 Dictionary Evaluation", "", f"Run: `{report['id']}`", "",
             "## Identity", "",
             f"- APK: `{report['build']['apk_sha256']}`",
             f"- Build: {report['build']['version_name']} / {report['build']['version_code']}; `{report['build']['package_name']}`",
             f"- Git: `{report['build']['git_sha']}`",
             f"- Certificate: `{', '.join(report['build']['signer_certificate_sha256'])}`",
             f"- Corpus: `{report['corpus_sha256']}`",
             f"- Engine: `{report['engine']['library_sha256']}`; {report['engine']['abi']}; {report['engine']['execution_mode']}",
             f"- Android: {report['device']['properties']['ro.build.version.release']} / API {report['device']['properties']['ro.build.version.sdk']}",
             "- Dictionary overlays are experiments, not the installed APK's shipped configuration.",
             "- Device fingerprint, source/seed/runtime checksums and effective configs: `report.json`.",
             "", "## Accuracy", "",
             "Top-1/Top-3 require exact text AND full input consumption; sentence exact uses sentence cases only.",
             "", "| Profile | Cohort | Split/mode | N | Top-1 % | Top-3 % | Sentence % | Extra choices /100 resolved chars | Unresolved |",
             "|---|---|---|---:|---:|---:|---:|---:|---:|"]
    for profile in profiles:
        for kind in ("cold", "learned"):
            for group, m in cohort(profile, kind)["metrics"].items():
                lines.append(f"| {profile} | {kind} | {group} | {m['n']} | {m['top1']} | {m['top3']} | {m['sentence_exact']} | {m['extra_selections_per_100_resolved_chars']} | {m['selection_unresolved']} |")
    lines.extend(["", "Extra choices are a bounded oracle replay, not a human usability measurement: longest matching target prefix among 80 candidates; each intermediate selection counts once; final confirmation counts only if non-first. Final commit is not executed. Scrolling, pinyin locking, retries and unresolved cases are NOT assigned zero cost. All totals here are recalculated from hashed step traces; early probe weighted-effort totals are not used. Different resolved coverage must not be compared without the paired subset below.",
                  "", "## Resources", "",
                  "| Profile | Fresh deploy seconds | Cold sampled engine PSS MiB | Learned sampled engine PSS MiB |",
                  "|---|---:|---:|---:|"])
    for profile in profiles:
        lines.append(f"| {profile} | {cohort(profile, 'cold')['fresh_deploy_ms'] / 1000:.2f} | {cohort(profile, 'cold')['sampled_peak_engine_pss_kib'] / 1024:.2f} | {cohort(profile, 'learned')['sampled_peak_engine_pss_kib'] / 1024:.2f} |")
    lines.extend(["", "PSS samples are from the standalone APK-engine process after queries, not the Android keyboard service and not deployment peak. Native-bridge timings cannot predict ARM phone performance. Learned groups reuse only cold compiled tables, never cold user DBs.",
                  "", "## Improved And Regressed", ""])
    examples = []
    paired = []
    learning_effect = []
    category_metrics = []
    for profile in profiles:
        cold = {(r["id"], r["simplified"]): r for r in records(root, profile, "cold")}
        learned = {(r["id"], r["simplified"]): r for r in records(root, profile, "learned")}
        for step in corpus["learning"]:
            for case in corpus["cases"]:
                if case[4] != step["pinyin"] or case[6] != step["traditional"]:
                    continue
                def learned_rank(row):
                    return next((i + 1 for i, item in enumerate(row["candidates"])
                                 if item["text"] == step["traditional"] and item["full"]), None)
                learning_effect.append({"profile": profile, "id": case[0], "target": step["traditional"],
                                        "cold_rank": learned_rank(cold[case[0], False]),
                                        "learned_rank": learned_rank(learned[case[0], False])})
        for kind in ("cold", "learned"):
            measured = cold.values() if kind == "cold" else learned.values()
            for category in sorted({c[2] for c in cases.values()}):
                for split in ("dev", "holdout"):
                    for simp in (False, True):
                        rows = [r for r in measured
                                if cases[r["id"]][2] == category and cases[r["id"]][1] == split
                                and r["simplified"] == simp]
                        def hit(row, limit):
                            gold = cases[row["id"]][5 if simp else 6]
                            return any(c["text"] == gold and c["full"] for c in row["candidates"][:limit])
                        category_metrics.append({"profile": profile, "cohort": kind, "category": category,
                                                 "split": split, "simplified": simp, "n": len(rows),
                                                 "top1_hits": sum(hit(r, 1) for r in rows),
                                                 "top3_hits": sum(hit(r, 3) for r in rows)})
    for profile in profiles:
        if profile == "luna":
            continue
        for kind in ("cold", "learned"):
            old_rows = {(r["id"], r["simplified"]): r for r in records(root, "luna", kind)}
            new_rows = records(root, profile, kind)
            common = [r for r in new_rows if r["cost"]["complete"] and old_rows[r["id"], r["simplified"]]["cost"]["complete"]]
            for split in ("dev", "holdout"):
                for simp in (False, True):
                    subset = [r for r in common if r["simplified"] == simp and cases[r["id"]][1] == split]
                    chars = sum(len(cases[r["id"]][5 if simp else 6]) for r in subset)
                    old_cost = sum(extra_selections(old_rows[r["id"], simp]["cost"]) for r in subset)
                    new_cost = sum(extra_selections(r["cost"]) for r in subset)
                    paired.append({"profile": profile, "cohort": kind, "split": split, "simplified": simp,
                                   "resolved_pairs": len(subset), "chars": chars,
                                   "old_per100": round(old_cost * 100 / chars, 2) if chars else None,
                                   "new_per100": round(new_cost * 100 / chars, 2) if chars else None})
            for row in new_rows:
                old = old_rows[row["id"], row["simplified"]]
                gold = cases[row["id"]][5 if row["simplified"] else 6]
                def rank(r):
                    return next((i + 1 for i, c in enumerate(r["candidates"]) if c["text"] == gold and c["full"]), None)
                before, after = rank(old), rank(row)
                if before != after:
                    examples.append({"profile": profile, "cohort": kind, "id": row["id"],
                                     "split": cases[row["id"]][1], "simplified": row["simplified"],
                                     "expected": gold, "before_rank": before, "after_rank": after,
                                     "direction": "improved" if (after or 999) < (before or 999) else "regressed",
                                     "old_first": old["candidates"][0]["text"] if old["candidates"] else "",
                                     "new_first": row["candidates"][0]["text"] if row["candidates"] else ""})
    for profile in profiles:
        for direction in ("improved", "regressed"):
            subset = [e for e in examples if e["profile"] == profile and e["direction"] == direction and e["cohort"] == "cold" and e["simplified"]]
            if not subset:
                continue
            lines.append(f"### {profile}: {direction}")
            lines.append("")
            for e in subset[:10]:
                lines.append(f"- {e['id']} ({e['split']}): {e['expected']}, rank {e['before_rank']} -> {e['after_rank']}; first {e['old_first']} -> {e['new_first']}")
            lines.append("")
    lines.extend(["## User Dictionary", "",
                  "Each training target is explicitly committed five times using exact pinyin; a new process then queries raw T9. Rank changes below are observable ranking effects, not merely the existence of a database file.", "",
                  "| Profile | Target | Cold rank | Learned rank |", "|---|---|---:|---:|"])
    lines.extend(f"| {e['profile']} | {e['target']} | {e['cold_rank']} | {e['learned_rank']} |" for e in learning_effect)
    lines.extend(["", "## Decision", "", f"Development-only nominee: **{nominee}**. Gated default: **{default}**.", ""])
    lines.extend(f"- {'PASS' if passed else 'FAIL'}: {name}" for name, passed in gates)
    lines.extend(["", "Full paired selection costs, all rank changes and gate outcomes are in `comparison.json`. This small authored corpus is a regression suite, not a population estimate; identical homophone codes have mutually exclusive goldens. No large dictionary or octagram model was enabled.", ""])
    result = {"id": report["id"], "dev_nominee": nominee, "default": default,
              "metrics_definition": "trace-v2: intermediate selections + non-first final selection; never double count an intermediate selection",
              "metrics": {p: {c: d["metrics"] for c, d in data["cohorts"].items()} for p, data in profiles.items()},
              "gates": dict(gates), "rank_changes": examples, "paired_selection_cost": paired,
              "learning_effect": learning_effect, "categories": category_metrics}
    b.write_json(root / "comparison.json", result)
    (root / "comparison.md").write_text("\n".join(lines), encoding="utf-8")
    print(root / "comparison.md")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("run", type=Path)
    comparison(parser.parse_args().run.resolve())
