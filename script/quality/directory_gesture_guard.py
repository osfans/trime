"""SOURCE_ONLY: bounded API35 DocumentsUI gesture guard for an existing owned Probe.

This module has no command-line entry and never starts a VM or builds an APK.
Root must review the source and raw PNG captures; XML/window evidence is not a
replacement for that visual review. The old UI20 originals are never modified.
"""
import hashlib
import json
import os
import re
import struct
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

SOURCE_STATE = "SOURCE_ONLY"
PACKAGE = "com.android.documentsui"
PICKER = PACKAGE + "/com.android.documentsui.picker.PickActivity"


def require(value, message):
    if not value:
        raise AssertionError(message)


def rect(value):
    match = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", value)
    require(match is not None, "Unrecognised node bounds")
    result = tuple(map(int, match.groups()))
    require(result[0] < result[2] and result[1] < result[3], "Empty node bounds")
    return result


def inside(inner, outer):
    return (outer[0] <= inner[0] < inner[2] <= outer[2]
            and outer[1] <= inner[1] < inner[3] <= outer[3])


def window_state(raw):
    text = raw.decode("utf-8", "strict")
    focus = re.findall(r"(?m)^\s*mCurrentFocus=Window\{([0-9a-f]+) u\d+ ([^}\r\n]+)\}\s*$", text)
    require(len(focus) == 1, "Require exactly one non-null focused window")
    headers = list(re.finditer(r"(?m)^  Window #\d+ Window\{([0-9a-f]+) u\d+ ([^}\r\n]+)\}:\r?$", text))
    require(headers, "Window list missing")
    windows = []
    for index, header in enumerate(headers):
        block = text[header.end():headers[index + 1].start() if index + 1 < len(headers) else len(text)]
        # Limit the last window to its section, not later global dump sections.
        block = re.split(r"\n\s*\n", block, maxsplit=1)[0]
        owner = re.search(r"\bpackage=([^\s]+)", block)
        windows.append(dict(token=header[1], title=header[2],
                            package=None if owner is None else owner[1],
                            visible=bool(re.search(r"(?m)^\s+isVisible=true\s*$", block)),
                            on_screen=bool(re.search(r"(?m)^\s+isOnScreen=true\s*$", block)),
                            drawn="mHasSurface=true isReadyForDisplay()=true" in block))
    matches = [w for w in windows if (w["token"], w["title"]) == focus[0]]
    require(len(matches) == 1 and all(matches[0][k] for k in ("visible", "on_screen", "drawn")),
            "Focused window is not uniquely visible and drawn")
    return dict(focus=matches[0], windows=windows)


def window_signature(state):
    focus = state["focus"]
    visible_above = []
    for window in state["windows"]:
        if window["token"] == focus["token"]:
            break
        if window["visible"] and window["on_screen"]:
            visible_above.append((window["token"], window["title"]))
    return focus["token"], focus["title"], tuple(visible_above)


def picker_geometry(snapshot):
    state = snapshot["window"]
    require(state["focus"]["title"] == PICKER, "DocumentsUI picker is not focused")
    for _, name in window_signature(state)[2]:
        require(name in ("StatusBar", "Taskbar", "NavigationBar"),
                "Unreviewed visible window above directory picker: " + name)
    nodes = list(ET.fromstring(snapshot["xml"]).iter("node"))

    def unique_id(resource_id):
        found = [n for n in nodes if n.get("resource-id") == resource_id and n.get("package") == PACKAGE]
        require(len(found) == 1, "Missing or ambiguous live node: " + resource_id)
        return found[0]

    toolbar = unique_id(PACKAGE + ":id/toolbar")
    directory = unique_id(PACKAGE + ":id/dir_list")
    footer = unique_id("android:id/button1")
    require(directory.get("scrollable") == "true", "Directory is not scrollable")
    tr, dr, fr = rect(toolbar.get("bounds")), rect(directory.get("bounds")), rect(footer.get("bounds"))
    width, height = snapshot["screen_size"]
    require(inside(tr, (0, 0, width, height)) and inside(fr, (0, 0, width, height)), "Chrome is outside actual PNG")
    visible = (max(0, dr[0]), max(dr[1], tr[3]), min(width, dr[2]), min(dr[3], fr[1]))
    require(visible[2] - visible[0] >= 160 and visible[3] - visible[1] >= 200,
            "Insufficient visible directory viewport")
    anchor_positions = {}
    for node in directory.iter("node"):
        if node.get("resource-id") != "android:id/title" or not node.get("text"):
            continue
        bounds = rect(node.get("bounds"))
        if inside(bounds, visible):
            anchor_positions.setdefault(node.get("text"), set()).add(bounds)
    # XML may duplicate the same logical row. Ambiguous labels are excluded.
    anchors = {name: next(iter(values)) for name, values in anchor_positions.items() if len(values) == 1}
    require(len(anchors) >= 2, "Fewer than two unambiguous visible directory anchors")
    more = [n for n in toolbar.iter("node") if n.get("content-desc") == "More options" and n.get("clickable") == "true"]
    require(len(more) == 1 and inside(rect(more[0].get("bounds")), tr), "Unique visible toolbar overflow control missing")
    return dict(toolbar=tr, footer=fr, viewport=visible, anchors=anchors, more=rect(more[0].get("bounds")))


