#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Actual UI-only ClipEditActivity Back/Cancel check on the marked API 35 emulator.

No row ID/type is supplied, no OK button is clicked, and no clipboard/collection
database is accessed. Input uses LatinIME and actual ASCII/Enter key events;
Trime is then selected before Back. All display and IME settings are restored.
This intentionally does not establish save persistence or title glyph geometry.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shlex
import struct
import subprocess
import sys
import tempfile
import time
import uuid


REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "script/quality"))
from android_tools import resolve_adb
from editor_windows import capture_editor_window
from run_navigation import select_test_ime
from runtime_audit import audit_log, device_audit_lock, filter_pid_log, log_after_marker, record_logcat

PACKAGE = "com.osfans.trime.debug"
ACTIVITY = PACKAGE + "/com.osfans.trime.ui.main.ClipEditActivity"
IME = PACKAGE + "/com.osfans.trime.ime.core.TrimeInputMethodService"
OBSERVER = "org.x2m7.trime.editorobserver"
FIELD = PACKAGE + ":id/clip_edit_text"
OK = PACKAGE + ":id/clip_edit_ok"
CANCEL = PACKAGE + ":id/clip_edit_cancel"
TARGET_SIZE = (1600, 824)


def require(value, message):
    if not value:
        raise RuntimeError(message)


def box(node):
    values = tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
    require(len(values) == 4 and values[0] < values[2] and values[1] < values[3], "Invalid control bounds")
    return values


def nodes(root, resource):
    return [n for n in root.iter("node") if n.get("resource-id") == resource]


def unique(root, resource):
    matches = nodes(root, resource)
    require(len(matches) == 1, "Expected exactly one visible " + resource)
    return matches[0]


def screen_contains(rect, size=TARGET_SIZE):
    x1, y1, x2, y2 = rect
    return 0 <= x1 < x2 <= size[0] and 0 <= y1 < y2 <= size[1]


def intersects(a, b):
    return max(a[0], b[0]) < min(a[2], b[2]) and max(a[1], b[1]) < min(a[3], b[3])


def finalize_log_review(report, out, pids, marker):
    """Require readable, scoped application evidence before accepting UI success."""
    report.update({"diagnostics_passed": False, "capture_has_records": False,
                   "diagnostic_scope": "application PIDs after the unique capture marker"})
    try:
        scoped = log_after_marker((out / "logcat-full.txt").read_text(), marker)
        (out / "logcat-after-marker.txt").write_text(scoped)
        app_log = "\n".join(filter_pid_log(scoped, pid).strip() for pid in sorted(pids)) + "\n"
        (out / "app-logcat.txt").write_text(app_log)
        audit = audit_log(app_log)
        (out / "runtime-audit.json").write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n")
        report.update({"app_pids": sorted(pids), "capture_has_records": audit["capture_has_records"],
                       "warning_count": len(audit["warnings"]), "warning_free": audit["warning_free"],
                       "project_diagnostic_count": len(audit["known_project_diagnostics"]),
                       "requires_log_review": bool(audit["warnings"] or audit["known_project_diagnostics"])})
        require(audit["capture_has_records"], "Scoped log capture contains no application records")
        require(not audit["known_project_diagnostics"], "Scoped log capture contains project diagnostics; see runtime-audit.json")
        report["diagnostics_passed"] = True
    except Exception as error:
        report["log_review_error"] = f"{type(error).__name__}: {error}"
    report["passed"] = (report["functional_passed"] and report["restoration"].get("passed", False)
                        and report["diagnostics_passed"]
                        and not any(key in report for key in ("error", "cleanup_error", "log_review_error")))


