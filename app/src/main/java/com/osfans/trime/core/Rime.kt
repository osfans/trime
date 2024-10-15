// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.opencc.OpenCCDictManager
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.util.appContext
import com.osfans.trime.util.isAsciiPrintable
import com.osfans.trime.util.isStorageAvailable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * Rime JNI and instance methods
 *
 * @see [librime](https://github.com/rime/librime)
 */
class Rime :
    RimeApi,
    RimeLifecycleOwner {
    private val lifecycleImpl = RimeLifecycleImpl()
    override val lifecycle get() = lifecycleImpl

    override val callbackFlow = callbackFlow_.asSharedFlow()

    override val stateFlow get() = lifecycle.currentStateFlow

    override val isReady: Boolean
        get() = lifecycle.currentStateFlow.value == RimeLifecycle.State.READY

    override var schemaItemCached = SchemaItem(".default")
        private set

    override var inputStatusCached = InputStatus()
        private set

    private val dispatcher =
        RimeDispatcher(
            object : RimeDispatcher.RimeLooper {
                override fun nativeStartup(fullCheck: Boolean) {
                    DataManager.sync()

                    val sharedDataDir = DataManager.sharedDataDir.absolutePath
                    val userDataDir = DataManager.userDataDir.absolutePath
                    Timber.i("Starting up Rime APIs ...")
                    startupRime(sharedDataDir, userDataDir, fullCheck)

                    lifecycleImpl.emitState(RimeLifecycle.State.READY)

                    ipcResponseCallback()
                    SchemaManager.init(getCurrentRimeSchema())
                }

                override fun nativeFinalize() {
                    exitRime()
                }
            },
        )

    private suspend inline fun <T> withRimeContext(crossinline block: suspend () -> T): T =
        withContext(dispatcher) {
            block()
        }

    override suspend fun isEmpty(): Boolean =
        withRimeContext {
            getCurrentRimeSchema() == ".default" // 無方案
        }

    override suspend fun processKey(
        value: Int,
        modifiers: UInt,
    ): Boolean =
        withRimeContext {
            processRimeKey(value, modifiers.toInt()).also {
                if (it) {
                    ipcResponseCallback()
                } else {
                    keyEventCallback(KeyValue(value), KeyModifiers(modifiers))
                }
            }
        }

    override suspend fun processKey(
        value: KeyValue,
        modifiers: KeyModifiers,
    ): Boolean =
        withRimeContext {
            processRimeKey(value.value, modifiers.toInt()).also {
                if (it) {
                    ipcResponseCallback()
                } else {
                    keyEventCallback(value, modifiers)
                }
            }
        }

    override suspend fun selectCandidate(idx: Int): Boolean =
        withRimeContext {
            selectRimeCandidate(idx).also { if (it) ipcResponseCallback() }
        }

    override suspend fun forgetCandidate(idx: Int): Boolean =
        withRimeContext {
            forgetRimeCandidate(idx).also { if (it) ipcResponseCallback() }
        }

    override suspend fun availableSchemata(): Array<SchemaItem> = withRimeContext { getAvailableRimeSchemaList() }

    override suspend fun enabledSchemata(): Array<SchemaItem> = withRimeContext { getSelectedRimeSchemaList() }

    override suspend fun setEnabledSchemata(schemaIds: Array<String>) = withRimeContext { selectRimeSchemas(schemaIds) }

    override suspend fun selectedSchemata(): Array<SchemaItem> = withRimeContext { getRimeSchemaList() }

    override suspend fun selectedSchemaId(): String = withRimeContext { getCurrentRimeSchema() }

    override suspend fun selectSchema(schemaId: String) = withRimeContext { selectRimeSchema(schemaId) }

    override suspend fun currentSchema() =
        withRimeContext {
            val schema = getRimeStatus()?.let { SchemaItem(it.schemaId, it.schemaName) }
            schema ?: schemaItemCached
        }

    override suspend fun commitComposition(): Boolean = withRimeContext { commitRimeComposition().also { if (it) ipcResponseCallback() } }

    override suspend fun clearComposition() =
        withRimeContext {
            clearRimeComposition()
            ipcResponseCallback()
        }

    override suspend fun setRuntimeOption(
        option: String,
        value: Boolean,
    ): Unit =
        withRimeContext {
            setRimeOption(option, value)
        }

    override suspend fun getRuntimeOption(option: String): Boolean =
        withRimeContext {
            getRimeOption(option)
        }

    override suspend fun getCandidates(
        startIndex: Int,
        limit: Int,
    ): Array<CandidateItem> =
        withRimeContext {
            getRimeCandidates(startIndex, limit) ?: emptyArray()
        }

    private fun handleRimeCallback(it: RimeCallback) {
        when (it) {
            is RimeNotification.SchemaNotification -> {
                schemaItemCached = it.value
                SchemaManager.init(it.value.id)
            }
            is RimeNotification.OptionNotification -> {
                getRimeStatus()?.let {
                    inputStatusCached = InputStatus.fromStatus(it)
                    inputStatus = it // for compatibility
                }
                SchemaManager.updateSwitchOptions()
            }
            is RimeNotification.DeployNotification -> {
                when (it.value) {
                    "start" -> OpenCCDictManager.buildOpenCCDict()
                }
            }
            is RimeEvent.IpcResponseEvent ->
                it.data.let event@{ data ->
                    data.status?.let {
                        val status = InputStatus.fromStatus(it)
                        inputStatusCached = status
                        inputStatus = it // for compatibility

                        val item = SchemaItem.fromStatus(it)
                        if (item != schemaItemCached) {
                            schemaItemCached = item
                        }
                    }
                    data.context?.let { inputContext = it } // for compatibility
                }
            else -> {}
        }
    }

    fun startup(fullCheck: Boolean) {
        if (lifecycle.currentStateFlow.value != RimeLifecycle.State.STOPPED) {
            Timber.w("Skip starting rime: not at stopped state!")
            return
        }
        if (appContext.isStorageAvailable()) {
            registerRimeCallbackHandler(::handleRimeCallback)
            lifecycleImpl.emitState(RimeLifecycle.State.STARTING)
            dispatcher.start(fullCheck)
        }
    }

    fun finalize() {
        if (lifecycle.currentStateFlow.value != RimeLifecycle.State.READY) {
            Timber.w("Skip stopping rime: not at ready state!")
            return
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPING)
        Timber.i("Rime finalize()")
        dispatcher.stop().let {
            if (it.isNotEmpty()) {
                Timber.w("${it.size} job(s) didn't get a chance to run!")
            }
        }
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPED)
        unregisterRimeCallbackHandler(::handleRimeCallback)
    }

    companion object {
        private var inputContext: RimeProto.Context? = null
        private var inputStatus: RimeProto.Status? = null
        private val callbackFlow_ =
            MutableSharedFlow<RimeCallback>(
                extraBufferCapacity = 15,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        private val callbackHandlers = ArrayList<(RimeCallback) -> Unit>()

        init {
            System.loadLibrary("rime_jni")
        }

        /*
  Android SDK包含了如下6个修饰键的状态，其中function键会被trime消费掉，因此只处理5个键
  Android和librime对按键命名并不一致。读取可能有误。librime按键命名见如下链接，
  https://github.com/rime/librime/blob/master/src/rime/key_table.cc
         */
        @JvmField
        val META_SHIFT_ON = getRimeModifierByName("Shift")

        @JvmField
        val META_CTRL_ON = getRimeModifierByName("Control")

        @JvmField
        val META_ALT_ON = getRimeModifierByName("Alt")

        @JvmField
        val META_SYM_ON = getRimeModifierByName("Super")

        @JvmField
        val META_META_ON = getRimeModifierByName("Meta")

        @JvmField
        val META_RELEASE_ON = getRimeModifierByName("Release")

        @JvmStatic
        val isComposing get() = inputStatus?.isComposing ?: false

        @JvmStatic
        val isAsciiMode get() = inputStatus?.isAsciiMode ?: true

        @JvmStatic
        val currentSchemaName get() = inputStatus?.schemaName ?: ""

        @JvmStatic
        fun hasMenu(): Boolean = !inputContext?.menu?.candidates.isNullOrEmpty()

        @JvmStatic
        fun hasLeft(): Boolean = hasMenu() && inputContext?.menu?.pageNumber != 0

        @JvmStatic
        fun showAsciiPunch(): Boolean = inputStatus?.isAsciiPunch == true || inputStatus?.isAsciiMode == true

        @JvmStatic
        val composingText: String
            get() = inputContext?.composition?.commitTextPreview ?: ""

        @JvmStatic
        fun isVoidKeycode(keycode: Int): Boolean {
            val voidSymbol = 0xffffff
            return keycode <= 0 || keycode == voidSymbol
        }

        // KeyProcess 调用JNI方法发送keycode和mask
        @JvmStatic
        fun processKey(
            keycode: Int,
            mask: Int,
        ): Boolean {
            if (isVoidKeycode(keycode)) return false
            Timber.d("processKey: keyCode=$keycode, mask=$mask")
            return processRimeKey(keycode, mask).also {
                Timber.d("processKey ${if (it) "success" else "failed"}")
                if (it) {
                    ipcResponseCallback()
                } else {
                    keyEventCallback(KeyValue(keycode), KeyModifiers.of(mask))
                }
            }
        }

        @JvmStatic
        fun simulateKeySequence(sequence: CharSequence): Boolean {
            if (!sequence.first().isAsciiPrintable()) return false
            Timber.d("simulateKeySequence: $sequence")
            return simulateRimeKeySequence(
                sequence.toString().replace("{}", "{braceleft}{braceright}"),
            ).also {
                Timber.d("simulateKeySequence ${if (it) "success" else "failed"}")
                if (it) ipcResponseCallback()
            }
        }

        @JvmStatic
        fun setOption(
            option: String,
            value: Boolean,
        ) {
            measureTimeMillis {
                setRimeOption(option, value)
            }.also { Timber.d("Took $it ms to set $option to $value") }
        }

        @JvmStatic
        fun getOption(option: String): Boolean = getRimeOption(option)

        @JvmStatic
        fun setCaretPos(caretPos: Int) {
            setRimeCaretPos(caretPos)
            ipcResponseCallback()
        }

        // init
        @JvmStatic
        external fun startupRime(
            sharedDir: String,
            userDir: String,
            fullCheck: Boolean,
        )

        @JvmStatic
        external fun exitRime()

        @JvmStatic
        external fun deployRimeSchemaFile(schemaFile: String): Boolean

        @JvmStatic
        external fun deployRimeConfigFile(
            fileName: String,
            versionKey: String,
        ): Boolean

        @JvmStatic
        external fun syncRimeUserData(): Boolean

        // input
        @JvmStatic
        external fun processRimeKey(
            keycode: Int,
            mask: Int,
        ): Boolean

        @JvmStatic
        external fun commitRimeComposition(): Boolean

        @JvmStatic
        external fun clearRimeComposition()

        // output
        @JvmStatic
        external fun getRimeCommit(): RimeProto.Commit?

        @JvmStatic
        external fun getRimeContext(): RimeProto.Context?

        @JvmStatic
        external fun getRimeStatus(): RimeProto.Status?

        // runtime options
        @JvmStatic
        external fun setRimeOption(
            option: String,
            value: Boolean,
        )

        @JvmStatic
        external fun getRimeOption(option: String): Boolean

        @JvmStatic
        external fun getRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun getCurrentRimeSchema(): String

        @JvmStatic
        external fun selectRimeSchema(schemaId: String): Boolean

        @JvmStatic
        external fun getRimeConfigMap(
            configId: String,
            key: String,
        ): Map<String, Any>?

        @JvmStatic
        external fun setRimeCustomConfigInt(
            configId: String,
            keyValuePairs: Array<Pair<String?, Int?>?>,
        )

        // testing
        @JvmStatic
        external fun simulateRimeKeySequence(keySequence: String): Boolean

        @JvmStatic
        external fun getRimeRawInput(): String?

        @JvmStatic
        external fun getRimeCaretPos(): Int

        @JvmStatic
        external fun setRimeCaretPos(caretPos: Int)

        @JvmStatic
        external fun selectRimeCandidateOnCurrentPage(index: Int): Boolean

        @JvmStatic
        external fun deleteRimeCandidateOnCurrentPage(index: Int): Boolean

        @JvmStatic
        external fun selectRimeCandidate(index: Int): Boolean

        @JvmStatic
        external fun forgetRimeCandidate(index: Int): Boolean

        @JvmStatic
        external fun getLibrimeVersion(): String

        // module
        @JvmStatic
        external fun runRimeTask(taskName: String?): Boolean

        @JvmStatic
        external fun getRimeSharedDataDir(): String?

        @JvmStatic
        external fun getRimeUserDataDir(): String?

        @JvmStatic
        external fun getRimeSyncDir(): String?

        @JvmStatic
        external fun getRimeUserId(): String?

        // key_table
        @JvmStatic
        external fun getRimeModifierByName(name: String): Int

        @JvmStatic
        external fun getRimeKeycodeByName(name: String): Int

        @JvmStatic
        external fun getAvailableRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun getSelectedRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun selectRimeSchemas(schemaIds: Array<String>): Boolean

        @JvmStatic
        external fun getRimeStateLabel(
            optionName: String,
            state: Boolean,
        ): String?

        @JvmStatic
        external fun getRimeCandidates(
            startIndex: Int,
            limit: Int,
        ): Array<CandidateItem>?

        /** call from rime_jni */
        @JvmStatic
        fun handleRimeNotification(
            messageType: String,
            messageValue: String,
        ) {
            val notification = RimeNotification.create(messageType, messageValue)
            Timber.d("Handling Rime notification: $notification")
            callbackHandlers.forEach { it.invoke(notification) }
            callbackFlow_.tryEmit(notification)
        }

        private fun ipcResponseCallback() {
            handleRimeEvent(
                RimeEvent.EventType.IpcResponse,
                RimeEvent.IpcResponseEvent.Data(getRimeCommit(), getRimeContext(), getRimeStatus()),
            )
        }

        private fun keyEventCallback(
            value: KeyValue,
            modifiers: KeyModifiers,
        ) {
            handleRimeEvent(RimeEvent.EventType.Key, RimeEvent.KeyEvent.Data(value, modifiers))
        }

        private fun <T> handleRimeEvent(
            type: RimeEvent.EventType,
            data: T,
        ) {
            val event = RimeEvent.create(type, data)
            Timber.d("Handling $event")
            callbackHandlers.forEach { it.invoke(event) }
            callbackFlow_.tryEmit(event)
        }

        private fun registerRimeCallbackHandler(handler: (RimeCallback) -> Unit) {
            if (callbackHandlers.contains(handler)) return
            callbackHandlers.add(handler)
        }

        private fun unregisterRimeCallbackHandler(handler: (RimeCallback) -> Unit) {
            callbackHandlers.remove(handler)
        }
    }
}
