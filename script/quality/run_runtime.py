#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Run a single runtime probe with pinned APKs and full-duration log capture."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid

from runtime_audit import audit_log, device_audit_lock, filter_pid_log, instrumentation_passed, instrumentation_pid, log_after_marker, record_logcat


PROBES = (
    "setup", "engine", "shutdown", "startupFailure", "lateStorage", "feedback",
    "t02", "t08", "t09", "clip", "clipSave", "saf", "editors", "privacy",
)


T09_TASKS = {
    "android16": "Android 16", "wifi": "Wi-Fi", "b_site": "B站",
    "email": "trime.test+16@example.org", "url": "https://example.org/a-b?x=16&lang=zh",
    "phone": "+8613800138000", "amount": "-12.50",
}
T09_REGRESSIONS = {"raw_64_symbols", "locked_ni_symbols", "chinese_editor_restore", "manual_english_editor_restore"}
T09_THEMES = {"trime", "tongwenfeng.trime"}


def t09_product_sources(reference, baseline, root):
    """Freeze production bytes; androidTest/host-only edits are outside product scope."""
    import os

    if not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_./-]*", reference):
        raise ValueError("Explicit published T08 source ref required")
    root = root.resolve()
    command = ["git", "-C", str(root)]
    published = subprocess.check_output(command + ["rev-parse", reference + "^{commit}"], text=True).strip()
    current = subprocess.check_output(command + ["rev-parse", "HEAD"], text=True).strip()
    prefixes = ("app/", "build-logic/", "codegen/", "gradle/")
    roots = {"build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat", ".gitmodules"}
    def tree(repository, ref):
        result = {}
        for raw in subprocess.check_output(["git", "-C", str(repository), "ls-tree", "-rz", ref]).split(b"\0"):
            if not raw:
                continue
            metadata, name = raw.split(b"\t", 1)
            name = name.decode()
            result[name] = metadata.decode().split()
        return result
    published_tree, current_tree = tree(root, published), tree(root, current)
    def entries(ref):
        full = published_tree if ref == published else current_tree
        return {name: metadata for name, metadata in full.items() if
                (name.startswith(prefixes) or name in roots) and not name.startswith(("app/src/androidTest/", "app/src/test/"))}
    before, now = entries(published), entries(current)
    untracked = subprocess.check_output(command + ["ls-files", "--others", "--exclude-standard", "-z"]).split(b"\0")
    unexpected = [name.decode() for name in untracked if name and
                  (name.decode().startswith(prefixes) or name.decode() in roots) and
                  not name.decode().startswith(("app/src/androidTest/", "app/src/test/"))]
    if unexpected:
        raise ValueError("Untracked product inputs exist: " + ", ".join(unexpected))
    if baseline and before != now:
        raise ValueError("Baseline production tree differs from published T08; only test/host changes allowed")
    repositories = {root: current_tree}
    submodules = {}
    def verify_submodules(repository, full):
        for name, (mode, kind, expected) in full.items():
            if kind != "commit":
                continue
            path = repository / name
            label = str(path.relative_to(root))
            if mode != "160000" or path.is_symlink() or not path.is_dir() or not path.resolve().is_relative_to(root):
                raise ValueError("Invalid pinned submodule path: " + label)
            actual = subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()
            dirty = subprocess.check_output(["git", "-C", str(path), "status", "--porcelain", "--untracked-files=all", "--ignore-submodules=none"], text=True)
            if actual != expected or dirty:
                raise ValueError("Pinned submodule differs: " + label)
            submodules[label] = actual
            nested = tree(path, actual)
            repositories[path] = nested
            verify_submodules(path, nested)
    verify_submodules(root, current_tree)

    def regular_blob(repository, name, metadata):
        mode, kind, expected = metadata
        path = repository / name
        if kind != "blob" or mode not in {"100644", "100755"} or path.is_symlink() or not path.is_file():
            raise ValueError("Expected tracked regular product file: " + str(path.relative_to(root)))
        if bool(path.stat().st_mode & 0o111) != (mode == "100755"):
            raise ValueError("Product executable mode differs: " + str(path.relative_to(root)))
        actual = subprocess.check_output(["git", "-C", str(repository), "hash-object", "--no-filters", "--", name], text=True).strip()
        if actual != expected:
            raise ValueError("Uncommitted product bytes differ: " + str(path.relative_to(root)))

    linked_targets = {}
    linked_dependencies_unchanged = True
    for name, metadata in now.items():
        mode, kind, expected = metadata
        if kind == "commit":
            continue # All levels were checked against their parent gitlink above.
        path = root / name
        if kind == "blob" and mode == "120000":
            if not path.is_symlink():
                raise ValueError("Tracked product symlink was replaced: " + name)
            link_bytes = os.readlink(os.fsencode(path))
            try:
                link_bytes.decode("utf-8")
                target = path.resolve(strict=True)
            except (UnicodeError, OSError, RuntimeError) as failure:
                raise ValueError("Product symlink target is unavailable: " + name) from failure
            actual = subprocess.check_output(command + ["hash-object", "--stdin"], input=link_bytes).decode().strip()
            if actual != expected or not target.is_relative_to(root) or not target.is_file():
                raise ValueError("Product symlink bytes/target differ: " + name)
            owner = max((repository for repository in repositories if target.is_relative_to(repository)), key=lambda repository: len(repository.parts))
            target_name = str(target.relative_to(owner))
            target_metadata = repositories[owner].get(target_name)
            if target_metadata is None:
                raise ValueError("Product symlink target is not tracked/pinned: " + name)
            if owner == root:
                dependency_unchanged = published_tree.get(target_name) == target_metadata
            else:
                top = min((repository for repository in repositories if repository != root and target.is_relative_to(repository)), key=lambda repository: len(repository.parts))
                top_name = str(top.relative_to(root))
                dependency_unchanged = published_tree.get(top_name) == current_tree.get(top_name)
            linked_dependencies_unchanged = linked_dependencies_unchanged and dependency_unchanged
            if baseline and not dependency_unchanged:
                raise ValueError("Product symlink dependency differs from published T08: " + name)
            regular_blob(owner, target_name, target_metadata)
            linked_targets[name] = {"target": str(target.relative_to(root)), "metadata": target_metadata}
        else:
            regular_blob(root, name, metadata)
    encoded = json.dumps({"product": now, "linked_targets": linked_targets, "submodules": submodules}, sort_keys=True, separators=(",", ":")).encode()
    return {"published_ref": reference, "published_commit": published, "current_commit": current,
            "production_tree_sha256": hashlib.sha256(encoded).hexdigest(), "production_files": len(now),
            "published_production_bytes_unchanged": before == now and linked_dependencies_unchanged, "baseline": baseline,
            "tracked_symlink_targets": linked_targets, "verified_recursive_submodules": submodules}


"""Post-baseline ignored FIFO guard; pure report validation, no commands on import."""

T09_RAPID_IDS = {"rapid_cn_en_a_cn", "rapid_cn_en_a_cn_64_space"}


