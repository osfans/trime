#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Combine independently completed sequential runs without discarding identities."""
import argparse
import copy
from pathlib import Path
import shutil
import uuid

from summarize import b, comparison, load_evidence


def compatible(reports):
    first = reports[0]
    names = set()
    for report in reports:
        if report["state"] != "complete":
            raise ValueError("Incomplete source run")
        for key in ("apk_sha256", "signer_certificate_sha256"):
            if report["build"][key] != first["build"][key]:
                raise ValueError("APK identity mismatch")
        if report["corpus_sha256"] != first["corpus_sha256"]:
            raise ValueError("Corpus mismatch")
        if report["device"] != first["device"]:
            raise ValueError("Device mismatch")
        for key in ("library_sha256", "abi", "execution_mode"):
            if report["engine"][key] != first["engine"][key]:
                raise ValueError("Engine mismatch")
        if report["engine"]["sources_sha256"]["probe.cc"] != first["engine"]["sources_sha256"]["probe.cc"]:
            raise ValueError("Probe algorithm/source mismatch")
        if names.intersection(report["profiles"]):
            raise ValueError("Duplicate profile; repeated trials must not be silently replaced")
        names.update(report["profiles"])


def merge(roots, output):
    reports = [load_evidence(root)[0] for root in roots]
    compatible(reports)
    root = output.resolve() / ("combined-" + uuid.uuid4().hex[:12])
    root.mkdir(parents=True)
    result = copy.deepcopy(reports[0])
    result["id"] = root.name
    result["scope"] = "Combined sequential evaluations; every profile retains its original run and probe identity"
    result.pop("remote", None)
    result.pop("tool_sources_sha256", None)
    result["profiles"] = {}
    result["origin_runs"] = {}
    shutil.copyfile(roots[0] / "corpus.json", root / "corpus.json")
    for source, report in zip(roots, reports):
        result["origin_runs"][report["id"]] = report
        for name, profile in report["profiles"].items():
            shutil.copytree(source / name, root / name)
            result["profiles"][name] = copy.deepcopy(profile)
            result["profiles"][name]["origin_run"] = report["id"]
            result["profiles"][name]["engine"] = report["engine"]
    b.write_json(root / "report.json", result)
    comparison(root)
    return root


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("runs", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/t04/runs"))
    args = parser.parse_args()
    print(merge([p.resolve() for p in args.runs], args.out))
