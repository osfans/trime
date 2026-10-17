/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

data class CommitProto(
    val text: String?,
)

/** Offsets refer to the original input, not the private engine spelling. */
data class T9SpanProto(
    val start: Int,
    val end: Int,
    val spelling: String,
    val completion: Boolean,
    val locked: Boolean,
    /** Independent source bits, zero for the original exact/completion choices. */
    val sources: Int = 0,
)

class T9StateProto(
    val enabled: Boolean = false,
    val revision: Int = 0,
    val input: String = "",
    val focus: Int = 0,
    val canUndo: Boolean = false,
    val segments: Array<T9SpanProto> = emptyArray(),
    val choices: Array<T9SpanProto> = emptyArray(),
)

enum class T9Action {
    Focus,
    Lock,
    Unlock,
    Undo,
    Cancel,
}

/** Cursor is a UTF-16 offset; null means the preview has no encoding mapping. */
data class InlinePreeditProto(val text: String, val cursor: Int? = null)

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
    /** Assigned after the JNI snapshot; does not change native constructor signatures. */
    val snapshot: CandidateSnapshot

    /** Filled in the same native snapshot; never inferred from an empty preedit. */
    val isPrediction: Boolean

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
        /** Whether the candidates are laid out horizontally (queried natively) */
        val isHorizontalLayout: Boolean = false,
        override val highlighted: Int = 0,
        override val candidates: Array<CandidateProto> = arrayOf(),
    ) : Candidates {
        // Public setter is part of the JNI bridge: setPrediction(Z)V.
        override var isPrediction: Boolean = false

        override var snapshot: CandidateSnapshot = CandidateSnapshot.EMPTY
            internal set

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Paged

            if (hasPrevPage != other.hasPrevPage) return false
            if (hasNextPage != other.hasNextPage) return false
            if (isHorizontalLayout != other.isHorizontalLayout) return false
            if (highlighted != other.highlighted) return false
            if (isPrediction != other.isPrediction) return false
            if (!candidates.contentEquals(other.candidates)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = hasPrevPage.hashCode()
            result = 31 * result + hasNextPage.hashCode()
            result = 31 * result + isHorizontalLayout.hashCode()
            result = 31 * result + highlighted
            result = 31 * result + isPrediction.hashCode()
            result = 31 * result + candidates.contentHashCode()
            return result
        }

        override fun toString(): String = "Paged(hasPrevPage=$hasPrevPage, hasNextPage=$hasNextPage, isHorizontalLayout=$isHorizontalLayout, highlighted=$highlighted, candidatesCount=${candidates.size})"
    }

    /** Bulk list: [total] is the candidate count, or -1 if unknown */
    data class Bulk(
        val total: Int = -1,
        override val highlighted: Int = 0,
        override val candidates: Array<CandidateProto> = arrayOf(),
    ) : Candidates {
        // Public setter is part of the JNI bridge: setPrediction(Z)V.
        override var isPrediction: Boolean = false

        override var snapshot: CandidateSnapshot = CandidateSnapshot.EMPTY
            internal set

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Bulk

            if (total != other.total) return false
            if (highlighted != other.highlighted) return false
            if (isPrediction != other.isPrediction) return false
            if (!candidates.contentEquals(other.candidates)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = total
            result = 31 * result + highlighted
            result = 31 * result + isPrediction.hashCode()
            result = 31 * result + candidates.contentHashCode()
            return result
        }

        override fun toString(): String = "Bulk(total=$total, highlighted=$highlighted, candidatesCount=${candidates.size})"
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