def interaction_target_evidence(prefix_raw, interval_raw, marker, token):
    """Android logs a target set only when it changes; retain a real last state.

    The caller must bind prefix_raw and interval_raw to one unchanged continuous
    file. No missing marker or absent target is accepted as successful dispatch.
    """
    header = r"^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}\s+([0-9]+)\s+([0-9]+)\s+I\s+input_interaction\s*:\s+Interaction with: (.*)$"
    prefix_lines = prefix_raw.decode("utf-8", "strict").splitlines()
    interval_lines = interval_raw.decode("utf-8", "strict").splitlines()
    marker_header = r"^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}\s+[0-9]+\s+[0-9]+\s+I\s+T13DirectoryGuard\s*:\s+"
    starts = [i for i, s in enumerate(interval_lines) if re.fullmatch(marker_header + re.escape(marker + "_START"), s)]
    ends = [i for i, s in enumerate(interval_lines) if re.fullmatch(marker_header + re.escape(marker + "_END"), s)]
    require(len(starts) == len(ends) == 1 and starts[0] < ends[0], "Actual log markers missing/ambiguous")
    prior = [(i, s, re.fullmatch(header, s)) for i, s in enumerate(prefix_lines + interval_lines[:starts[0]])]
    prior = [(i, s, m) for i, s, m in prior if m is not None]
    within = [(i, s, re.fullmatch(header, s)) for i, s in enumerate(interval_lines[starts[0]:ends[0] + 1], starts[0])]
    within = [(i, s, m) for i, s, m in within if m is not None]
    expected = token + " " + PICKER + " (server)"
    def expected_targets(match):
        targets = [part.strip() for part in match[3].split(",") if part.strip()]
        return expected in targets and all(target in (expected, "PointerEventDispatcher0 (server)") for target in targets)
    require(all(expected_targets(m) for _, _, m in within), "Swipe changed actual input target")
    if within:
        evidence = within[0]
        kind = "actual_target_change_inside_gesture"
    else:
        require(prior and expected_targets(prior[-1][2]), "No matching actual prior target state")
        evidence = prior[-1]
        kind = "unchanged_last_actual_target_state"
    _, raw, match = evidence
    # Every target observation through the end must belong to the same dispatcher.
    require(all((m[1], m[2]) == (match[1], match[2]) for _, _, m in within), "Dispatcher identity changed inside gesture")
    return dict(kind=kind, selected_raw=raw, selected_sha256=hashlib.sha256(raw.encode()).hexdigest(),
                emitter_pid=int(match[1]), emitter_tid=int(match[2]),
                observed_target_changes=len(within), prior_target_observations=len(prior),
                marker_start_line=starts[0], marker_end_line=ends[0])


