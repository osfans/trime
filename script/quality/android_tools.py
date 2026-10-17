# SPDX-License-Identifier: GPL-3.0-or-later
"""Resolve an actual local adb without machine-specific checkout/SDK defaults."""

import os
from pathlib import Path
import shutil


def resolve_adb(explicit=None, *, environment=None, find_executable=None):
    environment = os.environ if environment is None else environment
    find_executable = shutil.which if find_executable is None else find_executable
    if explicit is not None:
        candidates = [Path(explicit)]
    else:
        executable = "adb.exe" if os.name == "nt" else "adb"
        candidates = [Path(environment[name]) / "platform-tools" / executable
                      for name in ("ANDROID_SDK_ROOT", "ANDROID_HOME") if environment.get(name)]
        located = find_executable(executable)
        if located:
            candidates.append(Path(located))
    for candidate in candidates:
        resolved = candidate.expanduser().resolve()
        if resolved.is_file() and os.access(resolved, os.X_OK):
            return resolved
    raise FileNotFoundError("No executable adb: pass --adb, set ANDROID_SDK_ROOT/ANDROID_HOME, or add adb to PATH")
