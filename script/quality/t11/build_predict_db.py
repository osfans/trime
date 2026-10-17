#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build/check the original, bounded offline librime-predict example model.

No Android/Gradle build is started. --write needs a host native builder; compile
it explicitly through --compile-native with a fresh same-source build directory
and the verified Boost archive; no private header/system-librime combination.
The pinned predict_db.cc creates the DB twice and reads every expected result.
Only identical, structurally valid images are published to the asset directory.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import struct
import stat
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
SOURCE = HERE / "prediction.tsv"
ASSET = ROOT / "app/src/main/assets/shared/trime_predict.db"
MANIFEST = ASSET.with_suffix(".db.json")
MAX_MODEL_BYTES = 64 * 1024
MAX_ROWS = 128
MAX_PER_QUERY = 32
MAX_TEXT_CHARS = 12
FORMAT = b"Rime::Predict/1.0"
HAN = re.compile(r"[\u3400-\u9fff]+\Z")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_source(raw: str) -> dict[str, list[tuple[str, int]]]:
    """Reject incomplete/duplicate/unbounded input before upstream's builder."""
    data: dict[str, list[tuple[str, int]]] = {}
    rows = 0
    for number, line in enumerate(raw.splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 3:
            raise ValueError(f"line {number}: expected three tab-separated fields")
        query, text, weight = fields
        if any(not HAN.fullmatch(value) or len(value) > MAX_TEXT_CHARS for value in (query, text)):
            raise ValueError(f"line {number}: only bounded Han text is allowed")
        if not re.fullmatch(r"[1-9][0-9]{0,5}", weight):
            raise ValueError(f"line {number}: expected a positive bounded integer weight")
        entries = data.setdefault(query, [])
        if any(candidate == text for candidate, _ in entries):
            raise ValueError(f"line {number}: duplicate next word for query")
        value = int(weight)
        if entries and entries[-1][1] <= value:
            raise ValueError(f"line {number}: weights must decrease for each query")
        entries.append((text, value))
        rows += 1
        if rows > MAX_ROWS or len(entries) > MAX_PER_QUERY:
            raise ValueError("example model exceeds the row/candidate budget")
    if not data:
        raise ValueError("example model is empty")
    return dict(sorted(data.items(), key=lambda item: item[0].encode("utf-8")))


def normalized_input(data: dict[str, list[tuple[str, int]]]) -> bytes:
    return "".join(f"{query}\t{text}\t{weight}\n" for query, entries in data.items()
                   for text, weight in entries).encode("utf-8")


def inspect_image(image: bytes, data: dict[str, list[tuple[str, int]]]) -> dict[str, int]:
    """Check bounds before the host native reader; never dereference raw offsets."""
    if not 52 < len(image) <= MAX_MODEL_BYTES:
        raise ValueError("model byte budget or header size is invalid")
    header, checksum, key_relative, key_units, value_relative, value_bytes = struct.unpack_from("<32sIiIiI", image)
    if header != FORMAT.ljust(32, b"\0") or checksum != 0:
        raise ValueError("expected exact Rime::Predict/1.0 format and unused checksum")
    key_start = 36 + key_relative
    value_start = 44 + value_relative
    if (key_start < 52 or key_start % 4 or key_units == 0 or
            key_units > MAX_MODEL_BYTES // 4 or
            key_start + key_units * 4 != value_start or value_bytes == 0 or
            value_start + value_bytes != len(image)):
        raise ValueError("invalid trie/string-table bounds")
    if not image[value_start:].startswith(b"We love Marisa."):
        raise ValueError("expected the native MARISA string table")
    units = struct.unpack_from(f"<{key_units}I", image, key_start)

    def unit_at(index: int) -> int:
        if not 0 <= index < key_units:
            raise ValueError("query traversal exceeds key trie")
        return units[index]

    def offset(unit: int) -> int:
        return (unit >> 10) << ((unit & (1 << 9)) >> 6)

    def lookup(query: str) -> int | None:
        position, unit = 0, unit_at(0)
        for label in query.encode("utf-8"):
            position ^= offset(unit) ^ label
            unit = unit_at(position)
            if unit & ((1 << 31) | 255) != label:
                return None
        if not unit & (1 << 8):
            return None
        return unit_at(position ^ offset(unit)) & 0x7fffffff

    arrays: list[tuple[int, int]] = []
    distinct_texts = len({text for entries in data.values() for text, _ in entries})
    for query, expected in data.items():
        start = lookup(query)
        if start is None or start < 52 or start % 4 or start + 4 > key_start:
            raise ValueError("query does not resolve to a bounded candidate array")
        count, = struct.unpack_from("<I", image, start)
        end = start + 4 + count * 8
        if count != len(expected) or not 0 < count <= MAX_PER_QUERY or end > key_start:
            raise ValueError("candidate array count/bounds differ from the source")
        arrays.append((start, end))
        for index, (_, weight) in enumerate(expected):
            string_id, actual_weight = struct.unpack_from("<If", image, start + 4 + index * 8)
            if string_id >= distinct_texts or not math.isfinite(actual_weight) or actual_weight != weight:
                raise ValueError("invalid candidate string ID/weight")
    cursor = 52
    for start, end in sorted(arrays):
        if start != cursor:
            raise ValueError("candidate arrays overlap or leave unexpected data")
        cursor = end
    if cursor != key_start or lookup("$") is not None or lookup("不存在的上下文") is not None:
        raise ValueError("unexpected candidate payload/start-of-session prediction")
    return {"query_count": len(data), "next_word_count": sum(map(len, data.values())),
            "key_units": key_units, "string_table_bytes": value_bytes}


def metadata(image: bytes, data: dict[str, list[tuple[str, int]]]) -> dict:
    return {"format": FORMAT.decode("ascii"), "license": "CC0-1.0",
            "provenance": "Original hand-authored offline examples; no user or collected corpus data",
            "source": "script/quality/t11/prediction.tsv", "source_sha256": digest(SOURCE.read_bytes()),
            "database": ASSET.name, "sha256": digest(image), "size": len(image),
            "max_model_bytes": MAX_MODEL_BYTES, "default_max_candidates": 5,
            "default_max_iterations": 1, "initial_prediction": False,
            **inspect_image(image, data)}


def file_binding(path: Path) -> dict:
    path = path.absolute()
    if path.resolve(strict=True) != path:
        raise ValueError(f"Artifact path must be canonical and not a symlink: {path}")
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1:
        raise ValueError(f"Expected an owned regular single-link artifact: {path}")
    def identity(value):
        return (value.st_dev, value.st_ino, value.st_mode, value.st_nlink,
                value.st_size, value.st_mtime_ns, value.st_ctime_ns)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as stream:
        if identity(os.fstat(stream.fileno())) != identity(before):
            raise ValueError("Artifact changed before opening")
        value = {"path": str(path), "bytes": before.st_size,
                 "sha256": hashlib.file_digest(stream, "sha256").hexdigest()}
        if identity(os.fstat(stream.fileno())) != identity(before):
            raise ValueError("Artifact changed while reading")
    if identity(path.lstat()) != identity(before):
        raise ValueError("Artifact changed after reading")
    return value


def audited_model_builder(result_path: Path) -> tuple[Path, dict]:
    """Consume a completed current-source host audit, never guess a system ABI."""
    result_path = result_path.resolve(strict=True)
    if not result_path.is_relative_to(ROOT / "build/t11"):
        raise ValueError("Same-source result must be in the owned build/t11 tree")
    file_binding(result_path)
    result = json.loads(result_path.read_text(encoding="utf-8"))
    if not (result.get("passed") is True and result.get("source_inputs_verified") is True and
            result.get("model_builder_requested") is True and result.get("actual_test_checks") == 384 and
            result.get("global_deadline_respected") is True and
            all(result.get(key) == [] for key in ("signals", "cleanup_errors", "verification_errors", "residual_owned", "unresolved"))):
        raise ValueError("Same-source native build/384 checks/cleanup did not complete")
    head = subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "HEAD"], text=True, timeout=30).strip()
    if result["preparation"]["source_head"] != head:
        raise ValueError("Same-source build belongs to a different source commit")
    status = subprocess.check_output(["git", "-C", str(ROOT), "status", "--porcelain", "--untracked-files=no", "-z"], timeout=30)
    permitted = b" M script/quality/t11/prediction.tsv\0"
    if status.decode() != result["tracked_status_before"] or (
            status and not (result.get("allow_model_source_change") is True and status == permitted)):
        raise ValueError("Source status differs from the completed same-source build")
    for path, row in result["source_before"].items():
        if not Path(path).resolve().is_relative_to(ROOT) or file_binding(Path(path)) != row:
            raise ValueError("Same-source entry/helper/assertion input changed")
    # In the explicitly allowed edited-TSV flow, status alone cannot bind the
    # text. Recheck every copied production/model input, including that TSV.
    for relative, expected in result["preparation"]["inputs_before"].items():
        path = ROOT / relative
        if not path.resolve().is_relative_to(ROOT):
            raise ValueError("Native input escapes the source checkout")
        observed = file_binding(path)
        if {key: observed[key] for key in ("bytes", "sha256")} != expected:
            raise ValueError(f"Copied production/model input changed: {relative}")
    audit = result["elf_audit"]
    model = audit.get("model_builder")
    if (audit.get("passed") is not True or not model or model.get("passed") is not True or
            model.get("isolated_namespace") != "trime_model_tool" or
            model.get("production_predict_symbols_absent") is not True):
        raise ValueError("The isolated same-source model target was not audited")
    expected = result_path.parent / "stage/binary/host-bin/prediction-model-native"
    builder = Path(model["binary"]["path"])
    if builder.resolve(strict=True) != expected.resolve(strict=True):
        raise ValueError("Model builder is outside its actual owned same-source output")
    artifacts = [model[key] for key in ("binary", "link_map", "actual_build_config")]
    artifacts += [audit[key] for key in ("compile_commands", "target_info")]
    artifacts += list(model["archives"].values())
    if set(model["archives"]) != {"rime", "glog", "yaml", "snappy", "leveldb", "marisa", "opencc"}:
        raise ValueError("The model builder does not bind the complete native archive graph")
    if set(model["generated_sources"]) != {"predict_db.h", "predict_db.cc", "build_predict_db.cc", "LICENSE"}:
        raise ValueError("Missing generic model source-isolation evidence")
    for item in model["generated_sources"].values():
        if item["inverse_exact"] is not True:
            raise ValueError("Generic model algorithms were not preserved")
        artifacts += [item["original"], item["generated"]]
    for row in artifacts:
        if not Path(row["path"]).resolve().is_relative_to(result_path.parent) or file_binding(Path(row["path"])) != row:
            raise ValueError("Same-source model binary/archive/source evidence changed")
    return builder, model["binary"]


