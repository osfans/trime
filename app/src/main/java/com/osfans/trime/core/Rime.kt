/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.core

import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import com.osfans.trime.data.opencc.OpenCCDictManager
import com.osfans.trime.data.schema.SchemaManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * Rime JNI and instance methods
 *
 * @see [librime](https://github.com/rime/librime)
 */
class Rime(fullCheck: Boolean) {
    val notificationFlow = notificationFlow_.asSharedFlow()

    init {
        startup(fullCheck)
    }

    companion object {
        private var instance: Rime? = null

        @JvmStatic
        fun getInstance(fullCheck: Boolean = false): Rime {
            if (instance == null) instance = Rime(fullCheck)
            return instance!!
        }

        private var mContext: RimeContext? = null
        private var mStatus: RimeStatus? = null
        private var isHandlingRimeNotification = false
        private val notificationFlow_ =
            MutableSharedFlow<RimeNotification>(
                extraBufferCapacity = 15,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        init {
            System.loadLibrary("rime_jni")
        }

        private fun startup(fullCheck: Boolean) {
            isHandlingRimeNotification = false

            DataManager.sync()

            val sharedDataDir = AppPrefs.defaultInstance().profile.sharedDataDir
            val userDataDir = AppPrefs.defaultInstance().profile.userDataDir

            Timber.i("Starting up Rime APIs ...")
            startupRime(sharedDataDir, userDataDir, fullCheck)

            Timber.i("Initializing schema stuffs after starting up ...")
            SchemaManager.init(getCurrentRimeSchema())
            updateStatus()
        }

        fun destroy() {
            exitRime()
            instance = null
        }

        fun deploy() {
            destroy()
            getInstance(true)
            OpenCCDictManager.buildOpenCCDict()
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
                mContext = getRimeContext() ?: RimeContext()
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
            return isComposing && mContext?.menu?.numCandidates != 0
        }

        @JvmStatic
        fun hasLeft(): Boolean {
            return hasMenu() && mContext?.menu?.pageNo != 0
        }

        @JvmStatic
        fun hasRight(): Boolean {
            return hasMenu() && mContext?.menu?.isLastPage == false
        }

        @JvmStatic
        fun showAsciiPunch(): Boolean {
            return mStatus?.isAsciiPunch == true || mStatus?.isAsciiMode == true
        }

        @JvmStatic
        val composition: RimeComposition?
            get() = mContext?.composition

        @JvmStatic
        val compositionText: String
            get() = composition?.preedit ?: ""

        @JvmStatic
        val composingText: String
            get() = mContext?.commitTextPreview ?: ""

        @JvmStatic
        val selectLabels: Array<String>
            get() = mContext?.selectLabels ?: arrayOf()

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
                    mContext?.candidates ?: arrayOf()
                }
            }

        val candidatesWithoutSwitch: Array<CandidateListItem>
            get() = if (isComposing) mContext?.candidates ?: arrayOf() else arrayOf()

        @JvmStatic
        val candHighlightIndex: Int
            get() = if (isComposing) mContext?.menu?.highlightedCandidateIndex ?: -1 else -1

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
            if (isHandlingRimeNotification) return
            setRimeOption(option, value)
        }

        @JvmStatic
        fun getOption(option: String): Boolean {
            return getRimeOption(option)
        }

        fun toggleOption(option: String) {
            setOption(option, !getOption(option))
        }

        @JvmStatic
        val isEmpty: Boolean
            get() = getCurrentRimeSchema() == ".default" // 無方案

        fun selectSchema(schemaId: String): Boolean {
            Timber.d("selectSchema: schemaId=$schemaId")
            return selectRimeSchema(schemaId).also {
                updateContext()
            }
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
            isHandlingRimeNotification = true
            val notification = RimeNotification.create(messageType, messageValue)
            Timber.d("Handling Rime notification: $notification")
            notificationFlow_.tryEmit(notification)
            isHandlingRimeNotification = false
        }
    }
}
