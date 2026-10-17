#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Retain every runtime warning; functional success is not a warning-free run."""

import fcntl
import hashlib
import os
import re
import struct
import subprocess
import tempfile
from contextlib import contextmanager


@contextmanager
def device_audit_lock(serial):
    """Fail before touching a device already owned by another audit process."""
    key = hashlib.sha256(serial.encode()).hexdigest()
    path = os.path.join(tempfile.gettempdir(), f"trime-audit-{os.getuid()}-{key}.lock")
    fd = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "r+") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise RuntimeError(f"Another audit owns {serial}; wait for it to finish") from error
        try:
            yield
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


@contextmanager
def record_logcat(adb, destination):
    """Stream before starting the probe; do not depend on the finite device ring."""
    with destination.open("w") as output:
        process = subprocess.Popen([*adb, "logcat", "-v", "threadtime"],
                                   stdout=output, stderr=subprocess.STDOUT)
        try:
            yield
        finally:
            exited = process.poll()
            if exited is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
                    raise RuntimeError("Logcat recorder did not stop normally")
            else:
                raise RuntimeError(f"Logcat recorder exited early ({exited}); evidence is incomplete")


LOG_LINE = re.compile(
    r"^\s*\d{2}-\d{2}\s+[\d:.]+\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s?(.*)$"
)
PROJECT_DIAGNOSTICS = (
    "Invalid resource ID",
    "SPAN_EXCLUSIVE_EXCLUSIVE spans cannot have a zero length",
    "Unrecognized key",
    "Unrecognized modifier",
    "has waited",
    "requestLayout() improperly",
    "Failed to load theme",
    "FATAL EXCEPTION",
    "Fatal signal",
    "ANR in com.osfans.trime",
    "android.view.WindowLeaked",
    "has leaked window",
    "Glog is already initialized",
    "Encode failure:",
    "circular dependencies detected",
    "accessing blocking node with unresolved dependencies",
    "/build/default.schema.yaml",
    "Peer didn't provide a comm channel",
    "OnBackInvokedCallback is not enabled for the application",
    "requestCursorAnchorInfo on inactive InputConnection",
)


def filter_pid_log(log, pid):
    """API 21 logcat has no --pid; filter only by parsed PID, never by severity."""
    return "\n".join(line for line in log.splitlines()
                     if (match := LOG_LINE.match(line)) and int(match[1]) == pid) + "\n"


def log_after_marker(log, marker):
    """Scope reused-process probes without deleting the retained device backlog."""
    lines = log.splitlines()
    positions = [index for index, line in enumerate(lines)
                 if (match := LOG_LINE.match(line)) and match[4].strip() == 'TrimeRuntimeAudit' and match[5].strip() == marker]
    if len(positions) != 1:
        raise ValueError('Expected exactly one log capture boundary')
    return '\n'.join(lines[positions[0] + 1:]) + '\n'


def audit_log(log):
    warnings = []
    defects = []
    records = 0
    for line in log.splitlines():
        match = LOG_LINE.match(line)
        if match:
            records += 1
        if match and match[3] in "WEF":
            warnings.append({"pid": int(match[1]), "tid": int(match[2]),
                             "level": match[3], "tag": match[4].strip(),
                             "message": match[5], "line": line})
        application_warning = (match and match[3] in "WEF" and
                               (match[4].strip().startswith("[") or match[4].strip() == "rime.trime"))
        if application_warning or any(item in line for item in PROJECT_DIAGNOSTICS) or (
                "ThemeUtils" in line and "AppCompat" in line):
            defects.append(line)
    return {"capture_has_records": records > 0, "log_records": records,
            "warning_free": records > 0 and not warnings and not defects,
            "warnings": warnings, "known_project_diagnostics": defects}


def instrumentation_passed(returncode, output):
    return (returncode == 0
            and output.splitlines().count("INSTRUMENTATION_RESULT: passed=true") == 1
            and output.splitlines().count("INSTRUMENTATION_CODE: -1") == 1
            and not re.search(r"(?:INSTRUMENTATION_(?:FAILED|ABORTED)|FAIL:|FATAL EXCEPTION)", output))


def instrumentation_pid(output):
    values = re.findall(r"^INSTRUMENTATION_STATUS: audit_pid=(\d+)$", output, re.M)
    if len(values) != 1 or int(values[0]) <= 0:
        raise ValueError("Expected one process identity emitted by the running probe")
    return int(values[0])


def validate_screenshots(probe, screenshots):
    if probe == "t03":
        primary = []
        for theme in ("trime", "tongwenfeng.trime"):
            pattern = rf"t03-{re.escape(theme)}-\d+x\d+-font[\d.]+\.png"
            matches = [name for name in screenshots if re.fullmatch(pattern, name)]
            if len(matches) != 1:
                raise ValueError(f"Expected one primary screenshot for {theme}, got {matches}")
            primary.append(matches[0])
        expected = {name for base in primary for name in
                    [base, *(base[:-4] + suffix + ".png" for suffix in ("-left", "-right", "-height80"))]}
    else:
        primary = [f"{probe}-trime.png", f"{probe}-tongwenfeng.png"]
        expected = set(primary)
    if not expected.issubset(screenshots):
        raise ValueError(f"Missing theme screenshots: {sorted(expected - screenshots.keys())}")
    for name in expected:
        data = screenshots[name]
        if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
            raise ValueError(f"Invalid PNG screenshot: {name}")
        width, height = struct.unpack(">II", data[16:24])
        if min(width, height) < 300:
            raise ValueError(f"Unexpected screenshot dimensions: {name}: {width}x{height}")
    if screenshots[primary[0]] == screenshots[primary[1]]:
        raise ValueError("Both themes produced identical screenshots; check fallback and capture timing")
