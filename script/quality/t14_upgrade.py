#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Retained-data APK upgrade on one already-owned API 21/35 emulator.

This is a local gate, not a standalone emulator controller. --probe-source must
name an independently reviewed, SHA-bound Probe implementation with check(),
verify_guest(), command(), shell(), commands, ready, report and minimum. The
currently reviewed T13 implementations require runtime/output below build/t13;
that local dependency is explicit and is never downloaded or modified here.

No build, downgrade, uninstall, data clearing, publication or cross-package
migration occurs. Fixture preferences/configuration are written only in a fresh
dedicated sandbox; learning and lookup use the installed APK's real JNI. A pass
does not assert keyboard UI, SAF grants, physical devices or release approval.
"""

import argparse
import base64
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import resource
import signal
import time
import traceback
from types import SimpleNamespace
import uuid
import zlib
import xml.etree.ElementTree as ET

PACKAGE = "com.osfans.trime"
IME = PACKAGE + "/com.osfans.trime.ime.core.TrimeInputMethodService"
EDITOR = PACKAGE + "/com.osfans.trime.ui.main.ClipEditActivity"
NATIVE_ENTRY = PACKAGE + ".test/com.osfans.trime.UserDictionaryUpgradeInstrumentation"
EXTERNAL = "/sdcard/Android/data/" + PACKAGE + "/files/"
CACHE_BOUNDARY_FILE = "t10-upgrade-cache-boundary.json"
MAX_CACHE_BYTES = 64 * 1024
PREFERENCE_FIXTURE = {
    "candidate_text_size_override": ("int", "30"),
    "keyboard_height_override": ("int", "300"),
    "candidate_show_comments": ("boolean", "false"),
    "candidate_horizontal_scroll": ("boolean", "true"),
    "t9_one_hand": ("string", "RIGHT"),
}
# Only the crash-log process identity is volatile at ordinary app startup.
# No general timestamp/version-key exclusion is allowed.
VOLATILE_PREFERENCE = "general__pid"
REQUIRED_CHECKS = frozenset({
    "fresh_actual_old_and_test_install",
    "actual_old_native_learning_and_stopped_fixture",
    "strictly_newer_normal_release_install",
    "retained_uid_and_data_directory",
    "retained_selected_enabled_ime_and_subtype",
    "retained_nondefault_preferences_and_config",
    "retained_exact_native_state_metadata",
    "new_native_stopped_bytes_full_t9_learning_counters_and_three_backups",
    "final_release_identity_ime_and_nondefault_data",
})


def package_components(package):
    require(isinstance(package, str) and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+", package),
            "Explicit valid tested application package required")
    return dict(package=package, ime=package + "/com.osfans.trime.ime.core.TrimeInputMethodService",
                editor=package + "/com.osfans.trime.ui.main.ClipEditActivity",
                native_entry=package + ".test/com.osfans.trime.UserDictionaryUpgradeInstrumentation",
                external="/sdcard/Android/data/" + package + "/files/")


def require(value, message):
    if not value:
        raise RuntimeError(message)


def cache_paths(prefix):
    require(isinstance(prefix, str) and re.fullmatch(r"__t10_upgrade_[a-f0-9]{32}", prefix),
            "Untrusted cache fixture prefix")
    return ["build/" + prefix + "_" + suffix + "." + extension
            for suffix in ("full", "t9") for extension in ("schema.yaml", "prism.bin")]


def require_owned_tree(prefix, tree):
    """Keep every stopped fixture file; LevelDB table counts are not fixed."""
    require(isinstance(prefix, str) and re.fullmatch(r"__t10_upgrade_[a-f0-9]{32}", prefix),
            "Untrusted owned fixture prefix")
    fixed = set(cache_paths(prefix)) | {
        prefix + ".dict.yaml", prefix + "_full.schema.yaml", prefix + "_t9.schema.yaml",
        "build/" + prefix + ".table.bin", "build/" + prefix + ".reverse.bin",
    }
    require(isinstance(tree, dict) and fixed.issubset(tree), "Required owned fixture files missing")
    database = set()
    for path, digest in tree.items():
        require(isinstance(path, str) and isinstance(digest, str) and
                re.fullmatch(r"[a-f0-9]{64}", digest), "Invalid owned path or SHA-256")
        if path in fixed:
            continue
        stem = prefix + ".userdb/"
        require(path.startswith(stem), "File is outside the owned fixture")
        name = path[len(stem):]
        if name not in ("CURRENT", "LOCK", "LOG", "LOG.old"):
            match = re.fullmatch(r"(?:MANIFEST-([0-9]+)|([0-9]+)\.(?:log|sst|ldb|dbtmp))", name)
            require(match is not None, "Unknown or nested LevelDB filename")
            number = (match[1] or match[2]).lstrip("0") or "0"
            require(len(number) < 20 or (len(number) == 20 and number <= "18446744073709551615"),
                    "LevelDB file number exceeds uint64")
        database.add(name)
    require({"CURRENT", "LOCK", "LOG"}.issubset(database) and
            any(re.fullmatch(r"MANIFEST-[0-9]+", name) for name in database) and
            any(re.fullmatch(r"[0-9]+\.(?:ldb|sst)", name) for name in database) and
            any(re.fullmatch(r"[0-9]+\.log", name) for name in database),
            "Required stopped LevelDB structure missing")
    return tree


def require_stopped_install_tree(state, boundary, actual):
    expected = dict(require_owned_tree(state["prefix"], state["owned_tree"]))
    require(set(boundary["cache_files"]) == set(cache_paths(state["prefix"])),
            "Exactly four cache boundary paths required")
    expected.update({path: item["after_sha256"] for path, item in boundary["cache_files"].items()})
    require(actual == expected, "Native verified complete stopped install tree differs")


def schema_timestamp_mask(raw):
    """Preserve every original byte except canonical root build timestamp digits."""
    require(isinstance(raw, bytes) and 0 < len(raw) <= MAX_CACHE_BYTES and b"\r" not in raw,
            "Unexpected compiled schema bytes")
    raw.decode("utf-8")
    result, keys = [], set()
    in_build = in_timestamps = seen_build = seen_timestamps = False
    pieces = raw.split(b"\n")
    for index, body in enumerate(pieces):
        line = body + (b"\n" if index < len(pieces) - 1 else b"")
        if line == b"__build_info:\n":
            require(not seen_build, "Duplicate root build metadata")
            in_build = seen_build = True
        elif line and line[:1] not in (b" ", b"\t", b"\n"):
            in_build = in_timestamps = False
        if in_build and line == b"  timestamps:\n":
            require(not seen_timestamps, "Duplicate build timestamps")
            in_timestamps = seen_timestamps = True
        elif in_timestamps:
            if line.startswith(b"    "):
                match = re.fullmatch(rb"    ([A-Za-z0-9_.-]+): (0|[1-9][0-9]*)(\n?)", line)
                require(match is not None and match[1] not in keys and len(match[2]) <= 19 and
                        int(match[2]) <= 9223372036854775807, "Noncanonical build timestamp")
                keys.add(match[1])
                line = b"    " + match[1] + b": <timestamp>" + match[3]
            else:
                in_timestamps = False
        result.append(line)
    require(seen_build and seen_timestamps and keys, "Root build timestamps missing")
    return b"".join(result)


def cache_boundary(state, state_raw, before, after):
    """Create independently verifiable metadata-only handoff; never edit state."""
    require(json.loads(state_raw) == state, "Native state bytes differ")
    paths = cache_paths(state["prefix"])
    require_owned_tree(state["prefix"], state["owned_tree"])
    require(set(before) == set(after) == set(paths), "Exactly four registered caches required")
    rows = {}
    for suffix in ("full", "t9"):
        schema = "build/" + state["prefix"] + "_" + suffix + ".schema.yaml"
        prism = "build/" + state["prefix"] + "_" + suffix + ".prism.bin"
        require(schema_timestamp_mask(before[schema]) == schema_timestamp_mask(after[schema]),
                "Compiled schema changed outside root numeric build timestamps")
        left, right = before[prism], after[prism]
        header = b"Rime::Prism/4.0".ljust(32, b"\0")
        require(isinstance(left, bytes) and isinstance(right, bytes) and 40 <= len(left) <= MAX_CACHE_BYTES and
                len(left) == len(right) and left[:32] == right[:32] == header and
                left[:36] == right[:36] and left[40:] == right[40:],
                "Prism format, dictionary checksum or payload changed")
        require(left[36:40] == right[36:40] or before[schema] != after[schema],
                "Prism schema checksum changed without paired timestamp-only schema change")
        require(int.from_bytes(left[36:40], "little") == zlib.crc32(before[schema], 0xffffffff) and
                int.from_bytes(right[36:40], "little") == zlib.crc32(after[schema], 0xffffffff),
                "Prism schema checksum does not match its exact paired schema bytes")
        for path in (schema, prism):
            first, last = before[path], after[path]
            first_sha, last_sha = (hashlib.sha256(raw).hexdigest() for raw in (first, last))
            require(first_sha == state["owned_tree"][path], "Old cache bytes differ from immutable learning-origin state")
            rows[path] = dict(before_sha256=first_sha, after_sha256=last_sha,
                              before_base64=base64.b64encode(first).decode("ascii"),
                              after_base64=base64.b64encode(last).decode("ascii"))
    return dict(format_version=1, prefix=state["prefix"], package=state["package"], uid=state["uid"],
                old_version_code=state["old_version_code"], old_version_name=state["old_version_name"],
                state_sha256=hashlib.sha256(state_raw).hexdigest(), cache_files=rows)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def save(path, value):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def bound(path):
    require(path.is_absolute() and path.resolve() == path and path.is_file() and not path.is_symlink(),
            "Canonical regular input required: " + str(path))
    before = path.stat()
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            digest.update(block)
    after = path.stat()
    stamp = lambda stat: (stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns, stat.st_mode)
    require(stamp(before) == stamp(after), "Input changed while hashing")
    return dict(path=str(path), bytes=after.st_size, sha256=digest.hexdigest())


def read_json(path):
    require(path.stat().st_size <= 2 * 1024 * 1024, "Control JSON exceeds 2 MiB")

    def unique(pairs):
        value = {}
        for key, item in pairs:
            require(key not in value, "Duplicate JSON field: " + key)
            value[key] = item
        return value

    return json.loads(path.read_bytes(), object_pairs_hook=unique)


def preference_values(raw):
    require(len(raw) <= 1024 * 1024, "Preference fixture exceeds 1 MiB")
    root = ET.fromstring(raw)
    require(root.tag == "map", "Expected SharedPreferences map")
    values = {}
    for item in root:
        key = item.get("name")
        require(key and key not in values, "Missing/duplicate preference name")
        if item.tag == "string":
            value = item.text or ""
        elif item.tag == "set":
            require(all(child.tag == "string" for child in item), "Invalid preference set")
            value = sorted(child.text or "" for child in item)
        else:
            require(item.tag in ("int", "long", "float", "boolean") and item.get("value") is not None,
                    "Unknown preference type")
            value = item.get("value")
        values[key] = (item.tag, value)
    return values


def seeded_preferences(raw):
    preference_values(raw)
    root = ET.fromstring(raw)
    for child in list(root):
        if child.get("name") in PREFERENCE_FIXTURE:
            root.remove(child)
    for key, (kind, value) in PREFERENCE_FIXTURE.items():
        item = ET.SubElement(root, kind, name=key)
        if kind == "string":
            item.text = value
        else:
            item.set("value", value)
    result = ET.tostring(root, encoding="utf-8", xml_declaration=True) + b"\n"
    require(all(preference_values(result)[key] == value for key, value in PREFERENCE_FIXTURE.items()),
            "Non-default preference fixture differs")
    return result


def require_install_success(raw):
    # adb push installs on API 21 retain CRLF from PackageManager. Capture the
    # token, not its optional line ending; preserve the original stdout receipt.
    require(re.findall(rb"(?m)^(Success)\r?$", raw) == [b"Success"] and b"Failure [" not in raw,
            "Actual package installer did not uniquely report success")


def require_preserved_preferences(before, after):
    left = {key: value for key, value in before.items() if key != VOLATILE_PREFERENCE}
    right = {key: value for key, value in after.items() if key != VOLATILE_PREFERENCE}
    require(left == right, "A nonvolatile preference changed across upgrade/restart")


def require_registered_fixture(state, registered_config):
    """Bind normal schema registration and return the separate cleanup baseline."""
    require(state.get("format_version") == 3 and state.get("diagnostic_only") is not True,
            "A completed enabled-schema preparation is required")
    prefix = state.get("prefix")
    require(isinstance(prefix, str) and re.fullmatch(r"__t10_upgrade_[a-f0-9]{32}", prefix),
            "Untrusted owned schema prefix")
    original = state.get("original_enabled_schema_ids")
    require(isinstance(original, list) and original and all(isinstance(x, str) and x for x in original) and
            len(set(original)) == len(original) and not any(x.startswith(prefix) for x in original),
            "Original enabled schema list is invalid")
    expected = original + [prefix + "_full", prefix + "_t9"]
    registration = state.get("enabled_schema_registration", {})
    require(state.get("registered_enabled_schema_ids") == expected and
            registration.get("expected_ids") == registration.get("actual_ids") == expected and
            registration.get("ordinary_maintenance_readback") is True and
            registration.get("normal_startup_full_check") is False,
            "Owned full/T9 schemas were not read back after normal old-APK maintenance")

    def decode(value):
        require(isinstance(value, str) and len(value) <= 2 * 1024 * 1024, "Invalid saved configuration bytes")
        try:
            data = base64.b64decode(value, validate=True)
        except ValueError as error:
            raise RuntimeError("Invalid saved configuration encoding") from error
        require(len(data) <= 1024 * 1024, "Saved configuration exceeds fixture bound")
        return data

    require(registered_config and decode(state.get("registered_default_custom")) == registered_config and
            registration.get("default_custom_sha256") == hashlib.sha256(registered_config).hexdigest(),
            "Registered default customization differs from actual old-APK bytes")
    existed = state.get("original_default_custom_existed")
    require(type(existed) is bool and (existed or state.get("original_default_custom") is None),
            "Original configuration existence is ambiguous")
    return decode(state.get("original_default_custom")) if existed else None


def focused_window(raw):
    """Read current window focus, never an activity listing or an IME target."""
    fields = re.findall(r"(?m)^\s*mCurrentFocus=([^\r\n]+)\r?$", raw.decode("utf-8"))
    if len(fields) != 1:
        return None
    match = re.fullmatch(r"Window\{([0-9a-f]+) u([0-9]+) ([^{}]+)\}", fields[0])
    if match is None:
        return None
    return dict(window=match[1], user=int(match[2]), title=match[3])


def require_no_ui_error(window_raw, processes_raw=b""):
    focus = focused_window(window_raw)
    if focus is not None:
        require(not re.match(r"(?:Application Not Responding|Application Error):(?:\s|$)", focus["title"]),
                "Foreground Android ANR/application error dialog: " + focus["title"])
    # These are current process flags from dumpsys activity processes, not
    # historical ANR descriptions or arbitrary occurrences of the word ANR.
    # API21 starts this record with debugging=; API35 starts with mCrashing=.
    # The ANR flag follows other fields on the same line on both platforms.
    records = re.findall(r"(?m)^\s*(?:debugging|mCrashing)=(?:true|false)\b[^\r\n]*",
                         processes_raw.decode("utf-8"))
    require(not any(re.search(r"(?<!\S)(?:mCrashing|mNotResponding|crashing|notResponding)=true(?=\s|$)",
                              line.lstrip()) for line in records),
            "Android process is in an active crash/ANR state")
    return focus


def focused_editor_window(raw, editor=EDITOR):
    focus = require_no_ui_error(raw)
    if focus is None or focus["user"] != 0 or focus["title"] != editor:
        return None
    return focus


def bound_ime_state(raw, api, ime=IME):
    """Recognize the real API21/35 manager and service dump, not its IME list."""
    text = raw.decode("utf-8")
    connection = "mHaveConnect" if api == 21 else "mHaveConnection"
    current = re.findall(r"(?m)^\s*mCurMethodId=(\S+)\s*$", text)
    bindings = re.findall(r"(?m)^\s*mCurId=(\S+) " + connection +
                          r"=(true|false) mBoundToMethod=(true|false)(?=\s|$)", text)
    services = re.findall(r"(?m)^Input method service state for " +
                          re.escape("com.osfans.trime.ime.core.TrimeInputMethodService") +
                          r"@([0-9a-f]+):\r?$", text)
    if current != [ime] or bindings != [(ime, "true", "true")] or len(services) != 1:
        return None
    return dict(ime=ime, connection_field=connection, service_instance=services[0])


def activation_sample(before_raw, after_raw, manager_raw, api, expected=None, *, ime=IME):
    # A SharedPreferences disk write can be observed in progress. Retain each
    # raw sample; only a complete XML map may become the upgrade baseline.
    try:
        before, after = preference_values(before_raw), preference_values(after_raw)
    except ET.ParseError:
        return None
    if expected is not None:
        # Never wait for a changed setting to be restored or whitelist theme.
        require_preserved_preferences(expected, before)
        require_preserved_preferences(expected, after)
    require(all(before.get(key) == value and after.get(key) == value
                for key, value in PREFERENCE_FIXTURE.items()), "Non-default preference lost during activation")
    binding = bound_ime_state(manager_raw, api, ime=ime)
    if binding is None or before_raw != after_raw or after.get("selected_theme") != ("string", "trime"):
        return None
    return dict(preferences_sha256=hashlib.sha256(after_raw).hexdigest(), values=after, binding=binding)


def validate_artifacts(args):
    package = package_components(args.package)["package"]
    binding = bound(args.artifact_record)
    require(binding["sha256"] == args.artifact_record_sha256, "Artifact record SHA differs")
    record = read_json(args.artifact_record)
    require(bound(args.artifact_record) == binding, "Artifact record changed while reading")
    require(record.get("schema_version") == 1 and record.get("task") == "T14" and
            record.get("candidate_commit") == args.target_sha, "Artifact record target identity differs")
    require(re.fullmatch(r"[0-9a-f]{40}", args.target_sha), "Full target Git SHA required")
    rows = record["apks"]
    require(set(rows) == {"old", "new", "test"}, "Exactly old/new/test APK records required")
    inputs = [binding]
    for key, path in (("old", args.old_apk), ("new", args.new_apk), ("test", args.test_apk)):
        actual = bound(path)
        require(actual == {name: rows[key][name] for name in ("path", "bytes", "sha256")},
                "Explicit APK differs from frozen identity record: " + key)
        require(isinstance(rows[key]["version_code"], int) and rows[key]["version_code"] > 0 and
                isinstance(rows[key]["debuggable"], bool) and rows[key]["min_sdk"] <= args.api and
                re.fullmatch(r"[0-9a-f]{64}", rows[key]["certificate_sha256"]), "Invalid actual APK metadata")
        inputs.append(actual)
    old, new, test = (rows[key] for key in ("old", "new", "test"))
    require(old["package"] == new["package"] == package and test["package"] == package + ".test" and
            test["instrumentation_target"] == package, "Old/new/test must match the explicit same-package identity; no cross-package migration")
    require(old["certificate_sha256"] == new["certificate_sha256"] == test["certificate_sha256"],
            "Exact old/new/test certificate match required")
    require(new["version_code"] > old["version_code"] and new["debuggable"] is False,
            "New normal Release must strictly increase versionCode")
    require("x86_64" in old["abis"] and "x86_64" in new["abis"], "Real emulator ABI required")
    require(old["debuggable"] == (args.old_kind == "debug"), "Old sample kind does not match APK manifest")
    return record, inputs


def load_probe(args):
    descriptor = bound(args.probe_source)
    require(descriptor["sha256"] == args.probe_source_sha256 and descriptor["bytes"] <= 64 * 1024,
            "Reviewed bounded Probe source SHA differs")
    spec = importlib.util.spec_from_loader("t14_bound_probe", loader=None, origin=str(args.probe_source))
    module = importlib.util.module_from_spec(spec)
    module.__file__ = str(args.probe_source)
    raw = args.probe_source.read_bytes()
    require(hashlib.sha256(raw).hexdigest() == descriptor["sha256"], "Probe changed before loading")
    exec(compile(raw, str(args.probe_source), "exec"), module.__dict__)
    require(module.SOURCE_STATE == "ROOT_REVIEWED", "Unreviewed local Probe dependency")
    return module, descriptor


def run(args):
    components = package_components(args.package)
    PACKAGE, IME, EDITOR, NATIVE_ENTRY, EXTERNAL = (components[name] for name in
        ("package", "ime", "editor", "native_entry", "external"))
    source = bound(Path(__file__).absolute())
    require(source["sha256"] == args.reviewed_source_sha256, "Runner differs from reviewed source SHA")
    record, inputs = validate_artifacts(args)
    module, dependency = load_probe(args)
    inputs += [source, dependency]
    control = module.Probe(SimpleNamespace(runtime=args.runtime, output=args.output, mode="t14_upgrade",
                                          timeout=1800, install_debug=None, install_test=None))
    require(control.ready["api"] == args.api, "Owned runtime API differs")
    deadline = control.deadline
    control.deadline -= 60
    output = control.output
    report = dict(task="T14", schema_version=1, passed=False, started=now(), candidate_commit=args.target_sha,
                  api=args.api, old_kind=args.old_kind, tested_package=PACKAGE, package_components=components, inputs=inputs, phases=[], identities=[],
                  stopped_reason=None, checks={},
                  new_apk={key: record["apks"]["new"][key] for key in ("path", "bytes", "sha256")},
                  controlled_downgrade=False, uninstall_or_clear=False, cross_package_migration=False,
                  full_device_acceptance=False, public_ui_input_verified=False,
                  fixture_scope="Dedicated sandbox host-written preferences/configuration; real APK JNI learning",
                  activation_scope="Root-launched private empty editor; no clipboard record extras, input or save; no public UI acceptance",
                  scope="Same-package APK update, retained configuration/preferences/UID/IME and native dictionary")
    nonce = uuid.uuid4().hex
    marker = "T14_UPGRADE_" + nonce
    baseline = None
    running_native = False
    successful_upgrade = False

    def write(name, raw):
        path = output / name
        with path.open("xb") as stream:
            stream.write(raw)
        return bound(path)

    def read_remote(path, label, timeout=30):
        raw = control.command(["exec-out", "cat", path], timeout=timeout)
        require(len(raw) <= 2 * 1024 * 1024, "Fixture file exceeds 2 MiB")
        return raw, write(label, raw)

    def optional_remote(path, label):
        kind = control.shell("sh", "-c",
                             'if [ -L "$1" ]; then echo symlink; '
                             'elif [ -f "$1" ]; then echo file; '
                             'elif [ -e "$1" ]; then echo other; else echo absent; fi',
                             "t14-path", path)
        require(kind in (b"file", b"absent"), "Expected regular configuration or absent path")
        if kind == b"file":
            return read_remote(path, label)
        return None, dict(path=path, previously_present=False)

    def installed(label, kind):
        expected = record["apks"][kind]
        package = expected["package"]
        dump = control.shell("dumpsys", "package", package).decode()
        uids = set(re.findall(r"(?m)^\s*(?:userId|appId)=(\d+)\b", dump))
        dirs = set(re.findall(r"(?m)^\s*dataDir=(\S+)\s*$", dump))
        codes = re.findall(r"(?m)^\s*versionCode=(\d+)\b", dump)
        versions = re.findall(r"(?m)^\s*versionName=([^\s]+)", dump)
        require(len(uids) == len(dirs) == len(codes) == len(versions) == 1 and
                int(codes[0]) == expected["version_code"] and versions[0] == expected["version_name"] and
                ("DEBUGGABLE" in dump) == expected["debuggable"], "Installed identity differs: " + label)
        paths = control.shell("pm", "path", package).decode().splitlines()
        require(len(paths) == 1 and re.fullmatch(r"package:/[^\s]+\.apk", paths[0]), "Ambiguous installed APK")
        local = output / (label + "-installed.apk")
        control.command(["pull", paths[0][8:], local], timeout=120)
        actual = bound(local)
        require((actual["bytes"], actual["sha256"]) == (expected["bytes"], expected["sha256"]),
                "Installed raw APK differs: " + label)
        row = dict(label=label, uid=int(next(iter(uids))), data_dir=next(iter(dirs)), apk=actual,
                   version_code=int(codes[0]), version_name=versions[0], debuggable=expected["debuggable"])
        report["identities"].append(row)
        return row

    def install(kind):
        arguments = ["install", "--no-incremental", "-r"]
        if kind == "test":
            arguments.append("-t")
        arguments.append(record["apks"][kind]["path"])
        require("-d" not in arguments and "uninstall" not in arguments, "Unsafe install option")
        raw = control.command(arguments, timeout=120)
        require_install_success(raw)
        return installed(kind + "-installed", kind)

    def ime_state(label):
        values = {key: control.shell("settings", "get", "secure", key).decode() for key in
                  ("default_input_method", "enabled_input_methods", "selected_input_method_subtype")}
        write(label + "-input-method.txt", control.shell("dumpsys", "input_method"))
        save(output / (label + "-ime.json"), values)
        return values

    def native(phase):
        nonlocal running_native
        receipt_name = "t10-upgrade-" + phase + "-receipt.json"
        row = dict(phase=phase, started=now(), passed=False)
        report["phases"].append(row)
        try:
            control.verify_guest()
            control.shell("am", "force-stop", PACKAGE)
            control.shell("am", "force-stop", PACKAGE + ".test")
            control.shell("test", "!", "-e", EXTERNAL + receipt_name)
            running_native = True
            index = len(control.commands)
            raw = control.shell("am", "instrument", "-w", "-r", "-e", "t10Upgrade", phase,
                                NATIVE_ENTRY, timeout=900)
            row["command_index"] = index
            row["actual_command"] = control.commands[index]
            require(re.findall(rb"(?m)^INSTRUMENTATION_RESULT: passed=(true|false)\r?$", raw) == [b"true"] and
                    re.findall(rb"(?m)^INSTRUMENTATION_CODE: (-?[0-9]+)\r?$", raw) == [b"-1"] and
                    b"INSTRUMENTATION_FAILED" not in raw and b"INSTRUMENTATION_ABORTED" not in raw,
                    "Native Bundle/terminal did not pass")
            counts = re.findall(rb"(?m)^INSTRUMENTATION_RESULT: checks=([1-9][0-9]*)\r?$", raw)
            pids = re.findall(rb"(?m)^INSTRUMENTATION_STATUS: audit_pid=([1-9][0-9]*)\r?$", raw)
            terminal = rb"(?m)^INSTRUMENTATION_RESULT: stream=PASS: T10 actual native APK upgrade " + phase.encode()
            terminal += rb"; ([1-9][0-9]*) checks; native finalized; independent serial JNI context, no Daemon lifecycle claim\r?$"
            require(len(counts) == len(pids) == 1 and re.findall(terminal, raw) == counts,
                    "Missing/ambiguous native identity, check count or terminal")
            data, binding = read_remote(EXTERNAL + receipt_name, receipt_name)
            receipt = json.loads(data)
            old = record["apks"]["old"]
            tested = record["apks"]["old" if phase == "prepare" else "new"]
            require(receipt["passed"] is True and receipt["native_stopped"] is True and
                    receipt["phase"] == phase and receipt["checks"] == int(counts[0]) and
                    receipt.get("failure") is None and receipt["package"] == PACKAGE and
                    receipt["uid"] == baseline["uid"] and
                    receipt["fixture_retained_for_upgrade"] == (phase == "prepare") and
                    (receipt["old_version_name"], receipt["old_version_code"]) ==
                    (old["version_name"], old["version_code"]) and
                    (receipt["tested_version_name"], receipt["tested_version_code"]) ==
                    (tested["version_name"], tested["version_code"]), "Native receipt/installed identity differs")
            require(receipt["checks"] == (2 if phase == "prepare" else 3), "Native upgrade coverage changed")
            registration = receipt.get("enabled_schema_registration", {})
            require(registration.get("ordinary_maintenance_readback") is True and
                    registration.get("normal_startup_full_check") is False and
                    registration.get("actual_ids") == registration.get("expected_ids") and
                    len(registration.get("actual_ids", [])) >= 3,
                    "Native fixture was not registered through ordinary schema maintenance")
            if phase == "verify":
                require(receipt.get("cache_boundary_sha256") == boundary_binding["sha256"],
                        "Native verified cache boundary differs")
                require_stopped_install_tree(state, boundary, receipt.get("expected_stopped_install_tree"))
                require(registration == state["enabled_schema_registration"] and
                        receipt.get("schema_registration_restored") is True and
                        receipt.get("restored_enabled_schema_ids") == state["original_enabled_schema_ids"],
                        "Native successful cleanup did not restore the original schema registration")
                backup = receipt["release_backup"]
                require(backup["calls"] == 3 and backup["logical_entries"] == state["logical_entries"] and
                        backup["full_t9_lookup_preserved"] is True and len(backup["iterations"]) == 3 and
                        all(item["backup_returned"] is True and item["owned_temporary_files"] == 0 and
                            item["exported_entries"] == state["logical_entries"] and
                            item["sha256"] == backup["sha256"] for item in backup["iterations"]),
                        "Three actual native backups/learning counters differ")
            row.update(passed=True, native_pid=int(pids[0]), checks=int(counts[0]), receipt=binding)
            running_native = False
            return receipt
        finally:
            row["finished"] = now()
            save(output / ("native-" + phase + "-phase.json"), row)

    def snapshot(label, preference_path, configs):
        raw, binding = read_remote(preference_path, label + "-preferences.xml")
        values = preference_values(raw)
        require(all(values.get(key) == value for key, value in PREFERENCE_FIXTURE.items()),
                "Non-default preference lost: " + label)
        config_rows = {}
        for index, (remote, expected) in enumerate(configs.items()):
            reader = optional_remote if expected is None else read_remote
            data, item = reader(remote, label + "-config-" + str(index) + ".bin")
            require(data == expected, "Retained user configuration changed: " + remote)
            config_rows[remote] = item
        state = dict(preferences=binding, values=values, configs=config_rows)
        save(output / (label + "-retained-data.json"), state)
        return state

    def same_preferences(before, after):
        require_preserved_preferences(before["values"], after["values"])

    def capture_ui_health(label, enforce=True):
        entry = dict(label=label, started=now(), enforced=enforce, observations={}, failures={},
                     scope="Current window focus and process error flags; activity/power diagnostic receipts")
        report.setdefault("ui_health", []).append(entry)
        captured = {}
        for name, arguments in (("window", ("window",)), ("activities", ("activity", "activities")),
                                ("processes", ("activity", "processes")), ("power", ("power",))):
            try:
                raw = control.shell("dumpsys", *arguments, timeout=10)
                captured[name] = raw
                entry["observations"][name] = write(label + "-" + name + ".txt", raw)
            except Exception:
                entry["failures"][name] = traceback.format_exc()
        entry["finished"] = now()
        if enforce:
            require(not entry["failures"], "UI health capture incomplete: " + label)
            focus = require_no_ui_error(captured["window"], captured["processes"])
            require(focus is not None and focus["user"] == 0 and
                    re.fullmatch(r"[\w.]+/[\w.$]+", focus["title"]),
                    "No unambiguous foreground application window: " + label)
            entry["focused_window"] = focus
            entry["passed"] = True
        return entry

    def start_empty_editor(label):
        try:
            capture_ui_health(label + "-before-editor")
            raw = control.shell("am", "start", "-W", "-n", EDITOR, timeout=30)
            receipt = write(label + "-editor-start.stdout", raw)
            report.setdefault("editor_starts", []).append(dict(label=label, component=EDITOR, receipt=receipt))
            require(re.findall(rb"(?m)^Status: (\S+)\r?$", raw) == [b"ok"] and
                    not re.search(rb"(?m)^Error:", raw), "Private empty editor did not start: " + label)
        except BaseException:
            capture_ui_health(label + "-editor-start-failure", enforce=False)
            raise

    def await_activation(label, expected=None):
        # ThemeManager.init persists selected_theme after Rime readiness and
        # successful theme loading. Merely setting the IME does not await it.
        # Two stable observations also require the real bound service dump.
        limit = min(control.deadline, time.monotonic() + 60)
        previous = None
        observations = []
        entry = dict(label=label, completed=False, timeout_seconds=60, observations=observations,
                     scope="Focused private empty editor, bound IME, persisted theme and quiescent preferences; no public UI readiness claim")
        report.setdefault("activation_waits", []).append(entry)

        def remaining():
            control.check()
            left = limit - time.monotonic()
            require(left > 0, "IME theme/binding/preferences did not stabilize: " + label)
            return min(10, left)

        try:
            while True:
                index = len(observations)
                prefix = label + "-" + str(index).zfill(3)
                row = dict(started=now(), command_index=len(control.commands))
                observations.append(row)
                first, row["before"] = read_remote(preference_path, prefix + "-before.xml", remaining())
                manager = control.shell("dumpsys", "input_method", timeout=remaining())
                row["input_method"] = write(prefix + "-input-method.txt", manager)
                second, row["after"] = read_remote(preference_path, prefix + "-after.xml", remaining())
                sample = activation_sample(first, second, manager, args.api, expected, ime=IME)
                windows = control.shell("dumpsys", "window", timeout=remaining())
                row["window"] = write(prefix + "-window.txt", windows)
                focus = focused_editor_window(windows, editor=EDITOR)
                if sample is not None and focus is not None:
                    sample["focused_editor"] = focus
                else:
                    sample = None
                row.update(finished=now(), ready_sample=sample is not None)
                if sample is not None and sample == previous:
                    entry.update(completed=True, accepted_sample=sample, finished=now())
                    return sample
                previous = sample
                time.sleep(min(.25, remaining()))
        except BaseException:
            capture_ui_health(label + "-activation-failure", enforce=False)
            raise
        finally:
            save(output / (label + "-activation.json"), entry)

    try:
        control.verify_guest()
        capture_ui_health("before-fixture")
        packages = control.shell("pm", "list", "packages").decode().splitlines()
        require("package:" + PACKAGE not in packages and "package:" + PACKAGE + ".test" not in packages,
                "Fresh dedicated target required; existing installs are never uninstalled or cleared")
        control.shell("test", "!", "-e", EXTERNAL.rstrip("/"))
        report["initial_ime"] = ime_state("initial")
        latin = "com.android.inputmethod.latin/.LatinIME"
        require(latin in control.shell("ime", "list", "-s").decode().splitlines(), "Known system Latin IME missing")
        control.shell("ime", "enable", latin)
        control.shell("ime", "set", latin)
        require(control.shell("settings", "get", "secure", "default_input_method").decode() == latin,
                "Idle native phase system IME not selected")
        control.shell("log", "-t", "T14Upgrade", marker + "_START")
        report["log_marker"] = marker
        baseline = install("old")
        require(10000 <= baseline["uid"] < 100000 and re.fullmatch(
            r"/data/(?:data|user/0)/" + re.escape(PACKAGE), baseline["data_dir"]), "Unexpected sandbox identity")
        install("test")
        report["checks"]["fresh_actual_old_and_test_install"] = True
        # A partially initialized preference file must not make an old version
        # fall back to EXTERNAL_SYNC without a grant. This is the minimal fresh
        # profile state, before any old APK application/native initialization.
        preference_path = baseline["data_dir"] + "/shared_prefs/" + PACKAGE + "_preferences.xml"
        control.shell("test", "!", "-e", preference_path)
        control.shell("test", "!", "-L", preference_path)
        initial_preferences = (b'<map><string name="profile_data_storage_mode">APP_STORAGE</string>'
                               b'<boolean name="profile_fresh_input_setup_pending" value="true"/></map>\n')
        initial_binding = write("initial-storage-preferences.xml", initial_preferences)
        preference_directory = preference_path.rsplit("/", 1)[0]
        control.shell("mkdir", "-p", preference_directory)
        control.command(["push", initial_binding["path"], preference_path])
        control.shell("chown", str(baseline["uid"]) + ":" + str(baseline["uid"]),
                      preference_directory, preference_path)
        control.shell("chmod", "700", preference_directory)
        control.shell("chmod", "600", preference_path)
        require(control.command(["exec-out", "cat", preference_path]) == initial_preferences,
                "Minimal initial APP_STORAGE preferences differ")
        report["initial_storage_preferences"] = initial_binding
        control.shell("mkdir", "-p", EXTERNAL.rstrip("/"))
        dedicated = output / "runtime-audit-dedicated"
        dedicated.write_text(marker + "\n", encoding="utf-8")
        control.command(["push", dedicated, EXTERNAL + dedicated.name])
        for name in ("t10-upgrade-state.json", "t10-upgrade-prepare-receipt.json", "t10-upgrade-verify-receipt.json", CACHE_BOUNDARY_FILE):
            control.shell("test", "!", "-e", EXTERNAL + name)
        native("prepare")
        report["checks"]["actual_old_native_learning_and_stopped_fixture"] = True
        state_raw, state_binding = read_remote(EXTERNAL + "t10-upgrade-state.json", "old-native-state.json")
        state = json.loads(state_raw)
        require_owned_tree(state["prefix"], state["owned_tree"])
        require(state["package"] == PACKAGE and state["uid"] == baseline["uid"] and
                state["owned_tree"] and state["logical_entries"] and
                state["application_data_dir"] == baseline["data_dir"], "Actual old native learning state incomplete")
        report["old_native_state"] = state_binding
        control.shell("am", "force-stop", PACKAGE)
        original, report["old_original_preferences"] = read_remote(preference_path, "old-original-preferences.xml")
        require(preference_values(original).get("profile_data_storage_mode") == ("string", "APP_STORAGE"),
                "Old native preparation changed the explicit APP_STORAGE mode")
        preferences = seeded_preferences(original)
        seed_binding = write("seeded-preferences.xml", preferences)
        control.command(["push", seed_binding["path"], preference_path])
        control.shell("chown", str(baseline["uid"]) + ":" + str(baseline["uid"]), preference_path)
        control.shell("chmod", "600", preference_path)
        require(control.command(["exec-out", "cat", preference_path]) == preferences, "Preference seed readback differs")
        user = state["user_data_dir"]
        require(user in (EXTERNAL.rstrip("/") + "/rime",
                         "/storage/emulated/0/Android/data/" + PACKAGE + "/files/rime",
                         "/storage/sdcard/Android/data/" + PACKAGE + "/files/rime"),
                "Unexpected user dictionary path")
        config_path = user + "/default.custom.yaml"
        config_bytes, report["old_registered_config"] = read_remote(config_path, "old-registered-default.custom.yaml")
        original_config = require_registered_fixture(state, config_bytes)
        report["enabled_schema_registration"] = state["enabled_schema_registration"]
        report["cleanup_original_config"] = (
            write("cleanup-original-default.custom.yaml", original_config) if original_config is not None
            else dict(path=config_path, previously_present=False))
        # The real registered full/T9 list is already non-default functional
        # configuration. Keep its exact production bytes; do not append markers.
        phrase_path = user + "/custom_phrase.txt"
        control.shell("test", "!", "-e", phrase_path)
        control.shell("test", "!", "-L", phrase_path)
        configs = {config_path: config_bytes, phrase_path: "霁月青舟\tji yue qing zhou\t9\n".encode()}
        item = write("seed-custom_phrase.txt", configs[phrase_path])
        control.command(["push", item["path"], phrase_path])
        before_cache, after_cache = {}, {}
        cache_captures = dict(before={}, after={})
        report["old_reactivation_compiled_cache"] = cache_captures
        for relative in cache_paths(state["prefix"]):
            before_cache[relative], cache_captures["before"][relative] = read_remote(
                user + "/" + relative, "old-ime-before-" + relative[6:])
            require(hashlib.sha256(before_cache[relative]).hexdigest() == state["owned_tree"][relative],
                    "Old cache changed before public activation")
        # The target is selected during installation. It is never switched away
        # until the post-install selection and retained-data assertions finish.
        control.shell("ime", "enable", IME)
        control.shell("ime", "set", IME)
        start_empty_editor("old-ime")
        old_activation = await_activation("old-ime")
        for relative in cache_paths(state["prefix"]):
            after_cache[relative], cache_captures["after"][relative] = read_remote(
                user + "/" + relative, "old-ime-after-" + relative[6:])
        boundary = cache_boundary(state, state_raw, before_cache, after_cache)
        boundary_raw = (json.dumps(boundary, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        require(len(boundary_raw) <= 1024 * 1024, "Cache boundary handoff exceeds limit")
        boundary_binding = write(CACHE_BOUNDARY_FILE, boundary_raw)
        control.command(["push", boundary_binding["path"], EXTERNAL + CACHE_BOUNDARY_FILE])
        boundary_readback, report["cache_boundary_before_install"] = read_remote(
            EXTERNAL + CACHE_BOUNDARY_FILE, "before-install-cache-boundary.json")
        require(boundary_readback == boundary_raw, "Cache boundary handoff write/readback differs")
        report["cache_boundary_handoff"] = boundary_binding
        ime_before = ime_state("before-upgrade")
        require(ime_before["default_input_method"] == IME and
                IME in {entry.split(";")[0] for entry in ime_before["enabled_input_methods"].split(":")},
                "Old Trime is not actually enabled and selected")
        before = snapshot("before-upgrade", preference_path, configs)
        require_preserved_preferences(old_activation["values"], before["values"])
        current = install("new")
        require((current["uid"], current["data_dir"]) == (baseline["uid"], baseline["data_dir"]),
                "Ordinary upgrade changed UID/data directory")
        # Package replacement stops the old service. An idle launcher need not
        # bind the new IME until the next input request. First compare retained
        # selection and data without causing activation; the final explicit
        # reselection below must still bind and initialize the upgraded service.
        ime_after = ime_state("after-upgrade")
        require(ime_after == ime_before, "Ordinary APK update changed selected/enabled IME or subtype")
        after = snapshot("after-upgrade", preference_path, configs)
        same_preferences(before, after)
        retained, report["after_install_native_state"] = read_remote(
            EXTERNAL + "t10-upgrade-state.json", "after-install-native-state.json")
        require(retained == state_raw, "APK update changed native state metadata")
        retained_boundary, report["cache_boundary_after_install"] = read_remote(
            EXTERNAL + CACHE_BOUNDARY_FILE, "after-install-cache-boundary.json")
        require(retained_boundary == boundary_raw, "APK update changed cache boundary handoff bytes")
        report["ime_before_upgrade"], report["ime_after_upgrade"] = ime_before, ime_after
        report["ordinary_install_preserved_ime_uid_config_preferences"] = True
        report["checks"].update(strictly_newer_normal_release_install=True, retained_uid_and_data_directory=True,
                               retained_selected_enabled_ime_and_subtype=True,
                               retained_nondefault_preferences_and_config=True,
                               retained_exact_native_state_metadata=True)
        # Only after that assertion does the existing native probe need an idle
        # Daemon. Its stopped-LevelDB check and real learned-word reads remain.
        control.shell("ime", "set", latin)
        native("verify")
        report["checks"]["new_native_stopped_bytes_full_t9_learning_counters_and_three_backups"] = True
        control.shell("test", "!", "-e", EXTERNAL + "t10-upgrade-state.json")
        control.shell("test", "!", "-e", EXTERNAL + CACHE_BOUNDARY_FILE)
        # Normal installation was already checked against registered production
        # bytes above. Successful native fixture cleanup intentionally restores
        # only its saved default customization; the independent phrase stays.
        final_configs = {config_path: original_config, phrase_path: configs[phrase_path]}
        restored, report["restored_default_config"] = optional_remote(config_path, "restored-default.custom.yaml")
        require(restored == original_config, "Owned fixture cleanup changed the original configuration baseline")
        control.shell("ime", "set", IME)
        start_empty_editor("final-ime")
        await_activation("final-ime", before["values"])
        final_ime = ime_state("final")
        require(final_ime == ime_before, "Final upgraded Trime selection differs")
        final_data = snapshot("final", preference_path, final_configs)
        same_preferences(before, final_data)
        final_identity = installed("final-release", "new")
        require((final_identity["uid"], final_identity["data_dir"]) == (baseline["uid"], baseline["data_dir"]),
                "Final upgraded sandbox changed")
        control.shell("log", "-t", "T14Upgrade", marker + "_END")
        report["final_ime"] = final_ime
        report["checks"]["final_release_identity_ime_and_nondefault_data"] = True
        successful_upgrade = True
    except BaseException:
        report["failure"] = traceback.format_exc()
    finally:
        control.deadline = deadline
        if running_native or "failure" in report:
            try:
                control.verify_guest()
                for package in (PACKAGE, PACKAGE + ".test"):
                    control.shell("am", "force-stop", package, timeout=15)
                report["failed_probe_stop_requests_completed"] = True
            except BaseException:
                report["cleanup_failure"] = traceback.format_exc()
        try:
            for item in inputs:
                control.check()
                require(bound(Path(item["path"])) == item, "Frozen input changed during upgrade")
            for command in control.commands:
                arguments = command["argv"][3:]
                require("uninstall" not in arguments and not (arguments and arguments[0] == "install" and "-d" in arguments),
                        "Unexpected destructive/downgrade command")
            require(not any(command.get("signals") or command.get("cleanup_error") for command in control.commands),
                    "Forced or incompletely cleaned command cannot pass")
        except BaseException:
            report["binding_failure"] = traceback.format_exc()
        report.update(finished=now(), minimum_available_kib=control.minimum,
                      dedicated_sandbox_retained=True, full_system_warning_review_pending=True)
        if "failure" in report:
            report["stopped_reason"] = report["failure"].strip().splitlines()[-1]
        report["passed"] = successful_upgrade and set(report["checks"]) == REQUIRED_CHECKS and all(
            value is True for value in report["checks"].values()) and not any(
            key in report for key in ("failure", "cleanup_failure", "binding_failure"))
        control.report.update(commands=control.commands, finished=now(), scope=report["scope"])
        save(output / "control-report.json", control.report)
        report["control_report"] = bound(output / "control-report.json")
        save(output / "upgrade-report.json", report)
    print(json.dumps(dict(passed=report["passed"], full_acceptance=False,
                         report=bound(output / "upgrade-report.json"))), flush=True)
    return 0 if report["passed"] else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", type=int, choices=(21, 35), required=True)
    parser.add_argument("--package", required=True, help="Actual same old/new APK package; explicit identity, never inferred migration")
    parser.add_argument("--old-kind", choices=("release", "debug"), default="release")
    for name in ("old-apk", "new-apk", "test-apk", "artifact-record", "probe-source", "runtime", "output"):
        parser.add_argument("--" + name, type=lambda value: Path(value).absolute(), required=True)
    for name in ("target-sha", "artifact-record-sha256", "probe-source-sha256", "reviewed-source-sha256"):
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_AS, (256 * 1024 * 1024, 256 * 1024 * 1024))

    def interrupted(signum, frame):
        raise KeyboardInterrupt("Upgrade gate signal " + str(signum))

    signal.signal(signal.SIGTERM, interrupted)
    return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