def validate_t09_ascii_key_delivery(row):
    """AOSP sendKeyEvent returns false after dispatch; prove actual View delivery."""
    calls, recipients = row.get("ic_calls"), row.get("key_recipient_calls")
    if not isinstance(calls, list) or not isinstance(recipients, list):
        raise ValueError("T09 ASCII IC/View delivery records missing")
    sent = [call for call in calls if call.get("method") == "sendKeyEvent" and call.get("text") in {"0:29", "1:29"}]
    received = [call for call in recipients if call.get("key_code") == 29]
    if len(sent) != 2 or len(received) != 2 or [call.get("text") for call in sent] != ["0:29", "1:29"] or [call.get("action") for call in received] != [0, 1]:
        raise ValueError("T09 ASCII needs one ordered IC/View down/up pair")
    released = row["gate"]["release_ns"]
    field = row["prepared"]["field_id"]
    if type(released) is not int or released <= 0 or type(field) is not int:
        raise ValueError("T09 ASCII gate/editor field identity differs")
    for action, call, recipient in zip((0, 1), sent, received):
        if (type(call.get("returned")) is not bool or type(recipient.get("returned")) is not bool or
                type(recipient.get("action")) is not int or recipient["action"] != action or
                recipient.get("method") != ("onKeyDown" if action == 0 else "onKeyUp") or recipient.get("on_main_thread") is not True or
                any(type(record.get(name)) is not int for record in (call, recipient) for name in (
                    "t_ns", "completed_ns", "field_id", "editor_object", "down_time_ms", "event_time_ms")) or
                not released <= call["t_ns"] <= call["completed_ns"] or
                not call["t_ns"] <= recipient["t_ns"] <= recipient["completed_ns"] or
                not 0 < call["down_time_ms"] <= call["event_time_ms"] or
                call["field_id"] != field or recipient["field_id"] != field or
                any(recipient[name] != call[name] for name in ("editor_object", "down_time_ms", "event_time_ms"))):
            raise ValueError("T09 ASCII completed IC event did not reach its actual editor View")
    if (sent[0]["editor_object"] != sent[1]["editor_object"] or sent[0]["down_time_ms"] != sent[1]["down_time_ms"] or
            sent[0]["t_ns"] > sent[1]["t_ns"] or received[0]["t_ns"] > received[1]["t_ns"]):
        raise ValueError("T09 ASCII IC/View ownership or down/up order differs")


def validate_t09_ascii_text_delivery(row, chinese=False):
    """A virtual printable fallback must finalize text once before later composition."""
    calls, recipients = row.get("ic_calls"), row.get("key_recipient_calls")
    if (not isinstance(calls, list) or not isinstance(recipients, list) or
            any(not isinstance(call, dict) for call in calls + recipients)):
        raise ValueError("T09 virtual ASCII actual IC/View records missing")
    if any(call.get("method") == "sendKeyEvent" for call in calls) or recipients:
        raise ValueError("T09 virtual ASCII text also used an unexpected asynchronous key-event path")
    ascii_calls = [call for call in calls if call.get("method") == "commitText" and call.get("text") == "a"]
    def finalizations(value):
        return [call for call in calls if call.get("returned") is True and (
            (call.get("method") == "commitText" and call.get("text") == value) or
            (call.get("method") == "finishComposingText" and call.get("composing_before") == value and call.get("composing_after") is None))]
    if len(ascii_calls) != 1 or finalizations("a") != ascii_calls:
        raise ValueError("T09 virtual ASCII lacks exactly one successful commitText a")
    ascii_call = ascii_calls[0]
    if (any(name not in ascii_call or ascii_call[name] is not None for name in ("composing_before", "composing_after"))):
        raise ValueError("T09 virtual ASCII text unexpectedly used a composing range")
    preedit = [call for call in calls if call.get("method") == "setComposingText"]
    native = finalizations("你")
    text_calls = [call for call in calls if call.get("method") == "commitText" or
                  call.get("method") == "finishComposingText" and call.get("composing_before") is not None]
    if ([call.get("text") for call in preedit] != (["6", "64"] if chinese else []) or
            len(native) != (1 if chinese else 0) or text_calls != ascii_calls + native):
        raise ValueError("T09 virtual ASCII/native preedit or extra text finalization differs")
    released, field = row.get("gate", {}).get("release_ns"), row.get("prepared", {}).get("field_id")
    if type(released) is not int or released <= 0 or type(field) is not int:
        raise ValueError("T09 virtual ASCII original gate/editor differs")
    previous, editor_object = released, ascii_call.get("editor_object")
    if type(editor_object) is not int:
        raise ValueError("T09 virtual ASCII actual editor object missing")
    for call in ascii_calls + preedit + native:
        if (call.get("returned") is not True or call.get("on_main_thread") is not True or
                any(type(call.get(name)) is not int for name in ("t_ns", "completed_ns", "field_id", "editor_object")) or
                call["field_id"] != field or call["editor_object"] != editor_object or
                not previous <= call["t_ns"] <= call["completed_ns"]):
            raise ValueError("T09 virtual ASCII text/native sequence lacks same-editor Main completion in FIFO order")
        previous = call["completed_ns"]


