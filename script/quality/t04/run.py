#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Evaluate pinned dictionary overlays using the frozen APK engine on Android."""
import argparse
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / "script/baseline"))
import baseline as b  # noqa: E402


def validate(corpus):
    ids = set()
    if corpus["format_version"] != 1 or not 3 <= corpus["candidate_limit"] <= 200:
        raise ValueError("Unsupported corpus")
    if not corpus["cases"] or not corpus["learning"]:
        raise ValueError("Corpus requires evaluation and learning cases")
    for row in corpus["cases"]:
        if len(row) != 7 or row[0] in ids or row[1] not in ("dev", "holdout"):
            raise ValueError("Invalid/duplicate case")
        if not re.fullmatch("[a-z]+(?:[ '][a-z]+)*", row[4]):
            raise ValueError("Invalid pinyin")
        if not row[5] or len(row[5]) != len(row[6]):
            raise ValueError("Invalid traditional/simplified golden pair")
        ids.add(row[0])
    if {row[1] for row in corpus["cases"]} != {"dev", "holdout"}:
        raise ValueError("Both development and holdout cases are required")
    for row in corpus["learning"]:
        if not re.fullmatch("[a-z]+(?: [a-z]+)*", row["pinyin"]) or not 1 <= row["repeat"] <= 100:
            raise ValueError("Invalid learning script")


def compile_probe(cxx, library, output, remote="$ORIGIN"):
    db = b.read_json(cxx / "compile_commands.json")
    base = next(item for item in db if item["file"].endswith("/frontend.cc"))
    command = shlex.split(base["command"])
    flags = []
    i = 1
    while i < len(command):
        if command[i] in ("-o", "-c", "-MF", "-MT", "-MQ"):
            i += 2
        elif command[i] in ("-MD", "-MMD"):
            i += 1
        else:
            if command[i].startswith("-I") and "/boost/" in command[i]:
                flags.extend(["-isystem", command[i][2:]])
            else:
                flags.append(command[i])
            i += 1
    subprocess.run([command[0], *flags, "-std=c++17", "-O1", "-g0", "-Wall", "-Wextra",
                    "-Wno-missing-field-initializers", "-Wno-deprecated-declarations",
                    "-isystem", str(ROOT / "app/src/main/jni/OpenCC/deps/rapidjson-1.1.0"),
                    str(HERE / "probe.cc"), "-L" + str(library.parent), "-lrime_jni",
                    "-ldl", "-static-libstdc++", "-Wl,-rpath," + remote, "-o", str(output)],
                   cwd=base["directory"], check=True)


def verify_rime_headers(build):
    commit = build.get("git_sha")
    if not commit or not re.fullmatch("[0-9a-f]{40}", commit):
        raise ValueError("Private C++ probe requires an APK with a known source commit")
    module = ROOT / "app/src/main/jni/librime"
    expected = b.run(["git", "-C", ROOT, "rev-parse", commit + ":app/src/main/jni/librime"]).strip()
    actual = b.run(["git", "-C", module, "rev-parse", "HEAD"]).strip()
    dirty = b.run(["git", "-C", module, "diff", "HEAD", "--", "src"]).strip()
    if expected != actual or dirty:
        raise ValueError("Rime C++ headers differ from the APK source; use its matching checkout")
    return {"librime_commit": actual,
            "headers_sha256": {name: b.digest(module / "src/rime" / name)
                               for name in ("candidate.h", "context.h", "menu.h", "schema.h", "service.h")}}


def extra_selections(cost):
    if not cost["complete"]:
        return None
    steps = cost["steps"]
    if not steps:
        raise ValueError("Completed selection has no trace")
    # Intermediate selections already count once, regardless of candidate index.
    # Accepting the final first candidate is the ordinary baseline confirmation.
    return len(steps) - 1 + int(steps[-1]["index"] != 0)


def metrics(records, cases, split, simplified):
    rows = []
    for record in records:
        case = cases[record["id"]]
        if case[1] != split or record["simplified"] != simplified:
            continue
        goal = case[5 if simplified else 6]
        rank = next((i + 1 for i, item in enumerate(record["candidates"])
                     if item["text"] == goal and item["full"]), None)
        rows.append((case, record, rank))
    def rate(count, total):
        return round(count * 100 / total, 2) if total else None
    sentences = [row for row in rows if row[0][3] == "sentence"]
    resolved = [row for row in rows if row[1]["cost"]["complete"]]
    return {"n": len(rows), "top1_hits": sum(r == 1 for _, _, r in rows),
            "top3_hits": sum(r is not None and r <= 3 for _, _, r in rows),
            "top1": rate(sum(r == 1 for _, _, r in rows), len(rows)),
            "top3": rate(sum(r is not None and r <= 3 for _, _, r in rows), len(rows)),
            "sentence_exact": rate(sum(r == 1 for _, _, r in sentences), len(sentences)),
            "sentence_n": len(sentences), "selection_resolved": len(resolved),
            "selection_unresolved": len(rows) - len(resolved),
            "extra_selections_per_100_resolved_chars": rate(
                sum(extra_selections(r["cost"]) for _, r, _ in resolved),
                sum(len(c[5 if simplified else 6]) for c, _, _ in resolved))}


