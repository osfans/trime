# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# disable obfuscation
-dontobfuscate

# Keep JNI interface
-keep class com.osfans.trime.core.* { *; }

# The separately compiled acceptance APK calls these production entry points.
# Preserve their JVM signatures while the remaining Release code stays optimized.
-keepclassmembers class com.osfans.trime.daemon.RimeDaemon {
    public static final com.osfans.trime.daemon.RimeDaemon INSTANCE;
    private com.osfans.trime.core.Rime getRealRime();
    public kotlinx.coroutines.flow.StateFlow getEngineState$app();
    public com.osfans.trime.daemon.RimeSession getFirstSessionOrNull();
    public com.osfans.trime.daemon.RimeSession createSession(java.lang.String);
    public void destroySession(java.lang.String);
}
-keep interface com.osfans.trime.daemon.RimeSession {
    public java.lang.Object run(kotlin.jvm.functions.Function2);
    public java.lang.Object runOnReady(kotlin.jvm.functions.Function2, kotlin.coroutines.Continuation);
}
-keepclassmembers class com.osfans.trime.core.data.DataManager {
    public static final com.osfans.trime.core.data.DataManager INSTANCE;
    public java.io.File getUserDataDir();
    public java.io.File getSharedDataDir();
    public void sync();
}
-keepclassmembers class com.osfans.trime.data.prefs.AppPrefs {
    public static final com.osfans.trime.data.prefs.AppPrefs$Companion Companion;
    public com.osfans.trime.data.prefs.AppPrefs$Candidates getCandidates();
    public com.osfans.trime.data.prefs.AppPrefs$General getGeneral();
}
-keepclassmembers class com.osfans.trime.data.prefs.AppPrefs$Companion {
    public com.osfans.trime.data.prefs.AppPrefs defaultInstance();
}
-keepclassmembers class com.osfans.trime.data.prefs.AppPrefs$Candidates {
    public com.osfans.trime.data.prefs.PreferenceDelegate getNextWordPrediction();
}
-keepclassmembers class com.osfans.trime.data.prefs.AppPrefs$General {
    public com.osfans.trime.data.prefs.PreferenceDelegate$SerializableDelegate getInlinePreeditMode();
}
-keepclassmembers class com.osfans.trime.data.prefs.PreferenceDelegate {
    public android.content.SharedPreferences getSharedPreferences();
    public java.lang.String getKey();
    public java.lang.Object getValue();
    public void setValue(java.lang.Object);
}
-keepclassmembers class com.osfans.trime.data.prefs.PreferenceDelegate$SerializableDelegate {
    public java.lang.Object getValue();
    public void setValue(java.lang.Object);
}
-keepclassmembers enum com.osfans.trime.ime.core.InlinePreeditMode {
    public static kotlin.enums.EnumEntries getEntries();
}