def validate_t09_rapid(report, baseline, schema):
    rows = report.get("rapid_regressions", [])
    if baseline:
        if rows:
            raise ValueError("T09 baseline must not execute post-implementation rapid cases")
        return {"rapid_scope": "NOT_RUN_BASELINE", "rapid_regressions": 0, "rapid_passed": True}
    expected = {(theme, name) for theme in T09_THEMES for name in T09_RAPID_IDS}
    if not isinstance(rows, list) or len(rows) != 4 or {(row.get("theme"), row.get("id")) for row in rows} != expected:
        raise ValueError("T09 four separate rapid regressions missing or duplicate")
    for row in rows:
        if row.get("status") not in {"passed", "failed"}:
            raise ValueError("T09 rapid outcome incomplete or unsupported")
        if (row.get("scope") != "explicit_visible_commands_fifo" or row.get("excluded_from_fixed_task_costs") is not True or
                any(type(row.get(name)) is not int or row[name] != 0 for name in ("interkey_queue_barriers", "interkey_native_queries"))):
            raise ValueError("T09 rapid scope contains a query/barrier or fixed-task cost")
        actions = row.get("actions")
        counts = row.get("counts", {})
        if (not isinstance(actions, list) or counts.get("total_actions") != len(actions) or
                counts.get("dispatched_actions") != sum(action.get("dispatched") is True for action in actions)):
            raise ValueError("T09 rapid attempted/dispatched counts differ")
        if row["status"] == "failed":
            if not row.get("reason"):
                raise ValueError("T09 rapid failure lacks retained reason")
            continue
        chinese = row["id"] == "rapid_cn_en_a_cn_64_space"
        target = "a你" if chinese else "a"
        roles = ["english", "ascii_a", "chinese"] + (["native_6", "native_4", "native_space"] if chinese else [])
        if row.get("expected_text") != target or len(actions) != len(roles):
            raise ValueError("T09 rapid sequence/target differs")
        prepared, final, delayed = (row.get(name, {}) for name in ("prepared", "final", "post_delay_state"))
        theme_hash = report["effective_theme_identity"][row["theme"]]["effective_theme_sha256"]
        isolated = prepared.get("manual_language_preference", {})
        if (prepared.get("ascii_mode") is not False or prepared.get("t9_layout") is not True or
                prepared.get("schema_id") != schema or prepared.get("raw_input") != "" or prepared.get("editor_text") != "" or
                prepared.get("effective_theme_sha256") != theme_hash or isolated.get("status") != "registered" or
                isolated.get("exists") is not False or isolated.get("raw") is not None):
            raise ValueError("T09 rapid did not start at isolated actual Chinese T9")
        for state in (final, delayed):
            if (state.get("editor_text") != target or state.get("raw_input") != "" or state.get("ascii_mode") is not False or
                    state.get("t9_layout") is not True or state.get("schema_id") != schema or
                    state.get("editor_token") != prepared.get("editor_token") or state.get("composing_span") != [-1, -1] or
                    state.get("selection") != [len(target), len(target)]):
                raise ValueError("T09 rapid exact text/native/editor/final Chinese state differs")
        gate = row.get("gate", {})
        entered, released, joined = (gate.get(name) for name in ("entered_ns", "release_ns", "joined_ns"))
        anchor = gate.get("identity", {})
        if (gate.get("original_service_post_rime_job") is not True or gate.get("single_main_dispatch") is not True or
                gate.get("cancelled") is not False or any(type(value) is not int or value <= 0 for value in (entered, released, joined)) or
                not entered < released <= joined or anchor.get("editor_token") != prepared.get("editor_token") or
                anchor.get("schema_id") != schema or anchor.get("generation") != prepared.get("generation") or
                anchor.get("snapshot_editor") != prepared.get("snapshot_editor") or anchor.get("snapshot_request") != prepared.get("snapshot_request") or
                anchor.get("theme") != row["theme"] or anchor.get("field_id") != prepared.get("field_id") or
                any(type(anchor.get(key)) is not int for key in ("editor_token", "generation", "snapshot_editor", "snapshot_request", "theme_object", "input_object"))):
            raise ValueError("T09 rapid lacks entered original FIFO gate and ownership")
        previous = entered
        for index, (action, role) in enumerate(zip(actions, roles), 1):
            begin, end = action.get("callback_begin_ns"), action.get("callback_end_ns")
            if (action.get("seq") != index or action.get("driver") != "logical_key_action" or action.get("role") != role or
                    action.get("behavior") != ("long" if index == 1 else "click") or action.get("dispatched") is not True or
                    action.get("identity") != anchor or action.get("identity_after") != anchor or action.get("gate_released") is not False or
                    type(begin) is not int or type(end) is not int or not previous <= begin <= end < released or
                    any(type(action.get(key)) is not int for key in ("key_index", "keyboard_object", "key_view_object")) or
                    action["key_index"] < 0 or not action.get("visible_rect") or not action.get("keyboard_id")):
                raise ValueError("T09 rapid callback did not stay visible/in-order/inside gate")
            previous = end
            resolved = action.get("resolved_action", {})
            if (index == 1 and (resolved.get("code") != 212 or resolved.get("select") != ".ascii" or action.get("displayed_label") != "EN")):
                raise ValueError("T09 rapid English is not actual visible long EN")
            if index == 2 and (resolved.get("code") != 29 or resolved.get("modifier") != 0 or resolved.get("select") or resolved.get("toggle")):
                raise ValueError("T09 rapid letter is not the actual unshifted A action")
            if index == 3 and not ((resolved.get("code") == 212 and resolved.get("select") == ".native") or
                                  (row["theme"] == "trime" and resolved.get("toggle") == "ascii_mode" and resolved.get("code") == 95)):
                raise ValueError("T09 rapid Chinese return is not an actual visible command")
            if index > 3 and resolved.get("code") != {4: 13, 5: 11, 6: 62}[index]:
                raise ValueError("T09 rapid native digit/Space action differs")
            if (action.get("t9_layout") is not (index == 1 or index > 3) or
                    action.get("t9_layout_after") is not (index >= 3) or
                    (index > 3 and resolved.get("modifier") != 0)):
                raise ValueError("T09 rapid actual letter/T9 layout or native modifiers differ")
        options = [event for event in row.get("native_events", []) if event.get("event") == "ascii_option"]
        commits = [event for event in row.get("native_events", []) if event.get("event") == "native_commit" and event.get("text")]
        if (any(type(event.get("value")) is not bool for event in options) or [event.get("value") for event in options] != [True, False] or
                [event.get("text") for event in commits] != (["你"] if chinese else []) or
                any(event.get("editor") != final["editor_token"] for event in commits) or
                any(type(event.get("t_ns")) is not int or event["t_ns"] < released for event in options + commits)):
            raise ValueError("T09 rapid native options/commits lost or reordered")
        calls = row.get("ic_calls", [])
        modes = row.get("submission_modes", {})
        delivery = modes.get("a")
        if delivery == "native_unhandled_key_to_real_ic_keyevent":
            validate_t09_ascii_key_delivery(row)  # Historical producer contract remains exact.
        elif delivery == "virtual_printable_ascii_to_real_ic_text":
            validate_t09_ascii_text_delivery(row, chinese)
        else:
            raise ValueError("T09 rapid ASCII submission mode is unknown")
        def finalizations(value):
            return [call for call in calls if call.get("returned") is True and (
                (call.get("method") == "commitText" and call.get("text") == value) or
                (call.get("method") == "finishComposingText" and call.get("composing_before") == value and call.get("composing_after") is None))]
        if (len(finalizations("a")) != (1 if delivery == "virtual_printable_ascii_to_real_ic_text" else 0) or
                len(finalizations("你")) != (1 if chinese else 0) or
                modes.get("你") != ("native_commit_to_real_ic_finalization" if chinese else "not_requested")):
            raise ValueError("T09 rapid ASCII/native real editor submission proof differs")
    return {"rapid_scope": "explicit_visible_commands_fifo", "rapid_regressions": 4,
            "rapid_passed": all(row["status"] == "passed" for row in rows), "rapid_excluded_from_fixed_task_costs": True}


"""Four post-baseline actual onText cases; independent of rapid4 and fixed14 costs."""
T09_MACRO_SOURCES = {"macro_ascii_text": "{Keyboard_ascii}a{Keyboard_native}",
                     "macro_ascii_key": "{Keyboard_ascii}{a}{Keyboard_native}"}


