# SPDX-License-Identifier: GPL-3.0-or-later
import unittest
from unittest.mock import Mock, patch

from editor_windows import capture_editor_window


class EditorWindowsTest(unittest.TestCase):
    def capture(self, response, xml, expected):
        shell = Mock(side_effect=[response, xml])
        with patch('editor_windows.time.sleep'):
            return capture_editor_window(shell, expected, attempts=1)

    def test_requires_every_control_not_just_editor_window(self):
        root, _, history = self.capture('INSTRUMENTATION_RESULT: passed=true',
            '<hierarchy><node text="STABLE" /></hierarchy>', [('text', 'STABLE'), ('content-desc', '2 ABC')])
        self.assertIsNone(root)
        self.assertIn('not all visible', history[0]['error'])

    def test_accepts_controls_in_separate_windows(self):
        root, _, _ = self.capture('INSTRUMENTATION_RESULT: passed=true',
            '<hierarchy><window><node text="STABLE" /></window><window><node content-desc="2 ABC" /></window></hierarchy>',
            [('text', 'STABLE'), ('content-desc', '2 ABC')])
        self.assertIsNotNone(root)

    def test_does_not_read_stale_file_when_observer_failed(self):
        shell = Mock(return_value='INSTRUMENTATION_RESULT: shortMsg=Process crashed.')
        with patch('editor_windows.time.sleep'):
            root, _, _ = capture_editor_window(shell, attempts=1)
        self.assertIsNone(root)
        self.assertEqual(1, shell.call_count)

    def test_invalid_and_empty_hierarchies_fail(self):
        for xml in ('broken', '<hierarchy/>', '<other><node/></other>'):
            root, _, _ = self.capture('INSTRUMENTATION_RESULT: passed=true', xml, [])
            self.assertIsNone(root)


if __name__ == '__main__':
    unittest.main()
