# SPDX-License-Identifier: GPL-3.0-or-later
"""Linux PID/start ancestry and busy-process observation for serial host tests.

Imported definitions only. Process-group membership never proves ownership.
"""
import os
from pathlib import Path

def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def identity(pid):
    path = Path('/proc') / str(pid)
    raw = (path / 'stat').read_text()
    require(raw.startswith(str(pid) + ' ('), 'PID stat identity differs')
    fields = raw.rsplit(') ', 1)[1].split()
    return dict(pid=pid, starttime=int(fields[19]), ppid=int(fields[1]), pgid=int(fields[2]))


def busy_processes(allowed_pid=None):
    busy = []
    for path in Path('/proc').iterdir():
        if not path.name.isdecimal():
            continue
        try:
            argv = (path / 'cmdline').read_bytes().split(b'\0')
            if not argv or not argv[0]:
                continue
            executable = os.path.basename(argv[0].decode(errors='replace'))
            joined = b' '.join(argv)
            vm = executable.startswith('qemu-system-') or executable in {'emulator', 'emulator64-x86', 'emulator64-arm'}
            gradle = executable in {'java', 'java.exe'} and any(token in joined for token in (
                b'org.gradle.', b'GradleDaemon', b'GradleWrapperMain', b'org.jetbrains.kotlin.daemon'))
            if (vm and int(path.name) != allowed_pid) or gradle:
                busy.append(dict(pid=int(path.name), executable=executable, kind='emulator' if vm else 'gradle'))
        except (OSError, ProcessLookupError):
            continue
    return busy


