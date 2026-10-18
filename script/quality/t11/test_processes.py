#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Control actual ownership checks through deterministic /proc disappearance races."""
from collections import Counter
import errno
from pathlib import Path
import signal
import unittest
from unittest.mock import Mock, patch

from host_native import processes


def token(pid, start, parent=42, group=100):
    return dict(pid=pid, starttime=start, ppid=parent, pgid=group)


def gone(error_type=ProcessLookupError):
    code = errno.ESRCH if error_type is ProcessLookupError else errno.ENOENT
    return error_type(code, 'synthetic process disappears during /proc read')


class ProcReads:
    """Return PID/start snapshots and inject errors or reuse at one exact read."""

    def __init__(self, *rows):
        self.rows = {row['pid']: dict(row) for row in rows}
        self.calls = Counter()
        self.overrides = {}

    def __call__(self, pid):
        self.calls[pid] += 1
        value = self.overrides.get((pid, self.calls[pid]), self.rows.get(pid))
        if isinstance(value, BaseException):
            raise value
        if value is None:
            raise gone(FileNotFoundError)
        return dict(value)

    def snapshot(self):
        return {pid: dict(row) for pid, row in self.rows.items()}


class ProcessDisappearanceTest(unittest.TestCase):
    def setUp(self):
        self.root = token(100, 100)
        self.child = token(200, 200, parent=100)
        self.foreign = token(900, 900, parent=1)
        self.reads = ProcReads(self.root, self.child)
        self.sender = Mock()
        self.tracker = processes.OwnedProcessTracker(
            self.reads, snapshot=self.reads.snapshot, controller_pid=42, sender=self.sender,
        )
        self.tracker.seed(self.root)
        self.reads.calls.clear()

    def assert_only_root_owned(self):
        self.assertEqual(set(self.tracker.owned), {(100, 100)})
        self.sender.assert_not_called()

    def test_real_identity_propagates_esrch_from_proc_stat(self):
        with patch.object(Path, 'read_text', side_effect=gone()):
            with self.assertRaises(ProcessLookupError):
                processes.identity(100)

    def test_child_parent_child_parent_rechecks_handle_both_disappearance_errors(self):
        for error_type in (FileNotFoundError, ProcessLookupError):
            for pid, nth in ((200, 1), (100, 1), (200, 2), (100, 2)):
                with self.subTest(error=error_type.__name__, pid=pid, read=nth):
                    self.reads.calls.clear()
                    self.reads.overrides = {(pid, nth): gone(error_type)}
                    self.assertIsNone(self.tracker._proven_child(self.child))
                    self.assert_only_root_owned()

    def test_successful_live_ancestry_adopts_and_signals_actual_child(self):
        self.tracker.discover()
        self.assertEqual(set(self.tracker.owned), {(100, 100), (200, 200)})
        self.assertTrue(self.tracker.signal_owned(self.child, signal.SIGTERM))
        self.sender.assert_called_once_with(200, signal.SIGTERM)
        self.assertEqual(self.tracker.owned[(200, 200)]['proof']['kind'], 'live_parent_ancestry')

    def test_live_returns_false_when_identity_disappears(self):
        for error_type in (FileNotFoundError, ProcessLookupError):
            with self.subTest(error=error_type.__name__):
                self.reads.calls.clear()
                self.reads.overrides = {(100, 1): gone(error_type)}
                self.assertFalse(self.tracker.live(self.root))

    def test_discover_owned_refresh_and_live_group_recheck_handle_esrch(self):
        self.tracker.snapshot = lambda: {}
        for nth in (1, 2):
            with self.subTest(read=nth):
                self.reads.calls.clear()
                self.reads.overrides = {(100, nth): gone()}
                self.assertEqual(self.tracker.discover(), {})
                self.assertEqual(self.tracker.unresolved_group_members, [])
                self.assert_only_root_owned()

    def test_discover_child_disappears_without_adoption(self):
        for pid, nth in ((200, 1), (100, 2), (200, 2), (100, 3)):
            with self.subTest(pid=pid, read=nth):
                self.reads.calls.clear()
                self.reads.overrides = {(pid, nth): gone()}
                self.tracker.discover()
                self.assert_only_root_owned()

    def test_discover_unresolved_group_member_disappears(self):
        self.reads.rows = {100: self.root, 900: self.foreign}
        self.reads.overrides = {(900, 1): gone()}
        self.tracker.discover()
        self.assertEqual(self.tracker.unresolved_group_members, [])
        self.assert_only_root_owned()

    def test_require_busy_owned_skips_a_process_gone_at_final_read(self):
        self.tracker.snapshot = lambda: {}
        self.reads.overrides = {(100, 3): gone()}
        self.tracker.require_busy_owned([dict(pid=100, kind='jvm')])
        self.assertEqual(self.tracker.foreign_busy, [])
        self.assert_only_root_owned()

    def test_live_owned_skips_a_disappearing_identity(self):
        self.reads.overrides = {(100, 1): gone()}
        self.assertEqual(self.tracker.live_owned(), [])
        self.assert_only_root_owned()

    def test_signal_identity_disappears_without_sending(self):
        self.reads.overrides = {(100, 1): gone()}
        self.assertFalse(self.tracker.signal_owned(self.root, signal.SIGTERM))
        self.sender.assert_not_called()

    def test_process_disappears_between_identity_check_and_signal(self):
        self.sender.side_effect = gone()
        self.assertFalse(self.tracker.signal_owned(self.root, signal.SIGTERM))
        self.assertFalse(any(row['event'] == 'signal_sent' for row in self.tracker.events))

    def test_reused_pid_is_neither_live_nor_signalled(self):
        self.reads.rows[100] = token(100, 1000)
        self.assertFalse(self.tracker.live(self.root))
        self.assertEqual(self.tracker.live_owned(), [])
        self.assertFalse(self.tracker.signal_owned(self.root, signal.SIGTERM))
        self.assert_only_root_owned()

    def test_reused_parent_does_not_pass_ancestry(self):
        for start in (150, 1000):
            with self.subTest(parent_start=start):
                self.reads.rows[100] = token(100, start)
                self.assertIsNone(self.tracker._proven_child(self.child))
                self.assert_only_root_owned()

    def test_reuse_or_reparenting_during_ancestry_recheck_rejects_child(self):
        mutations = ((200, 1, token(200, 201, parent=100)),
                     (200, 2, token(200, 201, parent=100)),
                     (200, 2, token(200, 200, parent=900)),
                     (100, 2, token(100, 101)))
        for pid, nth, value in mutations:
            with self.subTest(pid=pid, read=nth, value=value):
                self.reads.calls.clear()
                self.reads.overrides = {(pid, nth): value}
                self.assertIsNone(self.tracker._proven_child(self.child))
                self.assert_only_root_owned()

    def test_foreign_same_group_is_unresolved_and_cannot_be_signalled(self):
        self.reads.rows = {100: self.root, 900: self.foreign}
        self.tracker.discover()
        self.assertEqual([row['identity'] for row in self.tracker.unresolved_group_members], [self.foreign])
        with self.assertRaisesRegex(RuntimeError, 'unproved'):
            self.tracker.signal_owned(self.foreign, signal.SIGTERM)
        with self.assertRaisesRegex(RuntimeError, 'Foreign'):
            self.tracker.require_busy_owned([dict(pid=900, kind='jvm')])
        self.assert_only_root_owned()

    def test_foreign_group_occupant_reusing_owned_pid_is_not_adopted(self):
        reused = token(100, 1000, parent=1)
        self.reads.rows = {100: reused}
        self.tracker.discover()
        self.assertEqual(self.tracker.unresolved_group_members[0]['identity'], reused)
        with self.assertRaisesRegex(RuntimeError, 'Foreign'):
            self.tracker.require_busy_owned([dict(pid=100, kind='jvm')])
        self.assertFalse(self.tracker.signal_owned(self.root, signal.SIGTERM))
        self.assert_only_root_owned()

    def test_previously_proven_identity_survives_reparenting_and_group_change(self):
        changed = token(100, 100, parent=1, group=777)
        self.reads.rows[100] = changed
        self.assertEqual(self.tracker.live_owned(), [changed])
        self.assertTrue(self.tracker.signal_owned(self.root, signal.SIGTERM))
        self.sender.assert_called_once_with(100, signal.SIGTERM)

    def test_permission_and_other_io_errors_propagate_from_ancestry_rechecks(self):
        for error in (PermissionError(errno.EACCES, 'synthetic denied'), OSError(errno.EIO, 'synthetic I/O failure')):
            for pid, nth in ((200, 1), (100, 1), (200, 2), (100, 2)):
                with self.subTest(error=type(error).__name__, pid=pid, read=nth):
                    self.reads.calls.clear()
                    self.reads.overrides = {(pid, nth): error}
                    with self.assertRaises(type(error)):
                        self.tracker._proven_child(self.child)
                    self.assert_only_root_owned()

    def test_permission_and_other_io_errors_propagate_from_public_operations(self):
        for error in (PermissionError(errno.EACCES, 'synthetic denied'), OSError(errno.EIO, 'synthetic I/O failure')):
            for operation in ('live', 'discover', 'live_owned', 'signal_owned', 'require_busy_owned'):
                with self.subTest(error=type(error).__name__, operation=operation):
                    self.reads.calls.clear()
                    self.reads.overrides = {(100, 1): error}
                    args = {'live': (self.root,), 'discover': (), 'live_owned': (),
                            'signal_owned': (self.root, signal.SIGTERM),
                            'require_busy_owned': ([dict(pid=100, kind='jvm')],)}[operation]
                    with self.assertRaises(type(error)):
                        getattr(self.tracker, operation)(*args)
                    self.assert_only_root_owned()

    def test_permission_error_at_final_busy_identity_check_propagates(self):
        self.tracker.snapshot = lambda: {}
        self.reads.overrides = {(100, 3): PermissionError(errno.EACCES, 'synthetic denied')}
        with self.assertRaises(PermissionError):
            self.tracker.require_busy_owned([dict(pid=100, kind='jvm')])
        self.assert_only_root_owned()

    def test_permission_error_at_unresolved_group_identity_check_propagates(self):
        self.reads.rows = {100: self.root, 900: self.foreign}
        self.reads.overrides = {(900, 1): PermissionError(errno.EACCES, 'synthetic denied')}
        with self.assertRaises(PermissionError):
            self.tracker.discover()
        self.assert_only_root_owned()

    def test_permission_error_from_signal_sender_propagates(self):
        self.sender.side_effect = PermissionError(errno.EPERM, 'synthetic denied')
        with self.assertRaises(PermissionError):
            self.tracker.signal_owned(self.root, signal.SIGTERM)
        self.assertFalse(any(row['event'] == 'signal_sent' for row in self.tracker.events))

    def test_invalid_seed_does_not_establish_ownership(self):
        tracker = processes.OwnedProcessTracker(self.reads, controller_pid=900, sender=self.sender)
        with self.assertRaisesRegex(RuntimeError, 'parent differs'):
            tracker.seed(self.root)
        self.assertEqual(tracker.owned, {})
        self.sender.assert_not_called()


