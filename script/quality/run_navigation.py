#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real back events and clipboard focus without replacing the IME process."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

if sys.flags.optimize:
    raise SystemExit(
        "Navigation gate refuses optimized Python (-O/-OO); "
        "its runtime assertions must remain enabled"
    )

from runtime_audit import audit_log, device_audit_lock, filter_pid_log, record_logcat


def validate_action_ids(root, package):
    for description, name in (("Deploy", "action_deploy"), ("Test input", "action_test_input")):
        matches = [n for n in root.iter("node") if n.get("content-desc") == description]
        assert len(matches) == 1, f"Missing or duplicate action: {description}"
        assert matches[0].get("resource-id") == f"{package}:id/{name}", f"Unresolvable action ID: {description}"


def select_test_ime(shell, method, attempts=10, sleep=time.sleep):
    history = []
    for _ in range(attempts):
        entry = {"command": shell("ime", "set", method), "observed": []}
        history.append(entry)
        for _ in range(4):
            sleep(0.25)
            selected = shell("settings", "get", "secure", "default_input_method")
            entry["observed"].append(selected)
            if selected != method:
                break
        else:
            return {"selected": True, "attempts": history}
    return {"selected": False, "attempts": history}


def capture_test_window(shell, remote, expected=(), attempts=6, sleep=time.sleep):
    history = []
    for _ in range(attempts):
        entry = {}
        history.append(entry)
        try:
            shell("rm", "-f", remote)
            entry["output"] = shell("uiautomator", "dump", remote)
            if "dumped to:" not in entry["output"]:
                raise ValueError("UIAutomator did not produce a fresh hierarchy")
            xml = shell("cat", remote)
            root = ET.fromstring(xml)
            if root.tag != "hierarchy" or not list(root.iter("node")):
                raise ValueError("Empty or invalid hierarchy")
            if expected and not any(n.get(field) == value for n in root.iter("node") for field, value in expected):
                entry["xml"] = xml
                raise ValueError("Expected control is not visible yet")
            return root, xml, history
        except (subprocess.SubprocessError, ValueError, ET.ParseError) as error:
            entry["error"] = str(error)
            sleep(0.25)
    return None, None, history


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cycles", type=int, choices=range(1, 11), default=4)
    parser.add_argument("--navigation-only", action="store_true",
                        help="On API 29+, pair navigation with run_runtime.py --probe clip; modern am rejects an app UID")
    args = parser.parse_args()
    with device_audit_lock(args.serial):
        run(args)


