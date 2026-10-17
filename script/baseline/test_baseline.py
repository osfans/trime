# SPDX-License-Identifier: GPL-3.0-or-later
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

import baseline


MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.osfans.trime.debug" android:versionCode="20261101" android:versionName="3.3.13">
    <application android:debuggable="true" /></manifest>'''
BUILD_CONFIG = '''.field public static final BUILD_COMMIT_HASH:Ljava/lang/String; = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
.field public static final BUILD_VERSION_NAME:Ljava/lang/String; = "v3.3.12-18-gd736cd2c"
'''
SIGNATURE = "Signer #1 certificate SHA-256 digest: " + "ab" * 32


class BaselineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "misleading-v999-release.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("assets/shared/luna_pinyin_t9.schema.yaml", "schema: {}\n")
            archive.writestr("assets/shared/luna_pinyin.dict.yaml", "dictionary\n")
            archive.writestr("lib/arm64-v8a/librime_jni.so", "native-library")
            archive.writestr("assets/shared/opencc/t2s.json", "{}")
            archive.writestr("assets/shared/opencc/TSCharacters.txt", "fixture")
        self.sdk_tools = dict(analyzer="analyzer", signer="signer")

    def prepare(self, compiled_from=None):
        with patch.object(baseline, "run", side_effect=[MANIFEST, SIGNATURE, BUILD_CONFIG]):
            return baseline.prepare(self.apk, "fixture", self.root / "runs",
                                    baseline.HERE / "corpus.json", self.sdk_tools, compiled_from)

    def test_fetch_latest_repository_is_explicit_and_recorded(self):
        for repository in ("osfans/trime", "X2M7/trime"):
            payload = b"synthetic-not-an-apk"
            asset = dict(name="fixture-arm64-v8a.apk", browser_download_url="https://example.invalid/fixture.apk",
                         digest="sha256:" + baseline.hashlib.sha256(payload).hexdigest())
            release = dict(tag_name="synthetic", id=123, html_url="https://example.invalid/release", assets=[asset])
            with self.subTest(repository=repository), patch.object(baseline.urllib.request, "urlopen", side_effect=[
                    io.BytesIO(json.dumps(release).encode()), io.BytesIO(payload)]) as opened:
                apk = baseline.fetch_latest(self.root, repository)
                self.assertEqual("https://api.github.com/repos/" + repository + "/releases/latest", opened.call_args_list[0].args[0].full_url)
                self.assertEqual(payload, apk.read_bytes())
                self.assertEqual(repository, baseline.read_json(apk.parent / "release.json")["repository"])

    def test_fetch_latest_refuses_ambiguous_repository_without_network(self):
        for repository in ("", "owner/repo/extra", "../trime", "osfans/trime?tag=bad"):
            with self.subTest(repository=repository), patch.object(baseline.urllib.request, "urlopen") as opened:
                with self.assertRaises(ValueError):
                    baseline.fetch_latest(self.root, repository)
                opened.assert_not_called()

    def test_opencc_seed_is_separate_from_frozen_shared_resources(self):
        root = self.prepare()
        self.assertEqual(baseline.read_json(root / "report.json")["fixture_version"], 2)
        for cohort in ("cold", "learned"):
            self.assertTrue((root / cohort / "user/opencc/t2s.json").is_file())
        (root / "cold/user/opencc/TSCharacters.ocd2").write_bytes(b"compiled")
        self.assertFalse((root / "inputs/shared/opencc/TSCharacters.ocd2").exists())
        baseline.load_run(root)

    def compiled_source(self):
        source = self.prepare()
        report = baseline.read_json(source / "report.json")
        report["engine"] = {"kind": "apk_native_engine"}
        baseline.write_json(source / "report.json", report)
        (source / "cold/user/build").mkdir()
        (source / "cold/user/build/luna_pinyin.table.bin").write_bytes(b"table")
        (source / "cold/user/personal.userdb").write_bytes(b"do not copy")
        baseline.write_json(source / "cold/runtime-files.sha256.json", baseline.tree_hashes(source / "cold/user"))
        return source

    def test_compiled_cache_does_not_copy_user_dictionary(self):
        source = self.compiled_source()
        root = self.prepare(source)
        for cohort in ("cold", "learned"):
            self.assertTrue((root / cohort / "user/build/luna_pinyin.table.bin").is_file())
            self.assertFalse((root / cohort / "user/personal.userdb").exists())
        self.assertIn("compiled_seed_source", baseline.read_json(root / "report.json"))

    def test_changed_compiled_cache_is_rejected(self):
        source = self.compiled_source()
        (source / "cold/user/build/luna_pinyin.table.bin").write_bytes(b"modified")
        with self.assertRaisesRegex(ValueError, "hashes"):
            self.prepare(source)

    def test_other_apk_compiled_cache_is_rejected(self):
        source = self.compiled_source()
        report = baseline.read_json(source / "report.json")
        report["build"]["apk_sha256"] = "another-apk"
        baseline.write_json(source / "report.json", report)
        with self.assertRaisesRegex(ValueError, "identical APK"):
            self.prepare(source)

    def test_host_library_compiled_cache_is_not_an_apk_cache(self):
        source = self.compiled_source()
        report = baseline.read_json(source / "report.json")
        report["engine"] = {"kind": "host_smoke_only"}
        baseline.write_json(source / "report.json", report)
        with self.assertRaisesRegex(ValueError, "identical APK"):
            self.prepare(source)

    def test_learning_reuses_only_compiled_resources_not_cold_user_data(self):
        root = self.prepare()
        corpus = baseline.read_json(root / "inputs/corpus.json")
        report = baseline.read_json(root / "report.json")

        def probe(command, **kwargs):
            user, phase = Path(command[3]), command[5]
            if user.parent.name == "cold":
                (user / "build").mkdir()
                (user / "build/dictionary.table.bin").write_bytes(b"compiled")
                (user / "cold-only.userdb").write_bytes(b"must stay isolated")
            else:
                self.assertTrue((user / "build/dictionary.table.bin").is_file())
                self.assertFalse((user / "cold-only.userdb").exists())
            records = ([{"input": step["input"], "committed": step["text"], "iteration": n + 1}
                        for step in corpus["learning"] for n in range(step["repeat"])] if phase == "train" else
                       [dict(case, candidates=[{"text": "fixture"}]) for case in corpus["cases"]])
            return json.dumps({"run_id": report["run_id"], "schema": corpus["schema"],
                               "options": corpus["options"], "phase": phase,
                               "opencc_validated": True, "records": records})

        args = SimpleNamespace(run=root, host_library=self.apk, phase_timeout=60, reuse_cold_build=True)
        with patch.object(baseline, "compile_probe", side_effect=lambda output, *args: output.write_bytes(b"probe")), \
                patch.object(baseline, "run", side_effect=probe):
            baseline.execute(args, self.sdk_tools)
        self.assertIn("host_smoke_only", baseline.verify(root))

    def test_legacy_fixture_cannot_be_verified(self):
        root = self.prepare()
        report = baseline.read_json(root / "report.json")
        report.pop("fixture_version")
        baseline.write_json(root / "report.json", report)
        with self.assertRaisesRegex(ValueError, "OpenCC"):
            baseline.verify(root)

    def test_recovery_refuses_active_probe_and_wrong_remote_owner(self):
        root = self.prepare()
        report = baseline.read_json(root / "report.json")
        remote = "/data/local/tmp/trime-baseline-" + "a" * 32
        report["engine"] = {"kind": "apk_native_engine", "remote_directory": remote}
        report["device"] = {"serial": "test"}
        report["cohorts"]["cold"]["status"] = "running"
        baseline.write_json(root / "report.json", report)
        args = SimpleNamespace(run=root, serial="test")
        with patch.object(baseline, "shell", return_value=remote + "/engine-probe"):
            with self.assertRaisesRegex(ValueError, "still running"):
                baseline.recover(args, {})
        with patch.object(baseline, "shell", side_effect=["no probe", "wrong owner"]), \
                patch.object(baseline, "adb") as adb:
            with self.assertRaisesRegex(ValueError, "ownership"):
                baseline.recover(args, {})
            adb.assert_not_called()

    def test_reads_manifest_version_not_apk_filename(self):
        root = self.prepare()
        report = baseline.read_json(root / "report.json")
        self.assertEqual(report["build"]["version_name"], "3.3.13")
        self.assertEqual(report["build"]["version_code"], 20261101)
        self.assertEqual(report["build"]["package_name"], "com.osfans.trime.debug")
        self.assertTrue(report["build"]["debuggable"])
        self.assertEqual(report["build"]["git_sha"], "a" * 40)
        self.assertEqual(report["build"]["signer_certificate_sha256"], ["ab" * 32])
        self.assertEqual(report["build"]["abis"], ["arm64-v8a"])

    def test_unknown_git_sha_is_not_guessed(self):
        with patch.object(baseline, "run", side_effect=[MANIFEST, SIGNATURE, RuntimeError("stripped")]):
            identity = baseline.inspect_apk(self.apk, self.sdk_tools)
        self.assertIsNone(identity["git_sha"])

    def test_unsigned_package_is_rejected(self):
        with patch.object(baseline, "run", side_effect=[MANIFEST, "no signer"]):
            with self.assertRaises(ValueError):
                baseline.inspect_apk(self.apk, self.sdk_tools)

    def test_two_fresh_cohorts_and_repeated_preparation_are_isolated(self):
        root = self.prepare()
        another = self.prepare()
        self.assertNotEqual(root, another)
        first = root / "cold/user/custom.userdb"
        first.write_text("learned state")
        self.assertFalse((root / "learned/user/custom.userdb").exists())
        self.assertFalse((another / "cold/user/custom.userdb").exists())
        self.assertEqual(baseline.read_json(root / "report.json")["cohorts"]["learned"]["status"], "not_run")

    def test_modified_dictionary_invalidates_frozen_run(self):
        root = self.prepare()
        baseline.load_run(root)
        (root / "inputs/shared/luna_pinyin.dict.yaml").write_text("changed")
        with self.assertRaisesRegex(ValueError, "modified"):
            baseline.load_run(root)

    def test_modified_corpus_invalidates_frozen_run(self):
        root = self.prepare()
        (root / "inputs/corpus.json").write_text("{}")
        with self.assertRaises(ValueError):
            baseline.load_run(root)

    def test_unowned_directory_is_rejected(self):
        root = self.prepare()
        baseline.write_json(root / "owner.json", {"run_id": "different"})
        with self.assertRaisesRegex(ValueError, "owned"):
            baseline.load_run(root)

    def test_archive_traversal_is_rejected(self):
        with zipfile.ZipFile(self.apk, "a") as archive:
            archive.writestr("assets/shared/../../../outside", "unexpected")
        with self.assertRaises(ValueError):
            baseline.extract_inputs(self.apk, self.root / "extract")
        self.assertFalse((self.root / "outside").exists())

    def test_archive_symlink_is_rejected(self):
        with zipfile.ZipFile(self.apk, "a") as archive:
            link = zipfile.ZipInfo("assets/shared/link")
            link.external_attr = 0o120777 << 16
            archive.writestr(link, "/some/personal/dictionary")
        with self.assertRaises(ValueError):
            baseline.extract_inputs(self.apk, self.root / "extract")

    def test_hashing_rejects_symlink_to_personal_data(self):
        source = self.root / "outside"
        source.write_text("personal")
        target = self.root / "input"
        target.mkdir()
        (target / "link").symlink_to(source)
        with self.assertRaises(ValueError):
            baseline.tree_hashes(target)

    def test_learning_and_inputs_are_literal_and_bounded(self):
        corpus = baseline.read_json(baseline.HERE / "corpus.json")
        baseline.validate_corpus(corpus)
        corpus["cases"][0]["input"] = "{Return}"
        with self.assertRaises(ValueError):
            baseline.validate_corpus(corpus)
        corpus = baseline.read_json(baseline.HERE / "corpus.json")
        corpus["learning"][0]["repeat"] = 100000
        with self.assertRaises(ValueError):
            baseline.validate_corpus(corpus)

    def test_artifact_has_build_and_cohort_identity(self):
        root = self.prepare()
        _, report = baseline.load_run(root)
        path = root / "learned/measure.json"
        path.write_text('{"records": []}')
        baseline.artifact(root, report, path, "learned", {"engine": {"kind": "host_smoke_only"}})
        identity = baseline.read_json(str(path) + ".identity.json")
        self.assertEqual(identity["apk_sha256"], baseline.digest(self.apk))
        self.assertEqual(identity["cohort"], "learned")
        self.assertEqual(identity["run_id"], report["run_id"])
        self.assertEqual(identity["sha256"], baseline.digest(path))
        self.assertEqual(identity["engine"]["kind"], "host_smoke_only")

    def test_existing_user_dictionary_is_never_reset(self):
        root = self.prepare()
        personal = root / "cold/user/custom.userdb"
        personal.write_text("keep this state")
        args = SimpleNamespace(run=root, host_library="/not/used", serial=None)
        with self.assertRaisesRegex(ValueError, "single-use"):
            baseline.execute(args, None)
        self.assertEqual(personal.read_text(), "keep this state")

    def test_capture_refuses_other_installed_binary_before_screenshot(self):
        root = self.prepare()
        args = SimpleNamespace(run=root, serial="test", runtime_export=None)
        with patch.object(baseline, "device_info", return_value={}), \
                patch.object(baseline, "installed_apks", return_value={"apk_sha256": "different"}), \
                patch.object(baseline, "adb") as adb:
            with self.assertRaisesRegex(ValueError, "match"):
                baseline.capture(args, self.sdk_tools)
            adb.assert_not_called()

    def test_learning_result_requires_all_commits_not_just_a_success_flag(self):
        corpus = baseline.read_json(baseline.HERE / "corpus.json")
        result = {"run_id": "run", "schema": corpus["schema"], "options": corpus["options"],
                  "phase": "train", "records": []}
        with self.assertRaisesRegex(ValueError, "completed exactly"):
            baseline.validate_result(result, corpus, "train", "run")
        result["records"] = [{"input": step["input"], "committed": step["text"], "iteration": n + 1}
                             for step in corpus["learning"] for n in range(step["repeat"])]
        baseline.validate_result(result, corpus, "train", "run")
        result["records"][-1]["committed"] = "wrong candidate"
        with self.assertRaises(ValueError):
            baseline.validate_result(result, corpus, "train", "run")

    def test_incomplete_cohorts_are_not_verified_as_success(self):
        root = self.prepare()
        with self.assertRaisesRegex(ValueError, "has not completed"):
            baseline.verify(root)

    def test_device_records_emulator_and_native_bridge_without_inventing_phone_identity(self):
        props = {"ro.product.cpu.abi": "x86_64", "ro.product.cpu.abilist": "x86_64,arm64-v8a",
                 "ro.kernel.qemu": "1", "ro.dalvik.vm.native.bridge": "libndk_translation.so",
                 "ro.enable.native.bridge.exec": "1", "ro.ndk_translation.version": "0.2.3"}
        with patch.object(baseline, "adb", return_value="device\n"), \
                patch.object(baseline, "shell", side_effect=lambda *args: props.get(args[-1], "")):
            info = baseline.device_info({}, "emulator-5554")
        self.assertEqual(info["serial"], "emulator-5554")
        for key, value in props.items():
            self.assertEqual(info["properties"][key], value)

    def test_phase_timeout_is_bounded_before_running_or_writing_device_data(self):
        root = self.prepare()
        for timeout in (0, -1, 3601):
            args = SimpleNamespace(run=root, phase_timeout=timeout)
            with self.assertRaisesRegex(ValueError, "phase-timeout"), \
                    patch.object(baseline, "adb") as adb:
                baseline.execute(args, self.sdk_tools)
            adb.assert_not_called()


if __name__ == "__main__":
    unittest.main()
