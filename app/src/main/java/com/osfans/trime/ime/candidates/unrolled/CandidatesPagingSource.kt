// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.daemon.RimeSession
import timber.log.Timber

class CandidatesPagingSource(
    val rime: RimeSession,
    val offset: Int,
) : PagingSource<Int, CandidateItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CandidateItem> {
        // use candidate index for key, null means load from beginning (including offset)
        val startIndex = params.key ?: offset
        val pageSize = params.loadSize
        Timber.d("getCandidates(offset=$startIndex, limit=$pageSize)")
        val candidates =
            rime.runOnReady {
                getCandidates(startIndex, pageSize)
            }
        val prevKey = if (startIndex >= pageSize) startIndex - pageSize else null
        val nextKey = if (candidates.size < pageSize) null else startIndex + pageSize
        return LoadResult.Page(candidates.toList(), prevKey, nextKey)
    }

    // always reload from beginning
    override fun getRefreshKey(state: PagingState<Int, CandidateItem>) = null
}
