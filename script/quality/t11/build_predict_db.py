#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build/check the original, bounded offline librime-predict example model.

No Android/Gradle build is started. --write needs a host native builder; compile
it explicitly with --compile-native (one c++ process, host librime/marisa/glog).
The pinned predict_db.cc creates the DB twice and reads every expected result.
Only identical, structurally valid images are published to the asset directory.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
SOURCE = HERE / "prediction.tsv"
ASSET = ROOT / "app/src/main/assets/shared/trime_predict.db"
MANIFEST = ASSET.with_suffix(".db.json")
MAX_MODEL_BYTES = 64 * 1024
MAX_ROWS = 128
MAX_PER_QUERY = 32
MAX_TEXT_CHARS = 12
FORMAT = b"Rime::Predict/1.0"
HAN = re.compile(r"[\u3400-\u9fff]+\Z")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_source(raw: str) -> dict[str, list[tuple[str, int]]]:
    """Reject incomplete/duplicate/unbounded input before upstream's builder."""
    data: dict[str, list[tuple[str, int]]] = {}
    rows = 0
    for number, line in enumerate(raw.splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 3:
            raise ValueError(f"line {number}: expected three tab-separated fields")
        query, text, weight = fields
        if any(not HAN.fullmatch(value) or len(value) > MAX_TEXT_CHARS for value in (query, text)):
            raise ValueError(f"line {number}: only bounded Han text is allowed")
        if not re.fullmatch(r"[1-9][0-9]{0,5}", weight):
            raise ValueError(f"line {number}: expected a positive bounded integer weight")
        entries = data.setdefault(query, [])
        if any(candidate == text for candidate, _ in entries):
            raise ValueError(f"line {number}: duplicate next word for query")
        value = int(weight)
        if entries and entries[-1][1] <= value:
            raise ValueError(f"line {number}: weights must decrease for each query")
        entries.append((text, value))
        rows += 1
        if rows > MAX_ROWS or len(entries) > MAX_PER_QUERY:
            raise ValueError("example model exceeds the row/candidate budget")
    if not data:
        raise ValueError("example model is empty")
    return dict(sorted(data.items(), key=lambda item: item[0].encode("utf-8")))


def normalized_input(data: dict[str, list[tuple[str, int]]]) -> bytes:
    return "".join(f"{query}\t{text}\t{weight}\n" for query, entries in data.items()
                   for text, weight in entries).encode("utf-8")


def inspect_image(image: bytes, data: dict[str, list[tuple[str, int]]]) -> dict[str, int]:
    """Check bounds before the host native reader; never dereference raw offsets."""
    if not 52 < len(image) <= MAX_MODEL_BYTES:
        raise ValueError("model byte budget or header size is invalid")
    header, checksum, key_relative, key_units, value_relative, value_bytes = struct.unpack_from("<32sIiIiI", image)
    if header != FORMAT.ljust(32, b"\0") or checksum != 0:
        raise ValueError("expected exact Rime::Predict/1.0 format and unused checksum")
    key_start = 36 + key_relative
    value_start = 44 + value_relative
    if (key_start < 52 or key_start % 4 or key_units == 0 or
            key_units > MAX_MODEL_BYTES // 4 or
            key_start + key_units * 4 != value_start or value_bytes == 0 or
            value_start + value_bytes != len(image)):
        raise ValueError("invalid trie/string-table bounds")
    if not image[value_start:].startswith(b"We love Marisa."):
        raise ValueError("expected the native MARISA string table")
    units = struct.unpack_from(f"<{key_units}I", image, key_start)

    def unit_at(index: int) -> int:
        if not 0 <= index < key_units:
            raise ValueError("query traversal exceeds key trie")
        return units[index]

    def offset(unit: int) -> int:
        return (unit >> 10) << ((unit & (1 << 9)) >> 6)

    def lookup(query: str) -> int | None:
        position, unit = 0, unit_at(0)
        for label in query.encode("utf-8"):
            position ^= offset(unit) ^ label
            unit = unit_at(position)
            if unit & ((1 << 31) | 255) != label:
                return None
        if not unit & (1 << 8):
            return None
        return unit_at(position ^ offset(unit)) & 0x7fffffff

    arrays: list[tuple[int, int]] = []
    distinct_texts = len({text for entries in data.values() for text, _ in entries})
    for query, expected in data.items():
        start = lookup(query)
        if start is None or start < 52 or start % 4 or start + 4 > key_start:
            raise ValueError("query does not resolve to a bounded candidate array")
        count, = struct.unpack_from("<I", image, start)
        end = start + 4 + count * 8
        if count != len(expected) or not 0 < count <= MAX_PER_QUERY or end > key_start:
            raise ValueError("candidate array count/bounds differ from the source")
        arrays.append((start, end))
        for index, (_, weight) in enumerate(expected):
            string_id, actual_weight = struct.unpack_from("<If", image, start + 4 + index * 8)
            if string_id >= distinct_texts or not math.isfinite(actual_weight) or actual_weight != weight:
                raise ValueError("invalid candidate string ID/weight")
    cursor = 52
    for start, end in sorted(arrays):
        if start != cursor:
            raise ValueError("candidate arrays overlap or leave unexpected data")
        cursor = end
    if cursor != key_start or lookup("$") is not None or lookup("不存在的上下文") is not None:
        raise ValueError("unexpected candidate payload/start-of-session prediction")
    return {"query_count": len(data), "next_word_count": sum(map(len, data.values())),
            "key_units": key_units, "string_table_bytes": value_bytes}


def metadata(image: bytes, data: dict[str, list[tuple[str, int]]]) -> dict:
    return {"format": FORMAT.decode("ascii"), "license": "CC0-1.0",
            "provenance": "Original hand-authored offline examples; no user or collected corpus data",
            "source": "script/quality/t11/prediction.tsv", "source_sha256": digest(SOURCE.read_bytes()),
            "database": ASSET.name, "sha256": digest(image), "size": len(image),
            "max_model_bytes": MAX_MODEL_BYTES, "default_max_candidates": 5,
            "default_max_iterations": 1, "initial_prediction": False,
            **inspect_image(image, data)}


def compile_native(builder: Path, compiler: str) -> None:
    builder.parent.mkdir(parents=True, exist_ok=True)
    include = builder.parent / "include/rime"
    include.mkdir(parents=True, exist_ok=True)
    (include / "build_config.h").write_text("#pragma once\n#define RIME_ENABLE_LOGGING\n", encoding="ascii")
    native = ROOT / "app/src/main/jni"
    # Android's configured Boost tree uses per-library include directories.
    # A host with packaged Boost can use /usr/include without this tree.
    boost_includes = [f"-I{path}" for path in sorted((native / "boost/libs").glob("*/include")) if path.is_dir()]
    command = [compiler, "-std=c++17", "-O0", "-g0", "-DNDEBUG", "-DGLOG_USE_GLOG_EXPORT", "-Wl,--build-id=none",
               f"-I{include.parent}", f"-I{native / 'librime/src'}", f"-I{native / 'librime/include'}",
               f"-I{native / 'librime-predict/src'}",
               f"-I{native / 'librime/deps/opencc/deps/darts-clone-0.32h/include'}",
               *boost_includes,
               str(HERE / "build_predict_db.cc"), str(native / "librime-predict/src/predict_db.cc"),
               "-lrime", "-lmarisa", "-lglog", "-o", str(builder)]
    subprocess.run(command, check=True)


def run_builder(builder: Path, mode: str, target: Path, data: dict) -> None:
    subprocess.run([str(builder.resolve()), mode, str(target.resolve())],
                   input=normalized_input(data), check=True, timeout=30)


def write_model(builder: Path, data: dict) -> None:
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="trime-predict-", dir=ASSET.parent) as directory:
        first, second = Path(directory) / "first.db", Path(directory) / "second.db"
        for target in (first, second):
            run_builder(builder, "--build", target, data)
            inspect_image(target.read_bytes(), data)
            run_builder(builder, "--verify", target, data)
        image = first.read_bytes()
        if image != second.read_bytes():
            raise ValueError("native builder output is not reproducible")
        descriptor = metadata(image, data)
        descriptor_path = Path(directory) / "model.json"
        descriptor_path.write_text(json.dumps(descriptor, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.replace(first, ASSET)
        os.replace(descriptor_path, MANIFEST)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-builder", type=Path)
    parser.add_argument("--compile-native", action="store_true", help="explicit single-process host compilation")
    parser.add_argument("--compiler", default="c++")
    parser.add_argument("--write", action="store_true", help="regenerate DB twice, verify, then publish assets")
    args = parser.parse_args()
    if (args.compile_native or args.write) and args.native_builder is None:
        parser.error("--compile-native/--write requires --native-builder")
    data = read_source(SOURCE.read_text(encoding="utf-8"))
    if args.compile_native:
        compile_native(args.native_builder, args.compiler)
    if args.write:
        write_model(args.native_builder, data)
    image = ASSET.read_bytes()
    expected = metadata(image, data)
    if json.loads(MANIFEST.read_text(encoding="utf-8")) != expected:
        raise ValueError("committed model manifest differs from the actual source/database")
    if args.native_builder is not None:
        run_builder(args.native_builder, "--verify", ASSET, data)
    print(json.dumps({"passed": True, **expected}, ensure_ascii=False))


if __name__ == "__main__":
    main()