def validate_t09_macros(report, baseline, schema):
    rows = report.get("macro_regressions", [])
    if baseline:
        if rows:
            raise ValueError("Baseline must not run post-implementation macros")
        return {"macro_scope": "NOT_RUN_BASELINE", "macro_regressions": 0, "macro_passed": True}
    expected = {(theme, name) for theme in T09_THEMES for name in T09_MACRO_SOURCES}
    if not isinstance(rows, list) or len(rows) != 4 or {(row.get("theme"), row.get("id")) for row in rows} != expected:
        raise ValueError("T09 four actual macro cases missing or duplicate")
    for row in rows:
        source = T09_MACRO_SOURCES[row["id"]]
        if (row.get("scope") != "real_on_text_entry_original_fifo" or row.get("excluded_from_fixed_task_costs") is not True or
                row.get("macro_source") != source or row.get("macro_source_sha256") != hashlib.sha256(source.encode()).hexdigest() or
                any(type(row.get(name)) is not int or row[name] != 0 for name in ("interkey_queue_barriers", "interkey_native_queries")) or
                row.get("status") not in {"passed", "failed"}):
            raise ValueError("T09 actual macro scope/source/outcome differs")
        actions = row.get("actions", [])
        if (not isinstance(actions, list) or row.get("counts", {}).get("total_actions") != len(actions) or
                row.get("counts", {}).get("dispatched_actions") != sum(action.get("dispatched") is True for action in actions)):
            raise ValueError("T09 macro callback counts differ")
        if row["status"] == "failed":
            if not row.get("reason"):
                raise ValueError("T09 macro failure lacks reason")
            continue
        prepared = row.get("prepared", {})
        isolated = prepared.get("manual_language_preference", {})
        if (prepared.get("ascii_mode") is not False or prepared.get("t9_layout") is not True or prepared.get("schema_id") != schema or
                prepared.get("editor_text") != "" or prepared.get("raw_input") != "" or isolated.get("status") != "registered" or
                isolated.get("exists") is not False or isolated.get("raw") is not None or
                prepared.get("effective_theme_sha256") != report["effective_theme_identity"][row["theme"]]["effective_theme_sha256"]):
            raise ValueError("T09 macro preparation is not isolated actual Chinese T9")
        for name in ("final", "post_delay_state"):
            state = row.get(name, {})
            if (state.get("editor_text") != "a" or state.get("raw_input") != "" or state.get("ascii_mode") is not False or
                    state.get("t9_layout") is not True or state.get("schema_id") != schema or
                    state.get("editor_token") != prepared.get("editor_token") or state.get("composing_span") != [-1, -1] or state.get("selection") != [1, 1]):
                raise ValueError("T09 actual macro final/late text/native/editor state differs")
        gate = row.get("gate", {})
        entered, released, joined = (gate.get(name) for name in ("entered_ns", "release_ns", "joined_ns"))
        anchor = gate.get("identity", {})
        if (gate.get("original_service_post_rime_job") is not True or gate.get("single_main_dispatch") is not True or
                gate.get("cancelled") is not False or any(type(value) is not int or value <= 0 for value in (entered, released, joined)) or
                not entered < released <= joined or any(anchor.get(name) != prepared.get(name) for name in (
                    "editor_token", "field_id", "schema_id", "generation", "snapshot_editor", "snapshot_request")) or
                anchor.get("theme") != row["theme"] or any(type(anchor.get(name)) is not int for name in ("theme_object", "input_object")) or len(actions) != 1):
            raise ValueError("T09 macro is not within its original queue gate/owner")
        action = actions[0]
        begin, end = action.get("callback_begin_ns"), action.get("callback_end_ns")
        if (action.get("seq") != 1 or action.get("driver") != "logical_input_macro" or action.get("behavior") != "macro" or
                action.get("role") != "macro" or action.get("macro_source") != source or action.get("dispatched") is not True or
                action.get("gate_released") is not False or action.get("identity") != anchor or action.get("identity_after") != anchor or
                type(begin) is not int or type(end) is not int or not entered <= begin <= end < released):
            raise ValueError("T09 macro did not call the actual onText entry once inside the gate")
        for name, select in (("resolved_english", ".ascii"), ("resolved_chinese", ".native")):
            resolved = action.get(name, {})
            if resolved.get("code") != 212 or resolved.get("select") != select or resolved.get("commit") or resolved.get("text"):
                raise ValueError("T09 macro fixture actions do not resolve to implemented theme language commands")
        events = row.get("native_events", [])
        options = [event for event in events if event.get("event") == "ascii_option"]
        commits = [event for event in events if event.get("event") == "native_commit" and event.get("text")]
        if (any(type(event.get("value")) is not bool for event in options) or [event.get("value") for event in options] != [True, False] or
                any(type(event.get("t_ns")) is not int or event["t_ns"] < released for event in options + commits)):
            raise ValueError("T09 actual macro native mode sequence differs")
        calls = row.get("ic_calls", [])
        finalizations = [call for call in calls if call.get("returned") is True and (
            (call.get("method") == "commitText" and call.get("text") == "a") or
            (call.get("method") == "finishComposingText" and call.get("composing_before") == "a" and call.get("composing_after") is None))]
        keycalls = [call for call in calls if call.get("method") == "sendKeyEvent" and call.get("text") in {"0:29", "1:29"}]
        if row.get("librime_ascii_commit_claimed") is not False:
            raise ValueError("T09 app ASCII fallback mislabeled as a native librime commit")
        if row["id"] == "macro_ascii_key":
            delivery = row.get("submission_mode")
            if commits:
                raise ValueError("T09 braced key fabricated a native ASCII commit")
            if delivery == "native_unhandled_key_to_real_ic_keyevent":
                if finalizations:
                    raise ValueError("T09 historical braced key also finalized text")
                validate_t09_ascii_key_delivery(row)
            elif delivery == "virtual_printable_ascii_to_real_ic_text":
                validate_t09_ascii_text_delivery(row)
            else:
                raise ValueError("T09 braced key ASCII submission mode is unknown")
        elif ([event.get("text") for event in commits] != ["a"] or any(event.get("editor") != prepared.get("editor_token") for event in commits) or
              len(finalizations) != 1 or keycalls or row.get("submission_mode") != "app_simulate_literal_fallback_commit_proto_to_real_ic_text"):
            raise ValueError("T09 plain text lacks one app fallback/real IC finalization or duplicated key path")
    return {"macro_scope": "real_on_text_entry_original_fifo", "macro_regressions": 4,
            "macro_passed": all(row["status"] == "passed" for row in rows), "macro_excluded_from_fixed_task_costs": True}


