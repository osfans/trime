// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

/** Normalize contemporary Unix timestamps for the millisecond-based Android date helpers. */
internal fun normalizeBuildTimestamp(raw: String): String {
    val timestamp = raw.trim().toLongOrNull()
    require(
        timestamp != null &&
            (
                timestamp in 1_000_000_000L..9_999_999_999L ||
                    timestamp in 1_000_000_000_000L..9_999_999_999_999L
                ),
    ) {
        "BUILD_TIMESTAMP/buildTimestamp must be epoch seconds (10 digits) " +
            "or epoch milliseconds (13 digits)"
    }
    val millis = if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    return millis.toString()
}
