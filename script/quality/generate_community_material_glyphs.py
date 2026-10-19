#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Offline, fixed-version extraction of Community Material cmd names and UTF-16 glyphs.

No download, eval, Kotlin execution, SDK or JVM. Use --check against the one
production raw resource. Generation creates a new output and never overwrites it.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import zipfile

COORDINATE = "com.mikepenz:community-material-typeface:7.0.96.1-kotlin"
SOURCE_JAR_SHA256 = "bf57c0be4b22c1a7ccad2314789fb13db445ecd6e4b18848e10398d64e91f598"
SOURCE_ENTRY = "main/com/mikepenz/iconics/typeface/library/community/material/CommunityMaterial.kt"
SOURCE_TEXT_SHA256 = "3084addcb731bb75cc666036875e6f34c967ace0a5ba9a23833d4c5fc9cae3f4"
RESOURCE_SHA256 = "d060a97a02f3639794fb847a43b7d9b6a773e021900659507fe0a0f3f561709f"
COUNT = 7134


def require(condition, message):
    if not condition:
        raise ValueError(message)


def extract(jar_bytes):
    require(hashlib.sha256(jar_bytes).hexdigest() == SOURCE_JAR_SHA256,
            "Source jar SHA differs; a library upgrade needs an explicit reviewed version change")
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as archive:
        entries = [info for info in archive.infolist() if info.filename == SOURCE_ENTRY]
        # The fixed upstream jar has two directory records pointing to one physical
        # entry. Verify that identity, then read it once through the final name entry.
        # Do not disable Python's overlap/bomb checks or read the zero-span alias.
        physical = [(info.header_offset, info.compress_size, info.file_size, info.CRC,
                     info.flag_bits, info.compress_type) for info in entries]
        require(len(entries) == 2 and len(set(physical)) == 1, "Missing or ambiguous source entry")
        source = archive.read(SOURCE_ENTRY)
    require(hashlib.sha256(source).hexdigest() == SOURCE_TEXT_SHA256, "Source entry bytes differ")
    pattern = r"^        (cmd_[a-zA-Z0-9_]+)\(" + re.escape("'" + chr(92) + "u") + r"([0-9a-fA-F]{4})'\)"
    pairs = re.findall(pattern, source.decode("utf-8"), re.MULTILINE)
    require(len(pairs) == COUNT, "Incomplete source mapping")
    names = [name for name, _ in pairs]
    require(len(set(names)) == COUNT and names == sorted(names), "Names are duplicate or not in original sorted order")
    for _, value in pairs:
        code = int(value, 16)
        require(code <= 0xFFFF and not 0xD800 <= code <= 0xDFFF,
                "IIcon.character cannot represent supplementary or surrogate glyphs")
    resource = "".join(name + "\t" + value.upper() + "\n" for name, value in pairs).encode("ascii")
    require(hashlib.sha256(resource).hexdigest() == RESOURCE_SHA256, "Generated mapping differs")
    return resource


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    require(args.source_jar.is_file() and not args.source_jar.is_symlink(), "Regular local source jar required")
    resource = extract(args.source_jar.read_bytes())
    if args.check:
        require(args.output.is_file() and not args.output.is_symlink(), "Regular existing output required")
        require(args.output.read_bytes() == resource, "Existing raw resource does not match fixed upstream source")
    else:
        with args.output.open("xb") as output:
            output.write(resource)
    print(json.dumps(dict(coordinate=COORDINATE, action="check" if args.check else "generate",
                          source_jar_sha256=SOURCE_JAR_SHA256, glyph_count=COUNT,
                          output=str(args.output), bytes=len(resource), sha256=RESOURCE_SHA256)))


if __name__ == "__main__":
    main()