def validate_t09_records(report, completion, run_id, baseline, schema):
    if (report.get("run_id") != run_id or completion.get("run_id") != run_id or
            report.get("baseline") is not baseline or completion.get("baseline") is not baseline or
            report.get("schema_id") != schema or report.get("driver") != "logical_visible_key_actions" or
            report.get("physical_touch_benchmark") is not False or report.get("performance_acceptance") is not False):
        raise ValueError("T09 nonce/schema/driver scope differs")
    corpus_json = report.get("corpus_json")
    if (not isinstance(corpus_json, str) or json.loads(corpus_json) != report.get("corpus") or len(report.get("corpus", [])) != 7 or
            hashlib.sha256(corpus_json.encode()).hexdigest() != report.get("corpus_sha256") or
            {item["id"]: item["target"] for item in report["corpus"]} != T09_TASKS):
        raise ValueError("T09 exact corpus or digest differs")
    identities = report.get("effective_theme_identity")
    if not isinstance(identities, dict) or set(identities) != T09_THEMES:
        raise ValueError("T09 effective active theme identities missing")
    for theme, identity in identities.items():
        required_ids = {theme, "trime"}
        packaged = identity.get("packaged_sha256", {})
        if (identity.get("requested_config_id") != theme or identity.get("selected_config_id") != theme or
                identity.get("status") != "verified_packaged_source_no_custom_overrides" or set(packaged) != required_ids or
                any(not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value) for value in packaged.values())):
            raise ValueError("T09 effective theme does not identify the selected packaged source")
        for prefix in ("effective_theme", "source_identity"):
            if (identity.get(prefix + "_file") != theme + ("-effective-theme.json" if prefix == "effective_theme" else "-source-identity.json") or
                    not re.fullmatch(r"[0-9a-f]{64}", identity.get(prefix + "_sha256", ""))):
                raise ValueError("T09 effective/source theme snapshot identity missing")
        files = identity.get("source_files", [])
        if (len(files) != 2 * len(required_ids) or
                {(row.get("resource_id"), row.get("root")) for row in files} != {(id, origin) for id in required_ids for origin in ("user", "shared")} or
                any(row.get("custom_exists") is not False or type(row.get("source_exists")) is not bool for row in files)):
            raise ValueError("T09 actual source roots/custom override evidence incomplete")
        for id in required_ids:
            candidates = sorted((row for row in files if row["resource_id"] == id), key=lambda row: row["root"] != "user")
            selected = next((row for row in candidates if row["source_exists"]), None)
            if selected is None or selected.get("sha256") != packaged[id]:
                raise ValueError("T09 first-priority theme source differs from packaged source")
    manual = report.get("manual_language_preference", {})
    restored = completion.get("manual_language_preference_cleanup", {})
    if manual.get("status") == "NOT_APPLICABLE_T09_PREF_NOT_IMPLEMENTED":
        if not baseline or restored.get("status") != "NOT_APPLICABLE_T09_PREF_NOT_IMPLEMENTED":
            raise ValueError("T09 acceptance/manual preference implementation missing")
    elif manual.get("status") == "registered":
        final_store = restored.get("restored_store", {})
        if (manual.get("key") != "manual_language_mode" or type(manual.get("exists")) is not bool or
                manual.get("raw") not in (("unset", "native", "ascii") if manual["exists"] else (None,))):
            raise ValueError("T09 manual preference original capture differs")
        restore_ok = (restored.get("status") == "restored_and_rebound" and final_store == manual and
                      type(restored.get("new_ordinary_editor", {}).get("editor_token")) is int and
                      restored["new_ordinary_editor"]["editor_token"] > 0 and
                      restored["new_ordinary_editor"]["editor_token"] != restored.get("prior_editor_token") and
                      (manual["raw"] not in {"native", "ascii"} or
                       restored["new_ordinary_editor"].get("ascii_mode") is (manual["raw"] == "ascii")))
        if not restore_ok and not completion.get("cleanup_errors"):
            raise ValueError("T09 manual preference original presence/raw or new editor restore differs")
    else:
        raise ValueError("T09 manual preference capture outcome missing")
    tasks, regressions = report.get("results", []), report.get("regressions", [])
    expected = {(theme, task) for theme in T09_THEMES for task in T09_TASKS}
    expected_regressions = {(theme, name) for theme in T09_THEMES for name in T09_REGRESSIONS}
    if (len(tasks) != 14 or {(item.get("theme"), item.get("task")) for item in tasks} != expected or
            len(regressions) != 8 or {(item.get("theme"), item.get("id")) for item in regressions} != expected_regressions):
        raise ValueError("T09 fixed 14 tasks / 8 regressions missing or duplicate")
    if report.get("collection_complete") is not True or completion.get("collection_complete") is not True:
        raise ValueError("T09 partial collection cannot pass collection completeness")
    statuses = []
    for item in tasks + regressions:
        status = item.get("status")
        if status not in {"passed", "failed", "unsupported"} or (not baseline and status == "unsupported"):
            raise ValueError("T09 invalid terminal status")
        statuses.append(status)
        actions = item.get("actions")
        counts = item.get("counts", {})
        if (not isinstance(actions, list) or counts.get("total_actions") != len(actions) or
                counts.get("dispatched_actions") != sum(action.get("dispatched") is True for action in actions)):
            raise ValueError("T09 attempted/dispatched counts fabricated or incomplete")
        for index, action in enumerate(actions, 1):
            if action.get("seq") != index or action.get("driver") not in {
                    "logical_key_action", "logical_visible_candidate_click", "logical_visible_syllable_click"}:
                raise ValueError("T09 action is not a visible logical callback")
        if status != "passed" and not item.get("reason"):
            raise ValueError("T09 failure/unsupported lacks retained reason")
        if status == "unsupported" and item.get("failure_kind") != "visible_route_unsupported":
            raise ValueError("T09 harness failure relabelled unsupported")
        if item in tasks and status == "passed":
            final = item.get("final", {})
            target = T09_TASKS[item["task"]]
            if (item.get("target") != target or final.get("editor_text") != target or final.get("raw_input") != "" or
                    final.get("composing_span") != [-1, -1] or final.get("selection") != [len(target), len(target)]):
                raise ValueError("T09 exact text/native/composing/caret assertion differs")
            if item["task"] in {"android16", "wifi", "b_site"}:
                prepared = item.get("prepared", {})
                if (prepared.get("ascii_mode") is not False or prepared.get("t9_layout") is not True or
                        final.get("ascii_mode") is not False or final.get("t9_layout") is not True or
                        prepared.get("schema_id") != schema or final.get("schema_id") != schema):
                    raise ValueError("T09 ordinary task did not begin/end at actual Chinese T9")
            if item.get("prepared", {}).get("effective_theme_sha256") != identities[item["theme"]]["effective_theme_sha256"]:
                raise ValueError("T09 task start belongs to a different effective theme")
            if manual.get("status") == "registered":
                isolated = item.get("prepared", {}).get("manual_language_preference", {})
                if isolated.get("status") != "registered" or isolated.get("exists") is not False or isolated.get("raw") is not None:
                    raise ValueError("T09 task manual language preference was not reset to UNSET")
            if item["task"] == "b_site":
                commits = [event for event in item.get("native_events", []) if event.get("event") == "native_commit" and
                           event.get("text") == "站" and event.get("editor") == final.get("editor_token")]
                calls = [call for call in item.get("ic_calls", []) if call.get("returned") is True and (
                    (call.get("method") == "commitText" and call.get("text") == "站") or
                    (call.get("method") == "finishComposingText" and call.get("composing_before") == "站" and call.get("composing_after") is None))]
                if len(commits) != 1 or len(calls) != 1:
                    raise ValueError("T09 B站 lacks one native commit and real IC finalization")
    cleanup = completion.get("cleanup_errors")
    if not isinstance(cleanup, list):
        raise ValueError("T09 cleanup outcome missing")
    rapid_facts = validate_t09_rapid(report, baseline, schema)
    macro_facts = validate_t09_macros(report, baseline, schema)
    probe_passed = "failed" not in statuses and not cleanup and rapid_facts["rapid_passed"] and macro_facts["macro_passed"]
    if completion.get("passed") is not probe_passed:
        raise ValueError("T09 completion conflicts with actual failed cases or cleanup")
    expected_unsupported = [{"theme": item["theme"], "task": item["task"]} for item in tasks if
                            item["status"] == "unsupported" and item["theme"] == "trime" and
                            item["task"] in {"android16", "wifi", "b_site"} and item.get("reason_code") == "no_visible_letter_route"]
    return {"collection_complete": True, "probe_collection_passed": probe_passed,
            "product_task_acceptance_passed": all(status == "passed" for status in statuses) and not cleanup and rapid_facts["rapid_passed"] and macro_facts["macro_passed"],
            **rapid_facts, **macro_facts,
            "supported_tasks": sum(item["status"] == "passed" for item in tasks),
            "unsupported_tasks": sum(item["status"] == "unsupported" for item in tasks),
            "failed_tasks": sum(item["status"] == "failed" for item in tasks),
            "expected_old_trime_english_unsupported": expected_unsupported, "cleanup_errors": cleanup,
            "cost_comparison": "Only matching successful task/theme/schema/start states; unsupported-to-supported is a capability gain, no percentage"}


