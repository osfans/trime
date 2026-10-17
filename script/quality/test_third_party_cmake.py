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
        for name in ("MarisaDict.cpp", "SerializableDict.hpp", "TextDict.cpp", "Dict.hpp", "PrefixMatch.cpp",
                     "PluginSegmentation.cpp", "plugin/OpenCCPlugin.h"):
            target = opencc_source / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((OPENCC / name).read_bytes())
        data = self.source / "vendor/OpenCC/data"
        data.mkdir()
        (data / "CMakeLists.txt").write_text(
            'find_package(Python3 REQUIRED COMPONENTS Interpreter)\n'
            'execute_process(COMMAND "${Python3_EXECUTABLE}" -c '
            '"import sys; assert sys.version_info.major == 3" RESULT_VARIABLE result)\n'
            'if(NOT result EQUAL 0)\nmessage(FATAL_ERROR "Host Python failed")\nendif()\n',
            encoding="utf-8")

    def configure(self, success=True, failure_message="Re-audit"):
        result = subprocess.run(
            [CMAKE, "-S", str(self.source), "-B", str(self.build),
             "-Werror=dev", "-Werror=deprecated"],
            capture_output=True, text=True, timeout=60)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn("Warning", result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(failure_message, result.stderr)
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
        self.assertEqual(serializable.count("struct FileCloser {"), 1)
        self.assertIn("void operator()(FILE* file) const noexcept", serializable)
        self.assertIn("if (file != nullptr)", serializable)
        self.assertIn("(void)std::fclose(file);", serializable)
        self.assertIn("std::unique_ptr<FILE, FileCloser> input(fp);", serializable)
        self.assertIn("std::unique_ptr<FILE, FileCloser> output", serializable)
        self.assertNotIn("decltype(&fclose)", serializable)
        self.assertIn('OpenSerializableFileUtf8(fileName, "wb"));', serializable)
        self.assertIn('OpenSerializableFileUtf8(fileName, "rb")', serializable)
        self.assertNotIn('fopen(fileName.c_str()', serializable)
        lookup = (patched / "TextDict.cpp").read_text()
        self.assertEqual(lookup.count("#include <string_view>"), 1)
        self.assertIn("const std::string_view key(word, len);", lookup)
        self.assertIn("return entry->KeyView() < query;", lookup)
        self.assertIn("((*found)->KeyView() == key)", lookup)
        self.assertNotIn("new NoValueDictEntry", lookup)
        dictionary = (patched / "Dict.hpp").read_text()
        self.assertIn("virtual PrefixMatchView MatchPrefixValue(const char*, size_t) const", dictionary)
        self.assertIn("return PrefixMatchView{};", dictionary)
        self.assertNotIn("MatchPrefixValue(const char* word", dictionary)
        prefix = (patched / "PrefixMatch.cpp").read_text()
        prefix_returns = (
            ("      CollectAllLeafDicts(dict, leaf.get());\n      return leaf;",
             "      CollectAllLeafDicts(dict, leaf.get());\n      return std::move(leaf);"),
            ("    return group;", "    return std::move(group);"),
            ("  leaf->AddDict(dict);\n  return leaf;",
             "  leaf->AddDict(dict);\n  return std::move(leaf);"),
        )
        for adapted, original in prefix_returns:
            self.assertEqual(prefix.count(adapted), 1)
            self.assertNotIn(original, prefix)
            prefix = prefix.replace(adapted, original, 1)
        self.assertEqual(prefix, (OPENCC / "PrefixMatch.cpp").read_text())
        plugin = (patched / "PluginSegmentation.cpp").read_text()
        minor_check = ('    if (descriptor->abi_minor < OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR) {\n'
                       '      throw Exception("Plugin ABI minor version is older than required by the host.");\n'
                       '    }')
        guarded_minor_check = '#if OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR > 0\n' + minor_check + '\n#endif'
        self.assertEqual(plugin.count(guarded_minor_check), 1)
        self.assertEqual(plugin.replace(guarded_minor_check, minor_check, 1),
                         (OPENCC / "PluginSegmentation.cpp").read_text())
        self.assertEqual((patched / "plugin/OpenCCPlugin.h").read_bytes(),
                         (OPENCC / "plugin/OpenCCPlugin.h").read_bytes())
        resource_outputs = {name: (patched / name).read_bytes()
                            for name in ("MarisaDict.cpp", "SerializableDict.hpp", "TextDict.cpp", "Dict.hpp", "PrefixMatch.cpp",
                                         "PluginSegmentation.cpp", "plugin/OpenCCPlugin.h")}
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

    def check_opencc_git_fallback_scope(self, parent_setup, parent_checks):
        path = self.source / "CMakeLists.txt"
        call = 'trime_add_dependency(vendor/OpenCC OpenCC 3.12)\n'
        text = path.read_text()
        self.assertEqual(text.count(call), 1)
        path.write_text(text.replace(call, parent_setup + call + parent_checks), encoding="utf-8")
        dependency = self.source / "vendor/OpenCC/CMakeLists.txt"
        dependency.write_text(dependency.read_text() + f'''
if(NOT CMAKE_DISABLE_FIND_PACKAGE_Git OR GIT_FOUND OR Git_FOUND)
  message(FATAL_ERROR "OpenCC inherited Git discovery results")
endif()
include("{(OPENCC.parent / 'cmake/GitVersion.cmake').as_posix()}")
if(NOT OPENCC_VERSION STREQUAL "1.4.2" OR
   NOT OPENCC_VERSION_SUFFIX STREQUAL "" OR OPENCC_VERSION_IS_RELEASE)
  message(FATAL_ERROR "OpenCC did not use its real source-archive version fallback")
endif()
''', encoding="utf-8")
        self.configure()

    def test_opencc_git_fallback_shadows_inherited_results_only_locally(self):
        self.check_opencc_git_fallback_scope(
            'set(CMAKE_DISABLE_FIND_PACKAGE_Git FALSE)\nset(GIT_FOUND TRUE)\nset(Git_FOUND TRUE)\n',
            'if(CMAKE_DISABLE_FIND_PACKAGE_Git OR NOT GIT_FOUND OR NOT Git_FOUND)\n'
            '  message(FATAL_ERROR "OpenCC changed parent Git discovery results")\nendif()\n')

    def test_opencc_git_fallback_preserves_inherited_cache_results(self):
        self.check_opencc_git_fallback_scope(
            'set(CMAKE_DISABLE_FIND_PACKAGE_Git FALSE CACHE BOOL "Parent policy")\n'
            'set(GIT_FOUND TRUE CACHE BOOL "Parent Git result")\n'
            'set(Git_FOUND TRUE CACHE BOOL "Parent Git result")\n',
            'if(CMAKE_DISABLE_FIND_PACKAGE_Git OR NOT GIT_FOUND OR NOT Git_FOUND OR\n'
            '   $CACHE{CMAKE_DISABLE_FIND_PACKAGE_Git} OR NOT $CACHE{GIT_FOUND} OR NOT $CACHE{Git_FOUND})\n'
            '  message(FATAL_ERROR "OpenCC changed parent cached Git discovery results")\nendif()\n')

    def test_opencc_git_fallback_does_not_publish_results_to_parent(self):
        variables = ("CMAKE_DISABLE_FIND_PACKAGE_Git", "GIT_FOUND", "Git_FOUND")
        setup = ''.join(f'unset({name})\nunset({name} CACHE)\n' for name in variables)
        checks = 'if(' + ' OR '.join(f'DEFINED {name}' for name in variables) + ')\n'
        checks += '  message(FATAL_ERROR "OpenCC published local Git results to parent")\nendif()\n'
        self.check_opencc_git_fallback_scope(setup, checks)

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

    def test_opencc_serializable_owner_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/SerializableDict.hpp"
        text = path.read_text()
        self.assertEqual(text.count("class OPENCC_EXPORT SerializableDict {"), 1)
        path.write_text(text.replace("class OPENCC_EXPORT SerializableDict {",
                                     "class OPENCC_EXPORT ChangedSerializableDict {"), encoding="utf-8")
        self.configure(success=False)

    def test_duplicate_opencc_serializable_owner_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/SerializableDict.hpp"
        path.write_text(path.read_text() + '\nclass OPENCC_EXPORT SerializableDict {\npublic:\n',
                        encoding="utf-8")
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

    def test_missing_opencc_lookup_source_is_rejected(self):
        (self.source / "vendor/OpenCC/src/TextDict.cpp").unlink()
        result = self.configure(success=False, failure_message="file failed to open for reading")
        self.assertIn("trime-deps/OpenCC/src/TextDict.cpp", result.stderr)

    def test_duplicate_opencc_lookup_target_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/TextDict.cpp"
        text = path.read_text()
        start = text.index("Optional<const DictEntry*> TextDict::Match(")
        end = text.index("LexiconPtr TextDict::GetLexicon()", start)
        path.write_text(text + '\n' + text[start:end], encoding="utf-8")
        self.configure(success=False)

    def test_opencc_lookup_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/TextDict.cpp"
        text = path.read_text()
        self.assertEqual(text.count("DictEntry::UPtrLessThan"), 1)
        path.write_text(text.replace("DictEntry::UPtrLessThan", "DictEntry::DifferentComparison"),
                        encoding="utf-8")
        self.configure(success=False)

    def test_already_adapted_opencc_lookup_requires_reaudit(self):
        self.configure()
        path = self.source / "vendor/OpenCC/src/TextDict.cpp"
        path.write_bytes((self.build / "trime-deps/OpenCC/src/TextDict.cpp").read_bytes())
        self.configure(success=False)

    def test_opencc_prefix_return_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/PrefixMatch.cpp"
        text = path.read_text()
        self.assertEqual(text.count("    return std::move(group);"), 1)
        path.write_text(text.replace("    return std::move(group);", "    return group;"),
                        encoding="utf-8")
        self.configure(success=False)

    def test_opencc_plugin_minor_minimum_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/plugin/OpenCCPlugin.h"
        path.write_text(path.read_text().replace("#define OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR 0",
                                                "#define OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR 1"),
                        encoding="utf-8")
        self.configure(success=False)

    def test_opencc_plugin_minor_hex_minimum_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/plugin/OpenCCPlugin.h"
        path.write_text(path.read_text().replace("#define OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR 0",
                                                "#define OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR 0x1"),
                        encoding="utf-8")
        self.configure(success=False)

    def test_opencc_plugin_minor_signed_type_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/plugin/OpenCCPlugin.h"
        path.write_text(path.read_text().replace("uint16_t abi_minor;", "int16_t abi_minor;"),
                        encoding="utf-8")
        self.configure(success=False)

    def test_opencc_plugin_minor_comparison_drift_requires_reaudit(self):
        path = self.source / "vendor/OpenCC/src/PluginSegmentation.cpp"
        path.write_text(path.read_text().replace("descriptor->abi_minor < OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR",
                                                "descriptor->abi_minor <= OPENCC_SEGMENTATION_PLUGIN_ABI_MINOR"),
                        encoding="utf-8")
        self.configure(success=False)

    def test_missing_opencc_plugin_header_is_rejected(self):
        (self.source / "vendor/OpenCC/src/plugin/OpenCCPlugin.h").unlink()
        result = self.configure(success=False, failure_message="file failed to open for reading")
        self.assertIn("trime-deps/OpenCC/src/plugin/OpenCCPlugin.h", result.stderr)

    def test_duplicate_patch_target_requires_reaudit(self):
        path = self.source / "vendor/snappy/CMakeLists.txt"
        path.write_text(path.read_text() * 2, encoding="utf-8")
        self.configure(success=False)


if __name__ == "__main__":
    unittest.main()
