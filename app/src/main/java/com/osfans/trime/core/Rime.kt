// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.opencc.OpenCCDictManager
import com.osfans.trime.data.schema.SchemaManager
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
class Rime : RimeApi, RimeLifecycleOwner {
    private val lifecycleImpl = RimeLifecycleImpl()
    override val lifecycle get() = lifecycleImpl

    override val notificationFlow = notificationFlow_.asSharedFlow()
    override val stateFlow get() = lifecycle.currentStateFlow

    override val isReady: Boolean
        get() = lifecycle.currentStateFlow.value == RimeLifecycle.State.READY

    private val dispatcher =
        RimeDispatcher(
            object : RimeDispatcher.RimeLooper {
                override fun nativeStartup(fullCheck: Boolean) {
                    DataManager.dirFireChange()
                    DataManager.sync()

                    val sharedDataDir = AppPrefs.Profile.getAppShareDir()
                    val userDataDir = AppPrefs.Profile.getAppUserDir()
                    Timber.i("Starting up Rime APIs ...")
                    startupRime(sharedDataDir, userDataDir, fullCheck)

                    SchemaManager.init(getCurrentRimeSchema())
                    updateStatus()

                    OpenCCDictManager.buildOpenCCDict()
                    lifecycleImpl.emitState(RimeLifecycle.State.READY)
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

    override suspend fun availableSchemata(): Array<SchemaListItem> = withRimeContext { getAvailableRimeSchemaList() }

    override suspend fun enabledSchemata(): Array<SchemaListItem> = withRimeContext { getSelectedRimeSchemaList() }

    override suspend fun setEnabledSchemata(schemaIds: Array<String>) = withRimeContext { selectRimeSchemas(schemaIds) }

    override suspend fun selectedSchemata(): Array<SchemaListItem> = withRimeContext { getRimeSchemaList() }

    override suspend fun selectedSchemaId(): String = withRimeContext { getCurrentRimeSchema() }

    override suspend fun selectSchema(schemaId: String) = withRimeContext { selectRimeSchema(schemaId) }

    fun startup(fullCheck: Boolean) {
        if (lifecycle.currentStateFlow.value != RimeLifecycle.State.STOPPED) {
            Timber.w("Skip starting rime: not at stopped state!")
            return
        }
        if (AppPrefs.defaultInstance().profile.isUserDataDirChosen()) {
            lifecycleImpl.emitState(RimeLifecycle.State.STARTING)
            dispatcher.start(fullCheck)
        }
    }

    fun finalize() {
        if (lifecycle.currentStateFlow.value != RimeLifecycle.State.READY) {
            Timber.w("Skip stopping rime: not at ready state!")
            return
        }
        dispatcher.stop()
        lifecycleImpl.emitState(RimeLifecycle.State.STOPPED)
    }

    companion object {
        var inputContext: RimeContext? = null
        private var mStatus: RimeStatus? = null
        private val notificationFlow_ =
            MutableSharedFlow<RimeNotification<*>>(
                extraBufferCapacity = 15,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        init {
            System.loadLibrary("rime_jni")
        }

        fun updateStatus() {
            SchemaManager.updateSwitchOptions()
            measureTimeMillis {
                mStatus = getRimeStatus() ?: RimeStatus()
            }.also { Timber.d("Took $it ms to get status") }
        }

        fun updateContext() {
            Timber.d("Update Rime context ...")
            measureTimeMillis {
                inputContext = getRimeContext() ?: RimeContext()
            }.also { Timber.d("Took $it ms to get context") }
            updateStatus()
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
        val isComposing get() = mStatus?.isComposing ?: false

        @JvmStatic
        val isAsciiMode get() = mStatus?.isAsciiMode ?: true

        @JvmStatic
        val isAsciiPunch get() = mStatus?.isAsciiPunch ?: true

        @JvmStatic
        val currentSchemaName get() = mStatus?.schemaName ?: ""

        @JvmStatic
        fun hasMenu(): Boolean {
            return isComposing && inputContext?.menu?.numCandidates != 0
        }

        @JvmStatic
        fun hasLeft(): Boolean {
            return hasMenu() && inputContext?.menu?.pageNo != 0
        }

        @JvmStatic
        fun hasRight(): Boolean {
            return hasMenu() && inputContext?.menu?.isLastPage == false
        }

        @JvmStatic
        fun showAsciiPunch(): Boolean {
            return mStatus?.isAsciiPunch == true || mStatus?.isAsciiMode == true
        }

        @JvmStatic
        val composition: RimeComposition?
            get() = inputContext?.composition

        @JvmStatic
        val compositionText: String
            get() = composition?.preedit ?: ""

        @JvmStatic
        val composingText: String
            get() = inputContext?.commitTextPreview ?: ""

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
                updateContext()
            }
        }

        private fun isValidText(text: CharSequence?): Boolean {
            if (text.isNullOrEmpty()) return false
            val ch = text.toString().codePointAt(0)
            return ch in 0x20..0x7f
        }

        @JvmStatic
        fun simulateKeySequence(sequence: CharSequence): Boolean {
            if (!isValidText(sequence)) return false
            Timber.d("simulateKeySequence: $sequence")
            return simulateRimeKeySequence(
                sequence.toString().replace("{}", "{braceleft}{braceright}"),
            ).also {
                Timber.d("simulateKeySequence ${if (it) "success" else "failed"}")
                updateContext()
            }
        }

        @JvmStatic
        val candidatesOrStatusSwitches: Array<CandidateListItem>
            get() {
                val showSwitches = AppPrefs.defaultInstance().keyboard.switchesEnabled
                return if (!isComposing && showSwitches) {
                    SchemaManager.getStatusSwitches()
                } else {
                    inputContext?.candidates ?: arrayOf()
                }
            }

        val candidatesWithoutSwitch: Array<CandidateListItem>
            get() = if (isComposing) inputContext?.candidates ?: arrayOf() else arrayOf()

        @JvmStatic
        val candHighlightIndex: Int
            get() = if (isComposing) inputContext?.menu?.highlightedCandidateIndex ?: -1 else -1

        fun commitComposition(): Boolean {
            return commitRimeComposition().also {
                updateContext()
            }
        }

        fun clearComposition() {
            clearRimeComposition()
            updateContext()
        }

        fun selectCandidate(index: Int): Boolean {
            return selectRimeCandidateOnCurrentPage(index).also {
                updateContext()
            }
        }

        fun deleteCandidate(index: Int): Boolean {
            return deleteRimeCandidateOnCurrentPage(index).also {
                updateContext()
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
        fun getOption(option: String): Boolean {
            return getRimeOption(option)
        }

        fun toggleOption(option: String) {
            setOption(option, !getOption(option))
        }

        @JvmStatic
        fun setCaretPos(caretPos: Int) {
            setRimeCaretPos(caretPos)
            updateContext()
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
        external fun getRimeCommit(): RimeCommit?

        @JvmStatic
        external fun getRimeContext(): RimeContext?

        @JvmStatic
        external fun getRimeStatus(): RimeStatus?

        // runtime options
        @JvmStatic
        external fun setRimeOption(
            option: String,
            value: Boolean,
        )

        @JvmStatic
        external fun getRimeOption(option: String): Boolean

        @JvmStatic
        external fun getRimeSchemaList(): Array<SchemaListItem>

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
        external fun getAvailableRimeSchemaList(): Array<SchemaListItem>

        @JvmStatic
        external fun getSelectedRimeSchemaList(): Array<SchemaListItem>

        @JvmStatic
        external fun selectRimeSchemas(schemaIds: Array<String>): Boolean

        @JvmStatic
        external fun getRimeStateLabel(
            optionName: String,
            state: Boolean,
        ): String?

        /** call from rime_jni */
        @JvmStatic
        fun handleRimeNotification(
            messageType: String,
            messageValue: String,
        ) {
            val notification = RimeNotification.create(messageType, messageValue)
            Timber.d("Handling Rime notification: $notification")
            notificationFlow_.tryEmit(notification)
        }
    }
}