def collect_t09_evidence(adb, package, output, directory, baseline, schema):
    evidence = {"collected": False, "passed": False}
    directory.mkdir(parents=True, exist_ok=False)
    def pull(name, maximum):
        raw = subprocess.check_output(adb + ["exec-out", "run-as", package, "cat", "cache/t09-mixed-input/" + name], timeout=60)
        (directory / name).write_bytes(raw)
        if len(raw) > maximum:
            raise ValueError("T09 artifact exceeds bound; retained whole bytes: " + name)
        return raw
    identifier = None
    try:
        identifiers = re.findall(r"(?m)^INSTRUMENTATION_STATUS: t09_run_id=([0-9a-f-]{36})$", output)
        if len(identifiers) != 1:
            raise ValueError("T09 requires one exact fresh status nonce")
        identifier = str(uuid.UUID(identifiers[0]))
        raw = pull("artifact-manifest.json", 2 * 1024 * 1024)
        manifest = json.loads(raw)
        attachments = manifest.get("attachments", {})
        if manifest.get("format") != 1 or manifest.get("run_id") != identifier or not isinstance(attachments, dict) or not 2 <= len(attachments) <= 1024:
            raise ValueError("T09 manifest nonce/shape differs")
        allowed = re.compile(r"(?:report|completion)\.json|(?:trime|tongwenfeng\.trime)-(?:effective-theme|source-identity)\.json|(?:trime|tongwenfeng\.trime)-[a-z0-9_]+-(?:actions\.json|start\.png|final\.png|layout-[1-9]\d*\.png)")
        total = 0
        for name, row in sorted(attachments.items()):
            if not allowed.fullmatch(name) or not isinstance(row, dict) or type(row.get("bytes")) is not int or not 0 <= row["bytes"] <= 24 * 1024 * 1024:
                raise ValueError("Unsafe/unbounded T09 artifact name or bytes")
            total += row["bytes"]
            if total > 512 * 1024 * 1024:
                raise ValueError("T09 total artifacts exceed bound")
            data = pull(name, 24 * 1024 * 1024)
            if len(data) != row["bytes"] or hashlib.sha256(data).hexdigest() != row.get("sha256"):
                raise ValueError("T09 manifest artifact hash differs: " + name)
        report = json.loads((directory / "report.json").read_text())
        completion = json.loads((directory / "completion.json").read_text())
        for identity in report.get("effective_theme_identity", {}).values():
            for prefix in ("effective_theme", "source_identity"):
                row = attachments.get(identity.get(prefix + "_file"), {})
                if row.get("sha256") != identity.get(prefix + "_sha256"):
                    raise ValueError("T09 retained effective/source theme artifact does not bind its receipt")
        facts = validate_t09_records(report, completion, identifier, baseline, schema)
        evidence.update(collected=True, passed=facts["probe_collection_passed"], run_id=identifier,
                        manifest_sha256=hashlib.sha256(raw).hexdigest(), attachments=attachments, **facts)
    except (ValueError, KeyError, TypeError, OSError, subprocess.SubprocessError) as error:
        evidence["error"] = str(error)
        # Interruption can occur before the final manifest. Preserve only fixed,
        # nonce-matching JSON as partial evidence; it cannot establish a pass.
        if identifier is not None:
            partial = {}
            for name in ("report.json", "completion.json"):
                try:
                    data = (directory / name).read_bytes() if (directory / name).is_file() else pull(name, 2 * 1024 * 1024)
                    row = json.loads(data)
                    if not isinstance(row, dict) or row.get("run_id") != identifier:
                        raise ValueError("Partial artifact nonce differs")
                    partial[name] = {"sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data),
                                     "verified_against_final_manifest": False}
                except (ValueError, OSError, subprocess.SubprocessError):
                    pass
            evidence["partial_artifacts"] = partial
    (directory / "collection.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n")
    return evidence


def instrumentation_options(probe, saf_tree=None, saf_revoke=False, t09_schema=None, t09_acceptance=False):
    options = [] if probe == "engine" else ["-e", probe, "true"]
    if saf_tree:
        options += ["-e", "safTree", saf_tree]
    if saf_revoke:
        options += ["-e", "safRevoke", "true"]
    if probe == "t09":
        options += ["-e", "t09Schema", t09_schema or "luna_pinyin_t9", "-e", "t09Baseline", "false" if t09_acceptance else "true"]
    return options


def api21_editor_focused(dump: str, probe="editors") -> bool:
    """Require the exact disposable host for the selected probe, never another window."""
    hosts = {"editors": "EditorProbeActivity", "t09": "ClipEditActivity"}
    if probe not in hosts:
        return False
    focus = re.findall(r"(?m)^\s*mCurrentFocus=(.*)$", dump)
    if len(focus) != 1:
        return False
    return re.fullmatch(
        rf"Window\{{[0-9a-fA-F]+ u0 com\.osfans\.trime\.debug/com\.osfans\.trime\.ui\.main\.{hosts[probe]}\}}",
        focus[0].strip(),
    ) is not None


def api21_editor_bound(dump: str, pid: int, probe="editors") -> bool:
    """Window focus can precede both the editor connection and LatinIME startup."""
    header = "Input method service state for com.android.inputmethod.latin.LatinIME@"
    if dump.count(header) != 1:
        return False
    manager, service = dump.split(header)
    attributes = re.findall(
        r"(?m)^\s*inputType=(0x[0-9a-fA-F]+)\s+imeOptions=(0x[0-9a-fA-F]+)(?=\s|$)", service)
    if len(attributes) != 1:
        return False
    input_type, ime_options = (int(value, 16) for value in attributes[0])
    # Preserve the existing editors ACTION_SEND gate. T09 has its own exact
    # ACTION_DONE + NO_PERSONALIZED_LEARNING ordinary-field policy.
    if probe not in ("editors", "t09"):
        return False
    allowed_options = (0x01000006, 0x09000006) if probe == "t09" else (0x4, 0x08000004)
    return all((
        "mCurMethodId=com.android.inputmethod.latin/.LatinIME" in manager,
        re.search(rf"mCurClient=ClientState\{{[^\n]*\bpid {pid}\}}", manager),
        "mInputShown=true" in manager,
        "mWindowVisible=true" in service,
        "mInputStarted=true mInputViewStarted=true" in service,
        "packageName=com.osfans.trime.debug " in service,
        input_type == 1 and ime_options in allowed_options,
    ))


def candidate_glyph_runs(output):
    """Pair theme and nonce from the same instrumentation status Bundle."""
    cases = {}
    for bundle in re.split(r"(?m)^INSTRUMENTATION_STATUS_CODE:.*$", output):
        themes = re.findall(r"(?m)^INSTRUMENTATION_STATUS: candidate_glyph_case=(.*)$", bundle)
        identifiers = re.findall(r"(?m)^INSTRUMENTATION_STATUS: candidate_glyph_run_id=([0-9a-f-]{36})$", bundle)
        if not themes and not identifiers:
            continue
        if len(themes) != 1 or len(identifiers) != 1 or themes[0] not in ("trime", "tongwenfeng.trime") or themes[0] in cases:
            raise ValueError("Missing, ambiguous or duplicate candidate glyph run identity")
        uuid.UUID(identifiers[0])
        cases[themes[0]] = identifiers[0]
    return cases


