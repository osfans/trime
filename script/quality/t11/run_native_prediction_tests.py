#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Serial Linux host regression against the same production native source graph.

Builds a private source copy with the production CMake overlays and static
Rime/plugins/dependencies. Never mixes private headers with system librime.
Requires a clean recursive checkout, the verified Boost archive, CMake/Ninja,
C/C++ compilers and readelf. No Java, Android build, emulator or asset writes.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import stat
import subprocess
import sys
import time
import traceback

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
NATIVE = ROOT / "app/src/main/jni"
OVERLAYS = NATIVE / "librime_jni/predict"
MODEL = ROOT / "app/src/main/assets/shared/trime_predict.db"
JNI = NATIVE / "librime_jni/rime_jni.cc"
PRIVACY_CMAKE = NATIVE / "cmake/RimePrivacyCompat.cmake"
FLOOR_KIB = 1048576
EXECUTABLE_TOOL_KEYS = frozenset(('cmake', 'c_compiler', 'cxx_compiler', 'ninja', 'readelf', 'python'))
if not __debug__:
    raise RuntimeError('Optimized Python would disable acceptance assertions')


def bind(path):
    path = Path(path).absolute()
    before = path.stat()
    assert path.is_file() and not path.is_symlink() and before.st_nlink == 1
    with path.open('rb') as stream:
        value = hashlib.file_digest(stream, 'sha256').hexdigest()
    after = path.stat()
    assert (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) == (
        after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
    return dict(path=str(path), bytes=after.st_size, sha256=value)


def bind_tool(path):
    """Bind an installed executable, admitting normal package-manager hardlinks.

    Source, archive and output files continue to use strict bind().
    Device/inode/mode/link count/timestamps are returned as well as the full
    content hash, so the final tool comparison detects replacement or mutation.
    """
    path = Path(path).resolve(strict=True)
    before = path.lstat()
    assert stat.S_ISREG(before.st_mode) and before.st_nlink >= 1
    assert os.access(path, os.X_OK)
    def identity(value):
        return (value.st_dev, value.st_ino, value.st_mode, value.st_nlink,
                value.st_size, value.st_mtime_ns, value.st_ctime_ns)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        opened = os.fstat(stream.fileno())
        assert stat.S_ISREG(opened.st_mode) and identity(opened) == identity(before)
        value = hashlib.file_digest(stream, 'sha256').hexdigest()
        assert identity(os.fstat(stream.fileno())) == identity(before)
    after = path.lstat()
    assert identity(after) == identity(before)
    return dict(path=str(path), bytes=after.st_size, sha256=value,
                device=after.st_dev, inode=after.st_ino, mode=after.st_mode,
                nlink=after.st_nlink, mtime_ns=after.st_mtime_ns, ctime_ns=after.st_ctime_ns)


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def available():
    return int(next(s.split()[1] for s in Path('/proc/meminfo').read_text().splitlines()
                    if s.startswith('MemAvailable:')))


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args], timeout=30)


