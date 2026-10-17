// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildTimestampTest {
    @Test
    fun `shell epoch seconds preserve the actual build date`() {
        val normalized = normalizeBuildTimestamp("1788912298")
        assertEquals("1788912298000", normalized)
        assertEquals(
            Instant.ofEpochSecond(1788912298),
            Instant.ofEpochMilli(normalized.toLong()),
        )
    }

    @Test
    fun `epoch milliseconds keep subsecond precision`() {
        assertEquals("1788912298123", normalizeBuildTimestamp("1788912298123"))
    }

    @Test
    fun `surrounding property whitespace is ignored`() {
        assertEquals("1788912298000", normalizeBuildTimestamp(" 1788912298\n"))
    }

    @Test
    fun `invalid values fail before generating BuildConfig`() {
        listOf("", "not-a-timestamp", "1788912298.5", "9223372036854775808").forEach {
            assertFailsWith<IllegalArgumentException>(it) { normalizeBuildTimestamp(it) }
        }
    }

    @Test
    fun `ambiguous or implausible units are rejected`() {
        listOf("0", "-1788912298", "178891229", "17889122980", "178891229800", "17889122980000").forEach {
            assertFailsWith<IllegalArgumentException>(it) { normalizeBuildTimestamp(it) }
        }
    }
}