def collect_candidate_glyphs(adb, package, output, directory):
    """Retain fixed private evidence on failure, and reject stale or empty screens."""
    evidence = {"collected": False, "passed": False, "cases": {}}
    try:
        cases = candidate_glyph_runs(output)
        for theme, identifier in cases.items():
            destination = directory / theme
            destination.mkdir(parents=True, exist_ok=False)

            def pull(name):
                data = subprocess.check_output(
                    adb + ["exec-out", "run-as", package, "cat",
                           f"files/runtime-audit/candidate-glyph/{theme}/{name}"], timeout=60)
                (destination / name).write_bytes(data)
                return data

            raw = pull("report.json")
            manifest = json.loads(raw)
            if not isinstance(manifest, dict) or manifest.get("run_id") != identifier or manifest.get("theme") != theme:
                raise ValueError("Candidate glyph evidence belongs to a different probe run")
            if manifest.get("hardware_capture_before_software_draw") is not True or manifest.get("fixture") != "native T9 64":
                raise ValueError("Candidate glyph capture order or fixture differs")
            native = manifest.get("native_state", {})
            if (not isinstance(native, dict) or native.get("raw_input") != "64" or
                    native.get("raw_input_source") != "t9Cached.input" or native.get("schema_id") != "luna_pinyin_t9" or
                    any(type(native.get(key)) is not int or native[key] <= 0 for key in ("editor", "generation", "capture_start_ns", "capture_end_ns")) or
                    native["capture_end_ns"] < native["capture_start_ns"] or
                    ("request" in native and (type(native["request"]) is not int or native["request"] < 0))):
                raise ValueError("Candidate glyph native state is missing or invalid")
            attachments = manifest.get("attachments_sha256", {})
            allowed = {"screen.png", "label-0.png", "label-1.png"}
            if not isinstance(attachments, dict) or not set(attachments) <= allowed:
                raise ValueError("Unexpected candidate glyph attachment names")
            for name, expected in sorted(attachments.items()):
                data = pull(name)
                if hashlib.sha256(data).hexdigest() != expected:
                    raise ValueError("Candidate glyph attachment hash mismatch: " + name)
                # Require a bounded, nonempty PNG header as well as matching bytes.
                if (len(data) < 33 or len(data) > 24 * 1024 * 1024 or data[:8] != b"\x89PNG\r\n\x1a\n" or
                        data[8:16] != b"\x00\x00\x00\rIHDR" or
                        any(not 0 < int.from_bytes(data[start:start + 4], "big") <= 4096 for start in (16, 20))):
                    raise ValueError("Candidate glyph attachment has an invalid PNG header: " + name)
            labels = manifest.get("labels", [])
            if not isinstance(labels, list) or not all(isinstance(label, dict) for label in labels):
                raise ValueError("Invalid candidate glyph label records")
            expected_files = {"screen.png", *(f"label-{index}.png" for index in range(len(labels)))}
            passed = (manifest.get("passed") is True and 1 <= len(labels) <= 2 and set(attachments) == expected_files and
                      all(label.get("accessibility_passed") is True and label.get("glyph_passed") is True for label in labels))
            evidence["cases"][theme] = {"run_id": identifier, "report_sha256": hashlib.sha256(raw).hexdigest(),
                                       "attachments_sha256": attachments, "passed": passed}
        evidence["collected"] = set(cases) == {"trime", "tongwenfeng.trime"}
        evidence["passed"] = evidence["collected"] and all(case["passed"] for case in evidence["cases"].values())
        if not evidence["collected"]:
            evidence["error"] = "Both shipped themes must have fresh candidate glyph evidence"
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        evidence["error"] = str(error)
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "collection.json").write_text(json.dumps(evidence, indent=2) + "\n")
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--probe", choices=PROBES, required=True)
    parser.add_argument("--t09-schema", default="luna_pinyin_t9")
    parser.add_argument("--t09-acceptance", action="store_true")
    parser.add_argument("--t09-product-ref", help="Published T08 tag/ref; baseline must retain its production bytes")
    parser.add_argument("--saf-tree")
    parser.add_argument("--saf-revoke", action="store_true")
    parser.add_argument("--bind-ime-after-editor-focus", action="store_true",
                        help="API 21 editors/t09 only: select Trime after instrumentation creates its editor")
    args = parser.parse_args()
    if args.probe == "t09" and (not args.t09_product_ref or not re.fullmatch(r"[A-Za-z0-9_.-]+", args.t09_schema)):
        parser.error("T09 requires an explicit published --t09-product-ref and safe schema ID")
    if args.probe != "t09" and (args.t09_acceptance or args.t09_product_ref):
        parser.error("T09 options cannot alter another probe")
    with device_audit_lock(args.serial):
        run(args)


