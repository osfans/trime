# SPDX-License-Identifier: GPL-3.0-or-later
"""Data integrity controls for the real-engine learning acceptance runner."""
import tempfile
from pathlib import Path
import unittest

from run import export_records, learned_counts, write_fixture


class LearningReceiptIntegrityTest(unittest.TestCase):
    def export(self, contents):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "export.txt"
            path.write_text(contents, encoding="utf-8")
            return export_records(path)

    def test_actual_rime_table_format_normalizes_only_code_whitespace(self):
        records = self.export("# Rime user dictionary export\n茶雲翎\tcha yun ling \t3\n墨羽燈\tmo yu deng\t4\n")
        self.assertEqual(learned_counts(records), {"茶雲翎": 3, "墨羽燈": 4})

    def test_different_spelling_does_not_prove_learning(self):
        records = self.export("茶雲翎\tcha yun lin\t3\n墨羽燈\tmo yu deng\t4\n")
        with self.assertRaisesRegex(ValueError, "persistently learned"):
            learned_counts(records)

    def test_zero_frequency_is_not_a_positive_learned_phrase(self):
        records = self.export("茶雲翎\tcha yun ling\t0\n墨羽燈\tmo yu deng\t4\n")
        with self.assertRaisesRegex(ValueError, "persistently learned"):
            learned_counts(records)

    def test_duplicate_records_cannot_inflate_accepted_counts(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.export("茶雲翎\tcha yun ling\t3\n茶雲翎\tcha yun ling \t9\n")

    def test_malformed_exports_do_not_become_empty_success(self):
        for contents in ("茶雲翎\tcha yun ling\n", "茶雲翎\tcha'yun'ling\t3\n", "茶雲翎\tcha yun ling\t-1\n"):
            with self.subTest(contents=contents), self.assertRaises(ValueError):
                self.export(contents)

    def test_fixture_has_no_golden_phrase_to_confuse_stock_and_learning(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            name = "__t10_" + "a" * 32
            write_fixture(root, name, 1)
            dictionary = (root / f"{name}.dict.yaml").read_text(encoding="utf-8")
            self.assertNotIn("茶雲翎", dictionary)
            self.assertNotIn("墨羽燈", dictionary)
            for suffix in ("full", "t9"):
                schema = (root / f"{name}_{suffix}.schema.yaml").read_text(encoding="utf-8")
                self.assertIn(f"dictionary: {name}", schema)
                self.assertNotIn("user_dict:", schema.replace("enable_user_dict:", ""))

    def test_unowned_dictionary_name_is_rejected_before_any_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("luna_pinyin", "../../personal", "__t10_unowned", "__t10_" + "a" * 33):
                with self.subTest(name=name), self.assertRaisesRegex(ValueError, "Unowned"):
                    write_fixture(root, name, 1)
            self.assertEqual(list(root.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
