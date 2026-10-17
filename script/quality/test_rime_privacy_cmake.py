# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify native privacy overlays without editing pinned dependencies."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "app/src/main/jni"
CMAKE = os.environ.get("CMAKE_EXECUTABLE", "cmake")
RIME_FILES = [
    "rime/context.h",
    "rime/context.cc",
    "rime/engine.cc",
    "rime/service.h",
    "rime/service.cc",
    "rime/dict/user_dictionary.cc",
    "rime/gear/ascii_composer.h",
    "rime/gear/ascii_composer.cc",
    "rime/gear/chord_composer.cc",
    "rime/gear/streaming_chord_processor.h",
    "rime/gear/streaming_chord_processor.cc",
    "rime/gear/key_binder.h",
    "rime/gear/key_binder.cc",
    "rime/gear/navigator.h",
    "rime/gear/navigator.cc",
    "rime/gear/punctuator.h",
    "rime/gear/punctuator.cc",
    "rime/gear/script_translator.h",
    "rime/gear/script_translator.cc",
    "rime/gear/table_translator.cc",
]


class RimePrivacyCmakeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        rime_source = self.root / "librime/src"
        for relative in RIME_FILES:
            target = rime_source / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(NATIVE / "librime/src" / relative, target)
        (rime_source / "untouched.cc").write_text("int untouched;\n")
        core_sources = " ".join(
            relative for relative in RIME_FILES if relative.endswith(".cc")
        )
        (rime_source / "CMakeLists.txt").write_text(
            f"add_library(rime-static STATIC {core_sources} untouched.cc)\n"
        )

        self._make_plugin(
            "librime-lua",
            "rime-lua-objs",
            "types_ext.cc",
            NATIVE / "librime-lua/src/types_ext.cc",
        )
        self._make_plugin("librime-predict", "rime-predict-objs")
        predict_dir = self.root / "librime-predict/src"
        for source in (NATIVE / "librime-predict/src").glob("*"):
            if source.suffix in {".cc", ".h"}:
                shutil.copyfile(source, predict_dir / source.name)
        (self.root / "librime-predict/CMakeLists.txt").write_text(
            "add_library(rime-predict-objs OBJECT src/dummy.cc "
            "src/predictor.cc src/predict_engine.cc src/predict_db.cc "
            "src/predict_translator.cc src/predict_module.cc)\n"
        )
        self._make_plugin("librime-octagram", "rime-octagram-objs")
        guard_dir = self.root / "librime_jni"
        guard_dir.mkdir()
        for name in ["trime_privacy_guard.cc", "trime_privacy_guard.h",
                     "trime_prediction_guard.cc", "trime_prediction_guard.h"]:
            shutil.copyfile(NATIVE / "librime_jni" / name, guard_dir / name)

        (self.root / "CMakeLists.txt").write_text(
            "cmake_minimum_required(VERSION 3.18...3.31)\n"
            "project(probe CXX)\n"
            "add_subdirectory(librime/src)\n"
            "add_subdirectory(librime-lua)\n"
            "add_subdirectory(librime-octagram)\n"
            "add_subdirectory(librime-predict)\n"
            f'include("{NATIVE}/cmake/ThirdPartyCmake.cmake")\n'
            f'include("{NATIVE}/cmake/RimePrivacyCompat.cmake")\n'
            "trime_adapt_rime_privacy()\n"
            "get_target_property(core rime-static SOURCES)\n"
            "get_target_property(lua rime-lua-objs SOURCES)\n"
            "get_target_property(predict rime-predict-objs SOURCES)\n"
            'file(WRITE "${CMAKE_BINARY_DIR}/core.txt" "${core}")\n'
            'file(WRITE "${CMAKE_BINARY_DIR}/lua.txt" "${lua}")\n'
            'file(WRITE "${CMAKE_BINARY_DIR}/predict.txt" "${predict}")\n'
        )

    def _make_plugin(self, directory, target, primary=None, source=None):
        plugin = self.root / directory
        src = plugin / "src"
        src.mkdir(parents=True)
        sources = ["src/dummy.cc"]
        (src / "dummy.cc").write_text("int dummy;\n")
        if primary:
            shutil.copyfile(source, src / primary)
            sources.append(f"src/{primary}")
        (plugin / "CMakeLists.txt").write_text(
            f"add_library({target} OBJECT {' '.join(sources)})\n"
        )

    def configure(self, build="build"):
        return subprocess.run(
            [
                CMAKE,
                "-S",
                str(self.root),
                "-B",
                str(self.root / build),
                "-Werror=dev",
                "-Werror=deprecated",
            ],
            capture_output=True,
            text=True,
            timeout=60,
        )

    def test_overlays_are_stable_scoped_and_complete(self):
        originals = {
            path: path.read_bytes()
            for path in [
                *(self.root / "librime/src" / item for item in RIME_FILES),
                self.root / "librime-lua/src/types_ext.cc",
                *(self.root / "librime-predict/src").glob("*"),
            ]
        }
        timestamps = None
        for _ in range(2):
            result = self.configure()
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertNotIn("Warning", result.stdout + result.stderr)
            outputs = [
                *(self.root / "build/trime-rime-privacy" / item for item in RIME_FILES),
                self.root / "build/trime-rime-lua-privacy/types_ext.cc",
                *(self.root / "build/trime-rime-predict").glob("*"),
            ]
            current = {path: path.stat().st_mtime_ns for path in outputs}
            if timestamps is not None:
                self.assertEqual(timestamps, current)
            timestamps = current

        self.assertEqual(originals, {path: path.read_bytes() for path in originals})
        core = (self.root / "build/core.txt").read_text().split(";")
        self.assertEqual(len(core), 15)
        self.assertEqual(sum("/trime-rime-privacy/" in path for path in core), 12)
        self.assertEqual(sum(path.endswith("/trime_privacy_guard.cc") for path in core), 1)
        self.assertIn("untouched.cc", core)
        self.assertEqual(sum(path.endswith("/trime_prediction_guard.cc") for path in core), 1)
        lua = (self.root / "build/lua.txt").read_text().split(";")
        self.assertEqual(len(lua), 2)
        self.assertEqual(sum("/trime-rime-lua-privacy/types_ext.cc" in path for path in lua), 1)
        predict = (self.root / "build/predict.txt").read_text().split(";")
        self.assertEqual(len(predict), 6)
        self.assertEqual(sum("/trime-rime-predict/predictor.cc" in path for path in predict), 1)
        self.assertEqual(sum("/trime-rime-predict/" in path for path in predict), 5)

        overlay = self.root / "build/trime-rime-privacy"
        context_header = (overlay / "rime/context.h").read_text()
        context = (overlay / "rime/context.cc").read_text()
        engine = (overlay / "rime/engine.cc").read_text()
        service_header = (overlay / "rime/service.h").read_text()
        service = (overlay / "rime/service.cc").read_text()
        user_dict = (overlay / "rime/dict/user_dictionary.cc").read_text()
        ascii_header = (overlay / "rime/gear/ascii_composer.h").read_text()
        ascii_composer = (overlay / "rime/gear/ascii_composer.cc").read_text()
        chord = (overlay / "rime/gear/chord_composer.cc").read_text()
        streaming_header = (overlay / "rime/gear/streaming_chord_processor.h").read_text()
        streaming = (overlay / "rime/gear/streaming_chord_processor.cc").read_text()
        key_binder_header = (overlay / "rime/gear/key_binder.h").read_text()
        key_binder = (overlay / "rime/gear/key_binder.cc").read_text()
        navigator_header = (overlay / "rime/gear/navigator.h").read_text()
        navigator = (overlay / "rime/gear/navigator.cc").read_text()
        punctuator_header = (overlay / "rime/gear/punctuator.h").read_text()
        punctuator = (overlay / "rime/gear/punctuator.cc").read_text()
        script_header = (overlay / "rime/gear/script_translator.h").read_text()
        script_translator = (overlay / "rime/gear/script_translator.cc").read_text()
        self.assertIn("commit_output_notifier_(this)", context)
        self.assertIn("unhandled_key_output_notifier()", context_header)
        self.assertIn("KeyEventNotifier unhandled_key_output_notifier_;", context_header)
        self.assertIn("context_->unhandled_key_output_notifier()", engine)
        self.assertIn("if (!synthetic && !trime::IsPrivacyModeEnabled())", engine)
        self.assertEqual(engine.count("if (!trime::IsPrivacyModeEnabled())"), 3)
        self.assertEqual(engine.count("if (!synthetic && !trime::IsPrivacyModeEnabled())"), 1)
        self.assertIn('context_->set_option("prediction", trime::IsPredictionRequested() &&', engine)
        self.assertIn('!context_->get_option("ascii_mode"));', engine)
        self.assertIn("void ResetPrivacyContexts(bool enabled);", service_header)
        self.assertIn("Engine* primary = engine_.get();", service)
        self.assertIn("Engine* active = engine_->active_engine();", service)
        self.assertIn("reset_context(active->context());", service)
        self.assertIn("switcher->Deactivate();", service)
        self.assertIn("engine_->set_active_engine();", service)
        self.assertIn("reset_context(primary->context());", service)
        self.assertEqual(service.count('context->set_option("trime_privacy_mode", enabled)'), 1)
        self.assertLess(service.index("reset_context(active->context());"), service.index("reset_context(primary->context());"))
        self.assertGreaterEqual(user_dict.count("trime::IsPrivacyModeEnabled()"), 4)
        self.assertIn("void OnPrivacyContextUpdate(Context* ctx);", ascii_header)
        self.assertIn("connection privacy_connection_;", ascii_header)
        self.assertEqual(ascii_composer.count("trime::IsPrivacyTransitionBoundary()"), 1)
        self.assertIn("if (had_inline_ascii)", ascii_composer)
        self.assertIn('ctx->set_option("ascii_mode", false)', ascii_composer)
        self.assertIn("toggle_expired_ = std::chrono::steady_clock::time_point{}", ascii_composer)
        self.assertIn("ctx->commit_output_notifier().connect(", ascii_composer)
        self.assertNotIn("ctx->commit_notifier().connect(", ascii_composer)
        ascii_boundary = ascii_composer.split("void AsciiComposer::OnPrivacyContextUpdate", 1)[1]
        for state in ["raw_keys_.clear()", "cached_input_.clear()", "cached_raw_keys_.clear()",
                      "is_inline_ascii_ = false", "ResetModifierState()"]:
            self.assertIn(state, ascii_boundary)
        self.assertEqual(chord.count("trime::IsPrivacyTransitionBoundary()"), 1)
        self.assertGreaterEqual(chord.count("raw_sequence_.clear()"), 5)
        self.assertGreaterEqual(chord.count("state_.Clear()"), 3)
        self.assertIn("ctx->unhandled_key_output_notifier().connect(", chord)
        self.assertNotIn("ctx->unhandled_key_notifier().connect(", chord)
        self.assertNotIn(
            "ProcessResult ChordComposer::ProcessKeyEvent(const KeyEvent& key_event) {\n"
            "  if (trime::IsPrivacyModeEnabled())",
            chord,
        )
        self.assertIn("~StreamingChordProcessor() override;", streaming_header)
        self.assertIn("privacy_connection_.disconnect()", streaming)
        self.assertEqual(streaming.count("trime::IsPrivacyTransitionBoundary()"), 1)
        streaming_boundary = streaming.split("void StreamingChordProcessor::OnPrivacyContextUpdate", 1)[1].split(
            "void StreamingChordProcessor::ResetTracking", 1)[0]
        for state in ["last_key_event_ = {}", "pending_solo_key_ = {}", "pressed_chord_keys_.clear()",
                      "current_chord_start_ = 0", "is_chord_open_ = false", "is_replaying_ = false"]:
            self.assertIn(state, streaming_boundary)
        self.assertNotIn("ReplayPendingKey()", streaming_boundary)
        self.assertNotIn("ProcessSyntheticKey(", streaming_boundary)
        self.assertIn("virtual ~KeyBinder();", key_binder_header)
        self.assertIn("class Context;", key_binder_header)
        self.assertGreaterEqual(key_binder.count("last_paging_key_ = 0"), 2)
        self.assertIn("paging_keystroke_count_ = 0", key_binder)
        self.assertNotIn("std::isalpha(ch)", key_binder)
        self.assertIn("(ch >= 'a' && ch <= 'z')", key_binder)
        self.assertIn("(ch >= 'A' && ch <= 'Z')", key_binder)
        self.assertNotIn("trime::IsPrivacyModeEnabled()", key_binder)
        self.assertEqual(key_binder.count("trime::IsPrivacyTransitionBoundary()"), 1)
        self.assertIn("update_connection_.disconnect()", key_binder)
        self.assertIn("void OnContextUpdate(Context*);", navigator_header)
        self.assertIn("connection update_connection_;", navigator_header)
        self.assertIn("update_connection_.disconnect()", navigator)
        self.assertNotIn("trime::IsPrivacyModeEnabled()", navigator)
        self.assertEqual(navigator.count("trime::IsPrivacyTransitionBoundary()"), 1)
        self.assertGreaterEqual(navigator.count("input_.clear()"), 1)
        self.assertGreaterEqual(navigator.count("spans_.Clear()"), 3)
        self.assertNotIn(
            "ProcessResult Navigator::ProcessKeyEvent(const KeyEvent& key_event) {\n"
            "  if (trime::IsPrivacyModeEnabled())",
            navigator,
        )
        self.assertIn("virtual ~Punctuator();", punctuator_header)
        self.assertIn("class Context;", punctuator_header)
        self.assertNotIn("trime::IsPrivacyModeEnabled()", punctuator)
        self.assertEqual(punctuator.count("trime::IsPrivacyTransitionBoundary()"), 1)
        self.assertIn("oddness_.clear()", punctuator)
        self.assertIn("update_connection_.disconnect()", punctuator)
        self.assertIn("virtual ~ScriptTranslator();", script_header)
        self.assertIn("connection update_connection_;", script_header)
        self.assertIn("if (trime::IsPrivacyTransitionBoundary())", script_translator)
        self.assertGreaterEqual(script_translator.count("queue_.clear()"), 2)
        for name in ["script", "table"]:
            translator = (overlay / f"rime/gear/{name}_translator.cc").read_text()
            self.assertIn("trime::IsPrivacyModeEnabled() ||", translator)
        lua_content = (
            self.root / "build/trime-rime-lua-privacy/types_ext.cc"
        ).read_text()
        self.assertIn("class EmptyDbAccessor", lua_content)
        self.assertIn("return New<EmptyDbAccessor>()", lua_content)
        self.assertIn("!trime::IsPrivacyModeEnabled() && t->Open()", lua_content)
        self.assertIn("!trime::IsPrivacyModeEnabled() && t->Update", lua_content)
        predictor = (
            self.root / "build/trime-rime-predict/predictor.cc"
        ).read_text()
        self.assertIn("predict_engine_->Clear()", predictor)
        self.assertIn("trime::IsPrivacyModeEnabled()", predictor)
        self.assertIn("trime::IsPrivacyTransitionBoundary()", predictor)
        self.assertIn("pending_commit_ = false", predictor)
        self.assertIn("pending_prediction_ = false", predictor)
        self.assertIn("pending_text_.clear()", predictor)
        self.assertIn("trime::IsPredictionTransitionBoundary()", predictor)
        self.assertIn("option_update_connection_.disconnect()", predictor)
        self.assertIn("self_updating_ = false", predictor)
        model_header = (self.root / "build/trime-rime-predict/trime_predict_model_hash.h").read_text()
        self.assertIn("kBundledModelBytes = 6364", model_header)
        self.assertIn("2bc7ee4fe184c433b82a0650625e0fc4ecbc1c64352ea9aa36342d2c7a612add", model_header)
        self.assertEqual(
            (self.root / "build/trime-rime-predict/predict_module.cc").read_bytes(),
            (self.root / "librime-predict/src/predict_module.cc").read_bytes(),
        )
        self.assertEqual(
            (self.root / "build/trime-rime-predict/LICENSE").read_bytes(),
            (NATIVE / "librime-predict/LICENSE").read_bytes(),
        )

    def test_predict_source_and_module_drift_are_not_silently_ignored(self):
        for name in ["predictor.cc", "predict_engine.h", "predict_db.cc", "predict_module.cc"]:
            with self.subTest(name=name):
                source = self.root / "librime-predict/src" / name
                original = source.read_bytes()
                try:
                    source.write_bytes(original + b"\n// changed pinned source\n")
                    result = self.configure(build="build-drift-" + name.replace(".", "-"))
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("Re-audit Rime predict source", result.stderr)
                finally:
                    source.write_bytes(original)

    def test_predict_module_registration_cannot_remain_in_vendor_directory(self):
        cmake = self.root / "librime-predict/CMakeLists.txt"
        cmake.write_text(cmake.read_text().replace(" src/predict_module.cc", ""))
        result = self.configure(build="build-missing-module")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit Rime predict source registration: predict_module.cc", result.stderr)

    def test_upstream_drift_is_not_silently_ignored(self):
        source = self.root / "librime/src/rime/engine.cc"
        source.write_text(
            source.read_text().replace(
                'context_->commit_history().Push(CommitRecord{"raw", text});',
                "context_->commit_history().clear();",
            )
        )
        result = self.configure(build="build-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)

    def test_transition_boundary_is_atomic_and_raii_scoped(self):
        guard_header = (NATIVE / "librime_jni/trime_privacy_guard.h").read_text()
        guard_source = (NATIVE / "librime_jni/trime_privacy_guard.cc").read_text()
        bridge = (NATIVE / "librime_jni/rime_jni.cc").read_text()
        kotlin_bridge = (ROOT / "app/src/main/java/com/osfans/trime/core/Rime.kt").read_text()
        self.assertIn("class PrivacyTransitionBoundary final", guard_header)
        self.assertIn("std::atomic_uint privacy_transition_depth", guard_source)
        self.assertIn("fetch_add(1, std::memory_order_acq_rel)", guard_source)
        self.assertIn("fetch_sub(1, std::memory_order_acq_rel)", guard_source)
        self.assertIn("trime::PrivacyTransitionBoundary transition_boundary;", bridge)
        self.assertIn("current->ResetPrivacyContexts(enabled);", bridge)
        self.assertIn(
            "if (editorChanged || this.privacyMode != privacyMode)",
            kotlin_bridge,
        )
        boundary = bridge.index("trime::PrivacyTransitionBoundary transition_boundary;")
        clear = bridge.index("current->ResetPrivacyContexts(enabled);", boundary)
        disable = bridge.index("trime::SetPrivacyModeEnabled(false);", boundary)
        self.assertLess(boundary, clear)
        self.assertLess(clear, disable)

    def test_stateful_processor_source_drift_is_not_silently_ignored(self):
        source = self.root / "librime/src/rime/gear/chord_composer.cc"
        source.write_text(
            source.read_text().replace(
                "void ChordComposer::OnContextUpdate(Context* ctx) {",
                "void ChordComposer::OnContextChanged(Context* ctx) {",
            )
        )
        result = self.configure(build="build-chord-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)

    def test_additional_stateful_processor_drift_is_not_silently_ignored(self):
        source = self.root / "librime/src/rime/gear/punctuator.cc"
        source.write_text(
            source.read_text().replace(
                "  config_.LoadConfig(engine_);\n}\n\nstatic bool punctuation_is_translated",
                "  config_.LoadConfig(engine_, true);\n}\n\nstatic bool punctuation_is_translated",
            )
        )
        result = self.configure(build="build-punctuator-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)

    def test_streaming_chord_drift_is_not_silently_ignored(self):
        source = self.root / "librime/src/rime/gear/streaming_chord_processor.cc"
        source.write_text(source.read_text().replace(
            "void StreamingChordProcessor::ResetTracking() {",
            "void StreamingChordProcessor::ResetTracking(bool force) {",
        ))
        result = self.configure(build="build-streaming-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)

    def test_paging_ctype_source_drift_is_not_silently_ignored(self):
        source = self.root / "librime/src/rime/gear/key_binder.cc"
        source.write_text(source.read_text().replace(
            "std::isalpha(ch)", "std::isalpha(static_cast<unsigned char>(ch))",
        ))
        result = self.configure(build="build-paging-ctype-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)

    def test_stateful_processor_header_drift_is_not_silently_ignored(self):
        source = self.root / "librime/src/rime/gear/navigator.h"
        source.write_text(
            source.read_text().replace(
                "  connection select_connection_;",
                "  connection selection_connection_;",
            )
        )
        result = self.configure(build="build-navigator-drift")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Re-audit", result.stderr)


if __name__ == "__main__":
    unittest.main()