# The separate acceptance APK uses the host's optimized Kotlin/coroutine APIs.
# Preserve only its actual T11 calls, type identities and shared cleanup calls.
# Empty facade rules preserve inherited-call owners; members stay with their
# original declaring classes. Production null-check removal below is unchanged.
-keep class androidx.preference.PreferenceManager {
    public static android.content.SharedPreferences getDefaultSharedPreferences(android.content.Context);
}
-keep class kotlin.ExceptionsKt { }
-keep class kotlin.ExceptionsKt__ExceptionsKt {
    public static final java.lang.String stackTraceToString(java.lang.Throwable);
    public static final void addSuppressed(java.lang.Throwable, java.lang.Throwable);
}
-keep class kotlin.KotlinNothingValueException {
    public <init>();
}
-keep class kotlin.Pair { }
-keep class kotlin.ResultKt {
    public static final void throwOnFailure(java.lang.Object);
}
-keep class kotlin.TuplesKt {
    public static final kotlin.Pair to(java.lang.Object, java.lang.Object);
}
-keep class kotlin.Unit {
    public static final kotlin.Unit INSTANCE;
}
-keep class kotlin.collections.ArraysKt { }
-keep class kotlin.collections.ArraysKt___ArraysKt {
    public static final java.lang.Object first(java.lang.Object[]);
}
-keep class kotlin.collections.CollectionsKt { }
-keep class kotlin.collections.CollectionsKt__CollectionsJVMKt {
    public static final java.util.List listOf(java.lang.Object);
}
-keep class kotlin.collections.CollectionsKt__CollectionsKt {
    public static final java.util.List listOf(java.lang.Object[]);
    public static final void throwCountOverflow();
}
-keep class kotlin.collections.CollectionsKt__IterablesKt {
    public static final int collectionSizeOrDefault(java.lang.Iterable, int);
}
-keep class kotlin.collections.CollectionsKt___CollectionsKt {
    public static final java.lang.Object last(java.util.List);
    public static final java.util.List drop(java.lang.Iterable, int);
    public static final java.util.List dropLast(java.util.List, int);
    public static final java.util.List plus(java.util.Collection, java.lang.Iterable);
    public static final java.util.Set toSet(java.lang.Iterable);
}
-keep class kotlin.collections.SetsKt { }
-keep class kotlin.collections.SetsKt__SetsKt {
    public static final java.util.Set setOf(java.lang.Object[]);
}
-keep interface kotlin.coroutines.Continuation { }
-keep interface kotlin.coroutines.CoroutineContext { }
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt { }
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt {
    public static final java.lang.Object getCOROUTINE_SUSPENDED();
}
-keep class kotlin.coroutines.jvm.internal.Boxing {
    public static final java.lang.Boolean boxBoolean(boolean);
    public static final java.lang.Integer boxInt(int);
    public static final java.lang.Long boxLong(long);
}
-keep class kotlin.coroutines.jvm.internal.ContinuationImpl {
    public <init>(kotlin.coroutines.Continuation);
}
-keep class kotlin.coroutines.jvm.internal.SpillingKt {
    public static final java.lang.Object nullOutSpilledVariable(java.lang.Object);
}
-keep class kotlin.coroutines.jvm.internal.SuspendLambda {
    public <init>(int, kotlin.coroutines.Continuation);
}
-keep interface kotlin.enums.EnumEntries { }
-keep class kotlin.io.FileTreeWalk { }
-keep class kotlin.io.FilesKt { }
-keep class kotlin.io.FilesKt__FileReadWriteKt {
    public static final byte[] readBytes(java.io.File);
    public static final void writeBytes(java.io.File, byte[]);
    public static void writeText$default(java.io.File, java.lang.String, java.nio.charset.Charset, int, java.lang.Object);
}
-keep class kotlin.io.FilesKt__FileTreeWalkKt {
    public static final kotlin.io.FileTreeWalk walkBottomUp(java.io.File);
    public static final kotlin.io.FileTreeWalk walkTopDown(java.io.File);
}
-keep class kotlin.io.FilesKt__UtilsKt {
    public static final boolean deleteRecursively(java.io.File);
}
-keep interface kotlin.jvm.functions.Function0 {
    public abstract java.lang.Object invoke();
}
-keep interface kotlin.jvm.functions.Function1 { }
-keep interface kotlin.jvm.functions.Function2 { }
-keep class kotlin.jvm.internal.DefaultConstructorMarker { }
-keep class kotlin.jvm.internal.Intrinsics {
    public static boolean areEqual(java.lang.Object, java.lang.Object);
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
}
-keep class kotlin.jvm.internal.Ref$BooleanRef {
    public <init>();
    public boolean element;
}
-keep class kotlin.jvm.internal.Ref$IntRef {
    public <init>();
    public int element;
}
-keep class kotlin.jvm.internal.Ref$ObjectRef {
    public <init>();
    public java.lang.Object element;
}
-keep class kotlin.ranges.RangesKt { }
-keep class kotlin.ranges.RangesKt___RangesKt {
    public static final long coerceAtLeast(long, long);
}
-keep interface kotlin.sequences.Sequence {
    public abstract java.util.Iterator iterator();
}
-keep class kotlin.sequences.SequencesKt { }
-keep class kotlin.sequences.SequencesKt___SequencesKt {
    public static final kotlin.sequences.Sequence filter(kotlin.sequences.Sequence, kotlin.jvm.functions.Function1);
}
-keep class kotlin.text.StringsKt { }
-keep class kotlin.text.StringsKt__StringsJVMKt {
    public static boolean startsWith$default(java.lang.String, java.lang.String, boolean, int, java.lang.Object);
    public static java.lang.String replace$default(java.lang.String, java.lang.String, java.lang.String, boolean, int, java.lang.Object);
}
-keep class kotlinx.coroutines.BuildersKt {
    public static final kotlinx.coroutines.Job launch(kotlinx.coroutines.CoroutineScope, kotlin.coroutines.CoroutineContext, kotlinx.coroutines.CoroutineStart, kotlin.jvm.functions.Function2);
    public static java.lang.Object runBlockingK$default(kotlin.coroutines.CoroutineContext, kotlin.jvm.functions.Function2, int, java.lang.Object);
}
-keep class kotlinx.coroutines.CoroutineDispatcher { }
-keep interface kotlinx.coroutines.CoroutineScope { }
-keep enum kotlinx.coroutines.CoroutineStart {
    public static final kotlinx.coroutines.CoroutineStart UNDISPATCHED;
}
-keep class kotlinx.coroutines.DelayKt {
    public static final java.lang.Object delay(long, kotlin.coroutines.Continuation);
}
-keep class kotlinx.coroutines.Dispatchers {
    public static final kotlinx.coroutines.CoroutineDispatcher getDefault();
}
-keep interface kotlinx.coroutines.Job { }
-keep class kotlinx.coroutines.JobKt {
    public static final java.lang.Object cancelAndJoin(kotlinx.coroutines.Job, kotlin.coroutines.Continuation);
}
-keep class kotlinx.coroutines.TimeoutKt {
    public static final java.lang.Object withTimeout(long, kotlin.jvm.functions.Function2, kotlin.coroutines.Continuation);
}
-keep interface kotlinx.coroutines.flow.Flow { }
-keep interface kotlinx.coroutines.flow.FlowCollector { }
-keep class kotlinx.coroutines.flow.FlowKt {
    public static final java.lang.Object first(kotlinx.coroutines.flow.Flow, kotlin.jvm.functions.Function2, kotlin.coroutines.Continuation);
}
-keep interface kotlinx.coroutines.flow.MutableStateFlow {
    public abstract java.lang.Object getValue();
    public abstract void setValue(java.lang.Object);
}
-keep interface kotlinx.coroutines.flow.SharedFlow {
    public abstract java.lang.Object collect(kotlinx.coroutines.flow.FlowCollector, kotlin.coroutines.Continuation);
}
-keep interface kotlinx.coroutines.flow.StateFlow { }
-keep class kotlinx.coroutines.flow.StateFlowKt {
    public static final kotlinx.coroutines.flow.MutableStateFlow MutableStateFlow(java.lang.Object);
}

# Use kotlinx-coroutines' bundled consumer rules for its Android providers.
# Extra keep rules for service types block R8's ServiceLoader rewrite into direct
# provider construction. Preserve service descriptors as R8 inputs; do not remove
# them from packaging before verifying the optimized dispatcher/handler code.

# remove kotlin null checks
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkNotNullExpressionValue(...);
    static void checkReturnedValueIsNotNull(...);
    static void checkFieldIsNotNull(...);
    static void checkParameterIsNotNull(...);
    static void checkNotNullParameter(...);
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
