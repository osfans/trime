# SPDX-License-Identifier: GPL-3.0-or-later
"""Synthetic executable files only; never start adb or contact a device."""
import os
from pathlib import Path
import tempfile
import unittest

from android_tools import resolve_adb


class AdbResolutionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.name = "adb.exe" if os.name == "nt" else "adb"

    def tool(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"synthetic-not-executed")
        path.chmod(0o700)
        return path

    def test_explicit_tool_wins_and_invalid_explicit_never_falls_back(self):
        explicit = self.tool(self.root / "explicit" / self.name)
        fallback = self.tool(self.root / "path" / self.name)
        self.assertEqual(explicit.resolve(), resolve_adb(explicit, environment={}, find_executable=lambda _: str(fallback)))
        with self.assertRaises(FileNotFoundError):
            resolve_adb(self.root / "missing", environment={}, find_executable=lambda _: str(fallback))

    def test_sdk_environment_order_and_path_fallback(self):
        sdk = self.root / "sdk"; home = self.root / "other-sdk"
        first = self.tool(sdk / "platform-tools" / self.name)
        second = self.tool(home / "platform-tools" / self.name)
        environment = {"ANDROID_SDK_ROOT": str(sdk), "ANDROID_HOME": str(home)}
        self.assertEqual(first.resolve(), resolve_adb(environment=environment, find_executable=lambda _: None))
        first.unlink()
        self.assertEqual(second.resolve(), resolve_adb(environment=environment, find_executable=lambda _: None))
        second.unlink()
        fallback = self.tool(self.root / "path" / self.name)
        self.assertEqual(fallback.resolve(), resolve_adb(environment=environment, find_executable=lambda _: str(fallback)))

    def test_missing_tools_fail_before_device_work(self):
        with self.assertRaises(FileNotFoundError):
            resolve_adb(environment={}, find_executable=lambda _: None)


if __name__ == "__main__":
    unittest.main()
