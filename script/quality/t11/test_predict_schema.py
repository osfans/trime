#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compile actual fork schemas with tiny dictionaries through host librime.

This checks include/custom patch resolution, including the simplified variant's
replaced switches. The engine pipelines are copied unchanged. Android runtime
prediction/privacy/editor tests remain separate from this host deployment test.
"""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import yaml

import build_predict_db as model


@unittest.skipUnless(shutil.which("rime_deployer"), "host rime_deployer is not installed")
class PredictionSchemaDeploymentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="trime-t11-schema-")
        cls.root = Path(cls.temporary.name)
        cls.shared, cls.user = cls.root / "shared", cls.root / "user"
        cls.shared.mkdir()
        cls.user.mkdir()
        assets = model.ROOT / "app/src/main/assets/shared"
        for name in ("default.yaml", "key_bindings.yaml", "symbols.yaml", "punctuation.yaml", "pinyin.yaml",
                     "luna_pinyin.schema.yaml", "luna_pinyin_simp.schema.yaml", "luna_pinyin_t9.schema.yaml",
                     "luna_pinyin.custom.yaml", "luna_pinyin_simp.custom.yaml", "stroke.schema.yaml"):
            shutil.copyfile(assets / name, cls.shared / name)
        # Use a tiny test dictionary instead of the bundled corpus; preserve schemas.
        fixture = (model.ROOT / "script/t9/fixtures/t9_test.dict.yaml").read_text(encoding="utf-8")
        (cls.shared / "luna_pinyin.dict.yaml").write_text(fixture.replace("name: t9_test", "name: luna_pinyin"), encoding="utf-8")
        (cls.shared / "stroke.dict.yaml").write_text(
            "---\nname: stroke\nversion: '1'\nsort: by_weight\nuse_preset_vocabulary: false\n...\n一\th\t100\n", encoding="utf-8")
        cls.compiled = {}
        cls.commands = []
        for schema in ("luna_pinyin", "luna_pinyin_simp", "luna_pinyin_t9"):
            command = [shutil.which("rime_deployer"), "--compile", str(cls.shared / f"{schema}.schema.yaml"),
                       str(cls.user), str(cls.shared)]
            completed = subprocess.run(command, capture_output=True, text=True, timeout=30)
            cls.commands.append({"command": command, "exit_code": completed.returncode,
                                 "stdout": completed.stdout, "stderr": completed.stderr})
            if completed.returncode:
                raise AssertionError(f"schema deploy failed: {schema}\n{completed.stderr}")
            cls.compiled[schema] = yaml.safe_load((cls.user / "build" / f"{schema}.schema.yaml").read_text(encoding="utf-8"))

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_predictor_is_before_key_binder_and_keeps_base_pipeline(self):
        for schema, config in self.compiled.items():
            with self.subTest(schema=schema):
                processors = config["engine"]["processors"]
                self.assertEqual(processors.count("predictor"), 1)
                self.assertLess(processors.index("predictor"), processors.index("key_binder"))
                self.assertIn("speller", processors)
                self.assertIn("express_editor", processors)
                self.assertEqual(config["engine"]["translators"].count("predict_translator"), 1)
                self.assertIn("script_translator", config["engine"]["translators"])

    def test_default_full_simplified_and_t9_all_have_disabled_switch(self):
        for schema, config in self.compiled.items():
            with self.subTest(schema=schema):
                switches = [item for item in config["switches"] if item.get("name") == "prediction"]
                self.assertEqual(len(switches), 1)
                self.assertEqual(switches[0]["reset"], 0)
                # Native F4 and Android's schema-switch menu only display
                # labelled states. Keep the option/reset without a second UI
                # control that can bypass Trime's saved global preference.
                self.assertNotIn("states", switches[0])
                ordinary = {item.get("name"): item for item in config["switches"]
                            if item.get("name") != "prediction"}
                for name in ("ascii_mode", "full_shape"):
                    self.assertEqual(len(ordinary[name]["states"]), 2)
                self.assertEqual(config["predictor"], {"db": "trime_predict.db", "max_candidates": 5, "max_iterations": 1})

    def test_missing_model_does_not_block_dictionary_deployment(self):
        self.assertFalse((self.shared / "trime_predict.db").exists())
        self.assertTrue((self.user / "build/luna_pinyin.table.bin").is_file())
        self.assertTrue((self.user / "build/luna_pinyin_t9.prism.bin").is_file())


if __name__ == "__main__":
    unittest.main()
