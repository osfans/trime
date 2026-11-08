#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run the real T9 controller against a separately built, pinned host librime."""

import argparse
import hashlib
import json
import pathlib
import shlex
import shutil
import subprocess


HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[4]
JNI = ROOT / "app/src/main/jni/librime_jni"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args, cwd=ROOT):
    return subprocess.check_output(["git", *args], cwd=cwd, text=True).strip()


def command(arguments, log, report, cwd=None, timeout=180):
    step = {"argv": arguments, "log": str(log), "timeout_seconds": timeout}
    report["commands"].append(step)
    with log.open("wb") as output:
        try:
            completed = subprocess.run(arguments, cwd=cwd, stdout=output,
                                       stderr=subprocess.STDOUT, timeout=timeout)
        except subprocess.TimeoutExpired:
            step["timed_out"] = True
            raise
    step["exit_code"] = completed.returncode
    step["log_sha256"] = sha256(log)
    if completed.returncode:
        raise RuntimeError(f"Command exited {completed.returncode}; see {log}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rime-build", type=pathlib.Path, required=True,
                        help="Completed host build of the repository's unmodified librime gitlink")
    parser.add_argument("--rime-source", type=pathlib.Path,
                        default=ROOT / "app/src/main/jni/librime",
                        help="Source of that gitlink, when the host build uses another clean checkout")
    parser.add_argument("--shared-data", type=pathlib.Path,
                        default=ROOT / "app/src/main/assets/shared",
                        help="Existing standard Rime resources; the T9 schema always comes from this repository")
    parser.add_argument("--double-schema", type=pathlib.Path, required=True,
                        help="Natural-code double_pinyin.schema.yaml, used only by the isolated test")
    parser.add_argument("--out", type=pathlib.Path, required=True,
                        help="New directory; existing directories are never reused")
    args = parser.parse_args()
    build = args.rime_build.resolve()
    rime_source = args.rime_source.resolve()
    expected = git("rev-parse", "HEAD:app/src/main/jni/librime")
    actual = git("rev-parse", "HEAD", cwd=rime_source)
    if actual != expected or git("status", "--porcelain", "--untracked-files=no", cwd=rime_source):
        raise RuntimeError("The librime source must match the unchanged repository gitlink")
    cache = {}
    for line in (build / "CMakeCache.txt").read_text().splitlines():
        if line and not line.startswith(("#", "//")) and "=" in line and ":" in line:
            key, value = line.split("=", 1)
            cache[key.split(":", 1)[0]] = value
    if pathlib.Path(cache["CMAKE_HOME_DIRECTORY"]).resolve() != rime_source.resolve():
        raise RuntimeError("Configure the host engine directly from this repository's librime source")
    database = build / "compile_commands.json"
    entries = json.loads(database.read_text())
    entry = next(item for item in entries if item["file"].endswith("/rime/context.cc"))
    arguments = entry.get("arguments") or shlex.split(entry["command"])
    flags = []
    index = 1
    while index < len(arguments):
        word = arguments[index]
        if word in ("-o", "-c", "-MF", "-MT", "-MQ"):
            index += 2
        elif word in ("-MD", "-MMD"):
            index += 1
        else:
            flags.append(word)
            index += 1
    library = build / "lib/librime.a"
    if not library.is_file():
        raise RuntimeError("Build the rime-static target before running these tests")
    dependencies = []
    for name in ("Boost_REGEX_LIBRARY_RELEASE", "Glog_LIBRARY", "YamlCpp_LIBRARY",
                 "LevelDb_LIBRARY", "Marisa_LIBRARY", "Opencc_LIBRARY", "Gflags_LIBRARY"):
        value = cache.get(name)
        if value and not value.endswith("-NOTFOUND"):
            dependencies.append(value)
    output = args.out.resolve()
    output.mkdir(parents=True, exist_ok=False)
    report = {
        "state": "running", "git_sha": git("rev-parse", "HEAD"),
        "worktree_dirty": bool(git("status", "--porcelain")),
        "librime_git_sha": actual,
        "librime_source": str(rime_source), "librime_build": str(build),
        "librime_source_tracked_clean": True,
        "librime_library_sha256": sha256(library),
        "compile_commands_sha256": sha256(database),
        "dependency_files": {path: sha256(pathlib.Path(path)) for path in dependencies},
        "inputs": {}, "commands": [],
        "scope": "Host controller and Rime integration; no Android, JNI runtime or real dictionary ranking acceptance",
    }
    try:
        for case in ("controller", "assist"):
            target = output / case
            shared = target / "shared"
            user = target / "user"
            shutil.copytree(HERE / "fixtures/common", shared)
            shutil.copytree(HERE / "fixtures" / case, shared, dirs_exist_ok=True)
            user.mkdir()
            for filename in ("luna_pinyin.schema.yaml", "pinyin.yaml", "symbols.yaml"):
                source = args.shared_data / filename
                shutil.copy2(source, shared / filename)
            shutil.copy2(ROOT / "app/src/main/assets/shared/luna_pinyin_t9.schema.yaml",
                         shared / "luna_pinyin_t9.schema.yaml")
            shutil.copy2(args.double_schema, shared / "double_pinyin.schema.yaml")
            source = HERE / f"{case}_test.cc"
            executable = target / "test"
            inputs = [source, JNI / "t9.cc", JNI / "t9.h", JNI / "t9_assist.cc",
                      JNI / "t9_assist.h", JNI / "helper-types.h", *shared.rglob("*")]
            report["inputs"][case] = {str(path): sha256(path) for path in inputs if path.is_file()}
            compile_command = [arguments[0], *flags, "-I" + str(JNI), str(source),
                               str(JNI / "t9.cc"), str(JNI / "t9_assist.cc"),
                               str(library), *dependencies, "-pthread", "-o", str(executable)]
            command(compile_command, target / "compile.log", report, cwd=entry["directory"])
            run_command = [str(executable), str(shared), str(user)]
            command(run_command, target / "run.log", report)
            # A separate process must reopen the user dictionary and verify canonical learning.
            recovery = [*run_command, "verify-learning"]
            command(recovery, target / "verify-learning.log", report)
        report["state"] = "passed"
    except BaseException:
        report["state"] = "failed"
        raise
    finally:
        (output / "result.json").write_text(json.dumps(report, indent=2) + "\n")
    print(f"PASS: controller, assistance and two process-restart learning checks; {output}")


if __name__ == "__main__":
    main()
