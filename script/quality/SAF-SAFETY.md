# SAF Synchronization Safety

Automatic local deletion requires a matching record for the currently selected
tree, a known size/timestamp and a matching SHA-256 of the local content. Import
hashes are computed while copying bytes. Export reuses the hash already verified
by the provider read-back. Both use bounded buffers, not whole-file allocation.

The following are not deletion authority:

- A first selection or switch to an empty external folder.
- An old index containing only size and modification time.
- Unknown provider metadata, a failed listing/import or an unreadable local file.
- Local content edited without changing its size or coarse modification time.

Installation metadata, user-database exclusions and own synchronization dumps
retain their existing protection. Only empty parents of successfully removed
tracked files are pruned. Absent history entries are retired after a complete
import; a future unrelated local file cannot inherit an old deletion record.
Legacy/untracked files may therefore need explicit manual cleanup.

Routine imports never copy raw `.userdb` directories, including a first import
with the legacy migration preference unset. A per-file copy is not a consistent
LevelDB snapshot and may overwrite a database that the engine already has open.
Portable `.userdb.txt` dumps remain importable for native Rime merging; existing
local binary user dictionaries are preserved. Real-provider fixtures cover both
an existing local database and an external-only database without touching any
actual learning data.

Index publication uses the existing atomic local-file writer. Failed copies do
not authorize orphan cleanup and partial import results are failures. A failed
pre-sync import stops subsequent export; a failed theme import keeps the active
theme. Coroutine cancellation propagates without resetting storage mode, logging
a routine cancellation as an error, or leaving import progress pending.

Storage mutations in setup/profile settings, background synchronization, schema
export and theme import coordinate with Rime maintenance. Native deployment must
not be called while a UI importer still holds that non-reentrant mutex.

## Validation

`OrphanCleanerTest`, `OrphanHistoryTest` and `SyncFingerprintTest` cover deletion,
preservation, legacy compatibility and standard digest encoding. Existing
provider fault and path/symlink tests remain in place. Real-provider Android
probes require a picker-granted dedicated `trime-saf-UUID` tree and exercise
persisted grants, replacement/read-back, hash-backed imports/deletions, partial
import failure, process restart and revocation. See `RUNTIME-AUDIT.md` for actual
execution status; source presence alone does not constitute a passed test.
