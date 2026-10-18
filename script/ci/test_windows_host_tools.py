#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Host-tool boundaries; real cmd.exe checks run only on Windows."""
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import setup_windows_host_tools as host


class WindowsHostEnvironmentTests(unittest.TestCase):
    def environment(self, **changes):
        values = {"Path": r"C:\Program Files\Microsoft Visual Studio\18\Enterprise\VC\bin;C:\Windows",
                  "INCLUDE": "C:\\虚构目录\\include", "LIB": r"C:\VC\lib", "LIBPATH": r"C:\VC\refs",
                  "VCToolsInstallDir": r"C:\VC", "VSCMD_ARG_HOST_ARCH": "x64", "VSCMD_ARG_TGT_ARCH": "x64"}
        values.update(changes)
        return "\r\n".join(name + "=" + value for name, value in values.items()).encode("utf-16le")

    def test_utf16_snapshot_preserves_nonascii_paths_and_value_equals(self):
        value = host.parse_environment(self.environment(INCLUDE="C:\\虚构目录\\a=b"), "x64")
        self.assertEqual(value["INCLUDE"], "C:\\虚构目录\\a=b")
        self.assertIn("Microsoft Visual Studio\\18", value["PATH"])

    def test_export_does_not_copy_credentials_or_global_generator_options(self):
        value = host.parse_environment(self.environment(GITHUB_TOKEN="private", CMAKE_GENERATOR="Visual Studio 18 2026",
                                                        JAVA_HOME="foreign", CXX="other-compiler"), "x64")
        self.assertTrue(set(value) <= host.ENVIRONMENT_KEYS)
        for name in ("GITHUB_TOKEN", "CMAKE_GENERATOR", "JAVA_HOME", "CXX"):
            self.assertNotIn(name, value)

    def test_wrong_target_or_host_cannot_select_an_unrunnable_converter(self):
        for changes in ({"VSCMD_ARG_TGT_ARCH": "arm64"}, {"VSCMD_ARG_HOST_ARCH": "x86"}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                host.parse_environment(self.environment(**changes), "x64")

    def test_incomplete_empty_or_duplicate_required_environment_is_rejected(self):
        for raw in (b"", self.environment(LIB=""), self.environment() + "\r\nPATH=C:\\other".encode("utf-16le")):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                host.parse_environment(raw, "x64")

    def test_host_architecture_is_explicit_and_unknown_architecture_is_rejected(self):
        for name, expected in (("AMD64", "x64"), ("x86_64", "x64"), ("ARM64", "arm64"), ("aarch64", "arm64"), ("x86", "x86")):
            with self.subTest(name=name):
                self.assertEqual(host.native_architecture(name), expected)
        with self.assertRaises(ValueError):
            host.native_architecture("riscv64")

    def test_locator_stderr_is_preserved_and_stops_setup(self):
        diagnostic = b"locator diagnostic\r\n"
        captured = io.BytesIO()
        result = subprocess.CompletedProcess(["vswhere.exe"], 0, b"[]", diagnostic)
        with patch.object(host.sys, "platform", "win32"), \
                patch.object(host.platform, "machine", return_value="AMD64"), \
                patch.dict(os.environ, {"ProgramFiles(x86)": "C:/Program Files (x86)"}), \
                patch.object(Path, "is_file", return_value=True), \
                patch.object(host.subprocess, "run", return_value=result) as run, \
                patch.object(host.sys, "stderr", SimpleNamespace(buffer=captured)):
            with self.assertRaises(SystemExit):
                host.main()
        self.assertEqual(captured.getvalue(), diagnostic)
        self.assertEqual(run.call_count, 1)

    def test_command_rejects_expansion_and_control_inputs(self):
        for path in ('bad"path', "bad%PATH%path", "bad!PATH!path", "bad\rpath", "bad\npath", "bad\x00path"):
            for index in range(3):
                arguments = ["cmd.exe", "vcvarsall.bat", "out"]
                arguments[index] = path
                with self.subTest(path=path, argument=index), self.assertRaises(ValueError):
                    host.vcvars_command(arguments[0], arguments[1], "x64", arguments[2])
        with self.assertRaises(ValueError):
            host.vcvars_command("cmd.exe", "vcvarsall.bat", "x64 && other", "out")


@unittest.skipUnless(sys.platform == "win32", "Requires real Windows cmd.exe; not an MSVC build test")
class WindowsCommandBoundaryTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="trime-host-boundary-")
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name) / "host (x86) tools"
        self.directory.mkdir()
        self.batch = self.directory / "vcvarsall.bat"
        self.snapshot = self.directory / "environment snapshot.txt"

    def run_batch(self, content):
        self.batch.write_bytes(content.replace("\n", "\r\n").encode("ascii"))
        return subprocess.run(host.vcvars_command(os.environ["COMSPEC"], self.batch, "x64", self.snapshot),
                              capture_output=True, timeout=30)

    def test_real_cmd_preserves_spaced_paths_diagnostic_and_utf16_snapshot(self):
        result = self.run_batch(
            '@echo off\n'
            'echo trime-vcvars-boundary-diagnostic\n'
            'set "PATH=C:\\VC\\bin;C:\\Windows"\n'
            'set "INCLUDE=C:\\VC\\include"\n'
            'set "LIB=C:\\VC\\lib"\n'
            'set "LIBPATH=C:\\VC\\refs"\n'
            'set "VCTOOLSINSTALLDIR=C:\\VC"\n'
            'set "VSCMD_ARG_HOST_ARCH=x64"\n'
            'set "VSCMD_ARG_TGT_ARCH=x64"\n'
            'exit /b 0\n')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, b"")
        self.assertIn("trime-vcvars-boundary-diagnostic", result.stdout.decode("utf-16le"))
        values = host.parse_environment(self.snapshot.read_bytes(), "x64")
        self.assertEqual(values["INCLUDE"], "C:\\VC\\include")
        self.assertEqual(values["PATH"], "C:\\VC\\bin;C:\\Windows")

    def test_failed_batch_keeps_nonzero_exit_and_does_not_create_snapshot(self):
        result = self.run_batch('@echo off\necho trime-vcvars-boundary-failure\nexit /b 7\n')
        self.assertEqual(result.returncode, 7)
        self.assertFalse(self.snapshot.exists())
        self.assertIn("trime-vcvars-boundary-failure", result.stdout.decode("utf-16le"))


if __name__ == "__main__":
    unittest.main()
