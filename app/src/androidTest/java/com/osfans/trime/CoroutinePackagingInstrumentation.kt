/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.lang.reflect.Modifier

/** Each component must be launched in a new target process; changing properties later is invalid. */
class CoroutineFastPackagingInstrumentation : CoroutinePackagingInstrumentation(true)

class CoroutineSlowPackagingInstrumentation : CoroutinePackagingInstrumentation(false)

abstract class CoroutinePackagingInstrumentation(private val expectedFastLoader: Boolean) : Instrumentation() {
    private val evidence = JSONObject()
    private var setupFailure: Throwable? = null
    private var targetLoader: ClassLoader? = null

    @Volatile private var applicationConstructed = false

    @Volatile private var applicationCreated = false

    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        // TrimeApplication's constructor creates MainScope(), which reads Dispatchers.Main.
        // Instrumentation.onCreate() follows that constructor and is already too late for FAST.
        try {
            check(!applicationConstructed) { "Dedicated probe attempted a second Application" }
            targetLoader = cl
            System.setProperty("kotlinx.coroutines.fast.service.loader", expectedFastLoader.toString())
            System.setProperty("kotlinx.coroutines.debug", "on")
            System.setProperty("kotlinx.coroutines.stacktrace.recovery", "true")
            cl.setClassAssertionStatus("kotlinx.coroutines.CoroutineId", true)
            evidence.put("configured_before_application_constructor", true)
                .put("configuration_uptime_ms", SystemClock.uptimeMillis())
                .put("application_class", className)
                .put("expected_fast_loader", expectedFastLoader)
        } catch (error: Throwable) {
            setupFailure = error
        }
        return super.newApplication(cl, className, context).also { applicationConstructed = true }
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        try {
            check(arguments?.getString("coroutine_packaging") == "true") { "Explicit coroutine_packaging=true is required" }
            check(applicationConstructed && !applicationCreated) { "Unexpected Application lifecycle order" }
            val loader = checkNotNull(targetLoader)
            // Read the real initialized field, not merely the System property we requested.
            val type = Class.forName("kotlinx.coroutines.internal.MainDispatcherLoader", true, loader)
            val field = type.getDeclaredField("FAST_SERVICE_LOADER_ENABLED").apply { isAccessible = true }
            check(Modifier.isStatic(field.modifiers) && field.type == Boolean::class.javaPrimitiveType)
            val actual = field.getBoolean(null)
            evidence.put("fast_loader_read_before_application_on_create", actual)
            check(actual == expectedFastLoader) { "FAST initialized as $actual instead of $expectedFastLoader" }
            setupFailure?.let { throw it }
        } catch (error: Throwable) {
            val previous = setupFailure
            if (previous == null) {
                setupFailure = error
            } else if (previous !== error) {
                previous.addSuppressed(error)
            }
        }
        start()
    }

    override fun callApplicationOnCreate(app: Application) {
        super.callApplicationOnCreate(app)
        applicationCreated = true
    }

    override fun onStart() {
        val result = Bundle()
        var passed = false
        try {
            waitForIdleSync()
            setupFailure?.let { throw it }
            check(applicationConstructed && applicationCreated) { "Real Application did not complete onCreate" }
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "This packaging probe only runs on an emulator" }
            val loader = checkNotNull(targetLoader)
            check(targetContext.classLoader === loader) { "Target ClassLoader changed after Application creation" }
            evidence.put("pid", android.os.Process.myPid()).put("api", Build.VERSION.SDK_INT)
                .put("uid", android.os.Process.myUid()).put("component", javaClass.name)
                .put("process_start_ticks", java.io.File("/proc/self/stat").readText().substringAfterLast(") ").split(' ')[19])
                .put("actual_fast_property", System.getProperty("kotlinx.coroutines.fast.service.loader"))
                .put("actual_debug_property", System.getProperty("kotlinx.coroutines.debug"))
                .put("actual_recovery_property", System.getProperty("kotlinx.coroutines.stacktrace.recovery"))
                .put("real_application_on_create_completed", applicationCreated)
                .put("scope", "Probe adds no Rime deployment or database operations; real Application initialization still runs")
            val checks = CoroutinePackagingProbe.verify(this, evidence)
            result.putInt("coroutine_packaging_checks", checks)
            check(evidence.getString("resource_layout") == "relocated") { "Dedicated SPI test requires the relocation helper and relocated descriptors" }

            val debug = Class.forName("kotlinx.coroutines.DebugKt", true, loader)
            fun flag(fieldName: String, getter: String): Boolean {
                val field = debug.getDeclaredField(fieldName).apply { isAccessible = true }
                check(Modifier.isStatic(field.modifiers) && field.type == Boolean::class.javaPrimitiveType)
                val stored = field.getBoolean(null)
                check(debug.getDeclaredMethod(getter).invoke(null) == stored) { "$getter disagrees with its backing field" }
                evidence.put(fieldName, stored)
                return stored
            }
            check(flag("DEBUG", "getDEBUG")) { "Coroutine debug support did not honor debug=on" }
            check(flag("RECOVER_STACK_TRACES", "getRECOVER_STACK_TRACES")) { "Stack recovery did not honor recovery=true" }
            val assertionStatus = Class.forName("kotlinx.coroutines.CoroutineId", false, loader).desiredAssertionStatus()
            check(flag("ASSERTIONS_ENABLED", "getASSERTIONS_ENABLED") == assertionStatus) {
                "Coroutine assertion flag disagrees with the actual platform class assertion status"
            }
            evidence.put("assertions_requested", true).put("assertions_platform_enabled", assertionStatus)
                .put("assertions_positive_control_available", assertionStatus)
            if (!assertionStatus) {
                evidence.put("assertions_limitation", "Platform did not enable CoroutineId assertions; equality alone cannot prove a true assertion path. Static bytecode review remains required.")
            }
            val mainLoader = Class.forName("kotlinx.coroutines.internal.MainDispatcherLoader", true, loader)
            check(mainLoader.getDeclaredField("dispatcher").get(null) === Dispatchers.Main) {
                "Test and target did not observe the same initialized Main dispatcher"
            }
            val handlerClass = Class.forName("kotlinx.coroutines.internal.CoroutineExceptionHandlerImplKt", true, loader)
            val field = handlerClass.getDeclaredField("platformExceptionHandlers").apply { isAccessible = true }
            val actual = field.get(null) as? Collection<*> ?: error("Actual platformExceptionHandlers is not a collection")
            check(handlerClass.getDeclaredMethod("getPlatformExceptionHandlers").invoke(null) === actual)
            val names = actual.map { checkNotNull(it).javaClass.name }
            evidence.put("actual_platform_exception_handlers", org.json.JSONArray(names))
            check(names == listOf("kotlinx.coroutines.android.AndroidExceptionPreHandler")) {
                "Unexpected actual platformExceptionHandlers: $names"
            }
            evidence.put("android_exception_prehandler_execution_tested", false)
                .put("api26_27_limitation", "Provider discovery is checked; no uncaught exception or Android 8.0/8.1 pre-handler execution is induced")
            result.putInt("coroutine_packaging_checks", checks + 5)
            passed = true
        } catch (error: Throwable) {
            evidence.put("error", android.util.Log.getStackTraceString(error))
        } finally {
            evidence.put("passed", passed)
            result.putBoolean("passed", passed)
            result.putString("coroutine_packaging_report", evidence.toString())
            result.putString("stream", "Coroutine packaging ${if (expectedFastLoader) "fast" else "slow"}: $passed\n${evidence.toString(2)}\n")
            // adb may still exit zero for instrumentation failure; the host must check both
            // INSTRUMENTATION_CODE and this explicit passed value.
            finish(if (passed) Activity.RESULT_OK else 1, result)
        }
    }
}
