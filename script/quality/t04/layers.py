#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build bounded, version-pinned dictionary experiments, never user configuration."""
import argparse
import hashlib
import heapq
import json
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
COMMIT = "fbb516b2786e4d5444383706d13c31c2e4d10c08"
URL = f"https://raw.githubusercontent.com/iDvel/rime-ice/{COMMIT}/"
SOURCES = ["LICENSE", "README.md", "cn_dicts/8105.dict.yaml",
           "cn_dicts/base.dict.yaml", "cn_dicts/ext.dict.yaml"]


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def entries(path):
    in_body = False
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            if not in_body:
                in_body = line.strip() == "..."
                continue
            if not line.strip() or line.startswith("#"):
                continue
            fields = line.rstrip("\n").split("\t")
            if len(fields) != 3 or not re.fullmatch(r"[a-z]+(?: [a-z]+)*", fields[1]):
                continue
            word, code, weight = fields
            if not re.fullmatch(r"\d+", weight):
                continue
            if len(word) != len(code.split()) or not all("\u3400" <= c <= "\u9fff" for c in word):
                continue
            yield word, code, int(weight)


def selected(path, limit, min_length, max_length):
    # Selection never reads the benchmark. Hash ties avoid alphabetical topic bias.
    rows = (row for row in entries(path) if min_length <= len(row[0]) <= max_length)
    return heapq.nsmallest(limit, rows,
                          key=lambda r: (-r[2], hashlib.sha256((r[0] + r[1]).encode()).hexdigest()))


def generate(cache, out):
    if out.exists():
        raise ValueError("Output must be new; generated packs are immutable")
    cache.mkdir(parents=True, exist_ok=True)
    for relative in SOURCES:
        target = cache / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            with urllib.request.urlopen(URL + relative, timeout=60) as response, target.open("xb") as stream:
                shutil.copyfileobj(response, stream)
    hashes = {name: sha(cache / name) for name in SOURCES}
    lock_file = Path(__file__).with_name("sources.lock.json")
    lock = json.loads(lock_file.read_text())
    if lock["commit"] != COMMIT or lock["files"] != hashes:
        raise ValueError("Pinned source checksum mismatch")
    data = ROOT / "app/src/main/jni/OpenCC/data"
    data_hashes = {name: sha(data / "dictionary" / name) for name in lock["OpenCC"]["data_sha256"]}
    conversion_commit = subprocess.run(["git", "rev-parse", "HEAD"], cwd=data.parent,
                                       capture_output=True, text=True, check=True).stdout.strip()
    if data_hashes != lock["OpenCC"]["data_sha256"] or conversion_commit != lock["OpenCC"]["commit"]:
        raise ValueError("Pinned OpenCC data mismatch")
    out.mkdir(parents=True)
    conversion = {
        "name": "Pinned s2t dictionary conversion", "segmentation": {"type": "mmseg", "dict": {
            "type": "text", "file": str(data / "dictionary/STPhrases.txt")}},
        "conversion_chain": [{"dict": {"type": "group", "dicts": [
            {"type": "text", "file": str(data / "dictionary/STPhrases.txt")},
            {"type": "text", "file": str(data / "dictionary/STCharacters.txt")} ]}}]}
    config = out / "conversion.json"
    config.write_text(json.dumps(conversion), encoding="utf-8")
    layers = {}
    for name, source, limit, shortest, longest in [
        ("chars", "8105", 15000, 1, 1), ("base", "base", 10000, 2, 6),
        ("ext", "ext", 2000, 3, 6)]:
        rows = selected(cache / f"cn_dicts/{source}.dict.yaml", limit, shortest, longest)
        converted = subprocess.run(["opencc", "-c", str(config)],
                                   input="\n".join(r[0] for r in rows) + "\n",
                                   text=True, capture_output=True, check=True).stdout.splitlines()
        if len(rows) != len(converted):
            raise ValueError("Conversion changed entry count")
        unique = {}
        for word, (_, code, weight) in zip(converted, rows):
            if len(word) != len(code.split()):
                raise ValueError("Conversion changed syllable count")
            unique[word, code] = max(weight, unique.get((word, code), 0))
        layers[name] = [(word, code, weight) for (word, code), weight in sorted(unique.items())]
        # Preserve the exact upstream header, including nested source attribution.
        header = (cache / f"cn_dicts/{source}.dict.yaml").read_text(encoding="utf-8").split("\n---\n", 1)[0]
        (out / f"UPSTREAM-{name}.txt").write_text(header + "\n", encoding="utf-8")
    for profile, names, scale in [
        ("common", ["chars", "base"], 1),
        ("extended", ["chars", "base", "ext"], 1),
        ("balanced", ["chars", "base", "ext"], 0.1),
        ("compact", ["chars", "base", "ext"], 1)]:
        folder = out / profile
        folder.mkdir()
        dictionary = "luna_pinyin.t9_" + profile
        body = ["# Generated by script/quality/t04/layers.py; see ../provenance.json.",
                "# Derived from iDvel/rime-ice; GPL-3.0, upstream notices retained.",
                "---", f"name: {dictionary}", f'version: "{COMMIT[:12]}-v1"',
                "sort: by_weight"]
        body.extend(["use_preset_vocabulary: false", "import_tables: []"] if profile == "compact"
                    else ["use_preset_vocabulary: true", "import_tables:", "  - luna_pinyin"])
        body.append("...")
        for name in names:
            body.extend(f"{word}\t{code}\t{max(1, round(weight * scale))}"
                        for word, code, weight in layers[name])
        (folder / (dictionary + ".dict.yaml")).write_text("\n".join(body) + "\n", encoding="utf-8")
        patch = {"patch": {"translator/dictionary": dictionary,
                            "translator/user_dict": "luna_pinyin",
                            "translator/enable_user_dict": True,
                            "translator/prism": "luna_pinyin_t9_" + profile}}
        # JSON is a YAML subset; no ad-hoc patching of the existing schema.
        (folder / "luna_pinyin_t9.custom.yaml").write_text(json.dumps(patch, indent=2) + "\n")
    shutil.copyfile(cache / "LICENSE", out / "LICENSE.rime-ice")
    shutil.copyfile(data.parent / "LICENSE", out / "LICENSE.OpenCC")
    shutil.copyfile(data.parent / "AUTHORS", out / "AUTHORS.OpenCC")
    config.unlink()  # Machine-specific absolute paths are not part of a portable pack.
    provenance = {"repository": "https://github.com/iDvel/rime-ice", "commit": COMMIT,
                  "files": hashes, "generator_sha256": sha(__file__),
                  "selection": "8105 BMP readings; highest-frequency 10000 base / 2000 ext; 2-6 / 3-6 Hanzi; SHA-256 tie-break; no corpus access",
                  "rows": {k: len(v) for k, v in layers.items()},
                  "conversion": {"tool_version": subprocess.run(["opencc", "--version"],
                                  capture_output=True, text=True, check=True).stdout.strip(),
                                 "tool_sha256": sha(shutil.which("opencc")),
                                 "OpenCC_commit": conversion_commit,
                                 "data_sha256": data_hashes},
                  "scope": "Experimental dictionary-only packs; not desktop configuration or a grammar model"}
    provenance["outputs"] = {str(p.relative_to(out)): sha(p) for p in sorted(out.rglob("*")) if p.is_file()}
    (out / "provenance.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n")
    print(out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, default=ROOT / "build/t04/upstream" / COMMIT)
    parser.add_argument("--out", type=Path, default=ROOT / "build/t04/packs-v1")
    args = parser.parse_args()
    generate(args.cache, args.out)
