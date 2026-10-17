# SPDX-License-Identifier: GPL-3.0-or-later
import copy
from pathlib import Path
import tempfile
import unittest
import hashlib
import json
import zipfile

import audit
import layers
import merge
import run
import summarize


class CorpusTests(unittest.TestCase):
    def test_fixed_balanced_corpus(self):
        corpus = run.b.read_json(run.HERE / "corpus.json")
        run.validate(corpus)
        self.assertEqual(len(corpus["cases"]), 64)
        for split in ("dev", "holdout"):
            self.assertEqual(sum(c[1] == split for c in corpus["cases"]), 32)
        self.assertEqual(len({c[2] for c in corpus["cases"]}), 8)

    def test_reject_duplicate_and_invalid_pinyin(self):
        source = run.b.read_json(run.HERE / "corpus.json")
        for mutate in (lambda c: c["cases"].append(c["cases"][0]),
                       lambda c: c["cases"][0].__setitem__(4, "64; rm")):
            corpus = copy.deepcopy(source)
            mutate(corpus)
            with self.assertRaises(ValueError):
                run.validate(corpus)

    def test_partial_candidates_are_not_full_matches(self):
        cases = {"a": ["a", "dev", "chat", "sentence", "ni hao", "你好", "你好"]}
        rows = [{"id": "a", "simplified": True,
                 "candidates": [{"text": "你好", "full": False},
                                {"text": "你好", "full": True}],
                 "cost": {"complete": True, "steps": [{"index": 1}]}}]
        result = run.metrics(rows, cases, "dev", True)
        self.assertEqual(result["top1"], 0)
        self.assertEqual(result["top3"], 100)
        self.assertEqual(result["sentence_exact"], 0)
        self.assertEqual(result["extra_selections_per_100_resolved_chars"], 50)

    def test_unresolved_is_not_zero_cost(self):
        cases = {"a": ["a", "holdout", "name", "phrase", "li na", "李娜", "李娜"]}
        rows = [{"id": "a", "simplified": False, "candidates": [],
                 "cost": {"complete": False, "extra_selections": -1}}]
        result = run.metrics(rows, cases, "holdout", False)
        self.assertEqual(result["selection_unresolved"], 1)
        self.assertIsNone(result["extra_selections_per_100_resolved_chars"])
        self.assertIsNone(result["sentence_exact"])

    def test_modes_and_splits_never_mixed(self):
        self.assertEqual(run.metrics([], {}, "holdout", True)["n"], 0)

    def test_duplicate_records_cannot_replace_missing_cases(self):
        corpus = {"cases": [["a"], ["b"]]}
        rows = [{"id": case, "simplified": mode} for case in ("a", "b") for mode in (False, True)]
        run.validate_records(rows, corpus)
        rows[-1] = rows[0]
        with self.assertRaises(ValueError):
            run.validate_records(rows, corpus)

    def test_intermediate_nonfirst_selection_is_not_double_counted(self):
        self.assertEqual(run.extra_selections({"complete": True, "steps": [{"index": 4}, {"index": 0}]}), 1)
        self.assertEqual(run.extra_selections({"complete": True, "steps": [{"index": 4}, {"index": 2}]}), 2)
        self.assertEqual(run.extra_selections({"complete": True, "steps": [{"index": 0}]}), 0)


class DictionaryTests(unittest.TestCase):
    def test_parse_and_bounded_selection(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "test.dict.yaml"
            path.write_text("# header\n---\nname: test\n...\n"
                            "你好\tni hao\t100\n谢谢\txie xie\t500\n"
                            "错误\twrong\t999\n#禁用\tjin yong\t999\n"
                            "自动注音\t100\n无频率\twu pin lv\n", encoding="utf-8")
            self.assertEqual(len(list(layers.entries(path))), 2)
            self.assertEqual(layers.selected(path, 1, 2, 6), [("谢谢", "xie xie", 500)])

    def test_same_input_generates_stable_tie_order(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "test.dict.yaml"
            prefix = "---\nname: test\n...\n"
            rows = ["你好\tni hao\t100\n", "谢谢\txie xie\t100\n"]
            path.write_text(prefix + "".join(rows), encoding="utf-8")
            first = layers.selected(path, 1, 2, 6)
            path.write_text(prefix + "".join(reversed(rows)), encoding="utf-8")
            self.assertEqual(first, layers.selected(path, 1, 2, 6))

    def test_export_ignores_headers_but_not_frequency(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "user.tsv"
            path.write_text("# time\n你好\tni hao\tc=2 d=3\n", encoding="utf-8")
            self.assertEqual(run.export_rows(path), ["你好\tni hao\tc=2 d=3"])


class EvidenceTests(unittest.TestCase):
    def test_default_gate_is_not_affected_by_percent_rounding(self):
        old = {"n": 32, "top1_hits": 23}
        self.assertTrue(summarize.sufficient_top1_gain(old, {"n": 32, "top1_hits": 25}))
        self.assertFalse(summarize.sufficient_top1_gain(old, {"n": 32, "top1_hits": 24}))
        self.assertFalse(summarize.sufficient_top1_gain(old, {"n": 0, "top1_hits": 0}))

    def test_apk_manifest_detects_stale_resource_and_missing_entry(self):
        with tempfile.TemporaryDirectory() as root:
            for body, include in [(b"word", True), (b"changed", True), (b"word", False)]:
                apk = Path(root) / "test.apk"
                files = {"shared": "", "shared/test.dict.yaml": hashlib.sha256(b"word").hexdigest()}
                if not include:
                    del files["shared/test.dict.yaml"]
                manifest = {"files": files, "sha256": hashlib.sha256(", ".join(k + files[k] for k in sorted(files)).encode()).hexdigest()}
                with zipfile.ZipFile(apk, "w") as archive:
                    archive.writestr("assets/checksums.json", json.dumps(manifest))
                    archive.writestr("assets/shared/test.dict.yaml", body)
                if body == b"word" and include:
                    self.assertEqual(audit.verify_apk(apk)["verified_file_count"], 1)
                else:
                    with self.assertRaises(ValueError):
                        audit.verify_apk(apk)

    def test_sequential_runs_require_same_engine_corpus_and_device(self):
        first = {"state": "complete", "build": {"apk_sha256": "apk", "signer_certificate_sha256": ["cert"]},
                 "corpus_sha256": "corpus", "device": {"serial": "test"},
                 "engine": {"library_sha256": "library", "abi": "arm64-v8a", "execution_mode": "native",
                            "sources_sha256": {"probe.cc": "probe"}}, "profiles": {"luna": {}}}
        second = copy.deepcopy(first)
        second["profiles"] = {"compact": {}}
        merge.compatible([first, second])
        for key, value in [("corpus_sha256", "changed"), ("state", "failed"),
                           ("device", {}), ("profiles", {"luna": {}})]:
            changed = copy.deepcopy(second)
            changed[key] = value
            with self.assertRaises(ValueError):
                merge.compatible([first, changed])


if __name__ == "__main__":
    unittest.main()
