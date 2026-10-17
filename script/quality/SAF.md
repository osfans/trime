# SAF Regression Checks

Run only against an emulator with the matching debug APK and androidTest APK.
The instrumentation checks the hardware name before creating UUID-scoped
fixtures. It does not reset the Rime user dictionary or run orphan cleanup
against the application's live Rime data directory.

```sh
adb -s emulator-5560 shell am instrument -w -r -e saf true \
  com.osfans.trime.debug.test/com.osfans.trime.StartupResponsivenessInstrumentation
```

Require both `passed=true` and `INSTRUMENTATION_CODE: -1` in the output. The
command's shell exit status alone does not prove an instrumentation test passed.
Use `-r` to retain the result bundle and final code, not only the pretty stream.

`SafCompatibilityProbe` exercises Android `DocumentsContract`, cursors, reliable
pipes, opaque/changing IDs, read-back verification, replacement and rollback
failures, retained recovery data, unsupported rename, incomplete/null listings,
invalid/duplicate names and denied access. The fixture resolver is in-process;
its permission failures are injected, not evidence of real URI-grant behavior.
It also tests Trime's own document provider using an isolated temporary subtree.

For the real system provider, select a newly created `trime-saf-UUID` directory
in the system document picker on the disposable emulator. Pass its granted URI:

```sh
adb -s emulator-5560 shell am instrument -w -r -e saf true \
  -e safTree 'content://com.android.externalstorage.documents/tree/primary%3ADocuments%2Ftrime-saf-UUID' \
  com.osfans.trime.debug.test/com.osfans.trime.StartupResponsivenessInstrumentation
```

This additional probe checks a persisted read/write grant, creates a new child
directory, performs a create/replace/read-back round trip, then removes only that
new child. Repeat after stopping/restarting the app to check persisted access.
Do not substitute a personal data directory or clear an existing emulator.

After closing the picker and force-stopping the test app, repeat with
`-e safRevoke true` and the same `safTree`. This releases only that directory's
persisted grant and requires an actual system `SecurityException` on the next
query. Remaining transient picker grants cause failure, not a false pass.
Re-select the directory before any subsequent round-trip test.

For clipboard-editor visibility, select this build as the default IME and use
`am instrument -w -r -e clip true` with the same runner. This requires editor
window focus, an actual visible `keyboard_view` in the IME accessibility window,
stable bounds for one second, and a nonblank screenshot. The system's IME-insets
visibility flag alone does not prove the asynchronous keyboard has rendered.

Before visibility tests, wait for emulator boot services and dismiss any recorded
system ANR dialog. Recheck the selected default IME after a force-stop; the system
can switch away from a stopped IME. Keep failed attempts as separate evidence.

Prefer letting the app create its runtime directories. When using a verified
same-build system-dictionary cache on a disposable rooted emulator, preserve the
app UID, group and SELinux labels. A directory created by `adb push` as root may
be unreadable or unwritable by the app. Do not disable SELinux or relax application
permissions to work around a test-fixture error. Never seed personal userdb files;
record cached system data as a warm UI check, not a cold performance baseline.

## Provider Limits

- Existing files require rename support for safe replacement. Providers lacking
  this capability can receive new files but replacement fails without overwriting
  the original. There is no silent destructive overwrite fallback.
- A loading or failed directory listing aborts the import before orphan cleanup.
  Retry after the remote provider has completed loading.
- Unknown size or timestamp disables the metadata-cache shortcut. Verification
  uses streamed SHA-256 read-back with bounded buffers, not descriptor stat size.
- Failed rollback retains the operation-scoped backup. Subsequent import/cleanup
  skips these recovery artifacts. Never delete them merely to silence an error.
- Duplicate names or repeated document IDs abort traversal. A provider exposing
  the same document under multiple parents is not treated as a plain sync tree.
- Local symbolic links below the sync root are not followed, including links
  back into that root; otherwise aliases could bypass user-dictionary exclusions.
- Passing the fixture and system-provider probes does not establish compatibility
  with every third-party cloud provider, OEM picker, or Android release. Record
  exactly which emulator/API/provider was exercised in the build report.

API contracts: [document flags and metadata](https://developer.android.com/reference/android/provider/DocumentsContract.Document),
[reliable descriptor errors](https://developer.android.com/reference/android/os/ParcelFileDescriptor#checkError()),
[persisted directory access](https://developer.android.com/training/data-storage/shared/documents-files#persist-permissions).
