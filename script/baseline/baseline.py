#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze APK identities and run Rime in disposable, separately owned directories."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, data):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def run(command, timeout=120, binary=False):
    env = dict(os.environ, JAVA_OPTS="-Xmx256m", JAVA_TOOL_OPTIONS="-Xmx256m")
    if env.get("JAVA_HOME"):
        env["PATH"] = str(Path(env["JAVA_HOME"]) / "bin") + os.pathsep + env.get("PATH", "")
    result = subprocess.run([str(x) for x in command], capture_output=True,
                            text=not binary, timeout=timeout, env=env)
    if result.returncode:
        error = result.stderr.decode(errors="replace") if binary else result.stderr
        raise RuntimeError(f"Command failed ({result.returncode}): {command[0]}: {error[:2200]}\n{error[-800:]}")
    return result.stdout


def tools(sdk):
    sdk = Path(sdk).resolve()
    versions = [p for p in (sdk / "build-tools").iterdir()
                if re.fullmatch(r"\d+(\.\d+)+", p.name)]
    build_tools = max(versions, key=lambda p: tuple(map(int, p.name.split("."))))
    return {"adb": sdk / "platform-tools/adb",
            "analyzer": sdk / "cmdline-tools/latest/bin/apkanalyzer",
            "signer": build_tools / "apksigner"}


def parse_manifest(xml):
    manifest = ET.fromstring(xml)
    app = manifest.find("application")
    return {"package_name": manifest.attrib["package"],
            "version_name": manifest.attrib.get(ANDROID + "versionName"),
            "version_code": int(manifest.attrib[ANDROID + "versionCode"]),
            "debuggable": app is not None and app.get(ANDROID + "debuggable") == "true"}


def parse_build_config(code):
    fields = {}
    for match in re.finditer(r'^\.field .*? ([A-Z_]+):Ljava/lang/String; = (".*")$', code, re.M):
        fields[match[1]] = json.loads(match[2])
    return fields


def tree_hashes(root):
    root = Path(root)
    if root.is_symlink():
        raise ValueError(f"Symlink not allowed as baseline data root: {root}")
    result = {}
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Symlink not allowed in baseline data: {path}")
        if path.is_file():
            result[path.relative_to(root).as_posix()] = digest(path)
    return result


