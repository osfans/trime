#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Owned same-source native tree and reviewable command plan.

This program never invokes CMake, a compiler, a test executable, Gradle, Java,
ADB or an emulator. The sibling runner executes the emitted commands serially
under its owned process/deadline/memory guardian and records real tool bindings.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

sys.dont_write_bytecode = True
if sys.flags.optimize:
    raise SystemExit("Preparation refuses optimized Python")

HERE = Path(__file__).resolve().parent
BOOST_SHA = "9bed76128d4e46755dbe818487788c6fceb6f72b378f4daa49b7e1e600d9088d"
PINS = {
    "app/src/main/jni/librime": "7bc3fb0005a03aff1c086611e4d52bb7f6444fdd",
    "app/src/main/jni/librime/deps/opencc": "025f371dc76b598d77384fbdab90c937471844d8",
}


def digest(path: Path) -> dict:
    h = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            size += len(block)
            h.update(block)
    return {"bytes": size, "sha256": h.hexdigest()}


def git(root: Path, *args: str) -> bytes:
    return subprocess.check_output(["git", "-C", str(root), *args], timeout=30)



def prepare_model_sources(native: Path, test_dir: Path) -> dict:
    """Isolate the pinned generic builder's names, preserving its wire algorithm.

    Production PredictDb validates the current bundled model SHA; it cannot
    verify a newly generated model. This host-only copy uses a separate class
    and metadata namespace, never a second definition of rime::PredictDb.
    """
    upstream = native / "librime-predict/src"
    expected = {
        "predict_db.h": "210b07c8b81437921a54d5cd11bf8d1d47044debe000e5497409e18e8e28df0f",
        "predict_db.cc": "459b1c7a889e3792e30cf19d874c9acfd9b7423203c4cd3d46e933539768fd19",
    }
    derived = test_dir / "model-tool"
    derived.mkdir()
    ledger = {}
    for name, sha in expected.items():
        original = upstream / name
        assert digest(original)["sha256"] == sha, "Generic model source pin changed"
        text = original.read_text()
        marker = "namespace rime {"
        closing = "}  // namespace rime"
        isolated = "namespace trime_model_tool {\nusing namespace ::rime;"
        isolated_end = "}  // namespace trime_model_tool"
        assert text.count(marker) == text.count(closing) == 1
        transformed = text.replace(marker, isolated, 1).replace(closing, isolated_end, 1)
        if name.endswith(".h"):
            assert transformed.count("RIME_PREDICT_DB_H_") == 3
            transformed = transformed.replace("RIME_PREDICT_DB_H_", "TRIME_HOST_MODEL_PREDICT_DB_H_")
        if name.endswith(".cc"):
            unused = "  const size_t kReservedSize = 1024;\n"
            comment = "  // The upstream reserved-size local was unused.\n"
            assert text.count(unused) == text.count("kReservedSize") == 1
            transformed = transformed.replace(unused, comment, 1)
        restored = transformed.replace(isolated, marker, 1).replace(isolated_end, closing, 1)
        if name.endswith(".cc"):
            restored = restored.replace(comment, unused, 1)
        if name.endswith(".h"):
            restored = restored.replace("TRIME_HOST_MODEL_PREDICT_DB_H_", "RIME_PREDICT_DB_H_")
        assert restored == text, "Model wire/lookup algorithm was changed"
        target = derived / name
        target.write_text(transformed)
        ledger[name] = {"original": {"path": str(original), **digest(original)},
                        "generated": {"path": str(target), **digest(target)},
                        "inverse_exact": True}
    original = test_dir / "build_predict_db.cc"
    text = original.read_text()
    changes = (("rime::predict::", "trime_model_tool::predict::"),
               ("rime::PredictDb", "trime_model_tool::PredictDb"))
    transformed = text
    for old, new in changes:
        assert old in transformed and new not in transformed
        transformed = transformed.replace(old, new)
    restored = transformed
    for old, new in reversed(changes):
        restored = restored.replace(new, old)
    assert restored == text, "Original bounded model checks were changed"
    target = derived / original.name
    target.write_text(transformed)
    ledger[original.name] = {"original": {"path": str(original), **digest(original)},
                             "generated": {"path": str(target), **digest(target)},
                             "inverse_exact": True}
    # Preserve the complete original license beside the derived source.
    license_source = native / "librime-predict/LICENSE"
    shutil.copyfile(license_source, derived / "LICENSE")
    ledger["LICENSE"] = {"original": {"path": str(license_source), **digest(license_source)},
                         "generated": {"path": str(derived / "LICENSE"), **digest(derived / "LICENSE")},
                         "inverse_exact": True}
    return ledger


