/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

fun subprocess(vararg commands: String): Process = Runtime.getRuntime().exec(commands)

fun Process.asFlow(): Flow<String> =
    inputStream
        .bufferedReader()
        .lineSequence()
        .asFlow()
        .flowOn(Dispatchers.IO)
        .cancellable()

suspend fun Process.readText() =
    withContext(Dispatchers.IO) {
        inputStream.bufferedReader().readText()
    }

suspend fun Process.readLines() =
    withContext(Dispatchers.IO) {
        inputStream.bufferedReader().readLines()
    }