class DirectoryGestureGuard:
    def __init__(self, control, output, continuous_log):
        self.control = control
        self.output = Path(output)
        self.output.mkdir(exist_ok=False)
        self.log = Path(continuous_log)
        require(self.log.is_file() and not self.log.is_symlink(), "Require original continuous device log")
        self.nonce = uuid.uuid4().hex
        self.deadline = min(control.deadline, time.monotonic() + 180)
        self.snapshots, self.events = [], []

    def check(self):
        self.control.check()
        require(time.monotonic() < self.deadline, "Directory guard 180-second deadline")

    def write(self, name, raw):
        path = self.output / name
        with path.open("xb") as stream:
            stream.write(raw)
        return dict(path=str(path), bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())

    def capture(self, label):
        self.check()
        self.control.verify_guest()
        name = f"{len(self.snapshots):02d}-{label}"
        before = self.control.shell("dumpsys", "window")
        # Avoid polluting the directory being exercised with dump XML files.
        remote = f"/data/local/tmp/trime-directory-{self.nonce}.xml"
        self.control.shell("uiautomator", "dump", remote, timeout=20)
        xml = self.control.shell("cat", remote)
        self.control.shell("rm", remote)
        png = self.control.command(["exec-out", "screencap", "-p"], timeout=15)
        after = self.control.shell("dumpsys", "window")
        bindings = {kind: self.write(name + suffix, raw) for kind, suffix, raw in (
            ("window_before", ".window-before.txt", before), ("xml", ".xml", xml),
            ("png", ".png", png), ("window_after", ".window-after.txt", after))}
        require(png[:8] == b"\x89PNG\r\n\x1a\n" and png[12:16] == b"IHDR", "Invalid raw PNG")
        state_before, state_after = window_state(before), window_state(after)
        require(window_signature(state_before) == window_signature(state_after), "Window changed during capture")
        snapshot = dict(label=label, captured=time.monotonic(), window=state_after, xml=xml,
                        screen_size=struct.unpack(">II", png[16:24]), bindings=bindings)
        self.snapshots.append({k: v for k, v in snapshot.items() if k != "xml"})
        return snapshot

    def ensure_picker(self, snapshot):
        focus = snapshot["window"]["focus"]
        if focus["title"].startswith("PopupWindow:") and focus["package"] == PACKAGE:
            self.events.append(dict(action="real_back_to_dismiss_unexpected_menu", token=focus["token"]))
            self.control.shell("input", "keyevent", "KEYCODE_BACK")
            snapshot = self.capture("after-real-back")
        picker_geometry(snapshot)  # Unknown focused states fail; never blind Back loops.
        return snapshot

    def stable_picker(self, first=None, dismiss_popup=True):
        first = first or self.capture("picker-before")
        if dismiss_popup:
            first = self.ensure_picker(first)
        else:
            picker_geometry(first)  # Postconditions never repair an unexpected menu.
        first_geometry = picker_geometry(first)
        time.sleep(.4)
        second = self.capture("picker-stable")
        require(window_signature(first["window"]) == window_signature(second["window"]), "Picker window not stable")
        require(first_geometry == picker_geometry(second), "Directory geometry/anchors not stable")
        return second

    def menu_roundtrip(self):
        before = self.stable_picker()
        left, top, right, bottom = picker_geometry(before)["more"]
        self.control.shell("input", "touchscreen", "tap", str((left + right) // 2), str((top + bottom) // 2))
        menu = self.capture("after-toolbar-menu-tap")
        require(menu["window"]["focus"]["title"].startswith("PopupWindow:")
                and menu["window"]["focus"]["package"] == PACKAGE, "Toolbar tap did not open observed DocumentsUI menu")
        return self.stable_picker(self.ensure_picker(menu))

    def scroll(self, direction):
        require(direction in ("up", "down"), "Scroll direction must describe finger travel")
        before = self.stable_picker()
        geometry = picker_geometry(before)
        l, t, r, b = geometry["viewport"]
        l, t, r, b = l + 24, t + 24, r - 24, b - 24
        x, y1, y2 = (l + r) // 2, t + (b - t) // 4, t + 3 * (b - t) // 4
        if direction == "up":
            y1, y2 = y2, y1
        marker = "T13_DIRECTORY_" + self.nonce + "_" + str(len(self.events))
        # Freeze a bounded complete-line prefix before injecting. AOSP logs the
        # interaction target only when its token set changes, not once per swipe.
        with self.log.open("rb") as stream:
            original = os.fstat(stream.fileno())
            prefix_start = max(0, original.st_size - 2 * 1024 * 1024)
            stream.seek(prefix_start)
            prefix_raw = stream.read(original.st_size - prefix_start)
        require(len(prefix_raw) == original.st_size - prefix_start, "Short original log prefix")
        if prefix_start:
            first_newline = prefix_raw.find(b"\n")
            require(first_newline >= 0, "No complete line in bounded original prefix")
            prefix_start += first_newline + 1
            prefix_raw = prefix_raw[first_newline + 1:]
        last_newline = prefix_raw.rfind(b"\n")
        require(last_newline >= 0, "Original prefix has no complete line")
        prefix_raw = prefix_raw[:last_newline + 1]
        offset = prefix_start + len(prefix_raw)
        prefix_binding = self.write(f"swipe-{len(self.events):02d}-prior.log", prefix_raw)
        self.control.shell("log", "-t", "T13DirectoryGuard", marker + "_START")
        self.control.shell("input", "touchscreen", "swipe", str(x), str(y1), str(x), str(y2), "450")
        after = self.stable_picker(dismiss_popup=False)
        self.control.shell("log", "-t", "T13DirectoryGuard", marker + "_END")
        log_raw = b""
        for _ in range(8):
            self.check()
            with self.log.open("rb") as stream:
                stat = os.fstat(stream.fileno())
                require((stat.st_dev, stat.st_ino) == (original.st_dev, original.st_ino)
                        and stat.st_size >= offset, "Original gesture log replaced or truncated")
                stream.seek(prefix_start)
                require(stream.read(len(prefix_raw)) == prefix_raw, "Original target-state prefix changed")
                stream.seek(offset)
                log_raw = stream.read(2 * 1024 * 1024 + 1)
            require(len(log_raw) <= 2 * 1024 * 1024, "Guard log interval too large")
            if marker.encode() + b"_END" in log_raw:
                break
            time.sleep(.25)
        log_binding = self.write(f"swipe-{len(self.events):02d}.log", log_raw)
        lines = log_raw.decode("utf-8", "strict").splitlines()
        marker_header = r"^\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}\s+[0-9]+\s+[0-9]+\s+I\s+T13DirectoryGuard\s*:\s+"
        starts = [i for i, s in enumerate(lines) if re.fullmatch(marker_header + re.escape(marker + "_START"), s)]
        ends = [i for i, s in enumerate(lines) if re.fullmatch(marker_header + re.escape(marker + "_END"), s)]
        require(len(starts) == len(ends) == 1 and starts[0] < ends[0], "Actual log markers missing/ambiguous")
        interval = lines[starts[0]:ends[0] + 1]
        failures = [s for s in interval if re.search(r"Input event injection .* failed|Not creating InputTarget|Dropping event because no targets|input_cancel:.*injection failed|Touched window was removed", s)]
        require(not failures, "Actual input dispatch failure; original logs retained")
        token = before["window"]["focus"]["token"]
        target_evidence = interaction_target_evidence(prefix_raw, log_raw, marker, token)
        require(window_signature(before["window"]) == window_signature(after["window"]), "Swipe changed picker window")
        after_geometry = picker_geometry(after)
        common = set(geometry["anchors"]) & set(after_geometry["anchors"])
        moves = {name: after_geometry["anchors"][name][1] - geometry["anchors"][name][1] for name in common}
        sign = 1 if direction == "down" else -1
        require(len(moves) >= 2 and all(sign * delta >= 24 for delta in moves.values())
                and max(moves.values()) - min(moves.values()) <= 16,
                "No two consistent directory-item displacements in intended direction")
        require(before["bindings"]["png"]["sha256"] != after["bindings"]["png"]["sha256"], "Before/after images identical")
        result = dict(action="actual_swipe", direction=direction, path=[x, y1, x, y2, 450],
                      viewport=geometry["viewport"], moves=moves, token=token,
                      before=before["bindings"], after=after["bindings"], raw_log=log_binding,
                      source_device=original.st_dev, source_inode=original.st_ino,
                      source_start=offset, source_end=offset + len(log_raw),
                      target_state_prefix=prefix_binding, target_state_prefix_start=prefix_start,
                      target_evidence=target_evidence)
        self.events.append(result)
        return result

    def report(self):
        return dict(source_state=SOURCE_STATE, events=self.events, snapshots=self.snapshots,
                    scope="Menu and real directory scroll diagnostics only; no URI authorization or import acceptance",
                    requires_root_raw_image_review=True)