def aggregate(data):
    return hashlib.sha256(json.dumps(data, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def inspect_apk(apk, sdk_tools):
    identity = parse_manifest(run([sdk_tools["analyzer"], "manifest", "print", apk]))
    signatures = run([sdk_tools["signer"], "verify", "--print-certs", apk])
    certs = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signatures)
    if not certs:
        raise ValueError("APK has no verified signer certificate")
    try:
        fields = parse_build_config(run([sdk_tools["analyzer"], "dex", "code", "--class",
                                        "com.osfans.trime.BuildConfig", apk]))
    except RuntimeError:
        fields = {}
    sha = fields.get("BUILD_COMMIT_HASH")
    if sha is not None and not re.fullmatch(r"[0-9a-f]{40,64}", sha):
        sha = None
    with zipfile.ZipFile(apk) as archive:
        resources = {}
        native = {}
        for info in archive.infolist():
            if not info.is_dir() and (info.filename.startswith("assets/shared/") or
                                       info.filename.startswith("lib/")):
                with archive.open(info) as stream:
                    value = hashlib.file_digest(stream, "sha256").hexdigest()
                (native if info.filename.startswith("lib/") else resources)[info.filename] = value
    identity.update({"apk_sha256": digest(apk), "apk_size_bytes": Path(apk).stat().st_size,
                     "signer_certificate_sha256": sorted(x.lower() for x in certs),
                     "git_sha": sha,
                     "git_sha_source": "APK BuildConfig" if sha else "unavailable (not inferred from filename/tag)",
                     "source_worktree_state": "unknown: not embedded in this APK",
                     "build_fields": fields,
                     "abis": sorted({name.split("/")[1] for name in native}),
                     "native_files_sha256": native, "bundled_resources_sha256": resources,
                     "bundled_resources_digest": aggregate(resources)})
    return identity


def extract_inputs(apk, destination):
    with zipfile.ZipFile(apk) as archive:
        seen = set()
        total = 0
        for info in archive.infolist():
            if not info.filename.startswith(("assets/shared/", "lib/")):
                continue
            path = PurePosixPath(info.filename)
            if ".." in path.parts or path.is_absolute() or "\\" in info.filename:
                raise ValueError("Unsafe APK resource path")
            if (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError("Symlink in APK resources")
            if info.is_dir():
                continue
            if info.filename in seen:
                raise ValueError("Duplicate APK resource path")
            seen.add(info.filename)
            total += info.file_size
            if total > 512 * 1024 * 1024:
                raise ValueError("APK resources exceed the 512 MiB extraction limit")
            relative = path.relative_to("assets") if path.parts[0] == "assets" else path
            target = destination.joinpath(*relative.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(info) as source, target.open("xb") as output:
                shutil.copyfileobj(source, output)


def validate_corpus(corpus):
    if corpus.get("format_version") != 1 or not re.fullmatch(r"[A-Za-z0-9_-]+", corpus["schema"]):
        raise ValueError("Invalid corpus schema/version")
    if not 1 <= corpus["candidate_limit"] <= 200 or not corpus["cases"] or not corpus["learning"]:
        raise ValueError("Corpus requires cases, learning and a bounded candidate limit")
    if not all(isinstance(v, bool) for v in corpus["options"].values()):
        raise ValueError("Corpus options must be booleans")
    ids = [case["id"] for case in corpus["cases"]]
    if len(set(ids)) != len(ids):
        raise ValueError("Duplicate test case ID")
    for case in corpus["cases"] + corpus["learning"]:
        if not re.fullmatch(r"[2-9']+", case["input"]):
            raise ValueError("Corpus only accepts literal T9 digits and syllable separators")
    for step in corpus["learning"]:
        if not step["text"] or not isinstance(step["repeat"], int) or not 1 <= step["repeat"] <= 100:
            raise ValueError("Invalid learning step")


def write_report(root, report):
    write_json(root / "report.json", report)
    b = report["build"]
    lines = ["# Trime T00 baseline", "", f"Run: `{report['run_id']}`", "",
             "| Field | Value |", "| --- | --- |",
             f"| Label | {report['label']} |",
             f"| Package | `{b['package_name']}` |",
             f"| versionName / versionCode | {b['version_name']} / {b['version_code']} |",
             f"| Git SHA (embedded) | `{b['git_sha']}` |",
             f"| APK SHA-256 | `{b['apk_sha256']}` |",
             f"| Signer SHA-256 | `{', '.join(b['signer_certificate_sha256'])}` |",
             f"| APK ABI | {', '.join(b['abis'])} |",
             f"| Schema | `{report['schema']}` |",
             f"| Schema SHA-256 | `{report['schema_sha256']}` |",
             f"| Resource digest | `{b['bundled_resources_digest']}` |",
             f"| Corpus SHA-256 | `{report['corpus_sha256']}` |",
             f"| Device | {report.get('device', {}).get('status', 'not_connected')} |",
             f"| Android | {report.get('device', {}).get('properties', {}).get('ro.build.version.release', 'not_observed')} |",
             f"| Emulator | {report.get('device', {}).get('properties', {}).get('ro.kernel.qemu', 'not_observed')} |",
             f"| Engine execution | {report.get('engine', {}).get('execution_mode', 'not_observed')} |",
             "", "## Cohorts", ""]
    for name, cohort in report["cohorts"].items():
        lines.append(f"- {name}: **{cohort['status']}** ({cohort['description']})")
    lines.extend(["", "Engine results do not measure Android keyboard touch/rendering latency.",
                  "Host smoke runs use a different library and cannot establish APK behavior or Android performance.",
                  "Full fingerprints, options, device information and artifact links are in report.json.", ""])
    (root / "report.md").write_text("\n".join(lines), encoding="utf-8")


def prepare(apk, label, output, corpus_path, sdk_tools, compiled_from=None):
    corpus = read_json(corpus_path)
    validate_corpus(corpus)
    identity = inspect_apk(apk, sdk_tools)
    schema_file = f"assets/shared/{corpus['schema']}.schema.yaml"
    if schema_file not in identity["bundled_resources_sha256"]:
        raise ValueError("The requested schema is not bundled in this APK")
    run_id = f"{identity['apk_sha256'][:12]}-{uuid.uuid4().hex[:12]}"
    root = Path(output).resolve() / run_id
    root.mkdir(parents=True, exist_ok=False)
    write_json(root / "owner.json", {"format_version": 1, "run_id": run_id})
    inputs = root / "inputs"
    inputs.mkdir()
    shutil.copyfile(apk, inputs / "application.apk")
    shutil.copyfile(corpus_path, inputs / "corpus.json")
    extract_inputs(apk, inputs)
    seed = inputs / "user-seed"
    seed.mkdir()
    opencc = inputs / "shared/opencc"
    if opencc.is_dir():
        shutil.copytree(opencc, seed / "opencc")
    compiled_source = None
    if compiled_from:
        source_root, source_report = load_run(compiled_from)
        if (source_report["build"]["apk_sha256"] != identity["apk_sha256"] or
                source_report.get("engine", {}).get("kind") != "apk_native_engine"):
            raise ValueError("Compiled resources must come from the identical APK")
        compiled = source_root / "cold/user/build"
        expected = read_json(source_root / "cold/runtime-files.sha256.json")
        expected = {k.removeprefix("build/"): v for k, v in expected.items() if k.startswith("build/")}
        if not expected or tree_hashes(compiled) != expected:
            raise ValueError("Compiled resource hashes do not match their source run")
        # Never copy user databases, installation IDs, or learned state.
        if any("/" in name or not name.endswith((".yaml", ".bin")) for name in expected):
            raise ValueError("Unexpected file in compiled resources")
        shutil.copytree(compiled, seed / "build")
        compiled_source = {"run_id": source_report["run_id"], "apk_sha256": identity["apk_sha256"],
                           "files_sha256": expected, "note": "Compiled resources only; no user dictionary copied"}
    (seed / "default.custom.yaml").write_text(
        "patch:\n  schema_list:\n    - schema: " + corpus["schema"] + "\n", encoding="utf-8")
    cohorts = {}
    for name in ("cold", "learned"):
        folder = root / name
        shutil.copytree(seed, folder / "user")
        (folder / "owner.txt").write_text(run_id + "\n", encoding="ascii")
        cohorts[name] = {"status": "not_run", "description": "empty user dictionary" if name == "cold"
                         else "fixed learning script, then restart and measure"}
    report = {"format_version": 1, "fixture_version": 2, "run_id": run_id, "label": label,
              "created_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
              "build": identity, "schema": corpus["schema"],
              "schema_sha256": identity["bundled_resources_sha256"][schema_file],
              "corpus_sha256": digest(inputs / "corpus.json"),
              "inputs_sha256": tree_hashes(inputs), "cohorts": cohorts, "artifacts": []}
    if compiled_source:
        report["compiled_seed_source"] = compiled_source
    write_report(root, report)
    return root


def load_run(root):
    root = Path(root).resolve()
    report = read_json(root / "report.json")
    if read_json(root / "owner.json") != {"format_version": 1, "run_id": report["run_id"]}:
        raise ValueError("Not an owned baseline directory")
    if tree_hashes(root / "inputs") != report["inputs_sha256"]:
        raise ValueError("Frozen APK, resources or corpus were modified; prepare a new run")
    return root, report


def validate_result(data, corpus, phase, run_id):
    if (data.get("run_id") != run_id or data.get("schema") != corpus["schema"] or
            data.get("phase") != phase or data.get("options") != corpus["options"]):
        raise ValueError("Probe result identity/options mismatch")
    records = data.get("records", [])
    if phase == "train":
        expected = [(step["input"], step["text"], n + 1) for step in corpus["learning"]
                    for n in range(step["repeat"])]
        actual = [(r.get("input"), r.get("committed"), r.get("iteration")) for r in records]
        if expected != actual:
            raise ValueError("Learning script was not completed exactly")
    else:
        expected = [(case["id"], case["input"]) for case in corpus["cases"]]
        actual = [(r.get("id"), r.get("input")) for r in records]
        if expected != actual or any(not r.get("candidates") for r in records):
            raise ValueError("Missing/reordered test cases or empty candidates")


def verify(root):
    root, report = load_run(root)
    if report.get("fixture_version", 1) < 2:
        raise ValueError("Legacy fixture omitted OpenCC deployment; prepare a version 2 run")
    corpus = read_json(root / "inputs/corpus.json")
    for entry in report["artifacts"]:
        path = root / entry["path"]
        if not path.resolve().is_relative_to(root) or path.is_symlink():
            raise ValueError("Artifact is outside the run directory")
        if (digest(path) != entry["sha256"] or
                read_json(str(path) + ".identity.json") != entry or
                entry["run_id"] != report["run_id"] or
                entry["apk_sha256"] != report["build"]["apk_sha256"] or
                entry["corpus_sha256"] != report["corpus_sha256"]):
            raise ValueError("Artifact identity/hash mismatch")
    for cohort in ("cold", "learned"):
        if report["cohorts"][cohort]["status"] not in ("measured", "host_smoke_passed"):
            raise ValueError(f"Cohort {cohort} has not completed")
        for phase in (["train", "measure"] if cohort == "learned" else ["measure"]):
            result = read_json(root / cohort / (phase + ".json"))
            validate_result(result, corpus, phase, report["run_id"])
            if result.get("opencc_validated") is not True:
                raise ValueError("OpenCC deployment was not validated")
        if tree_hashes(root / cohort / "user") != read_json(root / cohort / "runtime-files.sha256.json"):
            raise ValueError("Runtime data changed after measurement")
    return "Verified artifacts and both cohorts: " + report["run_id"] + " (" + report["engine"]["kind"] + ")"


def adb(sdk_tools, serial, *command, binary=False):
    return run([sdk_tools["adb"], "-s", serial, *command], binary=binary)


def shell(sdk_tools, serial, *command):
    return adb(sdk_tools, serial, "shell", shlex.join(str(x) for x in command))


def device_info(sdk_tools, serial):
    state = adb(sdk_tools, serial, "get-state").strip()
    if state != "device":
        raise ValueError("Device is not authorized/online")
    props = {}
    for key in ("ro.build.version.release", "ro.build.version.sdk", "ro.build.fingerprint",
                "ro.product.model", "ro.product.cpu.abilist", "ro.product.cpu.abi",
                "ro.kernel.qemu", "ro.boot.qemu.avd_name", "ro.dalvik.vm.native.bridge",
                "ro.enable.native.bridge.exec", "ro.ndk_translation.version"):
        props[key] = shell(sdk_tools, serial, "getprop", key).strip()
    return {"status": "connected", "serial": serial, "properties": props}


def installed_apks(sdk_tools, serial, package, destination):
    if not re.fullmatch(r"[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+", package):
        raise ValueError("Invalid package name")
    paths = shell(sdk_tools, serial, "pm", "path", package).splitlines()
    paths = [p.removeprefix("package:") for p in paths if p.startswith("package:")]
    if not paths:
        raise ValueError(f"Package is not installed: {package}")
    destination.mkdir(parents=True, exist_ok=False)
    pulled = []
    for index, path in enumerate(paths):
        target = destination / f"{index}-{PurePosixPath(path).name}"
        adb(sdk_tools, serial, "pull", path, str(target))
        pulled.append(target)
    base = next((p for p in pulled if p.name.endswith("-base.apk")), pulled[0])
    identity = inspect_apk(base, sdk_tools)
    identity["installed_apks_sha256"] = {p.name: digest(p) for p in pulled}
    return identity


def inventory(args, sdk_tools):
    root = Path(args.out).resolve() / ("device-" + uuid.uuid4().hex[:12])
    root.mkdir(parents=True)
    info = device_info(sdk_tools, args.serial)
    info["packages"] = {}
    packages = shell(sdk_tools, args.serial, "pm", "list", "packages").splitlines()
    for package in ("com.osfans.trime", "com.osfans.trime.debug"):
        if "package:" + package in packages:
            info["packages"][package] = installed_apks(sdk_tools, args.serial, package, root / package)
    write_json(root / "inventory.json", info)
    return root / "inventory.json"


def compile_probe(output, compiler, android=False):
    command = [compiler, "-std=c++17", "-O1", "-Wall", "-Wextra",
               "-Wno-missing-field-initializers", "-Wno-deprecated-declarations",
               "-I", REPO / "app/src/main/jni/librime/src",
               "-I", REPO / "app/src/main/jni/OpenCC/deps/rapidjson-1.1.0",
               HERE / "engine_probe.cc", "-ldl", "-o", output]
    if android:
        command.append("-static-libstdc++")
    run(command)


def artifact(root, report, path, cohort, extra=None):
    metadata = {"run_id": report["run_id"], "apk_sha256": report["build"]["apk_sha256"],
                "corpus_sha256": report["corpus_sha256"], "cohort": cohort,
                "path": Path(path).relative_to(root).as_posix(), "sha256": digest(path)}
    if extra:
        metadata.update(extra)
    write_json(str(path) + ".identity.json", metadata)
    report["artifacts"].append(metadata)


def execute(args, sdk_tools):
    root, report = load_run(args.run)
    if report.get("fixture_version", 1) < 2:
        raise ValueError("Legacy fixture omitted OpenCC deployment; prepare a version 2 run")
    for name in ("cold", "learned"):
        if (report["cohorts"][name]["status"] != "not_run" or
                tree_hashes(root / name / "user") != tree_hashes(root / "inputs/user-seed")):
            raise ValueError("Cohorts are single-use; prepare a new run (no reset/deletion is supported)")
    if not 1 <= args.phase_timeout <= 3600:
        raise ValueError("--phase-timeout must be between 1 and 3600 seconds")
    probe = root / "engine-probe"
    remote = None
    if args.host_library:
        library = Path(args.host_library).resolve(strict=True)
        compile_probe(probe, os.environ.get("CXX", "c++"))
        engine = {"kind": "host_smoke_only", "library_sha256": digest(library),
                  "android_version": None, "note": "Not the APK library; not an Android benchmark"}
    else:
        report["device"] = device_info(sdk_tools, args.serial)
        supported = report["device"]["properties"]["ro.product.cpu.abilist"].split(",")
        abi = next((x for x in supported if x in report["build"]["abis"] and
                    x in ("arm64-v8a", "x86_64")), None)
        if abi is None:
            raise ValueError("No supported 64-bit APK ABI on the connected device")
        if not args.ndk:
            raise ValueError("--ndk is required for Android probe compilation")
        target = "aarch64-linux-android21" if abi == "arm64-v8a" else "x86_64-linux-android21"
        compiler = Path(args.ndk) / "toolchains/llvm/prebuilt/linux-x86_64/bin" / (target + "-clang++")
        compile_probe(probe, compiler, android=True)
        remote = "/data/local/tmp/trime-baseline-" + uuid.uuid4().hex
        shell(sdk_tools, args.serial, "mkdir", remote)
        adb(sdk_tools, args.serial, "push", str(root / "inputs"), remote + "/inputs")
        adb(sdk_tools, args.serial, "push", str(probe), remote + "/engine-probe")
        shell(sdk_tools, args.serial, "chmod", "700", remote + "/engine-probe")
        library = f"{remote}/inputs/lib/{abi}/librime_jni.so"
        engine = {"kind": "apk_native_engine", "abi": abi, "remote_directory": remote,
                  "execution_mode": "native_bridge" if abi != report["device"]["properties"]["ro.product.cpu.abi"] else "native",
                  "library_sha256": report["build"]["native_files_sha256"][f"lib/{abi}/librime_jni.so"]}
    engine["probe_sha256"] = digest(probe)
    engine["phase_timeout_seconds"] = args.phase_timeout
    engine["probe_source_sha256"] = digest(HERE / "engine_probe.cc")
    engine["driver_source_sha256"] = digest(HERE / "baseline.py")
    engine["rime_api_header_sha256"] = digest(REPO / "app/src/main/jni/librime/src/rime_api.h")
    report["engine"] = engine
    write_report(root, report)
    try:
        for cohort in ("cold", "learned"):
            folder = root / cohort
            if cohort == "learned" and args.reuse_cold_build:
                compiled = root / "cold/user/build"
                hashes = tree_hashes(compiled)
                if not hashes or any("/" in name or not name.endswith((".yaml", ".bin")) for name in hashes):
                    raise ValueError("Unexpected cold compiled resource files")
                shutil.copytree(compiled, folder / "user/build", dirs_exist_ok=True)
                report["cohorts"][cohort]["compiled_resources_from"] = "cold/user/build"
                report["cohorts"][cohort]["compiled_resources_sha256"] = hashes
            report["cohorts"][cohort]["status"] = "running"
            write_report(root, report)
            if remote:
                adb(sdk_tools, args.serial, "push", str(folder), f"{remote}/{cohort}")
            for phase in (["train", "measure"] if cohort == "learned" else ["measure"]):
                print(f"{report['run_id']} {cohort}/{phase}: starting", flush=True)
                started = time.monotonic()
                if remote:
                    command = [f"{remote}/engine-probe", library, f"{remote}/inputs/shared",
                               f"{remote}/{cohort}/user", f"{remote}/inputs/corpus.json", phase, report["run_id"]]
                    output = run([sdk_tools["adb"], "-s", args.serial, "shell", shlex.join(command)], timeout=args.phase_timeout)
                else:
                    output = run([probe, library, root / "inputs/shared", folder / "user",
                                  root / "inputs/corpus.json", phase, report["run_id"]], timeout=args.phase_timeout)
                data = json.loads(output)
                validate_result(data, read_json(root / "inputs/corpus.json"), phase, report["run_id"])
                if data.get("opencc_validated") is not True:
                    raise ValueError("OpenCC deployment was not validated")
                print(f"{report['run_id']} {cohort}/{phase}: passed in {time.monotonic() - started:.1f}s", flush=True)
                path = folder / (phase + ".json")
                write_json(path, data)
                artifact(root, report, path, cohort, {"engine": engine})
            if remote:
                adb(sdk_tools, args.serial, "pull", f"{remote}/{cohort}/user/.", str(folder / "user"))
            fingerprints = tree_hashes(folder / "user")
            write_json(folder / "runtime-files.sha256.json", fingerprints)
            artifact(root, report, folder / "runtime-files.sha256.json", cohort, {"engine": engine})
            report["cohorts"][cohort]["status"] = "host_smoke_passed" if args.host_library else "measured"
            report["cohorts"][cohort]["runtime_digest"] = aggregate(fingerprints)
            write_report(root, report)
    except Exception as error:
        report["cohorts"][cohort]["status"] = "failed"
        report["cohorts"][cohort]["error"] = str(error)
        write_report(root, report)
        raise
    return root / "report.md"


def capture(args, sdk_tools):
    root, report = load_run(args.run)
    folder = root / ("capture-" + uuid.uuid4().hex[:12])
    folder.mkdir()
    info = device_info(sdk_tools, args.serial)
    installed = installed_apks(sdk_tools, args.serial, report["build"]["package_name"], folder / "installed")
    if installed["apk_sha256"] != report["build"]["apk_sha256"] or len(installed["installed_apks_sha256"]) != 1:
        raise ValueError("Installed APK does not exactly match the frozen single APK; capture refused")
    metadata = {"device": info, "schema_state": "not_observed",
                "user_dictionary_state": "existing_uncontrolled",
                "note": "Installed app UI is separate from isolated engine cold/learned cohorts"}
    if args.runtime_export:
        metadata["runtime_export_sha256"] = tree_hashes(Path(args.runtime_export).resolve(strict=True))
        metadata["runtime_export_source"] = "user-supplied export; may differ from live runtime"
    screenshot = folder / "screenshot.png"
    screenshot.write_bytes(adb(sdk_tools, args.serial, "exec-out", "screencap", "-p", binary=True))
    if not screenshot.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("Device did not return a PNG screenshot")
    memory = folder / "meminfo.txt"
    memory.write_text(shell(sdk_tools, args.serial, "dumpsys", "meminfo", installed["package_name"]), encoding="utf-8")
    for path in (screenshot, memory):
        artifact(root, report, path, "installed_uncontrolled", metadata)
    write_report(root, report)
    return folder


def recover(args, sdk_tools):
    root, report = load_run(args.run)
    engine = report.get("engine", {})
    remote = engine.get("remote_directory", "")
    if (engine.get("kind") != "apk_native_engine" or
            not re.fullmatch(r"/data/local/tmp/trime-baseline-[0-9a-f]{32}", remote) or
            report.get("device", {}).get("serial") != args.serial):
        raise ValueError("Recovery requires this run's original isolated Android directory/device")
    if remote + "/engine-probe" in shell(sdk_tools, args.serial, "ps", "-A", "-o", "ARGS"):
        raise ValueError("Probe is still running; recovery refused")
    for cohort in ("cold", "learned"):
        if report["cohorts"][cohort]["status"] not in ("running", "failed"):
            continue
        marker = shell(sdk_tools, args.serial, "cat", f"{remote}/{cohort}/owner.txt").strip()
        if marker != report["run_id"]:
            raise ValueError("Remote ownership marker mismatch")
        folder = root / cohort
        adb(sdk_tools, args.serial, "pull", f"{remote}/{cohort}/user/.", str(folder / "user"))
        path = folder / "runtime-files.sha256.json"
        write_json(path, tree_hashes(folder / "user"))
        artifact(root, report, path, cohort, {"recovered_after_interruption": True})
        report["cohorts"][cohort]["status"] = "interrupted"
        report["cohorts"][cohort]["error"] = "Interrupted driver; recovered files are not passing measurement results"
    write_report(root, report)
    return root / "report.md"


def fetch_latest(output, repository="osfans/trime"):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*/[A-Za-z0-9][A-Za-z0-9_.-]*", repository):
        raise ValueError("--repository must be a GitHub owner/repository pair")
    def open_url(url):
        return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "trime-t00-baseline"}), timeout=60)
    with open_url(f"https://api.github.com/repos/{repository}/releases/latest") as response:
        release = json.load(response)
    assets = [a for a in release["assets"] if a["name"].endswith(".apk") and "arm64-v8a" in a["name"]]
    if len(assets) != 1:
        raise ValueError("Latest release does not have exactly one ARM64 APK")
    asset = assets[0]
    root = Path(output).resolve() / ("release-" + uuid.uuid4().hex[:12])
    root.mkdir(parents=True)
    path = root / "latest-arm64.apk"
    with open_url(asset["browser_download_url"]) as source, path.open("xb") as target:
        shutil.copyfileobj(source, target)
    actual = digest(path)
    if asset.get("digest") and asset["digest"] != "sha256:" + actual:
        raise ValueError("Release asset SHA-256 mismatch")
    write_json(root / "release.json", {"repository": repository, "tag": release["tag_name"], "release_id": release["id"],
                                       "url": release["html_url"], "asset": asset, "apk_sha256": actual})
    return path