def method(source, signature):
    """Extract a complete brace-balanced production method, without edits."""
    assert source.count(signature) == 1, "Missing/duplicate production method"
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        assert end < len(source), "Unclosed production method"
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def ranking_bridge(destination):
    jni, cmake = JNI.read_text(), PRIVACY_CMAKE.read_text()
    reset = method(cmake, "bool Session::ResetPredictionContexts(bool enabled, bool update_option)")
    reset = reset.replace("bool Session::ResetPredictionContexts", "bool RankingSessionFixture::ResetPredictionContexts", 1)
    methods = [method(jni, signature) for signature in (
        "bool clearPredictionContext()", "bool setPredictionEnabled(bool enabled)",
        "void setCandidatePins(const std::vector<std::string>& schemas,",
        "void applyCandidatePins()")]
    lookups = ("rime::Service::instance().GetSession(id)",
               "rime::Service::instance().GetSession(session(false))")
    # Production algorithms are unchanged. Typed lookup connects the private
    # JNI singleton's methods to this exclusively owned real Engine fixture.
    for lookup in lookups:
        methods = [body.replace(lookup, "current_") for body in methods]
    destination.write_text(
        '#pragma once\n#include "candidate_ranking.h"\n#include <map>\n'
        '#include <rime/engine.h>\n#include <rime/schema.h>\n'
        'using rime::Context;\nusing rime::Engine;\n'
        'class RankingSessionFixture {\n public:\n'
        '  RankingSessionFixture() : engine_(rime::Engine::Create()) {}\n'
        '  rime::Context* context() { return engine_->context(); }\n'
        '  rime::Schema* schema() { return engine_->schema(); }\n'
        '  bool ResetPredictionContexts(bool enabled, bool update_option);\n'
        ' private:\n  std::unique_ptr<rime::Engine> engine_;\n};\n' + reset + '\n'
        'class RankingBridgeFixture {\n public:\n'
        '  explicit RankingBridgeFixture(RankingSessionFixture* current) : current_(current) {}\n' +
        '\n'.join(methods) + '\n private:\n'
        '  int session(bool = true) { return 1; }\n'
        '  std::string currentSchemaId() { return current_->schema()->schema_id(); }\n'
        '  std::string rawInput() { return current_->context()->input(); }\n'
        '  RankingSessionFixture* current_;\n  bool privacy_mode_ = false;\n'
        '  rime::an<rime::Menu> ranked_menu_, original_menu_;\n'
        '  std::map<std::pair<std::string, std::string>, std::string> pins_;\n};\n')


def resolve_tool(value):
    """Resolve an explicit path or executable name once; record its real identity."""
    word = str(value)
    located = shutil.which(word)
    path = Path(located if located is not None else word).expanduser().resolve(strict=True)
    assert path.is_file() and os.access(path, os.X_OK), f"Missing executable: {word}"
    return path



