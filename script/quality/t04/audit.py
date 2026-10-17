#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the APK resource manifest used by DataManager deployment."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

from layers import sha


def verify_apk(apk):
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate APK entry")
        manifest = json.loads(archive.read("assets/checksums.json"))
        files = manifest["files"]
        # Matches DataChecksumsPlugin's sorted Kotlin joinToString, not JSON hashing.
        aggregate = hashlib.sha256(", ".join(key + files[key] for key in sorted(files)).encode()).hexdigest()
        if aggregate != manifest["sha256"]:
            raise ValueError("Resource aggregate checksum mismatch")
        checked = {}
        for name, expected in files.items():
            if not expected:
                continue
            with archive.open("assets/" + name) as stream:
                actual = hashlib.file_digest(stream, "sha256").hexdigest()
            if actual != expected:
                raise ValueError(f"Bundled resource checksum mismatch: {name}")
            checked[name] = actual
        shared = {name.removeprefix("assets/") for name in names
                  if name.startswith("assets/shared/") and not name.endswith("/")}
        if shared != {name for name in checked if name.startswith("shared/")}:
            raise ValueError("Shared resource missing from deployment manifest")
    return {"apk_sha256": sha(apk), "resource_aggregate_sha256": aggregate,
            "verified_file_count": len(checked), "files": checked}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    result = json.dumps(verify_apk(args.apk), indent=2) + "\n"
    if args.out:
        with args.out.open("x") as stream:
            stream.write(result)
    else:
        print(result, end="")
