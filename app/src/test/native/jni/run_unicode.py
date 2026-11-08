#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run the production string wrappers in a real host JVM with checked JNI.

Requires a JDK 17, an Android NDK (for its production jni.h), librime headers
at the pinned submodule, and a host C++17 compiler. No Android or Gradle build.
An optional baseline Git SHA must fail the valid supplementary-string test.
"""
import argparse
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jdk', type=Path, required=True)
parser.add_argument('--ndk', type=Path, required=True)
parser.add_argument('--rime-source', type=Path, required=True)
parser.add_argument('--out', type=Path, required=True)
parser.add_argument('--baseline', help='Optional original commit for the failure control')
parser.add_argument('--offsets', action='store_true',
                    help='Check preedit byte offsets against Java UTF-16 positions')
parser.add_argument('--compiler', default=shutil.which('c++'))
args = parser.parse_args()
repo = Path(__file__).resolve().parents[5]
source = Path(__file__).resolve().parent / (
    'offset_test.cc' if args.offsets else 'unicode_test.cc')
headers = repo / 'app/src/main/jni/librime_jni'
rime_source = args.rime_source.resolve()
expected_rime = subprocess.check_output([
    'git', '-C', str(repo), 'rev-parse', 'HEAD:app/src/main/jni/librime'], text=True).strip()
actual_rime = subprocess.check_output([
    'git', '-C', str(rime_source), 'rev-parse', 'HEAD'], text=True).strip()
assert actual_rime == expected_rime, 'librime headers must match the pinned submodule'
subprocess.run(['git', '-C', str(rime_source), 'diff', '--exit-code', 'HEAD', '--',
                'include', 'src/rime_api.h'], check=True)
jni = args.ndk.resolve() / 'toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include'
vm = args.jdk.resolve() / 'lib/server'
assert args.compiler and (vm / 'libjvm.so').is_file(), 'Host compiler and JDK VM required'
assert (jni / 'jni.h').is_file(), 'Android NDK jni.h not found'
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=False)
local_jni = out / 'jni-headers'
local_jni.mkdir()
# Only jni.h is portable to the host; adding the whole Android sysroot would
# replace the host C-library headers with Bionic headers.
shutil.copyfile(jni / 'jni.h', local_jni / 'jni.h')
for mode in (['baseline', 'fixed'] if args.baseline else ['fixed']):
    folder = out / mode
    folder.mkdir()
    includes = headers
    if mode == 'baseline':
        includes = folder / 'headers'
        includes.mkdir()
        for name in ['jni-utils.h', 'objconv.h', 'helper-types.h']:
            (includes / name).write_bytes(subprocess.check_output([
                'git', '-C', str(repo), 'show',
                args.baseline + ':app/src/main/jni/librime_jni/' + name]))
    command = [args.compiler, '-std=c++17', '-O0', '-g0', '-Wall', '-Wextra',
               '-Werror', '-Wno-unused-parameter', '-pthread',
               '-isystem', str(local_jni), '-isystem', str(rime_source / 'include'),
               '-isystem', str(rime_source / 'src'),
               '-I', str(includes), str(source), '-L', str(vm),
               '-Wl,-rpath,' + str(vm), '-ljvm', '-o', str(folder / 'check')]
    if mode == 'baseline':
        # Its known signedness/move warnings are unrelated to the Unicode failure.
        command += ['-DTRIME_JNI_BASELINE', '-Wno-sign-compare', '-Wno-pessimizing-move']
    for stage, argv in [('compile', command), ('run', [str(folder / 'check')])]:
        result = subprocess.run(argv, capture_output=True, timeout=90)
        (folder / (stage + '.stdout')).write_bytes(result.stdout)
        (folder / (stage + '.stderr')).write_bytes(result.stderr)
        expected = 1 if mode == 'baseline' and stage == 'run' else 0
        print(mode, stage, 'exit', result.returncode, 'expected', expected, flush=True)
        if result.returncode != expected:
            raise SystemExit('Unexpected result; inspect ' + str(folder))
        if mode == 'baseline' and stage == 'run':
            failure = (b'composition length matches Java UTF-16 length' if args.offsets
                       else b'standard UTF-8 becomes expected UTF-16')
            if b'FAIL: ' + failure not in result.stderr:
                raise SystemExit('Unexpected baseline failure; inspect ' + str(folder))
        if mode == 'fixed' and result.stderr:
            raise SystemExit('Unexpected compiler/CheckJNI diagnostics; inspect ' + str(folder))
