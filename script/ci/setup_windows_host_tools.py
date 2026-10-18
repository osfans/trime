#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Discover the installed MSVC host tools without choosing a VS generator version."""
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile


ENVIRONMENT_KEYS = frozenset(("PATH", "INCLUDE", "LIB", "LIBPATH", "VCTOOLSINSTALLDIR",
                              "VCTOOLSVERSION", "VSINSTALLDIR", "WINDOWSSDKDIR", "WINDOWSSDKVERSION",
                              "UNIVERSALCRTSDKDIR", "UCRTVERSION", "WINDOWSSDKBINPATH",
                              "WINDOWSSDKVERBINPATH", "VSCMD_ARG_HOST_ARCH", "VSCMD_ARG_TGT_ARCH"))


def native_architecture(machine):
    aliases = {"amd64": "x64", "x86_64": "x64", "aarch64": "arm64", "arm64": "arm64",
               "x86": "x86", "i386": "x86", "i486": "x86", "i586": "x86", "i686": "x86"}
    if machine.lower() not in aliases:
        raise ValueError("Unsupported Windows host architecture: " + machine)
    return aliases[machine.lower()]


def parse_environment(raw, architecture):
    values = {}
    for line in raw.decode("utf-16le").splitlines():
        name, separator, value = line.partition("=")
        name = name.upper()
        if separator and name in ENVIRONMENT_KEYS:
            if name in values or not value or "\x00" in value:
                raise ValueError("Invalid or duplicate MSVC environment variable: " + name)
            values[name] = value
    required = {"PATH", "INCLUDE", "LIB", "LIBPATH", "VCTOOLSINSTALLDIR",
                "VSCMD_ARG_HOST_ARCH", "VSCMD_ARG_TGT_ARCH"}
    if not required <= values.keys():
        raise ValueError("MSVC environment is incomplete")
    if any(values[key].lower() != architecture for key in ("VSCMD_ARG_HOST_ARCH", "VSCMD_ARG_TGT_ARCH")):
        raise ValueError("MSVC environment does not target the native host architecture")
    return values


def vcvars_command(comspec, vcvars, architecture, destination):
    if architecture not in ("x64", "arm64", "x86"):
        raise ValueError("Unsupported vcvarsall architecture")
    for value in (comspec, vcvars, destination):
        if any(character in str(value) for character in ('"', "%", "!", "\r", "\n", "\x00")):
            raise ValueError("Invalid command path")
    # /s removes the command's outer quotes; CALL preserves the subsequent SET.
    # /u makes the SET snapshot UTF-16LE, independently of the system code page.
    return f'"{comspec}" /d /u /v:off /s /c "call "{vcvars}" {architecture} && set > "{destination}""'


def main():
    if sys.platform != "win32":
        raise SystemExit("MSVC environment setup is Windows-only")
    architecture = native_architecture(platform.machine())
    root = Path(os.environ.get("ProgramFiles(x86)") or os.environ["ProgramFiles"])
    vswhere = root / "Microsoft Visual Studio/Installer/vswhere.exe"
    if not vswhere.is_file():
        raise SystemExit("Missing installed Visual Studio locator: " + str(vswhere))
    component = "ARM64" if architecture == "arm64" else "x86.x64"
    discovery = subprocess.run([str(vswhere), "-latest", "-prerelease", "-products", "*", "-requires",
                                "Microsoft.VisualStudio.Component.VC.Tools." + component,
                                "-format", "json", "-utf8"], capture_output=True, timeout=30)
    if discovery.stderr:
        sys.stderr.buffer.write(discovery.stderr)
        raise SystemExit("Visual Studio locator emitted stderr diagnostics")
    discovery.check_returncode()
    installations = json.loads(discovery.stdout.decode("utf-8-sig"))
    if len(installations) != 1:
        raise SystemExit("Expected one latest Visual Studio installation with native C++ tools")
    installation = Path(installations[0]["installationPath"])
    vcvars = installation / "VC/Auxiliary/Build/vcvarsall.bat"
    if not vcvars.is_file():
        raise SystemExit("Missing installed vcvarsall.bat: " + str(vcvars))
    with tempfile.TemporaryDirectory(prefix="trime-msvc-") as temporary:
        destination = Path(temporary) / "environment.txt"
        result = subprocess.run(vcvars_command(os.environ["COMSPEC"], vcvars, architecture, destination),
                                capture_output=True, timeout=120)
        for stream, payload in ((sys.stdout, result.stdout), (sys.stderr, result.stderr)):
            if payload:
                stream.write(payload.decode("utf-16le"))
        result.check_returncode()
        values = parse_environment(destination.read_bytes(), architecture)
    toolchain = Path(values["VCTOOLSINSTALLDIR"]).resolve(strict=True)
    tools = {}
    for name in ("cl.exe", "link.exe", "lib.exe", "rc.exe", "mt.exe"):
        found = shutil.which(name, path=values["PATH"])
        if not found or not Path(found).is_file():
            raise SystemExit("Missing MSVC/Windows SDK host tool: " + name)
        tools[name] = str(Path(found).resolve(strict=True))
        if name in ("cl.exe", "link.exe", "lib.exe") and not Path(tools[name]).is_relative_to(toolchain):
            raise SystemExit("Host compiler/linker resolved outside the selected MSVC toolchain: " + name)
    os.environ.update(values)
    with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as stream:
        for name, value in sorted(values.items()):
            stream.write(name + "=" + value + "\n")
    print(json.dumps({"visual_studio_version": installations[0]["installationVersion"],
                      "architecture": architecture, "tools": tools}, indent=2))


if __name__ == "__main__":
    main()
