# Android 5.x Compatibility Constraints

The application must retain `minSdk = 21`. Do not use `tools:overrideLibrary`
to force incompatible Android libraries into the APK. Compile/target SDK upgrades
do not authorize changing this minimum.

The base audit checked published AAR manifests and POM dependencies on
2026-10-07. Paging was re-audited on 2026-10-09: the official runtime AAR
manifests declare minSdk 21 for 3.3.6 and minSdk 23 for 3.5.2. The 2026-10-14
re-audit of both official runtime and runtime-ktx AARs confirms minSdk 21 for
3.3.6 and minSdk 23 for the newly suggested 3.5.3. Keep the compatible version;
update only the exact audited version-notification exception.

| Dependency | Selected | Incompatible update | Reason |
| --- | --- | --- | --- |
| Activity | 1.11.0 | 1.12.0 / 1.13.0 | minSdk 23 |
| Core | 1.17.0 | 1.18.0 / 1.19.1 | minSdk 23 |
| AppCompat | 1.7.1 | 1.8.0 | minSdk 23 |
| Navigation | 2.9.8 | 2.10.2 | runtime-android minSdk 24 |
| Room | 2.7.2 | 2.8.5 | runtime-android minSdk 23 |
| Paging | 3.3.6 | 3.5.3 | runtime and runtime-ktx minSdk 23 |
| WorkManager | 2.10.5 | 2.12.0 | runtime minSdk 24 |

The version catalog uses strict versions at these boundaries. Manifest merger
and Lint API checks remain enabled: transitive dependencies must also be checked.
Re-audit the constraint before accepting an update, including its transitive
dependencies. Version-notification exceptions must match the audited coordinates
and versions only, never disable all dependency checks or create a general baseline.

`app/lint.xml` exempts exactly 11 `GradleDependency` update notices using seven
narrowly anchored patterns: Navigation has two coordinates, Room has four,
and Activity, AppCompat, Core, Paging and WorkManager have one each. The Paging
pattern now matches only the re-audited 3.3.6-to-3.5.3 notice. Room's build-time
compiler is kept aligned with its API-21-compatible
runtime; the compiler itself is not an Android runtime library. A change to a
selected version or suggested update stops matching the exception. Lint warnings
are treated as errors, and release-build checking is no longer disabled.

Build tool versions are separate from these Android runtime compatibility caps.
Gradle is updated to 9.8.1 for its published security fixes; its official binary
distribution was checked against the published SHA256, which is fixed in the
wrapper properties. Kotlin's three catalog plugins share version 2.4.21, and
Spotless uses 8.10.4. The former Gradle 9.8.0 version-notification exception is
removed. Neither `AndroidGradlePluginVersion` nor `NewerVersionAvailable` is
disabled, and upstream plugin deprecation warnings remain visible.

Iconics 5.6.0 and its Compose runtime 1.9.3 dependency both declare minSdk 21.
Do not infer a library's minimum solely from its release date or the general
AndroidX policy; inspect the actual AAR and resolved dependency graph.

The 2026-10-16 audit of AboutLibraries 15.3.0 confirms that its Android runtime
AAR still declares minSdk 21 and minCompileSdk 37, matching 15.2.0. Its Kotlin
2.4.21 and serialization 1.11.0 dependencies match the catalog. The Gradle plugin
requires JDK 17 and was published with Gradle 9.8.1, matching this build. Kotest
6.2.6 is used only by JVM tests; its JVM artifacts remain compatible with JDK 17.
These versions address the four new `NewerVersionAvailable` notices through
actual upgrades, without adding notification exceptions. Build, resolved-graph,
Lint and Android runtime results must still be recorded for the new candidate.

Primary references:

- [AndroidX minimum SDK policy](https://developer.android.com/jetpack/androidx/versions)
- [Room releases](https://developer.android.com/jetpack/androidx/releases/room)
- [Paging releases](https://developer.android.com/jetpack/androidx/releases/paging)
- [Paging 3.3.6 runtime AAR](https://dl.google.com/dl/android/maven2/androidx/paging/paging-runtime/3.3.6/paging-runtime-3.3.6.aar)
- [Paging 3.5.2 runtime AAR](https://dl.google.com/dl/android/maven2/androidx/paging/paging-runtime/3.5.2/paging-runtime-3.5.2.aar)
- [Paging 3.5.3 runtime AAR](https://dl.google.com/dl/android/maven2/androidx/paging/paging-runtime/3.5.3/paging-runtime-3.5.3.aar)
- [Paging 3.5.3 runtime-ktx AAR](https://dl.google.com/dl/android/maven2/androidx/paging/paging-runtime-ktx/3.5.3/paging-runtime-ktx-3.5.3.aar)
- [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work)
- [Core 1.17 AAR](https://dl.google.com/dl/android/maven2/androidx/core/core/1.17.0/core-1.17.0.aar)
- [Core 1.18 AAR](https://dl.google.com/dl/android/maven2/androidx/core/core/1.18.0/core-1.18.0.aar)
- [Activity 1.11 AAR](https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.11.0/activity-1.11.0.aar)
- [Activity 1.12 AAR](https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.12.0/activity-1.12.0.aar)
- [Activity 1.13 AAR](https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.13.0/activity-1.13.0.aar)
- [Core 1.19.1 AAR](https://dl.google.com/dl/android/maven2/androidx/core/core/1.19.1/core-1.19.1.aar)
- [Navigation 2.10.2 runtime AAR](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-runtime-android/2.10.2/navigation-runtime-android-2.10.2.aar)
- [Room 2.8.5 runtime AAR](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime-android/2.8.5/room-runtime-android-2.8.5.aar)
- [WorkManager 2.12.0 runtime AAR](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/2.12.0/work-runtime-2.12.0.aar)
- [Iconics 5.6 dependency POM](https://repo.maven.apache.org/maven2/com/mikepenz/iconics-core/5.6.0/iconics-core-5.6.0.pom)
- [Gradle 9.8.1 security fixes](https://docs.gradle.org/9.8.1/release-notes.html)
- [Gradle 9.8.1 binary distribution SHA256](https://services.gradle.org/distributions/gradle-9.8.1-bin.zip.sha256)
- [AboutLibraries 15.3.0 release](https://github.com/mikepenz/AboutLibraries/releases/tag/15.3.0)
- [AboutLibraries 15.3.0 Android AAR](https://repo.maven.apache.org/maven2/com/mikepenz/aboutlibraries-core-android/15.3.0/aboutlibraries-core-android-15.3.0.aar)
- [Kotest 6.2.6 release](https://github.com/kotest/kotest/releases/tag/v6.2.6)

Runtime test coverage, APK fingerprints and any remaining limitations belong in
the build-specific regression report. An API 21 manifest alone is not evidence
of a successful Android 5.x device test.