def run(args):
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    adb = [str(args.adb), "-s", args.serial]
    report = {"passed": False, "functional_passed": False, "cases": [], "restoration": {},
              "scope": "Actual key/touch Back and Cancel only; no database row and no save operation",
              "limitations": ["Accessibility bounds are checked; retained screenshots require manual review for title glyph clipping.",
                              "Only this API 35 x86_64 emulator, 800x412 dp, font scale 2.0 is covered.",
                              "Physical Android Back key injection exercises Back dispatch, not gesture-navigation animation.",
                              "OK is checked for visible enabled bounds, but never clicked; save behavior is outside this test."],
              "actions": [], "captures": []}
    original = None
    changed = False
    pids = set()
    marker = "BEGIN-CLIP-LARGEFONT-" + uuid.uuid4().hex
    identity = {"serial": args.serial, "source_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                "target_display": {"size_px": list(TARGET_SIZE), "density_dpi": 320, "font_scale": 2.0},
                "log_capture_marker": marker, "packages": {}, "harness_sha256": {}}
    for name in ("editor_windows.py", "runtime_audit.py", "run_navigation.py"):
        identity["harness_sha256"][name] = hashlib.sha256((REPO / "script/quality" / name).read_bytes()).hexdigest()

    def save(name, data):
        (out / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def shell(*command):
        # adb shell concatenates its argv: quote once for the remote POSIX shell.
        command = list(map(str, command))
        invocation = adb + ["shell", shlex.join(command)]
        completed = subprocess.run(invocation, text=True, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, timeout=60, check=False)
        with (out / "commands.jsonl").open("a", encoding="utf-8") as stream:
            stream.write(json.dumps({"command": command, "returncode": completed.returncode,
                                     "output": completed.stdout}, ensure_ascii=False) + "\n")
        require(completed.returncode == 0, "adb failed; see commands.jsonl: " + shlex.join(command))
        return completed.stdout.strip()

    def selected(method, label):
        status = select_test_ime(shell, method, attempts=5)
        save(label + "-ime-selection.json", status)
        require(status["selected"], "IME selection did not stabilize: " + method)

    def app_pid():
        matches = [int(row.split()[1]) for row in shell("ps", "-A").splitlines()
                   if row.split() and row.split()[-1] == PACKAGE]
        require(len(matches) <= 1, "Multiple main Trime processes")
        pids.update(matches)
        return matches[0] if matches else None

    def focus(label):
        windows = shell("dumpsys", "window", "displays")
        (out / (label + "-windows.txt")).write_text(windows + "\n")
        current = re.findall(r"mCurrentFocus=Window\{([^\s}]+)\s+u\d+\s+([^}]+)\}", windows)
        require(len(current) == 1, "Expected one focused window")
        return {"window_token": current[0][0], "component": current[0][1]}

    def screenshot(label):
        destination = out / (label + ".png")
        with destination.open("wb") as png:
            subprocess.run(adb + ["exec-out", "screencap", "-p"], stdout=png, check=True, timeout=60)
        raw = destination.read_bytes()
        require(raw[:8] == b"\x89PNG\r\n\x1a\n" and len(raw) > 32, "Screenshot was not a PNG")
        size = struct.unpack(">II", raw[16:24])
        require(size == TARGET_SIZE, "Unexpected actual screenshot size: " + str(size))
        return {"png_sha256": hashlib.sha256(raw).hexdigest(), "size": list(size)}

    def capture(label, expected=(), attempts=3):
        root, xml, history = capture_editor_window(shell, expected, attempts=attempts)
        save(label + "-capture.json", history)
        (out / (label + ".xml")).write_text(xml or "", encoding="utf-8")
        entry = {"label": label, "hierarchy_available": root is not None}
        report["captures"].append(entry)
        entry.update(screenshot(label))
        require(root is not None, "Fresh all-window observer capture failed: " + label)
        return root

    def wait_capture(label, predicate, expected=(), timeout=60):
        deadline = time.monotonic() + timeout
        attempt = 0
        while True:
            attempt += 1
            root = capture(f"{label}-{attempt:02d}", expected)
            if predicate(root):
                return root
            require(time.monotonic() < deadline and attempt < 20, "UI state did not stabilize: " + label)
            time.sleep(.3)

    def tap(node, label):
        rect = box(node)
        require(screen_contains(rect) and node.get("enabled") == "true", "Cannot tap unavailable control")
        x, y = (rect[0] + rect[2]) // 2, (rect[1] + rect[3]) // 2
        report["actions"].append({"name": label, "action": "actual_screen_tap", "node": dict(node.attrib),
                                  "x": x, "y": y})
        shell("input", "tap", x, y)

    def ime_dump(label):
        value = shell("dumpsys", "input_method")
        (out / (label + "-input-method.txt")).write_text(value + "\n")
        return value

    def keyboard_visible(root, method_package):
        return any(window.get("type") == "2" and any(n.get("package") == method_package for n in window.iter("node"))
                   for window in root.findall("window"))

    def case(name, lines):
        item = {"name": name, "passed": False, "expected_text": "\n".join(lines)}
        report["cases"].append(item)
        selected(args.latin_ime, name + "-latin")
        launch = shell("su", "0", "am", "start", "-W", "--user", "0", "-n", ACTIVITY,
                       "-f", "0x10000000")
        (out / (name + "-launch.txt")).write_text(launch + "\n")
        require("Status: ok" in launch and "Error:" not in launch and "Warning: Activity not started" not in launch,
                "Failed to launch a fresh unbound ClipEditActivity")
        require(app_pid() is not None, "Trime process missing")
        root = wait_capture(name + "-latin-ready", lambda r: bool(nodes(r, FIELD)) and keyboard_visible(r, args.latin_ime.split("/")[0]))
        original_focus = focus(name + "-launched")
        require(original_focus["component"] == ACTIVITY, "ClipEditActivity was not focused")
        item["original_focus"] = original_focus
        # The launch supplies neither id nor clip_type, so these keys edit only
        # this new Activity's transient field; they cannot load an existing row.
        for index, line in enumerate(lines):
            if index:
                shell("input", "keyevent", "KEYCODE_ENTER")
            shell("input", "text", line)
        report["actions"].append({"name": name, "action": "actual_ASCII_and_ENTER_input", "lines": lines})
        expected = item["expected_text"]
        root = wait_capture(name + "-typed", lambda r: bool(nodes(r, FIELD)) and unique(r, FIELD).get("text") == expected)
        selected(IME, name + "-trime")
        root = wait_capture(name + "-trime-visible", lambda r: bool(nodes(r, FIELD)) and bool(nodes(r, PACKAGE + ":id/keyboard_view")))
        keyboard_box = box(unique(root, PACKAGE + ":id/keyboard_view"))
        require(screen_contains(keyboard_box) and keyboard_box[2] - keyboard_box[0] > 100 and
                keyboard_box[3] - keyboard_box[1] > 100, "Visible Trime keyboard does not have usable geometry")
        item["trime_keyboard_bounds_before_back"] = list(keyboard_box)
        require(unique(root, FIELD).get("text") == expected, "Selecting Trime changed the typed text")
        require(focus(name + "-before-back") == original_focus, "Editor window changed before Back")
        dump = ime_dump(name + "-before-back")
        require("mInputShown=true" in dump, "Trime input was not shown before Back")
        end = str(len(expected))
        caret_at_end = (re.search(r"initialSelStart=" + end + r" initialSelEnd=" + end + r"\b", dump) is not None or
                        re.search(r"mCursorSelStart=" + end + r" mCursorSelEnd=" + end + r"\b", dump) is not None)
        require(caret_at_end, "Cannot prove last-line/end caret before Back; inspect IME dump")
        item["caret_at_end_before_back"] = True
        shell("input", "keyevent", "KEYCODE_BACK")
        report["actions"].append({"name": name, "action": "actual_KEYCODE_BACK", "count": 1})
        root = wait_capture(name + "-after-back", lambda r: not any(w.get("type") == "2" for w in r.findall("window")))
        require(focus(name + "-after-back") == original_focus, "Back closed or replaced the editor instead of only hiding the IME")
        require("mInputShown=false" in ime_dump(name + "-after-back"), "IME still reports shown after Back")
        require(unique(root, FIELD).get("text") == expected, "Back changed the edit text")
        controls = {"title": unique(root, "android:id/title"), "ok": unique(root, OK), "cancel": unique(root, CANCEL)}
        item["hidden_controls"] = {key: dict(node.attrib) for key, node in controls.items()}
        title, ok, cancel = (controls[key] for key in ("title", "ok", "cancel"))
        require(title.get("text", "").strip(), "Title text is missing after hiding IME")
        for key, node in controls.items():
            require(screen_contains(box(node)) and node.get("enabled") == "true", key + " is outside the screen or disabled")
        require(not intersects(box(ok), box(cancel)), "OK and Cancel overlap")
        require(box(title)[3] <= min(box(ok)[1], box(cancel)[1]), "Title overlaps button row")
        field_bounds = box(unique(root, FIELD))
        require(not intersects(field_bounds, box(ok)) and not intersects(field_bounds, box(cancel)),
                "Edit text overlaps OK/Cancel after hiding IME")
        item["editor_clear_of_buttons_after_back"] = True
        # Reject touch targets hidden behind visible system-bar windows. The
        # retained screenshot is still required for actual glyph clipping review.
        bars = [box(window.find("node")) for window in root.findall("window")
                if window.get("type") == "3" and window.find("node") is not None]
        for key, node in controls.items():
            require(not any(intersects(box(node), bar) for bar in bars), key + " overlaps a system window")
        item["same_window_after_back"] = True
        item["text_preserved_after_back"] = True
        item["controls_inside_screen_after_back"] = True
        tap(cancel, name + "-cancel")
        wait_capture(name + "-cancelled", lambda r: not nodes(r, FIELD))
        require(focus(name + "-cancelled")["component"] != ACTIVITY, "Cancel did not close ClipEditActivity")
        item["cancel_closed_by_actual_tap"] = True
        item["passed"] = True
        save("report.json", report)
        print("PASS:", name, flush=True)

    try:
        require(shell("getprop", "ro.kernel.qemu") == "1" and shell("getprop", "ro.hardware") in {"ranchu", "goldfish"}, "Marked disposable emulator only")
        require(shell("cat", f"/sdcard/Android/data/{PACKAGE}/files/runtime-audit-dedicated") == "", "Dedicated installation marker missing")
        identity["api"] = int(shell("getprop", "ro.build.version.sdk"))
        require(identity["api"] == 35, "This supplemental driver is bounded to API 35")
        identity["fingerprint"] = shell("getprop", "ro.build.fingerprint")
        for package in (PACKAGE, OBSERVER):
            remote = shell("pm", "path", package).removeprefix("package:")
            require(remote.startswith("/") and "\n" not in remote, "Expected one installed APK: " + package)
            with tempfile.TemporaryFile() as installed:
                subprocess.run(adb + ["exec-out", "cat", remote], stdout=installed, check=True, timeout=60)
                installed.seek(0)
                digest = hashlib.file_digest(installed, "sha256").hexdigest()
            identity["packages"][package] = {"installed_path": remote, "apk_sha256": digest}
            if package == PACKAGE:
                with args.apk.open("rb") as apk:
                    expected = hashlib.file_digest(apk, "sha256").hexdigest()
                identity["expected_apk_sha256"] = expected
                save("identity.json", identity)
                require(digest == expected, "Installed APK SHA differs from --apk")
                identity["verified_apk"] = str(args.apk.resolve())
        save("identity.json", identity)
        require(shell("su", "0", "id", "-u") == "0", "Emulator su 0 is required to launch the nonexported Activity")
        require(args.latin_ime in shell("ime", "list", "-s").splitlines(), "Expected installed LatinIME is unavailable")
        initial_focus = focus("preflight")
        require(initial_focus["component"] != ACTIVITY, "Refusing to replace an already-open ClipEditActivity")
        original = {name: shell("wm", name) for name in ("size", "density")}
        original["settings"] = {key: shell("settings", "get", "system", key)
                                for key in ("font_scale", "accelerometer_rotation", "user_rotation")}
        original["ime"] = shell("settings", "get", "secure", "default_input_method")
        require(original["ime"] not in {"", "null"}, "Original IME cannot be restored")
        save("original-display.json", original)
        with record_logcat(adb, out / "logcat-full.txt"):
            shell("log", "-t", "TrimeRuntimeAudit", "-p", "i", marker)
            deadline = time.monotonic() + 10
            while marker not in (out / "logcat-full.txt").read_text():
                require(time.monotonic() < deadline, "Log recorder did not reach the unique start marker")
                time.sleep(.1)
            try:
                changed = True
                shell("settings", "put", "system", "accelerometer_rotation", "0")
                shell("settings", "put", "system", "user_rotation", "0")
                shell("wm", "size", "1600x824")
                shell("wm", "density", "320")
                shell("settings", "put", "system", "font_scale", "2.0")
                time.sleep(1)
                require(shell("settings", "get", "system", "font_scale") == "2.0", "Font scale did not apply")
                case("short", ["ClipShort42"])
                case("eight-lines", [f"Line{i}abc" for i in range(1, 9)])
                report["functional_passed"] = True
            except BaseException as error:
                report["error"] = f"{type(error).__name__}: {error}"
                try:
                    capture("failure-final")
                    focus("failure-final")
                    ime_dump("failure-final")
                except Exception as capture_error:
                    report["failure_capture_error"] = str(capture_error)
                if isinstance(error, (KeyboardInterrupt, SystemExit)):
                    report["interrupted"] = True
            finally:
                # HOME stops only the temporary unsaved dialog if it is still
                # focused; onStop closes it. No app force-stop or DB mutation.
                try:
                    if focus("cleanup")["component"] == ACTIVITY:
                        shell("input", "keyevent", "KEYCODE_HOME")
                        report["cleanup_transient_editor"] = "HOME dispatched to close temporary unsaved dialog via onStop"
                    app_pid()
                except Exception as error:
                    report["cleanup_error"] = str(error)
    except BaseException as error:
        report["error"] = f"{type(error).__name__}: {error}"
    finally:
        if changed and original is not None:
            errors = []
            for name in ("size", "density"):
                try:
                    override = re.search(r"Override (?:size|density): (\S+)", original[name])
                    shell("wm", name, override.group(1) if override else "reset")
                except Exception as error:
                    errors.append(name + ": " + str(error))
            for key, value in original["settings"].items():
                try:
                    shell("settings", "delete", "system", key) if value == "null" else shell("settings", "put", "system", key, value)
                except Exception as error:
                    errors.append(key + ": " + str(error))
            try:
                selected(original["ime"], "restore")
            except Exception as error:
                errors.append("IME: " + str(error))
            try:
                observed = {name: shell("wm", name) for name in ("size", "density")}
                observed["settings"] = {key: shell("settings", "get", "system", key) for key in original["settings"]}
                observed["ime"] = shell("settings", "get", "secure", "default_input_method")
                report["restoration"] = {"passed": not errors and observed == original, "observed": observed, "errors": errors}
            except Exception as error:
                report["restoration"] = {"passed": False, "errors": errors + [str(error)]}
        finalize_log_review(report, out, pids, marker)
        report["artifact_sha256"] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                     for p in sorted(out.iterdir()) if p.is_file() and p.name != "report.json"}
        save("report.json", report)
        print(json.dumps({"passed": report["passed"], "report": str(out / "report.json"),
                          "error": report.get("error") or report.get("log_review_error"),
                          "diagnostics_passed": report["diagnostics_passed"],
                          "restored": report["restoration"].get("passed")}, indent=2), flush=True)
    return 0 if report["passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=REPO / "build/t06-runtime" / ("clip-largefont-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")))
    parser.add_argument("--adb", type=Path, help="Executable adb; otherwise resolve SDK environment/PATH")
    parser.add_argument("--serial", choices=["emulator-5560"], default="emulator-5560")
    parser.add_argument("--latin-ime", default="com.android.inputmethod.latin/.LatinIME")
    args = parser.parse_args()
    args.adb = resolve_adb(args.adb)
    require(args.apk.is_file(), "--apk must be a local frozen APK")
    with device_audit_lock(args.serial):
        return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
