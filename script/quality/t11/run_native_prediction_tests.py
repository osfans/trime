#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit, serial host compilation of the real Trime prediction overlays.

Requires host librime, marisa and glog. Never starts Gradle, Java or an emulator.
Run under an appropriate virtual-memory limit; each C++ translation unit is
compiled sequentially by one compiler command. Records failures unchanged.
"""

import argparse
import hashlib
import json
from pathlib import Path
import resource
import shutil
import subprocess
import time


ROOT = Path(__file__).resolve().parents[3]
NATIVE = ROOT / "app/src/main/jni"
OVERLAYS = NATIVE / "librime_jni/predict"
MODEL = ROOT / "app/src/main/assets/shared/trime_predict.db"
JNI = NATIVE / "librime_jni/rime_jni.cc"
PRIVACY_CMAKE = NATIVE / "cmake/RimePrivacyCompat.cmake"


def method(source, signature):
    """Extract a complete brace-balanced production method, without edits."""
    assert source.count(signature) == 1, "Missing/duplicate production method"
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        assert end < len(source), "Unclosed production method"
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def ranking_bridge(destination):
    jni, cmake = JNI.read_text(), PRIVACY_CMAKE.read_text()
    reset = method(cmake, "bool Session::ResetPredictionContexts(bool enabled, bool update_option)")
    reset = reset.replace("bool Session::ResetPredictionContexts", "bool RankingSessionFixture::ResetPredictionContexts", 1)
    methods = [method(jni, signature) for signature in (
        "bool clearPredictionContext()", "bool setPredictionEnabled(bool enabled)",
        "void setCandidatePins(const std::vector<std::string>& schemas,",
        "void applyCandidatePins()")]
    lookups = ("rime::Service::instance().GetSession(id)",
               "rime::Service::instance().GetSession(session(false))")
    # Production algorithms are unchanged. Typed lookup connects the private
    # JNI singleton's methods to this exclusively owned real Engine fixture.
    for lookup in lookups:
        methods = [body.replace(lookup, "current_") for body in methods]
    destination.write_text(
        '#pragma once\n#include "candidate_ranking.h"\n#include <map>\n'
        '#include <rime/engine.h>\n#include <rime/schema.h>\n'
        'using rime::Context;\nusing rime::Engine;\n'
        'class RankingSessionFixture {\n public:\n'
        '  RankingSessionFixture() : engine_(rime::Engine::Create()) {}\n'
        '  rime::Context* context() { return engine_->context(); }\n'
        '  rime::Schema* schema() { return engine_->schema(); }\n'
        '  bool ResetPredictionContexts(bool enabled, bool update_option);\n'
        ' private:\n  std::unique_ptr<rime::Engine> engine_;\n};\n' + reset + '\n'
        'class RankingBridgeFixture {\n public:\n'
        '  explicit RankingBridgeFixture(RankingSessionFixture* current) : current_(current) {}\n' +
        '\n'.join(methods) + '\n private:\n'
        '  int session(bool = true) { return 1; }\n'
        '  std::string currentSchemaId() { return current_->schema()->schema_id(); }\n'
        '  std::string rawInput() { return current_->context()->input(); }\n'
        '  RankingSessionFixture* current_;\n  bool privacy_mode_ = false;\n'
        '  rime::an<rime::Menu> ranked_menu_, original_menu_;\n'
        '  std::map<std::pair<std::string, std::string>, std::string> pins_;\n};\n')


def digest(path):
    data = path.read_bytes()
    return {"path": str(path), "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--compiler", default="c++")
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to((ROOT / "build/t11").resolve())
    out.mkdir(parents=True, exist_ok=False)
    include = out / "include/rime"
    include.mkdir(parents=True)
    (include / "build_config.h").write_text("#pragma once\n#define RIME_ENABLE_LOGGING\n")
    model = digest(MODEL)
    (out / "include/trime_predict_model_hash.h").write_text(
        "#pragma once\n#include <cstddef>\nnamespace trime::prediction {\n"
        f"inline constexpr std::size_t kBundledModelBytes = {model['bytes']};\n"
        f"inline constexpr char kBundledModelSha256[] = \"{model['sha256']}\";\n}}\n"
    )
    scratch = out / "runtime"
    scratch.mkdir()
    (scratch / "default.yaml").write_text("schema_list: []\n")
    generated = out / "generated"
    generated.mkdir()
    for path in OVERLAYS.iterdir():
        if path.suffix in {".cc", ".h"}:
            shutil.copyfile(path, generated / path.name)
    for name in ["predict_module.cc", "predict_translator.h"]:
        shutil.copyfile(NATIVE / "librime-predict/src" / name, generated / name)
    shutil.copyfile(OVERLAYS / "LICENSE", generated / "LICENSE")
    ranking_bridge(generated / "ranking_boundary_bridge.h")
    shutil.copyfile(MODEL, scratch / "trime_predict.db")
    sources = [Path(__file__).with_name("prediction_native_test.cc"),
               *(generated / name for name in ["predictor.cc", "predict_engine.cc",
                                               "predict_db.cc", "predict_translator.cc",
                                               "predict_module.cc"]),
               NATIVE / "librime_jni/trime_privacy_guard.cc",
               NATIVE / "librime_jni/trime_prediction_guard.cc"]
    inputs = [Path(__file__).resolve(), *sources, *OVERLAYS.glob("*.h"), *OVERLAYS.glob("*.cc"),
              OVERLAYS / "LICENSE", generated / "LICENSE",
              NATIVE / "librime-predict/src/predict_module.cc",
              NATIVE / "librime-predict/src/predict_translator.h",
              NATIVE / "librime_jni/trime_privacy_guard.h",
              NATIVE / "librime_jni/trime_prediction_guard.h", MODEL]
    inputs += [JNI, PRIVACY_CMAKE, NATIVE / "librime_jni/candidate_ranking.h",
               Path(__file__).with_name("ranking_boundary_test.h"),
               generated / "ranking_boundary_bridge.h",
               NATIVE / "librime/deps/opencc/deps/darts-clone-0.32h/include/darts.h"]
    before = [digest(path) for path in inputs]
    boost = [f"-isystem{path}" for path in sorted((NATIVE / "boost/libs").glob("*/include"))
             if path.is_dir()]
    binary = out / "prediction-native-test"
    command = [args.compiler, "-std=c++17", "-O0", "-g0", "-DNDEBUG",
               "-DGLOG_USE_GLOG_EXPORT", "-Wall", "-Wextra", "-Werror",
               "-Wno-unused-parameter", "-Wl,--build-id=none",
               f"-I{out / 'include'}", f"-I{generated}", f"-I{NATIVE / 'librime_jni'}",
               f"-isystem{NATIVE / 'librime/src'}", f"-isystem{NATIVE / 'librime/include'}",
               f"-isystem{NATIVE / 'librime-predict/src'}",
               f"-isystem{NATIVE / 'librime/deps/opencc/deps/darts-clone-0.32h/include'}",
               *boost, *map(str, sources), "-lrime", "-lmarisa", "-lglog", "-ldl", "-o", str(binary)]
    summary = {"started_at": time.time(), "model": model,
               "compile_command": command, "inputs_before": before,
               "compile_exit": None, "test_exit": None}
    with (out / "compile.log").open("wb") as log:
        compiled = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
    summary["compile_exit"] = compiled.returncode
    if compiled.returncode == 0:
        with (out / "test.log").open("wb") as log:
            tested = subprocess.Popen([str(binary), str(MODEL), str(scratch)],
                                      stdout=log, stderr=subprocess.STDOUT)
            summary["test_pid"] = tested.pid
            try:
                summary["test_exit"] = tested.wait(timeout=60)
                summary["test_timed_out"] = False
            except subprocess.TimeoutExpired:
                tested.kill()
                summary["test_exit"] = tested.wait()
                summary["test_timed_out"] = True
    summary["child_max_rss_kib"] = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
    summary["inputs_after"] = [digest(path) for path in inputs]
    summary["inputs_unchanged"] = before == summary["inputs_after"]
    summary["finished_at"] = time.time()
    summary["accepted"] = (summary["compile_exit"] == summary["test_exit"] == 0
                           and summary["inputs_unchanged"])
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({key: summary[key] for key in
                     ["compile_exit", "test_exit", "child_max_rss_kib", "accepted"]}))
    return 0 if summary["accepted"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
