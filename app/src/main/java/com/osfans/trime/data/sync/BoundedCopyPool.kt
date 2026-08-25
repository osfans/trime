// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object BoundedCopyPool {
    suspend fun <T, R> mapParallel(
        items: List<T>,
        parallelism: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        transform: suspend (T) -> R,
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val results = arrayOfNulls<Any?>(items.size)
        coroutineScope {
            val channel = Channel<Pair<Int, T>>(capacity = parallelism * 2)
            repeat(parallelism.coerceAtMost(items.size)) {
                launch(dispatcher) {
                    for ((index, item) in channel) {
                        results[index] = transform(item)
                    }
                }
            }
            items.forEachIndexed { index, item ->
                channel.send(index to item)
            }
            channel.close()
        }
        @Suppress("UNCHECKED_CAST")
        return results.map { checkNotNull(it) as R }
    }
}
