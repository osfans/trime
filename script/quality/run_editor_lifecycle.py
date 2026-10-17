#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Independent editor regression: process death, rotation and cross-application focus."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import uuid

if sys.flags.optimize:
    raise SystemExit(
        "Editor lifecycle gate refuses optimized Python (-O/-OO); "
        "its runtime assertions must remain enabled"
    )

from PIL import Image

from editor_windows import capture_editor_window
from editor_pixels import audit_t9_pixels, normalize_screenshot
from runtime_audit import LOG_LINE, audit_log, device_audit_lock, log_after_marker, record_logcat

PACKAGE = 'com.osfans.trime.debug'
FIXTURE = 'org.x2m7.trime.editorfixture'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--fixture-apk', type=Path, required=True)
    parser.add_argument('--observer-apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--release', action='store_true',
                        help='Verify the non-debuggable public package; use an already-rooted disposable emulator for process death')
    parser.add_argument('--split-screen', action='store_true', help='Exercise the verified AOSP API 35 split-screen shell commands')
    parser.add_argument('--split-screen-only', action='store_true', help='Run only split-screen checkpoints, without claiming the fullscreen matrix')
    args = parser.parse_args()
    args.split_screen = args.split_screen or args.split_screen_only
    with device_audit_lock(args.serial):
        run(args)


