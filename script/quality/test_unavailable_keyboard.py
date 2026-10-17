# SPDX-License-Identifier: GPL-3.0-or-later
import copy
import unittest
import xml.etree.ElementTree as ET

from run_unavailable_keyboard import (
    EDITOR_ID,
    FALLBACK_DESCRIPTION_CONTROLS,
    FALLBACK_TEXT_CONTROLS,
    FIXTURE,
    PACKAGE,
    fallback_control_snapshot,
    parse_bounds,
    parse_display_state,
    parse_target_pids,
    process_listing_command,
    require_stable_editor,
    system_navigation_window,
    validate_capture_interval,
)


def add_editor(root, text='STABLE'):
    window = ET.SubElement(root, 'window', type='1')
    return ET.SubElement(window, 'node', {
        'text': text,
        'resource-id': EDITOR_ID,
        'content-desc': '',
        'package': FIXTURE,
        'class': 'android.widget.EditText',
        'enabled': 'true',
        'bounds': '[0,75][1080,219]',
    })


def fallback_root():
    root = ET.Element('hierarchy')
    add_editor(root)
    window = ET.SubElement(root, 'window', type='2')
    container = ET.SubElement(window, 'node', {
        'package': PACKAGE,
        'class': 'android.widget.FrameLayout',
        'enabled': 'true',
        'bounds': '[0,300][1080,900]',
    })
    controls = [
        ('text', value, 'android.widget.Button')
        for value in sorted(FALLBACK_TEXT_CONTROLS)
    ] + [
        ('content-desc', value, 'android.widget.ImageButton')
        for value in sorted(FALLBACK_DESCRIPTION_CONTROLS)
    ]
    for index, (selector, value, class_name) in enumerate(controls):
        left = (index % 4) * 200
        top = 300 + (index // 4) * 100
        attributes = {
            'text': '',
            'content-desc': '',
            'package': PACKAGE,
            'class': class_name,
            'enabled': 'true',
            'bounds': f'[{left},{top}][{left + 180},{top + 80}]',
        }
        attributes[selector] = value
        ET.SubElement(container, 'node', attributes)
    return root


def navigation_root(package='com.android.systemui', overview='Overview'):
    root = ET.Element('hierarchy')
    add_editor(root)
    window = ET.SubElement(root, 'window', type='3')
    container = ET.SubElement(window, 'node', {
        'package': package,
        'class': 'android.widget.LinearLayout',
        'enabled': 'true',
        'bounds': '[0,900][1080,1000]',
    })
    for index, description in enumerate(('Back', 'Home', overview)):
        ET.SubElement(container, 'node', {
            'package': package,
            'class': 'android.widget.ImageView',
            'content-desc': description,
            'enabled': 'true',
            'bounds': f'[{index * 300 + 50},910][{index * 300 + 250},990]',
        })
    return root


def display_state(rotation=0, size='1080x1000'):
    return parse_display_state(
        f'Physical size: {size}',
        f'WINDOW MANAGER\n  mRotation={rotation}\n',
        '0',
        str(rotation),
    )


class ProcessIdentityTest(unittest.TestCase):
    def test_uses_legacy_ps_only_before_api26(self):
        self.assertEqual(('ps',), process_listing_command(21))
        self.assertEqual(('ps',), process_listing_command(25))
        self.assertEqual(('ps', '-A'), process_listing_command(26))
        self.assertEqual(('ps', '-A'), process_listing_command(35))

    def test_exact_last_column_selects_api21_and_api35_process_rows(self):
        api21 = (
            'USER PID PPID VSIZE RSS WCHAN PC NAME\n'
            'u0_a53 11213 1495 123 45 ffffffff 0 com.osfans.trime.debug\n'
            'u0_a53 11214 1495 123 45 ffffffff 0 com.osfans.trime.debug:worker\n'
            'u0_a54 11215 1495 123 45 ffffffff 0 com.osfans.trime.debugger\n'
        )
        api35 = (
            'USER PID PPID VSZ RSS WCHAN ADDR S NAME\n'
            'u0_a53 3123 1000 1 1 0 0 S com.osfans.trime.debug\n'
        )
        self.assertEqual({11213}, parse_target_pids(api21))
        self.assertEqual({3123}, parse_target_pids(api35))

    def test_rejects_malformed_or_duplicate_exact_target_rows(self):
        with self.assertRaisesRegex(RuntimeError, 'Malformed target process row'):
            parse_target_pids('u0_a53 not-a-pid com.osfans.trime.debug\n')
        row = 'u0_a53 11213 1495 com.osfans.trime.debug\n'
        with self.assertRaisesRegex(RuntimeError, 'duplicate'):
            parse_target_pids(row + row)


class CaptureStructureTest(unittest.TestCase):
    def test_requires_exact_enabled_stable_fixture_editor(self):
        root = fallback_root()
        editor = require_stable_editor(root)
        self.assertEqual('STABLE', editor.get('text'))
        for attribute, value in (
                ('text', 'STABLE2'), ('enabled', 'false'), ('package', 'spoof')):
            broken = fallback_root()
            editor = next(node for node in broken.iter('node')
                          if node.get('resource-id') == EDITOR_ID)
            editor.set(attribute, value)
            with self.assertRaisesRegex(RuntimeError, 'exact STABLE'):
                require_stable_editor(broken)

    def test_fallback_has_fixed_unique_semantics_and_sorted_bounds(self):
        signatures, nodes = fallback_control_snapshot(fallback_root())
        self.assertEqual(18, len(nodes))
        self.assertEqual(18, len(signatures))
        order = [(item['selector'], item['value']) for item in signatures]
        self.assertEqual(sorted(order), order)
        self.assertEqual(
            {('text', value) for value in FALLBACK_TEXT_CONTROLS}
            | {('content-desc', value) for value in FALLBACK_DESCRIPTION_CONTROLS},
            set(order),
        )

    def test_fallback_rejects_missing_duplicate_disabled_and_empty_controls(self):
        missing = fallback_root()
        container = next(node for node in missing.iter('node')
                         if node.get('class') == 'android.widget.FrameLayout')
        container.remove(list(container)[-1])
        with self.assertRaisesRegex(RuntimeError, 'exactly 18'):
            fallback_control_snapshot(missing)

        duplicate = fallback_root()
        buttons = [node for node in duplicate.iter('node')
                   if node.get('package') == PACKAGE
                   and node.get('class') == 'android.widget.Button']
        buttons[-1].set('text', buttons[0].get('text'))
        with self.assertRaisesRegex(RuntimeError, 'Duplicate'):
            fallback_control_snapshot(duplicate)

        disabled = fallback_root()
        next(node for node in disabled.iter('node')
             if node.get('class') == 'android.widget.Button').set('enabled', 'false')
        with self.assertRaisesRegex(RuntimeError, 'disabled'):
            fallback_control_snapshot(disabled)

        empty = fallback_root()
        next(node for node in empty.iter('node')
             if node.get('class') == 'android.widget.Button').set('bounds', '[0,0][0,80]')
        with self.assertRaisesRegex(RuntimeError, 'Empty screen bounds'):
            fallback_control_snapshot(empty)

    def test_navigation_accepts_known_owners_and_requires_three_unique_controls(self):
        for package, overview in (
                ('com.android.systemui', 'Overview'),
                ('com.android.launcher3', 'Recents')):
            window, nodes, bounds = system_navigation_window(
                navigation_root(package, overview),
            )
            self.assertEqual(package, window.get('package'))
            self.assertEqual(3, len(nodes))
            self.assertEqual((0, 900, 1080, 1000), bounds)

    def test_navigation_rejects_spoof_mixed_duplicate_disabled_and_bad_bounds(self):
        spoof = navigation_root('third.party', 'Overview')
        with self.assertRaisesRegex(RuntimeError, 'exactly one complete'):
            system_navigation_window(spoof)

        mixed = navigation_root()
        home = next(node for node in mixed.iter('node')
                    if node.get('content-desc') == 'Home')
        home.set('package', 'com.android.launcher3')
        with self.assertRaisesRegex(RuntimeError, 'exactly one complete'):
            system_navigation_window(mixed)

        spoof_duplicate = navigation_root()
        container = next(window.find('node') for window in spoof_duplicate.findall('window')
                         if window.get('type') == '3')
        ET.SubElement(container, 'node', {
            'package': 'com.android.launcher3',
            'class': 'android.widget.ImageView',
            'content-desc': 'Home',
            'enabled': 'true',
            'bounds': '[10,910][40,990]',
        })
        with self.assertRaisesRegex(RuntimeError, 'exactly one complete'):
            system_navigation_window(spoof_duplicate)

        duplicate = navigation_root()
        container = next(window.find('node') for window in duplicate.findall('window')
                         if window.get('type') == '3')
        ET.SubElement(container, 'node', {
            'package': 'com.android.systemui',
            'class': 'android.widget.ImageView',
            'content-desc': 'Back',
            'enabled': 'true',
            'bounds': '[10,910][40,990]',
        })
        with self.assertRaisesRegex(RuntimeError, 'exactly one complete'):
            system_navigation_window(duplicate)

        disabled = navigation_root()
        next(node for node in disabled.iter('node')
             if node.get('content-desc') == 'Back').set('enabled', 'false')
        with self.assertRaisesRegex(RuntimeError, 'disabled'):
            system_navigation_window(disabled)

        outside = navigation_root()
        next(node for node in outside.iter('node')
             if node.get('content-desc') == 'Back').set('bounds', '[50,890][250,990]')
        with self.assertRaisesRegex(RuntimeError, 'escapes'):
            system_navigation_window(outside)


class CaptureIntervalTest(unittest.TestCase):
    def setUp(self):
        self.before, _ = fallback_control_snapshot(fallback_root())
        self.navigation_bounds = (0, 900, 1080, 1000)
        self.states = [display_state(), display_state(), display_state()]

    def test_stable_bracket_and_full_navigation_window_pass(self):
        validate_capture_interval(
            self.before,
            copy.deepcopy(self.before),
            self.navigation_bounds,
            self.states,
        )

    def test_rejects_changed_controls_size_rotation_overlap_and_floating_nav(self):
        changed = copy.deepcopy(self.before)
        changed[0]['bounds'][0] += 1
        with self.assertRaisesRegex(RuntimeError, 'semantics or bounds changed'):
            validate_capture_interval(
                self.before, changed, self.navigation_bounds, self.states,
            )

        changed_states = copy.deepcopy(self.states)
        changed_states[-1]['window_rotation'] = 1
        with self.assertRaisesRegex(RuntimeError, 'size or rotation changed'):
            validate_capture_interval(
                self.before, self.before, self.navigation_bounds, changed_states,
            )

        overlap = copy.deepcopy(self.before)
        overlap[0]['bounds'] = [0, 950, 100, 990]
        with self.assertRaisesRegex(RuntimeError, 'overlaps'):
            validate_capture_interval(
                overlap, overlap, self.navigation_bounds, self.states,
            )

        with self.assertRaisesRegex(RuntimeError, 'complete display edge'):
            validate_capture_interval(
                self.before, self.before, (10, 800, 1070, 850), self.states,
            )

        with self.assertRaisesRegex(RuntimeError, 'complete display edge'):
            validate_capture_interval(
                self.before, self.before, (0, 800, 1070, 850), self.states,
            )

    def test_display_parser_uses_effective_size_and_exact_rotation(self):
        state = parse_display_state(
            'Physical size: 720x1600\nOverride size: 1080x1920',
            'header\n  mRotation=ROTATION_3\n',
            '0',
            '3',
        )
        self.assertEqual([1080, 1920], state['effective_size'])
        self.assertEqual(3, state['window_rotation'])
        absent_user = parse_display_state(
            'Physical size: 1080x1920',
            'header\n  mRotation=0\n',
            '1',
            'null',
        )
        self.assertEqual('1', absent_user['accelerometer_rotation'])
        self.assertEqual('null', absent_user['user_rotation'])
        absent_accelerometer = parse_display_state(
            'Physical size: 1080x1920',
            'header\n  mRotation=0\n',
            'null',
            '0',
        )
        self.assertEqual('null', absent_accelerometer['accelerometer_rotation'])
        self.assertEqual('0', absent_accelerometer['user_rotation'])
        for size, dump in (
                ('unknown', '  mRotation=0\n'),
                ('Physical size: 1x1', 'no rotation'),
                ('Physical size: 1x1', '  mRotation=0\n  mRotation=1\n')):
            with self.assertRaises(RuntimeError):
                parse_display_state(size, dump, '0', '0')
        for accelerometer, user in (
                ('', '0'), ('2', '0'), ('0', ''), ('0', '4'), ('0', 'garbage')):
            with self.assertRaises(RuntimeError):
                parse_display_state(
                    'Physical size: 1080x1920',
                    '  mRotation=0\n',
                    accelerometer,
                    user,
                )

    def test_bounds_parser_rejects_malformed_and_empty_values(self):
        self.assertEqual((0, 1, 2, 3), parse_bounds('[0,1][2,3]'))
        for value in ('', '[0,0,1,1]', '[0,0][0,1]'):
            with self.assertRaises(RuntimeError):
                parse_bounds(value)


if __name__ == '__main__':
    unittest.main()
