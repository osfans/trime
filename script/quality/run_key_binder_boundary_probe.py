#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compile only the real patched paging method against app-owned host stubs.

Does not compile librime/JNI or invoke Gradle, a JVM, ASAN or an emulator.
This is a functional boundary regression, not reproduction of an old crash.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import traceback

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "app/src/main/jni"


def binding(path):
    path = Path(path)
    data = path.read_bytes()
    return {"path": str(path), "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest()}


def method(source):
    signature = "bool KeyBinder::ReinterpretPagingKey(const KeyEvent& key_event) {"
    if source.count(signature) != 1:
        raise ValueError("Re-audit missing/duplicate real paging method")
    start = source.index(signature)
    # This exact audited final method is followed by the namespace terminator.
    suffix = "\n}\n\n}  // namespace rime"
    end = source.find(suffix, start)
    if end < 0 or source.find(suffix, end + 1) >= 0:
        raise ValueError("Re-audit paging method/namespace boundary")
    return source[start:end + 2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--cmake", required=True)
    parser.add_argument("--compiler", default="c++")
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to((ROOT / "build").resolve())
    out.mkdir(parents=True, exist_ok=False)
    source = NATIVE / "librime/src/rime/gear/key_binder.cc"
    third = NATIVE / "cmake/ThirdPartyCmake.cmake"
    overlay = NATIVE / "cmake/RimeKeyBinderCompat.cmake"
    fixture = Path(__file__).with_name("key_binder_boundary_fixture.cpp")
    cmake = Path(shutil.which(args.cmake) or args.cmake).resolve(strict=True)
    compiler = Path(shutil.which(args.compiler) or args.compiler).resolve(strict=True)
    inputs = [Path(__file__).resolve(), source, third, overlay, fixture, cmake, compiler]
    before = [binding(path) for path in inputs]
    report = {"scope": __doc__, "steps": [], "inputs_before": before,
              "passed": False, "old_crash_reproduced": False,
              "production_native_built": False}
    started = time.monotonic()

    def run(command, name, timeout):
        if time.monotonic() - started >= 90:
            raise TimeoutError("Paging probe total90s exceeded before command")
        value = subprocess.run(command, cwd=ROOT, capture_output=True,
                               timeout=min(timeout, 90 - (time.monotonic() - started)))
        stdout, stderr = out / (name + ".stdout"), out / (name + ".stderr")
        stdout.write_bytes(value.stdout)
        stderr.write_bytes(value.stderr)
        report["steps"].append({"command": list(map(str, command)), "actual_exit": value.returncode,
                                "stdout": binding(stdout), "stderr": binding(stderr)})
        if value.returncode != 0:
            raise RuntimeError(f"{name} failed, actual_exit={value.returncode}")
        if time.monotonic() - started > 90:
            raise TimeoutError("Paging probe command finished after total90s")
        return value.stdout

    try:
        copied = out / "key_binder-patched.cc"
        shutil.copyfile(source, copied)
        script = out / "apply-production-overlay.cmake"
        script.write_text(f'include("{third.as_posix()}")\n'
                          f'include("{overlay.as_posix()}")\n'
                          f'trime_adapt_key_binder_ascii("{copied.as_posix()}")\n')
        run([str(cmake), "-Werror=dev", "-Werror=deprecated", "-P", str(script)], "overlay", 30)
        real_method = method(copied.read_text())
        include = out / "key_binder_method.inc"
        include.write_text("namespace rime {\n" + real_method + "\n}  // namespace rime\n")
        report["extracted_real_method"] = binding(include)
        report["patched_whole_source"] = binding(copied)
        binary = out / "key-binder-boundary-probe"
        run([str(compiler), "-std=c++17", "-O0", "-g0", "-Wall", "-Wextra", "-Werror",
             "-I", str(out), str(fixture), "-o", str(binary)], "compile", 30)
        report["binary"] = binding(binary)
        observed = json.loads(run([str(binary)], "cases", 10).decode())
        if observed.get("passed") is not True or observed.get("cases") != 1153 or observed.get("assertions") != 6917:
            raise RuntimeError(f"Unexpected complete native result: {observed!r}")
        report["native_result"] = observed
        report["passed"] = True
    except BaseException:
        report["error"] = traceback.format_exc()
    finally:
        report["inputs_after"] = [binding(path) for path in inputs]
        report["inputs_unchanged"] = before == report["inputs_after"]
        report["elapsed_seconds"] = time.monotonic() - started
        report["passed"] = report["passed"] and report["inputs_unchanged"] and report["elapsed_seconds"] <= 90
        (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"passed": report["passed"], "report": binding(out / "report.json")}, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
