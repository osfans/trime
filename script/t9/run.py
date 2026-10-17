#!/usr/bin/env python3
"""Compile a small driver against the existing APK engine; isolated AVD data only."""
import argparse
import hashlib
import json
import pathlib
import shlex
import shutil
import subprocess
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--cxx', type=pathlib.Path, required=True)
    p.add_argument('--library', type=pathlib.Path, required=True)
    p.add_argument('--adb', type=pathlib.Path, required=True)
    p.add_argument('--serial', default='emulator-5554')
    p.add_argument('--source', choices=['experiment.cc', 'controller_test.cc', 'assist_test.cc', 'assist_benchmark.cc', 'jni_bridge_test.cc', 'deployment_test.cc'], default='experiment.cc')
    p.add_argument('--benchmark-shared', type=pathlib.Path,
                   help='Isolated system resources including build/*.bin for assist_benchmark.cc')
    p.add_argument('--double-schema', type=pathlib.Path,
                   default=pathlib.Path('/usr/share/rime-data/double_pinyin.schema.yaml'))
    args = p.parse_args()
    if (args.source == 'assist_benchmark.cc') != bool(args.benchmark_shared):
        p.error('assist_benchmark.cc requires --benchmark-shared, and only it accepts this option')
    output = ROOT / 'build/t9' / uuid.uuid4().hex[:12]
    output.mkdir(parents=True)
    remote = '/data/local/tmp/trime-t9-' + output.name
    compile_db = json.loads((args.cxx / 'compile_commands.json').read_text())
    base = next(x for x in compile_db if x['file'].endswith('/frontend.cc'))
    command = shlex.split(base['command'])
    flags = []
    i = 1
    while i < len(command):
        word = command[i]
        if word in ('-o', '-c', '-MF', '-MT', '-MQ'):
            i += 2
            continue
        if word in ('-MD', '-MMD'):
            i += 1
            continue
        flags.append(word)
        i += 1
    source = ROOT / 'script/t9' / args.source
    exe = output / 'probe'
    run(command[0], *flags, '-std=c++17', '-O1', '-g0', source,
        '-L' + str(args.library.resolve().parent), '-lrime_jni',
        '-static-libstdc++', '-Wl,-rpath,' + remote, '-o', exe, cwd=base['directory'])
    shared = output / 'shared'
    shutil.copytree(args.benchmark_shared or ROOT / 'script/t9/fixtures', shared)
    if args.source == 'assist_test.cc':
        for fixture in (ROOT / 'script/quality/t05/fixtures').iterdir():
            shutil.copy2(fixture, shared / fixture.name)
    shutil.copy2(ROOT / 'app/src/main/assets/shared/luna_pinyin_t9.schema.yaml', shared)
    shutil.copy2(ROOT / 'app/src/main/assets/shared/symbols.yaml', shared)
    if args.source == 'controller_test.cc':
        shutil.copy2(ROOT / 'app/src/main/assets/shared/luna_pinyin.schema.yaml', shared)
        shutil.copy2(ROOT / 'app/src/main/assets/shared/pinyin.yaml', shared)
        shutil.copy2(args.double_schema, shared / 'double_pinyin.schema.yaml')
    adb = [args.adb, '-s', args.serial]
    qemu = run(*adb, 'shell', 'getprop', 'ro.kernel.qemu', capture_output=True, text=True).stdout.strip()
    if qemu != '1':
        raise RuntimeError('This development runner only supports an emulator')
    run(*adb, 'shell', 'mkdir', '-p', remote + '/user')
    run(*adb, 'push', shared, remote + '/shared', stdout=subprocess.DEVNULL)
    run(*adb, 'push', exe, remote + '/probe', stdout=subprocess.DEVNULL)
    run(*adb, 'push', args.library, remote + '/librime_jni.so', stdout=subprocess.DEVNULL)
    run(*adb, 'shell', 'chmod', '700', remote + '/probe')
    identity = {
        'kind': 'native_' + args.source.removesuffix('.cc'),
        'engine_source': 'mock_jni_header_contracts' if args.source == 'jni_bridge_test.cc' else 'provided_library_not_installed_apk',
        'jni_header_sha256': hashlib.sha256((ROOT / 'app/src/main/jni/librime_jni/jni-utils.h').read_bytes()).hexdigest(),
        'git_sha': run('git', 'rev-parse', 'HEAD', cwd=ROOT, capture_output=True, text=True).stdout.strip(),
        'worktree_dirty': bool(run('git', 'status', '--porcelain', cwd=ROOT,
                                  capture_output=True, text=True).stdout.strip()),
        'library_sha256': hashlib.sha256(args.library.read_bytes()).hexdigest(),
        'serial': args.serial, 'remote': remote,
        'files': {str(x.relative_to(ROOT)): hashlib.sha256(x.read_bytes()).hexdigest()
                  for x in [source, ROOT / 'app/src/main/jni/librime_jni/t9.cc',
                            ROOT / 'app/src/main/jni/librime_jni/t9.h',
                            ROOT / 'app/src/main/jni/librime_jni/t9_assist.cc',
                            ROOT / 'app/src/main/jni/librime_jni/t9_assist.h',
                            ROOT / 'app/src/main/jni/librime_jni/helper-types.h',
                            ROOT / 'app/src/main/jni/librime_jni/rime_jni.cc',
                            *shared.rglob('*')] if x.is_file()},
        'state': 'running',
    }
    report = output / 'identity.json'
    report.write_text(json.dumps(identity, indent=2) + '\n')
    print(output, flush=True)
    try:
        with (output / 'results.txt').open('w') as stdout, (output / 'engine.log').open('w') as stderr:
            result = subprocess.run(list(map(str, [*adb, 'shell',
                f'LD_LIBRARY_PATH={remote}', remote + '/probe', remote + '/shared', remote + '/user'])),
                stdout=stdout, stderr=stderr, timeout=180)
            if result.returncode == 0 and args.source in ('controller_test.cc', 'assist_test.cc', 'deployment_test.cc'):
                phase = 'verify-recovery' if args.source == 'deployment_test.cc' else 'verify-learning'
                result = subprocess.run(list(map(str, [*adb, 'shell',
                    f'LD_LIBRARY_PATH={remote}', remote + '/probe', remote + '/shared', remote + '/user', phase])),
                    stdout=stdout, stderr=stderr, timeout=60)
    except (subprocess.TimeoutExpired, KeyboardInterrupt):
        identity['state'] = 'interrupted'
        report.write_text(json.dumps(identity, indent=2) + '\n')
        subprocess.run(list(map(str, [*adb, 'shell', 'pkill', '-f', remote + '/probe'])),
                       timeout=15, check=False)
        raise
    identity['state'] = 'passed' if result.returncode == 0 else 'failed'
    report.write_text(json.dumps(identity, indent=2) + '\n')
    print((output / 'results.txt').read_text())
    result.check_returncode()


if __name__ == '__main__':
    main()
