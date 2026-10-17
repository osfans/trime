#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run the API 21 real-picker SAF gate as three resumable evidence phases.

The gate deliberately does not start, wipe, or install an emulator.  It binds an
already prepared disposable API 21 emulator to one frozen candidate, pauses for
the one required system-picker interaction, and then runs these phases in order:

    roundtrip -> restart -> revoke

Each phase keeps the ordinary ``run_runtime.py`` evidence and adds an immutable
``phase-receipt.json``.  A later phase refuses a different candidate, device
boot, tree URI, installed APK, or altered earlier receipt.
"""

from __future__ import annotations

import argparse
from contextlib import redirect_stdout
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import traceback
from urllib.parse import unquote, urlsplit
import uuid
import xml.etree.ElementTree as ET

if sys.flags.optimize:
    raise SystemExit(
        "API 21 SAF gate refuses optimized Python (-O/-OO); "
        "its offline and runtime assertions must remain enabled"
    )


REPO = Path(__file__).resolve().parents[2]
T07_ROOT = REPO / "build/t07-privacy"
RUNTIME_ROOT = T07_ROOT / "runtime"
QUALITY = REPO / "script/quality"
RUNTIME_DRIVER = QUALITY / "run_runtime.py"
APP_NAME = "com.osfans.trime-3.3.13-t9.10-x86_64-debug.apk"
TEST_NAME = "com.osfans.trime-3.3.13-t9.10-debug-androidTest.apk"
PACKAGE = "com.osfans.trime.debug"
TEST_PACKAGE = PACKAGE + ".test"
MAIN_ACTIVITY = PACKAGE + "/com.osfans.trime.ui.main.MainActivity"
PREFERENCE_FILE = f"shared_prefs/{PACKAGE}_preferences.xml"
DISPOSABLE_MARKER = f"/sdcard/Android/data/{PACKAGE}/files/runtime-audit-dedicated"
PICKER_PACKAGES = ("com.android.documentsui", "com.google.android.documentsui")
STAGES = ("roundtrip", "restart", "revoke")
STAGE_ORDINAL = {name: index + 1 for index, name in enumerate(STAGES)}

sys.path.insert(0, str(QUALITY))
import run_runtime  # noqa: E402
from android_tools import resolve_adb  # noqa: E402
from runtime_audit import device_audit_lock, instrumentation_passed, instrumentation_pid  # noqa: E402


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path: Path, value, *, replace: bool = False) -> None:
    if path.exists() and not replace:
        raise FileExistsError(f"Refusing to replace evidence: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    os.replace(temporary, path)


def load_json(path: Path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object: {path}")
    return value


def validate_output_path(output: Path) -> Path:
    resolved = output.resolve()
    allowed = RUNTIME_ROOT.resolve()
    if resolved.parent != allowed:
        raise ValueError(f"Output must be a direct child of {allowed}")
    return resolved


def validate_candidate(candidate: Path, expected_commit: str) -> dict:
    candidate = candidate.resolve()
    if not re.fullmatch(r"[0-9a-f]{40}", expected_commit):
        raise ValueError("--commit must be a full lowercase Git SHA")
    identity_path = candidate / "identity.json"
    if not identity_path.is_file():
        raise FileNotFoundError(identity_path)
    identity = load_json(identity_path)
    if identity.get("git") != expected_commit:
        raise RuntimeError(
            f"Candidate commit mismatch: expected {expected_commit}, got {identity.get('git')!r}"
        )
    if identity.get("dirty") is not False:
        raise RuntimeError("Candidate identity is not from a clean tree")
    if identity.get("source_unchanged_during_build") is not True:
        raise RuntimeError("Candidate source changed during its build")
    artifacts = {}
    for name, package in ((APP_NAME, PACKAGE), (TEST_NAME, TEST_PACKAGE)):
        path = candidate / name
        if not path.is_file():
            raise FileNotFoundError(path)
        actual = sha256(path)
        recorded = identity.get("artifacts", {}).get(name)
        if recorded != actual:
            raise RuntimeError(
                f"Candidate artifact hash mismatch for {name}: {recorded!r} != {actual}"
            )
        artifacts[package] = {"name": name, "path": str(path), "sha256": actual}
    return {
        "path": str(candidate),
        "git": expected_commit,
        "identity_path": str(identity_path),
        "identity_sha256": sha256(identity_path),
        "artifacts": artifacts,
    }


def parse_tree_uri(value: str, expected_leaf: str | None = None) -> dict:
    parsed = urlsplit(value)
    if (
        parsed.scheme != "content"
        or parsed.netloc != "com.android.externalstorage.documents"
        or parsed.query
        or parsed.fragment
        or not parsed.path.startswith("/tree/")
        or parsed.path.count("/") != 2
    ):
        raise ValueError("Expected one ExternalStorageProvider tree URI")
    document_id = unquote(parsed.path.removeprefix("/tree/"))
    if document_id.count(":") != 1:
        raise ValueError("Tree document ID must contain one volume separator")
    volume, relative = document_id.split(":", 1)
    parts = relative.split("/")
    if not volume or len(parts) != 2 or parts[0] != "Documents":
        raise ValueError("Tree must be a direct child of Documents on one volume")
    leaf = parts[1]
    pattern = r"trime-saf-[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    if re.fullmatch(pattern, leaf, re.IGNORECASE) is None:
        raise ValueError("Tree leaf must be trime-saf-UUID")
    if expected_leaf is not None and leaf != expected_leaf:
        raise ValueError(f"Selected tree {leaf!r} differs from requested {expected_leaf!r}")
    return {
        "uri": value,
        "authority": parsed.netloc,
        "document_id": document_id,
        "volume": volume,
        "relative_path": relative,
        "leaf": leaf,
    }


def parse_preferences(raw: bytes) -> dict:
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as error:
        raise ValueError("Default preferences are not valid XML") from error
    if root.tag != "map":
        raise ValueError("Unexpected default-preference root")
    values = {}
    for child in root:
        name = child.get("name")
        if not name or name in values:
            raise ValueError("Preference key is missing or duplicated")
        if child.tag == "string":
            values[name] = child.text or ""
        elif child.tag in ("int", "long", "float", "boolean"):
            values[name] = child.get("value")
    return values


def validate_preferences(raw: bytes, tree_uri: str | None = None) -> dict:
    values = parse_preferences(raw)
    selected = values.get("profile_external_rime_tree_uri", "")
    mode = values.get("profile_data_storage_mode", "")
    result = {
        "tree_uri": selected,
        "data_storage_mode": mode,
        "display_name": values.get("profile_external_rime_display_name", ""),
        "application_pid": int(values.get("general__pid", "0") or "0"),
    }
    if tree_uri is not None:
        tree = parse_tree_uri(selected)
        if selected != tree_uri:
            raise RuntimeError("The app preference no longer names the gate tree URI")
        if mode != "EXTERNAL_SYNC":
            raise RuntimeError("The app is not in EXTERNAL_SYNC mode")
        if result["display_name"] not in ("", tree["leaf"]):
            raise RuntimeError("The displayed SAF directory differs from the selected tree")
    return result


class Device:
    def __init__(self, adb: Path, serial: str, command_log: Path):
        self.adb = [str(adb), "-s", serial]
        self.serial = serial
        self.command_log = command_log

    def _record(self, argv, result, *, binary=False):
        stdout = result.stdout if not binary else bytes(result.stdout)
        stderr = result.stderr if not binary else bytes(result.stderr)
        entry = {
            "time": now(),
            "argv": [str(value) for value in argv],
            "returncode": result.returncode,
        }
        for name, value in (("stdout", stdout), ("stderr", stderr)):
            if not value:
                continue
            encoded = value.encode("utf-8", "replace") if isinstance(value, str) else value
            entry[name + "_bytes"] = len(encoded)
            entry[name + "_sha256"] = hashlib.sha256(encoded).hexdigest()
            if not binary and len(encoded) <= 4096:
                entry[name] = value
        with self.command_log.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(entry, ensure_ascii=False) + "\n")

    def run(self, *args, timeout=120, check=True, binary=False):
        argv = [*self.adb, *map(str, args)]
        result = subprocess.run(
            argv,
            cwd=REPO,
            capture_output=True,
            text=not binary,
            timeout=timeout,
            check=False,
        )
        self._record(argv, result, binary=binary)
        if check and result.returncode:
            stderr = (
                result.stderr.decode("utf-8", "replace")
                if binary
                else result.stderr
            )
            raise RuntimeError(f"ADB command failed ({result.returncode}): {stderr.strip()}")
        return result

    def shell(self, *args, timeout=120, check=True) -> str:
        return self.run("shell", *args, timeout=timeout, check=check).stdout.strip()

    def preference_xml(self) -> bytes:
        result = self.run(
            "exec-out", "run-as", PACKAGE, "cat", PREFERENCE_FILE, binary=True
        )
        if not result.stdout:
            raise RuntimeError("Default preferences are empty")
        parse_preferences(result.stdout)
        return result.stdout

    def processes(self, names) -> dict[str, list[int]]:
        wanted = set(names)
        found = {name: [] for name in names}
        for line in self.shell("ps").splitlines():
            fields = line.split()
            if len(fields) >= 2 and fields[1].isdigit():
                process = fields[-1]
                base = process.split(":", 1)[0]
                if base in wanted:
                    found[base].append(int(fields[1]))
        return {name: sorted(set(pids)) for name, pids in found.items()}

    def installed_package(self, package: str) -> dict:
        output = self.shell("pm", "path", package)
        paths = [line.removeprefix("package:") for line in output.splitlines()]
        if len(paths) != 1 or not paths[0].startswith("/"):
            raise RuntimeError(f"Expected one installed APK for {package}: {output!r}")
        remote = paths[0]
        with tempfile.TemporaryFile() as stream:
            argv = [*self.adb, "exec-out", "cat", remote]
            result = subprocess.run(
                argv, cwd=REPO, stdout=stream, stderr=subprocess.PIPE, timeout=180
            )
            recorded = subprocess.CompletedProcess(
                argv, result.returncode, b"", result.stderr
            )
            self._record(argv, recorded, binary=True)
            if result.returncode:
                raise RuntimeError(
                    f"Unable to read installed APK for {package}: "
                    + result.stderr.decode("utf-8", "replace").strip()
                )
            stream.seek(0)
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        return {"installed_path": remote, "apk_sha256": digest}

    def installed_candidate(self, candidate: dict) -> dict:
        packages = {}
        for package in (PACKAGE, TEST_PACKAGE):
            installed = self.installed_package(package)
            expected = candidate["artifacts"][package]["sha256"]
            if installed["apk_sha256"] != expected:
                raise RuntimeError(f"Installed APK differs from candidate: {package}")
            packages[package] = installed
        return packages

    def installed_picker_packages(self) -> list[str]:
        installed = []
        for package in PICKER_PACKAGES:
            result = self.run("shell", "pm", "path", package, check=False)
            paths = [line for line in result.stdout.splitlines() if line.startswith("package:/")]
            if result.returncode == 0 and len(paths) == 1:
                installed.append(package)
        if len(installed) != 1:
            raise RuntimeError(f"Expected one DocumentsUI package, got {installed}")
        return installed


def validate_device(device: Device, candidate: dict) -> dict:
    if device.run("get-state").stdout.strip() != "device":
        raise RuntimeError("ADB target is not ready")
    if device.shell("getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("SAF gate requires a disposable emulator")
    if device.shell("getprop", "ro.build.version.sdk") != "21":
        raise RuntimeError("SAF gate requires API 21")
    if device.shell("getprop", "sys.boot_completed") != "1":
        raise RuntimeError("Emulator boot is incomplete")
    device.run("shell", "test", "-f", DISPOSABLE_MARKER)
    marker = device.run("exec-out", "cat", DISPOSABLE_MARKER, binary=True)
    if marker.stdout != b"" or marker.stderr:
        raise RuntimeError("Missing or nonempty disposable-installation marker")
    boot_id = device.shell("cat", "/proc/sys/kernel/random/boot_id")
    if re.fullmatch(r"[0-9a-f-]{36}", boot_id) is None:
        raise RuntimeError("Device boot identity is unavailable")
    return {
        "serial": device.serial,
        "api": "21",
        "fingerprint": device.shell("getprop", "ro.build.fingerprint"),
        "abis": device.shell("getprop", "ro.product.cpu.abilist"),
        "boot_id": boot_id,
        "disposable_marker": DISPOSABLE_MARKER,
        "installed_packages": device.installed_candidate(candidate),
        "picker_packages": device.installed_picker_packages(),
    }


def require_same_contract(preflight: dict, candidate: dict, device_identity: dict) -> None:
    if preflight.get("candidate") != candidate:
        raise RuntimeError("Candidate contract differs from the initial SAF phase")
    original = preflight.get("device", {})
    for key in ("serial", "api", "fingerprint", "abis", "boot_id"):
        if device_identity.get(key) != original.get(key):
            raise RuntimeError(f"Device contract changed at {key}")
    if device_identity.get("installed_packages") != original.get("installed_packages"):
        raise RuntimeError("Installed candidate changed between SAF phases")
    expected_drivers = {
        "gate": sha256(Path(__file__).resolve()),
        "runtime": sha256(RUNTIME_DRIVER),
    }
    for name, expected in expected_drivers.items():
        if preflight.get("drivers", {}).get(name, {}).get("sha256") != expected:
            raise RuntimeError(f"{name} driver changed between SAF phases")


def validate_resume_contract(root: Path, preflight: dict, state: dict) -> None:
    if preflight.get("schema") != 1 or preflight.get("gate") != "api21-real-picker-saf":
        raise RuntimeError("SAF preflight schema/gate is invalid")
    if Path(preflight.get("output", "")).resolve() != root:
        raise RuntimeError("SAF preflight names another output root")
    tree = parse_tree_uri(preflight.get("tree", {}).get("uri", ""))
    if preflight.get("tree") != tree:
        raise RuntimeError("SAF preflight tree metadata is inconsistent")
    interaction = preflight.get("picker_interaction", {})
    before = root / "picker-preferences-before.xml"
    after = root / "picker-preferences-after.xml"
    if not before.is_file() or not after.is_file():
        raise RuntimeError("Picker preference evidence is missing")
    if not all(
        (
            interaction.get("requested_leaf") == tree["leaf"],
            interaction.get("operator_confirmation") == tree["leaf"],
            interaction.get("selected_tree") == tree,
            interaction.get("previous_tree_uri") != tree["uri"],
            interaction.get("preferences_before_sha256") == sha256(before),
            interaction.get("preferences_after_sha256") == sha256(after),
        )
    ):
        raise RuntimeError("Picker interaction evidence changed or is incomplete")
    expected_state = {
        "schema": 1,
        "gate": "api21-real-picker-saf",
        "gate_id": preflight.get("gate_id"),
        "candidate_identity_sha256": preflight.get("candidate", {}).get(
            "identity_sha256"
        ),
        "device_boot_id": preflight.get("device", {}).get("boot_id"),
        "tree_uri": tree["uri"],
        "expected_stages": list(STAGES),
    }
    for key, expected in expected_state.items():
        if state.get(key) != expected:
            raise RuntimeError(f"SAF gate state changed at {key}")


def expected_prior_stages(stage: str) -> tuple[str, ...]:
    return STAGES[: STAGE_ORDINAL[stage] - 1]


def validate_prior_receipts(root: Path, state: dict, stage: str) -> list[dict]:
    expected = expected_prior_stages(stage)
    if state.get("completed_stages") != list(expected):
        raise RuntimeError(
            f"{stage} requires completed stages {list(expected)}, got "
            f"{state.get('completed_stages')!r}"
        )
    receipts = []
    recorded = state.get("receipt_sha256", {})
    for prior in expected:
        path = root / prior / "phase-receipt.json"
        if not path.is_file():
            raise FileNotFoundError(path)
        actual = sha256(path)
        if recorded.get(prior) != actual:
            raise RuntimeError(f"Earlier phase receipt changed: {prior}")
        receipt = load_json(path)
        if (
            receipt.get("passed") is not True
            or receipt.get("stage") != prior
            or receipt.get("gate_id") != state.get("gate_id")
        ):
            raise RuntimeError(f"Earlier phase receipt is invalid: {prior}")
        if receipt.get("candidate", {}).get("identity_sha256") != state.get(
            "candidate_identity_sha256"
        ):
            raise RuntimeError(f"Earlier phase candidate binding is invalid: {prior}")
        if receipt.get("device", {}).get("boot_id") != state.get("device_boot_id"):
            raise RuntimeError(f"Earlier phase device binding is invalid: {prior}")
        if receipt.get("tree", {}).get("uri") != state.get("tree_uri"):
            raise RuntimeError(f"Earlier phase tree binding is invalid: {prior}")
        receipts.append({"stage": prior, "path": str(path), "sha256": actual})
    if (root / stage).exists():
        raise FileExistsError(f"Refusing to replace phase evidence: {root / stage}")
    return receipts


def validate_runtime_evidence(
    phase: Path, stage: str, candidate: dict, tree_uri: str
) -> dict:
    paths = {
        name: phase / name
        for name in (
            "identity.json",
            "report.json",
            "runtime-audit.json",
            "instrumentation.log",
            "logcat-full.txt",
            "app-logcat.txt",
        )
    }
    for path in paths.values():
        if not path.is_file():
            raise FileNotFoundError(path)
    identity = load_json(paths["identity.json"])
    report = load_json(paths["report.json"])
    audit = load_json(paths["runtime-audit.json"])
    instrumentation = paths["instrumentation.log"].read_text(encoding="utf-8")
    expected_packages = {
        package: candidate["artifacts"][package]["sha256"]
        for package in (PACKAGE, TEST_PACKAGE)
    }
    if identity.get("api") != "21" or identity.get("probe") != "saf":
        raise RuntimeError("Runtime identity is not an API 21 SAF probe")
    if identity.get("source_sha256") != sha256(RUNTIME_DRIVER):
        raise RuntimeError("Runtime identity does not bind the current driver")
    for package, expected in expected_packages.items():
        if identity.get("packages", {}).get(package, {}).get("apk_sha256") != expected:
            raise RuntimeError(f"Runtime identity has the wrong APK: {package}")
    warnings = audit.get("warnings")
    diagnostics = audit.get("known_project_diagnostics")
    if not isinstance(warnings, list) or not isinstance(diagnostics, list):
        raise RuntimeError("Runtime SAF audit did not retain warning/diagnostic lists")
    if not all(
        (
            report.get("functional_passed") is True,
            report.get("assertions_passed") is True,
            report.get("injects_faults") is True,
            isinstance(report.get("requires_log_review"), bool),
            audit.get("capture_has_records") is True,
            isinstance(audit.get("warning_free"), bool),
            report.get("warning_free") is audit.get("warning_free"),
            report.get("warning_count") == len(warnings),
            report.get("tested_ime_diagnostic_count") == len(diagnostics),
            report.get("full_probe_diagnostic_count") == len(diagnostics),
            report.get("requires_log_review") is bool(warnings or diagnostics),
        )
    ):
        raise RuntimeError("Runtime SAF report did not satisfy its functional/audit gate")
    if not instrumentation_passed(0, instrumentation):
        raise RuntimeError("Instrumentation result bundle did not pass")
    if instrumentation.splitlines().count("INSTRUMENTATION_RESULT: checks=1") != 1:
        raise RuntimeError("API 21 SAF instrumentation must report exactly one aggregate check")
    ordinary = "system provider persisted grant and round trip"
    revoked = "system provider permission revoked and access denied"
    required = revoked if stage == "revoke" else ordinary
    if instrumentation.count(required) != 1:
        raise RuntimeError(f"Instrumentation did not prove the {stage} SAF condition")
    if stage != "revoke" and "real SAF import preserves untracked files" not in instrumentation:
        raise RuntimeError("System-provider import/history evidence is missing")
    return {
        "instrumentation_pid": instrumentation_pid(instrumentation),
        "tree_uri": tree_uri,
        "invocation": {
            "probe": "saf",
            "saf_tree": tree_uri,
            "saf_revoke": stage == "revoke",
        },
        "condition": {
            "persisted_grant_and_roundtrip": stage != "revoke",
            "permission_released_and_access_denied": stage == "revoke",
            "system_provider_import_history": stage != "revoke",
        },
        "report": report,
        "audit": {
            "capture_has_records": audit.get("capture_has_records"),
            "warning_free": audit.get("warning_free"),
            "warning_count": len(warnings),
            "known_project_diagnostic_count": len(diagnostics),
        },
        "files": {
            name: {"path": str(path), "sha256": sha256(path)}
            for name, path in paths.items()
        },
    }


def run_runtime_phase(args, phase: Path, tree_uri: str) -> str:
    namespace = argparse.Namespace(
        adb=str(args.adb.resolve()),
        serial=args.serial,
        apk=args.candidate.resolve() / APP_NAME,
        test_apk=args.candidate.resolve() / TEST_NAME,
        output=phase,
        probe="saf",
        saf_tree=tree_uri,
        saf_revoke=args.stage == "revoke",
        bind_ime_after_editor_focus=False,
    )
    captured = io.StringIO()
    try:
        with redirect_stdout(captured):
            run_runtime.run(namespace)
    finally:
        if phase.is_dir():
            (phase / "driver-output.txt").write_text(captured.getvalue(), encoding="utf-8")
    return captured.getvalue()


def picker_interaction(device: Device, root: Path) -> tuple[dict, bytes, bytes]:
    before = device.preference_xml()
    before_values = validate_preferences(before)
    requested_leaf = "trime-saf-" + str(uuid.uuid4())
    instructions = f"""API 21 real-picker SAF interaction

