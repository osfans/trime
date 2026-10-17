/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates.unrolled

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.StaleCandidateSnapshotException
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.ime.candidates.CandidateRow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.lang.reflect.Proxy

class CandidatesPagingSourceTest :
    StringSpec({
        "a known total keeps the final remaining candidate reachable" {
            nextCandidatePage(startIndex = 16, pageSize = 48, count = 48, total = 65) shouldBe 64
            nextCandidatePage(startIndex = 64, pageSize = 48, count = 1, total = 65) shouldBe null
        }

        "unknown totals stop on short or empty pages" {
            nextCandidatePage(16, 48, 48, -1) shouldBe 64
            nextCandidatePage(64, 48, 47, -1) shouldBe null
            nextCandidatePage(16, 48, 0, 200) shouldBe null
        }

        "a load carries its snapshot and caps the JNI batch" {
            val snapshot = CandidateSnapshot(7, 19, 23)
            var requestedStart = -1
            var requestedLimit = -1
            var requestedSnapshot: CandidateSnapshot? = null
            val api = candidateApi { start, limit, expected ->
                requestedStart = start
                requestedLimit = limit
                requestedSnapshot = expected
                arrayOf(CandidateProto("candidate", "", ""))
            }
            val source = CandidatesPagingSource(candidateSession(api), total = -1, offset = 16, snapshot = snapshot)
            val result = source.load(PagingSource.LoadParams.Refresh(null, 144, false)) as PagingSource.LoadResult.Page<Int, CandidateRow>
            requestedStart shouldBe 16
            requestedLimit shouldBe 48
            requestedSnapshot shouldBe snapshot
            result.data.single().index shouldBe 16
            result.data.single().snapshot shouldBe snapshot
        }

        "a stale engine snapshot invalidates the asynchronous page" {
            val api = candidateApi { _, _, _ -> throw StaleCandidateSnapshotException() }
            val source = CandidatesPagingSource(candidateSession(api), -1, 16, CandidateSnapshot(7, 19, 23))
            (source.load(PagingSource.LoadParams.Refresh(null, 48, false)) is PagingSource.LoadResult.Invalid<*, *>) shouldBe true
        }

        "a restored candidate starts near its global index with bounded nonoverlapping pages" {
            val snapshot = CandidateSnapshot(7, 19, 23)
            var requestedStart = -1
            var requestedLimit = -1
            val api = candidateApi { start, limit, expected ->
                requestedStart = start
                requestedLimit = limit
                expected shouldBe snapshot
                Array(limit) { CandidateProto("candidate${start + it}", "", "") }
            }
            val source = CandidatesPagingSource(candidateSession(api), 500, 16, snapshot, initialIndex = 196)
            val result = source.load(PagingSource.LoadParams.Refresh(null, 144, false)) as PagingSource.LoadResult.Page<Int, CandidateRow>
            requestedStart shouldBe 160
            requestedLimit shouldBe 48
            result.data.map { it.index } shouldBe (160 until 208).toList()
            result.prevKey shouldBe 112
            result.nextKey shouldBe 208
            val previous = source.load(PagingSource.LoadParams.Prepend(112, 48, false)) as PagingSource.LoadResult.Page<Int, CandidateRow>
            previous.data.last().index shouldBe result.data.first().index - 1
            previous.data.map { it.index }.intersect(result.data.map { it.index }.toSet()) shouldBe emptySet()
            candidatePageStart(15, 16, 48) shouldBe 16
            candidatePageStart(196, 16, 48) shouldBe 160
        }

        "refresh uses the visible global candidate only from the same snapshot" {
            val snapshot = CandidateSnapshot(7, 19, 23)
            val api = candidateApi { _, _, _ -> emptyArray() }
            val source = CandidatesPagingSource(candidateSession(api), 500, 16, snapshot)
            fun state(origin: CandidateSnapshot) = PagingState(
                pages = listOf(
                    PagingSource.LoadResult.Page(
                        data = (160 until 208).map { CandidateRow(CandidateProto("candidate$it", "", ""), it, origin) },
                        prevKey = 112,
                        nextKey = 208,
                    ),
                ),
                anchorPosition = 36,
                config = PagingConfig(pageSize = 48, enablePlaceholders = false),
                leadingPlaceholderCount = 0,
            )
            source.getRefreshKey(state(snapshot)) shouldBe 160
            source.getRefreshKey(state(snapshot.copy(generation = 20))) shouldBe null
        }
    })

private fun candidateApi(load: (Int, Int, CandidateSnapshot?) -> Array<CandidateProto>): RimeApi = Proxy.newProxyInstance(RimeApi::class.java.classLoader, arrayOf(RimeApi::class.java)) { _, method, args ->
    check(method.name == "getCandidates") { "Unexpected API call: ${method.name}" }
    load(args!![0] as Int, args[1] as Int, args[2] as CandidateSnapshot?)
} as RimeApi

private fun candidateSession(api: RimeApi): RimeSession = object : RimeSession {
    override fun <T> run(block: suspend RimeApi.() -> T): T = error("Not used by paging")

    override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T = block(api)

    override fun runIfReady(block: suspend RimeApi.() -> Unit) = error("Not used by paging")

    override val lifecycleScope = CoroutineScope(Dispatchers.Unconfined)
}