def validate_records(records, corpus):
    expected = {(case[0], mode) for case in corpus["cases"] for mode in (False, True)}
    actual = [(row["id"], row["simplified"]) for row in records]
    if len(actual) != len(expected) or set(actual) != expected:
        raise ValueError("Missing, duplicate or unexpected evaluation records")
    if any(type(row["simplified"]) is not bool for row in records):
        raise ValueError("Invalid evaluation mode")


def export_rows(path):
    if not path.exists():
        return []
    return sorted(line for line in path.read_text(encoding="utf-8").splitlines()
                  if line and not line.startswith("#"))


def execute(args):
    if not 1 <= args.timeout <= 3600 or len(set(args.profiles)) != len(args.profiles):
        raise ValueError("Invalid phase timeout or duplicate profiles")
    frozen, prior = b.load_run(args.baseline)
    abi_source = verify_rime_headers(prior["build"])
    corpus = b.read_json(args.corpus)
    validate(corpus)
    provenance = b.read_json(args.packs / "provenance.json")
    for path, expected in provenance["outputs"].items():
        if b.digest(args.packs / path) != expected:
            raise ValueError("Dictionary pack checksum mismatch")
    root = args.out.resolve() / uuid.uuid4().hex[:12]
    root.mkdir(parents=True)
    snapshot = root / "tools"
    snapshot.mkdir()
    for path in (HERE / "run.py", HERE / "probe.cc", HERE / "layers.py", HERE / "sources.lock.json"):
        shutil.copyfile(path, snapshot / path.name)
    sdk = {"adb": args.adb}
    device = b.device_info(sdk, args.serial)
    if device["properties"]["ro.kernel.qemu"] != "1":
        raise ValueError("This development runner requires an emulator")
    abi = "arm64-v8a"
    if abi not in device["properties"]["ro.product.cpu.abilist"].split(","):
        raise ValueError("ARM64 APK requires an ARM64-capable emulator/native bridge")
    library = frozen / f"inputs/lib/{abi}/librime_jni.so"
    shutil.copyfile(args.corpus, root / "corpus.json")
    remote = "/data/local/tmp/trime-t04-" + root.name
    compile_probe(args.cxx.resolve(), library, root / "probe", remote)
    def adb(*commands):
        return b.adb(sdk, args.serial, *commands)
    def shell(*commands):
        return b.shell(sdk, args.serial, *commands)
    shell("mkdir", remote)
    adb("push", str(root / "probe"), remote + "/probe")
    adb("push", str(library), remote + "/librime_jni.so")
    adb("push", str(root / "corpus.json"), remote + "/corpus.json")
    shell("chmod", "700", remote + "/probe")
    report = {"format_version": 1, "id": root.name, "build": prior["build"], "device": device,
              "corpus_sha256": b.digest(args.corpus), "pack": provenance, "remote": remote,
              "tool_sources_sha256": b.tree_hashes(snapshot),
              "engine": {"library_sha256": b.digest(library), "abi": abi,
                         "abi_source": abi_source,
                         "compile_database_sha256": b.digest(args.cxx / "compile_commands.json"),
                         "execution_mode": "native_bridge" if device["properties"]["ro.product.cpu.abi"] != abi else "native",
                         "probe_sha256": b.digest(root / "probe"),
                         "sources_sha256": {p.name: b.digest(p) for p in [HERE / "run.py", HERE / "probe.cc"]}},
              "scope": "APK engine plus explicit dictionary overlays; not installed app UI/PSS; no user directories touched",
              "profiles": {}, "state": "running"}
    b.write_json(root / "report.json", report)
    print(root, flush=True)
    try:
        for profile in args.profiles:
            print(f"{profile}: fresh deployment", flush=True)
            folder = root / profile
            folder.mkdir()
            shutil.copytree(frozen / "inputs/shared", folder / "shared")
            seed = folder / "seed"
            seed.mkdir()
            shutil.copytree(folder / "shared/opencc", seed / "opencc")
            b.write_json(seed / "default.custom.yaml", {"patch": {"schema_list": [{"schema": corpus["schema"]}]}})
            if profile != "luna":
                for path in (args.packs / profile).iterdir():
                    shutil.copyfile(path, (seed if path.name.endswith("custom.yaml") else folder / "shared") / path.name)
            fingerprint = {"shared": b.tree_hashes(folder / "shared"), "seed": b.tree_hashes(seed)}
            data = {"input_sha256": fingerprint, "input_digest": b.aggregate(fingerprint), "cohorts": {},
                    "grammar_model_files": [name for name in fingerprint["shared"] if name.endswith(".gram")]}
            report["profiles"][profile] = data
            adb("push", str(folder / "shared"), remote + "/" + profile + "/shared")
            # adb push creates parent directories, all under the new UUID-owned root.
            for cohort in ("cold", "learned"):
                user = folder / cohort / "user"
                shutil.copytree(seed, user)
                (user.parent / "owner.txt").write_text(root.name + "\n")
                if cohort == "learned":
                    shutil.copytree(folder / "cold/user/build", user / "build")
                    # Copy only compiled resources, never cold user databases.
                remote_user = f"{remote}/{profile}/{cohort}/user"
                adb("push", str(user.parent), f"{remote}/{profile}/{cohort}")
                result = {}
                data["cohorts"][cohort] = result
                phases = ["deploy"] if cohort == "cold" else ["train"]
                phases += ["export", "measure", "export"]
                before = None
                for index, phase in enumerate(phases):
                    label = f"{index}-{phase}"
                    print(f"{profile}/{cohort}/{label}", flush=True)
                    command = [args.adb, "-s", args.serial, "shell",
                               f"LD_LIBRARY_PATH={remote} " + shlex.join([
                                   remote + "/probe", f"{remote}/{profile}/shared", remote_user,
                                   remote + "/corpus.json", phase, root.name, profile])]
                    with (user.parent / (label + ".json")).open("w") as stdout, \
                            (user.parent / (label + ".log")).open("w") as stderr:
                        subprocess.run(list(map(str, command)), stdout=stdout, stderr=stderr,
                                       check=True, timeout=args.timeout)
                    payload = b.read_json(user.parent / (label + ".json"))
                    if payload["owner"] != root.name or payload["profile"] != profile:
                        raise ValueError("Probe identity mismatch")
                    result[label] = {"sha256": b.digest(user.parent / (label + ".json"))}
                    if phase == "export":
                        target = user.parent / (label + ".tsv")
                        adb("pull", remote_user + "/learning.tsv", str(target))
                        rows = export_rows(target)
                        if cohort == "learned":
                            codes = {(fields[0], " ".join(fields[1].split()))
                                     for line in rows if len(fields := line.split("\t")) >= 2}
                            expected = {(step["traditional"], step["pinyin"])
                                        for step in corpus["learning"]}
                            if not expected.issubset(codes) or any("~" in code for _, code in codes):
                                raise ValueError("Canonical learned codes did not survive restart")
                        if before is None:
                            before = rows
                        elif rows != before:
                            raise ValueError("Measurement changed learned entries")
                        result["user_dictionary_unchanged_during_measurement"] = before == rows
                        result["learned_entry_count"] = len(rows)
                    if phase == "measure":
                        validate_records(payload["records"], corpus)
                        cases = {c[0]: c for c in corpus["cases"]}
                        result["metrics"] = {f"{split}/{'simp' if simp else 'trad'}":
                            metrics(payload["records"], cases, split, simp)
                            for split in ("dev", "holdout") for simp in (False, True)}
                        result["sampled_peak_engine_pss_kib"] = payload["sampled_peak_pss_kib"]
                        result["effective_config"] = payload["effective_config"]
                    elif phase == "deploy":
                        result["fresh_deploy_ms"] = payload["startup_or_deploy_ms"]
                    b.write_json(root / "report.json", report)
                adb("pull", remote_user + "/.", str(user))
                result["runtime_files_sha256"] = b.tree_hashes(user)
                b.write_json(root / "report.json", report)
        report["state"] = "complete"
    except BaseException as error:
        report["state"] = "failed"
        report["error"] = str(error)
        # Kill only this runner's uniquely named process; retain all evidence/data.
        subprocess.run([str(args.adb), "-s", args.serial, "shell", "pkill", "-f", remote + "/probe"],
                       timeout=15, check=False, capture_output=True)
        raise
    finally:
        b.write_json(root / "report.json", report)
    print(root / "report.json")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--packs", type=Path, required=True)
    parser.add_argument("--cxx", type=Path, required=True)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", default="emulator-5560")
    parser.add_argument("--corpus", type=Path, default=HERE / "corpus.json")
    parser.add_argument("--out", type=Path, default=ROOT / "build/t04/runs")
    parser.add_argument("--profiles", nargs="+", choices=["luna", "common", "extended", "balanced", "compact"],
                        default=["luna", "common", "extended", "balanced"])
    parser.add_argument("--timeout", type=int, default=1800)
    execute(parser.parse_args())
