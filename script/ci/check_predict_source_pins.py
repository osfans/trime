#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check raw prediction source bytes before a prebuilt JNI cache can skip CMake."""

import hashlib
from pathlib import Path
import re


def main():
    root = Path(__file__).resolve().parents[2]
    guard = root / "app/src/main/jni/cmake/RimePredictCompat.cmake"
    blocks = re.findall(r"(?ms)^  set\(pinned\n(.*?)\)\n", guard.read_text(encoding="utf-8"))
    if len(blocks) != 1:
        raise SystemExit("Re-audit the prediction source pin block")
    pins = []
    for line in blocks[0].splitlines():
        binding = re.fullmatch(r'    "([A-Za-z0-9_.-]+\.(?:cc|h))\|([0-9a-f]{64})"', line)
        if binding is None:
            raise SystemExit("Re-audit the prediction source pin syntax")
        pins.append(binding.groups())
    if len(pins) != 9 or len({name for name, _ in pins}) != len(pins):
        raise SystemExit("Re-audit the prediction source pin list")
    failures = []
    for name, expected in pins:
        data = (root / "app/src/main/jni/librime-predict/src" / name).read_bytes()
        actual = hashlib.sha256(data).hexdigest()
        crlf = data.count(b"\r\n")
        print(f"{name}: expected={expected} actual={actual} bytes={len(data)} CRLF={crlf}", flush=True)
        if actual != expected:
            failures.append(name)
    if failures:
        raise SystemExit("Prediction source pin mismatch: " + ", ".join(failures))
    print("Verified all 9 pinned prediction source files", flush=True)


if __name__ == "__main__":
    main()
