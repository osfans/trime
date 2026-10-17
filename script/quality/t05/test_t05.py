# SPDX-License-Identifier: GPL-3.0-or-later
import copy
import unittest

from summarize import summarize


def fixture():
    rows = [{"pinyin": str(i), "input": str(i), "candidates": [str(i)],
             "suggestions": 0, "first_key_ms": i + 2,
             "last_key_and_80_candidates_ms": i + 1} for i in range(24)]
    on = copy.deepcopy(rows)
    for row in on:
        row["suggestions"] = 3
    return {"version": "test", "version_code": 1, "scope": "synthetic tool test",
            "groups": [{"all_rules": False, "records": rows, "app_pss_kib": 100},
                       {"all_rules": True, "records": on, "app_pss_kib": 110}],
            "recovery": [{"rule": rule, "input": str(i), "target": str(i),
                          "before": False, "after": True, "sources": 1 << rule}
                         for rule in range(9) for i in range(2 if rule < 6 else 4)]}


class SummaryTest(unittest.TestCase):
    def test_metrics(self):
        result = summarize(fixture())
        self.assertEqual(result["exact_candidate_lists_unchanged"], 24)
        self.assertEqual(result["measurements"][0]["median_ms"], 12.5)
        self.assertEqual(result["measurements"][0]["p95_ms"], 23)
        self.assertEqual(result["measurements"][0]["first_key_median_ms"], 13.5)
        self.assertEqual(result["measurements"][0]["first_key_max_ms"], 25)
        self.assertEqual(result["measurements"][0]["first_input_first_key_ms"], 2)
        self.assertEqual(result["measurements"][0]["subsequent_first_key_median_ms"], 14)
        self.assertEqual(result["recovery"]["typo"], {"total": 12, "before": 0, "after": 12})

    def test_failures_are_not_hidden(self):
        data = fixture()
        data["recovery"][0].update(before=True, after=False)
        result = summarize(data)
        self.assertEqual(len(result["regressed"]), 1)
        self.assertEqual(len(result["unrecovered"]), 1)

    def test_completion_can_already_recover(self):
        data = fixture()
        data["recovery"][0].update(before=True, sources=0)
        self.assertEqual(summarize(data)["recovery"]["fuzzy"]["before"], 1)

    def test_candidate_regression_rejected(self):
        data = fixture()
        data["groups"][1]["records"][0]["candidates"] = ["changed"]
        with self.assertRaises(ValueError):
            summarize(data)

    def test_bounds_and_missing_records_rejected(self):
        for mutate in (lambda d: d["groups"][1]["records"][0].update(suggestions=33),
                       lambda d: d["recovery"].pop(),
                       lambda d: d["recovery"][0].update(sources=511),
                       lambda d: d["groups"][0]["records"][0].update(last_key_and_80_candidates_ms=float("nan"))):
            data = fixture()
            mutate(data)
            with self.assertRaises(ValueError):
                summarize(data)


if __name__ == "__main__":
    unittest.main()