def compile_native(output: Path, boost_archive: Path, compiler: str, allow_model_source_change: bool) -> tuple[Path, dict]:
    if output is None or boost_archive is None:
        raise ValueError("--compile-native requires --native-build-out and --boost-archive; system-librime compilation is not supported")
    # The public same-source runner owns preparation, serial compilation, ELF
    # audits, original384 checks and cleanup. It never writes the model assets.
    subprocess.run([sys.executable, "-B", str(HERE / "run_native_prediction_tests.py"),
                    "--out", str(output), "--boost-archive", str(boost_archive),
                    "--compiler", compiler, "--build-model-tool",
                    *(["--allow-model-source-change"] if allow_model_source_change else [])], check=True)
    return audited_model_builder(output / "result.json")


def run_builder(builder: Path, mode: str, target: Path, data: dict, expected_builder: dict) -> None:
    if file_binding(builder) != expected_builder:
        raise ValueError("Audited native model builder changed before execution")
    primary = None
    try:
        subprocess.run([str(builder.resolve()), mode, str(target.resolve())],
                       input=normalized_input(data), check=True, timeout=30)
    except BaseException as error:
        primary = error
        raise
    finally:
        try:
            if file_binding(builder) != expected_builder:
                raise ValueError("Audited native model builder changed during execution")
        except BaseException as changed:
            if primary is None:
                raise
            primary.add_note(f"Model builder post-execution binding also failed: {changed!r}")