def run(args):
    args.output.mkdir(parents=True, exist_ok=False)
    adb = [args.adb, "-s", args.serial]
    package = "com.osfans.trime.debug"

    def shell(*command):
        return subprocess.check_output(adb + ["shell", *command], text=True, stderr=subprocess.STDOUT, timeout=120).strip()

    assert shell("getprop", "ro.kernel.qemu") == "1", "Emulator only"
    # Legacy adb shell does not propagate the remote command's exit status.
    assert shell("cat", f"/sdcard/Android/data/{package}/files/runtime-audit-dedicated") == ""
    assert shell("settings", "get", "secure", "default_input_method").startswith(package + "/")
    installed = shell("pm", "path", package).removeprefix("package:")
    assert installed.startswith("/") and "\n" not in installed
    with tempfile.TemporaryFile() as apk:
        subprocess.run(adb + ["exec-out", "cat", installed], stdout=apk, check=True, timeout=120)
        apk.seek(0)
        actual = hashlib.file_digest(apk, "sha256").hexdigest()
    with args.apk.open("rb") as apk:
        assert actual == hashlib.file_digest(apk, "sha256").hexdigest(), "Wrong installed APK"
    identity = {"api": shell("getprop", "ro.build.version.sdk"), "apk_sha256": actual,
                "cycles": args.cycles,
                "driver_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                "fingerprint": shell("getprop", "ro.build.fingerprint"), "serial": args.serial}
    (args.output / "identity.json").write_text(json.dumps(identity, indent=2) + "\n")
    sequence = 0

    def window(*expected):
        nonlocal sequence
        sequence += 1
        remote = "/sdcard/runtime-audit-navigation.xml"
        root, xml, attempts = capture_test_window(shell, remote, expected)
        (args.output / f"window-{sequence:02}-capture.json").write_text(json.dumps(attempts, indent=2) + "\n")
        assert root is not None, "Fresh target hierarchy unavailable; see capture attempts"
        (args.output / f"window-{sequence:02}.xml").write_text(xml)
        return root

    def find(root, field, value):
        return [n for n in root.iter("node") if n.get(field) == value]

    def tap(root, field, value):
        matches = find(root, field, value)
        assert len(matches) == 1, (field, value, len(matches))
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", matches[0].get("bounds", "")))
        shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

    passed = False
    clipboard_checked = False
    pid = None
    try:
        with record_logcat(adb, args.output / "logcat-full.txt"):
            shell("am", "force-stop", package)
            # Force-stop can reset the selected IME asynchronously, after ime set returns.
            selection = select_test_ime(shell, package + "/com.osfans.trime.ime.core.TrimeInputMethodService")
            (args.output / "ime-selection.json").write_text(json.dumps(selection, indent=2) + "\n")
            assert selection["selected"], "Test IME did not stabilize after force-stop"
            launch = shell("am", "start", "-W", "-n", package + "/com.osfans.trime.MainLauncherAlias")
            (args.output / "launch.txt").write_text(launch + "\n")
            processes = shell("ps", "-A") if int(identity["api"]) >= 26 else shell("ps")
            for row in processes.splitlines():
                fields = row.split()
                if fields and fields[-1] == package:
                    pid = int(fields[1])
            assert pid, "Missing application process"
            root = window(("text", "Schemata"), ("text", "No Notification Permission"))
            if find(root, "text", "No Notification Permission"):
                tap(root, "text", "CANCEL")
                root = window(("text", "Schemata"))
            validate_action_ids(root, package)
            for cycle in range(args.cycles):
                assert find(root, "text", "Schemata"), "Main settings is not visible"
                tap(root, "text", "Profile")
                root = window(("text", "Data storage mode"))
                assert find(root, "text", "Data storage mode")
                shell("input", "keyevent", "4")
                root = window(("text", "Schemata"))
                assert find(root, "text", "Schemata")
                tap(root, "content-desc", "Test input")
                root = window(("text", "Type text"))
                assert find(root, "text", "Type text"), "Input panel did not open"
                for _ in range(3):
                    shell("input", "keyevent", "4")
                    time.sleep(0.3)
                    root = window()
                    if not find(root, "text", "Type text"):
                        break
                assert not find(root, "text", "Type text"), "Input panel did not close"
                assert find(root, "text", "Schemata"), "Back exited settings instead of closing panel"
                print(f"Navigation/panel cycle {cycle + 1}: PASS", flush=True)
            if not args.navigation_only:
                # API 21 can retain a stale IME binding when instrumentation replaces
                # its process. Its legacy am allows launching as the Activity's own UID.
                # Modern Android checks am's shell package/UID: use the separate clip probe.
                launch = shell("run-as", package, "/system/bin/am", "start", "--user", "0", "-W", "-n",
                               package + "/com.osfans.trime.ui.main.ClipEditActivity")
                (args.output / "clip-launch.txt").write_text(launch + "\n")
                assert "Status: ok" in launch, launch
                root = window(("resource-id", package + ":id/clip_edit_text"))
                deadline = time.monotonic() + 60
                while True:
                    ime = shell("dumpsys", "input_method")
                    if "mInputShown=true" in ime:
                        break
                    assert time.monotonic() < deadline, "Keyboard was not shown"
                    time.sleep(0.2)
                (args.output / "clip-input-method.txt").write_text(ime + "\n")
                assert shell("settings", "get", "secure", "default_input_method").startswith(package + "/")
                time.sleep(1)
                with (args.output / "clip.png").open("wb") as png:
                    subprocess.run(adb + ["exec-out", "screencap", "-p"], stdout=png, check=True, timeout=60)
                tap(root, "text", "Cancel")
                root = window(("text", "Schemata"))
                clipboard_checked = True
                print("Clipboard focus, shown keyboard and cancel without saving: PASS", flush=True)
            passed = True
    finally:
        if pid:
            log = filter_pid_log((args.output / "logcat-full.txt").read_text(), pid)
            (args.output / "app-logcat.txt").write_text(log)
            audit = audit_log(log)
            (args.output / "runtime-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
            report = {"functional_passed": passed, "pid": pid,
                      "clipboard_checked": clipboard_checked,
                      "warning_free": audit["warning_free"],
                      "project_diagnostics": audit["known_project_diagnostics"]}
            (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    assert passed and audit["capture_has_records"] and not audit["known_project_diagnostics"]


if __name__ == "__main__":
    main()