def run(args):
    product_binding = t09_product_sources(args.t09_product_ref, not args.t09_acceptance, Path(__file__).resolve().parents[2]) if args.probe == "t09" else None
    args.output.mkdir(parents=True, exist_ok=False)
    adb = [args.adb, "-s", args.serial]
    package = "com.osfans.trime.debug"

    def shell(*command, timeout=120):
        return subprocess.check_output(adb + ["shell", *command], text=True, timeout=timeout).strip()

    if shell("getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Disposable emulator only")
    identity = {"serial": args.serial, "api": shell("getprop", "ro.build.version.sdk"),
                "device_abis": shell("getprop", "ro.product.cpu.abilist"), "probe": args.probe,
                "build_fingerprint": shell("getprop", "ro.build.fingerprint"), "packages": {},
                "source_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                "bind_ime_after_editor_focus": args.bind_ime_after_editor_focus}
    if product_binding is not None:
        identity["t09_product_source_binding"] = product_binding
        identity["t09_schema"] = args.t09_schema
        identity["t09_baseline"] = not args.t09_acceptance
    trime = package + "/com.osfans.trime.ime.core.TrimeInputMethodService"
    if args.bind_ime_after_editor_focus:
        if identity["api"] != "21" or args.probe not in ("editors", "t09"):
            raise RuntimeError("Delayed IME binding is limited to the API 21 editors/t09 tests")
        if shell("cat", f"/sdcard/Android/data/{package}/files/runtime-audit-dedicated") != "":
            raise RuntimeError("Requires an explicitly marked disposable installation")
        if shell("settings", "get", "secure", "default_input_method") != trime:
            raise RuntimeError("Select the tested Trime build before this probe")
    for name, apk in ((package, args.apk), (package + ".test", args.test_apk)):
        installed = shell("pm", "path", name).removeprefix("package:")
        if not installed.startswith("/") or "\n" in installed:
            raise RuntimeError("Expected one APK per package")
        with apk.open("rb") as source:
            expected = hashlib.file_digest(source, "sha256").hexdigest()
        with tempfile.TemporaryFile() as source:
            subprocess.run(adb + ["exec-out", "cat", installed], stdout=source, check=True, timeout=120)
            source.seek(0)
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        if actual != expected:
            raise RuntimeError(f"Installed APK mismatch: {name}")
        identity["packages"][name] = {"apk_sha256": actual, "installed_path": installed}
        (args.output / f"{name}.txt").write_text(shell("dumpsys", "package", name))
    (args.output / "identity.json").write_text(json.dumps(identity, indent=2) + "\n")
    options = instrumentation_options(args.probe, args.saf_tree, args.saf_revoke, args.t09_schema, args.t09_acceptance)
    # The editor probe waits for 100 complete system IME hide/show transitions.
    # Its 15-minute test bound plus startup needs a longer host process bound.
    instrumentation_timeout = 1700 if args.probe == "t09" else 1400 if args.probe == "editors" else 1000
    activation_marker = None
    try:
        with record_logcat(adb, args.output / "logcat-full.txt"), (args.output / "instrumentation.log").open("w") as log:
            command = adb + ["shell", "am", "instrument", "-w", "-r", *options,
                            package + ".test/com.osfans.trime.StartupResponsivenessInstrumentation"]
            if args.bind_ime_after_editor_focus:
                # API 21 can retain a stale service binding while instrumentation
                # replaces the IME process. This is setup, not a process-recovery test.
                process = None
                try:
                    shell("input", "keyevent", "KEYCODE_HOME")
                    shell("ime", "set", "com.android.inputmethod.latin/.LatinIME")
                    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
                    start = time.monotonic()
                    bound = False
                    stable_binding = 0
                    previous_observation = None
                    observation_index = 0
                    while process.poll() is None:
                        if time.monotonic() - start > instrumentation_timeout:
                            raise subprocess.TimeoutExpired(command, instrumentation_timeout)
                        output = (args.output / "instrumentation.log").read_text()
                        if not bound and "application_ready_wait_ms" in output:
                            windows = shell("dumpsys", "window", "windows", timeout=15)
                            focus = [line for line in windows.splitlines() if "mCurrentFocus=" in line]
                            focused = api21_editor_focused(windows, args.probe)
                            state = shell("dumpsys", "input_method") if focused else None
                            pid = instrumentation_pid(output)
                            ready = state is not None and api21_editor_bound(state, pid, args.probe)
                            stable_binding = stable_binding + 1 if ready else 0
                            observation = (focus, state, pid)
                            if observation != previous_observation:
                                # Preserve the dumps already read by this gate. Only changed
                                # states need full snapshots; each poll records its outcome.
                                files = {}
                                for name, data in (("windows", windows), ("input_method", state)):
                                    if data is not None:
                                        filename = f"api21-binding-{observation_index:04d}-{name}.txt"
                                        (args.output / filename).write_text(data)
                                        files[name] = {"file": filename, "sha256": hashlib.sha256(data.encode()).hexdigest()}
                                previous_observation = observation
                            with (args.output / "api21-binding-observations.jsonl").open("a") as observations:
                                observations.write(json.dumps({"index": observation_index,
                                                              "elapsed_seconds": time.monotonic() - start,
                                                              "pid": pid, "focused": focused,
                                                              "editor_ready": ready,
                                                              "stable_editor_observations": stable_binding,
                                                              "snapshots": files}) + "\n")
                            observation_index += 1
                            if focused:
                                if stable_binding >= 4:
                                    (args.output / "api21-binding-before.txt").write_text(state)
                                    # Audit Trime from BEFORE its activation, including service startup.
                                    # LatinIME setup diagnostics remain in the full audit, not suppressed.
                                    activation_marker = "TRIME-ACTIVATE-" + str(uuid.uuid4())
                                    shell("log", "-t", "TrimeRuntimeAudit", activation_marker)
                                    binding = {"elapsed_seconds": time.monotonic() - start,
                                               "stable_editor_observations": stable_binding,
                                               "activation_marker": activation_marker,
                                               "result": shell("ime", "set", trime)}
                                    (args.output / "api21-test-binding.json").write_text(json.dumps(binding, indent=2) + "\n")
                                    bound = True
                        time.sleep(.2)
                    run = subprocess.CompletedProcess(command, process.wait())
                    if not bound:
                        raise RuntimeError("Test editor did not meet the LatinIME binding gate; see api21-binding-observations.jsonl")
                finally:
                    if process is not None and process.poll() is None:
                        process.kill()
                        process.wait(timeout=10)
                    shell("ime", "set", trime)
            else:
                run = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=instrumentation_timeout)
    except Exception as failure:
        if args.probe == "t09":
            interrupted_output = (args.output / "instrumentation.log").read_text() if (args.output / "instrumentation.log").is_file() else ""
            partial = collect_t09_evidence(adb, package, interrupted_output, args.output / "t09-mixed-input", not args.t09_acceptance, args.t09_schema)
            interruption = {"functional_passed": False, "collection_complete": False, "host_interruption": type(failure).__name__, "error": str(failure), "t09_evidence": partial, "t09_product_source_binding": product_binding}
            (args.output / "report.json").write_text(json.dumps(interruption, indent=2) + "\n")
        raise

    output = (args.output / "instrumentation.log").read_text()
    glyph_evidence = None
    candidate_glyph_evidence = None
    t09_evidence = collect_t09_evidence(adb, package, output, args.output / "t09-mixed-input", not args.t09_acceptance, args.t09_schema) if args.probe == "t09" else None
    if product_binding is not None and t09_product_sources(args.t09_product_ref, not args.t09_acceptance, Path(__file__).resolve().parents[2]) != product_binding:
        raise RuntimeError("T09 production inputs changed during baseline/acceptance")
    if args.probe == "editors":
        candidate_glyph_evidence = collect_candidate_glyphs(adb, package, output, args.output / "candidate-glyph")
        # Pull only the probe's own fixed artifacts, including on assertion
        # failure. Its nonce and content hashes reject stale private files.
        glyph_evidence = {"collected": False, "passed": False}
        try:
            identifiers = set(re.findall(
                r"INSTRUMENTATION_(?:STATUS|RESULT): fallback_glyph_run_id=([0-9a-f-]{36})",
                output))
            if len(identifiers) != 1:
                raise ValueError("Missing or ambiguous fallback glyph run identity")
            directory = args.output / "fallback-glyph"
            directory.mkdir()

            def pull_glyph(name):
                data = subprocess.check_output(
                    adb + ["exec-out", "run-as", package, "cat",
                           "files/runtime-audit/fallback-glyph/" + name], timeout=60)
                (directory / name).write_bytes(data)
                return data

            manifest = json.loads(pull_glyph("report.json"))
            if not isinstance(manifest, dict):
                raise ValueError("Invalid fallback glyph manifest")
            if manifest.get("run_id") != identifiers.pop():
                raise ValueError("Fallback glyph evidence belongs to a different probe run")
            expected = {f"{state}-font{scale}.{extension}"
                        for state in ("preparing", "failed") for scale in (1, 2)
                        for extension in ("png", "json")}
            attachments = manifest.get("attachments", {})
            if not isinstance(attachments, dict) or not set(attachments) <= expected:
                raise ValueError("Unexpected fallback glyph attachment names")
            for name in sorted(attachments):
                if hashlib.sha256(pull_glyph(name)).hexdigest() != attachments[name]:
                    raise ValueError("Fallback glyph attachment hash mismatch: " + name)
            if set(attachments) != expected:
                raise ValueError("Incomplete fallback glyph evidence")
            glyph_evidence.update(collected=True, passed=manifest.get("passed") is True,
                                  run_id=manifest["run_id"],
                                  report_sha256=hashlib.sha256((directory / "report.json").read_bytes()).hexdigest())
        except (ValueError, OSError, subprocess.SubprocessError) as error:
            glyph_evidence["error"] = str(error)
        (args.output / "fallback-glyph-collection.json").write_text(json.dumps(glyph_evidence, indent=2) + "\n")
    pid = instrumentation_pid(output)
    app_log = filter_pid_log((args.output / "logcat-full.txt").read_text(), pid)
    (args.output / "app-logcat.txt").write_text(app_log)
    audit = audit_log(app_log)
    (args.output / "runtime-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
    tested_audit = audit
    if activation_marker:
        active_log = filter_pid_log(log_after_marker((args.output / "logcat-full.txt").read_text(), activation_marker), pid)
        (args.output / "tested-ime-logcat.txt").write_text(active_log)
        tested_audit = audit_log(active_log)
        (args.output / "tested-ime-audit.json").write_text(json.dumps(tested_audit, indent=2) + "\n")
    # Deliberate missing-theme/storage/provider faults still retain all warnings
    # for review. They never receive a warning-free label based on functional PASS.
    injects_faults = args.probe in ("engine", "startupFailure", "lateStorage", "saf", "clipSave")
    assertions_passed = instrumentation_passed(run.returncode, output)
    passed = assertions_passed and tested_audit["capture_has_records"]
    if t09_evidence is not None:
        passed = passed and t09_evidence["collected"] and t09_evidence["passed"]
        if args.t09_acceptance:
            passed = passed and t09_evidence.get("product_task_acceptance_passed") is True
    if glyph_evidence is not None:
        passed = passed and glyph_evidence["collected"] and glyph_evidence["passed"]
    if candidate_glyph_evidence is not None:
        passed = passed and candidate_glyph_evidence["collected"] and candidate_glyph_evidence["passed"]
    if not injects_faults:
        passed = passed and not tested_audit["known_project_diagnostics"]
    report = {"functional_passed": passed, "assertions_passed": assertions_passed,
              "diagnostic_scope": "before_trime_activation_through_completion" if activation_marker else "full_probe",
              "activation_marker": activation_marker, "warning_free": audit["warning_free"],
              "tested_ime_diagnostic_count": len(tested_audit["known_project_diagnostics"]),
              "full_probe_diagnostic_count": len(audit["known_project_diagnostics"]),
              "warning_count": len(audit["warnings"]), "injects_faults": injects_faults,
              "requires_log_review": bool(audit["warnings"] or audit["known_project_diagnostics"]), "pid": pid}
    if t09_evidence is not None:
        report["t09_evidence"] = t09_evidence
        report["t09_product_source_binding"] = product_binding
    if glyph_evidence is not None:
        report["fallback_glyph_evidence"] = glyph_evidence
    if candidate_glyph_evidence is not None:
        report["candidate_glyph_evidence"] = candidate_glyph_evidence
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report), flush=True)
    if not passed:
        raise RuntimeError("Probe failed; see retained instrumentation and runtime logs")


if __name__ == "__main__":
    main()