def publish_model_files(model: Path, descriptor: Path, verify_sources) -> Path:
    """Replace the pair with retained recovery copies, not a two-file atomicity claim.

    Ordinary failures (including KeyboardInterrupt) restore every attempted
    replacement. A kill/power loss can interrupt the pair; before.json and the
    untouched backups remain available for manual recovery after --check fails.
    """
    recovery_root = ROOT / "build/t11"
    recovery_root.mkdir(parents=True, exist_ok=True)
    recovery = Path(tempfile.mkdtemp(prefix="model-recovery-", dir=recovery_root))
    entries, attempted = [], []
    try:
        for index, target in enumerate((ASSET, MANIFEST)):
            original = file_binding(target)
            image = target.read_bytes()
            if file_binding(target) != original:
                raise ValueError("Existing model pair changed during recovery backup")
            backup = recovery / (str(index) + "-" + target.name + ".bak")
            backup.write_bytes(image)
            binding = file_binding(backup)
            if (binding["bytes"], binding["sha256"]) != (original["bytes"], original["sha256"]):
                raise ValueError("Model recovery backup does not match its original")
            entries.append({"target": str(target), "backup": binding,
                            "mode": stat.S_IMODE(target.stat().st_mode),
                            "bytes": original["bytes"], "sha256": original["sha256"]})
        before = {"recovery_directory": str(recovery), "entries": entries,
                  "pair_atomic": False, "backups_retained": True}
        (recovery / "before.json").write_text(json.dumps(before, indent=2) + "\n", encoding="utf-8")
        # This is deliberately after the backups and before either replacement.
        verify_sources()
        for staged, entry in zip((model, descriptor), entries):
            # Record intent first, so an interrupt immediately after replace is
            # still covered even if Python has not observed its successful return.
            attempted.append(entry)
            os.replace(staged, entry["target"])
        (recovery / "outcome.json").write_text(json.dumps({
            "state": "committed", "pair_atomic": False, "backups_retained": True,
            "recovery_directory": str(recovery)}, indent=2) + "\n", encoding="utf-8")
        return recovery
    except BaseException as primary:
        rollback_errors = []
        for index, entry in enumerate(reversed(attempted)):
            try:
                backup = Path(entry["backup"]["path"])
                if file_binding(backup) != entry["backup"]:
                    raise ValueError("Recovery backup changed; refusing corrupt restoration")
                # Stage on the target filesystem even if build/t11 is mounted
                # elsewhere. The immutable backup is never consumed by replace.
                restored = model.parent / ("restore-" + str(index) + ".tmp")
                with restored.open("xb") as stream:
                    stream.write(backup.read_bytes())
                os.chmod(restored, entry["mode"])
                os.replace(restored, entry["target"])
                actual = file_binding(Path(entry["target"]))
                if (actual["bytes"], actual["sha256"]) != (entry["bytes"], entry["sha256"]):
                    raise ValueError("Restored model bytes do not match recovery backup")
            except BaseException as error:
                rollback_errors.append({"target": entry["target"], "error": repr(error),
                                        "backup": entry["backup"]})
        outcome = {"state": "not-published" if not attempted else ("rollback-failed" if rollback_errors else "rolled-back"),
                   "primary_error": repr(primary), "rollback_errors": rollback_errors,
                   "attempted_targets": [entry["target"] for entry in attempted],
                   "pair_atomic": False, "backups_retained": True,
                   "recovery_directory": str(recovery)}
        try:
            (recovery / "outcome.json").write_text(json.dumps(outcome, indent=2) + "\n", encoding="utf-8")
        except BaseException as report_error:
            primary.add_note(f"Recovery outcome recording also failed: {report_error!r}")
        primary.add_note(f"Model publication failed; recovery backups retained at {recovery}")
        if rollback_errors:
            primary.add_note(f"Model rollback incomplete: {rollback_errors!r}; inspect backups before rebuilding")
        raise


