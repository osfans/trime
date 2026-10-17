# Stable build-plugin backport

AGP 9.4.1 and KSP 2.3.11 still call `Configuration.setVisible(false)`.
A Gradle 9.8.1 configuration run emits 52 AGP and 3 KSP deprecations per build phase.
There are 28 distinct AGP bytecode call sites and 3 KSP call sites; source-set
configuration executes some AGP sites repeatedly. These are build-plugin calls,
not application compiler or Android Lint warnings.

[Gradle's upgrade guide](https://docs.gradle.org/9.8.1/userguide/upgrading_version_9.html#deprecate-visible-property)
states that `visible` ceased to control `assemble` in Gradle 9.0 and can be
removed. It does not govern dependency resolution or access between projects.
[Google's upstream change](https://android.googlesource.com/platform/tools/base/+/b54a369d23f52967d0073cb715c70a9c998f51c6)
removes the same obsolete assignments while retaining the real consumption and
resolution flags. The official AGP 9.5.0-alpha09 source JAR has removed all 28
sites, but the latest stable AGP 9.4.1 still contains them. The KSP 2.3.12 source
JAR still contains all 3 KSP sites.

The settings plugin uses public artifact-type and artifact-transform APIs
to apply this narrow binary backport to the two stable plugin JARs. It keeps
Gradle 9.8.1, including its current security fixes, and avoids switching the
release build to an alpha AGP. It does not modify the Gradle distribution,
original dependency cache, warning logger, warning mode, or application JARs.

The settings plugin lives in the separate `build-logic:compatibility` module
and JAR, with only ASM and the public Gradle API. It does not share a JAR or
runtime dependency on AGP with the application convention plugins. Loading
application conventions from the settings classloader would make their
compile-only AGP types unavailable when app plugins are instantiated later;
putting unpatched AGP into that parent classloader would bypass the backport.

The included build's common parent declares `kotlin-dsl` and the matching
embedded-Kotlin serialization plugin with `apply false`. Both child modules
therefore share the Kotlin Gradle plugin classpath while producing separate
plugin JARs. The convention child applies serialization without another
version request, following [Gradle's parent plugin declaration pattern](https://docs.gradle.org/current/userguide/plugins_intermediate.html#sec:subprojects_plugins_dsl).
This resolves Kotlin's duplicate-plugin-classloader diagnostic without
disabling its checks or adding an AGP runtime dependency to the settings plugin.

The inputs must match these complete upstream JAR hashes:

| Artifact | SHA-256 | Call sites / changed classes |
| --- | --- | --- |
| `com.android.tools.build:gradle:9.4.1` | `4021fa6ea76b6fac076dd0bdacbb3e1b76c31f33b6c392e1a49a2e42072196bf` | 28 / 16 |
| `com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11` | `8a00502695b7c44a190c12b9b54d6668724b7aa583f48f9657fab437fbcf8fb9` | 3 / 2 |

Gradle's standard instrumentation pipeline produces class-only JARs while
retaining an original JAR for resources and analysis lookup. The backport must
run before this analysis: the original and instrumented classpath views must
agree on the same input bytes. An unconstrained transform allowed analysis of
the upstream JAR while the original classpath view selected the patched JAR.
Clean CI resolution then failed because the analysis metadata's original hash
could not be found. A warm local cache did not establish correct ordering.

The transform now requires `org.gradle.internal.instrumented=not-instrumented`
on both its input and output. This is an internal Gradle phase attribute,
explicitly pinned to Gradle 9.8.1 by the settings plugin's existing version
check. It orders the backport before Gradle's own analysis, metadata merge and
instrumentation. It does not set the classpath's requested instrumentation
phase, disable the instrumentation agent, or replace Gradle's pipeline.
[Gradle 9.8.1's classpath resolver](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/api/internal/initialization/DefaultScriptClassPathResolver.java)
creates the original, analysis and instrumented views, and
[its external instrumentation transform](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/api/internal/initialization/transform/ExternalDependencyInstrumentingArtifactTransform.java)
looks up the original file using the analysis metadata. This is an intentional
version-specific ordering dependency; re-audit it when changing Gradle.

The frozen instrumented-JAR hashes and pairing checks in the backport utility
and its tests retain the earlier investigation's rejection coverage. The
registered transform no longer accepts instrumented-phase inputs: only raw
JARs enter it, and Gradle generates and pairs the later instrumented JARs.
Original resources, plugin registrations and the normal instrumentation
validation remain intact. The plugin explicitly requires JDK 17 as well as
Gradle 9.8.1; a different environment fails before applying the backport.

Reproduce the executable checks from a clean checkout with:

```sh
./gradlew :build-logic:compatibility:test
./gradlew help --warning-mode=fail
```

In addition, run `help --warning-mode=fail` with a new Gradle user home and an
empty versioned transform cache, then repeat it warm. An isolated copy of the
wrapper distribution and dependency download cache may seed this check; do
not copy the previous `caches/9.8.1/transforms` directory or change the original
cache. Preserve both command logs and check for the 28 AGP and 3 KSP backport
call sites on the cold run. A successful warm run alone does not reproduce
the clean-CI resolution failure. The four-platform CI configuration steps
must also complete before claiming this regression is closed.

For a bytecode inventory, resolve the pinned raw artifacts above, verify their
complete SHA-256 values, and inspect the 18 class names listed in
`GradleVisibleBackport.kt`. Re-audit the upstream inputs and the Gradle phase
contract when changing this environment.

The per-class call counts are frozen in `GradleVisibleBackport.kt`. Each call
must use the exact public `Configuration` setter signature, pass the constant
`false`, and discard its result. The backport replaces that invocation with a
stack pop: the boolean is discarded, the original receiver remains, and the
existing following pop discards the receiver. This preserves the original
operand-stack types and frames without invoking the obsolete setter.
Real `canBeConsumed`, `canBeResolved`, and `canBeDeclared` setters remain intact.
Any legacy getter, unexpected signature/value/result use, changed input hash,
new version, signed JAR, duplicate archive entry, or changed call inventory
fails before writing the output JAR.

Unmodified classes and every input's own non-class entry retain their exact
bytes, including plugin registrations, manifests, upstream copyright notices
and licenses in the raw resource route. Only the 16 AGP and 2 KSP class names
can change in the raw input. Archive compression
may differ. The raw artifact bytes and filename participate in transform input
normalization. Other buildscript JARs use an identity transform, returning the
original input file without writing or modifying it. The attribute is attached
to external JAR artifacts rather than their published variants. The two local
plugin modules explicitly publish the already compatible attribute on their
`apiElements` and `runtimeElements`: their project JARs bypass this transform,
preserving Gradle's included-build producer dependencies. Application runtime
dependencies are outside this build-plugin transform's scope.
The transform must run before plugin application; verify
that requirement with `help --warning-mode=fail` before a complete build.

The tests execute a generated JVM method against a configuration proxy to
verify that the obsolete call disappears while real flags and return value
remain correct. They also reject malformed call patterns and changed plugin
inputs. Full Debug/Release compilation, JVM tests, Lint and APK compatibility
checks remain required; a passing transform test does not replace them.

When stable AGP and KSP remove these calls, remove the settings plugin,
`GradleCompatibilityPlugin.kt`, `GradleVisibleBackport.kt`, their tests, and the
build-only ASM dependency. Do not silently update the pinned hashes or relax
the guards merely to accommodate a new plugin release.

## Unused coroutine service resources after R8

Removing the legacy application keep rules lets the bundled coroutines 1.11.0
R8 rules rewrite service loading into direct Android provider construction.
A pre-fix Release inspection retained two service files renamed after interface
merging, and each Release APK emitted two v1 signature warnings. Reproduce the
post-fix packaging and signature checks from a clean checkout with:

```sh
./gradlew :app:assembleRelease --warning-mode=fail
for apk in app/build/outputs/apk/release/*.apk; do
  if unzip -Z1 "$apk" | grep -E 'META-INF/services/kotlinx[.]coroutines[.](internal[.]MainDispatcherFactory|CoroutineExceptionHandler|android[.]AndroidDispatcherFactory|android[.]AndroidExceptionPreHandler)'; then
    echo "unexpected optimized coroutine service descriptor in $apk" >&2
    exit 1
  fi
  "$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$apk"
done
```

`RemoveOptimizedCoroutineServicesTask` transforms the R8-produced Java resource
archive before APK packaging and signing. The original service descriptors stay
available to R8. This requires the reviewed internal AGP 9.4.1 artifact graph:
the resource transform is registered after AGP has registered its R8 producer,
and consumes the actual R8 DEX, configuration and original resource artifacts.
The expected order is merge resources, R8, checked resource transform, package,
then normal signing. It does not rewrite a signed APK, change DEX or disable v1.

The task pins AGP 9.4.1, R8 9.4.24 and its compiler revision, both original
coroutines 1.11.0 JAR hashes and their selected modern R8 consumer-rule hashes.
It requires the original two exact provider declarations and their exact
post-R8 equivalents, then checks every DEX method ID, string and class
definition. ServiceLoader calls, Class/ClassLoader resource lookups, dynamic
SPI literals, missing Android provider classes, malformed DEX, duplicate class
definitions, unknown services or provider contents, and signed input archives
block the operation. Every retained resource entry's bytes are checked against
the original archive. The eight binary DEX/ZIP tests include actual preservation
and rejection cases; actual Release DEX semantic review, signature verification
and Android acceptance are still required for publication.

## Debug coroutine service resources

Unminified Debug builds still need coroutine service discovery. Android v1
verification excludes `META-INF` entries from signature coverage, so keeping
the two provider declarations there produces signature warnings even when the
manifest contains their digests. Debug builds relocate their unchanged bytes
to `trime-services/` before normal APK signing. Release continues to use the
checked post-R8 removal described above.

`GenerateDebugCoroutineResourcesTask` pins the two original coroutines 1.11.0
JARs. `VerifyDebugCoroutineResourcesTask` checks dependency Java resources and
application resource directories before packaging exclusions: exactly the two
original declarations and two relocated copies must exist, with the reviewed
provider bytes. AGP 9.4.1 is required. Missing, conflicting, duplicate or extra
service declarations fail the build. Final APK resource inspection is still
required because compiler-added resources are not necessarily source resources.
Debug also removes AGP's exact default `/META-INF/services/**` merge rule:
AGP evaluates merge rules before exclusions. Other merge rules remain intact;
a regression test uses AGP's actual matcher to check this precedence.

The Debug application ASM transform redirects four service-loading calls in
three coroutine classes to `DebugCoroutineServices`. It checks the class,
method, invocation signature and occurrence count; it preserves other
instructions, frames and coroutine debug/recovery flags. The helper uses the
platform ServiceLoader and the original defining loader, adding the relocated
resource location. The supported APK has one built-in provider per service;
this is not a general ordering guarantee for arbitrary external providers.
The Android unit-test classpath is checked to retain the original coroutine
bytecode and service resources.

Run `:app:verifyDebugCoroutineResources`, the build-logic and Debug unit tests,
and normal Debug assembly. Inspect the resulting APK resources and all four
DEX call sites, then run `apksigner verify --verbose --print-certs` without
filtering its output. The dedicated `CoroutineFastPackagingInstrumentation`
and `CoroutineSlowPackagingInstrumentation` components require
`-e coroutine_packaging true` and separate fresh emulator target processes.
They set loader/debug/recovery properties before the real Application
constructor, check the actual initialized fields and provider collection, and
exercise Main and Main.immediate dispatch. They do not induce an uncaught
exception or prove Android 8.0/8.1 pre-handler execution. If ART does not enable
class assertions, the result records that limitation instead of claiming a
positive assertion test. These checks do not replace general device acceptance.
