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

Gradle's standard instrumentation pipeline additionally produces class-only
JARs while keeping the original artifact as the paired resource source. The
public transform can execute on both routes. It neither disables Gradle's
instrumentation nor relies on private attributes to order the routes.
[Gradle 9.8.1's implementation](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/api/internal/initialization/transform/ExternalDependencyInstrumentingArtifactTransform.java)
and its
[classpath resolver](https://github.com/gradle/gradle/blob/v9.8.1/subprojects/core/src/main/java/org/gradle/api/internal/initialization/DefaultScriptClassPathResolver.java)
describe this pairing. Those internal sources were read as implementation
evidence; this project does not call their APIs.

The additionally accepted input files were produced by the actual Gradle
9.8.1/JDK 17.0.19+10 run and inspected with `javap -c -p` for all 18 class names:

| Derived input | SHA-256 | Bytes |
| --- | --- | --- |
| `instrumented-gradle-9.4.1.jar` | `35811e6a9e6c682680b3657f21706d2bcb717fe510352f8a601451cce224d5c0` | 29,997,618 |
| `instrumented-symbol-processing-gradle-plugin-2.3.11.jar` | `1410088bae2f9717d78414944950cb4f4a113c9f7540bc527227481bf176b901` | 392,708 |

Their complete class-name sets match the corresponding original JARs. All
28/3 setter invocations still use the same public interface, constant false,
and discarded result; the real consumption/resolution flag invocations also
match (AGP 28/8, KSP 3/3 in the inspected classes). Raw and instrumented inputs
are independently hash-checked against their own complete bytes before
patching. Reproduce the executable checks from a clean checkout with:

```sh
./gradlew :build-logic:compatibility:test
./gradlew help --warning-mode=fail
```

For a bytecode inventory, resolve the pinned artifacts above, verify their
complete SHA-256 values, and run `javap -c -p` on the 18 class names listed in
`GradleVisibleBackport.kt`. Class-only instrumented JARs do not contain the
original resources; the separate raw route retains those exact resources and
plugin registrations.

The plugin explicitly requires Gradle 9.8.1 and JDK 17. A different version,
an unknown instrumented artifact or a changed generated hash fails rather than
silently accepting another instrumentation implementation. Re-audit the
upstream input and derived artifact when changing this environment.

The transformed class-only JAR keeps the producer's relative location
`instrumented/instrumented-<original-name>.jar`. The
[Gradle 9.8.1 pairing check](https://github.com/gradle/gradle/blob/v9.8.1/platforms/core-runtime/classloaders/src/main/java/org/gradle/internal/classpath/TransformedClassPath.java)
requires that directory and name; returning a flat output breaks its validation.
The project uses public `TransformOutputs.file()` to preserve that structure.
It does not disable or replace Gradle's validation of the original/instrumented
pair, instrumentation markers or dependency analysis.

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
can change in either route. Archive compression
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
