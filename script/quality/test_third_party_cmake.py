# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise build-tree adaptations without modifying real dependency sources."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CMAKE = os.environ.get("CMAKE_EXECUTABLE", "cmake")
ADAPTER = ROOT / "app/src/main/jni/cmake/ThirdPartyCmake.cmake"
OPENCC = ROOT / "app/src/main/jni/OpenCC/src"


class ThirdPartyCmakeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.build = self.root / "build"
        self.source.mkdir()
        (self.source / "CMakeLists.txt").write_text(
            'cmake_minimum_required(VERSION 3.18)\nproject(probe NONE)\n'
            f'include("{ADAPTER.as_posix()}")\n'
            'trime_add_dependency(vendor/snappy snappy 3.1)\n'
            'trime_add_dependency(vendor/leveldb librime/deps/leveldb 3.9)\n'
            'trime_add_dependency(vendor/OpenCC OpenCC 3.12)\n', encoding="utf-8")
        for name, version in (("snappy", "3.1"), ("leveldb", "3.9"), ("OpenCC", "3.12")):
            directory = self.source / "vendor" / name
            directory.mkdir(parents=True)
            (directory / "CMakeLists.txt").write_text(
                f'cmake_minimum_required(VERSION {version})\nproject({name} NONE)\n'
                + ('add_subdirectory(data)\n' if name == "OpenCC" else ''), encoding="utf-8")
        # Exercise the exact pinned resource code that the build-tree patch consumes.
        opencc_source = self.source / "vendor/OpenCC/src"
        opencc_source.mkdir()
        for name in ("MarisaDict.cpp", "SerializableDict.hpp"):
            (opencc_source / name).write_bytes((OPENCC / name).read_bytes())
        data = self.source / "vendor/OpenCC/data"
        data.mkdir()
        (data / "CMakeLists.txt").write_text(
            'find_package(Python3 REQUIRED COMPONENTS Interpreter)\n'
            'execute_process(COMMAND "${Python3_EXECUTABLE}" -c '
            '"import sys; assert sys.version_info.major == 3" RESULT_VARIABLE result)\n'
            'if(NOT result EQUAL 0)\nmessage(FATAL_ERROR "Host Python failed")\nendif()\n',
            encoding="utf-8")

    def configure(self, success=True):
        result = subprocess.run(
            [CMAKE, "-S", str(self.source), "-B", str(self.build),
             "-Werror=dev", "-Werror=deprecated"],
            capture_output=True, text=True, timeout=60)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn("Warning", result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Re-audit", result.stderr)
        return result

    def test_all_adaptations_preserve_sources_and_refresh(self):
        before = {p.relative_to(self.source): p.read_bytes()
                  for p in self.source.rglob("*") if p.is_file()}
        self.configure()
        self.assertEqual(before, {p.relative_to(self.source): p.read_bytes()
                                  for p in self.source.rglob("*") if p.is_file()})
        for name in ("snappy", "librime/deps/leveldb"):
            self.assertIn("...3.10)", (self.build / "trime-deps" / name / "CMakeLists.txt").read_text())
        self.assertEqual((self.source / "vendor/OpenCC/CMakeLists.txt").read_bytes(),
                         (self.build / "trime-deps/OpenCC/CMakeLists.txt").read_bytes())
        data = self.build / "trime-deps/OpenCC/data/CMakeLists.txt"
        self.assertNotIn("find_package(PythonInterp", data.read_text())
        self.assertIn("find_package(Python3 REQUIRED COMPONENTS Interpreter)", data.read_text())
        self.assertEqual(data.read_bytes(), (self.source / "vendor/OpenCC/data/CMakeLists.txt").read_bytes())
        patched = self.build / "trime-deps/OpenCC/src"
        marisa = (patched / "MarisaDict.cpp").read_text()
        serializable = (patched / "SerializableDict.hpp").read_text()
        self.assertNotIn("void* buffer = malloc", marisa)
        self.assertIn("std::vector<char> buffer(headerLen)", marisa)
        self.assertIn("Invalid OpenCC dictionary header", marisa)
        self.assertIn("std::unique_ptr<FILE, decltype(&fclose)> input", serializable)
        self.assertIn("std::unique_ptr<FILE, decltype(&fclose)> output", serializable)
        self.assertIn('OpenSerializableFileUtf8(fileName, "wb"), fclose)', serializable)
        self.assertIn('OpenSerializableFileUtf8(fileName, "rb")', serializable)
        self.assertNotIn('fopen(fileName.c_str()', serializable)
        resource_outputs = {name: (patched / name).read_bytes()
                            for name in ("MarisaDict.cpp", "SerializableDict.hpp")}
        added = self.source / "vendor/snappy/added.txt"
        added.write_text("new input", encoding="utf-8")
        self.configure()
        copy = self.build / "trime-deps/snappy/added.txt"
        self.assertEqual(copy.read_text(), "new input")
        added.unlink()
        self.configure()
        self.assertFalse(copy.exists())
        self.assertEqual(resource_outputs, {name: (patched / name).read_bytes()
                                            for name in resource_outputs})
        for name in resource_outputs:
            self.assertEqual((self.source / "vendor/OpenCC/src" / name).read_bytes(),
                             (OPENCC / name).read_bytes())

    def test_minimum_version_drift_requires_reaudit(self):
        path = self.source / "vendor/snappy/CMakeLists.txt"
        path.write_text(path.read_text().replace("VERSION 3.1", "VERSION 3.12"), encoding="utf-8")
        self.configure(success=False)

    def test_current_pinned_opencc_cmake_interface(self):
        script = self.root / "validate-opencc.cmake"
        script.write_text(f'include("{ADAPTER.as_posix()}")\n'
                          f'trime_validate_opencc_cmake("{OPENCC.parent.as_posix()}")\n',
                          encoding="utf-8")
        result = subprocess.run([CMAKE, "-Werror=dev", "-Werror=deprecated", "-P", str(script)],
                                capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("Warning", result.stdout + result.stderr)

    def test_python_find_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/data/CMakeLists.txt"
        path.write_text(path.read_text().replace("find_package(Python3", "find_package(Python"), encoding="utf-8")
        self.configure(success=False)

    def test_opencc_minimum_version_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/CMakeLists.txt"
        path.write_text(path.read_text().replace("VERSION 3.12", "VERSION 3.13"), encoding="utf-8")
        self.configure(success=False)

    def test_opencc_declared_minimum_must_match_audited_version(self):
        path = self.source / "CMakeLists.txt"
        path.write_text(path.read_text().replace("OpenCC OpenCC 3.12", "OpenCC OpenCC 3.5"), encoding="utf-8")
        self.configure(success=False)

    def test_duplicate_opencc_python_find_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/data/CMakeLists.txt"
        path.write_text(path.read_text() + 'find_package(Python3 REQUIRED COMPONENTS Interpreter)\n', encoding="utf-8")
        self.configure(success=False)

    def test_opencc_unicode_writer_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/SerializableDict.hpp"
        path.write_text(path.read_text().replace('OpenSerializableFileUtf8(fileName, "wb")',
                                                'fopen(fileName.c_str(), "wb")'), encoding="utf-8")
        self.configure(success=False)

    def test_opencc_resource_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/MarisaDict.cpp"
        # Alter the exact ownership code, not merely surrounding declarations.
        path.write_text(path.read_text().replace("malloc(sizeof(char) * headerLen)",
                                                "malloc(headerLen)"), encoding="utf-8")
        self.configure(success=False)

    def test_duplicate_opencc_resource_target_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/SerializableDict.hpp"
        ownership = '    std::shared_ptr<DICT> loadedDict = DICT::NewFromFile(fp);'
        text = path.read_text()
        start = text.index('    if (fp == NULL) {\n      return false;')
        end = text.index('    return true;', start) + len('    return true;')
        self.assertIn(ownership, text[start:end])
        path.write_text(text + '\n' + text[start:end] + '\n', encoding="utf-8")
        self.configure(success=False)

    def test_duplicate_patch_target_requires_reaudit(self):
        path = self.source / "vendor/snappy/CMakeLists.txt"
        path.write_text(path.read_text() * 2, encoding="utf-8")
        self.configure(success=False)


if __name__ == "__main__":
    unittest.main()