class ProcEnumerationTest(unittest.TestCase):
    def test_snapshot_skips_esrch_and_enoent_but_keeps_live_rows(self):
        for error_type in (FileNotFoundError, ProcessLookupError):
            with self.subTest(error=error_type.__name__):
                live = token(200, 200)
                tracker = processes.OwnedProcessTracker(Mock(side_effect=[gone(error_type), live]))
                with patch.object(Path, 'iterdir', return_value=[Path('/proc/100'), Path('/proc/200')]):
                    self.assertEqual(tracker._snapshot(), {200: live})

    def test_snapshot_propagates_permission_and_other_io_errors(self):
        for error in (PermissionError(errno.EACCES, 'synthetic denied'), OSError(errno.EIO, 'synthetic I/O failure')):
            with self.subTest(error=type(error).__name__):
                tracker = processes.OwnedProcessTracker(Mock(side_effect=error))
                with patch.object(Path, 'iterdir', return_value=[Path('/proc/100')]):
                    with self.assertRaises(type(error)):
                        tracker._snapshot()

    def test_busy_enumeration_skips_esrch_and_enoent_but_keeps_live_jvm(self):
        for error_type in (FileNotFoundError, ProcessLookupError):
            with self.subTest(error=error_type.__name__):
                with patch.object(Path, 'iterdir', return_value=[Path('/proc/100'), Path('/proc/200')]), \
                        patch.object(Path, 'read_bytes', side_effect=[gone(error_type), b'/jdk/bin/java\0GradleDaemon\0']):
                    self.assertEqual(processes.busy_processes(), [dict(pid=200, executable='java', kind='gradle')])

    def test_busy_enumeration_propagates_permission_and_other_io_errors(self):
        for error in (PermissionError(errno.EACCES, 'synthetic denied'), OSError(errno.EIO, 'synthetic I/O failure')):
            with self.subTest(error=type(error).__name__):
                with patch.object(Path, 'iterdir', return_value=[Path('/proc/100')]), \
                        patch.object(Path, 'read_bytes', side_effect=error):
                    with self.assertRaises(type(error)):
                        processes.busy_processes()


if __name__ == '__main__':
    unittest.main()
