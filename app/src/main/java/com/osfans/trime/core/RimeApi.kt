/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.data.prefs.InputSetupSelection
import com.osfans.trime.data.prefs.InputSetupState
import kotlinx.coroutines.flow.SharedFlow

interface RimeApi {
    val messageFlow: SharedFlow<RimeMessage<*>>

    val isReady: Boolean

    val usingExistingResources: Boolean

    val schemaCached: RimeSchema

    val statusCached: StatusProto

    val compositionCached: CompositionProto

    val hasMenu: Boolean

    val paging: Boolean

    val t9Cached: T9StateProto

    val candidateSnapshotCached: CandidateSnapshot

    suspend fun refreshT9Options()

    /** Apply the current user preference on the confined native context. */
    suspend fun refreshPredictionOptions()

    /** End next-word context without discarding ordinary unfinished input. */
    suspend fun clearPredictionContext()

    suspend fun t9Action(
        revision: Int,
        action: T9Action,
        start: Int = 0,
        end: Int = 0,
        spelling: String = "",
    ): Boolean

    suspend fun isEmpty(): Boolean

    suspend fun deploy(skipImport: Boolean = false)

    /** Validate and activate a picked folder, retaining the previous working configuration on failure. */
    suspend fun importConfiguration(treeUri: String): UserDataManager.SyncStats

    /** Import only the selected theme; a failed decode restores its source, caches and previous selection. */
    suspend fun importThemeConfiguration(configId: String): UserDataManager.SyncStats

    suspend fun inspectInputSetup(): InputSetupState

    suspend fun configureInput(selection: InputSetupSelection): InputSetupState

    suspend fun updateConfig()

    suspend fun deployConfigFile(fileName: String, versionKey: String): Boolean

    suspend fun syncUserData(): Boolean

    suspend fun processKey(
        value: Int,
        modifiers: UInt = 0u,
        isVirtual: Boolean = true,
    ): Boolean

    suspend fun processKey(
        value: KeyValue,
        modifiers: KeyModifiers,
        isVirtual: Boolean = true,
    ): Boolean

    suspend fun simulateKeySequence(
        sequence: String,
    ): Boolean

    suspend fun selectCandidate(idx: Int, global: Boolean, expectedSnapshot: CandidateSnapshot? = null): Boolean

    suspend fun deleteCandidate(idx: Int, global: Boolean, expectedSnapshot: CandidateSnapshot? = null): Boolean

    /** Pin only this schema/input pair; never write learned frequencies. */
    suspend fun pinCandidate(idx: Int, global: Boolean, expectedSnapshot: CandidateSnapshot): Boolean

    /** Remove explicit pins for the current input, preserving ordinary Rime learning. */
    suspend fun resetCandidateRanking(expectedSnapshot: CandidateSnapshot): Boolean

    /** Execute dictionary file operations after closing native database handles. */
    suspend fun <T> maintainUserDictionaries(block: () -> T): T

    suspend fun changeCandidatePage(backward: Boolean, expectedSnapshot: CandidateSnapshot? = null): Boolean

    /** Byte offset in the displayed UTF-8 preedit, not the private engine input. */
    suspend fun moveCursorPos(position: Int, expectedPreedit: String? = null)

    suspend fun availableSchemata(): Array<SchemaItem>

    suspend fun enabledSchemata(): Array<SchemaItem>

    suspend fun setEnabledSchemata(schemaIds: Array<String>): Boolean

    suspend fun selectedSchemata(): Array<SchemaItem>

    suspend fun selectedSchemaId(): String

    suspend fun selectSchema(schemaId: String): Boolean

    suspend fun currentSchema(): RimeSchema

    suspend fun commitComposition(): Boolean

    /** Finish pending input verbatim and append a literal digit, never select a numbered candidate. */
    suspend fun commitT9Digit(digit: Char): Boolean

    suspend fun clearComposition()

    suspend fun getRawInput(): String

    suspend fun setRuntimeOption(
        option: String,
        value: Boolean,
    )

    suspend fun getRuntimeOption(option: String): Boolean

    /** Non-blocking snapshot for drawing labels; unknown options are false. */
    fun getRuntimeOptionCached(option: String): Boolean

    suspend fun setNullInputType(value: Boolean)

    /** Discard the previous editor's transient state before binding any new input. */
    suspend fun beginEditor(
        token: Long,
        nullInputType: Boolean,
        privacyMode: Boolean,
    )

    /** Clear state owned by [token] when that editor is no longer active. */
    suspend fun finishEditor(token: Long)

    /**
     * Clear state only when the native editor belongs to one of [tokens].
     * Ownership matching and cleanup execute as one Rime dispatcher operation.
     */
    suspend fun finishOwnedEditors(tokens: LongArray)

    suspend fun getCandidates(
        startIndex: Int,
        limit: Int,
        expectedSnapshot: CandidateSnapshot? = null,
    ): Array<CandidateProto>

    suspend fun setCandidatePagingMode(enabled: Boolean)
}
