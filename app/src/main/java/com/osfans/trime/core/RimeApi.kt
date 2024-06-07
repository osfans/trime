// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

import kotlinx.coroutines.flow.SharedFlow

interface RimeApi {
    val notificationFlow: SharedFlow<RimeNotification<*>>

    val stateFlow: SharedFlow<RimeLifecycle.State>

    val isReady: Boolean

    val isStarting: Boolean

    suspend fun isEmpty(): Boolean

    suspend fun availableSchemata(): Array<SchemaListItem>

    suspend fun enabledSchemata(): Array<SchemaListItem>

    suspend fun setEnabledSchemata(schemaIds: Array<String>): Boolean

    suspend fun selectedSchemata(): Array<SchemaListItem>

    suspend fun selectedSchemaId(): String

    suspend fun selectSchema(schemaId: String): Boolean
}