def source_status(repo: Path, allow_model_source_change: bool) -> bytes:
    status = git(repo, "status", "--porcelain", "--untracked-files=no", "-z")
    permitted = b" M script/quality/t11/prediction.tsv\0"
    assert not status or (allow_model_source_change and status == permitted), (
        "Require clean tracked source, or explicitly allow only an unstaged prediction.tsv edit")
    return status


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--boost-archive", required=True, type=Path)
    parser.add_argument("--cmake", required=True, type=Path)
    parser.add_argument("--c-compiler", required=True, type=Path)
    parser.add_argument("--cxx-compiler", required=True, type=Path)
    parser.add_argument("--ninja", required=True, type=Path)
    parser.add_argument("--workers", type=int, choices=(1, 2), default=1)
    parser.add_argument("--build-model-tool", action="store_true")
    parser.add_argument("--allow-model-source-change", action="store_true")
    args = parser.parse_args()
    assert not args.allow_model_source_change or args.build_model_tool
    repo = args.repo.resolve(strict=True)
    out = args.out.resolve()
    out.relative_to(repo / "build/t11")
    assert out != repo / "build/t11", "Use a fresh child directory"
    assert not out.exists(), "Old outputs must not be overwritten"
    for relative, expected in PINS.items():
        assert git(repo / relative, "rev-parse", "HEAD").decode().strip() == expected
    head = git(repo, "rev-parse", "HEAD").decode().strip()
    status_before = source_status(repo, args.allow_model_source_change)
    submodules_before = git(repo, "submodule", "status", "--recursive")
    assert all(line.startswith(b" ") for line in submodules_before.splitlines())

    archive = args.boost_archive.resolve(strict=True)
    assert archive.name == "boost-1.92.0.tar.xz"
    archive_before = digest(archive)
    assert archive_before["sha256"] == BOOST_SHA
    tools = {}
    for name in ("cmake", "c_compiler", "cxx_compiler", "ninja"):
        path = getattr(args, name).resolve(strict=True)
        assert path.is_file() and os.access(path, os.X_OK)
        tools[name] = {"path": str(path), **digest(path)}

    raw_paths = git(repo, "ls-files", "--recurse-submodules", "-z", "--", "app/src/main/jni")
    relative_paths = sorted({Path(p.decode()) for p in raw_paths.split(b"\0") if p})
    extra_paths = [
        Path("app/src/main/assets/shared/trime_predict.db"),
        Path("app/src/main/assets/shared/trime_predict.db.json"),
        *(Path("script/quality/t11") / p for p in (
            "run_native_prediction_tests.py", "prediction_native_test.cc", "cache_recovery_native_test.cc",
            "ranking_boundary_test.h", "build_predict_db.py", "build_predict_db.cc",
            "prediction.tsv")),
    ]
    relative_paths = sorted(set(relative_paths + extra_paths))
    before = {}
    for relative in relative_paths:
        assert not relative.is_absolute() and ".." not in relative.parts
        source = repo / relative
        assert source.is_file() and not source.is_symlink(), str(source)
        before[str(relative)] = digest(source)

    out.mkdir(parents=True)
    report = {"preparation_only": True, "prepared": False, "source_head": head,
              "pins": PINS, "tools": tools,
              "model_builder_requested": args.build_model_tool,
              "allow_model_source_change": args.allow_model_source_change,
              "tracked_status_before": status_before.decode(), "archive": {"path": str(archive), **archive_before},
              "inputs_before": before, "build_started": False, "test_started": False}
    report_path = out / "preparation.json"
    try:
        source_root = out / "source"
        native = source_root / "app/src/main/jni"
        binary = out / "binary"
        binary.mkdir()
        for relative in relative_paths:
            target = source_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(repo / relative, target)
            assert digest(target) == before[str(relative)]

        # Nothing in the real native tree is added to CMake. Rime.cmake may
        # create its four known plugin links only inside this owned copy.
        host = native / "host-probe"
        host.mkdir()
        for name in ("options.cmake", "probe.cmake"):
            shutil.copyfile(HERE / name, host / name)

        # Exact real archive, not a fabricated cache sentinel. The production
        # Boost module sees an existing verified archive and cannot download.
        for target in (native / archive.name, binary / archive.name):
            shutil.copyfile(archive, target)
            assert digest(target) == archive_before
        with tarfile.open(archive, "r:xz") as bundle:
            members = bundle.getmembers()
            assert members and all(
                Path(member.name).parts and
                Path(member.name).parts[0] == "boost-1.92.0" and
                not Path(member.name).is_absolute() and
                ".." not in Path(member.name).parts
                for member in members)
            # Public Python extraction filter rejects unsafe links/devices.
            bundle.extractall(native, members=members, filter="data")
        extracted = native / "boost-1.92.0"
        assert extracted.is_dir() and not (native / "boost").exists()
        extracted.rename(native / "boost")

        original_top = (native / "CMakeLists.txt").read_text()
        marker = "\ninclude(Boost)\n"
        end = "add_subdirectory(librime_jni)"
        assert original_top.count(marker) == original_top.count(end) == 1
        replacement = ('\ninclude("${CMAKE_CURRENT_SOURCE_DIR}/host-probe/options.cmake")'
                       '\ninclude(Boost)\n')
        modified = original_top.replace(marker, replacement, 1).replace(
            end, 'include("${CMAKE_CURRENT_SOURCE_DIR}/host-probe/probe.cmake")', 1)
        assert modified.replace(replacement, marker, 1).replace(
            'include("${CMAKE_CURRENT_SOURCE_DIR}/host-probe/probe.cmake")', end, 1) == original_top
        (native / "CMakeLists.txt").write_text(modified)

        # Reuse the existing brace-balanced production ranking bridge verbatim.
        # Import does not execute its guarded main or compile/test anything.
        runner = source_root / "script/quality/t11/run_native_prediction_tests.py"
        spec = importlib.util.spec_from_file_location("owned_prediction_bridge", runner)
        assert spec is not None and spec.loader is not None
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        test_dir = runner.parent
        bridge = test_dir / "ranking_boundary_bridge.h"
        module.ranking_bridge(bridge)
        model_sources = prepare_model_sources(native, test_dir) if args.build_model_tool else {}

        scratch = out / "runtime"
        scratch.mkdir()
        (scratch / "default.yaml").write_text("schema_list: []\n")
        model = source_root / "app/src/main/assets/shared/trime_predict.db"
        shutil.copyfile(model, scratch / model.name)
        commands = [
            {"stage": "configure", "argv": [str(args.cmake.resolve()), "-S", str(native),
             "-B", str(binary), "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release",
             "-DCMAKE_MAKE_PROGRAM=" + str(args.ninja.resolve()),
             "-DCMAKE_C_COMPILER=" + str(args.c_compiler.resolve()),
             "-DCMAKE_CXX_COMPILER=" + str(args.cxx_compiler.resolve()),
             "-Werror=dev", "-Werror=deprecated",
             "-DTRIME_HOST_BUILD_MODEL_TOOL=" + ("ON" if args.build_model_tool else "OFF")],
             "environment": {"CMAKE_BUILD_PARALLEL_LEVEL": str(args.workers),
                             "GIT_CEILING_DIRECTORIES": str(source_root)}},
            {"stage": "build-original-384-fixture", "argv": [str(args.cmake.resolve()), "--build",
             str(binary), "--target", "prediction-native-test",
             *(["prediction-model-native"] if args.build_model_tool else []), "--parallel", str(args.workers), "--verbose"],
             "environment": {"CMAKE_BUILD_PARALLEL_LEVEL": str(args.workers),
                             "GIT_CEILING_DIRECTORIES": str(source_root)}},
            {"stage": "ELF-and-link-input-audit-before-execution", "required": True,
             "details": "Verify map/compile_commands/generated build_config/static rime provenance; reject system rime/OpenCC/marisa/glog/yaml/leveldb or external plugins. Do not execute if any check fails."},
            {"stage": "run-original-384-fixture", "argv": [str(binary / "host-bin/prediction-native-test"),
             str(model), str(scratch)], "timeout_seconds": 60,
             "expected_exact_stdout_line": "PASS 384 actual native prediction checks"},
        ]
        (out / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
        report.update({"source_root": str(source_root), "native": str(native), "binary": str(binary),
                       "workers": args.workers,
                       "generic_model_sources": model_sources,
                       "git_discovery_ceiling": str(source_root),
                       "generated_bridge": digest(bridge), "generated_top": digest(native / "CMakeLists.txt"),
                       "commands": digest(out / "commands.json"), "assertion_sources_unchanged": all(
                           digest(test_dir / p) == before[str(Path("script/quality/t11") / p)]
                           for p in ("prediction_native_test.cc", "ranking_boundary_test.h")),
                       "prepared": True})
    except BaseException as error:
        report["prepared"] = False
        report["error"] = repr(error)
        raise
    finally:
        report["inputs_after"] = {str(p): digest(repo / p) for p in relative_paths}
        report["source_inputs_unchanged"] = before == report["inputs_after"]
        report["archive_unchanged"] = digest(archive) == archive_before
        report["head_unchanged"] = git(repo, "rev-parse", "HEAD").decode().strip() == head
        report["submodules_unchanged"] = git(repo, "submodule", "status", "--recursive") == submodules_before
        report["tracked_status_after"] = source_status(repo, args.allow_model_source_change).decode()
        report["tracked_status_unchanged"] = report["tracked_status_after"] == report["tracked_status_before"]
        report["prepared"] &= all(report[key] for key in (
            "source_inputs_unchanged", "archive_unchanged", "head_unchanged", "submodules_unchanged", "tracked_status_unchanged"))
        report_path.write_text(json.dumps(report, indent=2) + "\n")
    if not report["prepared"]:
        raise SystemExit("Preparation inputs drifted; do not build")
    print(json.dumps({"prepared": True, "built_or_tested": False, "report": str(report_path)}))


if __name__ == "__main__":
    main()