class OwnedProcessTracker:
    def __init__(self, identity, snapshot=None, controller_pid=None, sender=None):
        self.identity = identity
        self.snapshot = snapshot or self._snapshot
        self.controller_pid = os.getpid() if controller_pid is None else controller_pid
        self.sender = os.kill if sender is None else sender
        self.owned = {}
        self.observed_groups = set()
        self.unresolved_group_members = []
        self.events = []
        self.foreign_busy = []
        self.discovery_count = 0

    @staticmethod
    def key(row):
        return int(row['pid']), int(row['starttime'])

    def _snapshot(self):
        result = {}
        for entry in Path('/proc').iterdir():
            if not entry.name.isdecimal():
                continue
            try:
                row = self.identity(int(entry.name))
                result[row['pid']] = row
            except (OSError, ValueError, IndexError):
                continue
        return result

    def _record(self, row, proof):
        key = self.key(row)
        if key not in self.owned:
            self.owned[key] = dict(initial=dict(row), latest=dict(row), proof=proof)
            self.events.append(dict(event='ownership_proven', identity=dict(row), proof=proof))
        else:
            old = self.owned[key]['latest']
            if (old['ppid'], old['pgid']) != (row['ppid'], row['pgid']):
                self.events.append(dict(event='same_process_reparent_or_group_change', before=dict(old), after=dict(row)))
            self.owned[key]['latest'] = dict(row)
        self.observed_groups.add(int(row['pgid']))

    def seed(self, token):
        # Only the actual still-identical direct Popen child is a new root.
        actual = self.identity(token['pid'])
        if self.key(actual) != self.key(token) or actual['ppid'] != self.controller_pid:
            raise RuntimeError('Popen seed identity/parent differs')
        self._record(actual, dict(kind='direct_Popen_child', controller_pid=self.controller_pid))

    def live(self, token):
        try:
            return self.key(self.identity(token['pid'])) == self.key(token)
        except FileNotFoundError:
            return False

    def _proven_child(self, candidate):
        # Read the child/parent/child again. Cached PPID or a historical PGID/name
        # alone must never admit a foreign/reused process.
        if candidate['ppid'] not in {key[0] for key in self.owned}:
            return None
        try:
            before = self.identity(candidate['pid'])
            if self.key(before) != self.key(candidate) or before['ppid'] != candidate['ppid']:
                return None
            parent = self.identity(before['ppid'])
            parent_key = self.key(parent)
            if parent_key not in self.owned or parent['starttime'] > before['starttime']:
                return None
            after = self.identity(before['pid'])
            parent_after = self.identity(parent['pid'])
            if self.key(after) != self.key(before) or after['ppid'] != parent['pid'] or self.key(parent_after) != parent_key:
                return None
            return after, dict(kind='live_parent_ancestry', parent_pid=parent['pid'], parent_starttime=parent['starttime'])
        except FileNotFoundError:
            return None

    def discover(self):
        table = self.snapshot()
        if len(table) > 16384:
            raise RuntimeError('Process snapshot bound exceeded')
        self.discovery_count += 1
        # Previously proved PID/start keys remain owned after either PPID or PGID
        # changes. No process inherits ownership when a numeric PID is reused.
        for token in list(self.owned.values()):
            try:
                actual = self.identity(token['initial']['pid'])
            except FileNotFoundError:
                continue
            if self.key(actual) == self.key(token['initial']):
                self._record(actual, token['proof'])
        changed = True
        while changed:
            changed = False
            for candidate in table.values():
                if self.key(candidate) in self.owned:
                    continue
                result = self._proven_child(candidate)
                if result is not None:
                    actual, proof = result
                    self._record(actual, proof)
                    changed = True
        live_groups = set()
        for record in list(self.owned.values()):
            try:
                actual = self.identity(record['initial']['pid'])
            except FileNotFoundError:
                continue
            if self.key(actual) == self.key(record['initial']):
                self._record(actual, record['proof'])
                live_groups.add(actual['pgid'])
        # Unknown group members are retained as unresolved candidates, never
        # adopted, allowed as JVMs, or signalled. Historical numbers can only
        # prevent an unsupported clean result, never establish a causal owner.
        unresolved = []
        for candidate in table.values():
            if self.key(candidate) in self.owned or candidate['pgid'] not in self.observed_groups:
                continue
            try:
                current = self.identity(candidate['pid'])
            except FileNotFoundError:
                continue
            if self.key(current) != self.key(candidate) or current['pgid'] not in self.observed_groups:
                continue
            unresolved.append(dict(identity=current, ownership_established=False,
                                   group_anchor='live_owned_PID_start_group' if current['pgid'] in live_groups else 'historical_number_only_not_proof'))
        self.unresolved_group_members = unresolved
        return table

    def require_busy_owned(self, busy, allow_owned=True):
        self.discover()
        for row in busy:
            try:
                actual = self.identity(row['pid'])
            except FileNotFoundError:
                continue
            permitted = allow_owned and row['kind'] in ('gradle', 'jvm') and self.key(actual) in self.owned
            if not permitted:
                self.foreign_busy.append(dict(kind=row['kind'], identity=actual, ownership_established=self.key(actual) in self.owned))
                raise RuntimeError('Foreign VM/JVM or still-active previous JVM; no name/group-based admission')
        if self.unresolved_group_members:
            raise RuntimeError('Unknown member of an observed build process group; no ownership claim or signal')

    def live_owned(self):
        result = []
        for record in list(self.owned.values()):
            try:
                current = self.identity(record['initial']['pid'])
            except FileNotFoundError:
                continue
            if self.key(current) == self.key(record['initial']):
                self._record(current, record['proof'])
                result.append(current)
        return result

    def signal_owned(self, token, sig):
        if self.key(token) not in self.owned:
            raise RuntimeError('Refuse signal to an unproved process')
        try:
            current = self.identity(token['pid'])
        except FileNotFoundError:
            return False
        if self.key(current) != self.key(token):
            return False
        # Immediate PID/start check; PPID/PGID are recorded but are not identity
        # invariants. Root Popen children remain reserved until actually reaped.
        try:
            self.sender(current['pid'], sig)
        except ProcessLookupError:
            return False
        self.events.append(dict(event='signal_sent', signal=int(sig), identity=current,
                                ownership_proof=self.owned[self.key(token)]['proof']))
        return True

    def report(self):
        return dict(algorithm='PID_start_plus_live_parent_ancestry',
                    discovery_count=self.discovery_count,
                    owned_processes=list(self.owned.values()),
                    events=self.events,
                    unresolved_group_members=self.unresolved_group_members,
                    foreign_busy=self.foreign_busy,
                    group_membership_establishes_ownership=False,
                    reparenting_revokes_proven_PID_start=False)
