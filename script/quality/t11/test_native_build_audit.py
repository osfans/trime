#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Temporary-file checks of the actual library-target compile audit only."""
from pathlib import Path
import tempfile
import unittest

import run_native_prediction_tests as native


class NativeBuildAuditTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='trime-native-audit-')
        self.addCleanup(temporary.cleanup)
        self.binary = Path(temporary.name).resolve()
        self.library = self.row('libopencc', 'TextDict.cpp', macro=True, built=True)

    def row(self, target, filename, macro, built):
        source = self.binary / 'trime-deps/OpenCC/src' / filename
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text('// synthetic compile record\n')
        output = Path('OpenCC/src/CMakeFiles') / (target + '.dir') / (filename + '.o')
        if built:
            obj = self.binary / output
            obj.parent.mkdir(parents=True, exist_ok=True)
            obj.write_bytes(b'synthetic presence check; not an ELF acceptance')
        argv = ['c++', '-DOPENCC_VERSION="1.4.2"']
        if macro:
            argv.append('-DRAPIDJSON_NOMEMBERITERATORCLASS')
        argv += ['-o', str(output), '-c', str(source)]
        return dict(directory=str(self.binary), file=str(source), arguments=argv)

    def test_real_native_warning_diagnostics_are_detected(self):
        for line in (
            "warning: unused value",
            "w: deprecated Kotlin API",
            "WARNING: configuration problem",
            "CMake Warning at CMakeLists.txt:4 (message):",
            "CMake Deprecation Warning at CMakeLists.txt:4 (message):",
            "source.cc:4:7: warning: unused value",
            "source.cc:4:7:warning: unused value",
            "C:\\project\\source.cc:4: warning: conversion",
        ):
            with self.subTest(line=line):
                self.assertTrue(native.native_warning_line(line))

    def test_readelf_cpp_warning_members_are_not_diagnostics(self):
        # Actual readelf symbol rows that previously aborted cache acceptance.
        for line in (
            '  2306: 00000000003226d0    12 FUNC    LOCAL  DEFAULT   13 LuaWrapper<void (*)(std::__cxx11::basic_string<char, std::char_traits<char>, std::allocator<char> > const&), &(anonymous namespace)::LogReg::warning>::wrap(lua_State*)',
            '  2307: 000000000033adf0   599 FUNC    LOCAL  DEFAULT   13 LuaWrapper<void (*)(std::__cxx11::basic_string<char, std::char_traits<char>, std::allocator<char> > const&), &(anonymous namespace)::LogReg::warning>::wrap_helper(lua_State*)',
            '  2433: 0000000000074f58   210 FUNC    LOCAL  DEFAULT   13 LuaWrapper<void (*)(std::__cxx11::basic_string<char, std::char_traits<char>, std::allocator<char> > const&), &(anonymous namespace)::LogReg::warning>::wrap_helper(lua_State*) [clone .cold]',
        ):
            with self.subTest(line=line):
                self.assertFalse(native.native_warning_line(line))

    def test_optional_unbuilt_tools_need_no_private_library_macro(self):
        tools = [self.row(target, 'tools/' + name, macro=False, built=False)
                 for target, name in [('opencc', 'CommandLine.cpp'),
                                      ('opencc_dict', 'DictConverter.cpp'),
                                      ('opencc_phrase_extract', 'PhraseExtract.cpp')]]
        before = native.audit_opencc_compilations(self.binary, [self.library, *tools])
        after = native.audit_opencc_compilations(self.binary, [self.library, *tools], True)
        self.assertEqual(before, after)
        self.assertEqual(len(after), 1)
        self.assertEqual(after[0]['file'], self.library['file'])

    def test_library_missing_macro_rejects(self):
        self.library['arguments'].remove('-DRAPIDJSON_NOMEMBERITERATORCLASS')
        with self.assertRaisesRegex(AssertionError, 'definition missing'):
            native.audit_opencc_compilations(self.binary, [self.library])

    def test_library_wrong_version_rejects(self):
        self.library['arguments'][1] = '-DOPENCC_VERSION="wrong"'
        with self.assertRaisesRegex(AssertionError, 'version differs'):
            native.audit_opencc_compilations(self.binary, [self.library])

    def test_missing_library_rejects(self):
        tool = self.row('opencc', 'tools/CommandLine.cpp', macro=False, built=False)
        with self.assertRaisesRegex(AssertionError, 'Missing compile commands'):
            native.audit_opencc_compilations(self.binary, [tool])

    def test_object_required_only_after_build(self):
        obj = self.binary / self.library['arguments'][self.library['arguments'].index('-o') + 1]
        obj.unlink()
        self.assertEqual(len(native.audit_opencc_compilations(self.binary, [self.library])), 1)
        with self.assertRaisesRegex(AssertionError, 'object was not built'):
            native.audit_opencc_compilations(self.binary, [self.library], True)

    def test_library_source_and_object_must_remain_owned(self):
        original_source = self.library['file']
        self.library['file'] = str(self.binary / 'other/TextDict.cpp')
        with self.assertRaisesRegex(AssertionError, 'source'):
            native.audit_opencc_compilations(self.binary, [self.library])
        self.library['file'] = original_source
        position = self.library['arguments'].index('-o') + 1
        self.library['arguments'][position] = str(self.binary.parent / 'CMakeFiles/libopencc.dir/foreign.o')
        with self.assertRaisesRegex(AssertionError, 'object escaped'):
            native.audit_opencc_compilations(self.binary, [self.library])


if __name__ == '__main__':
    unittest.main()