def run(args):
    out = args.output
    out.mkdir(parents=True, exist_ok=False)
    adb = [args.adb, '-s', args.serial]

    def shell(*command):
        return subprocess.check_output(adb + ['shell', *map(str, command)], text=True, stderr=subprocess.STDOUT, timeout=120).strip()

    assert shell('getprop', 'ro.kernel.qemu') == '1', 'Disposable emulator only'
    if args.release:
        assert re.fullmatch(r'uid=0\(root\)(?:[ \t]+[^\r\n]*)?', shell('id')), 'Release process-death probe requires an already-rooted disposable emulator'
    assert shell('cat', f'/sdcard/Android/data/{PACKAGE}/files/runtime-audit-dedicated') == ''
    assert shell('settings', 'get', 'secure', 'default_input_method').startswith(PACKAGE + '/')
    identity = {'api': int(shell('getprop', 'ro.build.version.sdk')), 'fingerprint': shell('getprop', 'ro.build.fingerprint'),
                'serial': args.serial, 'source_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'fullscreen_matrix': not args.split_screen_only, 'split_screen': args.split_screen,
                'release': args.release, 'process_kill': 'root_shell' if args.release else 'run_as', 'packages': {}}
    for package, path in ((PACKAGE, args.apk), (FIXTURE, args.fixture_apk), ('org.x2m7.trime.editorobserver', args.observer_apk)):
        remote = shell('pm', 'path', package).removeprefix('package:')
        assert remote.startswith('/') and '\n' not in remote
        with tempfile.TemporaryFile() as apk:
            subprocess.run(adb + ['exec-out', 'cat', remote], stdout=apk, check=True, timeout=120)
            apk.seek(0)
            digest = hashlib.file_digest(apk, 'sha256').hexdigest()
        assert digest == hashlib.sha256(path.read_bytes()).hexdigest()
        identity['packages'][package] = digest
        package_dump = shell('dumpsys', 'package', package)
        (out / (package + '.txt')).write_text(package_dump)
        if args.release and package == PACKAGE:
            assert not re.search(r'\bDEBUGGABLE\b', package_dump), 'Expected a non-debuggable Release APK'
    marker = 'BEGIN-' + str(uuid.uuid4())
    identity['log_capture_marker'] = marker
    identity['harness_sha256'] = {name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
                                for name in ('editor_windows.py', 'editor_pixels.py', 'runtime_audit.py')}
    (out / 'identity.json').write_text(json.dumps(identity, indent=2) + '\n')
    if args.split_screen:
        assert identity['api'] == 35, 'Split-screen driver verified on AOSP API 35 only'
        sizes = re.findall(r'(?:Physical|Override) size: (\d+)x(\d+)', shell('wm', 'size'))
        assert sizes, 'Cannot establish screenshot coordinates'
        portrait_size = tuple(map(int, sizes[-1]))
        assert portrait_size[0] < portrait_size[1], 'Portrait emulator required for split pixel checks'
    original_rotation = {key: shell('settings', 'get', 'system', key) for key in ('accelerometer_rotation', 'user_rotation')}
    pids = set()
    checkpoints = []
    split_task = None

    def pid(wait=True):
        deadline = time.monotonic() + 30
        while True:
            rows = shell('ps', '-A') if identity['api'] >= 26 else shell('ps')
            matches = [int(row.split()[1]) for row in rows.splitlines() if row.split() and row.split()[-1] == PACKAGE]
            assert len(matches) <= 1, 'Expected at most one main Trime process'
            if matches:
                pids.add(matches[0])
                return matches[0]
            if not wait:
                return None
            assert time.monotonic() < deadline, 'Trime process did not restart'
            time.sleep(.1)

    def window(label, ready=False):
        expected = [('resource-id', FIXTURE + ':id/editor')]
        if ready:
            expected.append(('resource-id', PACKAGE + ':id/keyboard_view'))
        attempts = []
        deadline = time.monotonic() + 90
        while True:
            root, xml, batch = capture_editor_window(shell, expected, attempts=1)
            attempts.extend(batch)
            if root is not None or time.monotonic() >= deadline:
                break
        (out / (label + '-capture.json')).write_text(json.dumps(attempts, indent=2) + '\n')
        assert root is not None, 'Editor/ready keyboard hierarchy unavailable'
        (out / (label + '.xml')).write_text(xml)
        return root

    def checkpoint(label, t9=True, expected='STABLE'):
        pid(wait=False)
        root = window(label + '-focus')
        editor = next(n for n in root.iter('node') if n.get('resource-id') == FIXTURE + ':id/editor')
        bounds = list(map(int, re.findall(r'-?\d+', editor.get('bounds'))))
        shell('input', 'tap', (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
        deadline = time.monotonic() + 90
        while True:
            dump = shell('dumpsys', 'input_method')
            if 'mInputShown=true' in dump:
                break
            assert time.monotonic() < deadline, 'Keyboard failed to show'
            time.sleep(.2)
        time.sleep(.7)
        root = window(label, ready=True)
        editor = next(n for n in root.iter('node') if n.get('resource-id') == FIXTURE + ':id/editor')
        password = editor.get('password') == 'true'
        password_evidence = None
        if password:
            if editor.get('text'):
                assert len(editor.get('text')) == len(expected), (label, 'Unexpected masked length')
                password_evidence = 'masked_length_only'
            else:
                # API21 redacts the entire accessibility value. Do not enable
                # speak-password or pretend an empty value proves its contents.
                assert identity['api'] == 21, 'Unexpected password redaction on this image'
                info = dump.split('mInputEditorInfo:', 1)[1].split('mShowInputRequested=', 1)[0]
                assert 'inputType=0x81 ' in info and 'packageName=' + FIXTURE + ' ' in info
                assert f'initialSelStart={len(expected)} initialSelEnd={len(expected)} ' in info
                assert f'mCursorSelStart={len(expected)} mCursorSelEnd={len(expected)} mCursorCandStart=-1 mCursorCandEnd=-1' in dump
                password_evidence = 'redacted_type_and_caret_only'
        else:
            assert editor.get('text') == expected, (label, editor.get('text'), expected)
        descriptions = {n.get('content-desc') for n in root.iter('node')}
        nine_key = all(v in descriptions for v in ('2 ABC', '3 DEF', '4 GHI', '5 JKL', '6 MNO', '7 PQRS', '8 TUV', '9 WXYZ'))
        assert nine_key == t9, (label, 'Unexpected keyboard', sorted(v for v in descriptions if v))
        (out / (label + '-input-method.txt')).write_text(dump)
        with (out / (label + '.png')).open('wb') as png:
            subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=png, check=True, timeout=60)
        pixels = None
        if t9:
            with Image.open(out / (label + '.png')) as screenshot:
                rotation_value = shell('settings', 'get', 'system', 'user_rotation')
                display_rotation = None if rotation_value == 'null' else int(rotation_value)
                normalized, screenshot_info = normalize_screenshot(screenshot, root, display_rotation)
                if args.split_screen and 'split' in label:
                    assert tuple(screenshot_info['raw_size']) == portrait_size, 'Split screenshot dimensions changed'
                    assert tuple(screenshot_info['logical_size']) == portrait_size, 'Split hierarchy dimensions changed'
                pixels = audit_t9_pixels(normalized, root, screenshot_info['logical_size'])
                pixels['screenshot'] = screenshot_info
            (out / (label + '-pixels.json')).write_text(json.dumps(pixels, indent=2) + '\n')
            assert pixels['passed'], 'T9 surface is blank or obscured; inspect screenshot and pixel audit'
        checkpoints.append({'name': label, 'pid': pid(), 't9': nine_key, 'expected_editor_text': expected,
                            'observed_editor_text': editor.get('text'), 'password_evidence': password_evidence,
                            'nonblank_t9_pixels': pixels['passed'] if pixels else None,
                            'masked_length_only': password_evidence == 'masked_length_only'})
        print('PASS:', label, flush=True)

    def launch(mode='chat', text='STABLE'):
        response = shell('am', 'start', '-S', '-W', '-n', FIXTURE + '/.EditorActivity', '--es', 'mode', mode, '--es', 'text', text)
        assert 'Status: ok' in response, response

    def split_command(*args):
        return shell('dumpsys', 'activity', 'service', 'com.android.systemui/.SystemUIService', 'WMShell', 'splitscreen', *args)

    def check_split(label):
        dump = shell('dumpsys', 'activity', 'activities')
        (out / (label + '-activities.txt')).write_text(dump)
        assert re.search(r'\* Task\{[^\n]*#' + str(split_task) + r' [^\n]*mode=multi-window', dump), 'Editor is not actually in split-screen'

    def hide_ime():
        if 'mInputShown=true' in shell('dumpsys', 'input_method'):
            shell('input', 'keyevent', 'KEYCODE_BACK')
            deadline = time.monotonic() + 10
            while 'mInputShown=true' in shell('dumpsys', 'input_method'):
                assert time.monotonic() < deadline, 'IME did not hide before changing split focus'
                time.sleep(.1)

    def type_split_word(label, prefix):
        for description in ('6 MNO', '4 GHI', 'Space'):
            root, xml, history = capture_editor_window(shell, [('content-desc', description)])
            (out / (label + '-' + description.split()[0] + '-capture.json')).write_text(json.dumps(history, indent=2))
            assert root is not None
            node = next(n for n in root.iter('node') if n.get('content-desc') == description)
            box = list(map(int, re.findall(r'-?\d+', node.get('bounds'))))
            shell('input', 'tap', (box[0] + box[2]) // 2, (box[1] + box[3]) // 2)
        root = window(label + '-committed', ready=True)
        value = next(n.get('text') for n in root.iter('node') if n.get('resource-id') == FIXTURE + ':id/editor')
        assert value.startswith(prefix) and len(value) == len(prefix) + 1 and ord(value[-1]) > 127, 'Split input did not commit one Hanzi exactly once'
        return value

    passed = False
    try:
        with record_logcat(adb, out / 'logcat-full.txt'):
            shell('log', '-t', 'TrimeRuntimeAudit', '-p', 'i', marker)
            deadline = time.monotonic() + 15
            while marker not in (out / 'logcat-full.txt').read_text():
                assert time.monotonic() < deadline, 'Log recorder did not reach the test boundary'
                time.sleep(.1)
            launch()
            if not args.split_screen_only:
                checkpoint('cold-editor')
                before = pid()
                shell('input', 'keyevent', 'KEYCODE_HOME')
                time.sleep(.8)
                if args.release:
                    assert shell('cat', f'/proc/{before}/cmdline').split('\0', 1)[0] == PACKAGE, 'Kill target is no longer the tested Trime process'
                    shell('kill', '-9', before)
                else:
                    shell('run-as', PACKAGE, 'kill', '-9', before)
                shell('am', 'start', '-W', '-f', '0x00020000', '-n', FIXTURE + '/.EditorActivity')
                checkpoint('after-background-process-death')
                assert pid() != before, 'Trime process was not replaced'
                shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
                shell('settings', 'put', 'system', 'user_rotation', '1')
                time.sleep(2)
                checkpoint('landscape')
                shell('settings', 'put', 'system', 'user_rotation', '0')
                time.sleep(2)
                checkpoint('portrait')
                shell('am', 'start', '-W', '-a', 'android.settings.SETTINGS')
                time.sleep(.5)
                shell('am', 'start', '-W', '-f', '0x00020000', '-n', FIXTURE + '/.EditorActivity')
                checkpoint('after-switching-applications')
                for mode in ('address', 'email', 'password', 'phone', 'integer', 'decimal'):
                    value = '12.3' if mode == 'decimal' else '12'
                    launch(mode, value)
                    checkpoint(mode, t9=False, expected=value)
                    launch()
                    checkpoint('chat-after-' + mode)
            if args.split_screen:
                shell('settings', 'put', 'system', 'accelerometer_rotation', '0')
                shell('settings', 'put', 'system', 'user_rotation', '0')
                time.sleep(1)
                shell('am', 'start', '-W', '-a', 'android.settings.SETTINGS')
                shell('am', 'start', '-W', '-f', '0x00020000', '-n', FIXTURE + '/.EditorActivity')
                checkpoint('before-split-screen')
                hide_ime()
                dump = shell('dumpsys', 'activity', 'activities')
                task_ids = set(re.findall(r'\* Task\{\S+ #(\d+) [^\n]*A=\d+:' + re.escape(FIXTURE) + r' ', dump))
                assert len(task_ids) == 1, 'Cannot identify the dedicated editor task'
                split_task = int(task_ids.pop())
                (out / 'split-enter.txt').write_text(split_command('moveToSideStage', split_task, 1))
                time.sleep(1)
                check_split('split-bottom')
                hide_ime()
                checkpoint('split-bottom')
                value = type_split_word('split-bottom', 'STABLE')
                checkpoint('split-bottom-typed', expected=value)
                hide_ime()
                (out / 'split-switch.txt').write_text(split_command('switchSplitPosition'))
                time.sleep(1)
                check_split('split-top')
                hide_ime()
                checkpoint('split-top', expected=value)
                value = type_split_word('split-top', value)
                checkpoint('split-top-typed', expected=value)
                (out / 'split-exit.txt').write_text(split_command('exitSplitScreen', split_task))
                split_task = None
                time.sleep(1)
                checkpoint('after-split-screen', expected=value)
            passed = True
    finally:
        if split_task is not None:
            split_command('exitSplitScreen', split_task)
        for key, value in original_rotation.items():
            if value == 'null':
                shell('settings', 'delete', 'system', key)
            else:
                shell('settings', 'put', 'system', key, value)
        (out / 'checkpoints.json').write_text(json.dumps(checkpoints, indent=2) + '\n')
        if (out / 'logcat-full.txt').exists():
            lines = []
            for line in log_after_marker((out / 'logcat-full.txt').read_text(), marker).splitlines():
                match = LOG_LINE.match(line)
                if match and int(match[1]) in pids:
                    lines.append(line)
            app_log = '\n'.join(lines) + '\n'
            (out / 'app-logcat.txt').write_text(app_log)
            audit = audit_log(app_log)
            (out / 'runtime-audit.json').write_text(json.dumps(audit, indent=2) + '\n')
            trace_lines = [line for line in app_log.splitlines()
                           if (match := LOG_LINE.match(line)) and match[4].strip() == 'TrimePerf']
            report = {'assertions_passed': passed, 'pids': sorted(pids), 'checkpoints': len(checkpoints),
                      'release': args.release, 'performance_trace_lines': trace_lines,
                      'passed': passed and audit['capture_has_records'] and not audit['known_project_diagnostics']
                      and (not args.release or not trace_lines),
                      'warning_free': audit['warning_free'], 'known_project_diagnostics': audit['known_project_diagnostics']}
            (out / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
            if passed:
                assert audit['capture_has_records'], 'No application records captured during the test'
                assert not audit['known_project_diagnostics'], 'Project diagnostics found; see retained logs'
                if args.release:
                    assert not trace_lines, 'Daily Release must not emit performance trace records'


if __name__ == '__main__':
    main()
