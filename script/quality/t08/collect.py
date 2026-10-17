#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only, bounded T08 capture on an explicitly selected physical Android device."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import threading
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from runtime_audit import device_audit_lock
from metrics import parse_meminfo


def sha256(path):
    result = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


def check_device(serial, props, package_dump):
    if serial.startswith("emulator-") or props["ro.kernel.qemu"] == "1" or props["ro.hardware"] in {"ranchu", "goldfish", "cutf_cvm"}:
        raise ValueError("T08 acceptance capture requires a physical device")
    flags = re.findall(r"\b(?:pkgFlags|flags)=\[([^]]*)\]", package_dump)
    if not flags or not re.search(r"\bversionCode=\d+", package_dump):
        raise ValueError("Cannot verify installed package identity and application flags")
    if any("DEBUGGABLE" in flag.split() for flag in flags):
        raise ValueError("Debuggable builds cannot supply release acceptance evidence")


def elapsed_ns(adb):
    uptime = subprocess.check_output([*adb, "shell", "cat", "/proc/uptime"], text=True, timeout=10)
    # /proc/uptime uses CLOCK_BOOTTIME, as does elapsedRealtimeNanos on Android.
    return int(float(uptime.split()[0]) * 1_000_000_000)


class TraceRecorder:
    def __init__(self, adb, destination, max_bytes):
        self.adb, self.destination, self.max_bytes = adb, destination, max_bytes
        self.error = None
        self.bytes_written = 0
        self.process = None

    def __enter__(self):
        self.process = subprocess.Popen([*self.adb, "logcat", "-v", "threadtime", "TrimePerf:I", "*:S"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        self.thread = threading.Thread(target=self._copy, daemon=True)
        self.thread.start()
        return self

    def _copy(self):
        try:
            with self.destination.open("xb") as output:
                while line := self.process.stdout.readline(4097):
                    if len(line) > 4096:
                        raise ValueError("Unexpected oversized trace line; capture incomplete")
                    if self.bytes_written + len(line) > self.max_bytes:
                        raise ValueError("Trace byte limit reached; shorten the capture, do not discard input records")
                    output.write(line)
                    self.bytes_written += len(line)
        except Exception as error:
            self.error = str(error)
            self.process.terminate()

    def __exit__(self, *unused):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=10)
                self.error = self.error or "Trace recorder required forced termination"
        elif self.error is None:
            self.error = "Trace recorder exited before the capture ended"
        self.thread.join(timeout=10)
        if self.thread.is_alive():
            self.error = self.error or "Trace writer did not stop"
        self.process.stdout.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--release-apk", type=Path, required=True)
    parser.add_argument("--protocol", type=Path, required=True, help="Frozen workload/settings description; identical for baseline and candidate")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--package", default="com.osfans.trime")
    parser.add_argument("--phase", choices=("steady", "first-deployment"), default="steady")
    parser.add_argument("--round", type=int, default=1)
    parser.add_argument("--seconds", type=int, default=180)
    parser.add_argument("--interval", type=int, default=5)
    parser.add_argument("--max-log-mib", type=int, default=16)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.package):
        parser.error("Invalid package identifier")
    if not 10 <= args.seconds <= 1800 or not 2 <= args.interval <= 60 or args.round < 1 or not 1 <= args.max_log_mib <= 64:
        parser.error("Use 10..1800 seconds, 2..60 second intervals, positive round, 1..64 MiB log limit")
    if args.protocol.stat().st_size > 65536:
        parser.error("Protocol description must be at most 64 KiB")
    json.loads(args.protocol.read_text())
    adb = [args.adb, "-s", args.serial]

    def shell(*command, timeout=20):
        return subprocess.check_output([*adb, "shell", *command], text=True, timeout=timeout).strip()

    def find_pid():
        result = subprocess.run([*adb, "shell", "pidof", args.package], capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            values = result.stdout.split()
            if len(values) == 1 and values[0].isdigit():
                return int(values[0])
            if values:
                raise ValueError("Expected a single main application process")
        # API 21 can lack pidof and ps -A. Match the exact process name.
        process_list = shell("ps")
        header = process_list.splitlines()[0].split() if process_list else []
        if "PID" not in header:
            raise ValueError("Cannot find PID column in process list")
        values = [line.split()[header.index("PID")] for line in process_list.splitlines()[1:] if line.split() and line.split()[-1] == args.package]
        if len(values) > 1:
            raise ValueError("Multiple main application processes")
        return int(values[0]) if values else None

    args.output.mkdir(parents=True, exist_ok=False)
    manifest = {"format": 1, "status": "incomplete", "phase": args.phase, "round": args.round,
                "package": args.package, "serial": args.serial, "seconds": args.seconds,
                "interval_seconds": args.interval, "protocol_sha256": sha256(args.protocol),
                "release_apk_sha256": sha256(args.release_apk), "samples": 0}
    manifest_path = args.output / "capture.json"

    def save():
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")

    save()
    with device_audit_lock(args.serial):
        try:
            props = {name: shell("getprop", name) for name in (
                "ro.kernel.qemu", "ro.hardware", "ro.product.model", "ro.build.fingerprint", "ro.build.version.sdk")}
            package_dump = shell("dumpsys", "package", args.package)
            (args.output / "package.txt").write_text(package_dump + "\n")
            check_device(args.serial, props, package_dump)
            manifest.update(properties=props, physical_device=True, debuggable=False,
                            boot_id=shell("cat", "/proc/sys/kernel/random/boot_id"))
            manifest["display_configuration"] = {
                "size": shell("wm", "size"), "density": shell("wm", "density"),
                "font_scale": shell("settings", "get", "system", "font_scale"),
                "user_rotation": shell("settings", "get", "system", "user_rotation"),
            }
            (args.output / "battery-start.txt").write_text(shell("dumpsys", "battery") + "\n")
            paths = shell("pm", "path", args.package).splitlines()
            base = [line.removeprefix("package:") for line in paths if line.endswith("/base.apk")]
            if not base and len(paths) == 1:
                base = [paths[0].removeprefix("package:")]
            if len(paths) != 1 or len(base) != 1 or not re.fullmatch(r"/[A-Za-z0-9_./+=@~-]+", base[0]):
                raise ValueError("Expected one standalone installed release APK")
            with tempfile.TemporaryFile() as source:
                subprocess.run([*adb, "exec-out", "cat", base[0]], stdout=source, check=True, timeout=120)
                source.seek(0)
                installed = hashlib.sha256()
                for chunk in iter(lambda: source.read(1024 * 1024), b""):
                    installed.update(chunk)
            manifest["installed_apk_sha256"] = installed.hexdigest()
            if installed.hexdigest() != manifest["release_apk_sha256"]:
                raise ValueError("Installed APK does not match the frozen release artifact")
            (args.output / "protocol.json").write_bytes(args.protocol.read_bytes())
            pid = find_pid()
            if pid is None and args.phase == "steady":
                raise ValueError("Start the deployed keyboard before collecting steady-state data")
            manifest["pid"] = pid
            manifest["start_elapsed_ns"] = elapsed_ns(adb)
            save()
            print(f"Collecting {args.phase} round {args.round} for {args.seconds}s; perform the frozen workload now", flush=True)
            started = time.monotonic()
            with TraceRecorder(adb, args.output / "trace.log", args.max_log_mib * 1024 * 1024) as recorder, (args.output / "memory.jsonl").open("x") as samples:
                while time.monotonic() - started < args.seconds:
                    if recorder.error or recorder.process.poll() is not None:
                        raise RuntimeError(recorder.error or "Trace recorder ended early")
                    current = find_pid()
                    if pid is None and current is not None:
                        pid = current
                        manifest["pid"] = pid
                    if pid is not None and current != pid:
                        raise ValueError("Application process changed/disappeared; residency and trace evidence are incomplete")
                    if pid is not None:
                        stamp = elapsed_ns(adb)
                        raw = shell("dumpsys", "meminfo", str(pid), timeout=30)
                        (args.output / f"meminfo-{manifest['samples']:04d}.txt").write_text(raw + "\n")
                        measured = parse_meminfo(raw)
                        samples.write(json.dumps({"t_ns": stamp, "pid": pid, **measured}) + "\n")
                        samples.flush()
                        manifest["samples"] += 1
                        print(f"sample {manifest['samples']} total_pss_kib={measured['total_pss_kib']}", flush=True)
                    save()
                    # Bounded polling; no device input, force-stop, deploy, or preference writes.
                    remaining = args.seconds - (time.monotonic() - started)
                    if remaining > 0:
                        time.sleep(min(args.interval, remaining))
            if recorder.error:
                raise RuntimeError(recorder.error)
            if manifest["samples"] < 3:
                raise ValueError("Fewer than three memory samples; capture incomplete")
            manifest.update(status="complete", end_elapsed_ns=elapsed_ns(adb), trace_bytes=recorder.bytes_written)
            (args.output / "battery-end.txt").write_text(shell("dumpsys", "battery") + "\n")
        except BaseException as error:
            manifest["status"] = "incomplete"
            manifest["error"] = str(error) or type(error).__name__
            raise
        finally:
            save()


if __name__ == "__main__":
    main()
