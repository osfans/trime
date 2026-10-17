#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify assertion-backed release gates cannot run with assertions disabled."""

import os
from pathlib import Path
import subprocess
import sys
import unittest


REPO = Path(__file__).resolve().parents[2]
TRACKED_GATES = (
    REPO / "script/quality/run_api21_saf_gate.py",
    REPO / "script/quality/run_navigation.py",
    REPO / "script/quality/run_editor_lifecycle.py",
    REPO / "script/quality/run_opencc_resource_controls.py",
)
REFUSAL = "refuses optimized Python (-O/-OO)"


class PythonOptimizationGateTest(unittest.TestCase):
    def assert_refuses_optimized_python(self, script, interpreter_args=(), environment=None):
        result = subprocess.run(
            [sys.executable, *interpreter_args, str(script), "--help"],
            cwd=REPO,
            env=environment,
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
        output = result.stdout + result.stderr
        self.assertNotEqual(result.returncode, 0, (script, output))
        self.assertIn(REFUSAL, output, (script, output))

    def test_tracked_gates_reject_dash_o_and_dash_oo(self):
        for script in TRACKED_GATES:
            for flag in ("-O", "-OO"):
                with self.subTest(script=script.name, flag=flag):
                    self.assert_refuses_optimized_python(script, (flag,))

    def test_tracked_gates_reject_pythonoptimize_environment(self):
        environment = os.environ.copy()
        environment["PYTHONOPTIMIZE"] = "1"
        for script in TRACKED_GATES:
            with self.subTest(script=script.name):
                self.assert_refuses_optimized_python(script, environment=environment)

if __name__ == "__main__":
    unittest.main()
