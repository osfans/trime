# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the pinned config overlay is scoped, reproducible and fails on drift."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "app/src/main/jni"
CMAKE = os.environ.get("CMAKE_EXECUTABLE", "cmake")
FILES = ["rime/config/config_data.h", "rime/config/config_data.cc",
         "rime/config/config_compiler.h", "rime/config/config_compiler.cc",
         "rime/config/config_component.cc", "rime/lever/deployment_tasks.cc"]


class RimeConfigCmakeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        source = self.root / "librime/src"
        for relative in FILES:
            target = source / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(NATIVE / "librime/src" / relative, target)
        (source / "CMakeLists.txt").write_text(
            'add_library(rime-static STATIC ' + ' '.join(f for f in FILES if f.endswith('.cc')) + ')\n')
        (self.root / "CMakeLists.txt").write_text(
            'cmake_minimum_required(VERSION 3.18...3.31)\nproject(probe CXX)\n'
            'add_subdirectory(librime/src)\n'
            f'include("{NATIVE}/cmake/ThirdPartyCmake.cmake")\n'
            f'include("{NATIVE}/cmake/RimeConfigCompat.cmake")\n'
            'trime_adapt_rime_config()\n'
            'get_target_property(adapted rime-static SOURCES)\n'
            'file(WRITE "${CMAKE_BINARY_DIR}/sources.txt" "${adapted}")\n')

    def configure(self):
        return subprocess.run([CMAKE, "-S", str(self.root), "-B", str(self.root / "build"),
                               "-Werror=dev", "-Werror=deprecated"],
                              capture_output=True, text=True, timeout=60)

    def test_sources_unchanged_and_all_expected_sources_replaced(self):
        before = {f: (self.root / "librime/src" / f).read_bytes() for f in FILES}
        timestamps = None
        for _ in range(2):
            result = self.configure()
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn("Warning", result.stdout + result.stderr)
            registered = (self.root / "build/sources.txt").read_text().split(';')
            self.assertEqual(len(registered), 4)
            self.assertTrue(all("/trime-rime-config/" in p for p in registered))
            current = {f: (self.root / "build/trime-rime-config" / f).stat().st_mtime_ns for f in FILES}
            if timestamps is not None:
                self.assertEqual(timestamps, current, "Unchanged overlays must not trigger recompilation")
            timestamps = current
        self.assertEqual(before, {f: (self.root / "librime/src" / f).read_bytes() for f in FILES})
        overlay = self.root / "build/trime-rime-config"
        self.assertIn("return config && !config->IsNull", (overlay / FILES[-1]).read_text())
        deployment = (overlay / FILES[-1]).read_text()
        self.assertLess(deployment.index('SetInt("var/last_build_time", 0)'),
                        deployment.index('new ConfigFileUpdate("default.yaml"'))
        self.assertIn('if (failure != 0) return false;', deployment)
        self.assertEqual(deployment.count('return user_config->SaveToFile(deployer->user_data_dir / "user.yaml");'), 1)
        self.assertIn("reference.resource_id, reference.optional", (overlay / FILES[3]).read_text())
        self.assertIn("OptionalReferenceMissing(compiler, reference)", (overlay / FILES[3]).read_text())

    def test_upstream_drift_is_not_silently_ignored(self):
        target = self.root / "librime/src" / FILES[-1]
        target.write_text(target.read_text().replace("ConfigNeedsUpdate(config.get())", "ConfigNeedsUpdate(nullptr)"))
        result = self.configure()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)


if __name__ == "__main__":
    unittest.main()
