/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

data class CommitProto(
    val text: String?,
)

data class CandidateProto(
    val text: String,
    val comment: String,
    val label: String,
)

data class CompositionProto(
    /**
     * Actually we can directly use [String.length] on [preedit], but
     * we add it here for the sake of completeness as it is semantically correct
     */
    val length: Int = 0,
    val cursorPos: Int = 0,
    val selStart: Int = 0,
    val selEnd: Int = 0,
    val preedit: String? = null,
    val commitTextPreview: String? = null,
) {
    internal constructor(text: String) : this(
        text.length,
        text.length,
        text.length,
        text.length,
        text,
    )
}

data class ContextProto(
    val composition: CompositionProto = CompositionProto(),
    val input: String = "",
    val caretPos: Int = 0,
)

data class StatusProto(
    val schemaId: String = "",
    val schemaName: String = "",
    val isDisabled: Boolean = true,
    val isComposing: Boolean = false,
    val isAsciiMode: Boolean = true,
    val isFullShape: Boolean = false,
    val isSimplified: Boolean = false,
    val isTraditional: Boolean = false,
    val isAsciiPunct: Boolean = true,
)

/**
 * The candidate set carried by a [RimeResponse]: a full paged menu when paging
 * mode is on, or a bulk candidate list otherwise.
 */
sealed interface Candidates {
    /** Candidates of the current page */
    val candidates: Array<CandidateProto>

    /** Index of the highlighted candidate */
    val highlighted: Int

    /** Paging mode: the full page menu */
    data class Paged(
        /** Whether there is a previous page to turn to */
        val hasPrevPage: Boolean = false,
        /** Whether there is a next page to turn to */
        val hasNextPage: Boolean = false,
        override val highlighted: Int = 0,
        override val candidates: Array<CandidateProto> = arrayOf(),
    ) : Candidates {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Paged

            if (hasPrevPage != other.hasPrevPage) return false
            if (hasNextPage != other.hasNextPage) return false
            if (highlighted != other.highlighted) return false
            if (!candidates.contentEquals(other.candidates)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = hasPrevPage.hashCode()
            result = 31 * result + hasNextPage.hashCode()
            result = 31 * result + highlighted
            result = 31 * result + candidates.contentHashCode()
            return result
        }
    }

    /** Bulk list: [total] is the candidate count, or -1 if unknown */
    data class Bulk(
        val total: Int = -1,
        override val highlighted: Int = 0,
        override val candidates: Array<CandidateProto> = arrayOf(),
    ) : Candidates {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Bulk

            if (total != other.total) return false
            if (highlighted != other.highlighted) return false
            if (!candidates.contentEquals(other.candidates)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = total
            result = 31 * result + highlighted
            result = 31 * result + candidates.contentHashCode()
            return result
        }
    }
}

/**
 * A combined snapshot of commit, composition, candidates and status,
 * fetched in a single JNI crossing to avoid repeated JVM/C++ boundary switches.
 */
data class RimeResponse(
    val commit: CommitProto,
    val composition: CompositionProto,
    val candidates: Candidates,
    val status: StatusProto,
)
