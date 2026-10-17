# SPDX-License-Identifier: GPL-3.0-or-later
"""Multi-window captures from a separate emulator-only instrumentation process."""
import subprocess
import time
import xml.etree.ElementTree as ET


def capture_editor_window(shell, expected=(), attempts=6):
    history = []
    for _ in range(attempts):
        entry = {}
        history.append(entry)
        try:
            entry['output'] = shell('am', 'instrument', '-w', '-r', 'org.x2m7.trime.editorobserver/.Observer')
            if 'INSTRUMENTATION_RESULT: passed=true' not in entry['output']:
                raise ValueError('Observer failed to produce a fresh hierarchy')
            xml = shell('run-as', 'org.x2m7.trime.editorobserver', 'cat', 'cache/windows.xml')
            root = ET.fromstring(xml)
            if root.tag != 'hierarchy' or not list(root.iter('node')):
                raise ValueError('Empty or invalid hierarchy')
            if not all(any(n.get(field) == value for n in root.iter('node')) for field, value in expected):
                entry['xml'] = xml
                raise ValueError('Expected controls are not all visible')
            return root, xml, history
        except (subprocess.SubprocessError, ValueError, ET.ParseError) as error:
            entry['error'] = str(error)
            time.sleep(.25)
    return None, None, history