On the visible disposable emulator:
1. Open Trime -> Profile.
2. Choose External sync, or tap User data directory and choose another directory.
3. In the Android Documents picker, create Documents/{requested_leaf}.
4. Enter that empty directory and select it. Wait until Trime returns to Profile.
5. Return to this terminal and type the exact directory name when prompted.

Do not select an existing directory or a directory containing personal data.
"""
    (root / "picker-instructions.txt").write_text(instructions, encoding="utf-8")
    device.run(
        "shell",
        "am",
        "start",
        "-W",
        "-a",
        "android.intent.action.MAIN",
        "-n",
        MAIN_ACTIVITY,
        timeout=60,
    )
    print(instructions, flush=True)
    try:
        confirmation = input(f"Type {requested_leaf} after selection: ").strip()
    except EOFError as error:
        raise RuntimeError("Picker confirmation requires an interactive terminal") from error
    if confirmation != requested_leaf:
        raise RuntimeError("Picker confirmation did not match the requested dedicated directory")
    after = device.preference_xml()
    after_values = validate_preferences(after)
    if after_values["tree_uri"] == before_values["tree_uri"]:
        raise RuntimeError("Picker did not store a newly selected tree URI")
    tree = parse_tree_uri(after_values["tree_uri"], requested_leaf)
    validate_preferences(after, tree["uri"])
    (root / "picker-preferences-before.xml").write_bytes(before)
    (root / "picker-preferences-after.xml").write_bytes(after)
    interaction = {
        "requested_leaf": requested_leaf,
        "operator_confirmation": confirmation,
        "previous_tree_uri": before_values["tree_uri"],
        "selected_tree": tree,
        "preferences_before_sha256": hashlib.sha256(before).hexdigest(),
        "preferences_after_sha256": hashlib.sha256(after).hexdigest(),
        "completed": now(),
    }
    write_json(root / "picker-interaction.json", interaction)
    return interaction, before, after


def stopped_boundary(device: Device, picker_packages: list[str]) -> dict:
    packages = [PACKAGE, TEST_PACKAGE, *picker_packages]
    before = device.processes(packages)
    commands = []
    for package in packages:
        device.shell("am", "force-stop", package)
        commands.append(["am", "force-stop", package])
    after = device.processes(packages)
    if any(after.values()):
        raise RuntimeError(f"Processes remained after force-stop: {after}")
    return {
        "required": True,
        "force_stop_commands": commands,
        "processes_before": before,
        "processes_after_force_stop": after,
        "all_absent_after_force_stop": True,
        "picker_force_stopped": all(package in packages for package in picker_packages),
        "target_and_test_force_stopped": PACKAGE in packages and TEST_PACKAGE in packages,
    }


def initial_state(gate_id: str, candidate: dict, device_identity: dict, tree: dict) -> dict:
    return {
        "schema": 1,
        "gate": "api21-real-picker-saf",
        "gate_id": gate_id,
        "candidate_identity_sha256": candidate["identity_sha256"],
        "device_boot_id": device_identity["boot_id"],
        "tree_uri": tree["uri"],
        "expected_stages": list(STAGES),
        "completed_stages": [],
        "receipt_sha256": {},
        "created": now(),
        "updated": now(),
    }


def make_summary(preflight: dict, state: dict, *, error: str | None = None) -> dict:
    completed = state.get("completed_stages", [])
    stages = {}
    for stage in completed:
        phase = Path(preflight["output"]) / stage
        stages[stage] = {
            "passed": True,
            "directory": str(phase),
            "report": str(phase / "report.json"),
            "identity": str(phase / "identity.json"),
            "runtime_audit": str(phase / "runtime-audit.json"),
            "phase_receipt": str(phase / "phase-receipt.json"),
            "phase_receipt_sha256": state["receipt_sha256"][stage],
        }
    result = {
        "schema": 1,
        "gate": "api21-real-picker-saf",
        "gate_id": state.get("gate_id"),
        "candidate": preflight.get("candidate"),
        "device": preflight.get("device"),
        "tree": preflight.get("tree"),
        "expected_stages": list(STAGES),
        "completed_stages": completed,
        "stages": stages,
        "passed": completed == list(STAGES) and error is None,
        "updated": now(),
    }
    if error is not None:
        result["error"] = error
    return result


def phase_receipt(
    root: Path,
    stage: str,
    preflight: dict,
    prior: list[dict],
    before_packages: dict,
    after_packages: dict,
    before_preferences: dict,
    after_preferences: dict,
    boundary: dict,
    runtime: dict,
) -> dict:
    previous_pid = None
    if prior:
        previous = load_json(Path(prior[-1]["path"]))
        previous_pid = previous.get("runtime", {}).get("instrumentation_pid")
    current_pid = runtime["instrumentation_pid"]
    distinct_process = (
        boundary.get("all_absent_after_force_stop") is True
        and previous_pid is not None
        and current_pid != previous_pid
    )
    process_condition = stage == "roundtrip" or distinct_process
    if not process_condition:
        raise RuntimeError("A distinct post-force-stop instrumentation process was not proven")
    revoke_requested = stage == "revoke"
    receipt = {
        "schema": 1,
        "gate": "api21-real-picker-saf",
        "gate_id": preflight["gate_id"],
        "stage": stage,
        "ordinal": STAGE_ORDINAL[stage],
        "passed": True,
        "created": now(),
        "candidate": preflight["candidate"],
        "device": {
            key: preflight["device"][key]
            for key in ("serial", "api", "fingerprint", "abis", "boot_id")
        },
        "tree": preflight["tree"],
        "prior_phase_receipts": prior,
        "installed_packages_before": before_packages,
        "installed_packages_after": after_packages,
        "preferences_before": before_preferences,
        "preferences_after": after_preferences,
        "process_boundary": {
            **boundary,
            "previous_instrumentation_pid": previous_pid,
            "current_instrumentation_pid": current_pid,
            "distinct_process_after_boundary": distinct_process,
            "phase_process_condition_satisfied": process_condition,
        },
        "revoke_contract": {
            "requested": revoke_requested,
            "picker_force_stopped": boundary.get("picker_force_stopped") is True,
            "target_and_test_force_stopped": boundary.get("target_and_test_force_stopped") is True,
            "probe_requires_security_exception_after_permission_release": revoke_requested,
            "permission_released_and_access_denied": runtime.get("condition", {}).get(
                "permission_released_and_access_denied"
            ) is True,
        },
        "runtime": runtime,
        "drivers": {
            "gate": {"path": str(Path(__file__).resolve()), "sha256": sha256(Path(__file__).resolve())},
            "runtime": {"path": str(RUNTIME_DRIVER), "sha256": sha256(RUNTIME_DRIVER)},
        },
    }
    if stage == "revoke" and not all(
        (
            receipt["revoke_contract"]["picker_force_stopped"],
            receipt["revoke_contract"]["target_and_test_force_stopped"],
            receipt["revoke_contract"]["permission_released_and_access_denied"],
        )
    ):
        raise RuntimeError("Revoke preconditions were not proven")
    return receipt


def run_phase(args) -> int:
    args.adb = args.adb.resolve()
    args.candidate = args.candidate.resolve()
    output = validate_output_path(args.output)
    candidate = validate_candidate(args.candidate, args.commit)
    if not args.adb.is_file() or not os.access(args.adb, os.X_OK):
        raise FileNotFoundError(f"Executable ADB not found: {args.adb}")
    if not RUNTIME_DRIVER.is_file():
        raise FileNotFoundError(RUNTIME_DRIVER)

    if args.stage == "roundtrip":
        output.mkdir(parents=True, exist_ok=False)
    elif not output.is_dir():
        raise FileNotFoundError(f"Start with the roundtrip phase: {output}")

    command_log = output / "commands.jsonl"
    device = Device(args.adb, args.serial, command_log)
    summary_path = output / "summary.json"
    state_path = output / "gate-state.json"
    preflight_path = output / "preflight.json"
    preflight = None
    state = None
    try:
        with device_audit_lock(args.serial):
            device_identity = validate_device(device, candidate)
            if args.stage == "roundtrip":
                interaction, _, selected_preferences = picker_interaction(device, output)
                tree = interaction["selected_tree"]
                gate_id = str(uuid.uuid4())
                preflight = {
                    "schema": 1,
                    "gate": "api21-real-picker-saf",
                    "gate_id": gate_id,
                    "output": str(output),
                    "candidate": candidate,
                    "device": device_identity,
                    "tree": tree,
                    "picker_interaction": interaction,
                    "drivers": {
                        "gate": {
                            "path": str(Path(__file__).resolve()),
                            "sha256": sha256(Path(__file__).resolve()),
                        },
                        "runtime": {
                            "path": str(RUNTIME_DRIVER),
                            "sha256": sha256(RUNTIME_DRIVER),
                        },
                    },
                    "contract": {
                        "stage_order": list(STAGES),
                        "same_candidate": True,
                        "same_installed_apks": True,
                        "same_device_boot": True,
                        "same_tree_uri": True,
                        "restart_requires_force_stop": True,
                        "revoke_requires_picker_and_app_force_stop": True,
                    },
                    "created": now(),
                }
                write_json(preflight_path, preflight)
                state = initial_state(gate_id, candidate, device_identity, tree)
                write_json(state_path, state)
                write_json(summary_path, make_summary(preflight, state))
                before_raw = selected_preferences
                prior = []
            else:
                preflight = load_json(preflight_path)
                state = load_json(state_path)
                require_same_contract(preflight, candidate, device_identity)
                validate_resume_contract(output, preflight, state)
                prior = validate_prior_receipts(output, state, args.stage)
                before_raw = device.preference_xml()
                validate_preferences(before_raw, state["tree_uri"])

            tree_uri = preflight["tree"]["uri"]
            before_preferences = validate_preferences(before_raw, tree_uri)
            before_packages = device.installed_candidate(candidate)
            if args.stage == "roundtrip":
                boundary = {
                    "required": False,
                    "all_absent_after_force_stop": False,
                    "picker_force_stopped": False,
                    "target_and_test_force_stopped": False,
                    "picker_grant_created_by_interaction": True,
                }
            else:
                boundary = stopped_boundary(device, preflight["device"]["picker_packages"])

            phase = output / args.stage
            run_runtime_phase(args, phase, tree_uri)
            if not phase.is_dir():
                raise RuntimeError("Runtime driver did not create phase evidence")
            (phase / "preferences-before.xml").write_bytes(before_raw)
            after_raw = device.preference_xml()
            (phase / "preferences-after.xml").write_bytes(after_raw)
            after_preferences = validate_preferences(after_raw, tree_uri)
            after_packages = device.installed_candidate(candidate)
            if before_packages != after_packages:
                raise RuntimeError("Installed APK identity changed during the SAF phase")
            runtime = validate_runtime_evidence(phase, args.stage, candidate, tree_uri)
            receipt = phase_receipt(
                output,
                args.stage,
                preflight,
                prior,
                before_packages,
                after_packages,
                before_preferences,
                after_preferences,
                boundary,
                runtime,
            )
            receipt_path = phase / "phase-receipt.json"
            write_json(receipt_path, receipt)
            state["completed_stages"].append(args.stage)
            state["receipt_sha256"][args.stage] = sha256(receipt_path)
            state["updated"] = now()
            write_json(state_path, state, replace=True)
            write_json(summary_path, make_summary(preflight, state), replace=True)
    except Exception as error:
        failure = f"{type(error).__name__}: {error}"
        failure_path = output / f"failure-{args.stage}.txt"
        if failure_path.exists():
            failure_path = output / f"failure-{args.stage}-{uuid.uuid4()}.txt"
        failure_path.write_text(
            traceback.format_exc(), encoding="utf-8"
        )
        if preflight is not None and state is not None:
            write_json(
                summary_path,
                make_summary(preflight, state, error=failure),
                replace=summary_path.exists(),
            )
        print(
            json.dumps(
                {
                    "passed": False,
                    "stage": args.stage,
                    "error": failure,
                    "evidence": str(output),
                },
                ensure_ascii=False,
            )
        )
        return 1

    summary = load_json(summary_path)
    print(
        json.dumps(
            {
                "passed": True,
                "stage": args.stage,
                "gate_passed": summary["passed"],
                "completed_stages": summary["completed_stages"],
                "evidence": str(output),
            },
            ensure_ascii=False,
        ),
        flush=True,
    )
    return 0


def selftest() -> int:
    assert MAIN_ACTIVITY == (
        "com.osfans.trime.debug/com.osfans.trime.ui.main.MainActivity"
    )
    allowed_output = RUNTIME_ROOT / "offline-selftest-output"
    assert validate_output_path(allowed_output) == allowed_output.resolve()
    for unsafe_output in (
        T07_ROOT / "stable-candidate18/api21-saf",
        RUNTIME_ROOT / "nested/api21-saf",
    ):
        try:
            validate_output_path(unsafe_output)
        except ValueError:
            pass
        else:
            raise AssertionError(f"Unsafe evidence output accepted: {unsafe_output}")
    good_leaf = "trime-saf-87390960-18de-41e8-b0f5-5a924e9c796e"
    good_uri = (
        "content://com.android.externalstorage.documents/tree/"
        "0000-0000%3ADocuments%2F" + good_leaf
    )
    assert parse_tree_uri(good_uri)["leaf"] == good_leaf
    rejected = (
        "file:///sdcard/Documents/" + good_leaf,
        "content://evil.example/tree/primary%3ADocuments%2F" + good_leaf,
        "content://com.android.externalstorage.documents/tree/primary%3A" + good_leaf,
        "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2Fpersonal",
        good_uri + "/document/child",
    )
    for value in rejected:
        try:
            parse_tree_uri(value)
        except ValueError:
            pass
        else:
            raise AssertionError(f"Unsafe tree URI accepted: {value}")

    preferences = (
        "<?xml version='1.0' encoding='utf-8'?><map>"
        f"<string name='profile_external_rime_tree_uri'>{good_uri}</string>"
        "<string name='profile_external_rime_display_name'>" + good_leaf + "</string>"
        "<string name='profile_data_storage_mode'>EXTERNAL_SYNC</string>"
        "<int name='general__pid' value='1234'/></map>"
    ).encode()
    parsed = validate_preferences(preferences, good_uri)
    assert parsed["application_pid"] == 1234
    try:
        validate_preferences(preferences.replace(b"EXTERNAL_SYNC", b"APP_STORAGE"), good_uri)
    except RuntimeError:
        pass
    else:
        raise AssertionError("Non-external storage mode was accepted")

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        state = {
            "schema": 1,
            "gate": "api21-real-picker-saf",
            "gate_id": "gate",
            "candidate_identity_sha256": "a" * 64,
            "device_boot_id": "boot",
            "tree_uri": good_uri,
            "expected_stages": list(STAGES),
            "completed_stages": [],
            "receipt_sha256": {},
        }
        picker_before = root / "picker-preferences-before.xml"
        picker_after = root / "picker-preferences-after.xml"
        picker_before.write_text("<map/>")
        picker_after.write_bytes(preferences)
        tree = parse_tree_uri(good_uri)
        preflight = {
            "schema": 1,
            "gate": "api21-real-picker-saf",
            "gate_id": "gate",
            "output": str(root),
            "candidate": {"identity_sha256": "a" * 64},
            "device": {"boot_id": "boot"},
            "tree": tree,
            "picker_interaction": {
                "requested_leaf": good_leaf,
                "operator_confirmation": good_leaf,
                "previous_tree_uri": "",
                "selected_tree": tree,
                "preferences_before_sha256": sha256(picker_before),
                "preferences_after_sha256": sha256(picker_after),
            },
        }
        validate_resume_contract(root, preflight, state)
        picker_after.write_text("<map/>")
        try:
            validate_resume_contract(root, preflight, state)
        except RuntimeError:
            pass
        else:
            raise AssertionError("Changed picker evidence was accepted")
        picker_after.write_bytes(preferences)
        assert validate_prior_receipts(root, state, "roundtrip") == []
        try:
            validate_prior_receipts(root, state, "restart")
        except RuntimeError:
            pass
        else:
            raise AssertionError("Restart without roundtrip was accepted")
        phase = root / "roundtrip"
        phase.mkdir()
        receipt = {
            "passed": True,
            "stage": "roundtrip",
            "gate_id": "gate",
            "candidate": {"identity_sha256": "a" * 64},
            "device": {"boot_id": "boot"},
            "tree": {"uri": good_uri},
        }
        write_json(phase / "phase-receipt.json", receipt)
        state["completed_stages"] = ["roundtrip"]
        state["receipt_sha256"]["roundtrip"] = sha256(phase / "phase-receipt.json")
        prior = validate_prior_receipts(root, state, "restart")
        assert len(prior) == 1 and prior[0]["stage"] == "roundtrip"
        receipt["passed"] = False
        write_json(phase / "phase-receipt.json", receipt, replace=True)
        try:
            validate_prior_receipts(root, state, "restart")
        except RuntimeError:
            pass
        else:
            raise AssertionError("Altered prior receipt was accepted")

        runtime_phase = root / "runtime-roundtrip"
        runtime_phase.mkdir()
        candidate = {
            "artifacts": {
                PACKAGE: {"sha256": "b" * 64},
                TEST_PACKAGE: {"sha256": "c" * 64},
            }
        }
        write_json(
            runtime_phase / "identity.json",
            {
                "api": "21",
                "probe": "saf",
                "source_sha256": sha256(RUNTIME_DRIVER),
                "packages": {
                    PACKAGE: {"apk_sha256": "b" * 64},
                    TEST_PACKAGE: {"apk_sha256": "c" * 64},
                },
            },
        )
        write_json(
            runtime_phase / "report.json",
            {
                "functional_passed": True,
                "assertions_passed": True,
                "injects_faults": True,
                "requires_log_review": False,
                "warning_free": True,
                "warning_count": 0,
                "tested_ime_diagnostic_count": 0,
                "full_probe_diagnostic_count": 0,
            },
        )
        write_json(
            runtime_phase / "runtime-audit.json",
            {
                "capture_has_records": True,
                "warning_free": True,
                "warnings": [],
                "known_project_diagnostics": [],
            },
        )
        instrumentation = "\n".join(
            (
                "INSTRUMENTATION_STATUS: audit_pid=4321",
                "INSTRUMENTATION_STATUS: stream=PASS: real SAF import preserves untracked files",
                "INSTRUMENTATION_RESULT: checks=1",
                "INSTRUMENTATION_RESULT: passed=true",
                "INSTRUMENTATION_RESULT: stream=PASS: 1 SAF checks; "
                "system provider persisted grant and round trip",
                "INSTRUMENTATION_CODE: -1",
                "",
            )
        )
        (runtime_phase / "instrumentation.log").write_text(instrumentation)
        (runtime_phase / "logcat-full.txt").write_text("record\n")
        (runtime_phase / "app-logcat.txt").write_text("record\n")
        runtime = validate_runtime_evidence(runtime_phase, "roundtrip", candidate, good_uri)
        assert runtime["condition"]["persisted_grant_and_roundtrip"] is True
        broken = instrumentation.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0")
        (runtime_phase / "instrumentation.log").write_text(broken)
        try:
            validate_runtime_evidence(runtime_phase, "roundtrip", candidate, good_uri)
        except RuntimeError:
            pass
        else:
            raise AssertionError("Failed instrumentation bundle was accepted")

    print(json.dumps({"passed": True, "tests": 19, "mode": "offline"}))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", choices=STAGES)
    parser.add_argument("--candidate", type=Path)
    parser.add_argument("--commit")
    parser.add_argument("--adb", type=Path, help="Executable adb; otherwise resolve SDK environment/PATH")
    parser.add_argument("--serial", default="emulator-5560")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()
    if args.selftest:
        if (
            args.stage is not None
            or args.candidate is not None
            or args.commit is not None
            or args.output is not None
        ):
            parser.error(
                "--selftest cannot be combined with a runtime stage/candidate/commit/output"
            )
        return selftest()
    if args.stage is None:
        parser.error("--stage is required")
    if args.candidate is None:
        parser.error("--candidate is required for a runtime stage")
    if args.commit is None:
        parser.error("--commit is required for a runtime stage")
    if args.output is None:
        parser.error("--output is required")
    args.adb = resolve_adb(args.adb)
    return run_phase(args)


if __name__ == "__main__":
    sys.exit(main())
