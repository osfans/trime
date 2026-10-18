# SPDX-License-Identifier: GPL-3.0-or-later
"""Keep the incomplete-cache overlay isolated, repeatable and strict on drift."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "app/src/main/jni"
CMAKE = os.environ.get("CMAKE_EXECUTABLE", "cmake")
FILES = ("rime/dict/mapped_file.cc", "rime/dict/mapped_file.h")
PLUGINS = ("rime-lua-objs", "rime-octagram-objs", "rime-predict-objs")


class RimeMappedFileCmakeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "librime/src"
        for relative in FILES:
            target = self.source / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(NATIVE / "librime/src" / relative, target)
        (self.source / "untouched.cc").write_text("int untouched() { return 1; }\n")
        self.sources = self.source / "CMakeLists.txt"
        self.write_sources("rime/dict/mapped_file.cc untouched.cc")
        (self.root / "CMakeLists.txt").write_text(
            "cmake_minimum_required(VERSION 3.18...3.31)\nproject(probe CXX)\n"
            "add_subdirectory(librime/src)\n"
            f'include("{NATIVE}/cmake/ThirdPartyCmake.cmake")\n'
            f'include("{NATIVE}/cmake/RimeMappedFileCompat.cmake")\n'
            "trime_adapt_rime_mapped_file()\n"
            "get_target_property(adapted rime-static SOURCES)\n"
            'file(WRITE "${CMAKE_BINARY_DIR}/sources.txt" "${adapted}")\n'
            "get_target_property(headers rime-static INTERFACE_INCLUDE_DIRECTORIES)\n"
            'file(WRITE "${CMAKE_BINARY_DIR}/headers.txt" "${headers}")\n'
            "get_target_property(core_headers rime-static INCLUDE_DIRECTORIES)\n"
            'file(WRITE "${CMAKE_BINARY_DIR}/core-headers.txt" "${core_headers}")\n'
            "foreach(plugin_target rime-lua-objs rime-octagram-objs rime-predict-objs)\n"
            "  get_target_property(plugin_headers ${plugin_target} INCLUDE_DIRECTORIES)\n"
            '  file(WRITE "${CMAKE_BINARY_DIR}/${plugin_target}-headers.txt" "${plugin_headers}")\n'
            "endforeach()\n")

    def write_sources(self, source_list):
        self.sources.write_text(
            f"add_library(rime-static STATIC {source_list})\n"
            'target_include_directories(rime-static PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")\n'
            "foreach(plugin_target rime-lua-objs rime-octagram-objs rime-predict-objs)\n"
            "  add_library(${plugin_target} OBJECT untouched.cc)\n"
            '  target_include_directories(${plugin_target} PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")\n'
            "endforeach()\n")

    def configure(self):
        return subprocess.run(
            [CMAKE, "-S", str(self.root), "-B", str(self.root / "build"),
             "-Werror=dev", "-Werror=deprecated"],
            capture_output=True, text=True, timeout=60)

    def test_sources_are_isolated_and_reconfiguration_is_idempotent(self):
        before = {f: (self.source / f).read_bytes() for f in (*FILES, "untouched.cc")}
        timestamps = None
        for _ in range(2):
            result = self.configure()
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn("Warning", result.stdout + result.stderr)
            overlay = self.root / "build/trime-rime-mapped-file"
            sources = (self.root / "build/sources.txt").read_text().split(";")
            self.assertEqual(sources, ["untouched.cc", str(overlay / FILES[0])])
            self.assertEqual((self.root / "build/headers.txt").read_text(), str(overlay))
            for target in ("core", *PLUGINS):
                headers = (self.root / f"build/{target}-headers.txt").read_text().split(";")
                self.assertEqual(headers, [str(overlay), str(self.source)])
            current = {f: (overlay / f).stat().st_mtime_ns for f in FILES}
            if timestamps is not None:
                self.assertEqual(timestamps, current)
            timestamps = current
        self.assertEqual(before, {f: (self.source / f).read_bytes() for f in before})
        self.assertEqual(set(p.name for p in overlay.iterdir()), {"rime"})

    def test_each_pinned_anchor_requires_review_after_upstream_drift(self):
        changes = {
            FILES[0]: ("bool MappedFile::OpenReadOnly() {", "bool MappedFile::OpenReadOnly() noexcept {"),
            FILES[1]: ("offset > size_", "offset >= size_"),
        }
        for relative, (old, new) in changes.items():
            with self.subTest(source=relative):
                target = self.source / relative
                original = target.read_text()
                self.assertEqual(original.count(old), 1)
                target.write_text(original.replace(old, new))
                try:
                    result = self.configure()
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("Re-audit", result.stderr)
                finally:
                    target.write_text(original)

    def test_missing_or_duplicate_original_source_registration_is_rejected(self):
        for source_list in ("untouched.cc", "rime/dict/mapped_file.cc rime/dict/mapped_file.cc untouched.cc"):
            with self.subTest(source_list=source_list):
                self.write_sources(source_list)
                result = self.configure()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Re-audit Rime source registration", result.stderr)


if __name__ == "__main__":
    unittest.main()
