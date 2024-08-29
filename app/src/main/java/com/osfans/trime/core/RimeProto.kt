package com.osfans.trime.core

class RimeProto {
    data class Commit(val text: String?)

    data class Candidate(val text: String, val comment: String?)

    data class Context(
        val composition: Composition,
        val menu: Menu,
        val commitTextPreview: String?,
        val selectLabels: Array<String>,
    ) {
        data class Composition(
            val length: Int = 0,
            val cursorPos: Int = 0,
            private val _selStart: Int = 0,
            private val _selEnd: Int = 0,
            val preedit: String? = null,
        ) {
            val selStart: Int =
                if (preedit.isNullOrEmpty()) 0 else String(preedit.toByteArray(), 0, _selStart).length

            val selEnd: Int =
                if (preedit.isNullOrEmpty()) 0 else String(preedit.toByteArray(), 0, _selEnd).length
        }

        data class Menu(
            val pageSize: Int = 0,
            val pageNumber: Int = 0,
            val isLastPage: Boolean = false,
            val highlightedCandidateIndex: Int = 0,
            val candidates: Array<Candidate> = arrayOf(),
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

                return true
            }

            override fun hashCode(): Int {
                var result = pageSize
                result = 31 * result + pageNumber
                result = 31 * result + isLastPage.hashCode()
                result = 31 * result + highlightedCandidateIndex
                result = 31 * result + candidates.contentHashCode()
                return result
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Context

            if (composition != other.composition) return false
            if (menu != other.menu) return false
            if (commitTextPreview != other.commitTextPreview) return false
            if (!selectLabels.contentEquals(other.selectLabels)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = composition.hashCode()
            result = 31 * result + menu.hashCode()
            result = 31 * result + (commitTextPreview?.hashCode() ?: 0)
            result = 31 * result + selectLabels.contentHashCode()
            return result
        }
    }

    data class Status(
        val schemaId: String,
        val schemaName: String,
        val isDisabled: Boolean,
        val isComposing: Boolean,
        val isAsciiMode: Boolean,
        val isFullShape: Boolean,
        val isSimplified: Boolean,
        val isTraditional: Boolean,
        val isAsciiPunch: Boolean,
    )
}