def write_model(builder: Path, data: dict, expected_builder: dict, result_path: Path, expected_source: dict) -> Path:
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="trime-predict-", dir=ASSET.parent) as directory:
        first, second = Path(directory) / "first.db", Path(directory) / "second.db"
        for target in (first, second):
            run_builder(builder, "--build", target, data, expected_builder)
            inspect_image(target.read_bytes(), data)
            run_builder(builder, "--verify", target, data, expected_builder)
        image = first.read_bytes()
        if image != second.read_bytes():
            raise ValueError("native builder output is not reproducible")
        descriptor = metadata(image, data)
        descriptor_path = Path(directory) / "model.json"
        descriptor_path.write_text(json.dumps(descriptor, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        def verify_publication_inputs():
            # Run after recovery copies are ready, immediately before publication.
            if file_binding(SOURCE) != expected_source or audited_model_builder(result_path) != (builder, expected_builder):
                raise ValueError("Model inputs changed before publication")
        return publish_model_files(first, descriptor_path, verify_publication_inputs)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-builder", type=Path)
    parser.add_argument("--compile-native", action="store_true", help="explicit serial same-source CMake build and audit")
    parser.add_argument("--native-build-out", type=Path, help="fresh build/t11 directory for the same-source native runner")
    parser.add_argument("--boost-archive", type=Path, help="verified production Boost archive for --compile-native")
    parser.add_argument("--allow-model-source-change", action="store_true", help="explicitly allow only an unstaged prediction.tsv edit during --compile-native")
    parser.add_argument("--same-source-result", type=Path, help="completed audited native runner result for --native-builder")
    parser.add_argument("--compiler", default="c++")
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--write", action="store_true", help="regenerate DB twice, verify, then publish assets")
    action.add_argument("--check", action="store_true", help="check the committed model (also the default)")
    args = parser.parse_args()
    if args.compile_native:
        if args.native_build_out is None or args.boost_archive is None:
            parser.error("--compile-native requires --native-build-out and --boost-archive; the old system-librime build is disabled")
        if args.native_builder is not None or args.same_source_result is not None:
            parser.error("--compile-native chooses its audited output; omit --native-builder/--same-source-result")
    elif args.native_build_out is not None or args.boost_archive is not None or args.allow_model_source_change:
        parser.error("--native-build-out/--boost-archive/--allow-model-source-change require --compile-native")
    elif (args.native_builder is None) != (args.same_source_result is None):
        parser.error("--native-builder and --same-source-result must be provided together")
    if args.write and not args.compile_native and args.native_builder is None:
        parser.error("--write requires an audited native builder or explicit --compile-native inputs")
    source_binding = file_binding(SOURCE)
    data = read_source(SOURCE.read_text(encoding="utf-8"))
    if file_binding(SOURCE) != source_binding:
        raise ValueError("Model source changed while reading")
    builder, binding = None, None
    if args.compile_native:
        builder, binding = compile_native(args.native_build_out, args.boost_archive, args.compiler, args.allow_model_source_change)
        args.same_source_result = args.native_build_out / "result.json"
    elif args.native_builder is not None:
        builder, binding = audited_model_builder(args.same_source_result)
        if builder.resolve() != args.native_builder.resolve():
            parser.error("--native-builder must match the completed same-source audit")
    if file_binding(SOURCE) != source_binding:
        raise ValueError("Model source changed during native build/audit")
    recovery = None
    if args.write:
        recovery = write_model(builder, data, binding, args.same_source_result, source_binding)
    image = ASSET.read_bytes()
    expected = metadata(image, data)
    if json.loads(MANIFEST.read_text(encoding="utf-8")) != expected:
        raise ValueError("committed model manifest differs from the actual source/database")
    if builder is not None:
        run_builder(builder, "--verify", ASSET, data, binding)
    print(json.dumps({"passed": True, **expected,
                      **({"recovery_directory": str(recovery)} if recovery is not None else {})}, ensure_ascii=False))


if __name__ == "__main__":
    main()