def compare(runs, output):
    reports = [load_run(path)[1] for path in runs]
    fields = ("package_name", "version_name", "version_code", "git_sha", "abis",
              "signer_certificate_sha256", "apk_sha256", "bundled_resources_digest")
    rows = ["# Frozen APK comparison", "", "| Field | " + " | ".join(r["label"] for r in reports) + " |",
            "| --- | " + " | ".join("---" for _ in reports) + " |"]
    for field in fields:
        rows.append("| " + field + " | " + " | ".join(str(r["build"][field]) for r in reports) + " |")
    rows += ["", "This compares APK identity, not measured phone usability.", ""]
    for report in reports:
        rows.append(f"- {report['label']}: run `{report['run_id']}`; cohorts " +
                    ", ".join(f"{k}={v['status']}" for k, v in report["cohorts"].items()))
    Path(output).write_text("\n".join(rows) + "\n", encoding="utf-8")
    return Path(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME"))
    sub = parser.add_subparsers(dest="command", required=True)
    fetch = sub.add_parser("fetch-latest")
    fetch.add_argument("--out", default="build/baseline")
    fetch.add_argument("--repository", default="osfans/trime", help="GitHub owner/repository to inspect")
    prep = sub.add_parser("prepare")
    prep.add_argument("--apk", required=True)
    prep.add_argument("--label", required=True)
    prep.add_argument("--corpus", default=str(HERE / "corpus.json"))
    prep.add_argument("--out", default="build/baseline")
    prep.add_argument("--compiled-from", help="Reuse only compiled resources from the same APK's prior cold run")
    execute_parser = sub.add_parser("run")
    execute_parser.add_argument("--run", required=True)
    backend = execute_parser.add_mutually_exclusive_group(required=True)
    backend.add_argument("--host-library")
    backend.add_argument("--serial")
    execute_parser.add_argument("--ndk")
    execute_parser.add_argument("--phase-timeout", type=int, default=600,
                                help="Seconds allowed per deployment/train/measurement process (1-3600)")
    execute_parser.add_argument("--reuse-cold-build", action="store_true",
                                help="Reuse this run's cold compiled resources, never its user dictionary")
    device = sub.add_parser("device")
    device.add_argument("--serial", required=True)
    device.add_argument("--out", default="build/baseline")
    cap = sub.add_parser("capture")
    cap.add_argument("--serial", required=True)
    cap.add_argument("--run", required=True)
    cap.add_argument("--runtime-export")
    recovery = sub.add_parser("recover", help="Archive interrupted isolated runtime after its probe has stopped")
    recovery.add_argument("--serial", required=True)
    recovery.add_argument("--run", required=True)
    comp = sub.add_parser("compare")
    comp.add_argument("runs", nargs="+", help="Frozen run directories")
    comp.add_argument("--out", required=True)
    verification = sub.add_parser("verify")
    verification.add_argument("--run", required=True)
    args = parser.parse_args()
    needs_sdk = args.command in ("prepare", "device", "capture", "recover") or (args.command == "run" and args.serial)
    if needs_sdk and not args.sdk:
        parser.error("Set ANDROID_SDK_ROOT or pass --sdk")
    sdk_tools = tools(args.sdk) if needs_sdk else None
    if args.command == "prepare":
        result = prepare(args.apk, args.label, args.out, args.corpus, sdk_tools, args.compiled_from)
    elif args.command == "fetch-latest":
        result = fetch_latest(args.out, args.repository)
    elif args.command == "device":
        result = inventory(args, sdk_tools)
    elif args.command == "capture":
        result = capture(args, sdk_tools)
    elif args.command == "recover":
        result = recover(args, sdk_tools)
    elif args.command == "run":
        result = execute(args, sdk_tools)
    elif args.command == "verify":
        result = verify(args.run)
    else:
        result = compare(args.runs, args.out)
    print(result)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"Baseline error: {error}", file=sys.stderr)
        sys.exit(1)