def source_status(allow_model_source_change):
    status = git('status', '--porcelain', '--untracked-files=no', '-z')
    permitted = b' M script/quality/t11/prediction.tsv\0'
    assert not status or (allow_model_source_change and status == permitted), (
        'Require clean tracked source, or explicitly allow only an unstaged prediction.tsv edit')
    return status


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', '--output', dest='output', type=Path, required=True)
    parser.add_argument('--boost-archive', type=Path, required=True)
    parser.add_argument('--cmake', default=os.environ.get('CMAKE_EXECUTABLE', 'cmake'))
    parser.add_argument('--c-compiler', default='cc')
    parser.add_argument('--compiler', '--cxx-compiler', dest='cxx_compiler', default='c++')
    parser.add_argument('--ninja', default='ninja')
    parser.add_argument('--readelf', default='readelf')
    parser.add_argument('--build-model-tool', action='store_true', help='Also build/audit the same-source model builder; never write assets')
    parser.add_argument('--allow-model-source-change', action='store_true', help='Only with --build-model-tool: admit an unstaged prediction.tsv edit, bound before/after')
    parser.add_argument('--expected-head', help='Optional explicit clean source commit binding')
    parser.add_argument('--minimum-start-available-kib', type=int, default=FLOOR_KIB)
    args = parser.parse_args()
    assert not args.allow_model_source_change or args.build_model_tool
    assert sys.platform.startswith('linux'), 'This same-source diagnostic uses Linux /proc and ELF'
    observed_head = git('rev-parse', 'HEAD').decode().strip()
    if args.expected_head is None:
        args.expected_head = observed_head
    assert re.fullmatch(r'[0-9a-f]{40}', args.expected_head) and args.expected_head == observed_head
    assert args.minimum_start_available_kib >= FLOOR_KIB
    source = Path(__file__).resolve().parent
    out = args.output.absolute()
    assert out.resolve() == out and out.is_relative_to(ROOT / 'build/t11')
    assert out != ROOT / 'build/t11' and not out.exists() and not out.is_relative_to(source)
    out.mkdir(parents=True, mode=0o700)
    started = time.monotonic()
    deadline = started + 5400
    commands = []
    samples = []
    forced = []
    cleanup_errors = []
    verification_errors = []
    error = None
    passed = False
    tracker = None
    freeze_binding = None
    source_before = None
    status_before = None
    tool_before = None
    preparation = None
    elf_audit = None
    actual_test_checks = None
    stage = out / 'stage'
    project = out
    env = dict(os.environ)
    removed_environment = []
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
                'CFLAGS', 'CXXFLAGS', 'CPPFLAGS', 'LDFLAGS', 'LIBRARY_PATH',
                'CPATH', 'C_INCLUDE_PATH', 'CPLUS_INCLUDE_PATH', 'LD_LIBRARY_PATH',
                'LD_PRELOAD', 'CMAKE_PREFIX_PATH', 'BOOST_ROOT', 'RIME_PLUGINS'):
        if key in env:
            removed_environment.append(key)
            del env[key]
    env['CMAKE_BUILD_PARALLEL_LEVEL'] = '1'
    # Copied native submodules have no .git. Direct git calls must not describe
    # the enclosing checkout; retain each dependency's official archive fallback.
    env['GIT_CEILING_DIRECTORIES'] = str(stage / 'source')

    def bounded_logs():
        paths = [p for p in out.glob('command-*.*') if p.suffix in ('.stdout', '.stderr')]
        sizes = [p.stat().st_size for p in paths]
        assert all(size <= 64 * 1024 * 1024 for size in sizes), 'Command log exceeds64MiB'
        assert sum(sizes) <= 128 * 1024 * 1024, 'Combined command logs exceed128MiB'

    def all_busy():
        busy = {row['pid']: row for row in g.busy_processes()}
        native_names = {'cmake', 'ninja', 'make', 'gmake', 'cc', 'c++', 'gcc', 'g++',
                        'clang', 'clang++', 'cc1', 'cc1plus', 'collect2', 'ld', 'ld.lld', 'lld', 'as'}
        for entry in Path('/proc').iterdir():
            if not entry.name.isdecimal():
                continue
            try:
                words = (entry / 'cmdline').read_bytes().split(b'\0')
                if not words or not words[0]:
                    continue
                name = Path(os.fsdecode(words[0])).name
                if name in ('java', 'java.exe', 'javac', 'javac.exe'):
                    busy.setdefault(int(entry.name), dict(pid=int(entry.name), kind='jvm', executable=name))
                elif name in native_names or re.fullmatch(r'(?:.+-)?(?:gcc|g\+\+|clang|clang\+\+)(?:-[0-9.]+)?', name):
                    busy.setdefault(int(entry.name), dict(pid=int(entry.name), kind='native', executable=name))
            except OSError:
                continue
        return list(busy.values())

    def check_external(allow_owned):
        bounded_logs()
        busy = all_busy()
        # Reuse the unchanged reviewed JVM/VM ownership gate. Native compiler
        # processes have their own accurately labelled PID/start ancestry gate.
        tracker.require_busy_owned([row for row in busy if row['kind'] != 'native'], allow_owned=allow_owned)
        for row in busy:
            if row['kind'] != 'native':
                continue
            try:
                actual = g.identity(row['pid'])
            except FileNotFoundError:
                continue
            if not (allow_owned and tracker.key(actual) in tracker.owned):
                tracker.foreign_busy.append(dict(kind='native', identity=actual,
                                                ownership_established=tracker.key(actual) in tracker.owned))
                raise RuntimeError('Foreign native build or still-active previous native process')

    def settle_owned_success(row,command_deadline):
     # Client return does not prove its detached single-use daemon has exited.
     # Keep the same command/global budgets and start no subsequent tool here.
     began=time.monotonic();settle_deadline=min(command_deadline,deadline,began+10)
     record=dict(started_monotonic=began,deadline_monotonic=settle_deadline,limit_seconds=10,
                 completed=False,checks=0,signals_sent=False,observed_live_descendants=[])
     row['graceful_descendant_settle']=record
     last=None
     while True:
      memory=available();samples.append(dict(epoch=time.time(),available_kib=memory))
      assert memory>=1048576,'1GiB floor during natural descendant shutdown'
      assert time.monotonic()<settle_deadline,'Natural descendant shutdown command/global/10s deadline'
      check_external(True) # discovers ancestry; foreign VM/JVM still fails immediately
      current=tracker.live_owned();record['checks']+=1
      identities=[dict(pid=v['pid'],starttime=v['starttime'],ppid=v['ppid'],pgid=v['pgid']) for v in current]
      if identities!=last:
       record['observed_live_descendants'].append(dict(observed_monotonic=time.monotonic(),identities=identities));last=identities
      if not current:
       record['finished_monotonic']=time.monotonic()
       assert record['finished_monotonic']<settle_deadline,'Natural shutdown observed after deadline'
       record['completed']=True
       return
      remaining=settle_deadline-time.monotonic()
      assert remaining>0,'Natural descendant shutdown deadline before polling'
      time.sleep(min(.1,remaining))


    def run(argv,limit):
     row=dict(argv=[str(v) for v in argv],started_epoch=time.time(),signals=[]);commands.append(row)
     stdout=out/('command-%02d.stdout'%len(commands));stderr=out/('command-%02d.stderr'%len(commands));child=None;token=None
     try:
      assert time.monotonic()<deadline and available()>=1048576;check_external(False)
      command_deadline=min(deadline,time.monotonic()+limit);row['deadline_monotonic']=command_deadline
      with stdout.open('xb') as so,stderr.open('xb') as se:
       assert time.monotonic()<command_deadline
       child=subprocess.Popen(row['argv'],cwd=project,env=env,stdout=so,stderr=se,start_new_session=True);row['pid']=child.pid
       try:token=g.identity(child.pid);tracker.seed(token);row['owned']=token
       except FileNotFoundError:assert child.poll() is not None
       while child.poll() is None:
        mem=available();samples.append(dict(epoch=time.time(),available_kib=mem));assert mem>=1048576,'1GiB memory floor'
        assert time.monotonic()<command_deadline,'Observed command/global deadline';check_external(True);time.sleep(.25)
       row['actual_exit']=child.wait();tracker.discover();row['actual_process_exit_observed_monotonic']=time.monotonic()
       assert row['actual_exit']==0 and row['actual_process_exit_observed_monotonic']<command_deadline,'Actual exit or observed completion deadline failed'
       settle_owned_success(row,command_deadline)
       row['observed_end_monotonic']=time.monotonic()
       assert row['observed_end_monotonic']<command_deadline,'Command/global deadline after natural descendant shutdown'
     except BaseException:row['error']=traceback.format_exc();raise
     finally:
      cleanup_errors=[]
      try:tracker.discover()
      except BaseException:cleanup_errors.append(traceback.format_exc())
      if child is not None and child.poll() is None:
       try:
        assert token is not None,'No authenticated Popen identity; refusing signal'
        if tracker.signal_owned(token,signal.SIGTERM):row['signals'].append('SIGTERM');forced.append(dict(token,signal='SIGTERM'))
        try:child.wait(timeout=5)
        except subprocess.TimeoutExpired:
         tracker.discover()
         if tracker.signal_owned(token,signal.SIGKILL):row['signals'].append('SIGKILL');forced.append(dict(token,signal='SIGKILL'))
         child.wait(timeout=5)
       except BaseException:cleanup_errors.append(traceback.format_exc())
      if child is not None:row['actual_exit']=child.poll()
      try:tracker.discover()
      except BaseException:cleanup_errors.append(traceback.format_exc())
      row['cleanup_errors']=cleanup_errors
      row['finished_epoch']=time.time();row['finished_monotonic']=time.monotonic()
      if stdout.is_file():row['stdout']=bind(stdout)
      if stderr.is_file():row['stderr']=bind(stderr)
      ownership_path=out/('command-%02d-ownership.json'%len(commands));save(ownership_path,tracker.report());row['ownership']=bind(ownership_path)
      save(out/'commands.json',commands);save(out/'memory.json',samples)
     assert not row['cleanup_errors'],'Command cleanup/identity error; original retained'
     bounded_logs()
     return stdout.read_text(),stderr.read_text()

    def checkpoint():
        memory = available()
        samples.append(dict(epoch=time.time(), available_kib=memory))
        assert memory >= FLOOR_KIB, '1GiB running memory floor'
        assert time.monotonic() < deadline, '90minute global deadline'
        check_external(False)

    def warn_free():
        bounded_logs()
        found = []
        pattern = re.compile(r'(?mi)(?:^\s*(?:CMake (?:Deprecation )?Warning\b|warning:|w:|WARNING\b)|:\s*warning\b)')
        for row in commands:
            for stream in ('stdout', 'stderr'):
                value = row.get(stream)
                if value:
                    text = Path(value['path']).read_text(errors='strict')
                    for line in text.splitlines():
                        if pattern.search(line):
                            found.append(dict(command=row['argv'], stream=stream, line=line))
        save(out / 'warnings.json', found)
        assert not found, 'Original command warnings present; no filtering or downgrade'

    try:
        # Bind committed entry/helper/assertion sources without a local ignored
        # manifest. prepare.py additionally snapshots every copied native input.
        source_files = [source / 'run_native_prediction_tests.py',
                        source / 'prediction_native_test.cc', source / 'ranking_boundary_test.h',
                        source / 'build_predict_db.cc',
                        *(source / 'host_native' / name for name in
                          ('prepare.py', 'processes.py', 'options.cmake', 'probe.cmake'))]
        source_before = {str(path): bind(path) for path in source_files}
        save(out / 'source-before.json', source_before)
        freeze_binding = bind(out / 'source-before.json')
        g = load_module('same_source_native_processes', source / 'host_native/processes.py')
        tracker = g.OwnedProcessTracker(g.identity)
        assert git('rev-parse', 'HEAD').decode().strip() == args.expected_head
        status_before = source_status(args.allow_model_source_change)
        for index in range(3):
            checkpoint()
            assert samples[-1]['available_kib'] >= args.minimum_start_available_kib, 'Requested startup headroom'
            save(out / 'memory.json', samples)
            if index < 2:
                time.sleep(1)
        tool_paths = {key: resolve_tool(getattr(args, key)) for key in (
            'cmake', 'c_compiler', 'cxx_compiler', 'ninja', 'readelf')}
        tool_paths['python'] = Path(sys.executable).resolve(strict=True)
        tool_paths['boost_archive'] = args.boost_archive.resolve(strict=True)
        tool_before = {key: (bind_tool(path) if key in EXECUTABLE_TOOL_KEYS else bind(path))
                       for key, path in tool_paths.items()}
        save(out / 'tools-before.json', tool_before)
        save(out / 'environment.json', dict(explicit={'CMAKE_BUILD_PARALLEL_LEVEL': '1',
                                           'GIT_CEILING_DIRECTORIES': str(stage / 'source')},
                                           removed_inherited_keys=removed_environment))
        prepare_argv = [sys.executable, '-B', str(source / 'host_native/prepare.py'), '--repo', str(ROOT), '--out', str(stage)]
        for key in ('boost_archive', 'cmake', 'c_compiler', 'cxx_compiler', 'ninja'):
            prepare_argv += ['--' + key.replace('_', '-'), str(tool_paths[key])]
        if args.build_model_tool:
            prepare_argv.append('--build-model-tool')
        if args.allow_model_source_change:
            prepare_argv.append('--allow-model-source-change')
        run(prepare_argv, 900)
        preparation_path = stage / 'preparation.json'
        preparation = json.loads(preparation_path.read_bytes())
        assert preparation['prepared'] is True and preparation['assertion_sources_unchanged'] is True
        assert preparation['build_started'] is preparation['test_started'] is False
        assert preparation['source_head'] == args.expected_head
        plan_path = stage / 'commands.json'
        assert bind(plan_path)['sha256'] == preparation['commands']['sha256']
        plan = json.loads(plan_path.read_bytes())
        assert [row['stage'] for row in plan] == ['configure', 'build-original-384-fixture',
            'ELF-and-link-input-audit-before-execution', 'run-original-384-fixture']
        expected_env = {'CMAKE_BUILD_PARALLEL_LEVEL': '1', 'GIT_CEILING_DIRECTORIES': str(stage / 'source')}
        assert all(row.get('environment') == expected_env for row in plan[:2])
        assert not any('CMAKE_BUILD_PARALLEL_LEVEL=' in arg for row in plan[:2] for arg in row['argv'])
        binary = Path(preparation['binary'])
        project = binary
        run(plan[0]['argv'], 300)
        warn_free()
        run(plan[1]['argv'], 3600)
        warn_free()
        checkpoint()
        compile_path = binary / 'compile_commands.json'
        compile_commands = json.loads(compile_path.read_bytes())
        assert compile_commands
        stage_source = Path(preparation['source_root'])
        opencc_definitions = []
        fixture_rows = []
        for row in compile_commands:
            compilation_file = Path(row['file']).resolve()
            assert compilation_file.is_relative_to(stage_source) or compilation_file.is_relative_to(binary)
            argv = row.get('arguments') or shlex.split(row['command'])
            assert all(not re.search(r'/usr/(?:local/)?include/(?:rime(?:/|$)|opencc(?:/|$))', word)
                       for word in argv), 'Distribution private native header admitted'
            if compilation_file.name == 'prediction_native_test.cc':
                fixture_rows.append(row)
            if '/OpenCC/src/' in str(compilation_file) and compilation_file.suffix in ('.cc', '.cpp'):
                definitions = [arg for arg in argv if arg.startswith('-DOPENCC_VERSION=')]
                assert definitions == ['-DOPENCC_VERSION="1.4.2"'], 'Actual OpenCC compile version differs from supported source fallback'
                assert '-DRAPIDJSON_NOMEMBERITERATORCLASS' in argv
                opencc_definitions.append(dict(file=str(compilation_file), version=definitions[0]))
        assert len(fixture_rows) == 1 and opencc_definitions
        configured = binary / 'librime/src/rime/build_config.h'
        assert re.search(r'(?m)^#define RIME_ENABLE_LOGGING(?:\s|$)', configured.read_text())
        target_info = (binary / 'host-targets.txt').read_text()
        archive_names = ('rime', 'glog', 'yaml', 'snappy', 'leveldb', 'marisa', 'opencc')
        archives = {}
        for name in archive_names:
            matches = re.findall(r'(?m)^' + name + r'_archive=(.+)$', target_info)
            assert len(matches) == 1
            path = Path(matches[0]).resolve(strict=True)
            assert path.is_relative_to(binary) and path.suffix == '.a'
            archives[name] = bind(path)
        test_binary = binary / 'host-bin/prediction-native-test'
        dynamic_text, _ = run([tool_paths['readelf'], '-d', test_binary], 30)
        symbols_text, _ = run([tool_paths['readelf'], '-Ws', test_binary], 30)
        needed = re.findall(r'\(NEEDED\).*\[([^]]+)\]', dynamic_text)
        assert needed and not any(re.match(r'lib(?:rime|opencc|marisa|glog|yaml-cpp|leveldb|snappy|lua|boost)', n) for n in needed)
        assert '_ZN4rime14ConcreteEngine19ProcessSyntheticKeyERKNS_8KeyEventE' in symbols_text
        link_map = binary / 'prediction-native.map'
        loads = []
        for raw in re.findall(r'(?m)^LOAD (.+\.a)$', link_map.read_text()):
            path = Path(raw)
            path = (path if path.is_absolute() else binary / path).resolve(strict=True)
            loads.append(path)
        assert all(Path(row['path']) in loads for row in archives.values()), 'Owned native archive absent from real link map'
        assert not any(re.search(r'/usr/(?:local/)?lib[^/]*/lib(?:rime|opencc|marisa|glog|yaml-cpp|leveldb|snappy|lua|boost)', str(p)) for p in loads)
        elf_audit = dict(passed=True, binary=bind(test_binary), needed=needed, archives=archives,
                         link_map=bind(link_map), compile_commands=bind(compile_path),
                         actual_build_config=bind(configured), target_info=bind(binary / 'host-targets.txt'),
                         actual_opencc_version_definitions=opencc_definitions,
                         fixture_compile_command=fixture_rows[0], source_only_no_android_claim=True)
        if args.build_model_tool:
            model_sources = preparation['generic_model_sources']
            assert set(model_sources) == {'predict_db.h', 'predict_db.cc', 'build_predict_db.cc', 'LICENSE'}
            for item in model_sources.values():
                assert item['inverse_exact'] is True
                for role in ('original', 'generated'):
                    row = item[role]
                    assert bind(row['path']) == row
            model_rows = [row for row in compile_commands
                          if Path(row['file']).resolve() == Path(model_sources['build_predict_db.cc']['generated']['path'])]
            generic_rows = [row for row in compile_commands
                            if Path(row['file']).resolve() == Path(model_sources['predict_db.cc']['generated']['path'])]
            assert len(model_rows) == len(generic_rows) == 1, 'Expected one isolated generic builder and one matching reader'
            model_binary = binary / 'host-bin/prediction-model-native'
            model_dynamic, _ = run([tool_paths['readelf'], '-d', model_binary], 30)
            model_symbols, _ = run([tool_paths['readelf'], '-Ws', '--demangle', model_binary], 30)
            required_symbols = ('trime_model_tool::PredictDb::Load()', 'trime_model_tool::PredictDb::Build(',
                                'trime_model_tool::PredictDb::Lookup(')
            assert all(symbol in model_symbols for symbol in required_symbols)
            assert not any(symbol in model_symbols for symbol in (
                'rime::PredictDb::', 'rime::Predictor::', 'rime_predict_initialize',
                'rime_register_module_predict', 'rime_require_module_predict')), 'Production prediction module leaked into generic model tool'
            model_needed = re.findall(r'\(NEEDED\).*\[([^]]+)\]', model_dynamic)
            assert model_needed and not any(re.match(r'lib(?:rime|opencc|marisa|glog|yaml-cpp|leveldb|snappy|lua|boost)', n)
                                            for n in model_needed)
            model_map = binary / 'prediction-model.map'
            model_loads = []
            for raw in re.findall(r'(?m)^LOAD (.+\.a)$', model_map.read_text()):
                path = Path(raw)
                model_loads.append((path if path.is_absolute() else binary / path).resolve(strict=True))
            assert all(Path(row['path']) in model_loads for row in archives.values())
            assert not any(re.search(r'/usr/(?:local/)?lib[^/]*/lib(?:rime|opencc|marisa|glog|yaml-cpp|leveldb|snappy|lua|boost)', str(path))
                           for path in model_loads)
            elf_audit['model_builder'] = dict(passed=True, binary=bind(model_binary),
                needed=model_needed, link_map=bind(model_map), archives=archives,
                compile_command=model_rows[0], generic_reader_compile_command=generic_rows[0],
                actual_build_config=bind(configured), isolated_namespace='trime_model_tool',
                generated_sources=model_sources, production_predict_symbols_absent=True)
        save(out / 'elf-and-source-audit.json', elf_audit)
        warn_free()
        expected_argv = [str(test_binary), str(stage_source / 'app/src/main/assets/shared/trime_predict.db'),
                         str(stage / 'runtime')]
        assert plan[3]['argv'] == expected_argv and plan[3]['timeout_seconds'] == 60
        stdout, _ = run(expected_argv, 60)
        counts = re.findall(r'(?m)^PASS ([0-9]+) actual native prediction checks$', stdout)
        assert counts == ['384'], 'The original exact384 checks did not complete'
        actual_test_checks = int(counts[0])
        warn_free()
        checkpoint()
        passed = True
    except BaseException:
        error = traceback.format_exc()
    finally:
        residual = []
        unresolved = []
        if tracker is not None:
            try:
                tracker.discover()
                grace = time.monotonic() + 5
                while tracker.live_owned() and time.monotonic() < grace:
                    time.sleep(.1)
                    tracker.discover()
                for sig, wait_seconds in ((signal.SIGTERM, 5), (signal.SIGKILL, 2)):
                    for token in tracker.live_owned():
                        try:
                            if tracker.signal_owned(token, sig):
                                forced.append(dict(token, signal=sig.name))
                        except BaseException:
                            cleanup_errors.append(traceback.format_exc())
                    end = time.monotonic() + wait_seconds
                    while tracker.live_owned() and time.monotonic() < end:
                        time.sleep(.1)
                        tracker.discover()
                tracker.discover()
                residual = tracker.live_owned()
                unresolved = tracker.unresolved_group_members
            except BaseException:
                cleanup_errors.append(traceback.format_exc())
        stable = False
        try:
            if source_before is not None:
                current_source = {p: bind(p) for p in source_before}
                assert current_source == source_before
                assert bind(out / 'source-before.json') == freeze_binding
            assert git('rev-parse', 'HEAD').decode().strip() == args.expected_head
            assert status_before is not None and source_status(args.allow_model_source_change) == status_before
            if tool_before is not None:
                assert {key: (bind_tool(row['path']) if key in EXECUTABLE_TOOL_KEYS else bind(row['path']))
                        for key, row in tool_before.items()} == tool_before
            if preparation is not None:
                for relative, expected in preparation['inputs_before'].items():
                    observed = bind(ROOT / relative)
                    assert {key: observed[key] for key in ('bytes', 'sha256')} == expected
                for name in ('prediction_native_test.cc', 'ranking_boundary_test.h'):
                    expected = preparation['inputs_before']['script/quality/t11/' + name]
                    observed = bind(Path(preparation['source_root']) / 'script/quality/t11' / name)
                    assert {key: observed[key] for key in ('bytes', 'sha256')} == expected
                if elf_audit:
                    for row in [elf_audit[key] for key in ('binary', 'link_map', 'compile_commands', 'actual_build_config', 'target_info')] + list(elf_audit['archives'].values()):
                        assert bind(row['path']) == row
                    if elf_audit.get('model_builder'):
                        for key in ('binary', 'link_map', 'actual_build_config'):
                            row = elf_audit['model_builder'][key]
                            assert bind(row['path']) == row
                        for item in elf_audit['model_builder']['generated_sources'].values():
                            for role in ('original', 'generated'):
                                row = item[role]
                                assert bind(row['path']) == row
            if tracker is not None:
                checkpoint()
                tracker.discover()
                residual = tracker.live_owned()
                unresolved = tracker.unresolved_group_members
            stable = source_before is not None and tool_before is not None and preparation is not None
        except BaseException:
            verification_errors.append(traceback.format_exc())
        if tracker is not None:
            save(out / 'ownership-final.json', tracker.report())
        save(out / 'memory.json', samples)
        accepted = bool(passed and stable and not cleanup_errors and not verification_errors
                        and not forced and not residual and not unresolved and time.monotonic() < deadline)
        result = dict(passed=accepted, error=error, verification_errors=verification_errors,
                      cleanup_errors=cleanup_errors, signals=forced, residual_owned=residual,
                      unresolved=unresolved, source_freeze=freeze_binding,
                      source_before=source_before, tools_before=tool_before,
                      preparation=preparation, elf_audit=elf_audit, actual_test_checks=actual_test_checks, model_builder_requested=args.build_model_tool,
                      allow_model_source_change=args.allow_model_source_change,
                      tracked_status_before=status_before.decode() if status_before is not None else None,
                      commands=commands, workers=1, global_timeout_seconds=5400,
                      stage_timeout_seconds=dict(prepare=900, configure=300, build=3600, test=60),
                      global_deadline_respected=time.monotonic() < deadline,
                      minimum_available_kib=min((v['available_kib'] for v in samples), default=None),
                      requested_start_kib=args.minimum_start_available_kib, running_floor_kib=FLOOR_KIB,
                      duration_seconds=time.monotonic() - started, source_inputs_verified=stable,
                      no_android_or_release_acceptance_claim=True)
        save(out / 'result.json', result)
        if time.monotonic() >= deadline:
            result.update(passed=False, global_deadline_respected=False)
            save(out / 'result.json', result)
    print(json.dumps(dict(passed=result['passed'], result=str(out / 'result.json'))))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
