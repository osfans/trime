// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

class RimeProto {
    data class Commit(
        val text: String?,
    )

    data class Candidate(
        val text: String,
        val comment: String?,
        val label: String,
    )

    data class Context(
        val composition: Composition = Composition(),
        val menu: Menu = Menu(),
        val input: String = "",
        val caretPos: Int = 0,
    ) {
        data class Composition(
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
            constructor(text: String) : this(
                text.length,
                text.length,
                text.length,
                text.length,
                text,
            )
        }

        data class Menu(
            val pageSize: Int = 0,
            val pageNumber: Int = 0,
            val isLastPage: Boolean = false,
            val highlightedCandidateIndex: Int = 0,
            val candidates: Array<Candidate> = arrayOf(),
            val selectKeys: String? = null,
            val selectLabels: Array<String> = arrayOf(),
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Menu

                if (pageSize != other.pageSize) return false
                if (pageNumber != other.pageNumber) return false
                if (isLastPage != other.isLastPage) return false
                if (highlightedCandidateIndex != other.highlightedCandidateIndex) return false
                if (!candidates.contentEquals(other.candidates)) return false
                if (selectKeys != other.selectKeys) return false
                if (!selectLabels.contentEquals(other.selectLabels)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = pageSize
                result = 31 * result + pageNumber
                result = 31 * result + isLastPage.hashCode()
                result = 31 * result + highlightedCandidateIndex
                result = 31 * result + candidates.contentHashCode()
                result = 31 * result + (selectKeys?.hashCode() ?: 0)
                result = 31 * result + selectLabels.contentHashCode()
                return result
            }
        }
    }

    data class Status(
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
}
