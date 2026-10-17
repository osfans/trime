# SPDX-License-Identifier: GPL-3.0-or-later
import unittest

from run_runtime import PROBES, api21_editor_bound, api21_editor_focused, instrumentation_options


READY = """Current Input Method Manager state:
  mCurMethodId=com.android.inputmethod.latin/.LatinIME
  mCurClient=ClientState{abcdef uid 10053 pid 3123} mCurSeq=5
  mInputShown=true
Input method service state for com.android.inputmethod.latin.LatinIME@30aa:
  mWindowVisible=true mWindowWasVisible=true
  mInputStarted=true mInputViewStarted=true mCandidatesViewStarted=false
  mInputEditorInfo:
    inputType=0x1 imeOptions=0x4 privateImeOptions=null
    packageName=com.osfans.trime.debug fieldId=1 fieldName=null
"""


class RuntimeBindingTest(unittest.TestCase):
    def test_editor_and_mixed_input_require_their_own_host(self):
        editor = "  mCurrentFocus=Window{356a474e u0 com.osfans.trime.debug/com.osfans.trime.ui.main.EditorProbeActivity}\n"
        mixed = editor.replace("EditorProbeActivity", "ClipEditActivity")
        self.assertTrue(api21_editor_focused(editor, "editors"))
        self.assertTrue(api21_editor_focused(mixed, "t09"))
        self.assertFalse(api21_editor_focused(mixed, "editors"))
        self.assertFalse(api21_editor_focused(editor, "t09"))

    def test_wrong_package_user_or_component_suffix_cannot_bind(self):
        editor = "mCurrentFocus=Window{356a474e u0 com.osfans.trime.debug/com.osfans.trime.ui.main.EditorProbeActivity}\n"
        for wrong in (editor.replace("u0", "u10"),
                      editor.replace("com.osfans.trime.debug/", "com.example/"),
                      editor.replace("EditorProbeActivity}", "EditorProbeActivityOther}")):
            with self.subTest(window=wrong):
                self.assertFalse(api21_editor_focused(wrong))

    def test_missing_duplicate_null_or_unsupported_focus_cannot_bind(self):
        editor = "mCurrentFocus=Window{356a474e u0 com.osfans.trime.debug/com.osfans.trime.ui.main.EditorProbeActivity}\n"
        for wrong in ("", "mCurrentFocus=null\n", editor + editor, editor + "mCurrentFocus=null\n"):
            with self.subTest(window=wrong):
                self.assertFalse(api21_editor_focused(wrong))
        self.assertFalse(api21_editor_focused(editor, "privacy"))

    def test_privacy_probe_is_exposed_and_maps_to_its_runner_argument(self):
        self.assertIn("privacy", PROBES)
        self.assertEqual(["-e", "privacy", "true"], instrumentation_options("privacy"))

    def test_existing_engine_and_saf_option_mapping_is_preserved(self):
        self.assertEqual([], instrumentation_options("engine"))
        self.assertEqual(
            ["-e", "saf", "true", "-e", "safTree", "content://tree", "-e", "safRevoke", "true"],
            instrumentation_options("saf", "content://tree", True),
        )

    def test_ready_editor(self):
        self.assertTrue(api21_editor_bound(READY, 3123))

    def test_api21_adds_navigate_next_to_send_for_laid_out_editor(self):
        # Captured on API 21 Build12: the editor is served and LatinIME shown,
        # but TextView adds this flag when the layout has another focus target.
        self.assertTrue(api21_editor_bound(READY.replace("imeOptions=0x4", "imeOptions=0x8000004"), 3123))

    def test_other_actions_flags_and_input_types_are_not_ready(self):
        for options in ("0x8000000", "0x8000005", "0x10000004", "0x18000004", "0x80000004"):
            with self.subTest(options=options):
                self.assertFalse(api21_editor_bound(READY.replace("imeOptions=0x4", "imeOptions=" + options), 3123))
        self.assertFalse(api21_editor_bound(READY.replace("inputType=0x1", "inputType=0x21"), 3123))

    def test_missing_malformed_or_duplicate_editor_attributes_are_not_ready(self):
        for attributes in ("", "inputType=0x1 imeOptions=0x4badword", "inputType=0x1 imeOptions=4",
                           "inputType=0x1 imeOptions=0x4\n    inputType=0x1 imeOptions=0x8000004"):
            with self.subTest(attributes=attributes):
                self.assertFalse(api21_editor_bound(READY.replace("inputType=0x1 imeOptions=0x4", attributes), 3123))

    def test_previous_client_is_not_ready(self):
        self.assertFalse(api21_editor_bound(READY, 2173))
        self.assertFalse(api21_editor_bound(READY.replace("pid 3123}", "pid 31230}"), 3123))

    def test_launcher_or_initial_activity_field_is_not_ready(self):
        self.assertFalse(api21_editor_bound(READY.replace("com.osfans.trime.debug field", "com.android.launcher field"), 3123))
        self.assertFalse(api21_editor_bound(READY.replace("imeOptions=0x4", "imeOptions=0x0"), 3123))

    def test_window_focus_is_not_sufficient(self):
        for condition in ("mInputShown=true", "mWindowVisible=true", "mInputStarted=true", "mInputViewStarted=true"):
            with self.subTest(condition=condition):
                self.assertFalse(api21_editor_bound(READY.replace(condition, condition.replace("true", "false")), 3123))

    def test_wrong_or_missing_service_is_not_ready(self):
        self.assertFalse(api21_editor_bound(READY.replace("com.android.inputmethod.latin/.LatinIME", "com.osfans.trime.debug/.IME"), 3123))
        self.assertFalse(api21_editor_bound("", 3123))
        self.assertFalse(api21_editor_bound(READY + READY, 3123))


if __name__ == "__main__":
    unittest.main()
