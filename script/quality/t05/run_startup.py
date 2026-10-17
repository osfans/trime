#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Repeated cold-process wizard probes on a prepared disposable emulator."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from runtime_audit import audit_log, filter_pid_log, instrumentation_passed, record_logcat


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--runs", type=int, default=5)
    args = parser.parse_args()
    if not 1 <= args.runs <= 20:
        parser.error("--runs must be between 1 and 20")
    args.output.mkdir(parents=True, exist_ok=False)
    adb = [args.adb, "-s", args.serial]
    package = "com.osfans.trime.debug"

    def shell(*command, timeout=60):
        return subprocess.check_output(adb + ["shell", *command], text=True, timeout=timeout)

    if shell("getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Emulator only; no physical-device force-stop")
    (args.output / "package.txt").write_text(shell("dumpsys", "package", package))
    apk = shell("pm", "path", package).strip().removeprefix("package:")
    if not apk.startswith("/") or "\n" in apk:
        raise RuntimeError("Expected one installed base APK")
    # Older system images lack sha256sum. Hash a streamed copy on the host.
    with tempfile.TemporaryFile() as source:
        subprocess.run(adb + ["exec-out", "cat", apk], stdout=source, check=True, timeout=120)
        source.seek(0)
        checksum = hashlib.sha256()
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    (args.output / "installed-apk.sha256").write_text(f"{checksum.hexdigest()}  {apk}\n")
    results = []
    for index in range(1, args.runs + 1):
        shell("am", "force-stop", package)
        entry = {"run": index, "passed": False}
        results.append(entry)
        report = args.output / "report.json"
        report.write_text(json.dumps(results, indent=2) + "\n")
        with record_logcat(adb, args.output / f"{index}-full.log"), (args.output / f"{index}.log").open("w") as log:
            run = subprocess.run(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "setup", "true",
                package + ".test/com.osfans.trime.StartupResponsivenessInstrumentation"],
                stdout=log, stderr=subprocess.STDOUT, timeout=180)
        text = (args.output / f"{index}.log").read_text()
        passed = instrumentation_passed(run.returncode, text)
        prefs = ET.fromstring(shell("run-as", package, "cat", f"shared_prefs/{package}_preferences.xml"))
        pid = int(prefs.find("./int[@name='general__pid']").attrib["value"])
        app_log = filter_pid_log((args.output / f"{index}-full.log").read_text(), pid)
        (args.output / f"{index}-app.log").write_text(app_log)
        audit = audit_log(app_log)
        (args.output / f"{index}-runtime-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
        passed = passed and audit["capture_has_records"] and not audit["known_project_diagnostics"]
        match = re.search(r"max_main_gap_ms=(\d+)", text)
        entry.update(passed=passed, pid=pid, max_main_gap_ms=int(match[1]) if match else None,
                     warning_free=audit["warning_free"], warning_count=len(audit["warnings"]))
        report.write_text(json.dumps(results, indent=2) + "\n")
        print(entry, flush=True)
        if not passed:
            raise RuntimeError(f"Wizard probe {index} failed; see retained evidence")


if __name__ == "__main__":
    main()
