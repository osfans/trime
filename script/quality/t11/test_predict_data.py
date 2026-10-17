#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Bounded model/source corruption regressions; native lookup is checked separately."""

import json
from pathlib import Path
import struct
import unittest

import build_predict_db as model


class PredictionSourceTest(unittest.TestCase):
    def test_original_examples_cover_distinct_limit_and_iteration_cases(self):
        data = model.read_source(model.SOURCE.read_text(encoding="utf-8"))
        self.assertEqual(data["你好"][0][0], "世界")
        self.assertEqual(len({text for text, _ in data["你好"]}), 10)
        self.assertEqual(data["世界"][0][0], "和平")
        self.assertEqual(data["世界"][1][0], "你好")
        self.assertNotIn("$", data)
        self.assertIn("今天", data)
        self.assertIn("謝謝", data)
        self.assertIn("谢谢", data)

    def test_rejects_incomplete_or_non_han_corpus(self):
        for source in ("", "你好\t世界", "你好 世界 10", "hello\t世界\t10", "你好\t验证码123\t10"):
            with self.subTest(source=source), self.assertRaises(ValueError):
                model.read_source(source)

    def test_rejects_duplicates_nonfinite_and_unordered_weights(self):
        for source in ("你好\t世界\t10\n你好\t世界\t9", "你好\t世界\tnan",
                       "你好\t世界\t0", "你好\t世界\t10\n你好\t朋友\t10",
                       "你好\t世界\t10\n你好\t朋友\t11"):
            with self.subTest(source=source), self.assertRaises(ValueError):
                model.read_source(source)

    def test_candidate_budget_rejects_oversized_source(self):
        source = "".join(f"你好\t字{chr(0x4e10+i)}\t{100-i}\n" for i in range(model.MAX_PER_QUERY + 1))
        with self.assertRaises(ValueError):
            model.read_source(source)


class PredictionImageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = model.read_source(model.SOURCE.read_text(encoding="utf-8"))
        cls.image = model.ASSET.read_bytes()

    def test_shipped_database_is_bounded_and_matches_original_source(self):
        self.assertLessEqual(len(self.image), 64 * 1024)
        self.assertEqual(json.loads(model.MANIFEST.read_text(encoding="utf-8")), model.metadata(self.image, self.data))

    def test_rejects_truncated_and_oversized_images(self):
        for image in (b"", self.image[:51], self.image[:-1], self.image + b"\0" * (64 * 1024)):
            with self.subTest(length=len(image)), self.assertRaises(ValueError):
                model.inspect_image(image, self.data)

    def test_rejects_wrong_format_unbounded_offsets_and_unit_counts(self):
        mutations = [(0, b"X"), (36, struct.pack("<i", -100)),
                     (36, struct.pack("<i", 0x7fffffff)), (40, struct.pack("<I", 0xffffffff)),
                     (44, struct.pack("<i", 0x7fffffff)), (48, struct.pack("<I", 0xffffffff))]
        for start, replacement in mutations:
            image = bytearray(self.image)
            image[start:start + len(replacement)] = replacement
            with self.subTest(start=start), self.assertRaises(ValueError):
                model.inspect_image(bytes(image), self.data)

    def test_rejects_candidate_array_count_and_weight_corruption(self):
        # First array is at the 52-byte metadata boundary, independently of trie order.
        for start, replacement in ((52, struct.pack("<I", 0xffffffff)),
                                   (60, struct.pack("<f", float("nan"))),
                                   (56, struct.pack("<I", 0xffffffff))):
            image = bytearray(self.image)
            image[start:start + len(replacement)] = replacement
            with self.subTest(start=start), self.assertRaises(ValueError):
                model.inspect_image(bytes(image), self.data)

    def test_model_manifest_detects_same_length_tail_corruption(self):
        image = bytearray(self.image)
        image[-1] ^= 1
        descriptor = json.loads(model.MANIFEST.read_text(encoding="utf-8"))
        self.assertNotEqual(model.digest(image), descriptor["sha256"])


if __name__ == "__main__":
    unittest.main()
