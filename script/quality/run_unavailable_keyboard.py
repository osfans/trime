#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Disposable-emulator fault injection through a real external editor and Retry button."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import zipfile

from run_navigation import select_test_ime
from editor_windows import capture_editor_window
from runtime_audit import (
    LOG_LINE,
    audit_log,
    device_audit_lock,
    log_after_marker,
    record_logcat,
)

PACKAGE = 'com.osfans.trime.debug'
FIXTURE = 'com.osfans.trime.editorfixture'
IME = PACKAGE + '/com.osfans.trime.ime.core.TrimeInputMethodService'
EDITOR_ID = FIXTURE + ':id/editor'
SYSTEM_NAV_PACKAGES = frozenset({'com.android.systemui', 'com.android.launcher3'})
SYSTEM_NAV_OVERVIEW = frozenset({'Recents', 'Overview'})
FALLBACK_TEXT_CONTROLS = frozenset({*(str(value) for value in range(10)), '.', '@', '-'})
FALLBACK_DESCRIPTION_CONTROLS = frozenset({
    'Retry deployment',
    'Switch input method',
    'Delete',
    'Space',
    'Send',
})
BOUNDS = re.compile(r'^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def parse_bounds(value):
    match = BOUNDS.fullmatch(value or '')
    require(match is not None, f'Invalid screen bounds: {value!r}')
    result = tuple(map(int, match.groups()))
    require(result[2] > result[0] and result[3] > result[1],
            f'Empty screen bounds: {value!r}')
    return result


def bounds_contain(outer, inner):
    return (outer[0] <= inner[0] and outer[1] <= inner[1]
            and outer[2] >= inner[2] and outer[3] >= inner[3])


def bounds_overlap(first, second):
    return (max(first[0], second[0]) < min(first[2], second[2])
            and max(first[1], second[1]) < min(first[3], second[3]))


def require_stable_editor(root):
    editors = [
        node for node in root.iter('node')
        if node.get('resource-id') == EDITOR_ID
    ]
    require(len(editors) == 1, 'Fresh capture must contain exactly one fixture editor')
    editor = editors[0]
    require(
        editor.get('package') == FIXTURE
        and editor.get('class') == 'android.widget.EditText'
        and editor.get('enabled') == 'true'
        and editor.get('text') == 'STABLE',
        'Fresh capture does not contain the enabled fixture editor with exact STABLE text',
    )
    parse_bounds(editor.get('bounds'))
    return editor


def fallback_control_snapshot(root):
    nodes = [
        node for node in root.iter('node')
        if node.get('package') == PACKAGE
        and node.get('class') in ('android.widget.Button', 'android.widget.ImageButton')
    ]
    require(len(nodes) == 18, 'Fallback must expose exactly 18 key/tool controls')
    signatures = []
    identities = set()
    for node in nodes:
        require(node.get('enabled') == 'true', 'Fallback control is disabled')
        text = node.get('text') or ''
        description = node.get('content-desc') or ''
        if text in FALLBACK_TEXT_CONTROLS and not description:
            selector, value = 'text', text
        elif description in FALLBACK_DESCRIPTION_CONTROLS and not text:
            selector, value = 'content-desc', description
        else:
            raise RuntimeError(f'Unexpected fallback control semantics: {node.attrib!r}')
        identity = (selector, value)
        require(identity not in identities, f'Duplicate fallback control: {identity!r}')
        identities.add(identity)
        signatures.append({
            'selector': selector,
            'value': value,
            'class': node.get('class'),
            'bounds': list(parse_bounds(node.get('bounds'))),
        })
    expected = ({('text', value) for value in FALLBACK_TEXT_CONTROLS}
                | {('content-desc', value) for value in FALLBACK_DESCRIPTION_CONTROLS})
    require(identities == expected,
            f'Fallback controls differ from the fixed limited-input set: {sorted(identities)!r}')
    signatures.sort(key=lambda item: (item['selector'], item['value']))
    return signatures, nodes


def system_navigation_window(root):
    candidates = []
    for window in root.findall('window'):
        if window.get('type') != '3':
            continue
        window_root = window.find('node')
        if window_root is None or window_root.get('package') not in SYSTEM_NAV_PACKAGES:
            continue
        owner = window_root.get('package')
        navigation = [
            node for node in window.iter('node')
            if node.get('content-desc') in {'Back', 'Home', *SYSTEM_NAV_OVERVIEW}
        ]
        if any(node.get('package') != owner for node in navigation):
            continue
        by_description = {}
        for node in navigation:
            by_description.setdefault(node.get('content-desc'), []).append(node)
        overview = sum((by_description.get(value, []) for value in SYSTEM_NAV_OVERVIEW), [])
        if (len(by_description.get('Back', [])) == 1
                and len(by_description.get('Home', [])) == 1
                and len(overview) == 1):
            candidates.append((window_root, navigation))
    require(len(candidates) == 1,
            'Fresh navigation capture must contain exactly one complete platform navigation window')
    window_root, navigation = candidates[0]
    require(window_root.get('enabled') == 'true', 'Platform navigation window is disabled')
    window_bounds = parse_bounds(window_root.get('bounds'))
    require(len(navigation) == 3, 'Platform navigation must contain exactly three controls')
    for node in navigation:
        require(node.get('enabled') == 'true', 'Platform navigation control is disabled')
        require(bounds_contain(window_bounds, parse_bounds(node.get('bounds'))),
                'Platform navigation control escapes its window bounds')
    return window_root, navigation, window_bounds


def process_listing_command(api):
    require(isinstance(api, int) and api > 0, f'Invalid Android API: {api!r}')
    return ('ps', '-A') if api >= 26 else ('ps',)


def parse_target_pids(listing, package=PACKAGE):
    matches = []
    for row in listing.splitlines():
        fields = row.split()
        if not fields or fields[-1] != package:
            continue
        require(len(fields) >= 2 and fields[1].isdigit() and int(fields[1]) > 0,
                f'Malformed target process row: {row!r}')
        matches.append(int(fields[1]))
    require(len(matches) == len(set(matches)), 'Target process listing contains duplicate rows')
    return set(matches)


def parse_display_state(wm_size, window_dump, accelerometer_rotation, user_rotation):
    sizes = re.findall(r'(?:Physical|Override) size:\s*(\d+)x(\d+)', wm_size)
    require(sizes, 'wm size did not expose a physical or override display size')
    rotations = re.findall(r'^\s*mRotation=(?:ROTATION_)?([0-3])(?:\s|$)', window_dump, re.M)
    require(len(rotations) == 1, 'Window manager did not expose exactly one display rotation')
    # ``settings get`` returns the literal string ``null`` when a setting has
    # never been persisted.  Fresh API 21 devices commonly omit
    # ``user_rotation``; the effective orientation still comes from Window
    # Manager above and the capture bracket verifies that it remains stable.
    require(accelerometer_rotation in ('0', '1', 'null'),
            'Invalid accelerometer rotation setting')
    require(user_rotation in ('0', '1', '2', '3', 'null'),
            'Invalid user rotation setting')
    width, height = map(int, sizes[-1])
    require(width > 0 and height > 0, 'Display size is empty')
    return {
        'wm_size': wm_size.strip(),
        'effective_size': [width, height],
        'window_rotation': int(rotations[0]),
        'accelerometer_rotation': accelerometer_rotation,
        'user_rotation': user_rotation,
    }


def validate_capture_interval(before, after, navigation_bounds, display_states):
    require(before == after,
            'Fallback control semantics or bounds changed across the navigation capture')
    require(len(display_states) == 3 and all(state == display_states[0]
                                                for state in display_states[1:]),
            'Display size or rotation changed during fallback/navigation capture')
    width, height = display_states[0]['effective_size']
    screen = (0, 0, width, height)
    require(bounds_contain(screen, navigation_bounds),
            'Platform navigation window escapes the display')
    horizontal_bar = (
        navigation_bounds[0] == 0
        and navigation_bounds[2] == width
        and (navigation_bounds[1] == 0 or navigation_bounds[3] == height)
    )
    vertical_bar = (
        navigation_bounds[1] == 0
        and navigation_bounds[3] == height
        and (navigation_bounds[0] == 0 or navigation_bounds[2] == width)
    )
    require(horizontal_bar or vertical_bar,
            'Platform navigation window does not span a complete display edge')
    for signature in before:
        button_bounds = tuple(signature['bounds'])
        require(bounds_contain(screen, button_bounds), 'Fallback control escapes the display')
        require(not bounds_overlap(button_bounds, navigation_bounds),
                f'Fallback control overlaps the platform navigation window: {signature!r}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with device_audit_lock(args.serial):
        run(args)


def run(args):
    out = args.output
    out.mkdir(parents=True, exist_ok=False)
    adb = [args.adb, '-s', args.serial]

    def shell(*command):
        return subprocess.check_output(adb + ['shell', *map(str, command)], text=True, stderr=subprocess.STDOUT, timeout=120).strip()

    require(shell('getprop', 'ro.kernel.qemu') == '1', 'Disposable emulator only')
    data = '/sdcard/Android/data/' + PACKAGE + '/files'
    require(
        shell('cat', data + '/runtime-audit-dedicated') == '',
        'Requires a marked disposable installation',
    )
    api = int(shell('getprop', 'ro.build.version.sdk'))
    checksum = ('/data/user_de/0/' if api >= 24 else '/data/data/') + PACKAGE + '/checksums.json'
    shared = data + '/shared/default.yaml'
    def read_file(path, private=False):
        command = ['run-as', PACKAGE, 'cat', path] if private else ['cat', path]
        return subprocess.check_output(adb + ['exec-out', *command], timeout=120)

    original_shared = read_file(shared)
    original_checksum = read_file(checksum, private=True)
    require(
        isinstance(json.loads(original_checksum), dict),
        'Readable original resource manifest required',
    )
    suffix = '.unavailable-audit-' + uuid.uuid4().hex
    saved_shared, saved_checksum = shared + suffix, checksum + suffix
    identity = {
        'schema': 1,
        'probe': 'unavailable-keyboard',
        'api': api,
        'fingerprint': shell('getprop', 'ro.build.fingerprint'),
        'source_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'packages': {},
    }
    for package in (PACKAGE, FIXTURE, 'com.osfans.trime.editorobserver'):
        path = shell('pm', 'path', package).removeprefix('package:')
        content = subprocess.check_output(adb + ['exec-out', 'cat', path], timeout=120)
        identity['packages'][package] = hashlib.sha256(content).hexdigest()
        if package == PACKAGE:
            with zipfile.ZipFile(io.BytesIO(content)) as apk:
                require(
                    original_shared == apk.read('assets/shared/default.yaml'),
                    'Fault injection requires the unmodified packaged default config',
                )
    identity['original_resource_hashes'] = {name: hashlib.sha256(value).hexdigest() for name, value in
                                            (('shared/default.yaml', original_shared), ('checksums.json', original_checksum))}
    (out / 'identity.json').write_text(json.dumps(identity, indent=2) + '\n')
    previous_ime = shell('settings', 'get', 'secure', 'default_input_method')
    moved_shared = moved_checksum = False
    passed = False
    observed_pids = set()
    activation_marker = 'TRIME-UNAVAILABLE-' + uuid.uuid4().hex

    def record_target_pids(required=False):
        try:
            result = subprocess.run(
                adb + ['shell', *process_listing_command(api)],
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
            require(result.returncode == 0,
                    f'Could not list target processes: {result.stderr.strip()}')
            current = parse_target_pids(result.stdout)
            require(not required or len(current) == 1,
                    f'Expected exactly one running target process, got {sorted(current)!r}')
            observed_pids.update(current)
        except Exception:
            if required:
                raise

    def current_display_state():
        return parse_display_state(
            shell('wm', 'size'),
            shell('dumpsys', 'window', 'windows'),
            shell('settings', 'get', 'system', 'accelerometer_rotation'),
            shell('settings', 'get', 'system', 'user_rotation'),
        )

    def capture(label, expected):
        root, xml, attempts = capture_editor_window(shell, expected, attempts=12)
        (out / (label + '.xml')).write_text(xml or '')
        (out / (label + '-capture.json')).write_text(json.dumps(attempts, indent=2) + '\n')
        require(root is not None, f'UI capture failed for {label}: {expected!r}')
        with (out / (label + '.png')).open('wb') as image:
            subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=image, check=True, timeout=60)
        return root

    def tap(root, field, value):
        node = next(n for n in root.iter('node') if n.get(field) == value and n.get('enabled') == 'true')
        box = list(map(int, re.findall(r'\d+', node.get('bounds'))))
        shell('input', 'tap', (box[0] + box[2]) // 2, (box[1] + box[3]) // 2)

    try:
        shell('input', 'keyevent', 'KEYCODE_HOME')
        shell('ime', 'set', 'com.android.inputmethod.latin/.LatinIME')
        shell('am', 'force-stop', PACKAGE)
        shell('mv', shared, saved_shared)
        moved_shared = True
        shell('run-as', PACKAGE, 'mv', checksum, saved_checksum)
        moved_checksum = True
        shell('mkdir', shared)
        with record_logcat(adb, out / 'logcat-full.txt'):
            time.sleep(.2)
            shell('log', '-t', 'TrimeRuntimeAudit', activation_marker)
            select_test_ime(shell, IME)
            shell('am', 'start', '-S', '-W', '-n', FIXTURE + '/.EditorActivity', '--es', 'text', 'STABLE')
            fallback_expected = [
                ('resource-id', EDITOR_ID),
                ('text', 'STABLE'),
                ('text', 'Chinese input unavailable. Numbers only.'),
                ('content-desc', 'Retry deployment'),
            ]
            before_root = capture('unavailable', fallback_expected)
            require_stable_editor(before_root)
            before, _ = fallback_control_snapshot(before_root)
            display_states = [current_display_state()]
            record_target_pids(required=True)

            navigation_root = capture('unavailable-navigation', [
                ('resource-id', EDITOR_ID),
                ('text', 'STABLE'),
                ('content-desc', 'Back'),
                ('content-desc', 'Home'),
            ])
            require_stable_editor(navigation_root)
            navigation_window, navigation, navigation_bounds = system_navigation_window(
                navigation_root,
            )
            display_states.append(current_display_state())

            root = capture('unavailable-after-navigation', fallback_expected)
            require_stable_editor(root)
            after, buttons = fallback_control_snapshot(root)
            display_states.append(current_display_state())
            validate_capture_interval(before, after, navigation_bounds, display_states)
            (out / 'navigation-bounds.json').write_text(json.dumps({
                'buttons': [n.attrib for n in buttons],
                'navigation': [n.attrib for n in navigation],
                'navigation_window': navigation_window.attrib,
                'navigation_window_bounds': list(navigation_bounds),
                'fallback_before': before,
                'fallback_after': after,
                'display_states': display_states,
                'capture_sequence': [
                    'unavailable',
                    'unavailable-navigation',
                    'unavailable-after-navigation',
                ],
            }, indent=2) + '\n')
            tap(root, 'text', '2')
            root = capture('limited-input', [
                ('text', 'STABLE2'),
                ('content-desc', 'Delete'),
            ])
            fallback_control_snapshot(root)
            tap(root, 'content-desc', 'Delete')
            capture('limited-delete', [('text', 'STABLE')])
            shell('rmdir', shared)
            shell('mv', saved_shared, shared)
            moved_shared = False
            shell('run-as', PACKAGE, 'mv', saved_checksum, checksum)
            moved_checksum = False
            root = capture('retry-ready', [('content-desc', 'Retry deployment')])
            tap(root, 'content-desc', 'Retry deployment')
            deadline = time.monotonic() + 180
            while True:
                root, xml, _ = capture_editor_window(shell)
                descriptions = {n.get('content-desc') for n in root.iter('node')} if root is not None else set()
                if '2 ABC' in descriptions and '9 WXYZ' in descriptions:
                    break
                require(
                    time.monotonic() < deadline,
                    'Retry never restored a nine-key layout',
                )
                time.sleep(.5)
            root = capture('recovered', [('content-desc', '2 ABC'), ('text', 'STABLE')])
            record_target_pids(required=True)
            tap(root, 'content-desc', '6 MNO')
            tap(root, 'content-desc', '4 GHI')
            capture('recovered-candidates', [('text', 'ni')])
            passed = True
    finally:
        # This best-effort observation must never mask the functional failure
        # or prevent the byte-for-byte resource restoration below.
        record_target_pids(required=False)
        if moved_shared or moved_checksum:
            shell('input', 'keyevent', 'KEYCODE_HOME')
            shell('ime', 'set', 'com.android.inputmethod.latin/.LatinIME')
            shell('am', 'force-stop', PACKAGE)
        if moved_shared:
            if subprocess.run(adb + ['shell', 'test', '-d', shared]).returncode == 0:
                shell('rmdir', shared)
            shell('mv', saved_shared, shared)
        if moved_checksum:
            shell('run-as', PACKAGE, 'mv', saved_checksum, checksum)
        select_test_ime(shell, previous_ime)
        restored = read_file(shared) == original_shared and read_file(checksum, private=True) == original_checksum
        logcat_path = out / 'logcat-full.txt'
        logcat = (logcat_path.read_text(encoding='utf-8', errors='replace')
                  if logcat_path.is_file() else '')
        try:
            scoped_log = log_after_marker(logcat, activation_marker)
            marker_valid = True
        except ValueError:
            scoped_log = ''
            marker_valid = False
        app_lines = [
            line
            for line in scoped_log.splitlines()
            if (match := LOG_LINE.match(line)) and int(match[1]) in observed_pids
        ]
        app_log = '\n'.join(app_lines) + ('\n' if app_lines else '')
        (out / 'app-logcat.txt').write_text(app_log, encoding='utf-8')
        audit = audit_log(app_log)
        (out / 'runtime-audit.json').write_text(json.dumps(audit, indent=2) + '\n')
        evidence_complete = marker_valid and bool(observed_pids) and audit['capture_has_records']
        functional_passed = passed and restored and evidence_complete
        identity['process_pids'] = sorted(observed_pids)
        identity['activation_marker'] = activation_marker
        identity['logcat_sha256'] = hashlib.sha256(
            logcat_path.read_bytes() if logcat_path.is_file() else b'',
        ).hexdigest()
        identity['app_logcat_sha256'] = hashlib.sha256(
            (out / 'app-logcat.txt').read_bytes(),
        ).hexdigest()
        (out / 'identity.json').write_text(json.dumps(identity, indent=2) + '\n')
        report = {
            'schema': 1,
            'probe': 'unavailable-keyboard',
            'passed': functional_passed,
            'functional_passed': functional_passed,
            'assertions_passed': passed,
            'injects_faults': True,
            'requires_log_review': True,
            'resources_restored': restored,
            'evidence_complete': evidence_complete,
            'process_pids': sorted(observed_pids),
            'activation_marker': activation_marker,
            'diagnostic_scope': 'after_activation_marker',
            'warning_free': audit['warning_free'],
            'warning_count': len(audit['warnings']),
            'full_probe_diagnostic_count': len(audit['known_project_diagnostics']),
            'runtime_audit_sha256': hashlib.sha256((out / 'runtime-audit.json').read_bytes()).hexdigest(),
        }
        (out / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        require(restored, 'Fault-injected resource bytes were not restored')
        require(evidence_complete, 'Target-process runtime audit is incomplete')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
