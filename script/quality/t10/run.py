#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Isolated real-host-Rime learning/restart/redeployment/backup acceptance.

This uses the host librime public C API, not the APK engine. It cannot establish
Android privacy guards, application upgrade compatibility, UI accuracy or timing.
Every probe invocation is a separate process and all its output is preserved.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
GOLDEN = {"茶雲翎": "cha yun ling", "墨羽燈": "mo yu deng"}


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        result = hashlib.file_digest(stream, "sha256").hexdigest()
    return result


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def export_records(path: Path) -> dict[tuple[str, str], int]:
    result: dict[tuple[str, str], int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 3:
            raise ValueError("Malformed real-engine user dictionary export")
        text, spelling, count = fields
        spelling = " ".join(spelling.split())
        if not text or not re.fullmatch(r"[a-z]+(?: [a-z]+)*", spelling):
            raise ValueError("Invalid exported record identity")
        identity = (text, spelling)
        if identity in result or not re.fullmatch(r"\d+", count):
            raise ValueError("Duplicate or invalid exported learning count")
        result[identity] = int(count)
    return result


def learned_counts(records: dict[tuple[str, str], int]) -> dict[str, int]:
    counts = {}
    for text, spelling in GOLDEN.items():
        identity = (text, spelling)
        if identity not in records or records[identity] <= 0:
            raise ValueError(f"Fictional phrase was not persistently learned: {text}")
        counts[text] = records[identity]
    return counts


def write_fixture(shared: Path, name: str, version: int) -> None:
    if not re.fullmatch(r"__t10_[a-f0-9]{32}", name):
        raise ValueError("Unowned fixture dictionary name")
    (shared / f"{name}.dict.yaml").write_text(
        "---\n" + f'name: {name}\nversion: "1"\n' +
        "sort: by_weight\nuse_preset_vocabulary: false\n...\n" +
        "茶\tcha\t100\n雲\tyun\t100\n翎\tling\t100\n" +
        "墨\tmo\t100\n羽\tyu\t100\n燈\tdeng\t100\n",
        encoding="utf-8",
    )
    schemas = []
    for suffix in ("full", "t9"):
        schema = f"{name}_{suffix}"
        schemas.append(schema)
        algebra = (
            "  algebra:\n    - derive/^(.*)$/\\U$1/\n"
            "    - xlit/ABCDEFGHIJKLMNOPQRSTUVWXYZ/22233344455566677778889999/\n"
            if suffix == "t9" else ""
        )
        (shared / f"{schema}.schema.yaml").write_text(
            f'schema:\n  schema_id: {schema}\n  name: T10 {suffix}\n  version: "{version}"\n'
            "switches:\n  - name: ascii_mode\n    reset: 0\n    states: [native, ascii]\n"
            "ascii_composer:\n  switch_key:\n    Shift_L: inline_ascii\n    Shift_R: commit_text\n"
            "engine:\n  processors: [ascii_composer, speller, selector, navigator, express_editor]\n"
            "  segmentors: [ascii_segmentor, abc_segmentor, fallback_segmentor]\n"
            "  translators: [script_translator]\n"
            "speller:\n  alphabet: abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ987654321\n"
            "  delimiter: \" '\"\n" + algebra +
            f"translator:\n  dictionary: {name}\n  prism: {schema}\n"
            "  enable_user_dict: true\n  enable_sentence: true\n  enable_completion: false\n"
            "menu:\n  page_size: 5\n",
            encoding="utf-8",
        )
    (shared / "default.yaml").write_text(
        'config_version: "1"\nschema_list:\n' +
        "".join(f"  - schema: {schema}\n" for schema in schemas), encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--library", type=Path, default=Path("/usr/lib/librime.so"))
    parser.add_argument("--include", type=Path, default=Path("/usr/include"))
    parser.add_argument("--compiler", default="g++")
    args = parser.parse_args()
    out = args.out.resolve()
    if ROOT / "build/t10" not in out.parents or out.exists():
        raise ValueError("Use a fresh run directory strictly below build/t10")
    library = args.library.resolve(strict=True)
    include = args.include.resolve(strict=True)
    headers = [include / "rime_api.h", include / "rime_levers_api.h"]
    out.mkdir(parents=True)
    owner = uuid.uuid4().hex
    name = "__t10_" + owner
    (out / "owner.txt").write_text(owner + "\n", encoding="ascii")
    shared, user = out / "shared", out / "user"
    shared.mkdir()
    user.mkdir()
    source_paths = [HERE / "run.py", HERE / "probe.cc", *headers, library]
    production_paths = [
        ROOT / "app/src/main/assets/shared/luna_pinyin_t9.schema.yaml",
        ROOT / "app/data/rime/luna-pinyin/luna_pinyin.schema.yaml",
        ROOT / "app/data/rime/luna-pinyin/luna_quanpin.schema.yaml",
        ROOT / "app/data/rime/luna-pinyin/luna_pinyin_simp.schema.yaml",
        ROOT / "app/src/main/jni/librime/src/rime/dict/user_dictionary.cc",
        ROOT / "app/src/main/jni/librime/src/rime/gear/memory.cc",
        ROOT / "app/src/main/jni/librime/src/rime/lever/user_dict_manager.cc",
    ]
    binding = {str(path): digest(path) for path in [*source_paths, *production_paths]}
    report: dict = {
        "passed": False, "format_version": 1, "owner": owner,
        "scope": "host librime public C API; miniature owned script-translator schemas",
        "boundaries": ["not APK engine", "not app binary upgrade", "not Android privacy acceptance",
                       "not candidate UI/P95/PSS/accuracy", "restore API merges; exact rollback only tested into an empty owned DB"],
        "source_before": binding, "phases": [], "checks": [],
    }
    write_json(out / "report.json", report)

    def command(label: str, argv: list[str]) -> subprocess.CompletedProcess:
        started = time.time()
        with (out / f"{label}.stdout").open("wb") as stdout, (out / f"{label}.stderr").open("wb") as stderr:
            execution = subprocess.run(argv, cwd=out, stdout=stdout, stderr=stderr, timeout=120, check=False)
        receipt = {"label": label, "argv": argv, "exit_code": execution.returncode,
                   "wall_seconds": time.time() - started,
                   "stdout_sha256": digest(out / f"{label}.stdout"),
                   "stderr_sha256": digest(out / f"{label}.stderr")}
        report["phases"].append(receipt)
        write_json(out / "report.json", report)
        if execution.returncode != 0:
            raise RuntimeError(f"{label} failed; raw output preserved in {out}")
        return execution

    def probe(label: str, phase: str, snapshot: Path | None = None) -> dict:
        argv = [str(out / "probe"), str(shared), str(user), name, phase, owner, str(out / f"{label}.txt")]
        if snapshot is not None:
            argv.append(str(snapshot))
        command(label, argv)
        result = json.loads((out / f"{label}.stdout").read_text(encoding="utf-8"))
        if result.get("passed") is not True or result["owner"] != owner or result["phase"] != phase:
            raise ValueError("Probe result identity did not match its execution")
        write_json(out / f"{label}.json", result)
        return result

    try:
        command("compiler-version", [args.compiler, "--version"])
        command("compile", [args.compiler, "-std=c++17", "-O1", "-g0", "-Wall", "-Wextra", "-Werror",
                            "-I", str(include), str(HERE / "probe.cc"), str(library),
                            "-Wl,-rpath," + str(library.parent), "-o", str(out / "probe")])
        report["probe_sha256"] = digest(out / "probe")
        write_fixture(shared, name, 1)
        initial = probe("01-deploy", "deploy")
        if len(initial["records"]) != 2 or any(
            row["dictionary"] != name or row["user_dict"] is not None or row["version"] != "1"
            for row in initial["records"]
        ):
            raise ValueError("Unexpected initial deployed shared dictionary configuration")
        probe("02-export-cold", "export")
        cold = export_records(out / "02-export-cold.txt")
        if any((text, spelling) in cold for text, spelling in GOLDEN.items()):
            raise ValueError("Cold fixture already contains golden phrase learning")
        learned = probe("03-learn", "learn")
        if len(learned["records"]) != 6:
            raise ValueError("The exact six learning commits were not executed")
        probe("04-export-learned", "export")
        before = export_records(out / "04-export-learned.txt")
        if any(count < 3 for count in learned_counts(before).values()):
            raise ValueError("The three actual commits per phrase were not learned")
        inspected = probe("05-restart-switch", "inspect")
        if len(inspected["records"]) != 4 or inspected["pid"] == learned["pid"]:
            raise ValueError("Restart/schema switching was not exercised")
        probe("06-export-after-read", "export")
        if before != export_records(out / "06-export-after-read.txt"):
            raise ValueError("Read-only queries polluted the learned dictionary")
        report["checks"].append("six exact commits learned; different process and both full/T9 schemas share the same DB; queries are read-only")
        old_stamps = {path: path.stat().st_mtime_ns for path in shared.glob("*.schema.yaml")}
        write_fixture(shared, name, 2)
        # Rime stores dependency mtimes as integer seconds. A tiny test can rewrite
        # a schema within that same second, so explicitly provide a distinct source
        # timestamp instead of bypassing its deployment check or waiting arbitrarily.
        for path, previous in old_stamps.items():
            stamp = max(path.stat().st_mtime_ns, previous + 2_000_000_000)
            os.utime(path, ns=(stamp, stamp))
        upgrade = probe("07-redeploy-version2", "deploy")
        if any(row["version"] != "2" for row in upgrade["records"]):
            raise ValueError("New schema version did not deploy")
        probe("08-after-redeploy", "inspect")
        probe("09-export-redeploy", "export")
        if before != export_records(out / "09-export-redeploy.txt"):
            raise ValueError("Schema redeployment changed logical learning records")
        report["checks"].append("schema version 1 to 2 redeployment preserves learned records; not app APK upgrade")
        backup = probe("10-backup", "backup")
        snapshot = Path(backup["records"][0]["snapshot"]).resolve(strict=True)
        if user not in snapshot.parents or snapshot.name != name + ".userdb.txt":
            raise ValueError("Engine returned an unowned backup snapshot")
        report["snapshot_sha256"] = digest(snapshot)
        probe("11-forget", "forget")
        probe("12-export-forgotten", "export")
        forgotten = export_records(out / "12-export-forgotten.txt")
        first = next(iter(GOLDEN.items()))
        if first in forgotten:
            raise ValueError("Forgotten phrase still has a positive exported learning record")
        second = list(GOLDEN.items())[1]
        if forgotten.get(second) != before[second]:
            raise ValueError("Forgetting one phrase changed another phrase")
        # Stock sentence generation can still produce the phrase: forgetting is not
        # a permanent prohibition on its text or future learning.
        probe("13-forgotten-candidates", "inspect-forgotten")
        probe("14-relearn", "relearn")
        probe("15-export-relearned", "export")
        relearned = export_records(out / "15-export-relearned.txt")
        learned_counts(relearned)
        report["checks"].append("forget removes positive learned record; underlying candidates can return and a later commit relearns it")
        invalid = out / "invalid-snapshot.txt"
        invalid.write_text("This is not a Rime user database snapshot.\n", encoding="ascii")
        probe("16-reject-corrupt", "reject-invalid-restore", invalid)
        probe("17-export-after-failure", "export")
        if relearned != export_records(out / "17-export-after-failure.txt"):
            raise ValueError("Rejected snapshot changed the original logical dictionary")
        owned_db = user / (name + ".userdb")
        if not owned_db.is_dir() or owned_db.is_symlink() or owned_db.resolve().parent != user:
            raise ValueError("Refusing to remove an unowned test DB")
        shutil.rmtree(owned_db)
        report["removed_only_owned_test_db"] = str(owned_db)
        probe("18-restore-empty", "restore", snapshot)
        probe("19-export-restored", "export")
        if before != export_records(out / "19-export-restored.txt"):
            raise ValueError("Recovery into empty owned DB did not restore logical backup records")
        probe("20-inspect-restored", "inspect")
        report["checks"].append("corrupt snapshot rejected without logical changes; owned empty DB recovered from frozen snapshot")
        report["source_after"] = {str(path): digest(path) for path in [*source_paths, *production_paths]}
        if report["source_after"] != report["source_before"]:
            raise ValueError("Bound source/header/library files changed during acceptance")
        report["rime_version"] = learned["rime_version"]
        report["learned_counts"] = learned_counts(before)
        report["passed"] = True
    except BaseException as failure:
        report["failure"] = {"type": type(failure).__name__, "message": str(failure)}
        raise
    finally:
        write_json(out / "report.json", report)
    print(json.dumps({"passed": True, "report": str(out / "report.json"), "checks": report["checks"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
