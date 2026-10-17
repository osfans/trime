#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build the real owned OpenCC lookup regression, serially and without Gradle.

Uses the production opencc-host wrapper and its exact lookup/resource overlays.
Requires CMake >=3.22, Ninja and a C++17 compiler. Use --require-gcc-major 16 for
the GCC16 warning regression. Every compile/configure warning rejects the run.
No JVM, emulator, system OpenCC library or original-submodule edit is required.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "app/src/main/jni"
if not __debug__:
    raise RuntimeError("Lookup control refuses optimized Python")


def binding(path):
    path = Path(path).resolve(strict=True)
    before = path.stat()
    with path.open("rb") as stream:
        value = hashlib.file_digest(stream, "sha256").hexdigest()
    after = path.stat()
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) != (
            after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns):
        raise RuntimeError("Input changed while hashing: " + str(path))
    return {"path": str(path), "bytes": after.st_size, "sha256": value}


def snapshot(directory):
    result = {}
    for path in sorted(directory.rglob("*")):
        if ".git" in path.relative_to(directory).parts:
            continue
        if path.is_symlink():
            raise RuntimeError("Unexpected source symlink: " + str(path))
        if path.is_file():
            result[str(path.relative_to(directory))] = binding(path)
    return result


def process(pid):
    raw = Path(f"/proc/{pid}/stat").read_text()
    fields = raw.rsplit(") ", 1)[1].split()
    return {"pid": pid, "ppid": int(fields[1]), "start": int(fields[19]), "pgid": int(fields[2])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--cmake", required=True)
    parser.add_argument("--compiler", default="c++")
    parser.add_argument("--ninja", default="ninja")
    parser.add_argument("--require-gcc-major", type=int)
    parser.add_argument("--baseline-darts-fixtures", type=Path,
                        help="Optional retained old-library fixture directory; compare actual fixed32 and legacy64 bytes")
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to((ROOT / "build").resolve())
    if out == (ROOT / "build").resolve():
        raise RuntimeError("Use a fresh child output directory")
    out.mkdir(parents=True, exist_ok=False)
    started = time.monotonic()
    deadline = started + 1800
    source = NATIVE / "OpenCC"
    adaptation = NATIVE / "cmake"
    fixture = Path(__file__).with_name("opencc_lookup_control.cpp")
    darts_fixture = Path(__file__).with_name("opencc_darts_control.cpp")
    tools = {name: Path(shutil.which(value) or value).resolve(strict=True)
             for name, value in (("cmake", args.cmake), ("compiler", args.compiler), ("ninja", args.ninja), ("nm", "nm"))}
    baseline_files = [] if args.baseline_darts_fixtures is None else [
        args.baseline_darts_fixtures.resolve(strict=True) / name
        for name in ("built-fixed32.ocd", "legacy64.ocd")]
    inputs = {str(path): binding(path) for path in [Path(__file__).resolve(), fixture, darts_fixture, *tools.values(), *baseline_files]}
    source_before = snapshot(source)
    adaptation_before = snapshot(adaptation)
    report = {"passed": False, "scope": __doc__, "workers": 1, "steps": [],
              "inputs_before": inputs, "source_before": source_before,
              "adaptation_before": adaptation_before, "original_negative_control_rerun": False,
              "source_unchanged": False, "global_timeout_seconds": 1800}
    owned = {}
    signals = []
    observed_pgroups = set()
    memory = []
    cleanup_errors = []
    active = None
    primary_error = None

    def save():
        (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")

    def discover():
        table = {}
        for directory in Path("/proc").iterdir():
            if not directory.name.isdecimal():
                continue
            try:
                row = process(int(directory.name))
                table[row["pid"]] = row
            except (OSError, ValueError, IndexError):
                continue
        changed = True
        while changed:
            changed = False
            for pid, row in table.items():
                if (pid, row["start"]) in owned:
                    observed_pgroups.add(row["pgid"])
                    continue
                parent = table.get(row["ppid"])
                if parent and (parent["pid"], parent["start"]) in owned:
                    try:
                        current, live_parent = process(pid), process(parent["pid"])
                    except FileNotFoundError:
                        continue
                    if (current == row and live_parent["start"] == parent["start"] and
                            current["ppid"] == live_parent["pid"]):
                        owned[(pid, row["start"])] = row
                        observed_pgroups.add(row["pgid"])
                        changed = True
        return table

    def live_owned():
        result = []
        for pid, start in owned:
            try:
                current = process(pid)
                if current["start"] == start:
                    result.append(current)
            except FileNotFoundError:
                pass
        return result

    def check_time():
        available = int(next(line.split()[1] for line in Path("/proc/meminfo").read_text().splitlines()
                             if line.startswith("MemAvailable:")))
        memory.append({"epoch": time.time(), "available_kib": available})
        if available < 1048576 or time.monotonic() >= deadline:
            raise RuntimeError("Lookup control memory1GiB floor or total1800s deadline")

    def run(argv, name, timeout):
        nonlocal active
        check_time()
        limit = min(deadline, time.monotonic() + timeout)
        stdout, stderr = out / (name + ".stdout"), out / (name + ".stderr")
        row = {"argv": list(map(str, argv)), "actual_exit": None}
        report["steps"].append(row)
        try:
            with stdout.open("xb") as so, stderr.open("xb") as se:
                active = subprocess.Popen(row["argv"], cwd=out, env=env, stdout=so, stderr=se,
                                          start_new_session=True)
                token = process(active.pid)
                if token["ppid"] != os.getpid():
                    raise RuntimeError("Popen child identity differs")
                owned[(token["pid"], token["start"])] = token
                observed_pgroups.add(token["pgid"])
                row["pid_start"] = token
                while active.poll() is None:
                    discover()
                    check_time()
                    if time.monotonic() >= limit:
                        raise TimeoutError(name + " deadline")
                    if stdout.stat().st_size + stderr.stat().st_size > 32 * 1024 * 1024:
                        raise RuntimeError("Command logs exceed32MiB")
                    time.sleep(.1)
                row["actual_exit"] = active.wait()
                active = None
                discover()
                if row["actual_exit"] != 0 or time.monotonic() >= limit:
                    raise RuntimeError(name + " actual exit/deadline failure")
            text = stdout.read_text() + stderr.read_text()
            if re.search(r"(?mi)(?:^\s*(?:CMake (?:Deprecation )?Warning\b|warning:|w:|WARNING\b)|:\s*warning\b)", text):
                raise RuntimeError(name + " emitted a real warning; original retained")
            check_time()
            return stdout.read_text()
        finally:
            if stdout.exists():
                row["stdout"] = binding(stdout)
            if stderr.exists():
                row["stderr"] = binding(stderr)
            save()

    def quote(path):
        value = str(path)
        if "]==]" in value:
            raise ValueError("Unsupported CMake bracket delimiter in path")
        return "[==[" + value + "]==]"

    env = dict(os.environ, CMAKE_BUILD_PARALLEL_LEVEL="1")
    for key in ("CFLAGS", "CXXFLAGS", "CPPFLAGS", "LDFLAGS", "CPATH", "CPLUS_INCLUDE_PATH",
                "C_INCLUDE_PATH", "LIBRARY_PATH", "LD_LIBRARY_PATH", "LD_PRELOAD"):
        env.pop(key, None)
    save()
    try:
        copied_fixture = out / fixture.name
        shutil.copyfile(fixture, copied_fixture)
        assert binding(copied_fixture)["sha256"] == inputs[str(fixture)]["sha256"]
        copied_darts = out / darts_fixture.name
        shutil.copyfile(darts_fixture, copied_darts)
        assert binding(copied_darts)["sha256"] == inputs[str(darts_fixture)]["sha256"]
        hook = out / "lookup-target.cmake"
        hook.write_text("""function(trime_add_lookup_control)
  if(NOT TARGET libopencc)
    message(FATAL_ERROR "The production host wrapper did not create libopencc")
  endif()
  get_target_property(opencc_source libopencc SOURCE_DIR)
  get_target_property(opencc_binary libopencc BINARY_DIR)
  add_executable(opencc_lookup_control """ + quote(copied_fixture) + """)
  set_target_properties(opencc_lookup_control PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
  target_compile_options(libopencc PRIVATE -O3 -Wall -Wextra -Werror)
  target_compile_options(opencc_lookup_control PRIVATE -O3 -Wall -Wextra -Werror)
  target_include_directories(opencc_lookup_control PRIVATE "${opencc_source}" "${opencc_binary}")
  target_link_libraries(opencc_lookup_control PRIVATE libopencc)
  add_executable(opencc_darts_control """ + quote(copied_darts) + """)
  set_target_properties(opencc_darts_control PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
  target_compile_options(opencc_darts_control PRIVATE -O3 -Wall -Wextra -Werror)
  target_include_directories(opencc_darts_control PRIVATE "${opencc_source}" "${opencc_binary}")
  target_link_libraries(opencc_darts_control PRIVATE libopencc)
endfunction()
cmake_language(DEFER CALL trime_add_lookup_control)
""")
        build = out / "build"
        run([tools["cmake"], "-S", adaptation / "opencc-host", "-B", build, "-G", "Ninja",
             "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_CXX_COMPILER=" + str(tools["compiler"]),
             "-DCMAKE_MAKE_PROGRAM=" + str(tools["ninja"]), "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON",
             "-DTRIME_OPENCC_SOURCE_DIR=" + str(source),
             "-DCMAKE_PROJECT_trime_opencc_data_INCLUDE=" + str(hook),
             "-Werror=dev", "-Werror=deprecated"], "configure", 180)
        compiler_files = list((build / "CMakeFiles").glob("*/CMakeCXXCompiler.cmake"))
        assert len(compiler_files) == 1
        compiler_info = compiler_files[0].read_text()
        compiler_id = re.findall(r'set\(CMAKE_CXX_COMPILER_ID "([^"]+)"\)', compiler_info)
        version = re.findall(r'set\(CMAKE_CXX_COMPILER_VERSION "([^"]+)"\)', compiler_info)
        assert len(compiler_id) == len(version) == 1
        report["actual_compiler"] = {"id": compiler_id[0], "version": version[0],
                                      "cmake_record": binding(compiler_files[0])}
        if args.require_gcc_major is not None:
            assert compiler_id == ["GNU"] and int(version[0].split(".")[0]) == args.require_gcc_major
        run([tools["cmake"], "--build", build, "--target", "opencc_lookup_control", "opencc_darts_control",
             "--parallel", "1", "--verbose"], "build", 1200)
        executable = build / "opencc_lookup_control"
        compile_commands = build / "compile_commands.json"
        rows = json.loads(compile_commands.read_text())
        fixture_rows = [row for row in rows if Path(row["file"]).resolve() == copied_fixture]
        textdict_rows = [row for row in rows if Path(row["file"]).name == "TextDict.cpp"]
        darts_rows = [row for row in rows if Path(row["file"]).resolve() == copied_darts]
        assert len(fixture_rows) == len(textdict_rows) == len(darts_rows) == 1
        adapted_source = build / "trime-deps/OpenCC/src/TextDict.cpp"
        assert Path(textdict_rows[0]["file"]).resolve() == adapted_source
        assert "new NoValueDictEntry(std::string(word, len))" not in adapted_source.read_text()
        assert "const std::string_view key(word, len);" in adapted_source.read_text()
        for row in fixture_rows + textdict_rows + darts_rows:
            assert all(flag in row["command"].split() for flag in ("-O3", "-Wall", "-Wextra", "-Werror"))
        output = run([executable], "test", 30)
        lines = [line for line in output.splitlines() if line.startswith("{")]
        assert len(lines) == 1
        result = json.loads(lines[0])
        assert result["passed"] and result["real_library"] and result["linear_oracle"]
        assert result["dictionaries"] == 4 and result["queries"] > 1000
        assert result["groups"]["all_256_single_bytes"] == 256
        assert result["groups"]["present_empty_key"] == result["groups"]["absent_empty_key"] == 1
        darts_executable = build / "opencc_darts_control"
        fixture_dir = out / "darts-fixtures"
        darts_output = run([darts_executable, fixture_dir], "darts-test", 30)
        darts_lines = [line for line in darts_output.splitlines() if line.startswith("{")]
        assert len(darts_lines) == 1
        darts_result = json.loads(darts_lines[0])
        assert darts_result["passed"] and darts_result["real_library"]
        assert darts_result["valid_readers"] == 4 and darts_result["rejected_readers"] == 24
        assert darts_result["deep_prefix_keys"] == 65
        wire_files = {path.name: binding(path) for path in sorted(fixture_dir.iterdir())}
        baseline_equal = None
        if baseline_files:
            baseline_equal = all(path.read_bytes() == (fixture_dir / path.name).read_bytes()
                                 for path in baseline_files)
            assert baseline_equal, "Private namespace changed actual old-library wire bytes"
        archive = build / "OpenCC/src/libopencc.a"
        symbols = run([tools["nm"], "-C", "--defined-only", archive], "darts-symbols", 30)
        private_symbols = [line for line in symbols.splitlines() if " TrimeOpenccDarts::" in line]
        original_symbols = [line for line in symbols.splitlines() if re.search(r"\bDarts::", line)]
        assert private_symbols and not original_symbols, "OpenCC Darts symbols were not isolated"
        report.update(observed=result, executable=binding(executable), fixture_copy=binding(copied_fixture),
                      adapted_textdict=binding(adapted_source), compiler_commands=binding(compile_commands),
                      actual_fixture_compile=fixture_rows[0], actual_textdict_compile=textdict_rows[0],
                      production_wrapper=binding(adaptation / "opencc-host/CMakeLists.txt"),
                      generated_hook=binding(hook), darts_observed=darts_result,
                      darts_executable=binding(darts_executable), darts_fixture_copy=binding(copied_darts),
                      darts_wire_files=wire_files, baseline_darts_wire_equal=baseline_equal,
                      opencc_archive=binding(archive), private_darts_symbols=private_symbols,
                      original_darts_symbols=original_symbols, actual_darts_compile=darts_rows[0], passed=True)
    except BaseException:
        primary_error = traceback.format_exc()
        report["error"] = primary_error
        report["passed"] = False
    finally:
        try:
            discover()
            for sig in (signal.SIGTERM, signal.SIGKILL):
                for row in live_owned():
                    try:
                        current = process(row["pid"])
                        if current["start"] == row["start"]:
                            os.kill(row["pid"], sig)
                            signals.append(dict(row, signal=sig.name))
                    except ProcessLookupError:
                        pass
                until = time.monotonic() + 5
                while live_owned() and time.monotonic() < until:
                    if active is not None and active.poll() is not None:
                        active.wait()
                        active = None
                    time.sleep(.1)
                    discover()
            if active is not None:
                active.wait(timeout=5)
                active = None
            table = discover()
            residual = live_owned()
            unknown = [row for row in table.values() if row["pgid"] in observed_pgroups
                       and (row["pid"], row["start"]) not in owned]
            report.update(signals=signals, residual_owned=residual, unknown_group_members=unknown)
            if signals or residual or unknown:
                report["passed"] = False
        except BaseException:
            cleanup_errors.append(traceback.format_exc())
            report["passed"] = False
        try:
            report["inputs_after"] = {path: binding(path) for path in inputs}
            report["source_after"] = snapshot(source)
            report["adaptation_after"] = snapshot(adaptation)
            report["source_unchanged"] = (report["inputs_after"] == inputs and
                report["source_after"] == source_before and report["adaptation_after"] == adaptation_before)
            check_time()
            report["passed"] &= report["source_unchanged"]
        except BaseException:
            report["verification_error"] = traceback.format_exc()
            report["passed"] = False
        report.update(cleanup_errors=cleanup_errors, memory=memory,
                      duration_seconds=time.monotonic() - started,
                      global_deadline_respected=time.monotonic() < deadline)
        report["passed"] &= not cleanup_errors and report["global_deadline_respected"]
        save()
    print(json.dumps({"passed": report["passed"], "report": str(out / "report.json")}))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
