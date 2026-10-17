#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run T03/T05 on a prepared API 29+ emulator, restoring display overrides."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
from runtime_audit import audit_log, device_audit_lock, filter_pid_log, instrumentation_passed, instrumentation_pid, record_logcat, validate_screenshots


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--case", choices=["360", "412", "tablet", "landscape", "large", "landscape-360", "landscape-large"])
    parser.add_argument("--geometry-only", action="store_true", help="Skip repeated T03 gestures or T05 corpus measurements; retain both-theme UI checks")
    parser.add_argument("--probe", choices=["t03", "t05"], default="t03")
    args = parser.parse_args()
    with device_audit_lock(args.serial):
        run(args)


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    base = [args.adb, "-s", args.serial]

    def adb(*argv, timeout=60):
        return subprocess.check_output(base + list(argv), timeout=timeout).decode().strip()

    def shell(*argv, timeout=60):
        return adb("shell", *argv, timeout=timeout)

    def screenshots():
        return [name for name in shell("run-as", package, "ls", "cache").splitlines()
                if re.fullmatch(args.probe + r"-[A-Za-z0-9_.-]+\.png", name)]

    if shell("getprop", "ro.hardware") not in {"ranchu", "goldfish"}:
        raise RuntimeError("Refusing to change display settings on a physical device")
    if int(shell("getprop", "ro.build.version.sdk")) < 29:
        raise RuntimeError("Probe requires API 29+")
    package = "com.osfans.trime.debug"
    runner = package + ".test/com.osfans.trime.StartupResponsivenessInstrumentation"
    original = {name: shell("wm", name) for name in ("size", "density")}
    font = shell("settings", "get", "system", "font_scale")
    (args.output / "original-display.json").write_text(json.dumps({**original, "font_scale": font}, indent=2) + "\n")
    (args.output / "installed-package.txt").write_text(shell("dumpsys", "package", package) + "\n")
    identity = {"serial": args.serial, "api": shell("getprop", "ro.build.version.sdk"),
                "build_fingerprint": shell("getprop", "ro.build.fingerprint"), "packages": {}}
    for name in (package, package + ".test"):
        installed = shell("pm", "path", name).removeprefix("package:")
        if not installed.startswith("/") or "\n" in installed:
            raise RuntimeError("Expected one APK per package")
        with tempfile.TemporaryFile() as source:
            subprocess.run(base + ["exec-out", "cat", installed], stdout=source, check=True, timeout=120)
            source.seek(0)
            digest = hashlib.file_digest(source, "sha256").hexdigest()
        identity["packages"][name] = {"apk_sha256": digest, "installed_path": installed}
    (args.output / "identity.json").write_text(json.dumps(identity, indent=2) + "\n")
    cases = {
        "360": ("720x1600", "1.0"),
        "412": ("824x1800", "1.0"),
        "tablet": ("1280x1600", "1.0"),
        "landscape": ("1600x824", "1.0"),
        "large": ("824x1800", "2.0"),
        "landscape-360": ("1600x720", "1.0"),
        "landscape-large": ("1600x824", "2.0"),
    }
    report = {}
    try:
        for name, (size, scale) in cases.items():
            if args.case and args.case != name:
                continue
            target = args.output / name
            target.mkdir(exist_ok=True)
            # Only probe-owned screenshots are removed; dictionaries and other app data are untouched.
            for filename in screenshots():
                shell("run-as", package, "rm", "cache/" + filename)
            shell("wm", "size", size)
            shell("wm", "density", "320")
            shell("settings", "put", "system", "font_scale", scale)
            print(f"Running {name}: {size}, density 320, font {scale}", flush=True)
            with record_logcat(base, target / "logcat-full.txt"), (target / "instrumentation.log").open("w") as log:
                options = ["-e", args.probe + "GeometryOnly", "true"] if args.geometry_only else []
                completed = subprocess.run(
                    base + ["shell", "am", "instrument", "-w", "-r", "-e", args.probe, "true"] + options + [runner],
                    stdout=log, stderr=subprocess.STDOUT, timeout=1500 if args.probe == "t05" else 1000, check=False,
                )
            output = (target / "instrumentation.log").read_text()
            passed = instrumentation_passed(completed.returncode, output)
            report[name] = {"passed": False, "probe_passed": passed, "artifacts_complete": False, "size": size, "density": 320, "font_scale": scale, "geometry_only": args.geometry_only, "screenshots": {}}
            # Keep an incomplete record even if the emulator disconnects during collection.
            (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
            for filename in screenshots():
                with (target / filename).open("wb") as png:
                    subprocess.run(base + ["exec-out", "run-as", package, "cat", "cache/" + filename], stdout=png, check=True, timeout=60)
                report[name]["screenshots"][filename] = hashlib.sha256((target / filename).read_bytes()).hexdigest()
            artifact_error = None
            try:
                validate_screenshots(args.probe, {filename: (target / filename).read_bytes()
                                                for filename in report[name]["screenshots"]})
            except ValueError as error:
                artifact_error = str(error)
            if args.probe == "t05" and not args.geometry_only and passed:
                metrics = shell("run-as", package, "cat", "cache/t05-metrics.json")
                (target / "metrics.json").write_text(metrics + "\n")
                report[name]["metrics_sha256"] = hashlib.sha256((target / "metrics.json").read_bytes()).hexdigest()
            pid = instrumentation_pid(output)
            app_log = filter_pid_log((target / "logcat-full.txt").read_text(), pid)
            (target / "app-logcat.txt").write_text(app_log + "\n")
            audit = audit_log(app_log)
            (target / "runtime-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
            diagnostics = audit["known_project_diagnostics"]
            passed = passed and audit["capture_has_records"] and not diagnostics and artifact_error is None
            report[name].update({"passed": passed, "app_pid": pid, "project_diagnostics": diagnostics,
                                 "warning_free": audit["warning_free"], "warning_count": len(audit["warnings"])})
            (target / "logcat.txt").write_text(shell("logcat", "-d", "-v", "threadtime", timeout=120) + "\n")
            report[name]["artifacts_complete"] = artifact_error is None
            report[name]["artifact_error"] = artifact_error
            (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
            print(f"{name}: {'PASS' if passed else 'FAIL'}", flush=True)
            if not passed:
                raise RuntimeError(f"Probe failed; see {target / 'instrumentation.log'}")
    finally:
        for name, output in original.items():
            match = re.search(r"Override (?:size|density): (\S+)", output)
            shell("wm", name, match.group(1) if match else "reset")
        if font == "null":
            shell("settings", "delete", "system", "font_scale")
        else:
            shell("settings", "put", "system", "font_scale", font)


if __name__ == "__main__":
    main()
