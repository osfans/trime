# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify Android 21 Rime Lua compatibility without editing the submodule."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "app/src/main/jni"
CMAKE = os.environ.get("CMAKE_EXECUTABLE", "cmake")


class RimeLuaCmakeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        plugin = self.root / "librime-lua"
        source_root = plugin / "src"
        source_root.mkdir(parents=True)
        for name in ["types.cc", "lua_gears.cc", "lua_gears.h", "modules.cc"]:
            shutil.copyfile(NATIVE / "librime-lua/src" / name, source_root / name)
        (plugin / "CMakeLists.txt").write_text(
            "add_library(rime-lua-objs OBJECT "
            "src/types.cc src/lua_gears.cc src/modules.cc)\n"
        )
        (self.root / "CMakeLists.txt").write_text(
            "cmake_minimum_required(VERSION 3.18...3.31)\n"
            "project(probe CXX)\n"
            f'include("{NATIVE}/cmake/ThirdPartyCmake.cmake")\n'
            "add_subdirectory(librime-lua)\n"
            f'include("{NATIVE}/cmake/RimeLuaCompat.cmake")\n'
            "trime_adapt_rime_lua()\n"
            "get_target_property(adapted rime-lua-objs SOURCES)\n"
            "get_target_property(definitions rime-lua-objs COMPILE_DEFINITIONS)\n"
            "get_target_property(includes rime-lua-objs INCLUDE_DIRECTORIES)\n"
            'file(WRITE "${CMAKE_BINARY_DIR}/sources.txt" "${adapted}")\n'
            'file(WRITE "${CMAKE_BINARY_DIR}/definitions.txt" "${definitions}")\n'
            'file(WRITE "${CMAKE_BINARY_DIR}/includes.txt" "${includes}")\n'
        )

    def configure(self, abi="armeabi-v7a", platform=21, build="build"):
        return subprocess.run(
            [
                CMAKE,
                "-S",
                str(self.root),
                "-B",
                str(self.root / build),
                "-DANDROID=TRUE",
                f"-DANDROID_ABI={abi}",
                f"-DANDROID_PLATFORM_LEVEL={platform}",
                "-Werror=dev",
                "-Werror=deprecated",
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )

    def test_overlay_is_stable_and_uses_platform_width(self):
        originals = {
            path: path.read_bytes()
            for path in (self.root / "librime-lua/src").iterdir()
        }
        timestamp = None
        for _ in range(2):
            result = self.configure()
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn("Warning", result.stdout + result.stderr)
            overlay_root = self.root / "build/trime-rime-lua"
            outputs = [
                overlay_root / name
                for name in ["types.cc", "lua_gears.cc", "lua_gears.h", "modules.cc"]
            ]
            current = {path: path.stat().st_mtime_ns for path in outputs}
            if timestamp is not None:
                self.assertEqual(timestamp, current)
            timestamp = current
        self.assertEqual(originals, {path: path.read_bytes() for path in originals})
        sources = (self.root / "build/sources.txt").read_text().split(";")
        self.assertEqual(len(sources), 3)
        for name in ["types.cc", "lua_gears.cc", "modules.cc"]:
            self.assertEqual(
                sum(path.endswith(f"/trime-rime-lua/{name}") for path in sources),
                1,
            )
        content = (self.root / "build/trime-rime-lua/types.cc").read_text()
        self.assertIn("#include <limits>", content)
        self.assertIn('#include "trime_privacy_guard.h"', content)
        self.assertIn("std::numeric_limits<size_t>::max()", content)
        self.assertIn(
            "if (trime::IsPrivacyModeEnabled())\n      return 0;\n    C_State C;",
            content,
        )
        self.assertIn(
            "return !trime::IsPrivacyModeEnabled() &&\n"
            "           t.SaveToFile",
            content,
        )
        self.assertIn(
            "return !trime::IsPrivacyModeEnabled() && t.SetBool(path, value);",
            content,
        )
        self.assertIn(
            "if (trime::IsPrivacyModeEnabled())\n      return;\n"
            "    auto r = lua->void_call",
            content,
        )
        self.assertIn(
            "/librime-lua/src", (self.root / "build/includes.txt").read_text()
        )
        self.assertIn(
            "/librime_jni", (self.root / "build/includes.txt").read_text()
        )
        gear = (self.root / "build/trime-rime-lua/lua_gears.cc").read_text()
        header = (self.root / "build/trime-rime-lua/lua_gears.h").read_text()
        self.assertIn("c_.reset();\n    f_.reset();", gear)
        self.assertIn(
            "LuaFilter::Apply(\n"
            "  an<Translation> translation, CandidateList* candidates) {\n"
            "  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())\n"
            "    return translation;",
            gear,
        )
        self.assertIn(
            "LuaTranslator::Query(const string& input,\n"
            "                                     const Segment& segment) {\n"
            "  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())\n"
            "    return an<Translation>();",
            gear,
        )
        self.assertIn(
            "LuaSegmentor::Proceed(Segmentation* segmentation) {\n"
            "  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())\n"
            "    return true;",
            gear,
        )
        self.assertIn(
            "LuaProcessor::ProcessKeyEvent(const KeyEvent& key_event) {\n"
            "  if (trime::IsPrivacyModeEnabled() || !EnsureInitialized())\n"
            "    return kNoop;",
            gear,
        )
        self.assertEqual(gear.count("!trime::IsPrivacyModeEnabled()"), 5)
        self.assertIn(
            "trime::IsPrivacyModeEnabled() || !EnsureInitialized()", header
        )
        self.assertIn(
            "c_.reset();\n      f_.reset();\n      set_exhausted(true);", header
        )
        self.assertEqual(header.count("bool EnsureInitialized();"), 4)
        self.assertEqual(
            (self.root / "build/definitions.txt").read_text(),
            "_FILE_OFFSET_BITS=32",
        )

    def test_file_offset_fallback_is_limited_to_legacy_32_bit_android(self):
        for abi, platform, expected in [
            ("x86", 21, "_FILE_OFFSET_BITS=32"),
            ("arm64-v8a", 21, "definitions-NOTFOUND"),
            ("armeabi-v7a", 24, "definitions-NOTFOUND"),
        ]:
            build = f"build-{abi}-{platform}"
            result = self.configure(abi, platform, build)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual(
                (self.root / build / "definitions.txt").read_text(), expected
            )

    def test_upstream_drift_is_not_silently_ignored(self):
        source = self.root / "librime-lua/src/types.cc"
        source.write_text(
            source.read_text().replace(
                "limit = limit == 0 ? 0xffffffffffffffff : limit;",
                "limit = limit == 0 ? 0xffffffff : limit;",
            )
        )
        result = self.configure()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)

    def test_privacy_anchor_drift_is_not_silently_ignored(self):
        source = self.root / "librime-lua/src/types.cc"
        source.write_text(
            source.read_text().replace(
                "return t.SaveToFile(COMPAT<Deployer>::to_path(f));",
                "return false;",
            )
        )
        result = self.configure(build="build-privacy-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)

    def test_lua_gear_privacy_drift_is_not_silently_ignored(self):
        source = self.root / "librime-lua/src/lua_gears.cc"
        source.write_text(
            source.read_text().replace(
                "ProcessResult LuaProcessor::ProcessKeyEvent(const KeyEvent& key_event) {",
                "ProcessResult LuaProcessor::ProcessKeyEvent(const KeyEvent& event) {",
            )
        )
        result = self.configure(build="build-gear-privacy-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)


if __name__ == "__main__":
    unittest.main()
