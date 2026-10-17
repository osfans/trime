#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Rime community
# SPDX-License-Identifier: GPL-3.0-or-later

"""Install CI SDK packages using the hash-pinned official Android CLI."""

import hashlib
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import urllib.request


VERSION = "1.0.16625370"
ARTIFACTS = {
    "linux_x86_64": (90638488, "0e56633db6aa75c6c6e8be89a1e4cb6d985258173d3f9d0c0ce5ee9a9d3b8165"),
    "darwin_arm64": (87122032, "27e1e54b8e660cab9c608c2120bc5131be13a95f360b8dffc1152224a926c07c"),
    "darwin_x86_64": (88645552, "15a9c5e26600f2eb9441ab0529acdc7bf9afc938c3a355e0791afad27e664a77"),
    "windows_x86_64": (87366808, "3782acdbdc2a2f589f81de5b853a74623765d018dd6a91069028a2a8ea6a3554"),
}


def host_platform():
    machine = platform.machine().lower()
    key = (platform.system(), machine)
    supported = {
        ("Linux", "x86_64"): "linux_x86_64",
        ("Darwin", "arm64"): "darwin_arm64",
        ("Darwin", "x86_64"): "darwin_x86_64",
        ("Windows", "amd64"): "windows_x86_64",
        ("Windows", "x86_64"): "windows_x86_64",
    }
    if key not in supported:
        raise RuntimeError(f"Unsupported Android CLI host: {key}")
    return supported[key]


def verify_download(path, expected_size, expected_sha):
    if path.is_symlink() or not path.is_file() or path.stat().st_size != expected_size:
        raise RuntimeError(f"Unexpected Android CLI file or size: {path}")
    with path.open("rb") as stream:
        observed = hashlib.file_digest(stream, "sha256").hexdigest()
    if observed != expected_sha:
        raise RuntimeError(f"Android CLI SHA-256 mismatch: {observed}")
    print(f"Android CLI {VERSION}: {path} SHA-256 {observed}", flush=True)


def download_cli(directory, host):
    size, sha = ARTIFACTS[host]
    filename = "android-cli.exe" if host == "windows_x86_64" else "android-cli"
    target = directory / filename
    if not target.exists():
        url = f"https://dl.google.com/android/cli/{VERSION}/{host}/{filename}"
        print(f"Downloading {url}", flush=True)
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=directory, delete=False) as output:
                temporary = Path(output.name)
                with urllib.request.urlopen(url, timeout=60) as response:
                    received = 0
                    while chunk := response.read(1024 * 1024):
                        received += len(chunk)
                        if received > size:
                            raise RuntimeError("Android CLI download exceeds pinned size")
                        output.write(chunk)
            verify_download(temporary, size, sha)
            temporary.replace(target)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
    verify_download(target, size, sha)
    if host != "windows_x86_64":
        target.chmod(0o755)
    return target


def require_revision(path, expected):
    revisions = [
        line.partition("=")[2].strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.partition("=")[0].strip() == "Pkg.Revision"
    ]
    if revisions != [expected]:
        raise RuntimeError(f"Unexpected SDK package revision: {path}: {revisions}")


def main():
    host = host_platform()
    sdk_value = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_value:
        raise RuntimeError("CI runner must provide ANDROID_HOME or ANDROID_SDK_ROOT")
    sdk = Path(sdk_value).resolve()
    other_sdk = os.environ.get("ANDROID_HOME")
    if other_sdk and Path(other_sdk).resolve() != sdk:
        raise RuntimeError("ANDROID_HOME and ANDROID_SDK_ROOT disagree")
    directory = Path(os.environ["RUNNER_TEMP"]) / f"trime-android-cli-{VERSION}-{host}"
    directory.mkdir(exist_ok=True)
    cli = download_cli(directory, host)
    command = [str(cli), "--no-metrics", f"--sdk={sdk}"]
    subprocess.run(command + ["--version"], check=True, timeout=60)
    # The pinned CLI displays SDK terms and installs the license metadata.
    # EOF keeps CI noninteractive; all terms and diagnostics remain visible.
    subprocess.run(
        command + [
            "sdk", "install", "cmdline-tools/22.0", "platform-tools@37.0.1",
            "cmake/3.31.6", "platforms/android-37.0@2", "build-tools/36.0.0",
            "ndk/28.0.13004108",
        ],
        stdin=subprocess.DEVNULL,
        check=True,
        timeout=900,
    )
    require_revision(sdk / "cmdline-tools" / "22.0" / "source.properties", "22.0")
    require_revision(sdk / "cmake" / "3.31.6" / "source.properties", "3.31.6")
    require_revision(sdk / "platform-tools" / "source.properties", "37.0.1")
    require_revision(sdk / "platforms" / "android-37.0" / "source.properties", "2")
    require_revision(sdk / "build-tools" / "36.0.0" / "source.properties", "36.0.0")
    require_revision(sdk / "ndk" / "28.0.13004108" / "source.properties", "28.0.13004108")
    suffix = ".exe" if host == "windows_x86_64" else ""
    subprocess.run([str(sdk / "cmake" / "3.31.6" / "bin" / f"cmake{suffix}"), "--version"], check=True, timeout=30)
    subprocess.run([str(sdk / "platform-tools" / f"adb{suffix}"), "version"], check=True, timeout=30)
    with open(os.environ["GITHUB_PATH"], "a", encoding="utf-8") as stream:
        stream.write(str(sdk / "cmdline-tools" / "22.0" / "bin") + "\n")
        stream.write(str(sdk / "platform-tools") + "\n")
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as stream:
        stream.write(f"ANDROID_HOME={sdk}\nANDROID_SDK_ROOT={sdk}\n")


if __name__ == "__main__":
    main()
